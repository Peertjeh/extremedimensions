package net.battlehub.core;

import java.util.Locale;

/** Every playable mode. Pure data: no Minecraft types, so matchmaking and stats stay testable. */
public enum GameMode {
	SWORD(Category.DUEL, false, 2, 2),
	AXE(Category.DUEL, false, 2, 2),
	MACE(Category.DUEL, false, 2, 2),
	BOW(Category.DUEL, true, 2, 2),
	CROSSBOW(Category.DUEL, true, 2, 2),
	/** Two islands with a goal each and a bridge to build between them; jump into the other goal to score. */
	BRIDGE(Category.DUEL, false, 2, 2),
	/** The Bridge with power-ups and random events (a test mode next to the normal Bridge; never ranked). */
	BRIDGE_CHAOS(Category.DUEL, false, 2, 2),
	/** No damage: the first to land 100 hits wins. */
	BOXING(Category.DUEL, false, 2, 2),
	/** Build, buckets, golden apples and no natural healing. */
	BUILD_UHC(Category.DUEL, false, 2, 2),
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
	/** Dig the snow away under the others with a shovel; the last one standing wins. */
	SPLEEF(Category.PARTY, false, 2, 16),
	/** Everyone on their own island with chests; loot, bridge over and be the last one alive. */
	SKYWARS(Category.PARTY, false, 2, 12),
	/** One murderer, one detective, the rest innocent: find the murderer before everyone is gone. */
	MURDER_MYSTERY(Category.PARTY, false, 4, 16),
	/** The Bridge, two against two. */
	BRIDGE_DUOS("bridge_2v2", Category.PARTY, false, 4, 4),
	/** The Bridge, everyone on their own island (up to four). */
	BRIDGE_FOUR("bridge_1v1v1v1", Category.PARTY, false, 2, 4),
	/** The Bridge 1v1v1v1 with the power-ups and events of The Bridge: Chaos. */
	BRIDGE_FOUR_CHAOS("bridge_chaos_1v1v1v1", Category.PARTY, false, 2, 4),
	/** The lobby city of the week as the map: loot the houses, the landmarks and the cornucopia, be the last one alive. */
	HUNGER_GAMES(Category.PARTY, false, 2, 16),
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

	/** Never ranked: chance (power-ups, events) plays too big a part for ELO. */
	public boolean isUnranked() {
		return this == BRIDGE_CHAOS || this == BRIDGE_FOUR_CHAOS;
	}

	/** Highlighted in the menus as the players' favourite. */
	public boolean isFanFavorite() {
		return this == BRIDGE_CHAOS;
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
		return new GameMode[] {SWORD, AXE, MACE, BOW, CROSSBOW, BRIDGE, BRIDGE_CHAOS, BOXING, BUILD_UHC};
	}

	/** Duels with ranked queues (all but the unranked ones). */
	public static GameMode[] rankedDuels() {
		return java.util.Arrays.stream(duels()).filter(m -> !m.isUnranked()).toArray(GameMode[]::new);
	}

	/** Duels that are only a kit (no rules of their own): these can be tournament kits. */
	public static GameMode[] kitDuels() {
		return new GameMode[] {SWORD, AXE, MACE, BOW, CROSSBOW};
	}

	public boolean isKitDuel() {
		for (GameMode m : kitDuels()) {
			if (m == this) return true;
		}
		return false;
	}

	/** Duels with rules of their own (the Duel Games menu). */
	public static GameMode[] duelGames() {
		return new GameMode[] {BRIDGE, BRIDGE_CHAOS, BOXING, BUILD_UHC};
	}

	public static GameMode[] parties() {
		return new GameMode[] {BOW_SPLEEF, TNT_RUN, SUMO, KOTH, CTF, PARKOUR, LMS, FFA, TOURNAMENT, BED_WARS, PROP_HUNT,
				SPLEEF, SKYWARS, MURDER_MYSTERY, BRIDGE_DUOS, BRIDGE_FOUR, BRIDGE_FOUR_CHAOS, HUNGER_GAMES};
	}

	/** The Party Games menu (Free for All is under PvP). */
	public static GameMode[] partyMenu() {
		return new GameMode[] {BOW_SPLEEF, TNT_RUN, SUMO, KOTH, CTF, PARKOUR, LMS, TOURNAMENT, BED_WARS, PROP_HUNT,
				SPLEEF, SKYWARS, MURDER_MYSTERY, BRIDGE_DUOS, BRIDGE_FOUR, BRIDGE_FOUR_CHAOS, HUNGER_GAMES};
	}

	public static GameMode[] teamFights() {
		return new GameMode[] {ONE_V_TWO, ONE_V_THREE};
	}

	/** Kits for PvP (FFA, 1v1, 1v2, 1v3). */
	public static GameMode[] meleeKits() {
		return new GameMode[] {SWORD, AXE, MACE};
	}
}
