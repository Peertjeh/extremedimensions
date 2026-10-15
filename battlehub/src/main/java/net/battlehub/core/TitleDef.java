package net.battlehub.core;

/**
 * A title from the config. {@code display} may use {@code &} colour codes.
 * Requirement types: default, level, wins, duel_wins, mode_wins, streak, mode_streak, kills, elo, coins, season
 * (the tier reached at the end of a ranked season: 0 bronze, 1 silver, 2 gold, 3 diamond, 4 netherite).
 */
public final class TitleDef {
	public String id = "";
	public String display = "";
	public String requirement = "default";
	/** Threshold for the requirement (level, wins, streak, kills, elo) or the price for "coins". */
	public int value;
	/** Mode id for mode_wins / mode_streak / elo (empty: any ranked mode). */
	public String mode = "";

	public TitleDef() {}

	public TitleDef(String id, String display, String requirement, int value, String mode) {
		this.id = id;
		this.display = display;
		this.requirement = requirement;
		this.value = value;
		this.mode = mode == null ? "" : mode;
	}

	public boolean purchasable() {
		return "coins".equalsIgnoreCase(requirement);
	}
}
