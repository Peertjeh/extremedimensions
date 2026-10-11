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

import net.fabricmc.loader.api.FabricLoader;

/**
 * Slaat de data van alle spelers op in config/extremedimensions-data.json.
 * (Simpel en robuust. Op een server staat dit bestand in de config-map van de server.)
 */
public final class DataStore {
	private DataStore() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<Map<String, PlayerData>>() {}.getType();
	private static Map<String, PlayerData> map = new HashMap<>();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("extremedimensions-data.json");
	}

	public static void load() {
		Path f = file();
		try {
			if (Files.exists(f)) {
				String json = Files.readString(f, StandardCharsets.UTF_8);
				Map<String, PlayerData> loaded = GSON.fromJson(json, TYPE);
				if (loaded != null) {
					map = loaded;
				}
			}
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.error("Kon spelerdata niet laden, begin met lege data", e);
			map = new HashMap<>();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(map, TYPE), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Kon spelerdata niet opslaan", e);
		}
	}

	public static PlayerData get(UUID id) {
		return map.computeIfAbsent(id.toString(), k -> new PlayerData());
	}
}
