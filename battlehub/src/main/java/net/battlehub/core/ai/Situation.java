package net.battlehub.core.ai;

import java.util.UUID;

/**
 * What an NPC knows right now: about itself, its target (as seen, or as remembered), the ground round it, incoming
 * danger and the game's objective. Built from the world every think; the decisions only use this.
 *
 * @param goal    what the game wants besides fighting (see {@link Objective}), or null
 * @param edge    for eight directions (0 = +z, going round via -x like Minecraft yaw), how far the ground reaches
 *                before a deadly drop, in blocks (capped at {@link #SAFE}); {@code null} means no edges known
 */
public record Situation(int tick, Self self, Target target, Objective goal, double[] edge, Threat threat) {
	public static final double SAFE = 4;

	/** The objective's point, or null. */
	public V3 objective() {
		return goal == null ? null : goal.point();
	}

	/**
	 * The NPC itself. {@code attackReady}: 0..1 like the attack strength meter. {@code reach}: how far it can hit.
	 * {@code hasMelee}: a sword, axe or mace on the hotbar. {@code hasBow}: a bow or crossbow and arrows.
	 * {@code drawTicks}: how long it has been drawing the bow (0 when not). {@code focus}: the weapon the fight is
	 * about (a bow fight, a mace fight; ANY for mixed kits). {@code canLaunch}: it carries a wind charge (for a mace
	 * smash from above). {@code fall}: how far it has fallen so far (the mace smashes after more than 1.5 blocks).
	 */
	public record Self(V3 pos, V3 vel, double health, double maxHealth, boolean onGround, double attackReady, boolean canHeal,
			boolean hasShield, double reach, boolean hasMelee, boolean hasBow, int drawTicks, Weapon focus, boolean canLaunch, double fall) {
		/** Not falling. */
		public Self(V3 pos, V3 vel, double health, double maxHealth, boolean onGround, double attackReady, boolean canHeal, boolean hasShield,
				double reach, boolean hasMelee, boolean hasBow, int drawTicks, Weapon focus, boolean canLaunch) {
			this(pos, vel, health, maxHealth, onGround, attackReady, canHeal, hasShield, reach, hasMelee, hasBow, drawTicks, focus, canLaunch, 0);
		}

		/** A melee fighter without a bow (most tests and most modes). */
		public Self(V3 pos, V3 vel, double health, double maxHealth, boolean onGround, double attackReady, boolean canHeal, boolean hasShield,
				double reach) {
			this(pos, vel, health, maxHealth, onGround, attackReady, canHeal, hasShield, reach, true, false, 0, Weapon.ANY, false, 0);
		}

		/** Without a fight weapon and wind charges. */
		public Self(V3 pos, V3 vel, double health, double maxHealth, boolean onGround, double attackReady, boolean canHeal, boolean hasShield,
				double reach, boolean hasMelee, boolean hasBow, int drawTicks) {
			this(pos, vel, health, maxHealth, onGround, attackReady, canHeal, hasShield, reach, hasMelee, hasBow, drawTicks, Weapon.ANY, false, 0);
		}

		public double healthShare() {
			return maxHealth <= 0 ? 0 : health / maxHealth;
		}
	}

	/**
	 * The target. {@code visible}: seen right now (in sight and in range); otherwise {@code pos} is where it was
	 * last seen and {@code confidence} how sure the NPC still is. {@code swung}: ticks since it was seen swinging
	 * (large when unknown).
	 */
	public record Target(UUID id, V3 pos, V3 vel, double health, double maxHealth, boolean visible, double confidence, int swung,
			boolean facingMe) {
		public double healthShare() {
			return maxHealth <= 0 ? 1 : health / maxHealth;
		}
	}

	/**
	 * Danger coming at the NPC: projectiles (how many and from where, a unit vector), and a mace smash coming down that it
	 * noticed (where, when, and where the attacker is now), or none.
	 */
	public record Threat(int incoming, V3 from, MaceFight.Smash smash, V3 attacker) {
		public static final Threat NONE = new Threat(0, V3.ZERO, null, null);

		public Threat(int incoming, V3 from) {
			this(incoming, from, null, null);
		}

		/** Anything to get away from. */
		public boolean any() {
			return incoming > 0 || smash != null;
		}
	}

	public double distance() {
		return target == null ? Double.POSITIVE_INFINITY : self.pos().distance(target.pos());
	}

	/** Index of the edge direction nearest to a horizontal direction. */
	public static int direction(V3 dir) {
		double a = Math.atan2(-dir.x(), dir.z());
		int i = (int) Math.round(a / (Math.PI / 4));
		return Math.floorMod(i, 8);
	}

	/** Unit vector of edge direction {@code i}. */
	public static V3 direction(int i) {
		double a = i * Math.PI / 4;
		return new V3(-Math.sin(a), 0, Math.cos(a));
	}

	/** How far the ground reaches in this direction (SAFE when unknown). */
	public double ground(V3 dir) {
		if (edge == null || edge.length != 8 || dir.flatLength() < 1e-6) return SAFE;
		return edge[direction(dir)];
	}

	/** The direction with the most ground. */
	public V3 safest() {
		if (edge == null || edge.length != 8) return V3.ZERO;
		int best = 0;
		for (int i = 1; i < 8; i++) {
			if (edge[i] > edge[best]) best = i;
		}
		return direction(best);
	}

	/** Shortest ground in any direction. */
	public double nearestEdge() {
		if (edge == null || edge.length != 8) return SAFE;
		double m = SAFE;
		for (double e : edge) m = Math.min(m, e);
		return m;
	}
}
