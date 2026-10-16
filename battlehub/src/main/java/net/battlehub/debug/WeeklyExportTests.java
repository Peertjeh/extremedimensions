package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.battlehub.config.ConfigManager;
import net.battlehub.data.PlayerStore;
import net.battlehub.export.MatchLog;
import net.battlehub.export.StatsExport;
import net.battlehub.tournament.Tournament;
import net.battlehub.tournament.Tournaments;
import net.battlehub.tournament.WeeklyTournament;
import net.minecraft.server.MinecraftServer;

/** The weekly tournament (sign-up, start, prizes, history, too few players) and the stats export with its web server. */
final class WeeklyExportTests {
	private WeeklyExportTests() {}

	private static final String A = "BHTestA";
	private static final String B = "BHTestB";

	static void all(MinecraftServer server, Script s) {
		weekly(server, s);
		tooFew(server, s);
		export(server, s);
	}

	private static void weekly(MinecraftServer server, Script s) {
		String tag = "weekly";
		SelfTest.reset(s, tag);
		s.run(tag + ": sign-ups open, A and B sign up", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			var w = ConfigManager.config().weekly;
			w.minPlayers = 2;
			w.kit = "sword";
			w.teamSize = 1;
			VARS.put("wHistory", WeeklyTournament.history().size());
			VARS.put("wWins", PlayerStore.get(p(A)).weeklyWins);
			VARS.put("wCoins", PlayerStore.get(p(A)).coins);
			WeeklyTournament.openNow(server, 2);
			WeeklyTournament.join(p(A));
			WeeklyTournament.join(p(B));
		});
		s.check(tag + ": two signed up, signing up twice does nothing", () -> !WeeklyTournament.join(p(A))
				&& WeeklyTournament.signups() == 2 && WeeklyTournament.signupsOpen());
		s.waitFor(tag + ": the weekly tournament starts", 200, () -> {
			Tournament t = Tournaments.of(p(A).getUUID());
			if (t == null || !t.weekly) return false;
			VARS.put("tournament", t);
			return true;
		});
		s.check(tag + ": sign-ups are closed again", () -> !WeeklyTournament.signupsOpen() && WeeklyTournament.signups() == 0);
		s.run(tag + ": B gives up", () -> net.battlehub.command.HubCommands.hub(p(B)));
		s.waitFor(tag + ": A wins it, with the weekly prize and in the history", 600, () -> {
			Tournament t = (Tournament) VARS.get("tournament");
			var prof = PlayerStore.get(p(A));
			return t.isDone() && prof.weeklyWins == (int) VARS.get("wWins") + 1
					&& prof.coins >= (long) VARS.get("wCoins") + ConfigManager.config().weekly.winnerCoins
					&& WeeklyTournament.history().size() == (int) VARS.get("wHistory") + 1
					&& WeeklyTournament.history().get(WeeklyTournament.history().size() - 1).winners.contains(A);
		});
		s.check(tag + ": the Weekly Champion title is A's now", () -> {
			var t = ConfigManager.titles().byId("weekly_champion");
			return t != null && net.battlehub.core.TitleRules.unlocked(t, PlayerStore.get(p(A)), 1000);
		});
	}

	private static void tooFew(MinecraftServer server, Script s) {
		String tag = "weekly too few";
		SelfTest.reset(s, tag);
		s.run(tag + ": only A signs up (two needed)", () -> {
			SelfTest.join(server, A);
			WeeklyTournament.openNow(server, 1);
			WeeklyTournament.join(p(A));
		});
		s.waitFor(tag + ": it is called off, no tournament", 100, () -> !WeeklyTournament.signupsOpen()
				&& Tournaments.of(p(A).getUUID()) == null);
	}

	private static Path dir() {
		return ConfigManager.dir().resolve("export-selftest");
	}

	private static JsonObject read(String name) throws java.io.IOException {
		return JsonParser.parseString(Files.readString(dir().resolve(name))).getAsJsonObject();
	}

	private static void export(MinecraftServer server, Script s) {
		String tag = "export";
		SelfTest.reset(s, tag);
		s.run(tag + ": switched on (with the web server), written now", () -> {
			SelfTest.join(server, A);
			var e = ConfigManager.config().export;
			e.enabled = true;
			e.directory = dir().toString();
			e.serveHttp = true;
			e.port = 25900 + (int) (System.nanoTime() % 300);
			VARS.put("exportOk", StatsExport.write(server, true));
		});
		s.check(tag + ": every file is there and reads as JSON", () -> {
			try {
				if (!Boolean.TRUE.equals(VARS.get("exportOk"))) return false;
				for (String f : new String[] {"players.json", "leaderboards.json", "clans.json", "season.json", "matches.json",
						"tournaments.json", "server.json", "index.html"}) {
					if (!Files.isRegularFile(dir().resolve(f))) return false;
				}
				JsonObject players = read("players.json");
				boolean hasA = false;
				for (var el : players.getAsJsonArray("players")) {
					hasA |= el.getAsJsonObject().get("name").getAsString().equals(A);
				}
				JsonObject boards = read("leaderboards.json").getAsJsonObject("boards");
				JsonObject srv = read("server.json");
				return hasA && boards.has("kills") && boards.getAsJsonObject("elo").has("sword") && srv.get("online").getAsInt() >= 1
						&& read("matches.json").getAsJsonArray("matches").size() == MatchLog.recent().size()
						&& read("tournaments.json").getAsJsonArray("history").size() >= 1;
			} catch (java.io.IOException | RuntimeException ex) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST export check: {}", ex.toString());
				return false;
			}
		});
		s.waitFor(tag + ": the web server is up", 140, StatsExport::serving);
		s.check(tag + ": a website can fetch the files (CORS), nothing outside the folder", () -> {
			try {
				int port = ConfigManager.config().export.port;
				HttpClient http = HttpClient.newHttpClient();
				HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/leaderboards.json"))
						.timeout(java.time.Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
				HttpResponse<String> bad = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/players.json.tmp"))
						.timeout(java.time.Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
				return r.statusCode() == 200 && "*".equals(r.headers().firstValue("Access-Control-Allow-Origin").orElse(""))
						&& r.body().equals(Files.readString(dir().resolve("leaderboards.json"))) && bad.statusCode() == 404;
			} catch (java.io.IOException | InterruptedException ex) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST export fetch: {}", ex.toString());
				return false;
			}
		});
		s.run(tag + ": switched off", () -> {
			var e = ConfigManager.config().export;
			e.enabled = false;
			e.serveHttp = false;
			e.directory = "";
		});
		s.waitFor(tag + ": the web server stops", 140, () -> !StatsExport.serving());
	}
}
