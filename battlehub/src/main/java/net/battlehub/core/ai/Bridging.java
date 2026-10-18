package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Building across a gap, the way a player bridges: the floor cells from the NPC towards where it wants to go, in
 * steps along x or z only (a block can only be put against the face of another), and the first cell on that line
 * without floor, together with the floor cell it is built against. The world layer checks that the NPC can reach
 * and see that face, sneaks to the edge and places the block through the normal rules.
 */
public final class Bridging {
	private Bridging() {}

	/** A floor cell. */
	public interface Floor {
		/** Solid floor here (something to stand on, no deadly drop). */
		boolean solid(int x, int z);

		/** A block may be built here (inside the game's build area, not protected). */
		boolean buildable(int x, int z);
	}

	/** The next block: put it at (x, z) against the face of (fromX, fromZ); {@code index}: how far down the line. */
	public record Step(int x, int z, int fromX, int fromZ, int index) {
		public int dx() {
			return Integer.signum(x - fromX);
		}

		public int dz() {
			return Integer.signum(z - fromZ);
		}
	}

	/**
	 * Cells from (x0, z0) to (x1, z1), both included, each one step along x or z from the last: the cells the straight
	 * line passes through, in order (on a corner, x first).
	 */
	public static List<int[]> line(int x0, int z0, int x1, int z1, int max) {
		List<int[]> out = new ArrayList<>();
		int nx = Math.abs(x1 - x0), nz = Math.abs(z1 - z0);
		int sx = Integer.signum(x1 - x0), sz = Integer.signum(z1 - z0);
		int x = x0, z = z0;
		out.add(new int[] {x, z});
		for (int ix = 0, iz = 0; (ix < nx || iz < nz) && out.size() < max;) {
			// step along the axis whose next cell border the line crosses first
			boolean stepX = iz >= nz || (ix < nx && (0.5 + ix) * nz <= (0.5 + iz) * nx);
			if (stepX) {
				x += sx;
				ix++;
			} else {
				z += sz;
				iz++;
			}
			out.add(new int[] {x, z});
		}
		return out;
	}

	/**
	 * The first cell on the line without floor (after the NPC's own cell), built against the cell before it, or null
	 * when the floor is whole as far as the line goes, or the gap cannot be built (outside the build area).
	 */
	public static Step next(List<int[]> line, Floor floor) {
		for (int i = 1; i < line.size(); i++) {
			int[] c = line.get(i);
			if (floor.solid(c[0], c[1])) continue;
			int[] from = line.get(i - 1);
			if (!floor.solid(from[0], from[1]) || !floor.buildable(c[0], c[1])) return null;
			return new Step(c[0], c[1], from[0], from[1], i);
		}
		return null;
	}
}
