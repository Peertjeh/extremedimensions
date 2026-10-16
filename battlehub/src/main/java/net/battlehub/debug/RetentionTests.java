package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.time.LocalDate;
import java.util.List;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.QuestProgress;
import net.battlehub.core.Quests;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.AchievementsMenu;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.PassMenu;
import net.battlehub.gui.QuestsMenu;
import net.battlehub.progression.Retention;
import net.minecraft.server.MinecraftServer;

/** Quests, the login streak, the season pass, achievements and prestige, on a real profile and through the menus. */
final class RetentionTests {
	private RetentionTests() {}

	private static final String A = "BHTestA";

	private static PlayerProfile prof() {
		return PlayerStore.get(p(A));
	}

	static void all(MinecraftServer server, Script s) {
		String tag = "retention";
		SelfTest.reset(s, tag);
		s.run(tag + ": quests, pass and achievements on for this test", () -> Retention.pausedForTest = false);
		s.run(tag + ": A has quests for today and this week", () -> SelfTest.join(server, A));
		s.check(tag + ": three daily and three weekly quests", () -> {
			Retention.ensureQuests(prof());
			return prof().dailyQuests.size() == ConfigManager.progress().dailyQuests
					&& prof().weeklyQuests.size() == ConfigManager.progress().weeklyQuests;
		});
		s.run(tag + ": today's first quest is 'play 3'; A plays three matches", () -> {
			prof().dailyQuests.set(0, new QuestProgress("play_3"));
			VARS.put("rCoins", prof().coins);
			VARS.put("rDone", prof().questsDone);
			VARS.put("rPass", prof().passXp);
			for (int i = 0; i < 3; i++) {
				Retention.matchPlayed(p(A), prof(), new Quests.Match(GameMode.SUMO, false, false, 0, 0, false, false), 1.0);
			}
		});
		s.check(tag + ": the quest is done and paid; the pass got XP", () -> prof().dailyQuests.get(0).done
				&& prof().questsDone == (int) VARS.get("rDone") + 1 && prof().coins >= (long) VARS.get("rCoins") + 50
				&& prof().passXp > (long) VARS.get("rPass"));
		s.run(tag + ": A logged in yesterday (day 3 of a streak) and comes back today", () -> {
			prof().loginDay = LocalDate.now().toEpochDay() - 1;
			prof().loginStreak = 3;
			VARS.put("rCoins", prof().coins);
			Retention.login(p(A));
		});
		s.check(tag + ": day 4 pays the fourth reward, once", () -> {
			long paid = ConfigManager.progress().loginRewards.get(3);
			boolean once = prof().coins == (long) VARS.get("rCoins") + paid;
			Retention.login(p(A));
			return prof().loginStreak == 4 && once && prof().coins == (long) VARS.get("rCoins") + paid;
		});
		s.run(tag + ": enough pass XP for tier 10", () -> {
			VARS.put("rCosmetics", prof().ownedCosmetics.size());
			VARS.put("rTier", Retention.passTier(prof()));
			Retention.addPassXp(p(A), prof(), (long) ConfigManager.progress().pass.xpPerTier * 10);
		});
		s.check(tag + ": tiers paid up to 10, with a cosmetic on tier 10", () -> Retention.passTier(prof()) >= 10
				&& prof().passTier == Retention.passTier(prof()) && prof().ownedCosmetics.size() > (int) VARS.get("rCosmetics"));
		s.run(tag + ": A has 12 kills overall", () -> {
			VARS.put("rCoins", prof().coins);
			VARS.put("rKills", prof().overall.kills);
			prof().overall.kills = Math.max(prof().overall.kills, 12);
			prof().achievements.remove("fighter");
			Retention.checkAchievements(p(A), prof());
		});
		s.check(tag + ": Fighter tier reached and paid once", () -> {
			int tier = prof().achievements.getOrDefault("fighter", 0);
			long coins = prof().coins;
			Retention.checkAchievements(p(A), prof());
			return tier >= 1 && coins > (long) VARS.get("rCoins") && prof().coins == coins;
		});
		s.run(tag + ": A reaches the top level and takes prestige", () -> {
			prof().overall.kills = (int) VARS.get("rKills");
			VARS.put("rXp", prof().xp);
			VARS.put("rLevel", prof().level);
			prof().level = ConfigManager.config().levels.maxLevel;
			Retention.prestige(p(A));
		});
		s.check(tag + ": prestige 1, back to level 1, 5% more coins", () -> prof().prestige == 1 && prof().level == 1
				&& prof().xp == 0 && Math.abs(Retention.coinFactor(prof()) - 1.05) < 1e-9);
		s.run(tag + ": the quest menu opens", () -> new QuestsMenu(p(A)).open());
		s.check(tag + ": quest menu", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": the pass menu opens", () -> new PassMenu(p(A), 1).open());
		s.check(tag + ": pass menu", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": the achievements menu opens", () -> new AchievementsMenu(p(A), false).open());
		s.check(tag + ": achievements menu", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": close; A's level and XP as before", () -> {
			p(A).closeContainer();
			prof().prestige = 0;
			prof().xp = (long) VARS.get("rXp");
			prof().level = (int) VARS.get("rLevel");
		});
		s.check(tag + ": nothing broken in the profile", () -> {
			List<QuestProgress> d = prof().dailyQuests;
			return !d.isEmpty() && prof().passSeason >= 1;
		});
		s.run(tag + ": quests, pass and achievements off again for the other tests", () -> Retention.pausedForTest = true);
	}
}
