package com.extremedimensions.data;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Per-world persistence in {@code <world>/extremedimensions.json}. Every world has its own progress. */
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
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<WorldData>() {}.getType();
	private static WorldData data = new WorldData();
	private static Path file = null;

	public static void load(MinecraftServer server) {
		data = new WorldData();
		file = server.getWorldPath(LevelResource.ROOT).resolve(ExtremeDimensions.MOD_ID + ".json");
		try {
			if (Files.exists(file)) {
				WorldData loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
				if (loaded != null) {
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
					if (loaded.seamCells != null) {
						data.seamCells = loaded.seamCells;
					}
				}
			}
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.error("Could not load world data, starting fresh", e);
			data = new WorldData();
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(data, TYPE), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Could not save world data", e);
		}
	}

	/** After a world closes nothing may leak into the next one. */
	public static void unload() {
		save();
		data = new WorldData();
		file = null;
	}

	public static boolean sealBroken() {
		return data.sealBroken;
	}

	public static void setSealBroken(boolean broken) {
		data.sealBroken = broken;
		save();
	}

	public static String ending() {
		return data.ending;
	}

	public static void setEnding(String ending) {
		data.ending = ending;
		save();
	}

	public static boolean heartBuilt() {
		return data.heartBuilt;
	}

	public static void setHeartBuilt(boolean built) {
		data.heartBuilt = built;
		save();
	}

	public static Set<String> seamCells() {
		return data.seamCells;
	}

	public static List<Hoard> hoards() {
		return data.hoards;
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
		save();
	}
}
