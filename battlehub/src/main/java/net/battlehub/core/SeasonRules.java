package net.battlehub.core;

import java.util.Map;

/**
 * Closing a season for one profile: the peak is recorded with its tier, a coin reward is paid for the tier,
 * every rating moves back towards the start (soft reset) and the season counters start over.
 */
public final class SeasonRules {
	private SeasonRules() {}

	/**
	 * @return the record of the season, or null when the player played no ranked match in it (ratings are still
	 *         reset)
	 */
	public static SeasonRecord close(PlayerProfile p, int season, int nextSeason, RankTiers tiers, int startElo, double keep,
			Map<String, Integer> rewards, int topStep) {
		int matches = 0;
		for (Integer n : p.seasonPlayed.values()) {
			matches += n == null ? 0 : n;
		}
		SeasonRecord rec = null;
		if (matches > 0) {
			rec = new SeasonRecord();
			rec.season = season;
			rec.matches = matches;
			rec.peakElo = Integer.MIN_VALUE;
			for (Map.Entry<String, Integer> e : p.seasonPeak.entrySet()) {
				if (e.getValue() != null && e.getValue() > rec.peakElo) {
					rec.peakElo = e.getValue();
					rec.mode = e.getKey();
				}
			}
			if (rec.peakElo == Integer.MIN_VALUE) {
				rec.peakElo = p.bestElo(startElo);
			}
			RankTiers.Tier t = tiers.tierFor(rec.peakElo);
			rec.tier = t.id();
			rec.tierIndex = Season.tierIndex(tiers, rec.peakElo);
			rec.division = Season.division(tiers, rec.peakElo, topStep);
			Integer coins = rewards == null ? null : rewards.get(t.id());
			rec.coins = coins == null ? 0 : Math.max(0, coins);
			p.bestSeasonTier = Math.max(p.bestSeasonTier, rec.tierIndex);
			p.pastSeasons.add(rec);
		}
		for (Map.Entry<String, Integer> e : p.elo.entrySet()) {
			e.setValue(Season.softReset(e.getValue(), startElo, keep));
		}
		p.seasonPlayed.clear();
		p.seasonPeak.clear();
		p.season = nextSeason;
		return rec;
	}

	/** A ranked match was played: counts towards placements and the season peak. */
	public static void played(PlayerProfile p, GameMode mode, int newElo) {
		p.seasonPlayed.merge(mode.id(), 1, Integer::sum);
		p.seasonPeak.merge(mode.id(), newElo, Math::max);
	}

	public static int playedThisSeason(PlayerProfile p, GameMode mode) {
		return p.seasonPlayed.getOrDefault(mode.id(), 0);
	}
}
