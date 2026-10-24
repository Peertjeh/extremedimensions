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
}
