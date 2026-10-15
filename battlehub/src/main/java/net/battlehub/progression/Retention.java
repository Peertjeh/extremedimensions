package net.battlehub.progression;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.ProgressConfig;
import net.battlehub.core.Achievement;
import net.battlehub.core.CosmeticDef;
import net.battlehub.core.Daily;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.Progression;
import net.battlehub.core.QuestDef;
import net.battlehub.core.QuestProgress;
import net.battlehub.core.Quests;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Quests, achievements, the login streak, the season pass and prestige: everything that rewards coming back.
 * Matches report to {@link #matchPlayed}; the rest is checked when a player enters the hub.
 */
public final class Retention {
	private Retention() {}

	private static ProgressConfig cfg() {
		return ConfigManager.progress();
	}

	private static long today() {
		return LocalDate.now().toEpochDay();
	}

	// ------------------------------------------------------------------ quests

	/** Gives the player today's and this week's quests when the day or week changed. */
	public static void ensureQuests(PlayerProfile p) {
		long day = today();
		long week = Quests.week(day);
		UUID id;
		try {
			id = UUID.fromString(p.uuid);
		} catch (IllegalArgumentException e) {
			return;
		}
		if (p.questDay != day) {
			p.questDay = day;
			p.dailyQuests.clear();
			for (QuestDef q : Quests.pick(cfg().quests, false, id, day, cfg().dailyQuests)) {
				p.dailyQuests.add(new QuestProgress(q.id));
			}
		}
		if (p.questWeek != week) {
			p.questWeek = week;
			p.weeklyQuests.clear();
			for (QuestDef q : Quests.pick(cfg().quests, true, id, week, cfg().weeklyQuests)) {
				p.weeklyQuests.add(new QuestProgress(q.id));
			}
		}
	}

	/** Seconds until the daily quests change (midnight, server time). */
	public static long secondsToNewDay() {
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		return java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
	}

	/** One match for one player: quests forward, pass XP, achievements. */
	public static void matchPlayed(ServerPlayer p, PlayerProfile prof, Quests.Match match, double passFactor) {
		ensureQuests(prof);
		List<QuestProgress> all = new ArrayList<>(prof.dailyQuests);
		all.addAll(prof.weeklyQuests);
		for (QuestProgress qp : all) {
			QuestDef q = cfg().quest(qp.id);
			if (q != null && Quests.advance(qp, q, match)) {
				completed(p, prof, q);
			}
		}
		ProgressConfig.Pass pass = cfg().pass;
		long xp = Math.round((pass.xpPerMatch + (match.won() ? pass.xpPerWin : 0)) * Math.max(0, passFactor));
		addPassXp(p, prof, xp);
		checkAchievements(p, prof);
	}

	private static void completed(ServerPlayer p, PlayerProfile prof, QuestDef q) {
		prof.questsDone++;
		Progression.addCoins(prof, q.coins);
		Progression.addXp(prof, q.xp, Ranks.curve(), ConfigManager.config().levels.bonusEvery, ConfigManager.config().levels.bonusCoins);
		if (p != null) {
			Msg.send(p, "quest.done", Lang.tr(p, "quest." + q.id), q.coins, q.xp);
			Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
		}
		addPassXp(p, prof, q.passXp);
	}

	// ------------------------------------------------------------------ season pass

	private static void ensurePass(PlayerProfile p) {
		int season = Math.max(1, Seasons.current());
		if (p.passSeason != season) {
			p.passSeason = season;
			p.passXp = 0;
			p.passTier = 0;
		}
	}

	public static int passTier(PlayerProfile p) {
		ensurePass(p);
		return Daily.passTier(p.passXp, cfg().pass.xpPerTier, cfg().pass.tiers);
	}

	/** Pass XP; every new tier pays out right away. */
	public static void addPassXp(ServerPlayer p, PlayerProfile prof, long xp) {
		ProgressConfig.Pass pass = cfg().pass;
		if (!pass.enabled || xp <= 0) {
			return;
		}
		ensurePass(prof);
		prof.passXp += xp;
		int tier = Daily.passTier(prof.passXp, pass.xpPerTier, pass.tiers);
		while (prof.passTier < tier) {
			prof.passTier++;
			prof.bestPassTier = Math.max(prof.bestPassTier, prof.passTier);
			payTier(p, prof, prof.passTier);
		}
	}

	/** What a pass tier gives: coins, more on every fifth, a cosmetic on every tenth. */
	public static String tierReward(int tier) {
		ProgressConfig.Pass pass = cfg().pass;
		if (tier % 10 == 0) return "cosmetic";
		return tier % 5 == 0 ? "coins:" + pass.coinsEveryFifth : "coins:" + pass.coinsPerTier;
	}

	private static void payTier(ServerPlayer p, PlayerProfile prof, int tier) {
		ProgressConfig.Pass pass = cfg().pass;
		String reward = tierReward(tier);
		if (reward.equals("cosmetic")) {
			CosmeticDef c = nextCosmetic(prof);
			if (c != null) {
				prof.ownedCosmetics.add(c.id);
				if (p != null) Msg.send(p, "pass.tier_cosmetic", tier, Lang.colored(c.display));
			} else {
				Progression.addCoins(prof, pass.coinsInsteadOfCosmetic);
				if (p != null) Msg.send(p, "pass.tier_coins", tier, pass.coinsInsteadOfCosmetic);
			}
		} else {
			int coins = Integer.parseInt(reward.substring(6));
			Progression.addCoins(prof, coins);
			if (p != null) Msg.send(p, "pass.tier_coins", tier, coins);
		}
		if (p != null) {
			Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.3f);
		}
	}

	/** The cheapest cosmetic the player does not own yet. */
	private static CosmeticDef nextCosmetic(PlayerProfile prof) {
		CosmeticDef best = null;
		for (CosmeticDef c : ConfigManager.cosmetics().cosmetics) {
			if (c == null || c.id == null || prof.ownedCosmetics.contains(c.id) || c.price <= 0) continue;
			if (best == null || c.price < best.price) best = c;
		}
		return best;
	}

	// ------------------------------------------------------------------ achievements

	/** Pays every achievement tier reached since the last check. */
	public static void checkAchievements(ServerPlayer p, PlayerProfile prof) {
		for (Achievement a : Achievement.values()) {
			int tier = a.tier(prof);
			int known = prof.achievements.getOrDefault(a.id(), 0);
			if (tier <= known) continue;
			for (int t = known + 1; t <= tier; t++) {
				long coins = Achievement.reward(t, cfg().achievementCoins);
				Progression.addCoins(prof, coins);
				if (p != null) {
					Msg.send(p, "achievement.reached", Lang.tr(p, "achievement." + a.id()), roman(t), coins);
				}
			}
			prof.achievements.put(a.id(), tier);
			if (p != null) {
				Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
			}
		}
	}

	public static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	// ------------------------------------------------------------------ login streak

	/** The first time a player comes into the hub on a day: the streak goes up and pays coins. */
	public static void login(ServerPlayer p) {
		PlayerProfile prof = PlayerStore.get(p);
		long day = today();
		ensureQuests(prof);
		if (prof.loginDay == day) {
			return;
		}
		prof.loginStreak = Daily.nextStreak(prof.loginStreak, prof.loginDay, day);
		prof.loginDay = day;
		int coins = Daily.loginCoins(prof.loginStreak, cfg().loginRewards);
		Progression.addCoins(prof, coins);
		Msg.send(p, "login.reward", prof.loginStreak, coins);
		Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
		checkAchievements(p, prof);
		PlayerStore.save(p.getUUID());
	}

	// ------------------------------------------------------------------ prestige

	public static boolean canPrestige(PlayerProfile p) {
		return cfg().prestige && p.level >= ConfigManager.config().levels.maxLevel;
	}

	/** Back to level 1 with one more star. */
	public static boolean prestige(ServerPlayer p) {
		PlayerProfile prof = PlayerStore.get(p);
		if (!canPrestige(prof)) {
			Msg.send(p, "prestige.not_yet", ConfigManager.config().levels.maxLevel);
			return false;
		}
		prof.prestige++;
		prof.xp = 0;
		prof.level = 1;
		PlayerStore.save(p.getUUID());
		Msg.title(p, Lang.tr(p, "prestige.title", Daily.prestigeStars(prof.prestige)), Lang.tr(p, "prestige.sub",
				Math.round(Daily.prestigeBonus(prof.prestige) * 100)), 10, 60, 20);
		Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
		Msg.broadcast(p.level().getServer(), "prestige.broadcast", p.getGameProfile().name(), prof.prestige);
		net.battlehub.hub.Travel.showLevel(p);
		TitleService.apply(p);
		return true;
	}

	/** Extra coins from prestige (1.05 for one prestige). */
	public static double coinFactor(PlayerProfile p) {
		return 1.0 + Daily.prestigeBonus(p.prestige);
	}
}
