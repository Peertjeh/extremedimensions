package net.battlehub.export;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.ToLongFunction;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.Leaderboard;
import net.battlehub.core.ModeStats;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.Travel;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.FeaturedMode;
import net.battlehub.progression.Seasons;
import net.battlehub.social.Clans;
import net.battlehub.tournament.WeeklyTournament;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Writes BattleHub's stats as JSON for a website: {@code players.json}, {@code leaderboards.json},
 * {@code clans.json}, {@code season.json}, {@code matches.json}, {@code tournaments.json} and {@code server.json},
 * plus a small {@code index.html} that shows them (written once; change it as you like). The data is gathered on
 * the server thread and written on another thread, file by file (each replaced in one go). Optionally served over
 * HTTP with CORS, so a website elsewhere can fetch the files.
 */
public final class StatsExport {
	private StatsExport() {}

	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BattleHub stats export");
		t.setDaemon(true);
		return t;
	});
	private static Object http;
	private static int httpPort = -1;
	private static long ticks;

	private static HubConfig.Export cfg() {
		return ConfigManager.config().export;
	}

	public static Path dir() {
		String d = cfg().directory;
		return d == null || d.isBlank() ? ConfigManager.dir().resolve("export") : Path.of(d.trim());
	}

	/** Every tick: writes the files every few minutes and keeps the HTTP server in line with the settings. */
	public static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % 100 == 0) {
			syncHttp();
		}
		if (cfg().enabled && ticks % (cfg().everyMinutes * 1200L) == 0) {
			write(server, false);
		}
	}

	/** Gathers everything now and writes it (on the writer thread, or right here when {@code wait}). */
	public static boolean write(MinecraftServer server, boolean wait) {
		Map<String, String> files;
		try {
			files = gather(server);
		} catch (RuntimeException e) {
			BattleHub.LOGGER.error("Stats export failed", e);
			return false;
		}
		Path out = dir();
		Runnable job = () -> {
			try {
				Files.createDirectories(out);
				for (Map.Entry<String, String> f : files.entrySet()) {
					Path tmp = out.resolve(f.getKey() + ".tmp");
					Files.writeString(tmp, f.getValue(), StandardCharsets.UTF_8);
					Files.move(tmp, out.resolve(f.getKey()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
				Path index = out.resolve("index.html");
				if (!Files.exists(index)) {
					Files.writeString(index, IndexPage.HTML, StandardCharsets.UTF_8);
				}
			} catch (IOException | RuntimeException e) {
				BattleHub.LOGGER.error("Stats export could not write to {}: {}", out, e.toString());
			}
		};
		if (wait) {
			job.run();
		} else {
			WRITER.execute(job);
		}
		return true;
	}

	static Map<String, String> gather(MinecraftServer server) {
		Collection<PlayerProfile> all = new ArrayList<>(PlayerStore.all());
		all.removeIf(p -> isBot(p));
		Map<String, String> out = new LinkedHashMap<>();
		long now = System.currentTimeMillis();
		out.put("players.json", json(Map.of("generated", now, "players", players(all))));
		out.put("leaderboards.json", json(Map.of("generated", now, "boards", boards(all))));
		out.put("clans.json", json(Map.of("generated", now, "clans", clans())));
		out.put("season.json", json(season()));
		out.put("matches.json", json(Map.of("generated", now, "matches", MatchLog.recent())));
		out.put("tournaments.json", json(WeeklyTournament.exportView()));
		out.put("server.json", json(serverInfo(server, now)));
		return out;
	}

	private static boolean isBot(PlayerProfile p) {
		try {
			return Bots.isBot(java.util.UUID.fromString(p.uuid));
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	private static String json(Object o) {
		return ConfigManager.GSON.toJson(o);
	}

	private static Map<String, Object> stats(ModeStats s) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("wins", s.wins);
		m.put("losses", s.losses);
		m.put("kills", s.kills);
		m.put("deaths", s.deaths);
		m.put("played", s.played);
		m.put("bestStreak", s.bestStreak);
		return m;
	}

	private static List<Map<String, Object>> players(Collection<PlayerProfile> all) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (PlayerProfile p : all) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("uuid", p.uuid);
			m.put("name", p.name);
			m.put("level", p.level);
			m.put("prestige", p.prestige);
			m.put("xp", p.xp);
			m.put("coins", p.coins);
			m.put("coinsEarned", p.coinsEarned);
			m.put("overall", stats(p.overall));
			Map<String, Object> modes = new LinkedHashMap<>();
			for (Map.Entry<String, ModeStats> e : p.modes.entrySet()) {
				if (e.getValue() != null && e.getValue().played > 0) modes.put(e.getKey(), stats(e.getValue()));
			}
			m.put("modes", modes);
			m.put("elo", p.elo);
			m.put("seasonPeak", p.seasonPeak);
			m.put("bestTimes", p.bestTimes);
			Clans.Clan c = clanOf(p);
			m.put("clan", c == null ? null : Map.of("name", c.name, "tag", c.tag));
			m.put("achievements", p.achievements.size());
			m.put("questsDone", p.questsDone);
			m.put("tournamentsWon", p.tournamentsWon);
			m.put("weeklyWins", p.weeklyWins);
			m.put("passTier", p.passTier);
			m.put("firstSeen", p.firstSeen);
			m.put("lastSeen", p.lastSeen);
			list.add(m);
		}
		return list;
	}

	private static Clans.Clan clanOf(PlayerProfile p) {
		try {
			return Clans.of(java.util.UUID.fromString(p.uuid));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static List<Map<String, Object>> board(Collection<PlayerProfile> all, ToLongFunction<PlayerProfile> value, long floor) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Leaderboard.Row<PlayerProfile> r : Leaderboard.top(all, value, cfg().top, floor)) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("rank", r.rank());
			m.put("name", r.item().name);
			m.put("uuid", r.item().uuid);
			m.put("value", r.value());
			rows.add(m);
		}
		return rows;
	}

	private static Map<String, Object> boards(Collection<PlayerProfile> all) {
		Map<String, Object> b = new LinkedHashMap<>();
		int minKills = ConfigManager.config().leaderboards.minKillsForKd;
		b.put("kills", board(all, p -> p.overall.kills, 0));
		b.put("wins", board(all, p -> p.overall.wins, 0));
		b.put("deaths", board(all, p -> p.overall.deaths, 0));
		// K/D times 100 (whole numbers), only with enough kills
		b.put("kd", board(all, p -> p.overall.kills >= minKills ? Math.round(p.overall.kdr() * 100) : Long.MIN_VALUE, -1));
		b.put("level", board(all, p -> p.xp, 0));
		b.put("prestige", board(all, p -> p.prestige, 0));
		b.put("coinsEarned", board(all, p -> p.coinsEarned, 0));
		b.put("tournaments", board(all, p -> p.tournamentsWon, 0));
		b.put("weekly", board(all, p -> p.weeklyWins, 0));
		Map<String, Object> elo = new LinkedHashMap<>();
		for (GameMode m : GameMode.duels()) {
			elo.put(m.id(), board(all, p -> p.elo.containsKey(m.id()) ? p.elo.get(m.id()) : Long.MIN_VALUE, Long.MIN_VALUE));
		}
		b.put("elo", elo);
		Map<String, Object> modes = new LinkedHashMap<>();
		for (GameMode m : GameMode.values()) {
			modes.put(m.id(), board(all, p -> p.peek(m).wins, 0));
		}
		b.put("winsPerMode", modes);
		// Parkour: fastest first (milliseconds)
		Map<String, Object> times = new LinkedHashMap<>();
		for (String d : List.of("parkour_easy", "parkour_medium", "parkour_hard", "parkour_extreme")) {
			List<Map<String, Object>> rows = board(all, p -> p.bestTimes.containsKey(d) ? -p.bestTimes.get(d) : Long.MIN_VALUE, Long.MIN_VALUE);
			for (Map<String, Object> r : rows) r.put("value", -((Long) r.get("value")));
			times.put(d, rows);
		}
		b.put("parkourTimes", times);
		return b;
	}

	private static List<Map<String, Object>> clans() {
		List<Map<String, Object>> list = new ArrayList<>();
		int rank = 1;
		for (Clans.Clan c : Clans.top(false)) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("rank", rank++);
			m.put("name", c.name);
			m.put("tag", c.tag);
			m.put("members", c.members.size());
			m.put("points", c.points);
			m.put("weeklyPoints", c.weeklyPoints);
			m.put("warsWon", c.warsWon);
			list.add(m);
		}
		return list;
	}

	private static Map<String, Object> season() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("enabled", Seasons.enabled());
		m.put("season", Seasons.current());
		m.put("daysLeft", Seasons.daysLeft());
		return m;
	}

	private static Map<String, Object> serverInfo(MinecraftServer server, long now) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("generated", now);
		m.put("version", net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(BattleHub.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		int online = 0;
		int inHub = 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(p)) continue;
			online++;
			if (Travel.inBattleHub(p)) inHub++;
		}
		m.put("online", online);
		m.put("inBattleHub", inHub);
		Map<String, Integer> playing = new LinkedHashMap<>();
		for (Match match : Matches.all()) {
			if (match.phase() != Match.Phase.CLOSED) playing.merge(match.mode.id(), 1, Integer::sum);
		}
		m.put("matchesPerMode", playing);
		GameMode featured = FeaturedMode.today();
		m.put("featuredMode", featured == null ? null : featured.id());
		m.put("nextWeeklyTournament", WeeklyTournament.next().toInstant().toEpochMilli());
		return m;
	}

	// ------------------------------------------------------------------ HTTP

	private static void syncHttp() {
		boolean want = cfg().enabled && cfg().serveHttp;
		if (want && (http == null || httpPort != cfg().port)) {
			stopHttp();
			startHttp(cfg().port);
		} else if (!want && http != null) {
			stopHttp();
		}
	}

	private static void startHttp(int port) {
		try {
			com.sun.net.httpserver.HttpServer s = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
			s.createContext("/", ex -> {
				try {
					String name = ex.getRequestURI().getPath().replaceFirst("^/+", "");
					if (name.isEmpty()) name = "index.html";
					Path f = dir().resolve(name).normalize();
					if (!f.startsWith(dir().normalize()) || !Files.isRegularFile(f) || name.contains("..")) {
						ex.sendResponseHeaders(404, -1);
						return;
					}
					byte[] body = Files.readAllBytes(f);
					ex.getResponseHeaders().add("Content-Type", name.endsWith(".html") ? "text/html; charset=utf-8"
							: "application/json; charset=utf-8");
					ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
					ex.getResponseHeaders().add("Cache-Control", "max-age=30");
					ex.sendResponseHeaders(200, body.length);
					try (OutputStream os = ex.getResponseBody()) {
						os.write(body);
					}
				} finally {
					ex.close();
				}
			});
			s.start();
			http = s;
			httpPort = port;
			BattleHub.LOGGER.info("BattleHub stats are served on port {}", port);
		} catch (IOException | RuntimeException | LinkageError e) {
			BattleHub.LOGGER.error("BattleHub stats could not be served on port {}: {}", port, e.toString());
			http = null;
			httpPort = port;
		}
	}

	private static void stopHttp() {
		if (http instanceof com.sun.net.httpserver.HttpServer s) {
			s.stop(0);
		}
		http = null;
		httpPort = -1;
	}

	public static boolean serving() {
		return http != null;
	}

	public static void stop() {
		stopHttp();
		ticks = 0;
	}
}
