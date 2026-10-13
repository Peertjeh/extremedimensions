package net.battlehub.core;

import java.util.Locale;

/** Every playable mode. Pure data: no Minecraft types, so matchmaking and stats stay testable. */
public enum GameMode {
	SWORD(Category.DUEL, false, 2, 2),
	AXE(Category.DUEL, false, 2, 2),
	MACE(Category.DUEL, false, 2, 2),
	BOW(Category.DUEL, true, 2, 2),
	CROSSBOW(Category.DUEL, true, 2, 2),
	BOW_SPLEEF(Category.PARTY, true, 2, 16),
	TNT_RUN(Category.PARTY, false, 2, 16),
	SUMO(Category.PARTY, false, 2, 16),
	KOTH(Category.PARTY, false, 2, 16),
	CTF(Category.PARTY, false, 2, 16),
	PARKOUR(Category.PARTY, false, 2, 16),
	LMS(Category.PARTY, false, 2, 16);

	public enum Category { DUEL, PARTY }

	private final Category category;
	private final boolean ranged;
	private final int defaultMin;
	private final int defaultMax;

	GameMode(Category category, boolean ranged, int defaultMin, int defaultMax) {
		this.category = category;
		this.ranged = ranged;
		this.defaultMin = defaultMin;
		this.defaultMax = defaultMax;
	}

	public Category category() {
		return category;
	}

	public boolean isDuel() {
		return category == Category.DUEL;
	}

	public boolean isParty() {
		return category == Category.PARTY;
	}

	/** Bow and Crossbow live in the Ranged menu, the others in Duels. */
	public boolean isRanged() {
		return ranged;
	}

	public int defaultMinPlayers() {
		return defaultMin;
	}

	public int defaultMaxPlayers() {
		return defaultMax;
	}

	/** Stable id used in config files, player data and commands, e.g. {@code bow_spleef}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static GameMode byId(String id) {
		if (id == null) {
			return null;
		}
		for (GameMode m : values()) {
			if (m.id().equalsIgnoreCase(id.trim())) {
				return m;
			}
		}
		return null;
	}

	public static GameMode[] duels() {
		return new GameMode[] {SWORD, AXE, MACE, BOW, CROSSBOW};
	}

	public static GameMode[] parties() {
		return new GameMode[] {BOW_SPLEEF, TNT_RUN, SUMO, KOTH, CTF, PARKOUR, LMS};
	}
}
