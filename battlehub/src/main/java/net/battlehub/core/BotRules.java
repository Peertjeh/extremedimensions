package net.battlehub.core;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What playing with bots is worth, without Minecraft (so it can be tested on its own). A match is only worth
 * something because of the other people in it: alone against bots, a player earns nothing at all (no coins, no XP,
 * no quest or pass progress, no prizes); with other people and bots, the configured share.
 */
public final class BotRules {
	private BotRules() {}

	/** Real players and bots among the participants. */
	public record Count(int humans, int bots) {
		/** One real player (or none) and at least one bot. */
		public boolean alone() {
			return bots > 0 && humans <= 1;
		}
	}

	public static Count count(Collection<UUID> participants, Predicate<UUID> isBot) {
		int humans = 0, bots = 0;
		for (UUID id : participants) {
			if (isBot.test(id)) {
				bots++;
			} else {
				humans++;
			}
		}
		return new Count(humans, bots);
	}

	/**
	 * The share of coins and XP a match pays its real players: everything without bots, nothing for a player alone
	 * against bots, {@code share} (0..1) when other people played too.
	 */
	public static double rewardFactor(Count c, double share) {
		if (c.bots() <= 0) return 1.0;
		if (c.alone()) return 0.0;
		return Math.max(0, Math.min(1, share));
	}

	/** Prizes (tournaments) are paid only when the winner beat other people, not only bots. */
	public static boolean paysPrizes(Count c) {
		return !c.alone();
	}
}
