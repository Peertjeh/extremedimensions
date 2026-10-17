package net.battlehub.arch.geom;

/**
 * Arch profiles as voxel steps: for an opening {@code width} blocks wide, how many blocks tall each column of the
 * opening is above the springing line. Worked out from the arch's real geometry (circles through the springing
 * points), so every arch of a kind has the same profile at any size.
 */
public enum ArchCurve {
	/** A half circle. */
	ROUND,
	/** Flatter than a half circle: a slice of a bigger circle. */
	SEGMENTAL,
	/** Gothic equilateral arch: two arcs, each centred on the opposite springing point. */
	POINTED,
	/** Sharper than equilateral: centres outside the span. */
	LANCET,
	/** Tudor-like: low and wide with a slight point. */
	FOUR_CENTERED,
	/** A straight lintel. */
	FLAT;

	/**
	 * Column heights of the opening above the springing line (index 0 is the leftmost column); the top of the
	 * opening in column i is at springY + h[i] - 1, so the arch ring starts at springY + h[i].
	 */
	public int[] heights(int width) {
		int[] h = new int[width];
		double half = width / 2.0;
		for (int i = 0; i < width; i++) {
			// distance from the springing point on the near side, in block centres
			double x = i + 0.5;
			double fromLeft = x;
			double fromRight = width - x;
			double near = Math.min(fromLeft, fromRight);
			double v = switch (this) {
				case ROUND -> {
					double d = half - near;
					yield Math.sqrt(Math.max(0, half * half - d * d));
				}
				case SEGMENTAL -> {
					double r = half * 1.6;
					double c = Math.sqrt(r * r - half * half);
					double d = half - near;
					yield Math.sqrt(Math.max(0, r * r - d * d)) - c;
				}
				case POINTED -> {
					// centre at the far springing point, radius = span
					double r = width;
					double far = width - near;
					yield Math.sqrt(Math.max(0, r * r - far * far));
				}
				case LANCET -> {
					double r = width * 1.5;
					double c = r - width;
					double far = width - near + c;
					yield Math.sqrt(Math.max(0, r * r - far * far));
				}
				case FOUR_CENTERED -> {
					double r1 = half * 0.55;
					if (near < r1) yield Math.sqrt(Math.max(0, r1 * r1 - (r1 - near) * (r1 - near)));
					yield r1 + (near - r1) * 0.45;
				}
				case FLAT -> 0;
			};
			h[i] = (int) Math.round(v + 0.25);
		}
		return h;
	}

	/** How tall the arch rises above its springing line at the crown. */
	public int rise(int width) {
		int m = 0;
		for (int v : heights(width)) m = Math.max(m, v);
		return m;
	}
}
