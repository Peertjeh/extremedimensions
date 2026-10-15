package net.battlehub.progression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.Progression;
import net.battlehub.core.Season;
import net.battlehub.core.SeasonRecord;
import net.battlehub.core.SeasonRules;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Ranked seasons. The season number follows from the date (fixed length from an anchor day). When a new season
 * starts, every profile is closed: peak and tier recorded, coins paid per tier, ratings softly reset. Which
 * season was closed last is kept in {@code <world>/battlehub/season.json}, so a season that ended while the
 * server was off is closed at the next start.
 */
public final class Seasons {
	private Seasons() {}

	private static int closed = -1;
	private static Path file;
	private static int ticks;

	private static HubConfig.Seasons cfg() {
		return ConfigManager.config().seasons;
	}

	public static boolean enabled() {
		return cfg().enabled;
	}

	public static long anchorDay() {
		try {
			return LocalDate.parse(cfg().start.trim()).toEpochDay();
		} catch (RuntimeException e) {
			return LocalDate.of(2026, 10, 1).toEpochDay();
		}
	}

	public static long today() {
		return LocalDate.now().toEpochDay();
	}

	/** The season now (0 when seasons are off). */
	public static int current() {
		return enabled() ? Season.number(today(), anchorDay(), cfg().lengthDays) : 0;
	}

	public static long daysLeft() {
		return Season.daysLeft(today(), anchorDay(), cfg().lengthDays);
	}

	public static void init(MinecraftServer server) {
		file = PlayerStore.worldDir(server).resolve("season.json");
		closed = -1;
		try {
			if (Files.exists(file)) {
				JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				closed = o.has("season") ? o.get("season").getAsInt() : -1;
			}
		} catch (Exception e) {
			BattleHub.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
		}
		check(server);
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % 1200 == 0) {
			check(server);
		}
	}

	private static void check(MinecraftServer server) {
		if (!enabled() || file == null) {
			return;
		}
		int now = current();
		if (closed < 0) {
			// First start with seasons: this one simply begins
			closed = now;
			write();
			return;
		}
		if (now > closed) {
			end(server, closed, now);
			closed = now;
			write();
		}
	}

	private static void write() {
		JsonObject o = new JsonObject();
		o.addProperty("season", closed);
		ConfigManager.writeJson(file, ConfigManager.GSON.toJson(o));
	}

	/** Closes season {@code ended} for every profile and tells the players who are online. */
	private static void end(MinecraftServer server, int ended, int next) {
		HubConfig.Seasons c = cfg();
		int closedProfiles = 0;
		for (PlayerProfile p : PlayerStore.all()) {
			SeasonRecord r = SeasonRules.close(p, ended, next, Ranks.tiers(), Ranks.startElo(), c.keep, c.rewards, c.topDivisionStep);
			if (r != null) {
				Progression.addCoins(p, r.coins);
				closedProfiles++;
			}
			try {
				PlayerStore.save(java.util.UUID.fromString(p.uuid));
			} catch (IllegalArgumentException ignored) {
				// A profile without a valid id is not saved
			}
		}
		BattleHub.LOGGER.info("Ranked season {} ended ({} players ranked); season {} started", ended, closedProfiles, next);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerProfile prof = PlayerStore.get(p);
			SeasonRecord r = prof.pastSeasons.isEmpty() ? null : prof.pastSeasons.get(prof.pastSeasons.size() - 1);
			Msg.send(p, "season.started", next);
			if (r != null && r.season == ended) {
				Msg.send(p, "season.ended", ended, rankName(p, r.peakElo), r.coins);
			}
			Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
		}
	}

	/** For the self-test: closes the current season right now (the date-based numbering is not changed). */
	public static void endForTest(MinecraftServer server) {
		int now = Math.max(1, current());
		end(server, now, now + 1);
	}

	/** Makes a profile's season counters belong to this season (a new player, or one created mid-season). */
	public static void ensure(PlayerProfile p) {
		int now = current();
		if (now > 0 && p.season != now && p.seasonPlayed.isEmpty()) {
			p.season = now;
		}
	}

	/** A ranked match changed this rating. */
	public static void played(PlayerProfile p, GameMode mode, int newElo) {
		ensure(p);
		SeasonRules.played(p, mode, newElo);
	}

	/** How many placement matches this player still has in this mode (0: placed). */
	public static int placementsLeft(PlayerProfile p, GameMode mode) {
		if (!enabled()) {
			return 0;
		}
		ensure(p);
		return Math.max(0, cfg().placements - SeasonRules.playedThisSeason(p, mode));
	}

	/** K-factor for this player's next ranked match: double during placements. */
	public static int kFactor(PlayerProfile p, GameMode mode, int baseK) {
		return placementsLeft(p, mode) > 0 ? baseK * 2 : baseK;
	}

	/** "Gold II". */
	public static Component rankName(ServerPlayer viewer, int elo) {
		int division = Season.division(Ranks.tiers(), elo, cfg().topDivisionStep);
		return Lang.tr(viewer, "season.rank", Ranks.tierName(viewer, elo), Season.roman(division));
	}
}
