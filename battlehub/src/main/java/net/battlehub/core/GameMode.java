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
	LMS(Category.PARTY, false, 2, 16),
	FFA(Category.PARTY, false, 2, 16),
	/** Teams of 1, 2, 4 or 8 in a single-elimination bracket; every match best of 3. */
	TOURNAMENT(Category.PARTY, false, 2, 16),
	/** Teams with beds and resource generators; no bed, no respawn. */
	BED_WARS(Category.PARTY, false, 2, 16),
	/** Hunters look for props hidden as blocks. */
	PROP_HUNT(Category.PARTY, false, 2, 16),
	/** One player against a team of two, with a sword, axe or mace kit. */
	ONE_V_TWO("1v2", Category.TEAM, false, 3, 3),
	/** One player against a team of three. */
	ONE_V_THREE("1v3", Category.TEAM, false, 4, 4);

	/** DUEL: 1v1 with matchmaking; PARTY: a lobby that starts with enough players; TEAM: one against a team. */
	public enum Category { DUEL, PARTY, TEAM }

	private final String id;
	private final Category category;
	private final boolean ranged;
	private final int defaultMin;
	private final int defaultMax;

	GameMode(Category category, boolean ranged, int defaultMin, int defaultMax) {
		this(null, category, ranged, defaultMin, defaultMax);
	}

	GameMode(String id, Category category, boolean ranged, int defaultMin, int defaultMax) {
		this.id = id == null ? name().toLowerCase(Locale.ROOT) : id;
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

	/** 1v2 or 1v3. */
	public boolean isTeamFight() {
		return category == Category.TEAM;
	}

	/** Players against the one: 2 for 1v2, 3 for 1v3 (1 otherwise). */
	public int opponents() {
		return this == ONE_V_TWO ? 2 : this == ONE_V_THREE ? 3 : 1;
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

	/** Stable id used in config files, player data and commands, e.g. {@code bow_spleef} or {@code 1v2}. */
	public String id() {
		return id;
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
		return new GameMode[] {BOW_SPLEEF, TNT_RUN, SUMO, KOTH, CTF, PARKOUR, LMS, FFA, TOURNAMENT, BED_WARS, PROP_HUNT};
	}

	/** The Party Games menu (Free for All is under PvP). */
	public static GameMode[] partyMenu() {
		return new GameMode[] {BOW_SPLEEF, TNT_RUN, SUMO, KOTH, CTF, PARKOUR, LMS, TOURNAMENT, BED_WARS, PROP_HUNT};
	}

	public static GameMode[] teamFights() {
		return new GameMode[] {ONE_V_TWO, ONE_V_THREE};
	}

	/** Kits for PvP (FFA, 1v1, 1v2, 1v3). */
	public static GameMode[] meleeKits() {
		return new GameMode[] {SWORD, AXE, MACE};
	}
}
