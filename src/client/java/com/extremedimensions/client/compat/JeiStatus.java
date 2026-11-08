package com.extremedimensions.client.compat;

/**
 * What the JEI plugin did, for the compatibility test (3.35): kept free of JEI's own types, so the test can ask without
 * JEI's classes on its path. The plugin fills it in when JEI calls it.
 */
public final class JeiStatus {
	private JeiStatus() {}

	/** Recipes given to JEI, by category (fuel, barter, Spindle, spoils), and item pages. */
	public static volatile int fuels, trades, spins, spoils, infos;
	/** Opens JEI's recipe screen on the mod's own categories (set once JEI's runtime is up). */
	public static volatile Runnable show;

	/** Opens the mod's categories in JEI's recipe screen; false when JEI's runtime is not up yet. */
	public static boolean show() {
		Runnable r = show;
		if (r == null) {
			return false;
		}
		r.run();
		return true;
	}
}
