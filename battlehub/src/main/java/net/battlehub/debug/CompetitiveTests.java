package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.battlehub.arena.ArenaSlots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.config.KitConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.KitLayout;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.fair.LeavePenalty;
import net.battlehub.fair.Reports;
import net.battlehub.match.Kits;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.Seasons;
import net.battlehub.queue.Challenges;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Competitive play: the recap after a duel, a kit in the player's own layout, the wait after leaving a match,
 * idle players taken out, a report on file, and a ranked season closing (reward, soft reset, placements).
 */
final class CompetitiveTests {
	private CompetitiveTests() {}

	private static final String A = "BHTestA";
	private static final String B = "BHTestB";

	private static HubConfig.Fairness fair() {
		return ConfigManager.config().fairness;
	}

	private static Match matchOf(String name) {
		return Matches.of(p(name).getUUID());
	}

	static void all(MinecraftServer server, Script s) {
		kitAndRecap(server, s);
		leaving(server, s);
		idle(server, s);
		report(server, s);
		season(server, s);
	}

	/** A sword duel with A's own layout (sword in slot 3); A hits B a few times and wins; the recap counts it. */
	private static void kitAndRecap(MinecraftServer server, Script s) {
		String tag = "kit and recap";
		SelfTest.reset(s, tag);
		s.run(tag + ": A moves the sword of the sword kit to slot 3, then challenges B", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			String kit = ConfigManager.config().game(GameMode.SWORD).kit;
			KitConfig.Kit k = ConfigManager.kits().kits.get(kit);
			List<Integer> slots = Kits.numericSlots(k);
			PlayerStore.get(p(A)).kitLayouts.put(kit, KitLayout.swap(slots, null, 0, 3));
			Challenges.send(p(A), p(B), GameMode.SWORD);
			Challenges.accept(p(B), p(A));
		});
		s.waitFor(tag + ": duel running", 600, () -> matchOf(A) != null && matchOf(A).phase() == Match.Phase.RUNNING);
		s.check(tag + ": A's sword is in slot 3, B's in slot 0", () ->
				p(A).getInventory().getItem(3).is(Items.DIAMOND_SWORD) && !p(A).getInventory().getItem(0).is(Items.DIAMOND_SWORD)
						&& p(B).getInventory().getItem(0).is(Items.DIAMOND_SWORD));
		// Three hits, each after the short time in which a player cannot be hurt again
		for (int i = 1; i <= 3; i++) {
			s.run(tag + ": A hits B (" + i + ")", () -> {
				ServerPlayer b = p(B);
				b.hurtServer(b.level(), b.damageSources().playerAttack(p(A)), 3f);
			});
			s.pause(11);
		}
		s.check(tag + ": the recap counts the hits, the damage and the combo", () -> {
			var l = matchOf(A).recap().line(p(A).getUUID());
			double taken = matchOf(A).recap().line(p(B).getUUID()).taken;
			boolean ok = l.hits == 3 && l.bestCombo == 3 && l.dealt > 0 && Math.abs(l.dealt - taken) < 0.01;
			if (!ok) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST recap: hits {}, best combo {}, dealt {}, B took {}, B health {}",
						l.hits, l.bestCombo, l.dealt, taken, p(B).getHealth());
			}
			return ok;
		});
		s.run(tag + ": A wins", () -> {
			ServerPlayer b = p(B);
			b.hurtServer(b.level(), b.damageSources().playerAttack(p(A)), 1000f);
		});
		s.check(tag + ": A is the MVP", () -> {
			Match m = matchOf(A);
			return m != null && p(A).getUUID().equals(m.recap().mvp(m.participants())) && m.recap().line(p(A).getUUID()).kills == 1;
		});
		s.run(tag + ": A's layout back to standard", () -> PlayerStore.get(p(A)).kitLayouts.clear());
		s.waitFor(tag + ": arena free", 600, () -> ArenaSlots.busy() == 0 && Sessions.isFree(p(A).getUUID()));
		s.check(tag + ": The Bridge's clay is part of its kit and follows A's layout (an old one, made before the clay)", () -> {
			KitConfig.Kit k = ConfigManager.kits().kits.get("bridge");
			List<Integer> slots = Kits.numericSlots(k);
			// a layout from before: only the first five items (sword and bow swapped)
			PlayerStore.get(p(A)).kitLayouts.put("bridge", List.of(1, 0, 2, 5, 8));
			Kits.give(p(A), "bridge", -1, "red");
			boolean ok = p(A).getInventory().getItem(0).is(Items.BOW) && p(A).getInventory().getItem(1).is(Items.IRON_SWORD)
					&& p(A).getInventory().countItem(net.battlehub.util.Ids.item("minecraft:red_terracotta")) == 128
					&& p(A).getInventory().getItem(3).is(net.battlehub.util.Ids.item("minecraft:red_terracotta")) && slots.size() == 7;
			// moving the clay in the editor moves it in the match
			PlayerStore.get(p(A)).kitLayouts.put("bridge", KitLayout.swap(slots, PlayerStore.get(p(A)).kitLayouts.get("bridge"), 3, 6));
			Kits.give(p(A), "bridge", -1, "blue");
			ok &= p(A).getInventory().getItem(6).is(net.battlehub.util.Ids.item("minecraft:blue_terracotta"));
			PlayerStore.get(p(A)).kitLayouts.clear();
			p(A).getInventory().clearContent();
			net.battlehub.lobby.HubItems.ensure(p(A), false);
			return ok;
		});
	}

	/** Leaving a duel early blocks queueing for a while; matches with bots do not count. */
	private static void leaving(MinecraftServer server, Script s) {
		String tag = "leave penalty";
		SelfTest.reset(s, tag);
		s.run(tag + ": penalty on; A challenges B", () -> {
			fair().leavePenalty = true;
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			PlayerProfile pa = PlayerStore.get(p(A));
			pa.leaves = 0;
			pa.queueBlockedUntil = 0;
			Challenges.send(p(A), p(B), GameMode.AXE);
			Challenges.accept(p(B), p(A));
		});
		s.waitFor(tag + ": duel running", 600, () -> matchOf(A) != null && matchOf(A).phase() == Match.Phase.RUNNING);
		s.run(tag + ": A leaves with /hub", () -> net.battlehub.command.HubCommands.hub(p(A)));
		s.check(tag + ": A has to wait before queueing", () -> LeavePenalty.secondsLeft(p(A).getUUID()) > 20
				&& !Queues.join(p(A), GameMode.SWORD, false) && Sessions.isFree(p(A).getUUID()));
		s.run(tag + ": the wait is over (for the test); penalty off again", () -> {
			PlayerStore.get(p(A)).queueBlockedUntil = 0;
			PlayerStore.get(p(A)).leaves = 0;
			fair().leavePenalty = false;
		});
		s.waitFor(tag + ": arena free", 600, () -> ArenaSlots.busy() == 0);
	}

	/** A player who does nothing in a duel is taken out after the idle time. */
	private static void idle(MinecraftServer server, Script s) {
		String tag = "afk";
		SelfTest.reset(s, tag);
		s.run(tag + ": idle limit 3 seconds; A challenges B", () -> {
			fair().afkMatchSeconds = 3;
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			Challenges.send(p(A), p(B), GameMode.SWORD);
			Challenges.accept(p(B), p(A));
		});
		s.waitFor(tag + ": duel running", 600, () -> {
			Match m = matchOf(A);
			if (m != null && m.phase() == Match.Phase.RUNNING) {
				VARS.put("afkMatch", m);
				return true;
			}
			return false;
		});
		s.waitFor(tag + ": an idle player was taken out and the match ended", 200, () -> {
			Match m = (Match) VARS.get("afkMatch");
			return m != null && !m.leftPlayers().isEmpty() && (m.phase() == Match.Phase.ENDING || m.phase() == Match.Phase.CLOSED);
		});
		s.run(tag + ": idle check off again", () -> fair().afkMatchSeconds = 0);
		s.waitFor(tag + ": arena free", 600, () -> ArenaSlots.busy() == 0);
	}

	/** /report writes a line to reports.log. */
	private static void report(MinecraftServer server, Script s) {
		String tag = "report";
		SelfTest.reset(s, tag);
		s.run(tag + ": A reports B", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			Reports.report(p(A), "BHTestB", "testing the report");
		});
		s.check(tag + ": on file and in the recent list; a second one right away is refused", () -> {
			try {
				String log = Files.readString(PlayerStore.worldDir(server).resolve("reports.log"));
				return log.contains("testing the report") && log.contains("BHTestB") && !Reports.recent().isEmpty()
						&& !Reports.report(p(A), "BHTestB", "again");
			} catch (java.io.IOException e) {
				return false;
			}
		});
	}

	/** A season ends: the peak is recorded, coins paid, ratings halfway back, placements start over. */
	private static void season(MinecraftServer server, Script s) {
		String tag = "season";
		s.run(tag + ": A played ranked sword this season and peaked at 1460", () -> {
			SelfTest.join(server, A);
			PlayerProfile pa = PlayerStore.get(p(A));
			VARS.put("seasonElo", new ArrayList<>(List.of(pa.elo(GameMode.SWORD, 1000))));
			VARS.put("seasonCoins", pa.coins);
			pa.setElo(GameMode.SWORD, 1400);
			pa.seasonPlayed.clear();
			pa.seasonPeak.clear();
			Seasons.played(pa, GameMode.SWORD, 1460);
			Seasons.played(pa, GameMode.SWORD, 1400);
		});
		s.check(tag + ": placements count down", () ->
				Seasons.placementsLeft(PlayerStore.get(p(A)), GameMode.SWORD) == ConfigManager.config().seasons.placements - 2);
		s.run(tag + ": the season ends", () -> Seasons.endForTest(server));
		s.check(tag + ": diamond recorded, 1000 coins, rating 1200, placements again, season title unlocked", () -> {
			PlayerProfile pa = PlayerStore.get(p(A));
			var rec = pa.pastSeasons.isEmpty() ? null : pa.pastSeasons.get(pa.pastSeasons.size() - 1);
			return rec != null && rec.peakElo == 1460 && "diamond".equals(rec.tier)
					&& pa.coins == (long) VARS.get("seasonCoins") + 1000 && pa.elo(GameMode.SWORD, 1000) == 1200
					&& Seasons.placementsLeft(pa, GameMode.SWORD) == ConfigManager.config().seasons.placements
					&& net.battlehub.core.TitleRules.unlocked(ConfigManager.titles().byId("season_diamond"), pa, 1000);
		});
		s.run(tag + ": A's rating as before", () -> {
			@SuppressWarnings("unchecked")
			List<Integer> before = (List<Integer>) VARS.get("seasonElo");
			PlayerStore.get(p(A)).setElo(GameMode.SWORD, before.get(0));
		});
	}
}
