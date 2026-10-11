package com.extremedimensions;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Slaat spelers en altaren op in de wereldmap (extremedimensions.json).
 * Elke wereld heeft dus zijn eigen voortgang.
 */
public final class DataStore {
	private DataStore() {}

	/** Alles wat per wereld wordt opgeslagen. */
	private static class WorldData {
		Map<String, PlayerData> players = new HashMap<>();
		Map<String, Altar> altars = new HashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<WorldData>() {}.getType();
	private static WorldData data = new WorldData();
	private static Path file = null;

	public static void load(MinecraftServer server) {
		data = new WorldData();
		file = server.getWorldPath(LevelResource.ROOT).resolve("extremedimensions.json");
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
				}
			}
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.error("Could not load Extreme Dimensions data, starting fresh", e);
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
			ExtremeDimensions.LOGGER.error("Could not save Extreme Dimensions data", e);
		}
	}

	/** Na het afsluiten van een wereld: niets meenemen naar de volgende. */
	public static void unload() {
		save();
		data = new WorldData();
		file = null;
	}

	public static PlayerData get(UUID id) {
		return data.players.computeIfAbsent(id.toString(), k -> new PlayerData());
	}

	public static Map<String, Altar> altars() {
		return data.altars;
	}
}
