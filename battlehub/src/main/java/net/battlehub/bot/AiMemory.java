package net.battlehub.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.ai.PlayerKnowledge;
import net.battlehub.data.PlayerStore;
import net.minecraft.server.MinecraftServer;

/**
 * What bots remember about each player across sessions ({@link PlayerKnowledge}): kept in memory while the server
 * runs, and in {@code battlehub/ai/<uuid>.json} when {@code bots.learningPersistent} is on. With {@code bots.learning}
 * off nothing is remembered between fights at all. Players see it with {@code /bots memory} and wipe it with
 * {@code /bots forget}.
 */
public final class AiMemory {
	private AiMemory() {}

	private static final Map<UUID, PlayerKnowledge> CACHE = new HashMap<>();

	public static boolean learning() {
		return ConfigManager.config().bots.learning;
	}

	private static boolean persistent() {
		return learning() && ConfigManager.config().bots.learningPersistent;
	}

	private static Path file(MinecraftServer server, UUID id) {
		return PlayerStore.worldDir(server).resolve("ai").resolve(id + ".json");
	}

	/** What is known about a player (empty knowledge when nothing is, or learning is off). */
	public static PlayerKnowledge get(MinecraftServer server, UUID id) {
		PlayerKnowledge k = CACHE.get(id);
		if (k != null) return k;
		k = new PlayerKnowledge();
		if (persistent()) {
			Path f = file(server, id);
			if (Files.exists(f)) {
				try {
					PlayerKnowledge read = ConfigManager.GSON.fromJson(Files.readString(f), PlayerKnowledge.class);
					if (read != null) k = read.clean();
				} catch (IOException | RuntimeException e) {
					BattleHub.LOGGER.warn("BattleHub: could not read what bots learned about {}: {}", id, e.toString());
				}
			}
		}
		CACHE.put(id, k);
		return k;
	}

	public static void save(MinecraftServer server, UUID id) {
		PlayerKnowledge k = CACHE.get(id);
		if (k == null || !persistent()) return;
		try {
			Files.createDirectories(file(server, id).getParent());
		} catch (IOException e) {
			return;
		}
		ConfigManager.writeJson(file(server, id), ConfigManager.GSON.toJson(k));
	}

	/** Wipes what bots know about a player (memory and file). */
	public static void forget(MinecraftServer server, UUID id) {
		CACHE.remove(id);
		try {
			Files.deleteIfExists(file(server, id));
		} catch (IOException e) {
			BattleHub.LOGGER.warn("BattleHub: could not delete {}", file(server, id));
		}
	}

	/** Wipes everything bots know about anyone; returns how many files went. */
	public static int forgetAll(MinecraftServer server) {
		CACHE.clear();
		Path dir = PlayerStore.worldDir(server).resolve("ai");
		int n = 0;
		if (!Files.isDirectory(dir)) return 0;
		try (var files = Files.list(dir)) {
			for (Path f : files.toList()) {
				if (f.toString().endsWith(".json") && Files.deleteIfExists(f)) n++;
			}
		} catch (IOException e) {
			BattleHub.LOGGER.warn("BattleHub: could not clear {}", dir);
		}
		return n;
	}

	/** Server stopping. */
	public static void clear() {
		CACHE.clear();
	}
}
