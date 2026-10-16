package net.battlehub.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import net.battlehub.BattleHub;
import net.fabricmc.loader.api.FabricLoader;

/** Loads and saves the JSON files in {@code config/battlehub/}. Missing keys are written back with defaults. */
public final class ConfigManager {
	private ConfigManager() {}

	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static HubConfig config = new HubConfig().normalize();
	private static KitConfig kits = new KitConfig().normalize();
	private static TitleConfig titles = new TitleConfig().normalize();
	private static CosmeticConfig cosmetics = new CosmeticConfig().normalize();
	private static BedWarsConfig bedwars = new BedWarsConfig().normalize();
	private static PropHuntConfig prophunt = new PropHuntConfig().normalize();
	private static ProgressConfig progress = new ProgressConfig().normalize();
	private static SkyWarsConfig skywars = new SkyWarsConfig().normalize();

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(BattleHub.MOD_ID);
	}

	public static HubConfig config() {
		return config;
	}

	public static KitConfig kits() {
		return kits;
	}

	public static TitleConfig titles() {
		return titles;
	}

	public static CosmeticConfig cosmetics() {
		return cosmetics;
	}

	public static BedWarsConfig bedwars() {
		return bedwars;
	}

	public static PropHuntConfig prophunt() {
		return prophunt;
	}

	public static ProgressConfig progress() {
		return progress;
	}

	public static SkyWarsConfig skywars() {
		return skywars;
	}

	/** (Re)loads every file. A broken file keeps the previous values and is reported in the log. */
	public static boolean load() {
		boolean ok = true;
		try {
			Files.createDirectories(dir().resolve("lang"));
		} catch (IOException e) {
			BattleHub.LOGGER.error("Could not create {}", dir(), e);
		}
		HubConfig c = read("config.json", HubConfig.class, HubConfig::new);
		if (c != null) {
			config = c.normalize();
			write("config.json", config);
		} else {
			ok = false;
		}
		KitConfig k = read("kits.json", KitConfig.class, KitConfig::new);
		if (k != null) {
			kits = k.normalize();
			write("kits.json", kits);
		} else {
			ok = false;
		}
		TitleConfig t = read("titles.json", TitleConfig.class, TitleConfig::new);
		if (t != null) {
			titles = t.normalize();
			write("titles.json", titles);
		} else {
			ok = false;
		}
		CosmeticConfig cos = read("cosmetics.json", CosmeticConfig.class, CosmeticConfig::new);
		if (cos != null) {
			cosmetics = cos.normalize();
			write("cosmetics.json", cosmetics);
		} else {
			ok = false;
		}
		BedWarsConfig bw = read("bedwars.json", BedWarsConfig.class, BedWarsConfig::new);
		if (bw != null) {
			bedwars = bw.normalize();
			write("bedwars.json", bedwars);
		} else {
			ok = false;
		}
		PropHuntConfig ph = read("prophunt.json", PropHuntConfig.class, PropHuntConfig::new);
		if (ph != null) {
			prophunt = ph.normalize();
			write("prophunt.json", prophunt);
		} else {
			ok = false;
		}
		ProgressConfig pr = read("progress.json", ProgressConfig.class, ProgressConfig::new);
		if (pr != null) {
			progress = pr.normalize();
			write("progress.json", progress);
		} else {
			ok = false;
		}
		SkyWarsConfig sw = read("skywars.json", SkyWarsConfig.class, SkyWarsConfig::new);
		if (sw != null) {
			skywars = sw.normalize();
			write("skywars.json", skywars);
		} else {
			ok = false;
		}
		return ok;
	}

	public static void saveMain() {
		write("config.json", config);
	}

	private static <T> T read(String name, Class<T> type, Supplier<T> fallback) {
		Path file = dir().resolve(name);
		if (!Files.exists(file)) {
			return fallback.get();
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonReader jr = new JsonReader(r);
			jr.setStrictness(com.google.gson.Strictness.LENIENT);
			T value = GSON.fromJson(jr, type);
			return value == null ? fallback.get() : value;
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not read {}: {}. Keeping the previous settings; fix the file and run /battlehub reload.", file, e.getMessage());
			return null;
		}
	}

	private static void write(String name, Object value) {
		writeJson(dir().resolve(name), GSON.toJson(value));
	}

	/** Writes to a temporary file first, then renames it over the target, so a crash never leaves half a file. */
	public static void writeJson(Path file, String json) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			BattleHub.LOGGER.error("Could not write {}", file, e);
		}
	}
}
