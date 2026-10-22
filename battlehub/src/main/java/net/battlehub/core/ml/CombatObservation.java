package net.battlehub.core.ml;

/**
 * The model's observation from a {@link CombatState}: the same formulas as ml/pvp_sim/obs.py (spec v1) and
 * ml/pvp_sim2/obs.py (v2), checked number for number against the golden files the simulation writes
 * (CombatObservationTest). Each value is (raw / scale), clipped. The spec says which values, in which order.
 */
public final class CombatObservation {
	private CombatObservation() {}

	/** The scaled, clipped observation, as the model takes it. */
	public static float[] build(CombatSpec spec, CombatState s) {
		double[] raw = raw(spec, s);
		float[] out = new float[raw.length];
		for (int k = 0; k < raw.length; k++) {
			double v = raw[k] / spec.obsScale[k];
			out[k] = (float) Math.max(-spec.obsClip[k], Math.min(spec.obsClip[k], v));
		}
		return out;
	}

	/** The same, in doubles (for the test against the golden file). */
	public static double[] scaled(CombatSpec spec, CombatState s) {
		double[] raw = raw(spec, s);
		for (int k = 0; k < raw.length; k++) raw[k] = Math.max(-spec.obsClip[k], Math.min(spec.obsClip[k], raw[k] / spec.obsScale[k]));
		return raw;
	}

	/** The unscaled values in spec order. */
	public static double[] raw(CombatSpec spec, CombatState s) {
		double ry = Math.toRadians(s.selfYaw);
		double fx = -Math.sin(ry), fz = Math.cos(ry);
		double lx = Math.cos(ry), lz = Math.sin(ry);
		double dx = s.oppX - s.selfX, dz = s.oppZ - s.selfZ, dy = s.oppY - s.selfY;
		double dist = Math.hypot(dx, dz);
		boolean vis = s.oppVisible;
		double ovx = vis ? s.oppVx : 0, ovy = vis ? s.oppVy : 0, ovz = vis ? s.oppVz : 0;
		double aFwd = dx * fx + dz * fz;
		double aLeft = dx * lx + dz * lz;
		boolean near = dist < 1e-9;
		double aimSin = near ? 0 : aLeft / dist;
		double aimCos = near ? 1 : aFwd / dist;
		double chestDy = (s.oppY + spec.chestHeight * s.oppScale) - (s.selfY + spec.eyeHeight * s.selfScale);
		double needPitch = -Math.toDegrees(Math.atan2(chestDy, dist));
		double ory = Math.toRadians(s.oppYaw);
		double ofx = -Math.sin(ory), ofz = Math.cos(ory);
		double olx = Math.cos(ory), olz = Math.sin(ory);
		double oFwd = -dx * ofx - dz * ofz;
		double oLeft = -dx * olx - dz * olz;
		double lookSin = near ? 0 : oLeft / dist;
		double lookCos = near ? 1 : oFwd / dist;
		// the second enemy
		boolean e2 = s.e2Present, e2v = e2 && s.e2Visible;
		double ex = e2 ? s.e2X - s.selfX : 0, ez = e2 ? s.e2Z - s.selfZ : 0, ey = e2 ? s.e2Y - s.selfY : 0;
		double edist = Math.hypot(ex, ez);
		double ery = Math.toRadians(s.e2Yaw);
		double elook = edist < 1e-9 ? 1 : (-ex * -Math.sin(ery) - ez * Math.cos(ery)) / edist;
		double[] threat = threat(spec, s);
		double[] out = new double[spec.obsDim()];
		for (int k = 0; k < out.length; k++) {
			String n = spec.obsNames.get(k);
			out[k] = switch (n) {
				case "opp_fwd" -> aFwd;
				case "opp_left" -> aLeft;
				case "opp_up" -> dy;
				case "opp_dist" -> dist;
				case "opp_vel_fwd" -> ovx * fx + ovz * fz;
				case "opp_vel_left" -> ovx * lx + ovz * lz;
				case "opp_vel_up" -> ovy;
				case "self_vel_fwd" -> s.selfVx * fx + s.selfVz * fz;
				case "self_vel_left" -> s.selfVx * lx + s.selfVz * lz;
				case "self_vel_up" -> s.selfVy;
				case "aim_yaw_sin" -> aimSin;
				case "aim_yaw_cos" -> aimCos;
				case "aim_pitch" -> needPitch - s.selfPitch;
				case "opp_look_sin" -> lookSin;
				case "opp_look_cos" -> lookCos;
				case "on_crosshair" -> crosshair(spec, s) ? 1 : 0;
				case "self_ground" -> s.selfOnGround ? 1 : 0;
				case "self_sprint" -> s.selfSprinting ? 1 : 0;
				case "self_charge" -> s.selfCharge;
				case "self_hurt" -> s.selfHurtTicks;
				case "self_health" -> s.selfHealth / Math.max(1e-9, s.selfMaxHealth);
				case "self_absorption" -> s.selfAbsorption;
				case "self_food" -> s.selfFood;
				case "self_saturation" -> s.selfSaturation;
				case "self_regen" -> s.selfRegen;
				case "self_fall" -> s.selfFall;
				case "opp_visible" -> vis ? 1 : 0;
				case "opp_ground" -> vis && s.oppOnGround ? 1 : 0;
				case "opp_sprint" -> vis && s.oppSprinting ? 1 : 0;
				case "opp_since_swing" -> vis ? s.oppSinceSwing : spec.obsScale[k];
				case "opp_hurt" -> vis ? s.oppHurtTicks : 0;
				case "opp_health" -> vis ? s.oppHealth / Math.max(1e-9, s.oppMaxHealth) : 1;
				case "opp_absorption" -> vis ? s.oppAbsorption : 0;
				case "opp_blocking" -> vis && s.oppBlocking ? 1 : 0;
				case "opp_draw" -> vis ? s.oppDraw : 0;
				case "opp_eating" -> vis && s.oppEating ? 1 : 0;
				case "opp_holds_bow" -> vis && s.oppHoldsBow ? 1 : 0;
				case "opp_holds_food" -> vis && s.oppHoldsFood ? 1 : 0;
				case "opp_holds_throwable" -> vis && s.oppHoldsThrowable ? 1 : 0;
				case "self_dmg" -> fullHit(s.selfMeleeDamage, s.selfMeleeBonus, s.oppArmor, s.oppToughness, s.oppResistance, s.oppMaxHealth);
				case "opp_dmg" -> fullHit(s.oppMeleeDamage, s.oppMeleeBonus, s.selfArmor, s.selfToughness, s.selfResistance, s.selfMaxHealth);
				case "self_cooldown" -> s.selfCooldown;
				case "opp_cooldown" -> s.oppCooldown;
				case "self_kb" -> s.selfKb;
				case "opp_kb" -> s.oppKb;
				case "self_speed" -> s.selfWalkSpeed / 0.1 - 1.0;
				case "self_jump" -> s.selfJumpPower / 0.42 - 1.0;
				case "self_gravity" -> s.selfGravity / 0.08 - 1.0;
				case "self_scale" -> s.selfScale - 1.0;
				case "opp_scale" -> s.oppScale - 1.0;
				case "self_reach" -> s.selfReach / 3.0 - 1.0;
				case "damage_on" -> s.damageOn ? 1 : 0;
				case "self_arrows" -> s.selfArrows;
				case "self_food_items" -> s.selfFoodItems;
				case "self_gapples" -> s.selfGapples;
				case "self_pearls" -> s.selfPearls;
				case "self_winds" -> s.selfWinds;
				case "self_shield" -> s.selfShield ? 1 : 0;
				case "pearl_cooldown" -> s.selfPearlCd;
				case "wind_cooldown" -> s.selfWindCd;
				case "shield_cooldown" -> s.selfShieldCd;
				case "self_use" -> s.selfUseTicks;
				case "self_blocking" -> s.selfBlocking ? 1 : 0;
				case "threat_time" -> threat[0];
				case "threat_miss" -> threat[1];
				case "threat_fwd" -> threat[2];
				case "threat_left" -> threat[3];
				case "e2_fwd" -> ex * fx + ez * fz;
				case "e2_left" -> ex * lx + ez * lz;
				case "e2_up" -> ey;
				case "e2_dist" -> e2 ? edist : spec.obsScale[k];
				case "e2_visible" -> e2v ? 1 : 0;
				case "e2_look" -> e2v ? elook : 0;
				case "e2_since_swing" -> e2v ? s.e2SinceSwing : spec.obsScale[k];
				case "e2_holds_bow" -> e2v && s.e2HoldsBow ? 1 : 0;
				case "enemies_near" -> s.enemiesNear;
				case "self_below" -> s.selfBelow;
				case "opp_below" -> s.oppBelow;
				case "opp_drop_behind" -> s.oppDropBehind;
				case "opp_void_behind" -> s.oppVoidBehind;
				default -> other(spec, n, s);
			};
		}
		return out;
	}

	/** The hand one-hot and the rays. */
	private static double other(CombatSpec spec, String name, CombatState s) {
		if (name.startsWith("hand_")) return spec.hands.indexOf(name.substring(5)) == s.selfHand ? 1 : 0;
		int u = name.lastIndexOf('_');
		if (u > 0) {
			int k = Integer.parseInt(name.substring(u + 1));
			switch (name.substring(0, u)) {
				case "edge": return s.edges[k];
				case "wall": return s.walls[k];
				case "void": return s.voids[k];
				case "drop": return s.drops[k];
				case "step": return s.steps[k];
				default: break;
			}
		}
		throw new IllegalArgumentException("the spec asks for an observation this mod does not know: " + name);
	}

	/** Damage after armor (the game's formula). */
	public static double armorReduce(double damage, double armor, double toughness) {
		double f = 2.0 + toughness / 4.0;
		double pts = Math.max(armor * 0.2, Math.min(20.0, armor - damage / f));
		return damage * (1.0 - pts / 25.0);
	}

	/** A full (charged, not critical) hit after armor and resistance, per the victim's max health. */
	public static double fullHit(double damage, double bonus, double armor, double toughness, double resistance, double maxHealth) {
		double d = armorReduce(Math.max(0.0, damage + bonus), armor, toughness) * Math.max(0.0, 1.0 - 0.2 * resistance);
		return d / Math.max(1e-9, maxHealth);
	}

	/**
	 * For the projectile coming at the bot: ticks until it is closest, how close it passes the chest, and where it is
	 * (fwd, left, horizontal unit); none: 20, 2, 0, 0.
	 */
	public static double[] threat(CombatSpec spec, CombatState s) {
		if (!s.threatPresent) return new double[] {20.0, 2.0, 0.0, 0.0};
		double ry = Math.toRadians(s.selfYaw);
		double fx = -Math.sin(ry), fz = Math.cos(ry), lx = Math.cos(ry), lz = Math.sin(ry);
		double px = s.threatX - s.selfX, py = s.threatY - (s.selfY + spec.chestHeight * s.selfScale), pz = s.threatZ - s.selfZ;
		double vx = s.threatVx, vy = s.threatVy, vz = s.threatVz;
		double vv = vx * vx + vy * vy + vz * vz;
		double t = vv > 1e-9 ? Math.max(0.0, Math.min(40.0, -(px * vx + py * vy + pz * vz) / vv)) : 0.0;
		double cx = px + vx * t, cy = py + vy * t, cz = pz + vz * t;
		double miss = Math.sqrt(cx * cx + cy * cy + cz * cz);
		double ph = Math.hypot(px, pz);
		boolean tiny = ph < 1e-9;
		return new double[] {t, miss, tiny ? 0 : (px * fx + pz * fz) / ph, tiny ? 0 : (px * lx + pz * lz) / ph};
	}

	/** The look ray from the eyes hits the opponent's hitbox within the bot's reach (or starts inside it). */
	public static boolean crosshair(CombatSpec spec, CombatState s) {
		double ry = Math.toRadians(s.selfYaw), rp = Math.toRadians(s.selfPitch);
		double[] l = {-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp)};
		double[] e = {s.selfX, s.selfY + spec.eyeHeight * s.selfScale, s.selfZ};
		double hw = spec.hitboxWidth * s.oppScale / 2;
		double[] lo = {s.oppX - hw, s.oppY, s.oppZ - hw};
		double[] hi = {s.oppX + hw, s.oppY + spec.hitboxHeight * s.oppScale, s.oppZ + hw};
		return rayHitsBox(e, l, lo, hi, s.selfReach);
	}

	static boolean rayHitsBox(double[] e, double[] l, double[] lo, double[] hi, double reach) {
		double tmin = Double.NEGATIVE_INFINITY, tmax = Double.POSITIVE_INFINITY;
		for (int a = 0; a < 3; a++) {
			if (Math.abs(l[a]) < 1e-12) {
				if (e[a] < lo[a] || e[a] > hi[a]) return false;
				continue;
			}
			double t1 = (lo[a] - e[a]) / l[a], t2 = (hi[a] - e[a]) / l[a];
			tmin = Math.max(tmin, Math.min(t1, t2));
			tmax = Math.min(tmax, Math.max(t1, t2));
		}
		return tmax >= Math.max(tmin, 0) && tmin <= reach;
	}
}
