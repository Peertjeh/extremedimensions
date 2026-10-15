package net.battlehub.core;

/** What clan names and tags may look like. */
public final class ClanRules {
	private ClanRules() {}

	/** 3-16 letters, digits or underscores. */
	public static boolean validName(String name) {
		return name != null && name.matches("[A-Za-z0-9_]{3,16}");
	}

	/** 2-4 letters or digits. */
	public static boolean validTag(String tag) {
		return tag != null && tag.matches("[A-Za-z0-9]{2,4}");
	}

	/** Clan points for one match: a win counts, a ranked win more. */
	public static int points(boolean won, boolean ranked) {
		return won ? (ranked ? 3 : 1) : 0;
	}
}
