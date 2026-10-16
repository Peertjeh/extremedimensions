package net.battlehub.arena;

import java.util.function.Supplier;

import net.battlehub.util.Ids;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Shape helpers for arena layouts. All coordinates are relative to the slot origin. */
public final class Build {
	private Build() {}

	/** Block names a layout asked for that this version does not have (they come out as air). */
	private static final java.util.Set<String> UNKNOWN = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static BlockState s(String id) {
		BlockState st = Ids.state(id);
		if (st.isAir() && !id.endsWith("air")) {
			UNKNOWN.add(id);
		}
		return st;
	}

	public static java.util.Set<String> unknown() {
		return UNKNOWN;
	}

	/** Leaves that never decay. */
	public static BlockState leaves(String id) {
		BlockState st = Ids.state(id);
		if (st.hasProperty(LeavesBlock.PERSISTENT)) {
			st = st.setValue(LeavesBlock.PERSISTENT, true);
		}
		return st;
	}

	public static BlockState slab(String id, boolean top) {
		BlockState st = Ids.state(id);
		if (st.hasProperty(SlabBlock.TYPE)) {
			st = st.setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
		}
		return st;
	}

	public static BlockState facing(String id, Direction dir) {
		BlockState st = Ids.state(id);
		if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
		}
		return st;
	}

	public static BlockState hanging(String id) {
		BlockState st = Ids.state(id);
		if (st.hasProperty(BlockStateProperties.HANGING)) {
			st = st.setValue(BlockStateProperties.HANGING, true);
		}
		return st;
	}

	public static double dist(double x, double z) {
		return Math.sqrt(x * x + z * z);
	}

	/** Filled circle of radius r (slightly rounded outwards so it looks round in blocks). */
	public static void disc(Blueprint bp, int cx, int y, int cz, double r, Supplier<BlockState> block) {
		int ir = (int) Math.ceil(r);
		for (int x = -ir; x <= ir; x++) {
			for (int z = -ir; z <= ir; z++) {
				if (x * x + z * z <= r * r + r * 0.6) {
					bp.set(cx + x, y, cz + z, block.get());
				}
			}
		}
	}

	/** Ring between two radii. */
	public static void ring(Blueprint bp, int cx, int y, int cz, double rIn, double rOut, Supplier<BlockState> block) {
		int ir = (int) Math.ceil(rOut);
		for (int x = -ir; x <= ir; x++) {
			for (int z = -ir; z <= ir; z++) {
				double d2 = x * x + z * z;
				if (d2 <= rOut * rOut + rOut * 0.6 && d2 > rIn * rIn + rIn * 0.6) {
					bp.set(cx + x, y, cz + z, block.get());
				}
			}
		}
	}

	/** Floating island: a top layer of {@code top} and a rough, tapering underside. */
	public static void island(Blueprint bp, int cx, int y, int cz, double r, int depth, Supplier<BlockState> top,
			BlockState under, BlockState deep, RandomSource rnd) {
		disc(bp, cx, y, cz, r, top);
		for (int d = 1; d <= depth; d++) {
			double rr = r * (1.0 - (double) d / (depth + 1)) + rnd.nextDouble() * 0.8;
			if (rr < 0.6) {
				break;
			}
			BlockState st = d <= 2 ? under : deep;
			disc(bp, cx, y - d, cz, rr, () -> st);
		}
	}

	/** An island whose top follows a pattern: {@code top} gives the block for each column (x, z). */
	public static void island(Blueprint bp, int cx, int y, int cz, double r, int depth,
			java.util.function.BiFunction<Integer, Integer, BlockState> top, BlockState under, BlockState deep, RandomSource rnd) {
		int ir = (int) Math.ceil(r);
		for (int x = cx - ir; x <= cx + ir; x++) {
			for (int z = cz - ir; z <= cz + ir; z++) {
				double dx = x - cx;
				double dz = z - cz;
				if (dx * dx + dz * dz <= r * r + r * 0.6) {
					bp.set(x, y, z, top.apply(x, z));
				}
			}
		}
		island(bp, cx, y, cz, r, depth, (Supplier<BlockState>) () -> null, under, deep, rnd);
	}

	public static void pillar(Blueprint bp, int x, int y, int z, int h, BlockState block) {
		for (int i = 0; i < h; i++) {
			bp.set(x, y + i, z, block);
		}
	}

	/** Simple round tree. */
	public static void tree(Blueprint bp, int x, int y, int z, int height, BlockState log, BlockState leaves, RandomSource rnd) {
		for (int i = 0; i < height; i++) {
			bp.set(x, y + i, z, log);
		}
		int top = y + height;
		for (int dy = -2; dy <= 1; dy++) {
			int r = dy <= -1 ? 2 : 1;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
					if (corner && (dy == 1 || rnd.nextInt(3) == 0)) {
						continue;
					}
					bp.setIfEmpty(x + dx, top + dy, z + dz, leaves);
				}
			}
		}
		bp.setIfEmpty(x, top + 2, z, leaves);
	}

	/** Round tower with crenellations and a light on top. */
	public static void tower(Blueprint bp, int cx, int y, int cz, int r, int h, BlockState wall, BlockState floor, BlockState light) {
		for (int dy = 0; dy < h; dy++) {
			ring(bp, cx, y + dy, cz, r - 1, r, () -> wall);
		}
		disc(bp, cx, y + h - 1, cz, r - 1, () -> floor);
		int ir = r;
		for (int x = -ir; x <= ir; x++) {
			for (int z = -ir; z <= ir; z++) {
				double d2 = x * x + z * z;
				if (d2 <= r * r + r * 0.6 && d2 > (r - 1) * (r - 1) + (r - 1) * 0.6 && ((x + z) & 1) == 0) {
					bp.set(cx + x, y + h, cz + z, wall);
				}
			}
		}
		bp.set(cx, y + h, cz, light);
	}

	/** Tapering spike (ice spikes). */
	public static void spike(Blueprint bp, int x, int y, int z, int h, BlockState block) {
		for (int i = 0; i < h; i++) {
			double r = Math.max(0, 1.6 * (1.0 - (double) i / h));
			int ir = (int) Math.floor(r);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz <= r * r + 0.3) {
						bp.set(x + dx, y + i, z + dz, block);
					}
				}
			}
		}
	}
}
