package net.battlehub.arch.geom;

/**
 * A ground plan shape in building coordinates: which columns it covers, and how far a column is from its edge
 * (so walls get a thickness and towers taper). Rectangles, octagons, circles and their unions.
 */
public interface Footprint {
	boolean contains(int x, int z);

	int minX();

	int minZ();

	int maxX();

	int maxZ();

	/** On the boundary: inside, with a side neighbour outside. */
	default boolean edge(int x, int z) {
		return contains(x, z) && (!contains(x + 1, z) || !contains(x - 1, z) || !contains(x, z + 1) || !contains(x, z - 1));
	}

	/** Within {@code t} steps of the boundary (a wall {@code t} thick). */
	default boolean shell(int x, int z, int t) {
		if (!contains(x, z)) return false;
		for (int d = 1; d <= t; d++) {
			if (!contains(x + d, z) || !contains(x - d, z) || !contains(x, z + d) || !contains(x, z - d)) return true;
		}
		return false;
	}

	default int width() {
		return maxX() - minX() + 1;
	}

	default int depth() {
		return maxZ() - minZ() + 1;
	}

	default double centerX() {
		return (minX() + maxX()) / 2.0;
	}

	default double centerZ() {
		return (minZ() + maxZ()) / 2.0;
	}

	/** Grown or shrunk by {@code d} blocks on every side (same kind of shape). */
	Footprint inset(int d);

	static Footprint rect(int x0, int z0, int x1, int z1) {
		return new Rect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), 0);
	}

	/** A rectangle with its corners cut at 45 degrees ({@code cut} blocks). */
	static Footprint octagon(int x0, int z0, int x1, int z1, int cut) {
		return new Rect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), cut);
	}

	/** A regular octagon around a centre ("radius" to the flat sides). */
	static Footprint octagon(int cx, int cz, int r) {
		int cut = (int) Math.round(r * 0.83);
		return new Rect(cx - r, cz - r, cx + r, cz + r, cut);
	}

	static Footprint circle(double cx, double cz, double r) {
		return new Circle(cx, cz, r);
	}

	/** Part of another footprint: the columns where {@code keep} says yes. */
	static Footprint clip(Footprint base, java.util.function.BiPredicate<Integer, Integer> keep) {
		return new Clip(base, keep);
	}

	/** Two footprints together. */
	static Footprint union(Footprint a, Footprint b) {
		return new Union(a, b);
	}

	record Clip(Footprint base, java.util.function.BiPredicate<Integer, Integer> keep) implements Footprint {
		@Override
		public boolean contains(int x, int z) {
			return base.contains(x, z) && keep.test(x, z);
		}

		@Override
		public int minX() {
			return base.minX();
		}

		@Override
		public int minZ() {
			return base.minZ();
		}

		@Override
		public int maxX() {
			return base.maxX();
		}

		@Override
		public int maxZ() {
			return base.maxZ();
		}

		@Override
		public Footprint inset(int d) {
			return new Clip(base.inset(d), keep);
		}
	}

	record Union(Footprint a, Footprint b) implements Footprint {
		@Override
		public boolean contains(int x, int z) {
			return a.contains(x, z) || b.contains(x, z);
		}

		@Override
		public int minX() {
			return Math.min(a.minX(), b.minX());
		}

		@Override
		public int minZ() {
			return Math.min(a.minZ(), b.minZ());
		}

		@Override
		public int maxX() {
			return Math.max(a.maxX(), b.maxX());
		}

		@Override
		public int maxZ() {
			return Math.max(a.maxZ(), b.maxZ());
		}

		@Override
		public Footprint inset(int d) {
			return new Union(a.inset(d), b.inset(d));
		}
	}

	record Rect(int minX, int minZ, int maxX, int maxZ, int cut) implements Footprint {
		@Override
		public boolean contains(int x, int z) {
			if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
			if (cut <= 0) return true;
			int dx = Math.min(x - minX, maxX - x);
			int dz = Math.min(z - minZ, maxZ - z);
			return dx + dz >= cut;
		}

		@Override
		public Footprint inset(int d) {
			if (maxX - minX < 2 * d || maxZ - minZ < 2 * d) return new Rect(minX + d, minZ + d, minX + d, minZ + d, 0);
			int c = cut <= 0 ? 0 : Math.max(1, cut - (int) Math.round(d * 0.41));
			return new Rect(minX + d, minZ + d, maxX - d, maxZ - d, c);
		}
	}

	record Circle(double cx, double cz, double r) implements Footprint {
		@Override
		public boolean contains(int x, int z) {
			double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5);
			return dx * dx + dz * dz <= r * r + 0.35;
		}

		@Override
		public int minX() {
			return (int) Math.floor(cx - r);
		}

		@Override
		public int minZ() {
			return (int) Math.floor(cz - r);
		}

		@Override
		public int maxX() {
			return (int) Math.ceil(cx + r);
		}

		@Override
		public int maxZ() {
			return (int) Math.ceil(cz + r);
		}

		@Override
		public Footprint inset(int d) {
			return new Circle(cx, cz, Math.max(0.5, r - d));
		}
	}
}
