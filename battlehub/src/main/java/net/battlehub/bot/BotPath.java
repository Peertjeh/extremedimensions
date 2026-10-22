package net.battlehub.bot;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.GridPath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Routes for bots through the real blocks of an arena (see {@link GridPath}). */
final class BotPath {
	private BotPath() {}

	private record World(ServerLevel level) implements GridPath.Terrain {
		@Override
		public boolean open(int x, int y, int z) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState st = level.getBlockState(p);
			if (st.is(Blocks.LAVA) || st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE)) {
				return false;
			}
			return st.getCollisionShape(level, p).isEmpty();
		}

		@Override
		public boolean stand(int x, int y, int z) {
			if (!open(x, y, z) || !open(x, y + 1, z)) {
				return false;
			}
			BlockPos below = new BlockPos(x, y - 1, z);
			BlockState st = level.getBlockState(below);
			return !st.getCollisionShape(level, below).isEmpty() && !st.is(Blocks.MAGMA_BLOCK) && !st.is(Blocks.CACTUS);
		}
	}

	/** Points to walk through from where the bot stands to near {@code goal}, or null when there is no way. */
	static List<Vec3> find(ServerLevel level, Vec3 from, Vec3 goal) {
		World w = new World(level);
		BlockPos start = standing(w, from);
		if (start == null) {
			return null;
		}
		BlockPos end = BlockPos.containing(goal.x, goal.y + 0.01, goal.z);
		// a goal nobody can stand on (The Bridge's goal is a 3x3 hole) is reached at its rim, 2 blocks from its middle
		double reach = w.stand(end.getX(), end.getY(), end.getZ()) ? 1.5 : 2.5;
		List<GridPath.Node> nodes = GridPath.find(w, new GridPath.Node(start.getX(), start.getY(), start.getZ()),
				new GridPath.Node(end.getX(), end.getY(), end.getZ()), reach, 2500);
		if (nodes == null) {
			return null;
		}
		List<Vec3> out = new ArrayList<>(nodes.size());
		for (GridPath.Node n : nodes) {
			out.add(new Vec3(n.x() + 0.5, n.y(), n.z() + 0.5));
		}
		return out;
	}

	/**
	 * The block it stands on: under its middle, or (on a one-block bridge, with the middle just past the edge) under
	 * another part of its feet, which are 0.6 wide; mid-jump or on a slab edge the spot just below.
	 */
	private static BlockPos standing(World w, Vec3 from) {
		double[][] feet = {{0, 0}, {0.29, 0}, {-0.29, 0}, {0, 0.29}, {0, -0.29}, {0.29, 0.29}, {0.29, -0.29}, {-0.29, 0.29}, {-0.29, -0.29}};
		for (int down = 0; down < 4; down++) {
			for (double[] f : feet) {
				BlockPos p = BlockPos.containing(from.x + f[0], from.y + 0.01 - down, from.z + f[1]);
				if (w.stand(p.getX(), p.getY(), p.getZ())) return p;
			}
		}
		return null;
	}
}
