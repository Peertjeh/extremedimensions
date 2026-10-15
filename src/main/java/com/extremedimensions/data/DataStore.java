package com.extremedimensions.data;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Per-world persistence in {@code <world>/extremedimensions.json}. Every world has its own progress. Changes are marked
 * dirty and written together with the world's own saves, so the mod's data never runs ahead of (or behind) the
 * inventories and blocks it belongs with.
 */
public final class DataStore {
	private DataStore() {}

	private static class WorldData {
		Map<String, PlayerData> players = new HashMap<>();
		/** Field keeps its historic name so older saves still load. */
		Map<String, Anchor> altars = new HashMap<>();
		boolean overworldPenalties = false;
		List<Hoard> hoards = new ArrayList<>();
		/** Seam cells (island grid) that were already built. */
		Set<String> seamCells = new HashSet<>();
		/** The Ender Dragon was the seal on the first wound; once slain, the world changes. */
		boolean sealBroken = false;
		/** "" until the Starved One falls; then "mended" or "severed". */
		String ending = "";
		boolean heartBuilt = false;
		/** The Starved One has fallen (it does not rise again, whether or not its Thread or Shears were used yet). */
		boolean starvedSlain = false;
		/** Where wild hoards were wrecked, as "dim|x|z|gameTime": no new one nests near for a while. */
		List<String> wrecks = new ArrayList<>();
		/** The Rift Trials scoreboard. */
		com.extremedimensions.trial.TrialBoard trials = new com.extremedimensions.trial.TrialBoard();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<WorldData>() {}.getType();
	private static WorldData data = new WorldData();
	private static Path file = null;
	/** Changed since the last write. */
	private static boolean dirty = false;

	public static void load(MinecraftServer server) {
		data = new WorldData();
		dirty = false;
		file = server.getWorldPath(LevelResource.ROOT).resolve(ExtremeDimensions.MOD_ID + ".json");
		WorldData loaded = readWithFallback(file, TYPE);
		if (loaded == null) {
			return;
		}
		if (loaded.players != null) {
			data.players = loaded.players;
		}
		if (loaded.altars != null) {
			data.altars = loaded.altars;
		}
		data.overworldPenalties = loaded.overworldPenalties;
		if (loaded.hoards != null) {
			data.hoards = loaded.hoards;
		}
		data.sealBroken = loaded.sealBroken;
		data.ending = loaded.ending == null ? "" : loaded.ending;
		data.heartBuilt = loaded.heartBuilt;
		data.starvedSlain = loaded.starvedSlain;
		if (loaded.wrecks != null) {
			data.wrecks = loaded.wrecks;
		}
		if (loaded.seamCells != null) {
			data.seamCells = loaded.seamCells;
		}
		if (loaded.trials != null) {
			data.trials = loaded.trials;
			if (data.trials.scores == null) {
				data.trials.scores = new HashMap<>();
			}
			if (data.trials.lastTop == null) {
				data.trials.lastTop = new ArrayList<>();
			}
		}
	}

	/**
	 * Reads a data file. If it is missing or damaged (a crash while writing), the last good copy ({@code .bak}) takes
	 * over. A file that cannot be read is never overwritten: it is moved aside ({@code .unreadable-<time>}) for the
	 * admin. Null = nothing readable, start fresh.
	 */
	public static <T> T readWithFallback(Path path, Type type) {
		T loaded = read(path, type);
		if (loaded != null) {
			return loaded;
		}
		if (Files.exists(path)) {
			Path aside = path.resolveSibling(path.getFileName() + ".unreadable-" + System.currentTimeMillis());
			try {
				Files.move(path, aside);
				ExtremeDimensions.LOGGER.error("World data could not be read; kept it as {}", aside.getFileName());
			} catch (IOException e) {
				ExtremeDimensions.LOGGER.error("World data could not be read or moved aside", e);
			}
		}
		T restored = read(backupOf(path), type);
		if (restored != null) {
			ExtremeDimensions.LOGGER.warn("World data restored from {}", backupOf(path).getFileName());
		}
		return restored;
	}

	/** The file's contents, or null if it is missing or cannot be read. */
	private static <T> T read(Path path, Type type) {
		if (!Files.exists(path)) {
			return null;
		}
		try {
			return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.error("Could not read {}", path.getFileName(), e);
			return null;
		}
	}

	public static Path backupOf(Path path) {
		return path.resolveSibling(path.getFileName() + ".bak");
	}

	/**
	 * Writes a file crash-safe: the new contents go to a temporary file that then replaces the old one in a single
	 * step, and the previous version stays behind as {@code .bak}. A crash at any moment leaves the old or the new
	 * file intact, never half of one.
	 */
	public static void writeAtomically(Path path, String json) throws IOException {
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.writeString(tmp, json, StandardCharsets.UTF_8);
		if (Files.exists(path)) {
			Files.copy(path, backupOf(path), StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Something changed: it is written with the next world save (or when the server stops). */
	public static void markDirty() {
		dirty = true;
	}

	/** Writes the data if anything changed (see {@link #writeAtomically}). */
	public static void flush() {
		if (file == null || !dirty) {
			return;
		}
		try {
			writeAtomically(file, GSON.toJson(data, TYPE));
			dirty = false;
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Could not save world data", e);
		}
	}

	/** After a world closes nothing may leak into the next one. */
	public static void unload() {
		flush();
		data = new WorldData();
		dirty = false;
		file = null;
	}

	public static boolean sealBroken() {
		return data.sealBroken;
	}

	public static void setSealBroken(boolean broken) {
		data.sealBroken = broken;
		markDirty();
	}

	public static com.extremedimensions.trial.TrialBoard trials() {
		return data.trials;
	}

	public static String ending() {
		return data.ending;
	}

	public static void setEnding(String ending) {
		data.ending = ending;
		markDirty();
	}

	public static boolean heartBuilt() {
		return data.heartBuilt;
	}

	public static boolean starvedSlain() {
		return data.starvedSlain;
	}

	public static void setStarvedSlain(boolean slain) {
		data.starvedSlain = slain;
		markDirty();
	}

	/** Where wild hoards were wrecked ("dim|x|z|gameTime"). */
	public static List<String> wrecks() {
		return data.wrecks;
	}

	public static void setHeartBuilt(boolean built) {
		data.heartBuilt = built;
		markDirty();
	}

	public static Set<String> seamCells() {
		return data.seamCells;
	}

	public static List<Hoard> hoards() {
		return data.hoards;
	}

	/** A player's data if there is any (never creates it: for hot paths that also see fake players). */
	public static PlayerData peek(UUID id) {
		return data.players.containsKey(id.toString()) ? get(id) : null;
	}

	public static PlayerData get(UUID id) {
		PlayerData d = data.players.computeIfAbsent(id.toString(), k -> new PlayerData());
		if (d.penaltyTime != null && !d.penaltyTime.isEmpty()) {
			d.migrate();
		}
		if (d.curses == null) {
			d.curses = new java.util.HashMap<>();
		}
		if (d.atonement == null) {
			d.atonement = new java.util.HashMap<>();
		}
		if (d.pendingMult == null) {
			d.pendingMult = new java.util.ArrayList<>();
		}
		if (d.pact == null) {
			d.pact = new java.util.HashMap<>();
		}
		return d;
	}

	public static Map<String, Anchor> anchors() {
		return data.altars;
	}

	public static boolean overworldPenalties() {
		return data.overworldPenalties;
	}

	public static void setOverworldPenalties(boolean on) {
		data.overworldPenalties = on;
		markDirty();
	}
}
