package net.battlehub.core.ml;

/**
 * The terrain part of spec v2's observation, the same as ml/pvp_sim2/terrain.py: a column's ground as the bot finds it
 * ({@link Columns}), sampled every {@code raySample} blocks along a line up to {@code rayMax}:
 * <ul>
 * <li>void: the column has no ground (none within {@code depth} below the feet);</li>
 * <li>drop: no ground, or more than {@code drop} below the feet;</li>
 * <li>step: more than {@code stepUp} above the feet (a jump is needed);</li>
 * <li>wall: more than {@code jumpOver} above the feet (no jumping over it).</li>
 * </ul>
 * A distance is that of the last sample before the condition (0 when the first meets it, rayMax when none does).
 * Directions are the bot's own: 0 ahead, then every 45 degrees to the left. Checked against the golden file.
 */
public final class TerrainRays {
	private TerrainRays() {}

	/** The ground of the world as the rays see it. */
	public interface Columns {
		/**
		 * The top of the ground in the column under (x, z) for feet at y: the highest solid block from the spec's
		 * scan_from above the feet down to its depth below them; NaN when there is none.
		 */
		double ground(double x, double z, double y);
	}

	/** void, drop, step and wall distances, each double[8]. */
	public static double[][] rays(CombatSpec spec, Columns c, double x, double y, double z, double yaw) {
		double ry = Math.toRadians(yaw);
		double fx = -Math.sin(ry), fz = Math.cos(ry), lx = Math.cos(ry), lz = Math.sin(ry);
		int n = spec.rayDirections;
		double[][] out = new double[4][n];
		int count = (int) Math.round(spec.rayMax / spec.raySample);
		for (int k = 0; k < n; k++) {
			double a = Math.toRadians(k * 360.0 / n);
			double ca = Math.cos(a), sa = Math.sin(a);
			double dx = fx * ca + lx * sa, dz = fz * ca + lz * sa;
			double voidAt = spec.rayMax, dropAt = spec.rayMax, stepAt = spec.rayMax, wallAt = spec.rayMax;
			for (int i = 0; i < count; i++) {
				double t = spec.raySample * (i + 1);
				double g = top(spec, c, x + dx * t, z + dz * t, y);
				double d = i * spec.raySample;
				boolean none = Double.isNaN(g);
				if (none && voidAt == spec.rayMax) voidAt = d;
				if ((none || g < y - spec.drop) && dropAt == spec.rayMax) dropAt = d;
				if (!none && g > y + spec.stepUp && stepAt == spec.rayMax) stepAt = d;
				if (!none && g > y + spec.jumpOver && wallAt == spec.rayMax) wallAt = d;
			}
			out[0][k] = voidAt;
			out[1][k] = dropAt;
			out[2][k] = stepAt;
			out[3][k] = wallAt;
		}
		return out;
	}

	/** From a body at x, y, z along the horizontal unit direction (dx, dz): ground before a drop, and before the void. */
	public static double[] behind(CombatSpec spec, Columns c, double x, double y, double z, double dx, double dz) {
		int count = (int) Math.round(spec.rayMax / spec.raySample);
		double dropAt = spec.rayMax, voidAt = spec.rayMax;
		for (int i = 0; i < count; i++) {
			double t = spec.raySample * (i + 1);
			double g = top(spec, c, x + dx * t, z + dz * t, y);
			boolean none = Double.isNaN(g);
			if ((none || g < y - spec.drop) && dropAt == spec.rayMax) dropAt = i * spec.raySample;
			if (none && voidAt == spec.rayMax) voidAt = i * spec.raySample;
		}
		return new double[] {dropAt, voidAt};
	}

	/** How far the ground is below the feet (the spec's depth when there is none within it). */
	public static double below(CombatSpec spec, Columns c, double x, double y, double z) {
		double g = top(spec, c, x, z, y);
		return Double.isNaN(g) ? spec.depth : Math.max(0.0, Math.min(spec.depth, y - g));
	}

	/** The column's ground, NaN when it is more than the spec's depth below the feet. */
	static double top(CombatSpec spec, Columns c, double x, double z, double y) {
		double g = c.ground(x, z, y);
		return g >= y - spec.depth ? g : Double.NaN;
	}

	/** A heightmap as columns (the simulation's maps; for tests): column i covers x in [i - size/2, i - size/2 + 1). */
	public static Columns heightmap(double[][] ground) {
		int size = ground.length;
		return (x, z, y) -> {
			int i = (int) Math.floor(x + size / 2), j = (int) Math.floor(z + size / 2);
			if (i < 0 || j < 0 || i >= size || j >= size) return Double.NaN;
			return ground[i][j];
		};
	}
}
