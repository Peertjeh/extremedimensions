package net.battlehub.tournament;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.reflect.TypeToken;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.Progression;
import net.battlehub.core.WeeklySchedule;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.Travel;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * The weekly tournament: at a set day and time (config.json, weekly). Sign-ups open some minutes before with an
 * announcement and a [Sign up] button; at the start everyone who signed up and is free in BattleHub plays a
 * tournament with bigger prizes. Once a week (also across restarts); the results are kept for the website export.
 */
public final class WeeklyTournament {
	private WeeklyTournament() {}

	/** One finished weekly tournament. */
	public static final class Result {
		public String week = "";
		public long time;
		public String kit = "";
		public int players;
		public List<String> winners = new ArrayList<>();
		public List<String> finalists = new ArrayList<>();
	}

	private static final class State {
		String lastWeek = "";
		List<Result> history = new ArrayList<>();
	}

	private static State state = new State();
	private static Path file;
	private static final Set<UUID> SIGNED = new LinkedHashSet<>();
	private static ZonedDateTime startAt;
	private static String openWeek;
	private static boolean warned5;
	private static boolean warned1;

	private static HubConfig.WeeklyTournament cfg() {
		return ConfigManager.config().weekly;
	}

	private static ZoneId zone() {
		try {
			return cfg().zone == null || cfg().zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(cfg().zone.trim());
		} catch (RuntimeException e) {
			return ZoneId.systemDefault();
		}
	}

	public static void load(MinecraftServer server) {
		file = PlayerStore.worldDir(server).resolve("weekly.json");
		state = new State();
		clearSignups();
		if (Files.exists(file)) {
			try {
				State s = ConfigManager.GSON.fromJson(Files.readString(file), new TypeToken<State>() {}.getType());
				if (s != null) {
					state = s;
					if (state.history == null) state.history = new ArrayList<>();
					if (state.lastWeek == null) state.lastWeek = "";
				}
			} catch (Exception e) {
				BattleHub.LOGGER.error("Could not read {}: {}", file, e.getMessage());
			}
		}
	}

	private static void save() {
		if (file != null) {
			ConfigManager.writeJson(file, ConfigManager.GSON.toJson(state));
		}
	}

	/** When the next weekly tournament starts (for menus and the export). */
	public static ZonedDateTime next() {
		if (startAt != null) return startAt;
		return WeeklySchedule.nextStart(ZonedDateTime.now(zone()), WeeklySchedule.day(cfg().day), WeeklySchedule.time(cfg().time));
	}

	public static boolean signupsOpen() {
		return startAt != null;
	}

	/** Twice a second. */
	public static void tick(MinecraftServer server) {
		ZonedDateTime now = ZonedDateTime.now(zone());
		if (startAt == null) {
			if (!cfg().enabled) return;
			ZonedDateTime next = WeeklySchedule.nextStart(now, WeeklySchedule.day(cfg().day), WeeklySchedule.time(cfg().time));
			String week = WeeklySchedule.weekKey(next);
			if (!week.equals(state.lastWeek) && WeeklySchedule.signupOpen(now, next, cfg().signupMinutes)) {
				open(server, next, week);
			}
			return;
		}
		long left = WeeklySchedule.secondsUntil(now, startAt);
		if (left <= 300 && !warned5 && left > 60) {
			warned5 = true;
			announce(server, "weekly.reminder", 5);
		}
		if (left <= 60 && !warned1 && left > 0) {
			warned1 = true;
			announce(server, "weekly.reminder", 1);
		}
		if (left <= 0) {
			begin(server);
		}
	}

	private static void open(MinecraftServer server, ZonedDateTime start, String week) {
		startAt = start;
		openWeek = week;
		SIGNED.clear();
		warned5 = false;
		warned1 = false;
		long minutes = Math.max(1, (WeeklySchedule.secondsUntil(ZonedDateTime.now(zone()), start) + 59) / 60);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(p)) continue;
			Msg.send(p, "weekly.open", minutes, Lang.tr(p, "weekly.kit." + cfg().kit.toLowerCase(java.util.Locale.ROOT)),
					cfg().winnerCoins);
			Msg.raw(p, Msg.button(p, "weekly.join_button", "weekly.join_hover", "/battlehub weekly join"));
			Msg.sound(p, SoundEvents.NOTE_BLOCK_BELL, 1f, 1f);
		}
	}

	private static void announce(MinecraftServer server, String key, Object arg) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(p)) continue;
			Msg.send(p, key, arg, SIGNED.size());
			if (!SIGNED.contains(p.getUUID())) {
				Msg.raw(p, Msg.button(p, "weekly.join_button", "weekly.join_hover", "/battlehub weekly join"));
			}
		}
	}

	public static boolean join(ServerPlayer p) {
		if (startAt == null) {
			Msg.send(p, "weekly.closed", WeeklySchedule.secondsUntil(ZonedDateTime.now(zone()), next()) / 3600);
			return false;
		}
		if (!SIGNED.add(p.getUUID())) {
			Msg.send(p, "weekly.already");
			return false;
		}
		Msg.send(p, "weekly.joined", SIGNED.size());
		Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.6f);
		return true;
	}

	public static boolean leave(ServerPlayer p) {
		if (SIGNED.remove(p.getUUID())) {
			Msg.send(p, "weekly.left");
			return true;
		}
		return false;
	}

	public static boolean signedUp(UUID id) {
		return SIGNED.contains(id);
	}

	public static int signups() {
		return SIGNED.size();
	}

	/** Start time: everyone who signed up and is free in BattleHub plays. */
	private static void begin(MinecraftServer server) {
		List<ServerPlayer> players = new ArrayList<>();
		for (UUID id : SIGNED) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && Travel.inBattleHub(p) && Sessions.inLobby(id) && !Tournaments.isIn(id)) {
				players.add(p);
			}
		}
		state.lastWeek = openWeek == null ? "" : openWeek;
		save();
		clearSignups();
		if (players.size() < cfg().minPlayers) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (!Bots.isBot(p)) Msg.send(p, "weekly.cancelled", players.size(), cfg().minPlayers);
			}
			return;
		}
		for (ServerPlayer p : players) {
			Queues.leaveAll(p);
		}
		Tournament t = Tournaments.startWeekly(server, players, cfg().kit, cfg().teamSize);
		if (t == null) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!Bots.isBot(p)) Msg.send(p, "weekly.started", players.size());
		}
	}

	private static void clearSignups() {
		SIGNED.clear();
		startAt = null;
		openWeek = null;
		warned5 = false;
		warned1 = false;
	}

	/** From the tournament: the weekly prizes and the history. */
	static void finished(Tournament t, List<UUID> champions, List<UUID> finalists, String championNames) {
		MinecraftServer server = BattleHub.server();
		Result r = new Result();
		r.week = WeeklySchedule.weekKey(ZonedDateTime.now(zone()));
		r.time = System.currentTimeMillis();
		r.kit = t.kit.id();
		r.players = t.size();
		for (UUID id : champions) {
			PlayerProfile prof = PlayerStore.get(id);
			prof.weeklyWins++;
			Progression.addCoins(prof, cfg().winnerCoins);
			PlayerStore.save(id);
			r.winners.add(prof.name);
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
			if (p != null) Msg.send(p, "weekly.prize", cfg().winnerCoins);
		}
		for (UUID id : finalists) {
			PlayerProfile prof = PlayerStore.get(id);
			Progression.addCoins(prof, cfg().finalistCoins);
			PlayerStore.save(id);
			r.finalists.add(prof.name);
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
			if (p != null) Msg.send(p, "weekly.prize", cfg().finalistCoins);
		}
		state.history.add(r);
		while (state.history.size() > 104) {
			state.history.remove(0);
		}
		save();
		if (server != null) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (!Bots.isBot(p)) Msg.send(p, "weekly.champion", championNames);
			}
		}
	}

	public static List<Result> history() {
		return List.copyOf(state.history);
	}

	/** Admin and self-test: open sign-ups now, starting in {@code seconds}. */
	public static void openNow(MinecraftServer server, int seconds) {
		ZonedDateTime start = ZonedDateTime.now(zone()).plusSeconds(Math.max(1, seconds));
		open(server, start, WeeklySchedule.weekKey(start) + "-manual");
	}

	public static void clear() {
		clearSignups();
		state = new State();
		file = null;
	}

	/** For the export: the stored state as a map. */
	public static Map<String, Object> exportView() {
		return Map.of("next", next().toInstant().toEpochMilli(), "signupsOpen", signupsOpen(), "signups", SIGNED.size(),
				"history", history());
	}
}
