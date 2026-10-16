package net.battlehub.arena.gen;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground of a generated arena: an outline, a height per column (0 = base floor) and which columns are taken by
 * features or kept clear. Hills are added in pairs through the symmetry, so both sides get the same terrain.
 */
public final class Field {
	public final int r;
	public final Shape shape;
	public final Symmetry symmetry;
	private final int off;
	private final int[][] h;
	private final boolean[][] used;

	public Field(int r, Shape shape, Symmetry symmetry) {
		this.r = r;
		this.shape = shape;
		this.symmetry = symmetry;
		this.off = r + 2;
		int n = 2 * off + 1;
		this.h = new int[n][n];
		this.used = new boolean[n][n];
	}

	public boolean inside(int x, int z) {
		return Math.abs(x) <= r + 1 && Math.abs(z) <= r + 1 && shape.inside(x, z, r);
	}

	/** Inside, and at least {@code margin} columns away from the outline. */
	public boolean insideWithMargin(int x, int z, int margin) {
		for (int dx = -margin; dx <= margin; dx++) {
			for (int dz = -margin; dz <= margin; dz++) {
				if (!inside(x + dx, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	public int height(int x, int z) {
		return inside(x, z) ? h[x + off][z + off] : 0;
	}

	public void setHeight(int x, int z, int value) {
		if (inside(x, z)) {
			h[x + off][z + off] = value;
		}
	}

	public int maxHeight() {
		int m = 0;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				m = Math.max(m, height(x, z));
			}
		}
		return m;
	}

	public boolean used(int x, int z) {
		return !inside(x, z) || used[x + off][z + off];
	}

	/** Marks a square of columns (and its mirror image) as taken. */
	public void use(int cx, int cz, int rad) {
		for (int x = cx - rad; x <= cx + rad; x++) {
			for (int z = cz - rad; z <= cz + rad; z++) {
				mark(x, z);
				mark(symmetry.x(x, z), symmetry.z(x, z));
			}
		}
	}

	private void mark(int x, int z) {
		if (Math.abs(x) <= off && Math.abs(z) <= off) {
			used[x + off][z + off] = true;
		}
	}

	/** Free square of columns (both it and its mirror image). */
	public boolean free(int cx, int cz, int rad) {
		for (int x = cx - rad; x <= cx + rad; x++) {
			for (int z = cz - rad; z <= cz + rad; z++) {
				if (used(x, z) || used(symmetry.x(x, z), symmetry.z(x, z))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Keeps a round area flat (height 0) and free, e.g. around a spawn. */
	public void keepClear(int cx, int cz, int rad) {
		for (int x = cx - rad; x <= cx + rad; x++) {
			for (int z = cz - rad; z <= cz + rad; z++) {
				if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= rad * rad + rad) {
					setHeight(x, z, 0);
					setHeight(symmetry.x(x, z), symmetry.z(x, z), 0);
					mark(x, z);
					mark(symmetry.x(x, z), symmetry.z(x, z));
				}
			}
		}
	}

	/** Keeps a band between two radii free of features (spawn rings). */
	public void keepBandClear(double rIn, double rOut) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d >= rIn && d <= rOut) {
					mark(x, z);
					setHeight(x, z, 0);
				}
			}
		}
	}

	/** Gentle hills: {@code count} bumps on one side and their mirror images, at most {@code maxH} high. */
	public void hills(RandomSource rnd, int count, int maxH) {
		if (maxH <= 0) {
			return;
		}
		double[][] acc = new double[h.length][h.length];
		for (int i = 0; i < count; i++) {
			int cx = -(2 + rnd.nextInt(Math.max(1, r - 2)));
			int cz = rnd.nextInt(2 * r + 1) - r;
			double rad = 3 + rnd.nextDouble() * 4;
			double height = 1 + rnd.nextDouble() * maxH;
			bump(acc, cx, cz, rad, height);
			bump(acc, symmetry.x(cx, cz), symmetry.z(cx, cz), rad, height);
		}
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (inside(x, z) && !used(x, z)) {
					h[x + off][z + off] = (int) Math.max(0, Math.min(maxH, Math.floor(acc[x + off][z + off])));
				}
			}
		}
		relax();
	}

	private void bump(double[][] acc, int cx, int cz, double rad, double height) {
		int ir = (int) Math.ceil(rad);
		for (int x = cx - ir; x <= cx + ir; x++) {
			for (int z = cz - ir; z <= cz + ir; z++) {
				if (Math.abs(x) > off || Math.abs(z) > off) continue;
				double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (d < rad) {
					acc[x + off][z + off] += height * (1 - d / rad);
				}
			}
		}
	}

	/** Neighbouring columns differ by at most one block, so every hill can be walked onto. */
	public void relax() {
		for (int pass = 0; pass < 4; pass++) {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (!inside(x, z)) continue;
					int lowest = Integer.MAX_VALUE;
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						if (inside(x + d[0], z + d[1])) {
							lowest = Math.min(lowest, height(x + d[0], z + d[1]));
						}
					}
					if (lowest != Integer.MAX_VALUE && height(x, z) > lowest + 1) {
						setHeight(x, z, lowest + 1);
					}
				}
			}
		}
	}

	/** Floor blocks on top of every column, filler below and a rough, tapering underside. */
	public void build(Blueprint bp, Theme t, RandomSource rnd, int depth) {
		build(bp, null, t, rnd, depth);
	}

	/** The same, with the floor following the arena's colour gradient and pattern. */
	public void build(Blueprint bp, Style style, RandomSource rnd, int depth) {
		build(bp, style, style.theme, rnd, depth);
	}

	private void build(Blueprint bp, Style style, Theme t, RandomSource rnd, int depth) {
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				if (!inside(x, z)) continue;
				int top = height(x, z);
				bp.set(x, top, z, style == null ? t.floor(rnd) : style.floor(x, z, rnd));
				for (int y = -1; y < top; y++) {
					bp.set(x, y, z, t.under());
				}
				double d = Math.sqrt(x * x + z * z) / Math.max(1, r);
				for (int y = 2; y <= depth; y++) {
					if (d < 1.0 - (double) y / (depth + 1) + rnd.nextDouble() * 0.12) {
						bp.set(x, -y, z, y <= 3 ? t.under() : t.rough());
					}
				}
			}
		}
	}

	/** Columns just outside the outline (where a wall goes). */
	public boolean isRim(int x, int z) {
		if (inside(x, z)) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (inside(x + dx, z + dz)) return true;
			}
		}
		return false;
	}

	/** Highest floor next to a rim column. */
	public int rimBase(int x, int z) {
		int m = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (inside(x + dx, z + dz)) m = Math.max(m, height(x + dx, z + dz));
			}
		}
		return m;
	}

	/** Every column inside the outline (packed with {@link Blueprint#column}). */
	public java.util.Set<Long> columns() {
		java.util.Set<Long> out = new java.util.HashSet<>();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				if (inside(x, z)) out.add(Blueprint.column(x, z));
			}
		}
		return out;
	}

	/**
	 * Invisible barrier blocks on top of the wall (made with the same {@code height}) up to {@code top}, so
	 * nobody builds or climbs over it and water stays inside.
	 */
	public void barrier(Blueprint bp, int height, int top) {
		for (int x = -r - 2; x <= r + 2; x++) {
			for (int z = -r - 2; z <= r + 2; z++) {
				if (!isRim(x, z)) continue;
				for (int y = rimBase(x, z) + height + 2; y <= top; y++) {
					bp.set(x, y, z, Build.s("barrier"));
				}
			}
		}
	}

	/** A wall around the outline, {@code height} blocks above the floor next to it, in the theme's style. */
	public void walls(Blueprint bp, Theme t, RandomSource rnd, int height) {
		walls(bp, null, t, rnd, height);
	}

	/** The same, fading from foot to top in the arena's wall gradient where the theme has one. */
	public void walls(Blueprint bp, Style style, RandomSource rnd, int height) {
		walls(bp, style, style.theme, rnd, height);
	}

	private void walls(Blueprint bp, Style style, Theme t, RandomSource rnd, int height) {
		int step = 0;
		for (int x = -r - 2; x <= r + 2; x++) {
			for (int z = -r - 2; z <= r + 2; z++) {
				if (!isRim(x, z)) continue;
				int base = rimBase(x, z);
				int topY = base + height;
				for (int y = -1; y <= topY; y++) {
					BlockState styled = style == null ? null : style.wall(y - base, height, x, z);
					bp.set(x, y, z, styled != null && usesStyledWall(t) ? styled : wallBlock(t, y - base, height, rnd));
				}
				switch (t) {
					case CASTLE, DESERT, BADLANDS -> {
						if (((x + z) & 1) == 0) bp.set(x, topY + 1, z, t.wall());
					}
					case NETHER, DEEP_DARK -> bp.set(x, topY + 1, z, Build.slab(t == Theme.NETHER ? "nether_brick_slab" : "polished_deepslate_slab", false));
					case ICE -> bp.set(x, topY, z, Build.s("blue_ice"));
					default -> { }
				}
				if ((step++ % 7) == 0) {
					bp.set(x, topY + 1, z, t.light());
				}
			}
		}
	}

	/** Hedges, mushroom caps and terracotta bands keep their own look. */
	private static boolean usesStyledWall(Theme t) {
		return switch (t) {
			case FOREST, JUNGLE, CHERRY, MUSHROOM, BADLANDS -> false;
			default -> true;
		};
	}

	private static BlockState wallBlock(Theme t, int above, int height, RandomSource rnd) {
		return switch (t) {
			// Hedges: a log base with leaves on top
			case FOREST, JUNGLE, CHERRY -> above <= 0 ? t.log() : t.leaves();
			case MUSHROOM -> above >= height ? Build.s("red_mushroom_block") : t.wall();
			case CASTLE -> rnd.nextInt(6) == 0 ? Build.s("mossy_stone_bricks") : t.wall();
			case BADLANDS -> Build.s(switch (Math.floorMod(above, 4)) {
				case 0 -> "orange_terracotta";
				case 1 -> "terracotta";
				case 2 -> "red_terracotta";
				default -> "yellow_terracotta";
			});
			default -> t.wall();
		};
	}
}
