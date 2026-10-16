package net.battlehub.arena;

import java.util.HashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Keeps the chunks of an arena loaded from the moment it is built until it is cleared. Otherwise parts of a big
 * arena unload while nobody is near, and the items, arrows and blocks in them are saved and come back in the next
 * arena built on that spot. Counted per chunk, in case two arenas ever share one.
 */
public final class ArenaChunks {
	private ArenaChunks() {}

	private static final Map<Long, Integer> HELD = new HashMap<>();

	private static long key(int x, int z) {
		return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
	}

	public static void hold(ServerLevel level, AABB box) {
		forEach(box, (x, z) -> {
			if (HELD.merge(key(x, z), 1, Integer::sum) == 1) {
				level.setChunkForced(x, z, true);
			}
		});
	}

	public static void release(ServerLevel level, AABB box) {
		forEach(box, (x, z) -> {
			long k = key(x, z);
			Integer n = HELD.get(k);
			if (n == null) {
				return;
			}
			if (n <= 1) {
				HELD.remove(k);
				level.setChunkForced(x, z, false);
			} else {
				HELD.put(k, n - 1);
			}
		});
	}

	/** At start-up: nothing is held, so chunks still forced from a crash are let go. */
	public static void reset(ServerLevel level) {
		HELD.clear();
		for (long k : new LongArrayList(level.getForceLoadedChunks())) {
			level.setChunkForced((int) k, (int) (k >>> 32), false);
		}
	}

	public static int held() {
		return HELD.size();
	}

	private interface ChunkAction {
		void at(int x, int z);
	}

	private static void forEach(AABB box, ChunkAction action) {
		int x0 = (int) Math.floor(box.minX) >> 4;
		int x1 = (int) Math.floor(box.maxX) >> 4;
		int z0 = (int) Math.floor(box.minZ) >> 4;
		int z1 = (int) Math.floor(box.maxZ) >> 4;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				action.at(x, z);
			}
		}
	}
}
