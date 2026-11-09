package com.extremedimensions.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The ground under a colossal body moved by hand rather than by the game's physics (the Elders, the Starved One), so
 * that it stands on what is under it and never in it.
 *
 * <p>A column's ground is looked for from the body's feet. With the feet in a block, it is found up through that block
 * and whatever stands on it, to the first room over it (a floor raised round the body, a cliff thrown up under it); a
 * gap of a single block is no room for a giant's feet. With the feet in the open, it is the first ground under them.
 * What hangs over the feet with open air between (an arch, a floating island, a curtain of roots, a halo) is never stood
 * on, however near. A whole footprint stands on the highest of its columns, so that no part of the body cuts into the
 * ground at an edge, a step or the rim of a pit.
 *
 * <p>Ground is whatever has a body to stand on, at the height of its top (a slab half a block up, a carpet barely
 * any), and liquids at their surface (lava carries the Ferryman's boat). Not ground: what has no body (grass, flowers,
 * a torch); what a giant wades through as through a thicket (leaves, trunks, huge fungi and mushrooms, bamboo, cacti,
 * chorus); and barriers, the invisible walls of a map (and of the game's tests), which nobody sees to stand on.
 */
public final class BossGround {
	private BossGround() {}

	/** How far up through blocks a body climbs out of them at most, in one look (the next look climbs on). */
	public static final int CLIMB = 40;
	/** How far under its feet the ground is looked for. */
	public static final int DOWN = 24;
	/** Play at the feet: a body standing on a block, or a hair into it, stands on it. */
	public static final double SLACK = 0.05;
	/** How far in from a footprint's edge its outermost columns are taken, so a wall it only touches is not under it. */
	private static final double INSET = 0.1;
	/** How far apart a hull is looked at along its length (under a block, so no column under it is missed). */
	private static final double HULL_STEP = 0.7;

	/** The top of the block at {@code pos} as ground under a giant, or NaN if it is none. */
	public static double top(Level level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.isAir() || s.is(Blocks.BARRIER) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.WART_BLOCKS)
				|| s.is(Blocks.SHROOMLIGHT) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
				|| s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.CHORUS_PLANT) || s.is(Blocks.CHORUS_FLOWER)) {
			return Double.NaN;
		}
		double top = Double.NaN;
		FluidState fluid = s.getFluidState();
		if (!fluid.isEmpty()) {
			top = pos.getY() + fluid.getHeight(level, pos);
		} else if (s.is(Blocks.POWDER_SNOW)) {
			top = pos.getY() + 1;
		}
		VoxelShape shape = s.getCollisionShape(level, pos);
		if (!shape.isEmpty()) {
			double t = pos.getY() + shape.max(Direction.Axis.Y);
			top = Double.isNaN(top) ? t : Math.max(top, t);
		}
		return top;
	}

	/**
	 * The ground of the column at (bx, bz) for feet at {@code feet}: up out of a block the feet are in, else down to the
	 * first ground under them (see the class). NaN with no ground within {@link #DOWN} under the feet, or the column's
	 * part of the world not loaded now.
	 */
	public static double column(Level level, int bx, int bz, double feet) {
		if (!Loaded.chunk(level, bx >> 4, bz >> 4)) {
			return Double.NaN;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int by = Mth.floor(feet + SLACK);
		double t = top(level, pos.set(bx, by, bz));
		if (!Double.isNaN(t) && t > feet + SLACK) {
			// In it: up through it and whatever stands on it, to the first room over it
			for (int i = 0; i < CLIMB; i++) {
				double up = top(level, pos.set(bx, by + 1, bz));
				if (!Double.isNaN(up)) {
					by++;
					t = up;
					continue;
				}
				double over = top(level, pos.set(bx, by + 2, bz));
				if (Double.isNaN(over)) {
					return t;
				}
				by += 2;
				t = over;
			}
			return t;
		}
		if (!Double.isNaN(t)) {
			// (low in the feet's own block: a slab, a carpet)
			return t;
		}
		for (int y = by - 1; y >= by - DOWN; y--) {
			t = top(level, pos.set(bx, y, bz));
			if (!Double.isNaN(t)) {
				return t;
			}
		}
		return Double.NaN;
	}

	/**
	 * The ground under a square footprint {@code half} blocks to each side of (x, z), for feet at {@code feet}: the highest
	 * of its columns; NaN if none has ground within reach.
	 */
	public static double box(Level level, double x, double z, double half, double feet) {
		int x0 = Mth.floor(x - half + INSET), x1 = Mth.floor(x + half - INSET);
		int z0 = Mth.floor(z - half + INSET), z1 = Mth.floor(z + half - INSET);
		double best = Double.NaN;
		for (int bx = x0; bx <= x1; bx++) {
			for (int bz = z0; bz <= z1; bz++) {
				best = higher(best, column(level, bx, bz, feet));
			}
		}
		return best;
	}

	/**
	 * The ground under a long hull (a boat) lying the way {@code yaw} faces through (x, z): from {@code from} to
	 * {@code to} blocks out towards each end, {@code across} blocks to each side of its keel; the highest under it, or NaN.
	 */
	public static double hull(Level level, double x, double z, float yaw, double from, double to, double across, double feet) {
		double a = Math.toRadians(yaw);
		double fx = -Math.sin(a), fz = Math.cos(a);
		double best = Double.NaN;
		int n = Math.max(1, (int) Math.ceil((to - from) / HULL_STEP));
		for (int i = 0; i <= n; i++) {
			double t = from + (to - from) * i / n;
			for (int end = -1; end <= 1; end += 2) {
				for (int side = -1; side <= 1; side++) {
					double px = x + fx * t * end - fz * across * side, pz = z + fz * t * end + fx * across * side;
					best = higher(best, column(level, Mth.floor(px), Mth.floor(pz), feet));
				}
			}
		}
		return best;
	}

	private static double higher(double a, double b) {
		return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b);
	}
}
