package net.battlehub.arena.layout;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.gen.Scenery;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Theme scenery and blocks shared by the party layouts. */
final class Decor {
	private Decor() {}

	/** Theme scenery in a ring around an arena; {@code y} is the ground height of the ring. */
	static void ring(Blueprint bp, Theme t, RandomSource rnd, int rIn, int rOut, int y) {
		Scenery.around(bp, t, rnd, rIn, rOut, y);
	}

	/** Block that disappears in Bow Spleef / TNT Run, per theme. */
	static BlockState breakable(Theme t, RandomSource rnd) {
		return t.breakable(rnd);
	}
}
