package net.battlehub.core;

/**
 * Ranked seasons: fixed-length periods counted from an anchor day. At the end ratings move halfway back to the
 * start rating (a soft reset), and the first matches of a season are placement matches that count double.
 */
public final class Season {
	private Season() {}

	/** Season number (1, 2, ...) for a day; days before the anchor belong to season 1. */
	public static int number(long day, long anchorDay, int lengthDays) {
		int len = Math.max(1, lengthDays);
		return (int) (Math.max(0, day - anchorDay) / len) + 1;
	}

	/** First day of a season. */
	public static long start(int number, long anchorDay, int lengthDays) {
		return anchorDay + (long) (Math.max(1, number) - 1) * Math.max(1, lengthDays);
	}

	/** Days left in the season that contains {@code day}, counting today. */
	public static long daysLeft(long day, long anchorDay, int lengthDays) {
		int n = number(day, anchorDay, lengthDays);
		return start(n + 1, anchorDay, lengthDays) - Math.max(day, anchorDay);
	}

	/** The rating a player starts the next season with: {@code keep} of the distance to the start rating stays. */
	public static int softReset(int elo, int startElo, double keep) {
		double k = Math.max(0, Math.min(1, keep));
		return (int) Math.round(startElo + (elo - startElo) * k);
	}

	/** Placement matches count this many times as much for the rating. */
	public static int kFactor(int baseK, int playedThisSeason, int placements) {
		return playedThisSeason < Math.max(0, placements) ? baseK * 2 : baseK;
	}

	/**
	 * Division inside a tier: 3 (lowest) to 1 (highest). A tier is split into three equal parts; the top tier,
	 * which has no ceiling, in steps of {@code topStep}.
	 */
	public static int division(RankTiers tiers, int elo, int topStep) {
		RankTiers.Tier t = tiers.tierFor(elo);
		RankTiers.Tier next = tiers.nextTier(elo);
		int pos = Math.max(0, elo - t.minElo());
		int part = next == null ? Math.max(1, topStep) : Math.max(1, (next.minElo() - t.minElo() + 2) / 3);
		return 3 - Math.min(2, pos / part);
	}

	public static String roman(int division) {
		return switch (division) {
			case 1 -> "I";
			case 2 -> "II";
			default -> "III";
		};
	}

	/** Index of a tier from the bottom (0 = lowest). */
	public static int tierIndex(RankTiers tiers, int elo) {
		return tiers.all().indexOf(tiers.tierFor(elo));
	}
}
