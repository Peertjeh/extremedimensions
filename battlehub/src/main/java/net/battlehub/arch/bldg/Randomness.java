package net.battlehub.arch.bldg;

/**
 * How adventurous the choices are: conservative stays close to each style's typical proportions, chaotic tries
 * unusual combinations and extremes (but geometry and collision rules still hold, so chaotic is never broken).
 */
public enum Randomness {
	CONSERVATIVE(0.4), BALANCED(1.0), EXPERIMENTAL(1.6), CHAOTIC(2.4);

	/** Spread multiplier for random ranges. */
	public final double spread;

	Randomness(double spread) {
		this.spread = spread;
	}
}
