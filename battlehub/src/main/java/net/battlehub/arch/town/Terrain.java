package net.battlehub.arch.town;

import net.battlehub.arch.Noise3;

/**
 * The land of a floating island as a height field: the surface height of every column inside an irregular
 * coastline, and how deep the rock goes underneath. Building pads and roads level it (cut and fill); what is
 * left is emitted as a shell (surface, soil and the underside), never as a solid lump.
 */
public final class Terrain {
	public static final int NONE = Integer.MIN_VALUE;

	final int minX, minZ, size;
	final int[] top;
	final int[] bottom;
	/** Columns whose surface is a road or a pad (no grass, no flowers). */
	final boolean[] built;

	public Terrain(int radius) {
		this.minX = -radius;
		this.minZ = -radius;
		this.size = 2 * radius + 1;
		this.top = new int[size * size];
		this.bottom = new int[size * size];
		this.built = new boolean[size * size];
		java.util.Arrays.fill(top, NONE);
	}

	int idx(int x, int z) {
		int ix = x - minX, iz = z - minZ;
		if (ix < 0 || iz < 0 || ix >= size || iz >= size) return -1;
		return iz * size + ix;
	}

	/** The surface height (topmost solid y) of a column, or {@link #NONE} off the island. */
	public int height(int x, int z) {
		int i = idx(x, z);
		return i < 0 ? NONE : top[i];
	}

	public int bottom(int x, int z) {
		int i = idx(x, z);
		return i < 0 ? NONE : bottom[i];
	}

	public boolean land(int x, int z) {
		return height(x, z) != NONE;
	}

	public void set(int x, int z, int h) {
		int i = idx(x, z);
		if (i >= 0 && top[i] != NONE) {
			top[i] = h;
			if (bottom[i] > h - 2) bottom[i] = h - 2;
		}
	}

	public void markBuilt(int x, int z) {
		int i = idx(x, z);
		if (i >= 0) built[i] = true;
	}

	public boolean built(int x, int z) {
		int i = idx(x, z);
		return i >= 0 && built[i];
	}

	/**
	 * Shapes the island: a coastline wobbling round {@code radius}, a flat heart for the plaza, gentle hills
	 * further out, raised ground where {@code lift} says so, and a deep rocky underside that tapers to the edge.
	 */
	public static Terrain island(long seed, int radius, int flat, Lift lift) {
		Terrain t = new Terrain(radius + 2);
		Noise3 coast = new Noise3(seed ^ 0xC0A57L);
		Noise3 hills = new Noise3(seed ^ 0x4111L);
		Noise3 under = new Noise3(seed ^ 0xDEE9L);
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				double d = Math.hypot(x, z);
				double a = Math.atan2(z, x);
				double edge = radius * (0.86 + 0.14 * coast.at(Math.cos(a) * 3, 0, Math.sin(a) * 3, 1.3, 3));
				if (d > edge) continue;
				double rise = Math.max(0, Math.min(1, (d - flat) / 40.0));
				double hill = (hills.at(x, 0, z, 38, 3) - 0.5) * 14 * rise + rise * 3;
				double up = lift.at(x, z);
				// a soft fall towards the coast
				double coastDrop = Math.max(0, (d - (edge - 12)) / 12.0);
				int h = (int) Math.round(Math.max(hill, up) - coastDrop * coastDrop * 4);
				if (d < flat) h = 0;
				int depth = (int) Math.round(6 + (edge - d) * 0.45 + under.at(x, 7, z, 9, 2) * 14);
				int i = t.idx(x, z);
				t.top[i] = h;
				t.bottom[i] = h - Math.max(3, depth);
			}
		}
		return t;
	}

	/** Extra height wanted at a column (hills under the castle, terraces for landmarks). */
	public interface Lift {
		double at(int x, int z);
	}

	/** Levels a rectangle to height h (cut and fill), with a sloped border of {@code margin} blocks. */
	public void level(int x0, int z0, int x1, int z1, int h, int margin) {
		for (int x = x0 - margin; x <= x1 + margin; x++) {
			for (int z = z0 - margin; z <= z1 + margin; z++) {
				if (!land(x, z)) continue;
				int dx = Math.max(0, Math.max(x0 - x, x - x1));
				int dz = Math.max(0, Math.max(z0 - z, z - z1));
				int dist = Math.max(dx, dz);
				if (dist == 0) {
					set(x, z, h);
					markBuilt(x, z);
				} else {
					double f = (double) dist / (margin + 1);
					int cur = height(x, z);
					set(x, z, (int) Math.round(h + (cur - h) * f));
				}
			}
		}
	}
}
