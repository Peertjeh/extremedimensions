package net.battlehub.core;

import java.util.Map;

/**
 * BattleHub counts the coins a player earned since 1.6. Profiles from before get an estimate once, so the
 * "coins earned" leaderboards are not empty after an update: per mode from the match counters and the current
 * reward settings, and in total at least the coins the player has plus what they spent in the shops.
 */
public final class CoinHistory {
	private CoinHistory() {}

	/** True when the profile was written before 1.6 and was not estimated yet. */
	public static boolean needsEstimate(PlayerProfile p) {
		return p.version < 2;
	}

	/**
	 * @param duel   rewards of casual duels
	 * @param party  rewards of every other casual mode (party games, Free for All, 1v2, 1v3)
	 * @param ranked rewards of ranked duels
	 * @param spent  coins the player paid for the titles and cosmetics they own
	 */
	public static void estimate(PlayerProfile p, RewardSettings duel, RewardSettings party, RewardSettings ranked, long spent) {
		if (!needsEstimate(p)) {
			return;
		}
		long total = 0;
		for (Map.Entry<String, ModeStats> e : p.modes.entrySet()) {
			GameMode mode = GameMode.byId(e.getKey());
			ModeStats all = e.getValue();
			// Parkour pays per difficulty (often nothing), so its counters say little about coins
			if (mode == null || mode == GameMode.PARKOUR) {
				continue;
			}
			if (all.coins <= 0) {
				ModeStats r = p.ranked.get(e.getKey());
				long rankedCoins = 0;
				if (r != null) {
					rankedCoins = r.coins > 0 ? r.coins : paid(r.played, r.wins, r.kills, ranked);
					r.coins = rankedCoins;
				}
				int played = all.played - (r == null ? 0 : r.played);
				int wins = all.wins - (r == null ? 0 : r.wins);
				int kills = all.kills - (r == null ? 0 : r.kills);
				all.coins = rankedCoins + paid(played, wins, kills, mode.isDuel() ? duel : party);
			}
			total += all.coins;
		}
		p.overall.coins = Math.max(p.overall.coins, total);
		p.coinsEarned = Math.max(p.coinsEarned, Math.max(total, p.coins + Math.max(0, spent)));
		p.version = 2;
	}

	private static long paid(int played, int wins, int kills, RewardSettings s) {
		if (s == null) {
			return 0;
		}
		return (long) Math.max(0, played) * s.participationCoins + (long) Math.max(0, wins) * s.winCoins
				+ (long) Math.max(0, kills) * s.killCoins;
	}
}
