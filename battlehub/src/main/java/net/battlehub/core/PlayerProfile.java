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
	public int version = 1;
	public String uuid = "";
	public String name = "";
	public long coins;
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
		if (level < 1) level = 1;
		if (coins < 0) coins = 0;
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
