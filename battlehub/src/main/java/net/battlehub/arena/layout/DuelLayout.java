package net.battlehub.arena.layout;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1v1 arena: a round floor with walls, two spawns facing each other and some cover. Each theme dresses it up:
 * Forest (hedges, trees, clearings), Castle (stone walls, towers, courtyard), Nether (netherrack, lava moat,
 * basalt), Ice (snow, slippery packed ice, ice spikes) and Sky (floating islands without walls, lots of height).
 */
public final class DuelLayout {
	private DuelLayout() {}

	public static final int R = 17;

	public static Blueprint design(Theme theme, RandomSource rnd) {
		Blueprint bp = new Blueprint();
		if (theme == Theme.SKY) {
			sky(bp, rnd);
		} else {
			walled(bp, theme, rnd);
		}
		bp.spawn(-11.5, 1, 0.5, -90);
		bp.spawn(12.5, 1, 0.5, 90);
		bp.spectator(0.5, 15, -(R + 9) + 0.5, 0, 32);
		bp.voidY(-12);
		bp.radius(R + 22);
		return bp;
	}

	private static void walled(Blueprint bp, Theme t, RandomSource rnd) {
		Build.island(bp, 0, 0, 0, R + 2, 5, () -> t.floor(rnd), t.under(), t.rough(), rnd);
		// Walls
		BlockState wall = t.wall();
		for (int y = 1; y <= 4; y++) {
			Build.ring(bp, 0, y, 0, R, R + 2, () -> wall);
		}
		switch (t) {
			case FOREST -> forest(bp, t, rnd);
			case CASTLE -> castle(bp, t, rnd);
			case NETHER -> nether(bp, t, rnd);
			case ICE -> ice(bp, t, rnd);
			default -> { }
		}
		// Lights on the wall every 45 degrees
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int x = (int) Math.round(Math.cos(a) * (R + 1));
			int z = (int) Math.round(Math.sin(a) * (R + 1));
			bp.set(x, 5, z, t.light());
		}
	}

	private static void forest(Blueprint bp, Theme t, RandomSource rnd) {
		// The wall becomes a hedge on a log base
		BlockState log = Build.s("oak_log");
		BlockState leaves = Build.leaves("oak_leaves");
		Build.ring(bp, 0, 1, 0, R, R + 2, () -> log);
		for (int y = 2; y <= 5; y++) {
			Build.ring(bp, 0, y, 0, R, R + 2, () -> leaves);
		}
		// Trees around the arena on a wider ground ring
		Build.ring(bp, 0, 0, 0, R + 2, R + 9, () -> t.floor(rnd));
		Build.ring(bp, 0, -1, 0, R + 2, R + 8, t::under);
		for (int i = 0; i < 14; i++) {
			double a = i * Math.PI * 2 / 14 + rnd.nextDouble() * 0.2;
			double r = R + 5 + rnd.nextInt(3);
			Build.tree(bp, (int) Math.round(Math.cos(a) * r), 1, (int) Math.round(Math.sin(a) * r), 5 + rnd.nextInt(3),
					rnd.nextBoolean() ? log : Build.s("birch_log"), rnd.nextBoolean() ? leaves : Build.leaves("birch_leaves"), rnd);
		}
		// Two small trees and bushes inside as cover; the middle stays an open clearing
		Build.tree(bp, 0, 1, 7, 4, log, leaves, rnd);
		Build.tree(bp, 0, 1, -7, 4, log, leaves, rnd);
		for (int i = 0; i < 6; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int x = (int) Math.round(Math.cos(a) * (R - 3));
			int z = (int) Math.round(Math.sin(a) * (R - 3));
			if (Math.abs(z) < 3) continue;
			bp.set(x, 1, z, leaves);
			if (rnd.nextBoolean()) bp.set(x, 2, z, leaves);
		}
		String[] plants = {"short_grass", "short_grass", "short_grass", "fern", "poppy", "dandelion", "cornflower", "azure_bluet"};
		for (int x = -R + 1; x < R; x++) {
			for (int z = -R + 1; z < R; z++) {
				if (Build.dist(x, z) < R - 1 && !bp.has(x, 1, z) && rnd.nextInt(9) == 0 && bp.get(x, 0, z).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
					bp.set(x, 1, z, Build.s(plants[rnd.nextInt(plants.length)]));
				}
			}
		}
	}

	private static void castle(Blueprint bp, Theme t, RandomSource rnd) {
		BlockState wall = t.wall();
		// Crenellations
		for (int x = -(R + 2); x <= R + 2; x++) {
			for (int z = -(R + 2); z <= R + 2; z++) {
				double d = Build.dist(x, z);
				if (d > R + 0.3 && d <= R + 2.3 && ((x + z) & 1) == 0) {
					bp.set(x, 5, z, wall);
				}
			}
		}
		// Four towers
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int x = (int) Math.round(Math.cos(a) * (R + 1));
			int z = (int) Math.round(Math.sin(a) * (R + 1));
			Build.tower(bp, x, 0, z, 3, 9, wall, t.rough(), t.light());
		}
		// Courtyard: a cross of chiseled bricks and two low cover walls
		for (int i = -R + 2; i <= R - 2; i++) {
			bp.set(i, 0, 0, t.accent());
			bp.set(0, 0, i, t.accent());
		}
		for (int x = -2; x <= 2; x++) {
			bp.set(x, 1, 7, wall);
			bp.set(x, 2, 7, Build.slab("stone_brick_slab", false));
			bp.set(x, 1, -7, wall);
			bp.set(x, 2, -7, Build.slab("stone_brick_slab", false));
		}
		bp.set(0, 3, 7, Build.s("lantern"));
		bp.set(0, 3, -7, Build.s("lantern"));
	}

	private static void nether(Blueprint bp, Theme t, RandomSource rnd) {
		// Lava moat outside the walls, held in by a netherrack rim
		Build.ring(bp, 0, 0, 0, R + 2, R + 6, () -> Build.s("netherrack"));
		Build.ring(bp, 0, -1, 0, R + 2, R + 6, () -> Build.s("netherrack"));
		Build.ring(bp, 0, 0, 0, R + 2, R + 4, () -> Build.s("lava"));
		Build.ring(bp, 0, 1, 0, R + 4, R + 6, () -> Build.s("nether_bricks"));
		// Basalt columns as cover
		int[][] cols = {{0, 7}, {0, -7}, {-6, 11}, {6, -11}};
		for (int[] c : cols) {
			int h = 3 + rnd.nextInt(2);
			Build.pillar(bp, c[0], 1, c[1], h, Build.s("basalt"));
			bp.set(c[0], 1 + h, c[1], Build.s("shroomlight"));
		}
		// Fungus and roots on nylium
		for (int x = -R + 1; x < R; x++) {
			for (int z = -R + 1; z < R; z++) {
				if (!bp.has(x, 1, z) && bp.get(x, 0, z).is(net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM) && rnd.nextInt(4) == 0) {
					bp.set(x, 1, z, Build.s(rnd.nextBoolean() ? "crimson_roots" : "crimson_fungus"));
				}
			}
		}
		// Wall caps
		Build.ring(bp, 0, 5, 0, R, R + 2, () -> Build.slab("nether_brick_slab", false));
	}

	private static void ice(Blueprint bp, Theme t, RandomSource rnd) {
		Build.ring(bp, 0, 5, 0, R, R + 2, () -> Build.s("blue_ice"));
		// Ice spikes around the arena
		Build.ring(bp, 0, 0, 0, R + 2, R + 8, () -> Build.s("snow_block"));
		for (int i = 0; i < 10; i++) {
			double a = i * Math.PI * 2 / 10 + rnd.nextDouble() * 0.3;
			double r = R + 5 + rnd.nextInt(2);
			Build.spike(bp, (int) Math.round(Math.cos(a) * r), 1, (int) Math.round(Math.sin(a) * r), 7 + rnd.nextInt(7), Build.s("packed_ice"));
		}
		// Slippery packed-ice lanes through the middle
		for (int i = -R + 2; i <= R - 2; i++) {
			bp.set(i, 0, 3, Build.s("packed_ice"));
			bp.set(i, 0, -3, Build.s("packed_ice"));
		}
		// Snow piles along the wall
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				double d = Build.dist(x, z);
				if (d > R - 2.5 && d <= R && !bp.has(x, 1, z) && rnd.nextInt(3) == 0) {
					bp.set(x, 1, z, Build.s("snow"));
				}
			}
		}
		int[][] cover = {{0, 8}, {0, -8}};
		for (int[] c : cover) {
			Build.spike(bp, c[0], 1, c[1], 4, Build.s("packed_ice"));
		}
	}

	private static void sky(Blueprint bp, RandomSource rnd) {
		Theme t = Theme.SKY;
		BlockState stone = Build.s("stone");
		Build.island(bp, 0, 0, 0, 15, 7, () -> t.floor(rnd), t.under(), stone, rnd);
		// Satellite islands at different heights: room for wind-charge jumps and mace smashes
		int[][] isl = {{0, 9, 23}, {0, 6, -23}, {23, 12, 0}, {-23, 8, 0}, {16, 17, 16}, {-16, 15, -16}};
		for (int[] i : isl) {
			Build.island(bp, i[0], i[1], i[2], 5, 4, () -> t.floor(rnd), t.under(), stone, rnd);
			if (rnd.nextBoolean()) {
				Build.tree(bp, i[0], i[1] + 1, i[2], 4, Build.s("oak_log"), Build.leaves("oak_leaves"), rnd);
			}
		}
		// A few quartz ruins on the main island as cover
		for (int[] c : new int[][] {{0, 7}, {0, -7}}) {
			Build.pillar(bp, c[0], 1, c[1], 4, t.pillar());
			bp.set(c[0], 5, c[1], t.light());
		}
		// Cloud puffs below for depth
		for (int i = 0; i < 6; i++) {
			int cx = rnd.nextInt(60) - 30;
			int cz = rnd.nextInt(60) - 30;
			if (Build.dist(cx, cz) < 18) continue;
			Build.disc(bp, cx, -18, cz, 2 + rnd.nextInt(3), () -> Build.s("white_wool"));
		}
	}
}
