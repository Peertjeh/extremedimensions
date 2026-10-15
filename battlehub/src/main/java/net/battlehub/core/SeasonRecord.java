package net.battlehub.core;

/** How a player ended one ranked season. Plain fields for Gson. */
public final class SeasonRecord {
	public int season;
	/** Highest rating of the season (any ranked mode) and the mode it was in. */
	public int peakElo;
	public String mode = "";
	/** Tier id of the peak, its index from the bottom, and the division (1-3). */
	public String tier = "";
	public int tierIndex;
	public int division = 3;
	public int matches;
	public long coins;
}
