package net.battlehub.hub;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The lobby BattleHub builds in its own world: a round plaza with a fountain, a compass-rose floor and lantern
 * posts, and four bridges to small islands in the style of the arenas (Castle, Forest, Nether, Ice).
 * Floor at y=0, centre at 0,0; players spawn south of the fountain, looking at it.
 */
final class HubLayout {
	private HubLayout() {}

	static final int RADIUS = 22;
	static final double SPAWN_X = 0.5;
	static final double SPAWN_Y = 1;
	static final double SPAWN_Z = 9.5;
	static final float SPAWN_YAW = 180;

	static Blueprint design(long seed) {
		RandomSource rnd = RandomSource.create(seed);
		Blueprint bp = new Blueprint();
		plaza(bp, rnd);
		fountain(bp);
		posts(bp);
		bridge(bp, 0, -1);
		bridge(bp, 1, 0);
		bridge(bp, 0, 1);
		bridge(bp, -1, 0);
		castle(bp, rnd, 0, -36);
		forest(bp, rnd, 36, 0);
		nether(bp, rnd, 0, 36);
		ice(bp, rnd, -36, 0);
		bp.spawn(SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_YAW);
		bp.spectator(SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_YAW, 0);
		bp.radius(60);
		bp.voidY(-40);
		return bp;
	}

	private static BlockState floorAt(int x, int z, RandomSource rnd) {
		double d = Build.dist(x, z);
		if (d <= 5.6) {
			return Build.s("smooth_quartz");
		}
		if (d > RADIUS - 2.5) {
			return Build.s(rnd.nextInt(5) == 0 ? "mossy_stone_bricks" : "stone_bricks");
		}
		boolean spoke = x == 0 || z == 0 || x == z || x == -z;
		if (spoke) {
			return Build.s(d < 12 ? "polished_deepslate" : "deepslate_tiles");
		}
		if (Math.abs(d - 12) < 0.7) {
			return Build.s("polished_blackstone_bricks");
		}
		return Build.s(((x + z) & 1) == 0 ? "polished_andesite" : "smooth_stone");
	}

	private static void plaza(Blueprint bp, RandomSource rnd) {
		int r = RADIUS;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z <= r * r + r * 0.6) {
					bp.set(x, 0, z, floorAt(x, z, rnd));
				}
			}
		}
		// Rough underside
		for (int d = 1; d <= 9; d++) {
			double rr = r * (1.0 - (double) d / 10) + rnd.nextDouble();
			BlockState st = Build.s(d <= 2 ? "stone" : (rnd.nextBoolean() ? "andesite" : "cobblestone"));
			Build.disc(bp, 0, -d, 0, rr, () -> st);
		}
		// Lights flush with the floor at the ends of the spokes
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			bp.set((int) Math.round(Math.cos(a) * 16), 0, (int) Math.round(Math.sin(a) * 16), Build.s("sea_lantern"));
		}
		// Low rim with gaps for the four bridges
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d2 = x * x + z * z;
				boolean rim = d2 <= r * r + r * 0.6 && d2 > (r - 1) * (r - 1) + (r - 1) * 0.6;
				boolean gap = Math.abs(x) <= 2 || Math.abs(z) <= 2;
				if (rim && !gap) {
					bp.set(x, 1, z, Build.s("stone_bricks"));
					if (((x + z) & 1) == 0) {
						bp.set(x, 2, z, Build.slab("stone_brick_slab", false));
					}
				}
			}
		}
	}

	private static void fountain(Blueprint bp) {
		// Basin: the floor inside is lowered by one and filled with water, held in by a quartz rim
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double d = Build.dist(x, z);
				if (d <= 3.4) {
					bp.set(x, 0, z, Build.s("water"));
					bp.set(x, -1, z, Build.s("prismarine_bricks"));
				} else if (d <= 4.5) {
					bp.set(x, 1, z, Build.s("quartz_bricks"));
				}
			}
		}
		Build.pillar(bp, 0, -1, 0, 5, Build.s("quartz_pillar"));
		bp.set(0, 4, 0, Build.s("sea_lantern"));
		bp.set(0, 5, 0, Build.s("chiseled_quartz_block"));
		bp.set(0, 6, 0, Build.s("lantern"));
	}

	private static void posts(Blueprint bp) {
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int x = (int) Math.round(Math.cos(a) * 9);
			int z = (int) Math.round(Math.sin(a) * 9);
			Build.pillar(bp, x, 1, z, 3, Build.s("polished_andesite"));
			bp.set(x, 4, z, Build.s("lantern"));
		}
	}

	/** Bridge from the plaza rim to an island 36 blocks out, in direction (dx, dz). */
	private static void bridge(Blueprint bp, int dx, int dz) {
		for (int s = RADIUS - 2; s <= 30; s++) {
			for (int w = -2; w <= 2; w++) {
				int x = dx * s + (dz != 0 ? w : 0);
				int z = dz * s + (dx != 0 ? w : 0);
				boolean edge = Math.abs(w) == 2;
				bp.set(x, 0, z, Build.s(edge ? "stone_bricks" : (w == 0 ? "polished_deepslate" : "smooth_stone")));
				bp.set(x, -1, z, Build.s("stone_bricks"));
				if (edge && s > RADIUS && (s & 1) == 0) {
					bp.set(x, 1, z, Build.slab("stone_brick_slab", false));
				}
				if (edge && s > RADIUS && s % 4 == 0) {
					bp.set(x, 1, z, Build.s("stone_bricks"));
					bp.set(x, 2, z, Build.s("lantern"));
				}
			}
		}
	}

	private static void castle(Blueprint bp, RandomSource rnd, int cx, int cz) {
		Build.island(bp, cx, 0, cz, 8, 6, () -> Build.s(rnd.nextInt(4) == 0 ? "cracked_stone_bricks" : "stone_bricks"),
				Build.s("cobblestone"), Build.s("andesite"), rnd);
		Build.tower(bp, cx, 1, cz - 2, 4, 10, Build.s("stone_bricks"), Build.s("stone_brick_slab"), Build.s("lantern"));
		for (int i = -6; i <= 6; i += 12) {
			Build.pillar(bp, cx + i, 1, cz + 3, 4, Build.s("stone_bricks"));
			bp.set(cx + i, 5, cz + 3, Build.s("lantern"));
		}
	}

	private static void forest(Blueprint bp, RandomSource rnd, int cx, int cz) {
		Build.island(bp, cx, 0, cz, 8, 6, () -> Build.s(rnd.nextInt(5) == 0 ? "moss_block" : "grass_block"),
				Build.s("dirt"), Build.s("stone"), rnd);
		int[][] trees = {{3, -4}, {-2, 4}, {4, 3}};
		for (int[] t : trees) {
			Build.tree(bp, cx + t[0], 1, cz + t[1], 4 + rnd.nextInt(2), Build.s("oak_log"), Build.leaves("oak_leaves"), rnd);
		}
		for (int i = 0; i < 10; i++) {
			int x = cx + rnd.nextInt(13) - 6;
			int z = cz + rnd.nextInt(13) - 6;
			if (bp.has(x, 0, z) && !bp.has(x, 1, z)) {
				bp.set(x, 1, z, Build.s(rnd.nextBoolean() ? "short_grass" : (rnd.nextBoolean() ? "poppy" : "dandelion")));
			}
		}
	}

	private static void nether(Blueprint bp, RandomSource rnd, int cx, int cz) {
		Build.island(bp, cx, 0, cz, 8, 6, () -> Build.s(rnd.nextInt(4) == 0 ? "crimson_nylium" : "netherrack"),
				Build.s("netherrack"), Build.s("blackstone"), rnd);
		// Portal frame (no portal)
		for (int y = 1; y <= 5; y++) {
			bp.set(cx - 2, y, cz + 2, Build.s("obsidian"));
			bp.set(cx + 2, y, cz + 2, Build.s("obsidian"));
		}
		for (int x = -2; x <= 2; x++) {
			bp.set(cx + x, 1, cz + 2, Build.s("obsidian"));
			bp.set(cx + x, 5, cz + 2, Build.s("obsidian"));
		}
		int[][] basalt = {{-5, -3}, {5, -2}, {-4, 4}};
		for (int[] b : basalt) {
			Build.pillar(bp, cx + b[0], 1, cz + b[1], 3 + rnd.nextInt(3), Build.s("basalt"));
		}
		bp.set(cx + 4, 0, cz + 4, Build.s("shroomlight"));
		bp.set(cx - 4, 0, cz - 4, Build.s("shroomlight"));
	}

	private static void ice(Blueprint bp, RandomSource rnd, int cx, int cz) {
		Build.island(bp, cx, 0, cz, 8, 6, () -> Build.s(rnd.nextInt(4) == 0 ? "packed_ice" : "snow_block"),
				Build.s("packed_ice"), Build.s("blue_ice"), rnd);
		Build.spike(bp, cx - 3, 1, cz - 3, 7, Build.s("packed_ice"));
		Build.spike(bp, cx + 3, 1, cz + 2, 5, Build.s("packed_ice"));
		Build.spike(bp, cx - 2, 1, cz + 4, 4, Build.s("blue_ice"));
		bp.set(cx + 4, 0, cz - 4, Build.s("sea_lantern"));
	}
}
