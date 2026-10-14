package net.battlehub.arena.layout;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.gen.ArenaGen;
import net.minecraft.util.RandomSource;

/**
 * 1v1 arena: generated new for every match (outline, size, terrain, cover), point-symmetric so both players get
 * the same ground. Walled in most themes; floating islands in the open themes (Sky, End).
 */
public final class DuelLayout {
	private DuelLayout() {}

	public static Blueprint design(Theme theme, RandomSource rnd) {
		return design(theme, rnd, false);
	}

	/** {@code ranged}: bow and crossbow duels get more cover and raised platforms. */
	public static Blueprint design(Theme theme, RandomSource rnd, boolean ranged) {
		return ArenaGen.duel(theme, rnd, ranged);
	}

	/** {@code scale}: size factor (mace arenas are bigger); {@code perSide}: players per team. */
	public static Blueprint design(Theme theme, RandomSource rnd, boolean ranged, double scale, int perSide) {
		return ArenaGen.duel(theme, rnd, ranged, scale, perSide);
	}
}
