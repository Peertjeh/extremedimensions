package net.battlehub.core.ml;

/**
 * The model's observation from a {@link CombatState}: the same formulas as ml/pvp_sim/obs.py, checked number for number
 * against the golden file the simulation writes (CombatObservationTest). Each value is (raw / scale), clipped.
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
		double chestDy = (s.oppY + spec.chestHeight) - (s.selfY + spec.eyeHeight);
		double needPitch = -Math.toDegrees(Math.atan2(chestDy, dist));
		double ory = Math.toRadians(s.oppYaw);
		double ofx = -Math.sin(ory), ofz = Math.cos(ory);
		double olx = Math.cos(ory), olz = Math.sin(ory);
		double oFwd = -dx * ofx - dz * ofz;
		double oLeft = -dx * olx - dz * olz;
		double lookSin = near ? 0 : oLeft / dist;
		double lookCos = near ? 1 : oFwd / dist;
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
				case "opp_visible" -> vis ? 1 : 0;
				case "opp_ground" -> vis && s.oppOnGround ? 1 : 0;
				case "opp_sprint" -> vis && s.oppSprinting ? 1 : 0;
				case "opp_since_swing" -> vis ? s.oppSinceSwing : spec.obsScale[k];
				case "opp_hurt" -> vis ? s.oppHurtTicks : 0;
				case "opp_health" -> vis ? s.oppHealth / Math.max(1e-9, s.oppMaxHealth) : 1;
				default -> ray(n, s);
			};
		}
		return out;
	}

	private static double ray(String name, CombatState s) {
		if (name.startsWith("edge_")) return s.edges[Integer.parseInt(name.substring(5))];
		if (name.startsWith("wall_")) return s.walls[Integer.parseInt(name.substring(5))];
		throw new IllegalArgumentException("the spec asks for an observation this mod does not know: " + name);
	}

	/** The look ray from the eyes hits the opponent's hitbox within reach (or starts inside it). */
	public static boolean crosshair(CombatSpec spec, CombatState s) {
		double ry = Math.toRadians(s.selfYaw), rp = Math.toRadians(s.selfPitch);
		double[] l = {-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp)};
		double[] e = {s.selfX, s.selfY + spec.eyeHeight, s.selfZ};
		double hw = spec.hitboxWidth / 2;
		double[] lo = {s.oppX - hw, s.oppY, s.oppZ - hw};
		double[] hi = {s.oppX + hw, s.oppY + spec.hitboxHeight, s.oppZ + hw};
		return rayHitsBox(e, l, lo, hi, spec.reach);
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
