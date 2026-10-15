package net.battlehub.core;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Permanent goals with tiers (I, II, III, ...), measured from the profile: kills, wins, streaks, levels,
 * cosmetics, parkour finishes, seasons. Each new tier pays coins once.
 */
public enum Achievement {
	FIGHTER(p -> p.overall.kills, List.of(10L, 100L, 500L, 2000L)),
	WINNER(p -> p.overall.wins, List.of(5L, 50L, 250L, 1000L)),
	REGULAR(p -> p.overall.played, List.of(10L, 100L, 500L, 2000L)),
	STREAK(p -> p.overall.bestStreak, List.of(3L, 10L, 25L)),
	DUELIST(p -> p.duelWins(), List.of(10L, 100L, 500L)),
	CLIMBER(p -> p.level, List.of(10L, 25L, 50L, 100L)),
	COLLECTOR(p -> p.ownedCosmetics.size() + p.ownedTitles.size(), List.of(3L, 10L, 25L)),
	RUNNER(p -> p.peek(GameMode.PARKOUR).wins, List.of(5L, 25L, 100L)),
	BED_BREAKER(p -> p.peek(GameMode.BED_WARS).wins, List.of(5L, 25L, 100L)),
	HUNTER(p -> p.peek(GameMode.PROP_HUNT).wins, List.of(5L, 25L, 100L)),
	RANKED(p -> p.pastSeasons.size(), List.of(1L, 3L, 6L)),
	QUESTER(p -> p.questsDone, List.of(10L, 50L, 200L, 1000L));

	private final ToLongFunction<PlayerProfile> measure;
	public final List<Long> tiers;

	Achievement(ToLongFunction<PlayerProfile> measure, List<Long> tiers) {
		this.measure = measure;
		this.tiers = tiers;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public long value(PlayerProfile p) {
		return measure.applyAsLong(p);
	}

	/** Tiers reached (0 = none). */
	public int tier(PlayerProfile p) {
		long v = value(p);
		int t = 0;
		for (Long need : tiers) {
			if (v >= need) t++;
		}
		return t;
	}

	/** Coins for reaching a tier (1-based): more for higher tiers. */
	public static long reward(int tier, int base) {
		return (long) base * tier * tier;
	}
}
