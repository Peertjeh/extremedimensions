package net.battlehub.arena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Places and removes arena blocks spread over ticks (a budget per tick), so building an arena never freezes the
 * server. Jobs run in order; each job calls back when it is done.
 */
public final class ArenaBuilder {
	private ArenaBuilder() {}

	/** Send to clients, skip shape updates of neighbours, never drop items. */
	private static final int FLAGS = 2 | 16 | 32;

	private interface Job {
		/** Work through up to {@code budget} units; return the units used. */
		int step(int budget);

		boolean done();

		void finish();
	}

	private static final Deque<Job> JOBS = new ArrayDeque<>();

	public static void build(Arena arena, Runnable onDone) {
		JOBS.add(new PlaceJob(arena, onDone));
	}

	public static void clear(Arena arena, Runnable onDone) {
		JOBS.add(new ClearJob(arena.level(), arena.bounds(), onDone));
	}

	public static void clear(ServerLevel level, AABB box, Runnable onDone) {
		JOBS.add(new ClearJob(level, box, onDone));
	}

	public static boolean busy() {
		return !JOBS.isEmpty();
	}

	public static void tick() {
		int budget = ConfigManager.config().arenas.blocksPerTick;
		while (budget > 0 && !JOBS.isEmpty()) {
			Job job = JOBS.peek();
			try {
				budget -= Math.max(1, job.step(budget));
			} catch (Exception e) {
				BattleHub.LOGGER.error("Arena job failed", e);
				JOBS.poll();
				safeFinish(job);
				continue;
			}
			if (job.done()) {
				JOBS.poll();
				safeFinish(job);
			}
		}
	}

	private static void safeFinish(Job job) {
		try {
			job.finish();
		} catch (Exception e) {
			BattleHub.LOGGER.error("Arena job callback failed", e);
		}
	}

	/** Runs every queued job to the end right now (server shutdown). */
	public static void flush() {
		while (!JOBS.isEmpty()) {
			Job job = JOBS.poll();
			try {
				while (!job.done()) {
					job.step(Integer.MAX_VALUE / 4);
				}
			} catch (Exception e) {
				BattleHub.LOGGER.error("Arena job failed during shutdown", e);
			}
			safeFinish(job);
		}
	}

	private static final class PlaceJob implements Job {
		private final Arena arena;
		private final Runnable onDone;
		private final List<long[]> order = new ArrayList<>();
		private final List<BlockState> states = new ArrayList<>();
		private int index;

		PlaceJob(Arena arena, Runnable onDone) {
			this.arena = arena;
			this.onDone = onDone;
			BlockPos o = arena.origin();
			List<Map.Entry<Long, BlockState>> entries = new ArrayList<>(arena.blueprint().blocks().entrySet());
			// Bottom to top, so supporting blocks exist before what stands on them.
			entries.sort((a, b) -> {
				int ya = BlockPos.getY(a.getKey());
				int yb = BlockPos.getY(b.getKey());
				if (ya != yb) return Integer.compare(ya, yb);
				int xa = BlockPos.getX(a.getKey()) >> 4;
				int xb = BlockPos.getX(b.getKey()) >> 4;
				if (xa != xb) return Integer.compare(xa, xb);
				return Integer.compare(BlockPos.getZ(a.getKey()) >> 4, BlockPos.getZ(b.getKey()) >> 4);
			});
			for (Map.Entry<Long, BlockState> e : entries) {
				long k = e.getKey();
				order.add(new long[] {o.getX() + BlockPos.getX(k), o.getY() + BlockPos.getY(k), o.getZ() + BlockPos.getZ(k)});
				states.add(e.getValue());
			}
		}

		@Override
		public int step(int budget) {
			ServerLevel level = arena.level();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int used = 0;
			while (used < budget && index < order.size()) {
				long[] p = order.get(index);
				pos.set((int) p[0], (int) p[1], (int) p[2]);
				level.setBlock(pos, states.get(index), FLAGS);
				index++;
				used++;
			}
			return used;
		}

		@Override
		public boolean done() {
			return index >= order.size();
		}

		@Override
		public void finish() {
			if (onDone != null) onDone.run();
		}
	}

	private static final class ClearJob implements Job {
		private final ServerLevel level;
		private final AABB box;
		private final Runnable onDone;
		private final int minX, minY, minZ, maxX, maxY, maxZ;
		private int x, y, z;
		private boolean entitiesCleared;
		private boolean finished;

		ClearJob(ServerLevel level, AABB box, Runnable onDone) {
			this.level = level;
			this.box = box;
			this.onDone = onDone;
			minX = (int) Math.floor(box.minX);
			minY = Math.max(level.getMinY(), (int) Math.floor(box.minY));
			minZ = (int) Math.floor(box.minZ);
			maxX = (int) Math.ceil(box.maxX);
			maxY = Math.min(level.getMaxY(), (int) Math.ceil(box.maxY));
			maxZ = (int) Math.ceil(box.maxZ);
			x = minX;
			y = minY;
			z = minZ;
		}

		@Override
		public int step(int budget) {
			if (!entitiesCleared) {
				for (Entity e : level.getEntities((Entity) null, box.inflate(8), e -> !(e instanceof Player))) {
					e.discard();
				}
				entitiesCleared = true;
			}
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			BlockState air = Blocks.AIR.defaultBlockState();
			int used = 0;
			// Reading is cheap, writing is not: count four reads as one unit.
			int reads = 0;
			while (used < budget && !finished) {
				pos.set(x, y, z);
				if (!level.getBlockState(pos).isAir()) {
					level.setBlock(pos, air, FLAGS);
					used++;
				} else if (++reads % 4 == 0) {
					used++;
				}
				if (++x > maxX) {
					x = minX;
					if (++z > maxZ) {
						z = minZ;
						if (++y > maxY) {
							finished = true;
						}
					}
				}
			}
			return used;
		}

		@Override
		public boolean done() {
			return finished;
		}

		@Override
		public void finish() {
			if (onDone != null) onDone.run();
		}
	}
}
