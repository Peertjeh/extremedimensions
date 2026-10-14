package net.battlehub.arena.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Obstacles and cover for generated arenas. Each feature is built once into a small blueprint and stamped twice
 * through the field's symmetry, so both halves get exactly the same cover.
 */
public final class Features {
	private Features() {}

	public enum Kind { PILLAR, WALL, BOULDER, TREE, RUIN, SPIKE, ARCH, PLATFORM }

	/** Which kinds suit a theme (ranged duels also get raised platforms). */
	public static List<Kind> kinds(Theme t, boolean ranged) {
		List<Kind> k = new ArrayList<>(List.of(Kind.PILLAR, Kind.WALL, Kind.BOULDER, Kind.RUIN, Kind.ARCH));
		if (t.hasTrees()) {
			k.add(Kind.TREE);
			k.add(Kind.TREE);
		}
		if (t == Theme.ICE || t == Theme.END || t == Theme.DEEP_DARK || t == Theme.NETHER || t == Theme.BADLANDS) {
			k.add(Kind.SPIKE);
		}
		if (ranged) {
			k.add(Kind.PLATFORM);
			k.add(Kind.PLATFORM);
		}
		return k;
	}

	/** Footprint radius of a kind (columns from its centre). */
	static int radius(Kind kind) {
		return switch (kind) {
			case PILLAR, SPIKE -> 1;
			case TREE, BOULDER, RUIN, ARCH -> 2;
			case WALL, PLATFORM -> 2;
		};
	}

	/**
	 * Places up to {@code count} features at free spots on one half (x &lt; 0) and their mirror images. Columns
	 * under a feature are raised to its highest column so nothing floats.
	 */
	public static void scatter(Blueprint bp, Field f, Theme t, RandomSource rnd, int count, List<Kind> kinds) {
		int placed = 0;
		for (int attempt = 0; attempt < count * 25 && placed < count; attempt++) {
			Kind kind = kinds.get(rnd.nextInt(kinds.size()));
			int rad = radius(kind);
			int x = -(rad + 2 + rnd.nextInt(Math.max(1, f.r - rad - 2)));
			int z = rnd.nextInt(2 * f.r + 1) - f.r;
			if (!f.insideWithMargin(x, z, rad + 1) || !f.free(x, z, rad + 1)) {
				continue;
			}
			place(bp, f, t, rnd, kind, x, z);
			placed++;
		}
	}

	/** Builds one feature at (x, z) and at its mirror image. */
	public static void place(Blueprint bp, Field f, Theme t, RandomSource rnd, Kind kind, int x, int z) {
		int rad = radius(kind);
		int base = 0;
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				base = Math.max(base, f.height(x + dx, z + dz));
			}
		}
		// Level the footprint on both sides
		int mx = f.symmetry.x(x, z);
		int mz = f.symmetry.z(x, z);
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				f.setHeight(x + dx, z + dz, base);
				f.setHeight(mx + dx, mz + dz, base);
			}
		}
		f.use(x, z, rad + 1);
		Blueprint fb = new Blueprint();
		build(fb, t, rnd, kind);
		stamp(bp, fb, f.symmetry, x, base + 1, z);
	}

	/** Copies a feature to (x, y, z) and to the mirror image of that spot (mirrored too). */
	public static void stamp(Blueprint bp, Blueprint fb, Symmetry sym, int x, int y, int z) {
		for (Map.Entry<Long, BlockState> e : fb.blocks().entrySet()) {
			long k = e.getKey();
			int dx = BlockPos.getX(k);
			int dy = BlockPos.getY(k);
			int dz = BlockPos.getZ(k);
			bp.set(x + dx, y + dy, z + dz, e.getValue());
			bp.set(sym.x(x + dx, z + dz), y + dy, sym.z(x + dx, z + dz), e.getValue());
		}
	}

	/** The feature itself, standing on y = 0 of its own little blueprint. */
	public static void build(Blueprint fb, Theme t, RandomSource rnd, Kind kind) {
		switch (kind) {
			case PILLAR -> {
				int hgt = 2 + rnd.nextInt(3);
				boolean wide = rnd.nextBoolean();
				for (int dx = 0; dx <= (wide ? 1 : 0); dx++) {
					for (int dz = 0; dz <= (wide ? 1 : 0); dz++) {
						Build.pillar(fb, dx, 0, dz, hgt, t.pillar());
					}
				}
				fb.set(0, hgt, 0, t.light());
			}
			case WALL -> {
				boolean alongX = rnd.nextBoolean();
				int len = 3 + rnd.nextInt(3);
				for (int i = -len / 2; i <= len / 2; i++) {
					int hgt = rnd.nextInt(4) == 0 ? 1 : 2;
					Build.pillar(fb, alongX ? i : 0, 0, alongX ? 0 : i, hgt, rnd.nextInt(5) == 0 ? t.rough() : t.wall());
				}
			}
			case BOULDER -> {
				double rr = 1.3 + rnd.nextDouble() * 0.9;
				int ir = (int) Math.ceil(rr);
				for (int dx = -ir; dx <= ir; dx++) {
					for (int dy = 0; dy <= ir; dy++) {
						for (int dz = -ir; dz <= ir; dz++) {
							if (dx * dx + dy * dy * 1.6 + dz * dz <= rr * rr) {
								fb.set(dx, dy, dz, rnd.nextInt(4) == 0 ? t.under() : t.rough());
							}
						}
					}
				}
			}
			case TREE -> Build.tree(fb, 0, 0, 0, 4 + rnd.nextInt(3), t.log(), t.leaves(), rnd);
			case RUIN -> {
				for (int i = -2; i <= 2; i++) {
					int hgt = 1 + rnd.nextInt(3);
					Build.pillar(fb, i, 0, -2, hgt, rnd.nextInt(3) == 0 ? t.rough() : t.wall());
				}
				for (int i = -1; i <= 2; i++) {
					int hgt = 1 + rnd.nextInt(2);
					Build.pillar(fb, -2, 0, i, hgt, rnd.nextInt(3) == 0 ? t.rough() : t.wall());
				}
			}
			case SPIKE -> {
				BlockState b = switch (t) {
					case ICE -> Build.s(rnd.nextBoolean() ? "packed_ice" : "blue_ice");
					case END -> Build.s("obsidian");
					case DEEP_DARK -> Build.s("deepslate_bricks");
					case NETHER -> Build.s("basalt");
					default -> t.rough();
				};
				Build.spike(fb, 0, 0, 0, 4 + rnd.nextInt(4), b);
			}
			case ARCH -> {
				boolean alongX = rnd.nextBoolean();
				for (int i = -2; i <= 2; i += 4) {
					Build.pillar(fb, alongX ? i : 0, 0, alongX ? 0 : i, 3, t.pillar());
				}
				for (int i = -2; i <= 2; i++) {
					fb.set(alongX ? i : 0, 3, alongX ? 0 : i, t.wall());
				}
				fb.set(0, 4, 0, t.light());
			}
			case PLATFORM -> {
				// A 3x3 platform two blocks up with a one-block step, for height in ranged fights
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						fb.set(dx, 0, dz, t.under());
						fb.set(dx, 1, dz, t.floor(rnd));
					}
				}
				fb.set(2, 0, 0, t.trim());
				fb.set(-1, 2, -1, t.light());
			}
		}
	}

	/** Plants on free floor columns. */
	public static void plants(Blueprint bp, Field f, Theme t, RandomSource rnd, int oneIn) {
		for (int x = -f.r; x <= f.r; x++) {
			for (int z = -f.r; z <= f.r; z++) {
				if (!f.inside(x, z) || f.used(x, z) || rnd.nextInt(oneIn) != 0) continue;
				int top = f.height(x, z);
				if (!bp.has(x, top + 1, z)) {
					BlockState plant = t.plant(rnd);
					if (plant != null) bp.set(x, top + 1, z, plant);
				}
			}
		}
	}
}
