package net.battlehub.core;

/**
 * Smooth random noise for arena floors: value noise on a grid with smooth interpolation, a few octaves added up.
 * The same seed always gives the same pattern; values lie between 0 and 1.
 */
public final class Noise {
	private final long seed;

	public Noise(long seed) {
		this.seed = seed;
	}

	private double lattice(int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}

	private static double smooth(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	/** One octave at this point (grid spacing 1). */
	public double value(double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fz = smooth(z - z0);
		double a = lattice(x0, z0);
		double b = lattice(x0 + 1, z0);
		double c = lattice(x0, z0 + 1);
		double d = lattice(x0 + 1, z0 + 1);
		double top = a + (b - a) * fx;
		double bottom = c + (d - c) * fx;
		return top + (bottom - top) * fz;
	}

	/** Several octaves: big shapes ({@code scale} blocks across) with finer detail on top. */
	public double at(double x, double z, double scale, int octaves) {
		double sum = 0;
		double weight = 0;
		double amp = 1;
		double freq = 1 / Math.max(1e-6, scale);
		for (int i = 0; i < Math.max(1, octaves); i++) {
			sum += value(x * freq + i * 17.3, z * freq - i * 9.1) * amp;
			weight += amp;
			amp *= 0.5;
			freq *= 2;
		}
		return sum / weight;
	}

	/** A random number for one block (0..1), the same every time for the same block. */
	public double hash(int x, int z) {
		return lattice(x * 31 + 7, z * 17 - 3);
	}
}
