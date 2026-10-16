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

	/** Spawn column on the west side; the other spawn is its mirror image. */
	private static int spawnX(Field f) {
		return -(f.r - 4);
	}

	private static int rows(int perSide) {
		return (perSide + 3) / 4;
	}

	/** Keeps the ground around the spawns flat and free. */
	private static void clearSpawns(Field f, int perSide) {
		int rows = rows(perSide);
		f.keepClear(spawnX(f) + (rows - 1), 0, 2 + rows + (perSide > 1 ? 1 : 0));
	}

	/** West side first (spawns 0 .. n-1), then the east side in the same order (mirrored through the centre). */
	private static void spawns(Blueprint bp, Field f, int perSide) {
		int sx = spawnX(f);
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
		int r = scaled(15 + rnd.nextInt(7), s);
		Field f = new Field(r, shape, Symmetry.POINT);
		clearSpawns(f, perSide);
		center(bp, f, t, rnd);
		f.hills(rnd, scaled(2 + rnd.nextInt(4), s), rnd.nextInt(3) == 0 ? 1 : 2);
		int features = ranged ? 5 + rnd.nextInt(4) : 3 + rnd.nextInt(3);
		Features.scatter(bp, f, t, rnd, scaled(features, s * s), Features.kinds(t, ranged));
		f.build(bp, t, rnd, 6);
		f.walls(bp, t, rnd, WALL_HEIGHT);
		Features.plants(bp, f, t, rnd, 7);
		Scenery.around(bp, t, rnd, r + 4, r + 12, 0);
		spawns(bp, f, perSide);
		int top = buildHeight > 0 ? f.maxHeight() + buildHeight : f.maxHeight() + 40;
		bp.playArea(f.columns(), top);
		if (buildHeight > 0) {
			f.barrier(bp, WALL_HEIGHT, top + 2);
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
		int r = scaled(13 + rnd.nextInt(5), s);
		Field f = new Field(r, shape, Symmetry.POINT);
		clearSpawns(f, perSide);
		center(bp, f, t, rnd);
		f.hills(rnd, scaled(2 + rnd.nextInt(3), s), 1);
		int features = ranged ? 4 + rnd.nextInt(3) : 2 + rnd.nextInt(3);
		Features.scatter(bp, f, t, rnd, scaled(features, s * s), Features.kinds(t, ranged));
		f.build(bp, t, rnd, 8);
		Features.plants(bp, f, t, rnd, 8);
		// Satellite islands on one side and their mirror images
		int count = scaled(2 + rnd.nextInt(3), s);
		for (int i = 0; i < count; i++) {
			double a = Math.PI / 2 + rnd.nextDouble() * Math.PI;
			double dist = r + scaled(6, s) + rnd.nextInt(scaled(6, s));
			int x = (int) Math.round(Math.cos(a) * dist);
			int z = (int) Math.round(Math.sin(a) * dist);
			int y = 4 + rnd.nextInt(scaled(12, s));
			double ir = (3 + rnd.nextDouble() * 2.5) * Math.sqrt(s);
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
		spawns(bp, f, perSide);
		// The islands and the satellites round them; the scenery further out is not part of the field
		bp.playArea(Blueprint.circle(r + scaled(12, s) + 8), f.maxHeight() + 40);
		bp.spectator(0.5, scaled(24, s), -(r + far) + 0.5, 0, 35);
		bp.voidY(-14);
		bp.radius(r + far + scaled(16, s));
		return bp;
	}

	/** Something in the middle (half the time): a hill, a pillar group, a fountain or a monument. */
	private static void center(Blueprint bp, Field f, Theme t, RandomSource rnd) {
		switch (rnd.nextInt(6)) {
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
					return;
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
			default -> { }
		}
	}
}
