package net.battlehub.core;

/**
 * How a balloon floats: it wants to be over its owner's right hand, a string's length up, swaying a little; it
 * follows late (so it trails when you run) and never further than the string allows.
 */
public final class Balloons {
	private Balloons() {}

	/** The string's length, and how much of the way to its spot the balloon goes each tick. */
	public static final double STRING = 2.2, FOLLOW = 0.18;

	/** Where the hand is: a little to the right of the feet and up (Minecraft yaw, degrees). */
	public static double[] hand(double x, double y, double z, float bodyYaw) {
		double yaw = Math.toRadians(bodyYaw);
		return new double[] {x - Math.cos(yaw) * 0.45, y + 0.9, z - Math.sin(yaw) * 0.45};
	}

	/** The balloon's next place from where it is ({@code at}) and the hand. */
	public static double[] step(double[] at, double[] hand, long tick) {
		double sway = 0.25 * Math.sin(tick * 0.05), swayZ = 0.2 * Math.cos(tick * 0.037);
		double tx = hand[0] + sway, ty = hand[1] + STRING - 0.15 + 0.08 * Math.sin(tick * 0.09), tz = hand[2] + swayZ;
		double nx = at[0] + (tx - at[0]) * FOLLOW, ny = at[1] + (ty - at[1]) * FOLLOW, nz = at[2] + (tz - at[2]) * FOLLOW;
		double dx = nx - hand[0], dy = ny - hand[1], dz = nz - hand[2];
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len > STRING) {
			double k = STRING / len;
			nx = hand[0] + dx * k;
			ny = hand[1] + dy * k;
			nz = hand[2] + dz * k;
		}
		return new double[] {nx, ny, nz};
	}
}
