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
	private int wTap;

	public int strafeDir() {
		return strafeDir;
	}

	public Intent steer(Tactic tactic, Situation s, Track track, Personality p, Difficulty d, Random rng) {
		Situation.Self me = s.self();
		Situation.Target t = s.target();
		if (wTap > 0) wTap--;
		if (t == null && tactic != Tactic.OBJECTIVE && tactic != Tactic.RECOVER) {
			return Intent.NOTHING;
		}
		double dist = s.distance();
		double reach = me.reach();
		V3 aim = t == null ? null : aimPoint(s, track, d);
		V3 facing = t == null ? V3.ZERO : t.pos().sub(me.pos()).flat().unit();
		boolean canHit = t != null && t.visible() && dist <= reach && me.attackReady() >= d.readyToHit;
		boolean fallingCrit = t != null && t.visible() && dist <= reach && !me.onGround() && me.vel().y() < -0.05
				&& me.attackReady() >= 0.85 && d.critSkill >= 0.5;
		return switch (tactic) {
			case APPROACH -> {
				V3 to = track != null && !track.empty() ? track.predict(s.tick(), d.predictTicks) : t.pos();
				boolean crit = critJump(me, dist, reach, d, rng);
				yield new Intent(1, 0, true, crit, dist < 6 ? aim : null, to, canHit || fallingCrit, false, false);
			}
			case STRIKE -> {
				double fwd = wTap > 0 ? 0 : dist > reach - 0.3 ? 1 : dist < 1.4 ? -0.6 : 0.25;
				double str = strafe(s, facing, d, rng);
				boolean crit = critJump(me, dist, reach, d, rng);
				boolean hit = canHit || fallingCrit;
				if (hit && d.wTap) wTap = 2;
				boolean block = !hit && me.hasShield() && me.attackReady() < 0.5 && dist < reach + 0.5 && rng.nextDouble() < d.shieldSkill * 0.3;
				yield new Intent(fwd, str, dist > 2.0 && wTap == 0, crit, aim, null, hit, block, false);
			}
			case SPACE -> {
				double hold = Math.max(reach + 0.25, Math.min(6, p.range()));
				double fwd = dist > hold + 0.4 ? 0.8 : dist < hold - 0.3 ? -0.8 : 0;
				double str = strafe(s, facing, d, rng);
				// they stepped into reach: punish
				boolean hit = t.visible() && dist <= reach - 0.1 && me.attackReady() >= d.readyToHit;
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
			case OBJECTIVE -> new Intent(1, 0, true, false, null, s.objective(), canHit, false, false);
			case IDLE -> aim == null ? Intent.NOTHING : Intent.NOTHING.withLook(aim);
		};
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

	/** Sideways movement: more and smarter at higher levels; never towards an edge. */
	private double strafe(Situation s, V3 facing, Difficulty d, Random rng) {
		if (d.strafeSkill <= 0) return 0;
		if (rng.nextDouble() < 0.02 + d.strafeSkill * 0.04) strafeDir = -strafeDir;
		V3 side = facing.left().scale(strafeDir);
		if (s.ground(side) < 1.5) {
			strafeDir = -strafeDir;
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

	private static boolean critJump(Situation.Self me, double dist, double reach, Difficulty d, Random rng) {
		return me.onGround() && dist > reach - 0.5 && dist < reach + 1.5 && me.attackReady() > 0.85 && rng.nextDouble() < d.critSkill * 0.15;
	}
}
