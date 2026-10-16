package net.battlehub.arena;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.SlotAllocator;
import net.battlehub.data.PlayerStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Which arena slots are in use. Built areas are remembered in {@code <world>/battlehub/arenas.json} until they are
 * cleared, so arenas left behind by a crash are cleaned on the next start before the slot is used again.
 */
public final class ArenaSlots {
	private ArenaSlots() {}

	private static final Type TYPE = new TypeToken<Map<Integer, double[]>>() {}.getType();
	private static SlotAllocator alloc = new SlotAllocator(1);
	private static Map<Integer, double[]> dirty = new HashMap<>();
	private static Path file;

	public static void init(MinecraftServer server) {
		alloc = new SlotAllocator(ConfigManager.config().arenas.maxSlots);
		dirty = new HashMap<>();
		file = PlayerStore.worldDir(server).resolve("arenas.json");
		try {
			if (Files.exists(file)) {
				Map<Integer, double[]> loaded = ConfigManager.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
				if (loaded != null) {
					dirty.putAll(loaded);
				}
			}
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not read {}", file, e);
		}
		ServerLevel level = ArenaWorld.level(server);
		if (level == null) {
			return;
		}
		ArenaChunks.reset(level);
		for (Map.Entry<Integer, double[]> e : new HashMap<>(dirty).entrySet()) {
			int slot = e.getKey();
			double[] b = e.getValue();
			if (b == null || b.length != 6) {
				dirty.remove(slot);
				continue;
			}
			alloc.reserve(slot);
			BattleHub.LOGGER.info("Cleaning arena slot {} left over from the last run", slot);
			AABB box = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
			ArenaChunks.hold(level, box);
			// Cleared a few seconds later, once the entities of the freshly loaded chunks are there to be swept too
			net.battlehub.util.Tasks.later(60, () -> ArenaBuilder.clear(level, box, () -> {
				ArenaChunks.release(level, box);
				release(slot);
			}));
		}
		save();
	}

	public static int acquire() {
		return alloc.acquire();
	}

	public static void markBuilt(int slot, AABB box) {
		dirty.put(slot, new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ});
		save();
	}

	public static void release(int slot) {
		dirty.remove(slot);
		alloc.release(slot);
		save();
	}

	public static int busy() {
		return alloc.busy();
	}

	public static int capacity() {
		return alloc.capacity();
	}

	private static void save() {
		if (file != null) {
			ConfigManager.writeJson(file, ConfigManager.GSON.toJson(dirty, TYPE));
		}
	}
}
