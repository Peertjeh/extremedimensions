package net.battlehub.core.ai;

import java.util.Random;

/**
 * Turns the chosen tactic into this tick's inputs: where to look (leading a moving target), how to move (closing,
 * holding distance, backing off, strafing to the side with more ground), when to sprint, jump for a crit, w-tap,
 * raise a shield and swing. The swing waits for the attack charge the level allows; nothing here can hit from
 * further than the NPC's reach.
 */
public final class CombatSteering {
	private int strafeDir = 1;
	/** Ticks until the strafe changes direction (a human holds a key for a while, not one tick). */
	private int strafeHold;
	private int wTap;
	private Clicker clicker;
	private int lastLaunch = Integer.MIN_VALUE / 2;

	public int strafeDir() {
		return strafeDir;
	}

	public Intent steer(Tactic tactic, Situation s, Track track, Personality p, Difficulty d, Random rng) {
		if (clicker == null) clicker = new Clicker(d, rng);
		Intent in = intent(tactic, s, track, p, d, rng);
		if (in.attack()) clicker.clicked();
		if (launch(tactic, s, d, rng)) {
			lastLaunch = s.tick();
			in = in.withLaunch();
		}
		return in;
	}

	/** Ticks from one launch to the next at the least (the flight itself takes 30 to 60 of them). */
	static final int LAUNCH_GAP = 50;

	/**
	 * A mace fight with a wind charge at hand: on the ground, near the target, far from an edge, a wind charge at its
	 * own feet sends it up, so the mace comes down on them with the smash bonus. People do it from 1.5 to 5.5 blocks
	 * away (half of the time 2.5 to 5.5) at a target on the ground, again soon after landing: every 3 to 4 seconds
	 * while they are close.
	 */
	boolean launch(Tactic tactic, Situation s, Difficulty d, Random rng) {
		Situation.Self me = s.self();
		Situation.Target t = s.target();
		if (me.focus() != Weapon.MACE || !me.canLaunch() || !me.onGround() || t == null || !t.visible()) return false;
		if (tactic != Tactic.STRIKE && tactic != Tactic.APPROACH && tactic != Tactic.SPACE) return false;
		double dist = s.distance();
		if (dist < 1.5 || dist > 5.5 || t.pos().y() - me.pos().y() > 1.5 || s.nearestEdge() < 2.5 || s.tick() - lastLaunch < LAUNCH_GAP) return false;
		return rng.nextDouble() < 0.01 + 0.07 * d.critSkill;
	}

	/** Ticks since its last launch (large when it never launched). */
	public int sinceLaunch(int tick) {
		return tick - lastLaunch;
	}

	/**
	 * Up in the air in a mace fight ({@link MaceFight}): drifting over the target (the keys pressed in the world towards
	 * the spot where the drift comes to rest over them, see {@link MaceFight#airPush}; {@code moveTo} is a block that
	 * way, or null to let go), eyes on them, and the smash as soon as they are within reach on the way down. A lower
	 * level now and then lets the moment pass.
	 */
	private Intent dive(Situation s, Track track, Difficulty d, Random rng) {
		Situation.Self me = s.self();
		Situation.Target t = s.target();
		V3 aim = aimPoint(s, track, d);
		V3 at = new V3(aim.x(), t.pos().y(), aim.z());
		V3 push = MaceFight.airPush(me.pos(), me.vel(), at);
		V3 to = push.flatLength() < 1e-6 ? null : me.pos().add(push);
		boolean smash = t.visible() && MaceFight.smash(me, t.pos()) && rng.nextDouble() < 0.4 + 0.6 * d.critSkill;
		return new Intent(0, 0, to != null && at.sub(me.pos()).flatLength() > 2, false, aim, to, smash, false, false);
	}

	private Intent intent(Tactic tactic, Situation s, Track track, Personality p, Difficulty d, Random rng) {
		Situation.Self me = s.self();
		Situation.Target t = s.target();
		if (wTap > 0) wTap--;
		if (t == null && tactic != Tactic.OBJECTIVE && tactic != Tactic.RECOVER) {
			return Intent.NOTHING;
		}
		// up in the air with the mace: coming down on them goes before whatever it would do on the ground. High up every
		// direction looks like a drop, so the mind calls for recovering; on its own launch (the ground was checked before
		// it threw, and the landing is still checked) the dive goes on
		int sinceLaunch = s.tick() - lastLaunch;
		if ((tactic != Tactic.RECOVER || sinceLaunch <= MaceFight.FLIGHT) && MaceFight.diving(me, t, sinceLaunch)) {
			return dive(s, track, d, rng);
		}
		double dist = s.distance();
		double reach = me.reach();
		V3 aim = t == null ? null : aimPoint(s, track, d);
		V3 facing = t == null ? V3.ZERO : t.pos().sub(me.pos()).flat().unit();
		// a click when the finger is ready: the charge it waits for this time, a little late, sometimes from too far
		boolean canHit = t != null && clicker.wants(me.attackReady(), dist, reach, t.visible());
		boolean fallingCrit = t != null && t.visible() && dist <= reach && !me.onGround() && me.vel().y() < -0.05
				&& me.attackReady() >= 0.85 && d.critSkill >= 0.5 && canHit;
		return switch (tactic) {
			case APPROACH -> {
				V3 to = track != null && !track.empty() ? track.predict(s.tick(), d.predictTicks) : t.pos();
				boolean crit = critJump(me, dist, reach, d, rng, s);
				yield new Intent(1, 0, true, crit, dist < 6 ? aim : null, to, canHit || fallingCrit, false, false);
			}
			case STRIKE -> {
				double fwd = wTap > 0 ? 0 : dist > reach - 0.3 ? 1 : dist < 1.4 ? -0.6 : 0.25;
				double str = strafe(s, facing, d, rng);
				boolean crit = critJump(me, dist, reach, d, rng, s);
				boolean hit = canHit || fallingCrit;
				if (hit && d.wTap) wTap = 2;
				boolean block = !hit && me.hasShield() && me.attackReady() < 0.5 && dist < reach + 0.5 && rng.nextDouble() < d.shieldSkill * 0.3;
				yield new Intent(fwd, str, dist > 2.0 && wTap == 0, crit, aim, null, hit, block, false);
			}
			case SHOOT -> {
				// keep a distance the arrow likes; strafe so they have to aim too
				double hold = Math.max(9, Math.min(24, p.range() * 4));
				double fwd = dist < hold - 3 ? -0.7 : dist > hold + 8 ? 0.7 : 0;
				double str = strafe(s, facing, d, rng) * 0.8;
				yield new Intent(fwd, str, false, false, arrowAim(s, track, d), null, false, false, false, true);
			}
			case SPACE -> {
				double hold = Math.max(reach + 0.25, Math.min(6, p.range()));
				double fwd = dist > hold + 0.4 ? 0.8 : dist < hold - 0.3 ? -0.8 : 0;
				double str = strafe(s, facing, d, rng);
				// they stepped into reach: punish
				boolean hit = dist <= reach - 0.1 && canHit;
				boolean block = !hit && me.hasShield() && dist < reach + 0.3 && me.attackReady() < 0.6 && rng.nextDouble() < d.shieldSkill * 0.4;
				yield new Intent(fwd, str, false, false, aim, null, hit, block, false);
			}
			case RETREAT, HEAL -> {
				V3 away = facing.scale(-1);
				double fwd = s.ground(away) > 1.5 ? -1 : 0;
				double str = fwd == 0 ? sideWithGround(s, facing) : strafe(s, facing, d, rng) * 0.6;
				boolean heal = tactic == Tactic.HEAL;
				yield new Intent(fwd, str, false, false, aim, null, !heal && canHit, false, heal);
			}
			case EVADE -> {
				V3 from = s.threat() == null ? facing : s.threat().from().flat().unit();
				V3 side = from.left();
				double left = s.ground(side), right = s.ground(side.scale(-1));
				// strafe is relative to where we look (at the target); pick the side of the threat's path with ground
				double str = left >= right ? 1 : -1;
				yield new Intent(0, str, false, me.onGround() && rng.nextDouble() < 0.3, aim, null, canHit, false, false);
			}
			case RECOVER -> {
				V3 safe = s.safest();
				V3 to = me.pos().add(safe.scale(2.5));
				yield new Intent(0, 0, false, false, aim, to, canHit, false, false);
			}
			case SEARCH -> new Intent(1, 0, p.aggression() > 0.5, false, null, t.pos(), false, false, false);
			case OBJECTIVE -> objective(s, aim, canHit);
			case IDLE -> aim == null ? Intent.NOTHING : Intent.NOTHING.withLook(aim);
		};
	}

	/**
	 * Towards the objective; on the hill or at the post: stay there, facing whoever is about (a little to and fro on
	 * a hill, so standing on it is not standing still).
	 */
	static Intent objective(Situation s, V3 aim, boolean canHit) {
		Objective g = s.goal();
		if (g == null) return Intent.NOTHING;
		boolean stay = (g.kind() == Objective.Kind.HOLD || g.kind() == Objective.Kind.DEFEND)
				&& s.self().pos().flatDistance(g.point()) <= Math.max(0.8, g.radius() * 0.6);
		if (stay) {
			double str = g.kind() == Objective.Kind.HOLD ? ((s.tick() / 20) % 2 == 0 ? 0.4 : -0.4) : 0;
			return new Intent(0, str, false, false, aim, null, canHit, false, false);
		}
		return new Intent(1, 0, true, false, null, g.point(), canHit, false, false);
	}

	/** Where to aim: the chest, a few ticks ahead along their path (as far ahead as the level can judge). */
	static V3 aimPoint(Situation s, Track track, Difficulty d) {
		Situation.Target t = s.target();
		V3 at = t.pos();
		if (track != null && !track.empty() && d.predictTicks > 0) {
			V3 p = track.predict(s.tick(), Math.min(d.predictTicks, 3));
			if (p != null) at = p;
		}
		return at.add(new V3(0, 1.2, 0));
	}

	/** The speed of an arrow from a fully drawn bow (blocks per tick). */
	static final double ARROW_SPEED = 3.0;

	/**
	 * Where to aim an arrow: their chest where they will be when it arrives (its flight time from the real arrow
	 * physics), as far ahead as the level can judge. The world layer adds the arc and the aim wobble.
	 */
	static V3 arrowAim(Situation s, Track track, Difficulty d) {
		Situation.Target t = s.target();
		V3 eye = s.self().pos().add(new V3(0, 1.6, 0));
		double h = t.pos().flatDistance(eye);
		int flight = net.battlehub.core.BotMath.arrowTicks(h, ARROW_SPEED);
		int ahead = Math.min(flight, d.predictTicks * 2 + (d.predictTicks > 0 ? 2 : 0));
		V3 at = t.pos();
		if (track != null && !track.empty() && ahead > 0) {
			V3 p = track.predict(s.tick(), ahead);
			if (p != null) at = new V3(p.x(), t.pos().y(), p.z());
		}
		return at.add(new V3(0, 1.1, 0));
	}

	/** Sideways movement: more and smarter at higher levels; never towards an edge. */
	private double strafe(Situation s, V3 facing, Difficulty d, Random rng) {
		if (d.strafeSkill <= 0) return 0;
		if (strafeHold-- <= 0) {
			// a key is held for a while: shorter and less regular at higher levels
			strafeDir = -strafeDir;
			strafeHold = (int) (5 + 13 * (1 - d.strafeSkill)) + rng.nextInt((int) (10 + 15 * (1 - d.strafeSkill)));
		}
		V3 side = facing.left().scale(strafeDir);
		if (s.ground(side) < 1.5) {
			strafeDir = -strafeDir;
			strafeHold = 6 + rng.nextInt(8);
			side = side.scale(-1);
			if (s.ground(side) < 1.5) return 0;
		}
		return strafeDir * (0.4 + 0.6 * d.strafeSkill);
	}

	/** The side (left 1, right -1) with more ground, for backing off when there is no room behind. */
	private static double sideWithGround(Situation s, V3 facing) {
		V3 left = facing.left();
		return s.ground(left) >= s.ground(left.scale(-1)) ? 1 : -1;
	}

	/** A jump for a falling hit (a crit; in a mace fight too: on the ground that is the sword's, the smash needs a launch). */
	private static boolean critJump(Situation.Self me, double dist, double reach, Difficulty d, Random rng, Situation s) {
		double chance = d.critSkill * 0.15;
		return me.onGround() && dist > reach - 0.5 && dist < reach + 1.5 && me.attackReady() > 0.85 && rng.nextDouble() < chance;
	}
}
