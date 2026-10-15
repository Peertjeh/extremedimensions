package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.QuestDef;

/**
 * {@code config/battlehub/progress.json}: reasons to come back. Daily and weekly quests, the login streak, the
 * free season pass, achievement rewards and prestige.
 */
public final class ProgressConfig {
	public int dailyQuests = 3;
	public int weeklyQuests = 3;
	public List<QuestDef> quests = defaultQuests();
	/** Coins for day 1, 2, 3, ... of a login streak; the last one repeats. */
	public List<Integer> loginRewards = new ArrayList<>(List.of(50, 75, 100, 150, 200, 250, 400));
	public Pass pass = new Pass();
	/** Coins for an achievement tier: this times the tier squared (100, 400, 900, ...). */
	public int achievementCoins = 100;
	/** Going back to level 1 at the top level, for a star and more coins. */
	public boolean prestige = true;

	/** The free season pass: tiers of pass XP, coins on every tier, more on every fifth, a cosmetic on every tenth. */
	public static final class Pass {
		public boolean enabled = true;
		public int tiers = 50;
		public int xpPerTier = 1000;
		public int xpPerMatch = 60;
		public int xpPerWin = 60;
		public int coinsPerTier = 60;
		public int coinsEveryFifth = 300;
		/** When the cosmetic for a tenth tier is already owned (or there are none left), these coins instead. */
		public int coinsInsteadOfCosmetic = 600;
	}

	static List<QuestDef> defaultQuests() {
		List<QuestDef> l = new ArrayList<>();
		l.add(new QuestDef("play_3", "daily", "play", "", 3, 50, 60, 150));
		l.add(new QuestDef("win_2", "daily", "win", "", 2, 80, 80, 200));
		l.add(new QuestDef("kill_10", "daily", "kill", "", 10, 70, 70, 180));
		l.add(new QuestDef("damage_30", "daily", "damage", "", 30, 60, 60, 150));
		l.add(new QuestDef("featured_2", "daily", "featured", "", 2, 100, 80, 200));
		l.add(new QuestDef("mvp_1", "daily", "mvp", "", 1, 80, 80, 200));
		l.add(new QuestDef("win_sumo", "daily", "win", "sumo", 1, 70, 70, 180));
		l.add(new QuestDef("play_bed_wars", "daily", "play", "bed_wars", 1, 70, 70, 180));
		l.add(new QuestDef("play_ffa_2", "daily", "play", "ffa", 2, 60, 60, 160));
		l.add(new QuestDef("win_duel_sword", "daily", "win", "sword", 1, 70, 70, 180));
		l.add(new QuestDef("play_parkour", "daily", "play", "parkour", 1, 50, 60, 150));
		l.add(new QuestDef("play_prop_hunt", "daily", "play", "prop_hunt", 1, 60, 60, 160));
		l.add(new QuestDef("win_15", "weekly", "win", "", 15, 400, 400, 1000));
		l.add(new QuestDef("kill_100", "weekly", "kill", "", 100, 400, 400, 1000));
		l.add(new QuestDef("play_30", "weekly", "play", "", 30, 350, 350, 900));
		l.add(new QuestDef("ranked_win_5", "weekly", "ranked_win", "", 5, 500, 400, 1200));
		l.add(new QuestDef("win_bed_wars_3", "weekly", "win", "bed_wars", 3, 400, 400, 1000));
		l.add(new QuestDef("win_parkour_5", "weekly", "win", "parkour", 5, 400, 400, 1000));
		l.add(new QuestDef("mvp_5", "weekly", "mvp", "", 5, 450, 400, 1100));
		l.add(new QuestDef("damage_300", "weekly", "damage", "", 300, 400, 400, 1000));
		return l;
	}

	public ProgressConfig normalize() {
		if (quests == null || quests.isEmpty()) quests = defaultQuests();
		quests.removeIf(q -> q == null || q.id == null || q.id.isBlank());
		for (QuestDef q : quests) {
			if (q.period == null) q.period = "daily";
			if (q.goal == null) q.goal = "play";
			if (q.mode == null) q.mode = "";
			q.target = Math.max(1, q.target);
		}
		dailyQuests = Math.max(0, Math.min(7, dailyQuests));
		weeklyQuests = Math.max(0, Math.min(7, weeklyQuests));
		if (loginRewards == null || loginRewards.isEmpty()) loginRewards = new ArrayList<>(List.of(50, 75, 100, 150, 200, 250, 400));
		loginRewards.removeIf(v -> v == null);
		if (pass == null) pass = new Pass();
		pass.tiers = Math.max(1, Math.min(200, pass.tiers));
		pass.xpPerTier = Math.max(1, pass.xpPerTier);
		achievementCoins = Math.max(0, achievementCoins);
		return this;
	}

	public QuestDef quest(String id) {
		for (QuestDef q : quests) {
			if (q.id.equals(id)) return q;
		}
		return null;
	}
}
