package net.battlehub.core.ai;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Spleef without Minecraft: the floor an NPC stands on as a grid of cells (snow or hole), the opponents on that
 * floor, and the NPC's reach. It decides which block to dig (the last block holding an opponent up, or where they
 * are about to step, as far ahead as the level can judge) and where to stand (on solid ground with blocks all
 * round, close enough to dig at someone, not next to someone who can dig under it). It never digs the blocks
 * holding the NPC up, and never a block that would cut it off on a small island.
 */
public final class SpleefPlanner {
	private SpleefPlanner() {}

	/** Cells kept round the NPC after a dig: below this the region it stands on counts as a trap. */
	static final int MIN_REGION = 8;
	private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** The floor: {@code size} by {@code size} cells starting at block (x0, z0); {@code present[i]} is a solid block. */
	public record Floor(int x0, int z0, int size, boolean[] present) {
		public boolean at(int x, int z) {
			int i = x - x0, k = z - z0;
			return i >= 0 && k >= 0 && i < size && k < size && present[k * size + i];
		}

		Floor without(int x, int z) {
			boolean[] copy = present.clone();
			int i = x - x0, k = z - z0;
			if (i >= 0 && k >= 0 && i < size && k < size) copy[k * size + i] = false;
			return new Floor(x0, z0, size, copy);
		}
	}

	/** An opponent on this floor: position and speed (blocks per tick). */
	public record Foe(double x, double z, double vx, double vz) {}

	/** What to do: the block to dig (or null), the cell to go to (or null to stay), and why. */
	public record Plan(int[] dig, int[] stand, String why) {}

	/** The blocks under a player standing at (x, z): every cell their feet (0.6 wide) overlap. */
	static int[][] support(Floor f, double x, double z) {
		int[][] corners = {{(int) Math.floor(x - 0.3), (int) Math.floor(z - 0.3)}, {(int) Math.floor(x + 0.3), (int) Math.floor(z - 0.3)},
				{(int) Math.floor(x - 0.3), (int) Math.floor(z + 0.3)}, {(int) Math.floor(x + 0.3), (int) Math.floor(z + 0.3)}};
		int n = 0;
		int[][] out = new int[4][];
		for (int[] c : corners) {
			if (!f.at(c[0], c[1])) continue;
			boolean dup = false;
			for (int i = 0; i < n; i++) dup |= out[i][0] == c[0] && out[i][1] == c[1];
			if (!dup) out[n++] = c;
		}
		int[][] trimmed = new int[n][];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
	}

	/** How many cells the NPC can walk to from (x, z) on this floor (capped, for speed). */
	static int region(Floor f, int x, int z, int cap) {
		if (!f.at(x, z)) return 0;
		boolean[] seen = new boolean[f.size() * f.size()];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {x, z});
		seen[(z - f.z0()) * f.size() + (x - f.x0())] = true;
		int n = 0;
		while (!q.isEmpty() && n < cap) {
			int[] c = q.poll();
			n++;
			for (int[] d : NEIGHBOURS) {
				int nx = c[0] + d[0], nz = c[1] + d[1];
				if (!f.at(nx, nz)) continue;
				int i = (nz - f.z0()) * f.size() + (nx - f.x0());
				if (seen[i]) continue;
				seen[i] = true;
				q.add(new int[] {nx, nz});
			}
		}
		return n;
	}

	/** Solid cells within two blocks of a cell (how firmly someone stands there). */
	static int firmness(Floor f, int x, int z) {
		int n = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx * dx + dz * dz <= 5 && f.at(x + dx, z + dz)) n++;
			}
		}
		return n;
	}

	public static Plan plan(Floor f, double bx, double bz, List<Foe> foes, double reach, Personality p, Difficulty d) {
		int[][] mine = support(f, bx, bz);
		if (mine.length == 0) return new Plan(null, null, "falling");
		int cx = (int) Math.floor(bx), cz = (int) Math.floor(bz);
		int[] anchor = f.at(cx, cz) ? new int[] {cx, cz} : mine[0];

		// ---- what to dig
		int[] bestDig = null;
		double bestScore = 0;
		String digWhy = null;
		int lead = d.predictTicks + (d.predictTicks > 0 ? 2 : 0);
		for (Foe foe : foes) {
			int[][] under = support(f, foe.x(), foe.z());
			int px = (int) Math.floor(foe.x() + foe.vx() * lead), pz = (int) Math.floor(foe.z() + foe.vz() * lead);
			java.util.List<int[]> options = new java.util.ArrayList<>(java.util.Arrays.asList(under));
			boolean predicted = lead > 0 && (px != (int) Math.floor(foe.x()) || pz != (int) Math.floor(foe.z())) && f.at(px, pz);
			if (predicted) options.add(new int[] {px, pz});
			for (int[] c : options) {
				if (!f.at(c[0], c[1]) || !digSafe(f, c, mine, anchor, bx, bz)) continue;
				double dist = Math.hypot(c[0] + 0.5 - bx, c[1] + 0.5 - bz);
				if (dist > reach) continue;
				boolean last = under.length == 1 && under[0][0] == c[0] && under[0][1] == c[1];
				boolean ahead = predicted && c[0] == px && c[1] == pz;
				// someone running is off their block by the time it is gone: then where they are going counts more
				double speed = Math.hypot(foe.vx(), foe.vz());
				double now = speed < 0.08 ? 1.0 : 0.5;
				double s = ahead ? 0.8 : last ? now : now * 0.45;
				s -= dist * 0.02;
				if (s > bestScore) {
					bestScore = s;
					bestDig = c;
					digWhy = last ? "the last block under them" : ahead ? "where they are going" : "a block under them";
				}
			}
		}

		// ---- where to stand
		double nearest = Double.MAX_VALUE;
		for (Foe foe : foes) nearest = Math.min(nearest, Math.hypot(foe.x() - bx, foe.z() - bz));
		boolean threatened = nearest < 2.5;
		int[] stand = null;
		String standWhy = null;
		if (bestDig == null || threatened || firmness(f, anchor[0], anchor[1]) < 8) {
			double best = Double.NEGATIVE_INFINITY;
			double want = Math.max(2.5, reach - 0.8);
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					int x = anchor[0] + dx, z = anchor[1] + dz;
					if (!f.at(x, z)) continue;
					double s = firmness(f, x, z) * (0.6 + 0.4 * (1 - p.risk()));
					double toFoe = Double.MAX_VALUE;
					for (Foe foe : foes) toFoe = Math.min(toFoe, Math.hypot(foe.x() - (x + 0.5), foe.z() - (z + 0.5)));
					if (toFoe < Double.MAX_VALUE) {
						if (toFoe < 2.2) s -= 8;
						// close enough to dig at them, how much depends on the style
						s -= Math.abs(toFoe - want) * (0.4 + 0.8 * p.aggression());
					}
					s -= Math.hypot(dx, dz) * 0.15;
					if (s > best) {
						best = s;
						stand = new int[] {x, z};
					}
				}
			}
			if (stand != null && stand[0] == anchor[0] && stand[1] == anchor[1]) stand = null;
			standWhy = threatened ? "too close to someone" : bestDig == null ? "nobody in reach" : "standing on thin ground";
		}
		if (bestDig != null && !threatened) return new Plan(bestDig, stand, "dig " + digWhy);
		if (bestDig != null) return new Plan(bestDig, stand, "dig " + digWhy + ", then move: " + standWhy);
		return new Plan(null, stand, standWhy == null ? "hold" : standWhy);
	}

	/** Not under the NPC, not right next to it, and not cutting it off on a small island. */
	static boolean digSafe(Floor f, int[] c, int[][] mine, int[] anchor, double bx, double bz) {
		for (int[] m : mine) {
			if (m[0] == c[0] && m[1] == c[1]) return false;
		}
		if (Math.hypot(c[0] + 0.5 - bx, c[1] + 0.5 - bz) < 1.1) return false;
		Floor after = f.without(c[0], c[1]);
		return region(after, anchor[0], anchor[1], MIN_REGION) >= MIN_REGION;
	}
}
