package net.battlehub.core;

/**
 * How a floor runs through its colour gradient: each pattern gives every block a place on the gradient between 0
 * (one end) and 1 (the other). Picked at random per arena, so no two arenas look alike.
 */
public enum FloorPattern {
	/** Soft clouds of colour. */
	NOISE,
	/** From the middle out to the edge. */
	RADIAL,
	/** Straight bands across the arena, slightly wavy. */
	BANDS,
	/** Patches like paving or a patchwork. */
	PATCHES,
	/** Rings round the middle. */
	RINGS,
	/** Veins like marble. */
	MARBLE;

	/**
	 * The place of block (x, z) on the gradient. {@code radius}: size of the arena; {@code angle}: direction of
	 * bands (radians); {@code scale}: size of the shapes in blocks.
	 */
	public double t(Noise n, int x, int z, int radius, double angle, double scale) {
		double r = Math.max(1, radius);
		double wobble = n.at(x, z, scale, 2) - 0.5;
		double v = switch (this) {
			case NOISE -> n.at(x, z, scale, 3);
			case RADIAL -> Math.sqrt(x * x + z * z) / r + wobble * 0.35;
			case BANDS -> tri((x * Math.cos(angle) + z * Math.sin(angle)) / (scale * 1.6) + wobble * 0.6);
			case PATCHES -> patch(n, x, z, scale);
			case RINGS -> tri(Math.sqrt(x * x + z * z) / (scale * 0.9) + wobble * 0.5);
			case MARBLE -> tri((x * Math.cos(angle) + z * Math.sin(angle)) / scale + n.at(x, z, scale * 0.7, 3) * 2.5);
		};
		return clamp(v);
	}

	/** A triangle wave: 0 → 1 → 0 every whole unit, so repeating bands have no hard edge. */
	static double tri(double v) {
		double f = v - Math.floor(v);
		return 1 - Math.abs(2 * f - 1);
	}

	/** The value of the nearest of a scatter of points (cells of roughly {@code scale} blocks). */
	private static double patch(Noise n, int x, int z, double scale) {
		double cell = Math.max(2, scale * 0.6);
		int cx = (int) Math.floor(x / cell);
		int cz = (int) Math.floor(z / cell);
		double best = Double.MAX_VALUE;
		double value = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int gx = cx + dx;
				int gz = cz + dz;
				double px = (gx + n.hash(gx, gz)) * cell;
				double pz = (gz + n.hash(gz + 101, gx - 57)) * cell;
				double d = (px - x) * (px - x) + (pz - z) * (pz - z);
				if (d < best) {
					best = d;
					value = n.hash(gx * 7 + 3, gz * 13 - 5);
				}
			}
		}
		return value;
	}

	public static double clamp(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/**
	 * The step of a gradient of {@code steps} blocks for a place {@code t}, with a little dithering ({@code jitter}
	 * 0..1 per block, {@code amount} how much) so the borders between steps fray like a real fade.
	 */
	public static int step(double t, int steps, double jitter, double amount) {
		if (steps <= 1) {
			return 0;
		}
		double v = clamp(t + (jitter - 0.5) * amount);
		return (int) Math.min(steps - 1, Math.floor(v * steps));
	}
}
