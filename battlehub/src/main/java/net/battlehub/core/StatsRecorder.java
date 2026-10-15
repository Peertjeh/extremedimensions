package net.battlehub.core;

/** Applies one match result to a profile: counters, streaks per mode and overall. */
public final class StatsRecorder {
	private StatsRecorder() {}

	public record Result(int overallStreak, int modeStreak, boolean newBestOverall, boolean newBestMode) {}

	public static Result record(PlayerProfile p, GameMode mode, boolean ranked, Outcome outcome, int kills, int deaths) {
		ModeStats overall = p.overall;
		ModeStats ms = p.mode(mode);
		apply(overall, outcome, kills, deaths);
		apply(ms, outcome, kills, deaths);
		if (ranked) {
			apply(p.rankedStats(mode), outcome, kills, deaths);
		}
		boolean bestOverall = outcome == Outcome.WIN && overall.streak == overall.bestStreak && overall.streak > 0;
		boolean bestMode = outcome == Outcome.WIN && ms.streak == ms.bestStreak && ms.streak > 0;
		return new Result(overall.streak, ms.streak, bestOverall, bestMode);
	}

	/** Coins paid for a match, per mode (and ranked) and overall, for the "coins earned" leaderboards. */
	public static void recordCoins(PlayerProfile p, GameMode mode, boolean ranked, long coins) {
		if (coins <= 0) {
			return;
		}
		p.overall.coins += coins;
		p.mode(mode).coins += coins;
		if (ranked) {
			p.rankedStats(mode).coins += coins;
		}
	}

	private static void apply(ModeStats s, Outcome outcome, int kills, int deaths) {
		s.played++;
		s.kills += Math.max(0, kills);
		s.deaths += Math.max(0, deaths);
		switch (outcome) {
			case WIN -> {
				s.wins++;
				s.streak++;
				s.bestStreak = Math.max(s.bestStreak, s.streak);
			}
			case LOSS -> {
				s.losses++;
				s.streak = 0;
			}
			case DRAW -> s.draws++;
		}
	}

	/** True when {@code streak} is exactly one of the configured milestones (5, 10, 25, ...). */
	public static boolean isMilestone(int streak, Iterable<Integer> milestones) {
		for (Integer m : milestones) {
			if (m != null && m == streak) {
				return true;
			}
		}
		return false;
	}
}
