package net.battlehub.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything BattleHub remembers about a player. Stored as JSON per UUID; every field is public and
 * Gson-friendly. Call {@link #normalize()} after loading to repair missing collections.
 */
public final class PlayerProfile {
	/** 2 since 1.6 (coins earned are counted); older files get an estimate on load, see {@link CoinHistory}. */
	public int version = 2;
	public String uuid = "";
	public String name = "";
	public long coins;
	/** Every coin ever earned (matches, level bonuses, tournament prizes); spending does not lower it. */
	public long coinsEarned;
	public long xp;
	public int level = 1;
	/** Selected title id, empty for none. */
	public String title = "";
	/** Titles bought with coins. */
	public Set<String> ownedTitles = new LinkedHashSet<>();
	/** Titles the player was already told about (so "title unlocked" is announced once). */
	public Set<String> knownTitles = new LinkedHashSet<>();
	public ModeStats overall = new ModeStats();
	public Map<String, ModeStats> modes = new LinkedHashMap<>();
	public Map<String, ModeStats> ranked = new LinkedHashMap<>();
	public Map<String, Integer> elo = new LinkedHashMap<>();
	/** Best Parkour Race time in milliseconds per course length bucket; only "parkour" today. */
	public Map<String, Long> bestTimes = new LinkedHashMap<>();
	public long firstSeen;
	public long lastSeen;
	/** Preferred duel format: "bo1" or "bo3". */
	public String duelFormat = "bo1";
	public int tournamentsWon;
	/** Cosmetics bought with coins (ids from cosmetics.json). */
	public Set<String> ownedCosmetics = new LinkedHashSet<>();
	/** Selected cosmetic per category ("trail", "kill", ...). */
	public Map<String, String> cosmetics = new LinkedHashMap<>();
	/** Season the season counters belong to (0: not in a season yet). */
	public int season;
	/** Ranked matches per mode this season; the first few are placement matches. */
	public Map<String, Integer> seasonPlayed = new LinkedHashMap<>();
	/** Highest rating per mode this season. */
	public Map<String, Integer> seasonPeak = new LinkedHashMap<>();
	/** How earlier seasons ended. */
	public java.util.List<SeasonRecord> pastSeasons = new java.util.ArrayList<>();
	/** Best tier reached in a finished season (index from the bottom, -1: none). */
	public int bestSeasonTier = -1;
	/** Own arrangement per kit (see KitLayout). */
	public Map<String, java.util.List<Integer>> kitLayouts = new LinkedHashMap<>();
	/** Day (since 1970) and week of the quests below, and the quests themselves. */
	public long questDay = -1;
	public long questWeek = -1;
	public java.util.List<QuestProgress> dailyQuests = new java.util.ArrayList<>();
	public java.util.List<QuestProgress> weeklyQuests = new java.util.ArrayList<>();
	public int questsDone;
	/** Tier reached per achievement (so each tier pays once). */
	public Map<String, Integer> achievements = new LinkedHashMap<>();
	/** Last day a login reward was given and the streak of days in a row. */
	public long loginDay = -1;
	public int loginStreak;
	/** Season pass: season it belongs to, its XP, the highest tier paid out, and the best tier ever. */
	public int passSeason;
	public long passXp;
	public int passTier;
	public int bestPassTier;
	/** Times the player went back to level 1 after the top level. */
	public int prestige;
	/** Friends (uuids) and friend requests from others. */
	public java.util.Set<String> friends = new LinkedHashSet<>();
	public java.util.Set<String> friendRequests = new LinkedHashSet<>();
	/** The clan (its name in lower case, empty for none) and clans that invited the player. */
	public String clan = "";
	public java.util.Set<String> clanInvites = new LinkedHashSet<>();
	/** Matches left early in the last day, when the last one was, and until when queueing is blocked (epoch ms). */
	public int leaves;
	public long lastLeave;
	public long queueBlockedUntil;

	public PlayerProfile normalize() {
		if (uuid == null) uuid = "";
		if (name == null) name = "";
		if (title == null) title = "";
		if (ownedTitles == null) ownedTitles = new LinkedHashSet<>();
		if (duelFormat == null) duelFormat = "bo1";
		if (ownedCosmetics == null) ownedCosmetics = new LinkedHashSet<>();
		if (cosmetics == null) cosmetics = new LinkedHashMap<>();
		if (knownTitles == null) knownTitles = new LinkedHashSet<>();
		if (overall == null) overall = new ModeStats();
		if (modes == null) modes = new LinkedHashMap<>();
		if (ranked == null) ranked = new LinkedHashMap<>();
		if (elo == null) elo = new LinkedHashMap<>();
		if (bestTimes == null) bestTimes = new LinkedHashMap<>();
		if (seasonPlayed == null) seasonPlayed = new LinkedHashMap<>();
		if (seasonPeak == null) seasonPeak = new LinkedHashMap<>();
		if (pastSeasons == null) pastSeasons = new java.util.ArrayList<>();
		if (kitLayouts == null) kitLayouts = new LinkedHashMap<>();
		pastSeasons.removeIf(r -> r == null);
		if (dailyQuests == null) dailyQuests = new java.util.ArrayList<>();
		if (weeklyQuests == null) weeklyQuests = new java.util.ArrayList<>();
		dailyQuests.removeIf(q -> q == null || q.id == null);
		weeklyQuests.removeIf(q -> q == null || q.id == null);
		if (achievements == null) achievements = new LinkedHashMap<>();
		if (friends == null) friends = new LinkedHashSet<>();
		if (friendRequests == null) friendRequests = new LinkedHashSet<>();
		if (clan == null) clan = "";
		if (clanInvites == null) clanInvites = new LinkedHashSet<>();
		kitLayouts.values().removeIf(l -> l == null);
		if (level < 1) level = 1;
		if (coins < 0) coins = 0;
		if (coinsEarned < 0) coinsEarned = 0;
		if (xp < 0) xp = 0;
		modes.values().removeIf(v -> v == null);
		ranked.values().removeIf(v -> v == null);
		elo.values().removeIf(v -> v == null);
		return this;
	}

	public ModeStats mode(GameMode mode) {
		return modes.computeIfAbsent(mode.id(), k -> new ModeStats());
	}

	public ModeStats rankedStats(GameMode mode) {
		return ranked.computeIfAbsent(mode.id(), k -> new ModeStats());
	}

	/** Read-only view: an empty object when the player never played the mode. */
	public ModeStats peek(GameMode mode) {
		ModeStats s = modes.get(mode.id());
		return s == null ? new ModeStats() : s;
	}

	public int elo(GameMode mode, int start) {
		Integer v = elo.get(mode.id());
		return v == null ? start : v;
	}

	public void setElo(GameMode mode, int value) {
		elo.put(mode.id(), value);
	}

	/** Highest rating over all ranked modes, or {@code start} if none were played. */
	public int bestElo(int start) {
		int best = Integer.MIN_VALUE;
		for (Integer v : elo.values()) {
			best = Math.max(best, v);
		}
		return best == Integer.MIN_VALUE ? start : best;
	}

	/** Wins over all duel modes together. */
	public DuelFormat format() {
		return DuelFormat.byId(duelFormat);
	}

	public int duelWins() {
		int sum = 0;
		for (GameMode m : GameMode.duels()) {
			sum += peek(m).wins;
		}
		return sum;
	}
}
