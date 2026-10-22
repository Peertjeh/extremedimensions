package com.extremedimensions.anchor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.server.Dims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Portal lookup and the free-air path from an anchor to its portal, used for streams and blocking. */
public final class AnchorPaths {
	private AnchorPaths() {}

	private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	public static boolean isPortalFor(BlockState s, String type) {
		if (Dims.NETHER.equals(type)) {
			return s.is(Blocks.NETHER_PORTAL);
		}
		return s.is(Blocks.END_PORTAL) || s.is(Blocks.END_PORTAL_FRAME);
	}

	/**
	 * Nearest portal block within {@link Config#PORTAL_RANGE}, or null. It looks only into chunks loaded now: one still
	 * loading (round a player just arrived) is passed over, never waited on, for the server thread would stand still
	 * until it was.
	 */
	public static BlockPos findPortal(ServerLevel level, BlockPos center, String type) {
		int r = Config.PORTAL_RANGE;
		BlockPos best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					int dist = dx * dx + dy * dy + dz * dz;
					if (dist >= bestDist) {
						continue;
					}
					BlockPos q = center.offset(dx, dy, dz);
					LevelChunk chunk = level.getChunkSource().getChunkNow(q.getX() >> 4, q.getZ() >> 4);
					if (chunk != null && isPortalFor(chunk.getBlockState(q), type)) {
						best = q;
						bestDist = dist;
					}
				}
			}
		}
		return best;
	}

	/** Air and collision-free blocks (torches, grass) let streams through; liquids do not. */
	static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.isAir()) {
			return true;
		}
		return !s.liquid() && s.getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * Breadth-first search through free blocks from the anchor to its portal.
	 * Returns the path points ending in the portal's centre, or an empty list if there is no portal or no path.
	 */
	public static List<double[]> findPath(ServerLevel level, Anchor a) {
		List<double[]> out = new ArrayList<>();
		BlockPos start = new BlockPos(a.x, a.y, a.z);
		BlockPos portal = findPortal(level, start, a.type);
		if (portal == null) {
			return out;
		}

		// Every block of this portal (diagonals included, for the ring of End Portal Frames)
		Set<Long> portalSet = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		portalSet.add(portal.asLong());
		queue.add(portal);
		double sx = 0, sy = 0, sz = 0;
		int count = 0;
		while (!queue.isEmpty() && count < 160) {
			BlockPos c = queue.poll();
			sx += c.getX();
			sy += c.getY();
			sz += c.getZ();
			count++;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = c.offset(dx, dy, dz);
						if (!portalSet.contains(n.asLong()) && isPortalFor(level.getBlockState(n), a.type)) {
							portalSet.add(n.asLong());
							queue.add(n);
						}
					}
				}
			}
		}
		double cx = sx / count + 0.5;
		double cy = sy / count + 0.5;
		double cz = sz / count + 0.5;

		int limit = Config.PORTAL_RANGE + 3;
		Map<Long, Long> parent = new HashMap<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		parent.put(start.asLong(), Long.MIN_VALUE);
		open.add(start);
		BlockPos goal = null;
		while (!open.isEmpty() && goal == null) {
			BlockPos c = open.poll();
			for (int[] dir : DIRS) {
				BlockPos n = c.offset(dir[0], dir[1], dir[2]);
				long key = n.asLong();
				if (parent.containsKey(key)
						|| Math.abs(n.getX() - a.x) > limit || Math.abs(n.getY() - a.y) > limit || Math.abs(n.getZ() - a.z) > limit) {
					continue;
				}
				boolean inPortal = portalSet.contains(key);
				if (!inPortal && !passable(level, n)) {
					continue;
				}
				parent.put(key, c.asLong());
				if (inPortal || touches(n, portalSet)) {
					goal = n;
					break;
				}
				open.add(n);
			}
		}
		if (goal == null) {
			return out;
		}

		List<double[]> rev = new ArrayList<>();
		long k = goal.asLong();
		while (k != Long.MIN_VALUE) {
			BlockPos b = BlockPos.of(k);
			rev.add(new double[] {b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5});
			k = parent.get(k);
		}
		for (int i = rev.size() - 1; i >= 0; i--) {
			out.add(rev.get(i));
		}
		out.add(new double[] {cx, cy, cz});
		return out;
	}

	private static boolean touches(BlockPos p, Set<Long> portalSet) {
		for (int[] dir : DIRS) {
			if (portalSet.contains(p.offset(dir[0], dir[1], dir[2]).asLong())) {
				return true;
			}
		}
		return false;
	}
}
