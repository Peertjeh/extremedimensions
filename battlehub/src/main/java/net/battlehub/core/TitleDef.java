package net.battlehub.core;

/**
 * A title from the config. {@code display} may use {@code &} colour codes.
 * Requirement types: default, level, wins, duel_wins, mode_wins, streak, mode_streak, kills, elo, coins, season
 * (the tier reached at the end of a ranked season: 0 bronze, 1 silver, 2 gold, 3 diamond, 4 netherite), pass (season
 * pass tier ever reached), prestige, weekly_wins and placeholder (1.51: earned in another mod, read through Text
 * Placeholder API; once earned it is kept, like a bought title).
 */
public final class TitleDef {
	public String id = "";
	public String display = "";
	public String requirement = "default";
	/** Threshold for the requirement (level, wins, streak, kills, elo) or the price for "coins". */
	public int value;
	/** Mode id for mode_wins / mode_streak / elo (empty: any ranked mode). */
	public String mode = "";
	/** For "placeholder": the placeholder to read, without % (e.g. "extremedimensions:expeditions"). */
	public String placeholder = "";
	/** For "placeholder": the text it must give (any case); empty: a number of at least {@code value}. */
	public String text = "";
	/** What to do for it, shown in the titles menu: a lang key or text with & codes (empty: the requirement's own text). */
	public String hint = "";

	public TitleDef() {}

	public TitleDef(String id, String display, String requirement, int value, String mode) {
		this.id = id;
		this.display = display;
		this.requirement = requirement;
		this.value = value;
		this.mode = mode == null ? "" : mode;
	}

	/** A "placeholder" title. */
	public TitleDef placeholder(String placeholder, String text, String hint) {
		this.placeholder = placeholder;
		this.text = text;
		this.hint = hint;
		return this;
	}

	public boolean purchasable() {
		return "coins".equalsIgnoreCase(requirement);
	}
}
