package com.extremedimensions.reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.extremedimensions.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The design of an Unfinished Anchor (3.39): what the Endermen of the Pale Reach build towards a way home, out over the
 * void between the far islands, and never finish. A plinth of end stone hangs under a round floor with a ring of end
 * stone bricks; at its heart the fitting (a lodestone) stands between four golden posts. Six pillars of purpur and pale
 * stone rise round it to broken tops, half-built arches between them; high over the fitting stands a halo of end stone
 * bricks with golden spokes, not yet closed, chains hanging from it, and loose blocks hang in the air all round, as if
 * set down a moment ago. Two barrels hold what the builders put by, a third sometimes sits on the tallest pillar.
 *
 * <p>Finished (3.44, see {@link AnchorRites}), the same anchor stands whole: its six pillars as tall as the tallest and
 * crowned with gold and light like the fitting's posts, an arch closed between every two of them, the halo a full ring
 * golden at every spoke, and nothing hanging loose.
 *
 * <p>Everything here is worked out from the anchor's seed and its floor's middle, so the same anchor is designed the
 * same way again after a restart.
 */
public final class PaleAnchorDesign {
	private PaleAnchorDesign() {}

	/** The plinth's and the floor's radius, and how deep the plinth hangs. */
	static final int R = 9, DEPTH = 13;
	/** How high over the floor the halo's middle is, and its radius. */
	static final int HALO_Y = 24, HALO_R = 7;

	/** A design: blocks by position, the fitting and the barrels to fill once it stands. */
	public static final class Blueprint {
		final LinkedHashMap<Long, BlockState> blocks = new LinkedHashMap<>();
		public BlockPos fitting;
		public final List<BlockPos> caches = new ArrayList<>();
		/** The halo's middle, and the way its plane runs (an angle round from east towards south). */
		public BlockPos halo;
		public double plane;
		/** The middle of the gap the halo was left open at (an angle round its ring). */
		double gapMid;
		/** The blocks of the halo's ring and the rods out of its spokes. */
		final Set<Long> ring = new HashSet<>();
		/** The chains hanging from the halo, and the blocks at their ends. */
		final Set<Long> chains = new HashSet<>();
		/** The feet of the six pillars' cores. */
		final List<BlockPos> pillars = new ArrayList<>();
		/** Where loose blocks hang round the anchor unfinished (finished, none does). */
		final List<BlockPos> loose = new ArrayList<>();

		void set(int x, int y, int z, BlockState s) {
			blocks.put(BlockPos.asLong(x, y, z), s);
		}

		void soft(int x, int y, int z, BlockState s) {
			blocks.putIfAbsent(BlockPos.asLong(x, y, z), s);
		}

		boolean has(int x, int y, int z) {
			return blocks.containsKey(BlockPos.asLong(x, y, z));
		}

		/** How many blocks it lays. */
		public int size() {
			return blocks.size();
		}

		/** The block it lays at a spot, or null. */
		public BlockState at(BlockPos pos) {
			return blocks.get(pos.asLong());
		}

		/** Its blocks bottom to top, and from the middle out on each level (the order they fly in). */
		public List<Map.Entry<Long, BlockState>> ordered(BlockPos middle) {
			List<Map.Entry<Long, BlockState>> list = new ArrayList<>(blocks.entrySet());
			list.sort(Comparator.<Map.Entry<Long, BlockState>>comparingInt(e -> BlockPos.getY(e.getKey()))
					.thenComparingDouble(e -> {
						double dx = BlockPos.getX(e.getKey()) - middle.getX(), dz = BlockPos.getZ(e.getKey()) - middle.getZ();
						return dx * dx + dz * dz;
					}));
			return list;
		}

		/** How many of its blocks are of a block (for the tests). */
		public int count(net.minecraft.world.level.block.Block b) {
			int n = 0;
			for (BlockState s : blocks.values()) {
				n += s.is(b) ? 1 : 0;
			}
			return n;
		}

		/** The halo's ring, block by block (for the tests). */
		public List<BlockPos> ring() {
			List<BlockPos> out = new ArrayList<>();
			for (long p : ring) {
				out.add(BlockPos.of(p));
			}
			return out;
		}
	}

	/**
	 * What is seen of an anchor standing in the world whose seed is not known: the turn its pillars stand at, the way
	 * its halo runs (NaN where it could not be told), and how tall its tallest pillar stands (0: not seen).
	 */
	static final class Shape {
		double turn = Double.NaN, plane = Double.NaN;
		int tallest;
	}

	/** A vanilla block by id (block constants come and go between versions). */
	static BlockState mc(String id, BlockState fallback) {
		return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", id)).map(h -> h.value().defaultBlockState()).orElse(fallback);
	}

	/** The side a direction points to most. */
	static Direction nearest(double x, double y, double z) {
		double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
		if (ay >= ax && ay >= az) {
			return y >= 0 ? Direction.UP : Direction.DOWN;
		}
		if (ax >= az) {
			return x >= 0 ? Direction.EAST : Direction.WEST;
		}
		return z >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static BlockState facing(BlockState s, Direction d) {
		return s.hasProperty(BlockStateProperties.FACING) ? s.setValue(BlockStateProperties.FACING, d) : s;
	}

	private static BlockState axis(BlockState s, Direction.Axis a) {
		return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}

	/** A number from 0 to 1 for a spot, the same every time for the same seed. */
	static double unit(long seed, long a, long b, long c) {
		long h = seed ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 32;
		return (h & 0xFFFFFFL) / (double) 0x1000000L;
	}

	/** The design of the anchor whose floor's middle is {@code o}. */
	public static Blueprint design(BlockPos o, long seed) {
		return make(o, seed, false, null);
	}

	/**
	 * The same anchor finished (3.44): pillars whole and crowned, every arch closed, the halo a full ring with all its
	 * spokes, nothing loose. It lays everything the unfinished one does, but for the loose blocks, and more.
	 */
	public static Blueprint whole(BlockPos o, long seed) {
		return make(o, seed, true, null);
	}

	/** An anchor finished whose seed is not known: its pillars and its halo where they are seen to stand. */
	static Blueprint whole(BlockPos o, Shape seen) {
		return make(o, 0L, true, seen);
	}

	/**
	 * The design, unfinished or whole. Both draw the same numbers in the same order, so the finished anchor is the
	 * unfinished one completed; the unfinished one is laid out exactly as it always was.
	 */
	private static Blueprint make(BlockPos o, long seed, boolean whole, Shape seen) {
		Blueprint bp = new Blueprint();
		java.util.Random r = new java.util.Random(seed);
		BlockState endStone = Blocks.END_STONE.defaultBlockState(), bricks = Blocks.END_STONE_BRICKS.defaultBlockState();
		BlockState purpur = Blocks.PURPUR_BLOCK.defaultBlockState(), pillar = axis(Blocks.PURPUR_PILLAR.defaultBlockState(), Direction.Axis.Y);
		BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState(), crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		BlockState pale = ModBlocks.PALE_HUSH_BRICKS.defaultBlockState(), palePol = ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState rod = facing(Blocks.END_ROD.defaultBlockState(), Direction.UP);
		BlockState chain = axis(mc("iron_chain", mc("chain", Blocks.END_ROD.defaultBlockState())), Direction.Axis.Y);
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();

		// The plinth: a rough cone of end stone hanging under the floor, veined with obsidian
		for (int dy = 1; dy <= DEPTH; dy++) {
			double t = dy / (double) DEPTH;
			double rad = R * (1 - Math.pow(t, 1.25)) + 0.6;
			for (int x = -R; x <= R; x++) {
				for (int z = -R; z <= R; z++) {
					double d = Math.sqrt(x * x + z * z) + (unit(seed, x, z, dy) - 0.5) * 1.6;
					if (d <= rad) {
						bp.set(ox + x, oy - dy, oz + z, dy > 1 && unit(seed, x, z, dy + 100) < 0.09 ? obsidian : endStone);
					}
				}
			}
		}

		// The floor: crying obsidian under the fitting, purpur round it, pale stone out to a ring of end stone bricks
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > R - 0.5) {
					continue;
				}
				BlockState s = d <= 1.5 ? crying : d <= 3.5 ? purpur : d >= 6 && d < 7.2 ? bricks : unit(seed, x, z, 7) < 0.25 ? pale : palePol;
				bp.set(ox + x, oy, oz + z, s);
			}
		}
		// The fitting, between four golden posts each crowned with a rod of light
		bp.fitting = new BlockPos(ox, oy + 1, oz);
		bp.set(ox, oy + 1, oz, Blocks.LODESTONE.defaultBlockState());
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				bp.set(ox + 2 * sx, oy + 1, oz + 2 * sz, gold);
				bp.set(ox + 2 * sx, oy + 2, oz + 2 * sz, rod);
			}
		}

		// Six pillars round the floor's edge, broken off at different heights (finished: all as tall as the tallest)
		double turn = r.nextDouble() * Math.PI * 2;
		if (seen != null && !Double.isNaN(seen.turn)) {
			turn = seen.turn;
		}
		int[] heights = new int[6];
		int[][] at = new int[6][2];
		int tallest = 0;
		for (int k = 0; k < 6; k++) {
			double a = turn + k * Math.PI / 3;
			int cx = (int) Math.round(Math.cos(a) * 7.0), cz = (int) Math.round(Math.sin(a) * 7.0);
			at[k][0] = cx;
			at[k][1] = cz;
			int h = 12 + r.nextInt(14);
			heights[k] = h;
			if (h > heights[tallest]) {
				tallest = k;
			}
		}
		int top = Math.max(15, seen != null && seen.tallest > 0 ? seen.tallest : heights[tallest]);
		for (int k = 0; k < 6; k++) {
			int cx = at[k][0], cz = at[k][1], h = whole ? top : heights[k];
			bp.pillars.add(new BlockPos(ox + cx, oy + 1, oz + cz));
			for (int y = 1; y <= h; y++) {
				for (int px = -1; px <= 1; px++) {
					for (int pz = -1; pz <= 1; pz++) {
						boolean core = px == 0 && pz == 0, corner = px != 0 && pz != 0;
						// the top few courses broken away, the core standing longest
						if (!whole && !core && y > h - 3 && unit(seed, k * 7 + px, pz, y) < 0.45 + 0.15 * (y - (h - 3))) {
							continue;
						}
						BlockState s = core ? pillar : whole && y == h ? palePol : corner ? pale : y % 6 == 0 ? palePol : bricks;
						bp.set(ox + cx + px, oy + y, oz + cz + pz, s);
					}
				}
			}
			if (whole) {
				// finished, each is crowned as the fitting's posts are, with gold and a rod of light
				bp.set(ox + cx, oy + h + 1, oz + cz, gold);
				bp.set(ox + cx, oy + h + 2, oz + cz, rod);
			}
		}

		// Half-built arches between neighbouring pillars, each with a gap where the work stopped (finished: all closed)
		for (int k = 0; k < 6; k++) {
			int n = (k + 1) % 6;
			int x0 = at[k][0], z0 = at[k][1], x1 = at[n][0], z1 = at[n][1];
			int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
			int y, gap;
			if (heights[k] < 14 || heights[n] < 14 || r.nextDouble() > 0.65) {
				if (!whole) {
					continue;
				}
				// never begun, it is finished at a height of its own
				y = 12 + (int) (unit(seed, k, n, 41) * 2);
				gap = -9;
			} else {
				y = 12 + r.nextInt(2);
				int left = 1 + r.nextInt(Math.max(1, steps - 2));
				gap = whole ? -9 : left;
			}
			for (int i = 0; i <= steps; i++) {
				if (i == gap || i == gap + 1) {
					continue;
				}
				int x = x0 + Math.round((x1 - x0) * i / (float) steps), z = z0 + Math.round((z1 - z0) * i / (float) steps);
				bp.soft(ox + x, oy + y, oz + z, i % 3 == 0 ? pale : bricks);
				// the arch rises a course towards its middle
				if (i > steps / 4 && i < steps - steps / 4) {
					bp.soft(ox + x, oy + y + 1, oz + z, bricks);
				}
				if (whole && i == steps / 2) {
					// and finished, it has its keystone
					bp.soft(ox + x, oy + y + 2, oz + z, palePol);
				}
			}
		}

		// The halo: an upright ring over the fitting, golden at its spokes, open where it was never closed (finished: whole)
		double plane = r.nextDouble() * Math.PI;
		if (seen != null && !Double.isNaN(seen.plane)) {
			plane = seen.plane;
		}
		double ux = Math.cos(plane), uz = Math.sin(plane);
		double gapFrom = r.nextDouble() * Math.PI * 2, gapTo = gapFrom + Math.toRadians(70);
		int hy = oy + HALO_Y;
		bp.halo = new BlockPos(ox, hy, oz);
		bp.plane = plane;
		bp.gapMid = gapFrom + Math.toRadians(35);
		List<int[]> rim = new ArrayList<>();
		for (int i = 0; i < 360; i += 3) {
			double phi = Math.toRadians(i);
			double rel = phi - gapFrom;
			rel -= Math.floor(rel / (Math.PI * 2)) * Math.PI * 2;
			boolean open = rel < gapTo - gapFrom;
			if (open && !whole) {
				continue;
			}
			double h = Math.cos(phi) * HALO_R, v = Math.sin(phi) * HALO_R;
			int x = ox + (int) Math.round(h * ux), y = hy + (int) Math.round(v), z = oz + (int) Math.round(h * uz);
			boolean spoke = i % 45 == 0;
			bp.set(x, y, z, spoke ? gold : unit(seed, x, y, z) < 0.3 ? pale : bricks);
			bp.ring.add(BlockPos.asLong(x, y, z));
			if (spoke) {
				// a rod of light sticking out of each spoke
				Direction out = nearest(Math.cos(phi) * ux, Math.sin(phi), Math.cos(phi) * uz);
				bp.soft(x + out.getStepX(), y + out.getStepY(), z + out.getStepZ(), facing(Blocks.END_ROD.defaultBlockState(), out));
				bp.ring.add(BlockPos.asLong(x + out.getStepX(), y + out.getStepY(), z + out.getStepZ()));
			}
			// (the chains hang only where the unfinished ring stands, so both draw the same chains)
			if (!open && Math.sin(phi) < -0.5) {
				rim.add(new int[] {x, y, z});
			}
		}
		if (whole) {
			// every spoke golden (where the ring came round onto a spoke's block again, it had laid brick over it)
			for (int i = 0; i < 360; i += 45) {
				double phi = Math.toRadians(i), h = Math.cos(phi) * HALO_R, v = Math.sin(phi) * HALO_R;
				bp.set(ox + (int) Math.round(h * ux), hy + (int) Math.round(v), oz + (int) Math.round(h * uz), gold);
			}
		}
		// Chains hang from the halo's underside, a block at the end of some
		for (int c = 0; c < 4 && !rim.isEmpty(); c++) {
			int[] p = rim.get(r.nextInt(rim.size()));
			int len = 3 + r.nextInt(5);
			for (int d = 1; d <= len; d++) {
				bp.soft(p[0], p[1] - d, p[2], chain);
				bp.chains.add(BlockPos.asLong(p[0], p[1] - d, p[2]));
			}
			if (r.nextBoolean()) {
				bp.soft(p[0], p[1] - len - 1, p[2], r.nextBoolean() ? purpur : endStone);
				bp.chains.add(BlockPos.asLong(p[0], p[1] - len - 1, p[2]));
			}
		}

		// Loose blocks hanging in the air all round, as if just set down (finished, they are part of it: none hangs loose)
		for (int i = 0; i < 14; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 6;
			int x = ox + (int) Math.round(Math.cos(a) * d), y = oy + 3 + r.nextInt(20), z = oz + (int) Math.round(Math.sin(a) * d);
			BlockState s = switch (r.nextInt(4)) {
				case 0 -> purpur;
				case 1 -> obsidian;
				case 2 -> pale;
				default -> endStone;
			};
			if (!whole) {
				bp.soft(x, y, z, s);
			}
			bp.loose.add(new BlockPos(x, y, z));
			if (r.nextInt(3) == 0) {
				if (!whole) {
					bp.soft(x + 1, y, z, s);
					bp.soft(x, y + 1, z, s);
				}
				bp.loose.add(new BlockPos(x + 1, y, z));
				bp.loose.add(new BlockPos(x, y + 1, z));
			}
		}

		// What the builders put by: two barrels on the floor between the pillars, a third sometimes on the tallest
		for (int k : new int[] {0, 3}) {
			double a = turn + k * Math.PI / 3 + Math.PI / 6;
			int x = ox + (int) Math.round(Math.cos(a) * 5.0), z = oz + (int) Math.round(Math.sin(a) * 5.0);
			Direction in = nearest(ox - x, 0, oz - z);
			bp.set(x, oy + 1, z, facing(Blocks.BARREL.defaultBlockState(), in));
			bp.caches.add(new BlockPos(x, oy + 1, z));
		}
		if (r.nextBoolean() && seen == null) {
			int x = ox + at[tallest][0], y = oy + heights[tallest] + 1, z = oz + at[tallest][1];
			bp.set(x, y, z, facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
			bp.caches.add(new BlockPos(x, y, z));
			if (whole) {
				// finished, its pillar rises no further over it: it is not walled in (the halo and its chains stay)
				for (int up = y + 1; up <= oy + top + 2; up++) {
					long k = BlockPos.asLong(x, up, z);
					if (!bp.ring.contains(k) && !bp.chains.contains(k)) {
						bp.blocks.remove(k);
					}
				}
			}
		}
		return bp;
	}

	/**
	 * What can be told of the anchor whose floor's middle is {@code o} from what stands there ({@code world} reads a
	 * block): the turn that puts the most of its pillars' cores on purpur pillars, the way of the halo that puts the most
	 * of its ring on blocks, and how tall the tallest pillar stands. For an anchor whose seed is not known.
	 */
	static Shape seen(BlockPos o, Function<BlockPos, BlockState> world) {
		Shape s = new Shape();
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		int[] score = new int[240];
		for (int step = 0; step < score.length; step++) {
			double turn = step * (Math.PI / 3) / score.length;
			for (int k = 0; k < 6; k++) {
				double a = turn + k * Math.PI / 3;
				BlockPos p = new BlockPos(ox + (int) Math.round(Math.cos(a) * 7.0), oy + 2, oz + (int) Math.round(Math.sin(a) * 7.0));
				score[step] += world.apply(p).is(Blocks.PURPUR_PILLAR) ? 1 : 0;
			}
		}
		int best = middle(score);
		if (best >= 0) {
			s.turn = best * (Math.PI / 3) / score.length;
			for (int k = 0; k < 6; k++) {
				double a = s.turn + k * Math.PI / 3;
				int x = ox + (int) Math.round(Math.cos(a) * 7.0), z = oz + (int) Math.round(Math.sin(a) * 7.0);
				int h = 0;
				while (h < HALO_Y + HALO_R && world.apply(new BlockPos(x, oy + h + 1, z)).is(Blocks.PURPUR_PILLAR)) {
					h++;
				}
				s.tallest = Math.max(s.tallest, h);
			}
		}
		score = new int[360];
		for (int step = 0; step < score.length; step++) {
			double plane = step * Math.PI / score.length, ux = Math.cos(plane), uz = Math.sin(plane);
			for (int i = 0; i < 360; i += 6) {
				double phi = Math.toRadians(i), h = Math.cos(phi) * HALO_R, v = Math.sin(phi) * HALO_R;
				BlockPos p = new BlockPos(ox + (int) Math.round(h * ux), oy + HALO_Y + (int) Math.round(v), oz + (int) Math.round(h * uz));
				score[step] += world.apply(p).isAir() ? 0 : 1;
			}
		}
		best = middle(score);
		if (best >= 0) {
			s.plane = best * Math.PI / score.length;
		}
		return s;
	}

	/**
	 * Of scores taken at even steps round a circle, the step in the middle of the longest run of the best score (the
	 * angle that fits best, and most surely); -1 if nothing scored at all.
	 */
	private static int middle(int[] score) {
		int n = score.length, best = 0;
		for (int v : score) {
			best = Math.max(best, v);
		}
		if (best == 0) {
			return -1;
		}
		int from = 0, run = 0, bestFrom = 0, bestRun = 0;
		// (round twice, so that a run over the end is seen whole)
		for (int i = 0; i < 2 * n; i++) {
			if (score[i % n] != best) {
				run = 0;
				continue;
			}
			if (run == 0) {
				from = i;
			}
			run++;
			if (run > bestRun && run <= n) {
				bestRun = run;
				bestFrom = from;
			}
		}
		return (bestFrom + bestRun / 2) % n;
	}
}
