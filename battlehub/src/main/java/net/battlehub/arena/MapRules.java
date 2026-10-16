package net.battlehub.arena;

import java.util.List;

import net.battlehub.core.GameMode;

/**
 * Which games can be played on hand-built maps, and what such a map must have. Games whose arena is part of
 * the game itself (TNT Run's layers, Bow Spleef's floor, parkour courses, Bed Wars, Prop Hunt, Capture the Flag,
 * 1v2 and 1v3, the tournament) always use generated arenas.
 */
public final class MapRules {
	private MapRules() {}

	public static boolean allows(GameMode mode) {
		return mode.isDuel() || switch (mode) {
			case FFA, LMS, SUMO, KOTH, SPLEEF, SKYWARS, MURDER_MYSTERY -> true;
			default -> false;
		};
	}

	public static int spawns(GameMode mode) {
		return mode.isDuel() ? 2 : 1;
	}

	public static List<String> marks(GameMode mode) {
		return switch (mode) {
			case BRIDGE, BRIDGE_CHAOS -> List.of("goal_red", "goal_blue", "build_min", "build_max");
			case KOTH -> List.of("zone");
			default -> List.of();
		};
	}

	/** The maps a mode can use. */
	public static List<CustomMaps.Map> maps(GameMode mode) {
		return allows(mode) ? CustomMaps.forMode(mode, spawns(mode), marks(mode)) : List.of();
	}
}
