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
		return t.open() ? islands(t, rnd, ranged) : walled(t, rnd, ranged);
	}

	/** Spawn column on the west side; the other spawn is its mirror image. */
	private static int spawnX(Field f) {
		return -(f.r - 4);
	}

	private static void spawns(Blueprint bp, Field f) {
		int sx = spawnX(f);
		bp.spawn(sx + 0.5, f.height(sx, 0) + 1, 0.5, -90);
		bp.spawn(-sx + 0.5, f.height(-sx, 0) + 1, 0.5, 90);
	}

	private static Blueprint walled(Theme t, RandomSource rnd, boolean ranged) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.random(rnd);
		int r = 15 + rnd.nextInt(7);
		Field f = new Field(r, shape, Symmetry.POINT);
		f.keepClear(spawnX(f), 0, 3);
		center(bp, f, t, rnd);
		f.hills(rnd, 2 + rnd.nextInt(4), rnd.nextInt(3) == 0 ? 1 : 2);
		Features.scatter(bp, f, t, rnd, ranged ? 5 + rnd.nextInt(4) : 3 + rnd.nextInt(3), Features.kinds(t, ranged));
		f.build(bp, t, rnd, 6);
		f.walls(bp, t, rnd, 4);
		Features.plants(bp, f, t, rnd, 7);
		Scenery.around(bp, t, rnd, r + 4, r + 12, 0);
		spawns(bp, f);
		bp.spectator(0.5, f.maxHeight() + 15, -(r + 8) + 0.5, 0, 35);
		bp.voidY(-12);
		bp.radius(r + 24);
		return bp;
	}

	/** Open themes: a main island and smaller islands at different heights (room for mace and wind charges). */
	private static Blueprint islands(Theme t, RandomSource rnd, boolean ranged) {
		Blueprint bp = new Blueprint();
		Shape shape = Shape.roundish(rnd);
		int r = 13 + rnd.nextInt(5);
		Field f = new Field(r, shape, Symmetry.POINT);
		f.keepClear(spawnX(f), 0, 3);
		center(bp, f, t, rnd);
		f.hills(rnd, 2 + rnd.nextInt(3), 1);
		Features.scatter(bp, f, t, rnd, ranged ? 4 + rnd.nextInt(3) : 2 + rnd.nextInt(3), Features.kinds(t, ranged));
		f.build(bp, t, rnd, 8);
		Features.plants(bp, f, t, rnd, 8);
		// Satellite islands on one side and their mirror images
		int count = 2 + rnd.nextInt(3);
		for (int i = 0; i < count; i++) {
			double a = Math.PI / 2 + rnd.nextDouble() * Math.PI;
			double dist = r + 6 + rnd.nextInt(6);
			int x = (int) Math.round(Math.cos(a) * dist);
			int z = (int) Math.round(Math.sin(a) * dist);
			int y = 4 + rnd.nextInt(12);
			double ir = 3 + rnd.nextDouble() * 2.5;
			Blueprint isl = new Blueprint();
			Build.island(isl, 0, 0, 0, ir, 4, () -> t.floor(rnd), t.under(), t.rough(), rnd);
			if (t.hasTrees() && rnd.nextBoolean()) {
				Build.tree(isl, 0, 1, 0, 4, t.log(), t.leaves(), rnd);
			} else if (t == Theme.END) {
				Build.pillar(isl, 0, 1, 0, 2 + rnd.nextInt(3), Build.s("obsidian"));
			}
			Features.stamp(bp, isl, Symmetry.POINT, x, y, z);
		}
		Scenery.around(bp, t, rnd, r + 14, r + 22, -10);
		spawns(bp, f);
		bp.spectator(0.5, 24, -(r + 14) + 0.5, 0, 35);
		bp.voidY(-14);
		bp.radius(r + 30);
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
