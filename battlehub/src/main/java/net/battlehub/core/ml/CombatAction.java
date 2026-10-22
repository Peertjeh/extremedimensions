package net.battlehub.core.ml;

import java.util.Random;

/**
 * The model's action: one choice per head (forward, strafe, jump, sprint, attack, yaw, pitch), picked from its logits
 * and decoded to the values of the spec, as ml/pvp_sim/actions.py does.
 */
public record CombatAction(int forward, int strafe, boolean jump, boolean sprint, boolean attack, double yawLeft, double pitchDown) {

	public static final CombatAction NOTHING = new CombatAction(0, 0, false, false, false, 0, 0);

	/** The values of the chosen index of each head. */
	public static CombatAction decode(CombatSpec spec, int[] idx) {
		double[] v = new double[spec.actionValues.length];
		for (int h = 0; h < v.length; h++) {
			double[] values = spec.actionValues[h];
			v[h] = values[Math.max(0, Math.min(values.length - 1, idx[h]))];
		}
		return new CombatAction((int) Math.round(v[head(spec, "forward")]), (int) Math.round(v[head(spec, "strafe")]),
				v[head(spec, "jump")] > 0.5, v[head(spec, "sprint")] > 0.5, v[head(spec, "attack")] > 0.5, v[head(spec, "yaw")], v[head(spec, "pitch")]);
	}

	/** The highest logit of each head (what the model thinks best). */
	public static int[] best(CombatSpec spec, float[] logits) {
		int[] heads = spec.actionHeads();
		int[] out = new int[heads.length];
		int o = 0;
		for (int h = 0; h < heads.length; h++) {
			int arg = 0;
			for (int k = 1; k < heads[h]; k++) {
				if (logits[o + k] > logits[o + arg]) arg = k;
			}
			out[h] = arg;
			o += heads[h];
		}
		return out;
	}

	/** One choice per head drawn from softmax(logits / temperature): less sharp play for the lower levels. */
	public static int[] sample(CombatSpec spec, float[] logits, double temperature, Random rng) {
		int[] heads = spec.actionHeads();
		int[] out = new int[heads.length];
		double t = Math.max(1e-3, temperature);
		int o = 0;
		for (int h = 0; h < heads.length; h++) {
			double max = Double.NEGATIVE_INFINITY;
			for (int k = 0; k < heads[h]; k++) max = Math.max(max, logits[o + k] / t);
			double[] p = new double[heads[h]];
			double sum = 0;
			for (int k = 0; k < heads[h]; k++) sum += p[k] = Math.exp(logits[o + k] / t - max);
			double u = rng.nextDouble() * sum;
			int pick = heads[h] - 1;
			for (int k = 0; k < heads[h]; k++) {
				u -= p[k];
				if (u <= 0) {
					pick = k;
					break;
				}
			}
			out[h] = pick;
			o += heads[h];
		}
		return out;
	}

	private static int head(CombatSpec spec, String name) {
		int k = spec.actionNames.indexOf(name);
		if (k < 0) throw new IllegalArgumentException("the spec has no action " + name);
		return k;
	}
}
