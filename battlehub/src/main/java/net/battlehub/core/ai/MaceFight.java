package net.battlehub.core.ai;

/**
 * How a player fights a mace fight (1.46, measured in people's mace fights): the sword on the ground, a wind charge at
 * the feet to fly up, the mace in hand on the way up, drifting over the target, and the smash as soon as they come
 * within reach on the way down. In the fights people sent in they threw it 2.5 to 5.5 blocks from the target, flew
 * 9 to 12 blocks up and hit with 5 to 9 blocks of fall behind them, from right above (under a block to the side).
 */
public final class MaceFight {
	private MaceFight() {}

	/** The fall the mace needs for its smash bonus (vanilla: more than 1.5 blocks). */
	public static final double SMASH_FALL = 1.5;
	/** How long after a launch the bot counts as up in the air for it (a flight takes 30 to 60 ticks). */
	public static final int FLIGHT = 60;
	/** In the air a player keeps 91% of its sideways speed every tick. */
	static final double AIR_DRAG = 0.91;

	/**
	 * The mace in hand rather than the sword: in the air after a launch, or falling far enough for a smash. An ordinary
	 * jump (a fall of 1.25 at most) or a knock keeps the sword, so its charge is not lost to a switch.
	 */
	public static boolean maceInHand(boolean onGround, double fall, int sinceLaunch) {
		return !onGround && (sinceLaunch <= FLIGHT || fall > SMASH_FALL);
	}

	/** Coming down on the target with the mace: in the air above them after a launch, or falling onto them. */
	public static boolean diving(Situation.Self me, Situation.Target t, int sinceLaunch) {
		return me.focus() == Weapon.MACE && t != null && maceInHand(me.onGround(), me.fall(), sinceLaunch) && me.pos().y() > t.pos().y() + 0.5;
	}

	/**
	 * From the eyes to the nearest point of the target's body (0.6 wide, 1.8 tall): the distance the reach is measured
	 * over, which from above is much less than the distance between the feet.
	 */
	public static double reachTo(V3 me, V3 them) {
		double eye = me.y() + 1.62;
		double dy = eye > them.y() + 1.8 ? eye - them.y() - 1.8 : eye < them.y() ? them.y() - eye : 0;
		double dx = Math.max(0, Math.abs(me.x() - them.x()) - 0.3);
		double dz = Math.max(0, Math.abs(me.z() - them.z()) - 0.3);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The moment for the smash: on the way down, far enough fallen for the bonus, and the target within reach. */
	public static boolean smash(Situation.Self me, V3 them) {
		return !me.onGround() && me.vel().y() < -0.1 && me.fall() > SMASH_FALL && reachTo(me.pos(), them) <= me.reach() - 0.1;
	}

	/** What the keys add to the sideways speed in the air every tick (vanilla: 0.02). */
	static final double AIR_PUSH = 0.02;

	/** How far a player sliding at {@code speed} through the air still goes while pressing against it. */
	static double brakeDistance(double speed) {
		double d = 0, v = speed;
		while (v > 0) {
			v = (v - AIR_PUSH) * AIR_DRAG;
			if (v > 0) d += v;
		}
		return d;
	}

	/**
	 * Which way to press in the air (a unit vector in the world, or zero) to come to rest over {@code at}: towards the
	 * target while it could still stop in time, against the drift once pressing back is needed to stop over them (the
	 * keys stop a player in the air much sooner than letting go: 0.55 blocks from 0.2 a tick, against 2). In the world,
	 * not relative to where the bot looks: right over the target the eyes swing round, the keys must not swing with them.
	 */
	public static V3 airPush(V3 pos, V3 vel, V3 at) {
		V3 v = vel.flat();
		double speed = v.flatLength();
		V3 stop = speed < 1e-9 ? V3.ZERO : v.scale(brakeDistance(speed) / speed);
		V3 miss = at.sub(pos).flat().sub(stop);
		return miss.flatLength() < 0.15 ? V3.ZERO : miss.unit();
	}

	// ------------------------------------------------------------------ seeing a smash coming (1.49)

	/** How close to the target (sideways) a smash that comes down counts as coming at them. */
	public static final double SMASH_ZONE = 3.5;
	/** How far ahead (ticks) a bot looks for a smash coming down. */
	public static final int SMASH_HORIZON = 30;

	/** A smash coming down on someone: where the attacker reaches their head height, and in how many ticks. */
	public record Smash(V3 at, int in) {}

	/**
	 * Whether someone in the air at {@code pos}, moving at {@code vel} with {@code fall} blocks of fall behind them, comes
	 * down on a target at {@code me} within {@code horizon} ticks with enough fall for the smash: where and when they
	 * reach the target's head height (within {@link #SMASH_ZONE} sideways), or null. On the way up too: a wind charge
	 * that just went off is seen coming. The flight follows the game (gravity 0.08 a tick, the air keeping 98% of the
	 * speed up or down and 91% of the speed sideways), without the attacker steering.
	 */
	public static Smash incoming(V3 me, V3 pos, V3 vel, double fall, int horizon) {
		if (pos.y() < me.y() + 1.0) return null;
		double x = pos.x(), y = pos.y(), z = pos.z(), vx = vel.x(), vy = vel.y(), vz = vel.z(), f = Math.max(0, fall);
		double head = me.y() + 1.8;
		for (int t = 1; t <= horizon; t++) {
			vy = (vy - 0.08) * 0.98;
			vx *= AIR_DRAG;
			vz *= AIR_DRAG;
			x += vx;
			z += vz;
			double ny = y + vy;
			if (vy < 0) {
				f += y - ny;
				if (ny <= head) {
					if (f <= SMASH_FALL) return null;
					return Math.hypot(x - me.x(), z - me.z()) <= SMASH_ZONE ? new Smash(new V3(x, me.y(), z), t) : null;
				}
			} else {
				// on the way up (a wind charge's burst clears the fall) nothing counts yet
				f = 0;
			}
			y = ny;
		}
		return null;
	}

	/**
	 * Which way to run from a smash (flat, unit): away from where it comes down; from right under it, away from where the
	 * attacker is now (sideways to their path), else along {@code fallback}.
	 */
	public static V3 away(V3 me, Smash s, V3 attacker, V3 fallback) {
		V3 d = me.sub(s.at()).flat();
		if (d.flatLength() > 0.4) return d.unit();
		V3 a = me.sub(attacker).flat();
		if (a.flatLength() > 0.2) return a.unit();
		return fallback.flat().flatLength() > 1e-6 ? fallback.flat().unit() : new V3(1, 0, 0);
	}

	/**
	 * Whether a bot of this level reacts to a smash it saw coming {@code seenFor} ticks ago: after its reaction time, and
	 * only if it notices at all ({@code roll} 0..1, drawn once per smash): a beginner seldom, a master nearly always.
	 */
	public static boolean reacts(Difficulty d, int seenFor, double roll) {
		double notice = Math.max(0.1, Math.min(1.0, 1 - 2.5 * d.mistakeRate));
		return seenFor >= d.reactionTicks && roll < notice;
	}
}
