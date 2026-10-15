package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.ArenaSlots;
import net.battlehub.bot.BotPlayer;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.BotLevel;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.games.ParkourMatch;
import net.battlehub.gui.HubMenu;
import net.battlehub.hub.HubNpcs;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.FeaturedMode;
import net.battlehub.queue.Parties;
import net.battlehub.queue.Queues;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Bots and the lobby: practice duels (sword and bow) where the bot really fights, queues and party lobbies filled
 * with bots, a bot running a parkour course, nothing saved for bots and no stats from bot matches; the lobby
 * characters and the mode of the day.
 */
final class BotTests {
	private BotTests() {}

	static final String A = "BHTestA";

	private static HubConfig.Bots cfg() {
		return ConfigManager.config().bots;
	}

	private static Match matchOfA() {
		return Matches.of(p(A).getUUID());
	}

	private static List<BotPlayer> botsIn(Match m) {
		List<BotPlayer> out = new ArrayList<>();
		if (m == null) return out;
		for (UUID id : m.participants()) {
			ServerPlayer pl = m.player(id);
			if (pl instanceof BotPlayer b) out.add(b);
		}
		return out;
	}

	private static void endAndClean(Script s, String tag) {
		s.run(tag + ": end the match", () -> {
			Match m = matchOfA();
			if (m != null) Matches.end(m.id);
		});
		s.waitFor(tag + ": bots gone and arenas free", 600, () -> Bots.online() == 0 && ArenaSlots.busy() == 0);
	}

	static void all(MinecraftServer server, Script s) {
		lobby(server, s);
		practiceSword(server, s);
		practiceBow(server, s);
		queueFill(server, s);
		partyFill(server, s);
		parkour(server, s);
		s.run("bots: filling off again for the other tests", () -> {
			cfg().fillQueues = false;
			cfg().fillAfterSeconds = 15;
			cfg().partyFillTo = 4;
		});
	}

	/** The characters on the plaza and the mode of the day. */
	private static void lobby(MinecraftServer server, Script s) {
		String tag = "npcs";
		SelfTest.reset(s, tag);
		s.run(tag + ": A in the lobby; sumo is the mode of the day", () -> {
			SelfTest.join(server, A);
			VARS.put("featuredModes", new ArrayList<>(ConfigManager.config().featured.modes));
			ConfigManager.config().featured.enabled = true;
			ConfigManager.config().featured.modes = new ArrayList<>(List.of("sumo"));
		});
		s.waitFor(tag + ": eight characters with click boxes, on A's client", 200, () -> HubNpcs.count() == 8
				&& HubNpcs.hitboxes() == 8 && HubNpcs.showing(p(A).getUUID()) && p(A).clientEntities().containsAll(HubNpcs.entityIds()));
		s.waitFor(tag + ": labels show the name, how many play, and the mode of the day on the party games", 60, () -> {
			String bw = HubNpcs.label(p(A).getUUID(), "bed_wars");
			String party = HubNpcs.label(p(A).getUUID(), "party");
			return bw.contains("Bed Wars") && bw.contains("playing") && party.contains("★") && !bw.contains("★")
					&& FeaturedMode.is(GameMode.SUMO);
		});
		s.run(tag + ": A clicks the practice character", () -> HubNpcs.click(p(A), HubNpcs.hitbox("practice")));
		s.check(tag + ": the practice menu opens", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": close it; no mode of the day for the other tests", () -> {
			p(A).closeContainer();
			@SuppressWarnings("unchecked")
			List<String> modes = (List<String>) VARS.get("featuredModes");
			ConfigManager.config().featured.modes = modes;
			ConfigManager.config().featured.enabled = false;
		});
		s.check(tag + ": bot join and leave messages are not shown in chat", () ->
				Bots.silenced(Component.translatable("multiplayer.player.joined", "Bot_Nova"))
						&& !Bots.silenced(Component.translatable("multiplayer.player.joined", "Steve"))
						&& Bots.isBot(Bots.uuid("Bot_Nova")) && !Bots.isBot(p(A).getUUID()));
	}

	/** A sword duel against a hard bot: it walks up and hits; A wins; no stats for anyone, half the coins, nothing saved. */
	private static void practiceSword(MinecraftServer server, Script s) {
		String tag = "bots sword";
		SelfTest.reset(s, tag);
		s.run(tag + ": A starts a practice duel against a hard bot", () -> {
			SelfTest.join(server, A);
			PlayerProfile prof = PlayerStore.get(p(A).getUUID());
			VARS.put("botWins", prof.overall.wins);
			VARS.put("botPlayed", prof.overall.played);
			VARS.put("botCoins", prof.coins);
			Bots.practice(p(A), GameMode.SWORD, BotLevel.HARD);
		});
		s.waitFor(tag + ": the duel runs with one bot", 600, () -> {
			Match m = matchOfA();
			return m != null && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.waitFor(tag + ": the bot comes over and hits A", 400, () -> p(A).getHealth() < p(A).getMaxHealth());
		s.run(tag + ": A wins", () -> {
			BotPlayer bot = botsIn(matchOfA()).get(0);
			VARS.put("bot", bot.getUUID());
			bot.hurtServer(bot.level(), bot.damageSources().playerAttack(p(A)), 1000f);
		});
		s.waitFor(tag + ": match over", 200, () -> matchOfA() == null || matchOfA().phase() == Match.Phase.ENDING
				|| matchOfA().phase() == Match.Phase.CLOSED);
		s.check(tag + ": no stats from a bot match, but some coins", () -> {
			PlayerProfile prof = PlayerStore.get(p(A).getUUID());
			return prof.overall.wins == (int) VARS.get("botWins") && prof.overall.played == (int) VARS.get("botPlayed")
					&& prof.coins > (long) VARS.get("botCoins");
		});
		s.waitFor(tag + ": the bot leaves after the match", 400, () -> Bots.online() == 0);
		s.check(tag + ": nothing saved for the bot", () -> {
			UUID bot = (UUID) VARS.get("bot");
			boolean noFile = !java.nio.file.Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(bot + ".dat"));
			boolean noProfile = PlayerStore.all().stream().noneMatch(pr -> pr.uuid.equals(bot.toString()));
			return noFile && noProfile;
		});
		s.waitFor(tag + ": arena free", 600, () -> ArenaSlots.busy() == 0);
	}

	/** A bow duel: the bot keeps its distance and shoots. */
	private static void practiceBow(MinecraftServer server, Script s) {
		String tag = "bots bow";
		SelfTest.reset(s, tag);
		s.run(tag + ": A starts a bow duel against a normal bot", () -> {
			SelfTest.join(server, A);
			Bots.practice(p(A), GameMode.BOW, BotLevel.NORMAL);
		});
		s.waitFor(tag + ": running", 600, () -> matchOfA() != null && matchOfA().phase() == Match.Phase.RUNNING);
		s.waitFor(tag + ": the bot's arrows hit A", 600, () -> p(A).getHealth() < p(A).getMaxHealth());
		endAndClean(s, tag);
	}

	/** Alone in a casual queue: a bot joins after a while. Ranked: never. */
	private static void queueFill(MinecraftServer server, Script s) {
		String tag = "bots queue";
		SelfTest.reset(s, tag);
		s.run(tag + ": filling on, after one second; A queues for ranked sword", () -> {
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			SelfTest.join(server, A);
			Queues.join(p(A), GameMode.SWORD, true, DuelFormat.BO1);
		});
		s.pause(80);
		s.check(tag + ": no bot in ranked", () -> matchOfA() == null && Bots.online() == 0);
		s.run(tag + ": A queues for casual axe", () -> {
			Queues.leaveAll(p(A));
			Queues.join(p(A), GameMode.AXE, false, DuelFormat.BO1);
		});
		s.waitFor(tag + ": a duel against a bot starts", 400, () -> botsIn(matchOfA()).size() == 1 && matchOfA().mode == GameMode.AXE);
		endAndClean(s, tag);
	}

	/** One player in the Sumo lobby: bots fill it to four and the game starts. */
	private static void partyFill(MinecraftServer server, Script s) {
		String tag = "bots party";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins Sumo alone", () -> {
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 4;
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.SUMO);
		});
		s.waitFor(tag + ": three bots join and the match starts", 600, () -> {
			Match m = matchOfA();
			return m != null && m.mode == GameMode.SUMO && botsIn(m).size() == 3;
		});
		s.waitFor(tag + ": the bots fight (someone gets hit)", 600, () -> {
			Match m = matchOfA();
			if (m == null || m.phase() != Match.Phase.RUNNING) return false;
			for (BotPlayer b : botsIn(m)) {
				if (b.getLastHurtByMob() != null) return true;
			}
			return p(A).getLastHurtByMob() != null;
		});
		endAndClean(s, tag);
		s.run(tag + ": filling off", () -> cfg().fillQueues = false);
	}

	/** A bot on an easy parkour course gets past the first platforms. */
	private static void parkour(MinecraftServer server, Script s) {
		String tag = "bots parkour";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins an easy Parkour Race; one bot fills in", () -> {
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 2;
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.PARKOUR, "easy");
		});
		s.waitFor(tag + ": the race runs with a bot", 900, () -> {
			Match m = matchOfA();
			return m instanceof ParkourMatch && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.run(tag + ": no more bots", () -> cfg().fillQueues = false);
		s.waitFor(tag + ": the bot jumps from platform to platform", 900, () -> {
			Match m = matchOfA();
			if (!(m instanceof ParkourMatch pm) || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			return pm.platformIndex(b) >= 3 || pm.lastPlatform(b.getUUID()) >= 3;
		});
		endAndClean(s, tag);
	}
}
