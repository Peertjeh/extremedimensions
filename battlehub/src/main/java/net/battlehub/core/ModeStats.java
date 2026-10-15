package net.battlehub.core;

/** Counters for one mode (or the overall totals). Plain fields so Gson can store it directly. */
public final class ModeStats {
	public int played;
	public int wins;
	public int losses;
	public int draws;
	public int kills;
	public int deaths;
	public int streak;
	public int bestStreak;
	/** Coins paid out for matches of this mode (counted since 1.6; older profiles got an estimate). */
	public long coins;

	public double kdr() {
		return deaths == 0 ? kills : (double) kills / deaths;
	}

	public double winRate() {
		return played == 0 ? 0 : (double) wins / played;
	}
}
