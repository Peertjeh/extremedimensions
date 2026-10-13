package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.TitlesMenu;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.Ranks;
import net.battlehub.queue.Queues;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.scores.PlayerTeam;

/** Titles bought with coins and shown in chat, nametag and tab list; level-ups with bonus; win streak milestones. */
final class ProgressionTests {
	private ProgressionTests() {}

	static void all(MinecraftServer server, Script s) {
		titles(server, s);
		levelAndStreak(server, s);
	}

	private static void titles(MinecraftServer server, Script s) {
		String tag = "titles";
		SelfTest.reset(s, tag);
		s.run(tag + ": join", () -> SelfTest.join(server, "BHTestA"));
		s.pause(5);
		s.run(tag + ": A has 600 coins and opens the titles menu", () -> {
			PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			prof.coins = 600;
			prof.ownedTitles.remove("rich");
			prof.title = "";
			new TitlesMenu(p("BHTestA")).open();
		});
		s.run(tag + ": A clicks the Rich title (500 coins)", () -> {
			HubMenu m = (HubMenu) p("BHTestA").containerMenu;
			for (int i = 0; i < 54; i++) {
				if (m.getSlot(i).getItem().getHoverName().getString().equals("Rich")) {
					m.clicked(i, 0, ContainerInput.PICKUP, p("BHTestA"));
					return;
				}
			}
			throw new IllegalStateException("Rich is not in the titles menu");
		});
		s.waitFor(tag + ": bought, paid and selected", 5, () -> {
			PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			return prof.ownedTitles.contains("rich") && prof.coins == 100 && "rich".equals(prof.title);
		});
		s.check(tag + ": title shown on the nametag and in chat and the tab list", () -> {
			ServerPlayer a = p("BHTestA");
			PlayerTeam team = server.getScoreboard().getPlayersTeam(a.getScoreboardName());
			return team != null && team.getPlayerPrefix().getString().contains("Rich")
					&& a.getDisplayName().getString().contains("Rich") && a.getDisplayName().getString().contains("BHTestA");
		});
		s.run(tag + ": A cannot afford Champion", () -> {
			HubMenu m = (HubMenu) p("BHTestA").containerMenu;
			for (int i = 0; i < 54; i++) {
				if (m.getSlot(i).getItem().getHoverName().getString().equals("Champion")) {
					m.clicked(i, 0, ContainerInput.PICKUP, p("BHTestA"));
					return;
				}
			}
			throw new IllegalStateException("Champion is not in the titles menu");
		});
		s.pause(2);
		s.check(tag + ": nothing bought, coins unchanged", () -> {
			PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			return !prof.ownedTitles.contains("champion") && prof.coins == 100 && "rich".equals(prof.title);
		});
		s.run(tag + ": close", () -> p("BHTestA").closeContainer());
	}

	/** A is one XP short of level 5 (a bonus level) and on a 4-win streak; one more win does both. */
	private static void levelAndStreak(MinecraftServer server, Script s) {
		String tag = "level/streak";
		SelfTest.reset(s, tag);
		s.run(tag + ": join", () -> {
			SelfTest.join(server, "BHTestA");
			SelfTest.join(server, "BHTestB");
		});
		s.pause(5);
		s.run(tag + ": A is almost level 5 and has won 4 in a row", () -> {
			PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			prof.xp = Ranks.curve().totalFor(5) - 1;
			prof.level = 4;
			prof.overall.streak = 4;
			prof.overall.bestStreak = Math.max(prof.overall.bestStreak, 4);
			prof.knownTitles.remove("fighter");
			VARS.put("coinsA", prof.coins);
			Queues.join(p("BHTestA"), GameMode.SWORD, false);
			Queues.join(p("BHTestB"), GameMode.SWORD, false);
		});
		s.waitFor(tag + ": fight started", 400, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			if (m != null && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", m);
				return true;
			}
			return false;
		});
		s.run(tag + ": A wins", () -> {
			ServerPlayer b = p("BHTestB");
			b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestA")), 1000f);
		});
		s.check(tag + ": level 5 reached with the bonus coins", () -> {
			PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			long gained = prof.coins - (long) VARS.get("coinsA");
			return prof.level == 5 && gained >= ConfigManager.config().rewards.duel.winCoins + ConfigManager.config().levels.bonusCoins;
		});
		s.check(tag + ": win streak is 5 (a milestone)", () -> {
			PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			return prof.overall.streak == 5 && prof.overall.bestStreak >= 5
					&& ConfigManager.config().streaks.milestones.contains(5);
		});
		s.check(tag + ": Fighter (level 5) is now unlocked", () ->
				PlayerStore.get(p("BHTestA").getUUID()).knownTitles.contains("fighter"));
		s.check(tag + ": the loser's streak is reset", () -> PlayerStore.get(p("BHTestB").getUUID()).overall.streak == 0);
		s.waitFor(tag + ": match closed", 200, () -> ((Match) VARS.get("match")).phase() == Match.Phase.CLOSED);
	}
}
