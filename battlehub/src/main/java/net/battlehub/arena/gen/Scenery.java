package net.battlehub.arena.gen;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Theme scenery in a ring around an arena, out of reach of the players. */
public final class Scenery {
	private Scenery() {}

	private static int angleX(double a, double r) {
		return (int) Math.round(Math.cos(a) * r);
	}

	private static int angleZ(double a, double r) {
		return (int) Math.round(Math.sin(a) * r);
	}

	/**
	 * Fills the ring between {@code rIn} and {@code rOut}; {@code y} is the ground height of the ring. With
	 * {@code arenas.architecture} on, the architecture engine builds it (stands, islands, landmarks); otherwise the
	 * simple scenery of earlier versions.
	 */
	public static void around(Blueprint bp, Theme t, RandomSource rnd, int rIn, int rOut, int y) {
		if (net.battlehub.config.ConfigManager.config().arenas.architecture) {
			net.battlehub.arena.ArchArenas.surroundings(bp, t, rnd.nextLong(), rIn, rOut, y);
			return;
		}
		simple(bp, t, rnd, rIn, rOut, y);
	}

	/** The scenery of earlier versions: a ring of trees, towers, spikes or columns, or floating islands. */
	public static void simple(Blueprint bp, Theme t, RandomSource rnd, int rIn, int rOut, int y) {
		int count = Math.max(5, (rIn + rOut) / 3);
		if (t.open()) {
			floatingIslands(bp, t, rnd, rIn, rOut, y, count);
			return;
		}
		switch (t) {
			case CASTLE -> {
				Build.ring(bp, 0, y, 0, rIn, rOut, () -> Build.s("cobblestone"));
				for (int dy = 1; dy <= 3; dy++) {
					Build.ring(bp, 0, y + dy, 0, rOut - 2, rOut, t::wall);
				}
				int towers = Math.max(4, count / 2);
				for (int i = 0; i < towers; i++) {
					double a = i * Math.PI * 2 / towers;
					Build.tower(bp, angleX(a, rOut - 1), y, angleZ(a, rOut - 1), 3, 8, t.wall(), t.rough(), t.light());
				}
			}
			case NETHER -> {
				ground(bp, t, rnd, rIn, rOut, y, Build.s("netherrack"));
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.4;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					int x = angleX(a, r);
					int z = angleZ(a, r);
					if (rnd.nextBoolean()) {
						Build.disc(bp, x, y, z, 1.5, () -> Build.s("lava"));
					} else {
						int h = 3 + rnd.nextInt(5);
						Build.pillar(bp, x, y + 1, z, h, Build.s("basalt"));
						bp.set(x, y + 1 + h, z, Build.s(rnd.nextBoolean() ? "glowstone" : "shroomlight"));
					}
				}
				// Keep the lava from flowing over the edge
				Build.ring(bp, 0, y, 0, rOut, rOut + 1, () -> Build.s("nether_bricks"));
				Build.ring(bp, 0, y + 1, 0, rOut, rOut + 1, () -> Build.s("nether_bricks"));
			}
			case ICE -> {
				ground(bp, t, rnd, rIn, rOut, y, Build.s("snow_block"));
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.3;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					if (rnd.nextInt(3) == 0) {
						Build.tree(bp, angleX(a, r), y + 1, angleZ(a, r), 5 + rnd.nextInt(3), t.log(), t.leaves(), rnd);
					} else {
						Build.spike(bp, angleX(a, r), y + 1, angleZ(a, r), 5 + rnd.nextInt(8), Build.s(rnd.nextInt(4) == 0 ? "blue_ice" : "packed_ice"));
					}
				}
			}
			case DESERT, BADLANDS -> {
				ground(bp, t, rnd, rIn, rOut, y, null);
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.4;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					int x = angleX(a, r);
					int z = angleZ(a, r);
					if (t == Theme.DESERT && rnd.nextBoolean()) {
						int h = 2 + rnd.nextInt(2);
						Build.pillar(bp, x, y, z, 1, Build.s("sand"));
						Build.pillar(bp, x, y + 1, z, h, Build.s("cactus"));
					} else {
						// Mesa column with coloured bands
						int h = 4 + rnd.nextInt(6);
						for (int k = 0; k < h; k++) {
							BlockState b = t == Theme.BADLANDS
									? Build.s(k % 3 == 0 ? "red_terracotta" : (k % 3 == 1 ? "orange_terracotta" : "terracotta"))
									: Build.s(k % 2 == 0 ? "sandstone" : "smooth_sandstone");
							Build.disc(bp, x, y + 1 + k, z, 1.6 - k * 0.08, () -> b);
						}
					}
				}
			}
			case DEEP_DARK -> {
				ground(bp, t, rnd, rIn, rOut, y, null);
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.4;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					int h = 4 + rnd.nextInt(6);
					Build.pillar(bp, angleX(a, r), y + 1, angleZ(a, r), h, Build.s(rnd.nextBoolean() ? "deepslate_bricks" : "cobbled_deepslate"));
					bp.set(angleX(a, r), y + 1 + h, angleZ(a, r), Build.s("soul_lantern"));
				}
			}
			case OCEAN -> {
				ground(bp, t, rnd, rIn, rOut, y, null);
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.4;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					int h = 3 + rnd.nextInt(6);
					String coral = switch (rnd.nextInt(4)) {
						case 0 -> "tube_coral_block";
						case 1 -> "brain_coral_block";
						case 2 -> "fire_coral_block";
						default -> "horn_coral_block";
					};
					Build.pillar(bp, angleX(a, r), y + 1, angleZ(a, r), h, Build.s(rnd.nextInt(3) == 0 ? coral : "prismarine_bricks"));
					bp.set(angleX(a, r), y + 1 + h, angleZ(a, r), Build.s("sea_lantern"));
				}
			}
			default -> {
				// Forest, jungle, cherry, mushroom: a ring of trees
				ground(bp, t, rnd, rIn, rOut, y, null);
				for (int i = 0; i < count; i++) {
					double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.3;
					double r = rIn + 2 + rnd.nextInt(Math.max(1, rOut - rIn - 3));
					Build.tree(bp, angleX(a, r), y + 1, angleZ(a, r), 4 + rnd.nextInt(4), t.log(), t.leaves(), rnd);
				}
			}
		}
	}

	private static void ground(Blueprint bp, Theme t, RandomSource rnd, int rIn, int rOut, int y, BlockState top) {
		Build.ring(bp, 0, y, 0, rIn, rOut, () -> top != null ? top : t.floor(rnd));
		Build.ring(bp, 0, y - 1, 0, rIn, rOut - 1, t::under);
	}

	private static void floatingIslands(Blueprint bp, Theme t, RandomSource rnd, int rIn, int rOut, int y, int count) {
		int n = count / 2 + 2;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + rnd.nextDouble() * 0.5;
			double r = (rIn + rOut) / 2.0 + rnd.nextInt(5) - 2;
			int x = angleX(a, r);
			int z = angleZ(a, r);
			int iy = y - 6 + rnd.nextInt(14);
			Build.island(bp, x, iy, z, 2.5 + rnd.nextDouble() * 2, 4, () -> t.floor(rnd), t.under(), t.rough(), rnd);
			if (t.hasTrees() && rnd.nextInt(3) == 0) {
				Build.tree(bp, x, iy + 1, z, 4, t.log(), t.leaves(), rnd);
			} else if (t == Theme.END && rnd.nextBoolean()) {
				Build.pillar(bp, x, iy + 1, z, 3 + rnd.nextInt(4), Build.s("obsidian"));
				bp.set(x, iy + 4, z, Build.s("end_rod"));
			}
		}
	}
}
