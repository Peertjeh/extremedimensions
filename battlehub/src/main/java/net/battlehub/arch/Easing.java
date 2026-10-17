package net.battlehub.arch;

/** How a gradient moves from 0 to 1: straight, softly (smoothstep), slowly at first, or in custom curves. */
public enum Easing {
	LINEAR, SMOOTH, SMOOTHER, QUAD_IN, QUAD_OUT, CUBIC_IN, CUBIC_OUT;

	public double apply(double t) {
		t = Math.max(0, Math.min(1, t));
		return switch (this) {
			case LINEAR -> t;
			case SMOOTH -> t * t * (3 - 2 * t);
			case SMOOTHER -> t * t * t * (t * (t * 6 - 15) + 10);
			case QUAD_IN -> t * t;
			case QUAD_OUT -> 1 - (1 - t) * (1 - t);
			case CUBIC_IN -> t * t * t;
			case CUBIC_OUT -> 1 - Math.pow(1 - t, 3);
		};
	}

	public static double clamp(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/** (v - lo) / (hi - lo), clamped to 0..1. */
	public static double norm(double v, double lo, double hi) {
		return hi == lo ? 0 : clamp((v - lo) / (hi - lo));
	}
}
