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
		BlockPos start = BlockPos.containing(from.x, from.y + 0.01, from.z);
		// Mid-jump or on a slab edge: the standing spot just below
		for (int i = 0; i < 3 && !w.stand(start.getX(), start.getY(), start.getZ()); i++) {
			start = start.below();
		}
		BlockPos end = BlockPos.containing(goal.x, goal.y + 0.01, goal.z);
		List<GridPath.Node> nodes = GridPath.find(w, new GridPath.Node(start.getX(), start.getY(), start.getZ()),
				new GridPath.Node(end.getX(), end.getY(), end.getZ()), 1.5, 2500);
		if (nodes == null) {
			return null;
		}
		List<Vec3> out = new ArrayList<>(nodes.size());
		for (GridPath.Node n : nodes) {
			out.add(new Vec3(n.x() + 0.5, n.y(), n.z() + 0.5));
		}
		return out;
	}
}
