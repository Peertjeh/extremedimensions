package net.battlehub.data;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.CoinHistory;
import net.battlehub.core.CosmeticDef;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.TitleDef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Player profiles as {@code <world>/battlehub/players/<uuid>.json}. All profiles are kept in memory (leaderboards
 * need them); files are written atomically after every match and on logout.
 */
public final class PlayerStore {
	private PlayerStore() {}

	private static final Map<UUID, PlayerProfile> PROFILES = new ConcurrentHashMap<>();
	/** Bots: a profile while they play, never saved and never on a leaderboard. */
	private static final Map<UUID, PlayerProfile> BOTS = new ConcurrentHashMap<>();
	private static Path dir;

	public static Path worldDir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(BattleHub.MOD_ID);
	}

	public static void load(MinecraftServer server) {
		PROFILES.clear();
		dir = worldDir(server).resolve("players");
		List<UUID> estimated = new ArrayList<>();
		try {
			Files.createDirectories(dir);
			try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
				for (Path f : files) {
					try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
						PlayerProfile p = ConfigManager.GSON.fromJson(r, PlayerProfile.class);
						if (p != null) {
							p.normalize();
							UUID id = UUID.fromString(f.getFileName().toString().replace(".json", ""));
							p.uuid = id.toString();
							if (CoinHistory.needsEstimate(p)) {
								estimateCoins(p);
								estimated.add(id);
							}
							PROFILES.put(id, p);
						}
					} catch (Exception e) {
						BattleHub.LOGGER.error("Skipping unreadable player file {}: {}", f, e.getMessage());
					}
				}
			}
		} catch (IOException e) {
			BattleHub.LOGGER.error("Could not read player data from {}", dir, e);
		}
		for (UUID id : estimated) {
			save(id);
		}
		BattleHub.LOGGER.info("Loaded {} player profiles", PROFILES.size());
	}

	/** Profiles from before 1.6 did not count coins earned; estimate them once (see CoinHistory). */
	private static void estimateCoins(PlayerProfile p) {
		HubConfig cfg = ConfigManager.config();
		long spent = 0;
		for (TitleDef t : ConfigManager.titles().titles) {
			if (t != null && t.id != null && p.ownedTitles.contains(t.id)) {
				spent += Math.max(0, t.value);
			}
		}
		for (CosmeticDef c : ConfigManager.cosmetics().cosmetics) {
			if (c != null && c.id != null && p.ownedCosmetics.contains(c.id)) {
				spent += Math.max(0, c.price);
			}
		}
		CoinHistory.estimate(p, cfg.rewards.duel, cfg.rewards.party, cfg.rewards.ranked, spent);
	}

	/** Profile for this id; created (not yet saved) when unknown. */
	public static PlayerProfile get(UUID id) {
		if (net.battlehub.bot.Bots.isBot(id)) {
			return BOTS.computeIfAbsent(id, k -> {
				PlayerProfile p = new PlayerProfile();
				p.uuid = k.toString();
				return p;
			});
		}
		return PROFILES.computeIfAbsent(id, k -> {
			PlayerProfile p = new PlayerProfile();
			p.uuid = k.toString();
			p.firstSeen = System.currentTimeMillis();
			return p;
		});
	}

	/** Profile of an online player; keeps the stored name current. */
	public static PlayerProfile get(ServerPlayer player) {
		PlayerProfile p = get(player.getUUID());
		String name = player.getGameProfile().name();
		if (name != null && !name.equals(p.name)) {
			p.name = name;
		}
		return p;
	}

	public static PlayerProfile find(String name) {
		if (name == null) {
			return null;
		}
		String n = name.toLowerCase(Locale.ROOT);
		for (PlayerProfile p : PROFILES.values()) {
			if (p.name != null && p.name.toLowerCase(Locale.ROOT).equals(n)) {
				return p;
			}
		}
		return null;
	}

	public static Collection<PlayerProfile> all() {
		return List.copyOf(PROFILES.values());
	}

	public static List<String> names() {
		List<String> out = new ArrayList<>();
		for (PlayerProfile p : PROFILES.values()) {
			if (p.name != null && !p.name.isBlank()) {
				out.add(p.name);
			}
		}
		return out;
	}

	public static void save(UUID id) {
		PlayerProfile p = PROFILES.get(id);
		if (p == null || dir == null) {
			return;
		}
		p.lastSeen = System.currentTimeMillis();
		ConfigManager.writeJson(dir.resolve(id + ".json"), ConfigManager.GSON.toJson(p));
	}

	public static void saveAll() {
		for (UUID id : PROFILES.keySet()) {
			save(id);
		}
	}

	public static void unload() {
		PROFILES.clear();
		BOTS.clear();
		dir = null;
	}
}
