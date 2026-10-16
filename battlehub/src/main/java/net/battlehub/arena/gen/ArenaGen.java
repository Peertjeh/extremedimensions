package net.battlehub.arena.gen;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Random duel arenas: every match gets a new outline, size, terrain and set of obstacles, dressed in its theme.
 * Point symmetry gives both players the same ground, cover and distance to everything.
 */
public final class ArenaGen {
	private ArenaGen() {}

	public static Blueprint duel(Theme t, RandomSource rnd, boolean ranged) {
		return duel(t, rnd, ranged, 1.0, 1);
	}

	/**
	 * {@code scale}: size factor (mace and team fights get more room); {@code perSide}: players per side, who
	 * start next to each other in rows of up to four.
	 */
	public static Blueprint duel(Theme t, RandomSource rnd, boolean ranged, double scale, int perSide) {
		double s = Math.max(0.5, Math.min(3.0, scale));
		int n = Math.max(1, Math.min(8, perSide));
		return t.open() ? islands(t, rnd, ranged, s, n) : walled(t, rnd, ranged, s, n, 0);
	}

	/**
	 * An arena to build in (Build UHC): always walled, also in the open themes, with room for {@code buildHeight}
	 * blocks above the ground and an invisible barrier above the wall up to there.
	 */
	public static Blueprint buildArena(Theme t, RandomSource rnd, double scale, int buildHeight) {
		return walled(t, rnd, false, Math.max(0.5, Math.min(3.0, scale)), 1, Math.max(6, buildHeight));
	}

	private static final int WALL_HEIGHT = 4;

	/** Spawn column on the west side (this far in from the edge); the other spawn is its mirror image. */
	private static int spawnX(Field f, int margin) {
		return -(f.r - margin);
	}

	/** How far in from the edge the spawns are: different every arena, but never closer than 16 blocks apart. */
	private static int margin(Field f, RandomSource rnd) {
		return Math.max(3, Math.min(3 + rnd.nextInt(5), f.r - 8));
	}

	private static int rows(int perSide) {
		return (perSide + 3) / 4;
	}

	/** Keeps the ground around the spawns flat and free. */
	private static void clearSpawns(Field f, int perSide, int margin) {
		int rows = rows(perSide);
		f.keepClear(spawnX(f, margin) + (rows - 1), 0, 2 + rows + (perSide > 1 ? 1 : 0));
	}

	/** West side first (spawns 0 .. n-1), then the east side in the same order (mirrored through the centre). */
	private static void spawns(Blueprint bp, Field f, int perSide, int margin) {
		int sx = spawnX(f, margin);
		int[][] west = new int[perSide][];
		for (int i = 0; i < perSide; i++) {
			int row = i / 4;
			int inRow = Math.min(4, perSide - row * 4);
			west[i] = new int[] {sx + 2 * row, perSide == 1 ? 0 : -(inRow - 1) + 2 * (i % 4)};
		}
		for (int[] w : west) {
			bp.spawn(w[0] + 0.5, f.height(w[0], w[1]) + 1, w[1] + 0.5, -90);
		}
		for (int[] w : west) {
			bp.spawn(-w[0] + 0.5, f.height(-w[0], -w[1]) + 1, -w[1] + 0.5, 90);
		}
	}

	private static int scaled(int value, double factor) {
		return Math.max(1, (int) Math.round(value * factor));
	}

	private static Blueprint walled(Theme t, RandomSource rnd, boolean ranged, double s, int perSide, int buildHeight) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.random(rnd);
		// Size, spawn distance, terrain, walls and look all differ from arena to arena
		int r = scaled(13 + rnd.nextInt(12), s);
		Field f = new Field(r, shape, Symmetry.POINT);
		int margin = margin(f, rnd);
		Style style = Style.of(t, rnd, r);
		clearSpawns(f, perSide, margin);
		int mosaic = center(bp, f, style, rnd);
		terrain(f, rnd, s);
		int features = ranged ? 4 + rnd.nextInt(6) : 2 + rnd.nextInt(5);
		Features.scatter(bp, f, t, rnd, scaled(features, s * s), Features.kinds(t, ranged));
		f.build(bp, style, rnd, 6);
		mosaic(bp, style, mosaic);
		int wallHeight = 3 + rnd.nextInt(4);
		f.walls(bp, style, rnd, wallHeight);
		Features.plants(bp, f, t, rnd, 4 + rnd.nextInt(8));
		Scenery.around(bp, t, rnd, r + 4, r + 12, 0);
		spawns(bp, f, perSide, margin);
		int top = buildHeight > 0 ? f.maxHeight() + buildHeight : f.maxHeight() + 40;
		bp.playArea(f.columns(), top);
		if (buildHeight > 0) {
			f.barrier(bp, wallHeight, top + 2);
		}
		bp.spectator(0.5, f.maxHeight() + scaled(15, s), -(r + 8) + 0.5, 0, 35);
		bp.voidY(-12);
		bp.radius(r + 24);
		return bp;
	}

	/** Open themes: a main island and smaller islands at different heights (room for mace and wind charges). */
	private static Blueprint islands(Theme t, RandomSource rnd, boolean ranged, double s, int perSide) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.roundish(rnd);
		int r = scaled(11 + rnd.nextInt(9), s);
		Field f = new Field(r, shape, Symmetry.POINT);
		int margin = margin(f, rnd);
		Style style = Style.of(t, rnd, r);
		clearSpawns(f, perSide, margin);
		int mosaic = center(bp, f, style, rnd);
		f.hills(rnd, scaled(1 + rnd.nextInt(4), s), rnd.nextInt(4) == 0 ? 2 : 1);
		int features = ranged ? 3 + rnd.nextInt(4) : 1 + rnd.nextInt(4);
		Features.scatter(bp, f, t, rnd, scaled(features, s * s), Features.kinds(t, ranged));
		f.build(bp, style, rnd, 6 + rnd.nextInt(5));
		mosaic(bp, style, mosaic);
		Features.plants(bp, f, t, rnd, 5 + rnd.nextInt(6));
		// Satellite islands on one side and their mirror images: how many, how far and how high differs every time
		int count = scaled(1 + rnd.nextInt(5), s);
		for (int i = 0; i < count; i++) {
			double a = Math.PI / 2 + rnd.nextDouble() * Math.PI;
			double dist = r + scaled(5, s) + rnd.nextInt(scaled(8, s));
			int x = (int) Math.round(Math.cos(a) * dist);
			int z = (int) Math.round(Math.sin(a) * dist);
			int y = 2 + rnd.nextInt(scaled(14, s));
			double ir = (2.5 + rnd.nextDouble() * 3.5) * Math.sqrt(s);
			Blueprint isl = new Blueprint();
			Build.island(isl, 0, 0, 0, ir, 4, () -> t.floor(rnd), t.under(), t.rough(), rnd);
			if (t.hasTrees() && rnd.nextBoolean()) {
				Build.tree(isl, 0, 1, 0, 4, t.log(), t.leaves(), rnd);
			} else if (t == Theme.END) {
				Build.pillar(isl, 0, 1, 0, 2 + rnd.nextInt(3), Build.s("obsidian"));
			}
			Features.stamp(bp, isl, Symmetry.POINT, x, y, z);
		}
		int far = scaled(14, s);
		Scenery.around(bp, t, rnd, r + far, r + far + 8, -10);
		spawns(bp, f, perSide, margin);
		// The islands and the satellites round them; the scenery further out is not part of the field
		bp.playArea(Blueprint.circle(r + scaled(13, s) + 9), f.maxHeight() + 40);
		bp.spectator(0.5, scaled(24, s), -(r + far) + 0.5, 0, 35);
		bp.voidY(-14);
		bp.radius(r + far + scaled(16, s));
		return bp;
	}

	/** Flat, gentle, rolling or hilly: how much the ground moves differs per arena. */
	private static void terrain(Field f, RandomSource rnd, double s) {
		switch (rnd.nextInt(5)) {
			case 0 -> { }
			case 1 -> f.hills(rnd, scaled(2 + rnd.nextInt(3), s), 1);
			case 2 -> f.hills(rnd, scaled(3 + rnd.nextInt(4), s), 2);
			case 3 -> f.hills(rnd, scaled(5 + rnd.nextInt(4), s), 3);
			default -> f.hills(rnd, scaled(1 + rnd.nextInt(3), s), 2);
		}
	}

	/**
	 * Something in the middle: a hill, a pillar group, a fountain, a monument, a crater, a plateau, ruins or a
	 * mosaic in the floor (or nothing).
	 */
	private static int center(Blueprint bp, Field f, Style style, RandomSource rnd) {
		Theme t = style.theme;
		switch (rnd.nextInt(10)) {
			case 0 -> {
				// Stepped hill
				for (int x = -3; x <= 3; x++) {
					for (int z = -3; z <= 3; z++) {
						int d = Math.max(Math.abs(x), Math.abs(z));
						f.setHeight(x, z, d <= 1 ? 2 : 1);
					}
				}
				f.use(0, 0, 3);
			}
			case 1 -> {
				// Four pillars around an open centre
				for (int[] c : new int[][] {{-2, -2}, {2, 2}, {-2, 2}, {2, -2}}) {
					Build.pillar(bp, c[0], 1, c[1], 3, t.pillar());
					bp.set(c[0], 4, c[1], t.light());
				}
				f.use(0, 0, 2);
			}
			case 2 -> {
				if (t == Theme.NETHER || t.open()) {
					return 0;
				}
				// Small fountain: water held in by a rim
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
						if (rim) {
							bp.set(x, 1, z, t.trim());
						} else {
							bp.set(x, 1, z, Build.s("water"));
						}
					}
				}
				Build.pillar(bp, 0, 1, 0, 2, t.pillar());
				bp.set(0, 3, 0, t.light());
				f.use(0, 0, 2);
			}
			case 3 -> {
				// Monument: a plus-shaped column with a light (symmetric around the centre column)
				BlockState b = t.accent();
				for (int[] c : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					Build.pillar(bp, c[0], 1, c[1], c[0] == 0 && c[1] == 0 ? 5 : 3, b);
				}
				bp.set(0, 6, 0, t.light());
				f.use(0, 0, 2);
			}
			case 4 -> {
				// Crater: a ring of raised ground round a flat middle
				for (int x = -6; x <= 6; x++) {
					for (int z = -6; z <= 6; z++) {
						double d = Math.sqrt(x * x + z * z);
						if (d >= 3.5 && d <= 6.2) f.setHeight(x, z, d < 4.6 || d > 5.4 ? 1 : 2);
					}
				}
				f.use(0, 0, 6);
			}
			case 5 -> {
				// Plateau: a raised square with a light at each corner
				int h = 2 + rnd.nextInt(2);
				for (int x = -4; x <= 4; x++) {
					for (int z = -4; z <= 4; z++) {
						int d = Math.max(Math.abs(x), Math.abs(z));
						f.setHeight(x, z, Math.max(0, h - Math.max(0, d - 2)));
					}
				}
				for (int[] c : new int[][] {{-2, -2}, {2, 2}, {-2, 2}, {2, -2}}) {
					bp.set(c[0], h + 1, c[1], t.light());
				}
				f.use(0, 0, 4);
			}
			case 6 -> {
				// Ruins: broken pillars of different heights in a ring
				int n = 5 + rnd.nextInt(4);
				double ringR = 3 + rnd.nextDouble() * 2;
				for (int i = 0; i < n; i++) {
					if (i % 2 == 1) continue;
					double a = i * Math.PI * 2 / n;
					int x = (int) Math.round(Math.cos(a) * ringR);
					int z = (int) Math.round(Math.sin(a) * ringR);
					int h = 1 + rnd.nextInt(4);
					Build.pillar(bp, x, 1, z, h, t.pillar());
					Build.pillar(bp, -x, 1, -z, h, t.pillar());
				}
				f.use(0, 0, 1);
			}
			case 7, 8 -> {
				// A mosaic in the floor (laid after the floor): the middle stays flat and free
				int rad = 3 + rnd.nextInt(3);
				f.use(0, 0, rad);
				return rad;
			}
			default -> { }
		}
		return 0;
	}

	/** Rings of the arena's gradient round the middle, with a light in the centre. */
	private static void mosaic(Blueprint bp, Style style, int rad) {
		if (rad <= 0) {
			return;
		}
		for (int x = -rad; x <= rad; x++) {
			for (int z = -rad; z <= rad; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d <= rad + 0.3) bp.set(x, 0, z, style.floorAt(1 - d / (rad + 1)));
			}
		}
		bp.set(0, 0, 0, style.theme.light());
	}
}
