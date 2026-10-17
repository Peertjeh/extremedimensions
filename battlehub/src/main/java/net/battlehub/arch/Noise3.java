package net.battlehub.arch;

/**
 * Smooth value noise in three dimensions (0..1), with octaves: the same seed always gives the same field. Used
 * to break up gradients into patches, never directly as a block-by-block coin toss.
 */
public final class Noise3 {
	private final long seed;

	public Noise3(long seed) {
		this.seed = seed;
	}

	private double lattice(int x, int y, int z) {
		return Rng.hash(seed, x, y, z);
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	public double value(double x, double y, double z) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
		double fx = fade(x - x0), fy = fade(y - y0), fz = fade(z - z0);
		double c000 = lattice(x0, y0, z0), c100 = lattice(x0 + 1, y0, z0);
		double c010 = lattice(x0, y0 + 1, z0), c110 = lattice(x0 + 1, y0 + 1, z0);
		double c001 = lattice(x0, y0, z0 + 1), c101 = lattice(x0 + 1, y0, z0 + 1);
		double c011 = lattice(x0, y0 + 1, z0 + 1), c111 = lattice(x0 + 1, y0 + 1, z0 + 1);
		double a = c000 + (c100 - c000) * fx;
		double b = c010 + (c110 - c010) * fx;
		double c = c001 + (c101 - c001) * fx;
		double d = c011 + (c111 - c011) * fx;
		double e = a + (b - a) * fy;
		double f = c + (d - c) * fy;
		return e + (f - e) * fz;
	}

	/** Octaves added up: features about {@code scale} blocks across with finer detail on top. */
	public double at(double x, double y, double z, double scale, int octaves) {
		double sum = 0, weight = 0, amp = 1, freq = 1 / Math.max(1e-6, scale);
		for (int i = 0; i < Math.max(1, octaves); i++) {
			sum += value(x * freq + i * 31.7, y * freq - i * 11.3, z * freq + i * 5.9) * amp;
			weight += amp;
			amp *= 0.5;
			freq *= 2;
		}
		return sum / weight;
	}

	/**
	 * Noise spread out to cover 0..1 about evenly (octave sums bunch up round 0.5), for thresholds: "remove where
	 * below 0.3" then removes about 30%.
	 */
	public double even(double x, double y, double z, double scale, int octaves) {
		double v = (at(x, y, z, scale, octaves) - 0.5) * (octaves > 1 ? 3.0 : 2.4) + 0.5;
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/** Signed noise, -1..1, roughly centred on 0. */
	public double signed(double x, double y, double z, double scale, int octaves) {
		return (at(x, y, z, scale, octaves) - 0.5) * 2;
	}
}
