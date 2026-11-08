package com.extremedimensions.reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	}

	/** A vanilla block by id (block constants come and go between versions). */
	private static BlockState mc(String id, BlockState fallback) {
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
		Blueprint bp = new Blueprint();
		java.util.Random r = new java.util.Random(seed);
		BlockState endStone = Blocks.END_STONE.defaultBlockState(), bricks = Blocks.END_STONE_BRICKS.defaultBlockState();
		BlockState purpur = Blocks.PURPUR_BLOCK.defaultBlockState(), pillar = axis(Blocks.PURPUR_PILLAR.defaultBlockState(), Direction.Axis.Y);
		BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState(), crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		BlockState pale = ModBlocks.PALE_HUSH_BRICKS.defaultBlockState(), palePol = ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
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
				bp.set(ox + 2 * sx, oy + 2, oz + 2 * sz, facing(Blocks.END_ROD.defaultBlockState(), Direction.UP));
			}
		}

		// Six pillars round the floor's edge, broken off at different heights
		double turn = r.nextDouble() * Math.PI * 2;
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
			for (int y = 1; y <= h; y++) {
				for (int px = -1; px <= 1; px++) {
					for (int pz = -1; pz <= 1; pz++) {
						boolean core = px == 0 && pz == 0, corner = px != 0 && pz != 0;
						// the top few courses broken away, the core standing longest
						if (!core && y > h - 3 && unit(seed, k * 7 + px, pz, y) < 0.45 + 0.15 * (y - (h - 3))) {
							continue;
						}
						BlockState s = core ? pillar : corner ? pale : y % 6 == 0 ? palePol : bricks;
						bp.set(ox + cx + px, oy + y, oz + cz + pz, s);
					}
				}
			}
		}

		// Half-built arches between neighbouring pillars, each with a gap where the work stopped
		for (int k = 0; k < 6; k++) {
			int n = (k + 1) % 6;
			if (heights[k] < 14 || heights[n] < 14 || r.nextDouble() > 0.65) {
				continue;
			}
			int y = 12 + r.nextInt(2);
			int x0 = at[k][0], z0 = at[k][1], x1 = at[n][0], z1 = at[n][1];
			int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
			int gap = 1 + r.nextInt(Math.max(1, steps - 2));
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
			}
		}

		// The halo: an upright ring over the fitting, golden at its spokes, open where it was never closed
		double plane = r.nextDouble() * Math.PI;
		double ux = Math.cos(plane), uz = Math.sin(plane);
		double gapFrom = r.nextDouble() * Math.PI * 2, gapTo = gapFrom + Math.toRadians(70);
		int hy = oy + HALO_Y;
		List<int[]> rim = new ArrayList<>();
		for (int i = 0; i < 360; i += 3) {
			double phi = Math.toRadians(i);
			double rel = phi - gapFrom;
			rel -= Math.floor(rel / (Math.PI * 2)) * Math.PI * 2;
			if (rel < gapTo - gapFrom) {
				continue;
			}
			double h = Math.cos(phi) * HALO_R, v = Math.sin(phi) * HALO_R;
			int x = ox + (int) Math.round(h * ux), y = hy + (int) Math.round(v), z = oz + (int) Math.round(h * uz);
			boolean spoke = i % 45 == 0;
			bp.set(x, y, z, spoke ? gold : unit(seed, x, y, z) < 0.3 ? pale : bricks);
			if (spoke) {
				// a rod of light sticking out of each spoke
				Direction out = nearest(Math.cos(phi) * ux, Math.sin(phi), Math.cos(phi) * uz);
				bp.soft(x + out.getStepX(), y + out.getStepY(), z + out.getStepZ(), facing(Blocks.END_ROD.defaultBlockState(), out));
			}
			if (Math.sin(phi) < -0.5) {
				rim.add(new int[] {x, y, z});
			}
		}
		// Chains hang from the halo's underside, a block at the end of some
		for (int c = 0; c < 4 && !rim.isEmpty(); c++) {
			int[] p = rim.get(r.nextInt(rim.size()));
			int len = 3 + r.nextInt(5);
			for (int d = 1; d <= len; d++) {
				bp.soft(p[0], p[1] - d, p[2], chain);
			}
			if (r.nextBoolean()) {
				bp.soft(p[0], p[1] - len - 1, p[2], r.nextBoolean() ? purpur : endStone);
			}
		}

		// Loose blocks hanging in the air all round, as if just set down
		for (int i = 0; i < 14; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 6;
			int x = ox + (int) Math.round(Math.cos(a) * d), y = oy + 3 + r.nextInt(20), z = oz + (int) Math.round(Math.sin(a) * d);
			BlockState s = switch (r.nextInt(4)) {
				case 0 -> purpur;
				case 1 -> obsidian;
				case 2 -> pale;
				default -> endStone;
			};
			bp.soft(x, y, z, s);
			if (r.nextInt(3) == 0) {
				bp.soft(x + 1, y, z, s);
				bp.soft(x, y + 1, z, s);
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
		if (r.nextBoolean()) {
			int x = ox + at[tallest][0], y = oy + heights[tallest] + 1, z = oz + at[tallest][1];
			bp.set(x, y, z, facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
			bp.caches.add(new BlockPos(x, y, z));
		}
		return bp;
	}
}
