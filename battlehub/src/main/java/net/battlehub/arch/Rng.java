package net.battlehub.arch;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Seeded randomness in separate named streams ("massing", "materials", "decor", "weathering"...), so changing how
 * much decoration a building gets never moves its walls. The same seed and names give the same numbers.
 */
public final class Rng {
	private final long seed;
	private final SplittableRandom r;

	public Rng(long seed) {
		this.seed = seed;
		this.r = new SplittableRandom(seed);
	}

	public long seed() {
		return seed;
	}

	/** A stream of its own, derived from this seed and a name (independent of how much this one was used). */
	public Rng stream(String name) {
		return new Rng(mix(seed ^ (name.hashCode() * 0x9E3779B97F4A7C15L) ^ 0x632BE59BD9B4E019L));
	}

	public Rng stream(String name, int index) {
		return new Rng(mix(stream(name).seed + index * 0xD1B54A32D192ED03L));
	}

	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** A hash of a position to 0..1 (no state). */
	public static double hash(long seed, int x, int y, int z) {
		long h = mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L));
		return (h >>> 11) * 0x1.0p-53;
	}

	public double next() {
		return r.nextDouble();
	}

	public int nextInt(int bound) {
		return r.nextInt(bound);
	}

	/** Between lo and hi, both included. */
	public int range(int lo, int hi) {
		return hi <= lo ? lo : lo + r.nextInt(hi - lo + 1);
	}

	public double range(double lo, double hi) {
		return lo + r.nextDouble() * (hi - lo);
	}

	public boolean chance(double p) {
		return r.nextDouble() < p;
	}

	public <T> T pick(List<T> list) {
		return list.get(r.nextInt(list.size()));
	}

	@SafeVarargs
	public final <T> T pick(T... list) {
		return list[r.nextInt(list.length)];
	}

	/** One of {@code items} by weight. */
	public <T> T weighted(List<T> items, java.util.function.ToDoubleFunction<T> weight) {
		double total = 0;
		for (T t : items) total += Math.max(0, weight.applyAsDouble(t));
		if (total <= 0) return items.isEmpty() ? null : items.get(0);
		double x = r.nextDouble() * total;
		for (T t : items) {
			x -= Math.max(0, weight.applyAsDouble(t));
			if (x < 0) return t;
		}
		return items.get(items.size() - 1);
	}
}
