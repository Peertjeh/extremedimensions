package net.battlehub.arch.arena;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Noise3;
import net.battlehub.arch.Slot;
import net.battlehub.arch.mod.Ctx;

/**
 * The land of one floating island in its own frame: a coastline wobbling round a radius (mirror-symmetric or
 * four-fold symmetric when the island must be fair to more than one side), a flat top at y = 0 and a rocky
 * underside that tapers to a point, with lumps so it does not look turned on a lathe.
 */
final class Isle {
	enum Symmetry {
		NONE, MIRROR, FOUR
	}

	final double radius;
	private final Noise3 coast;
	private final Noise3 under;
	private final Symmetry sym;
	private final double rough;
	private final double deep;

	Isle(long seed, double radius, double rough, double deep, Symmetry sym) {
		this.radius = radius;
		this.coast = new Noise3(seed ^ 0xC0A57L);
		this.under = new Noise3(seed ^ 0xDEE9L);
		this.sym = sym;
		this.rough = rough;
		this.deep = deep;
	}

	/** Folds a column into the part of the island that decides its shape. */
	private int[] fold(int x, int z) {
		return switch (sym) {
			case NONE -> new int[] {x, z};
			case MIRROR -> new int[] {Math.abs(x), z};
			case FOUR -> {
				int a = Math.abs(x), b = Math.abs(z);
				yield new int[] {Math.max(a, b), Math.min(a, b)};
			}
		};
	}

	/** The coast's distance from the centre in the direction of this column. */
	double edge(int x, int z) {
		int[] f = fold(x, z);
		double a = Math.atan2(f[0], f[1]);
		double n = coast.at(Math.cos(a) * 2.2 + 5, 0, Math.sin(a) * 2.2 + 5, 1.0, 2);
		return radius * (1 - rough + 2 * rough * n);
	}

	boolean land(int x, int z) {
		return Math.hypot(x, z) <= edge(x, z);
	}

	/** How far the island reaches down under a column (blocks below the top). */
	int depth(int x, int z) {
		int[] f = fold(x, z);
		double e = edge(x, z);
		double d = Math.hypot(x, z);
		double inner = Math.max(0, e - d);
		double lump = under.at(f[0] * 0.9, 3, f[1] * 0.9, 4.5, 2);
		return Math.max(1, (int) Math.round(1 + inner * deep + (lump - 0.5) * 4 * Math.min(1, inner / 2)));
	}

	int reach() {
		return (int) Math.ceil(radius * (1 + rough)) + 1;
	}

	/** Draws the island: ground on top, soil under it, rock below (meant to float). */
	void draw(Ctx c) {
		draw(c, null);
	}

	/** The same, with {@code hanging} (roots, vines) dangling here and there from the underside. */
	void draw(Ctx c, String hanging) {
		int r = reach();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (!land(x, z)) continue;
				int d = depth(x, z);
				c.set(x, 0, z, Cell.of(Slot.GROUND, Cell.TERRAIN).flag(Cell.FLOAT));
				for (int y = 1; y <= d; y++) {
					c.set(x, -y, z, Cell.of(y <= 2 ? Slot.SOIL : Slot.ROCK, Cell.TERRAIN).flag(Cell.FLOAT));
				}
				if (hanging != null && d >= 2 && net.battlehub.arch.Rng.hash(radius > 0 ? (long) (radius * 1000) : 1, x, 0, z) < 0.07) {
					c.set(x, -d - 1, z, Cell.literal(hanging, Cell.DETAIL).flag(Cell.FLOAT));
				}
			}
		}
	}
}
