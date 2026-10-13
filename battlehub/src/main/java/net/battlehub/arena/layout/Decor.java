package net.battlehub.arena.layout;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Theme scenery around an arena, out of reach of the players. */
final class Decor {
	private Decor() {}

	/**
	 * Fills a ring between two radii with theme scenery: trees (Forest), towers (Castle), lava pools and basalt
	 * (Nether), ice spikes (Ice) or small floating islands (Sky). {@code y} is the ground height of the ring.
	 */
	static void ring(Blueprint bp, Theme t, RandomSource rnd, int rIn, int rOut, int y) {
		int count = Math.max(4, (rIn + rOut) / 3);
		switch (t) {
			case FOREST -> {
				Build.ring(bp, 0, y, 0, rIn, rOut, () -> t.floor(rnd));
				Build.ring(bp, 0, y - 1, 0, rIn, rOut - 1, t::under);
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.3;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					BlockState log = rnd.nextInt(3) == 0 ? Build.s("birch_log") : Build.s("oak_log");
					BlockState leaves = rnd.nextInt(3) == 0 ? Build.leaves("birch_leaves") : Build.leaves("oak_leaves");
					Build.tree(bp, (int) Math.round(Math.cos(a) * r), y + 1, (int) Math.round(Math.sin(a) * r), 4 + rnd.nextInt(3), log, leaves, rnd);
				}
			}
			case CASTLE -> {
				Build.ring(bp, 0, y, 0, rIn, rOut, () -> Build.s("cobblestone"));
				for (int dy = 1; dy <= 3; dy++) {
					Build.ring(bp, 0, y + dy, 0, rOut - 2, rOut, t::wall);
				}
				int towers = Math.max(4, count / 2);
				for (int i = 0; i < towers; i++) {
					double a = i * Math.PI * 2 / towers;
					Build.tower(bp, (int) Math.round(Math.cos(a) * (rOut - 1)), y, (int) Math.round(Math.sin(a) * (rOut - 1)), 3, 8, t.wall(), t.rough(), t.light());
				}
			}
			case NETHER -> {
				Build.ring(bp, 0, y, 0, rIn, rOut, () -> Build.s("netherrack"));
				Build.ring(bp, 0, y - 1, 0, rIn, rOut, () -> Build.s("netherrack"));
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.4;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					int x = (int) Math.round(Math.cos(a) * r);
					int z = (int) Math.round(Math.sin(a) * r);
					if (rnd.nextBoolean()) {
						Build.disc(bp, x, y, z, 1.5, () -> Build.s("lava"));
					} else {
						int h = 3 + rnd.nextInt(5);
						Build.pillar(bp, x, y + 1, z, h, Build.s("basalt"));
						bp.set(x, y + 1 + h, z, Build.s(rnd.nextBoolean() ? "glowstone" : "shroomlight"));
					}
				}
				// Keep the lava pools from flowing over the edge
				Build.ring(bp, 0, y, 0, rOut, rOut + 1, () -> Build.s("nether_bricks"));
				Build.ring(bp, 0, y + 1, 0, rOut, rOut + 1, () -> Build.s("nether_bricks"));
			}
			case ICE -> {
				Build.ring(bp, 0, y, 0, rIn, rOut, () -> Build.s("snow_block"));
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.3;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					Build.spike(bp, (int) Math.round(Math.cos(a) * r), y + 1, (int) Math.round(Math.sin(a) * r), 5 + rnd.nextInt(8),
							Build.s(rnd.nextInt(4) == 0 ? "blue_ice" : "packed_ice"));
				}
			}
			case SKY -> {
				for (int i = 0; i < count / 2 + 2; i++) {
					double a = i * Math.PI * 2 / (count / 2 + 2) + rnd.nextDouble() * 0.5;
					double r = (rIn + rOut) / 2.0 + rnd.nextInt(5) - 2;
					int x = (int) Math.round(Math.cos(a) * r);
					int z = (int) Math.round(Math.sin(a) * r);
					int iy = y - 6 + rnd.nextInt(14);
					Build.island(bp, x, iy, z, 2.5 + rnd.nextDouble() * 2, 4, () -> t.floor(rnd), t.under(), Build.s("stone"), rnd);
					if (rnd.nextInt(3) == 0) {
						Build.tree(bp, x, iy + 1, z, 4, Build.s("oak_log"), Build.leaves("oak_leaves"), rnd);
					}
				}
			}
		}
	}

	/** Block that disappears in Bow Spleef / TNT Run, per theme. */
	static BlockState breakable(Theme t, RandomSource rnd) {
		return switch (t) {
			case FOREST -> rnd.nextInt(4) == 0 ? Build.leaves("oak_leaves") : Build.s("moss_block");
			case CASTLE -> Build.s(rnd.nextInt(3) == 0 ? "andesite" : "smooth_stone");
			case NETHER -> Build.s(rnd.nextInt(3) == 0 ? "soul_soil" : "netherrack");
			case ICE -> Build.s(rnd.nextInt(4) == 0 ? "packed_ice" : "snow_block");
			case SKY -> Build.s(rnd.nextInt(4) == 0 ? "light_gray_wool" : "white_wool");
		};
	}
}
