package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.extremedimensions.config.Config;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The insides of the Seam's buildings: what the Weavers' people left where they lived and worked. A tower's floors are
 * kept as a library, a weavery, a store or a shrine, its things set along the walls (never in a doorway, on a stair or
 * where a bridge comes in) round a rug; a hall has pews down either side of a runner, candles between the bays and
 * chests by the door; every house a ladder up to the room under its roof, a loom, a table, shelves and a store; a great
 * loom barrels of silk round its floor; a ring of standing stones, now and then, a cache by its fire.
 *
 * <p>Every storeroom is a barrel or a chest that holds what a household put by ({@code chests/seam_storeroom}), filled
 * once it stands, and only if it still does (a ruin may take it: it keeps nothing standing for it). Each holds anything at
 * {@code seam_storeroom_chance}; {@code seam_interiors} at 0 leaves the walls bare. The furnishing draws from a stream of
 * its own, from where the building stands, so the building itself is laid out exactly as before.
 */
final class Furnish {
	private Furnish() {}

	static final int LIBRARY = 0, WEAVERY = 1, STORE = 2, SHRINE = 3, THEMES = 4;

	static boolean on() {
		return Config.SEAM_INTERIORS > 0;
	}

	private static Random stream(Frame f, int salt) {
		return new Random(f.wx(0, 0) * 341873128712L ^ f.wz(0, 0) * 132897987541L ^ (long) f.oy * 0x9E3779B97F4A7C15L ^ salt);
	}

	// ------------------------------------------------------------------ the pieces

	static BlockState faced(BlockState s, Direction d) {
		if (s == null) {
			return null;
		}
		if (d.getAxis().isHorizontal() && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		}
		if (s.hasProperty(BlockStateProperties.FACING)) {
			return s.setValue(BlockStateProperties.FACING, d);
		}
		return s;
	}

	static BlockState candles(Random r) {
		BlockState s = mc("candle");
		if (s == null) {
			return soulLantern();
		}
		if (s.hasProperty(BlockStateProperties.CANDLES)) {
			s = s.setValue(BlockStateProperties.CANDLES, 1 + r.nextInt(4));
		}
		if (s.hasProperty(BlockStateProperties.LIT)) {
			s = s.setValue(BlockStateProperties.LIT, true);
		}
		return s;
	}

	static BlockState barrel() {
		return faced(Blocks.BARREL.defaultBlockState(), Direction.UP);
	}

	static BlockState chest(Direction front) {
		return faced(mc("chest", Blocks.BARREL.defaultBlockState()), front);
	}

	static BlockState carpet(int k) {
		String[] c = {"purple_carpet", "gray_carpet", "magenta_carpet", "light_gray_carpet"};
		return mc(c[Math.floorMod(k, c.length)]);
	}

	static BlockState silk() {
		return mc("white_wool", wool());
	}

	/** A storeroom's barrel or chest at a free spot, filled once it stands (at {@code seam_storeroom_chance}). */
	static boolean store(Frame f, int x, int y, int z, BlockState s, Random r) {
		if (s == null || f.has(x, y, z)) {
			return false;
		}
		f.set(x, y, z, s);
		if (r.nextDouble() < Config.SEAM_STOREROOM_CHANCE) {
			f.thenMinor(x, y, z, stock(f.pos(x, y, z), 0));
		}
		return true;
	}

	private static void put(Frame f, int x, int y, int z, BlockState s) {
		if (s != null && !f.has(x, y, z)) {
			f.set(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ towers

	/**
	 * The floors of a tower: its ground floor, and in a great tower every floor its stair climbs through (the floors of a
	 * slender tower above its ground floor have no way up, so they stay bare).
	 */
	static void tower(Colossus.Tower w, boolean great) {
		if (!on()) {
			return;
		}
		Random r = stream(w.f, 1);
		int theme = r.nextInt(THEMES);
		for (int i = 0; i < w.floors.size(); i++) {
			int[] fl = w.floors.get(i);
			int h = fl[1];
			if (h < 4 || i > 0 && !(great && h >= 5)) {
				continue;
			}
			floor(w, r, fl[0], h, Colossus.Form.values()[fl[2]], great, (theme + i) % THEMES, w.sc.ruin);
		}
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static void floor(Colossus.Tower w, Random r, int y0, int h, Colossus.Form form, boolean great, int theme, boolean ruin) {
		Frame f = w.f;
		int y = y0 + 1;
		// The spots along the walls: on the floor, backed by the wall, open in front, clear of the doorways and bridges
		// (the middle of each face) and of a great tower's stair
		List<int[]> spots = new ArrayList<>();
		for (int x = -h + 1; x <= h - 1; x++) {
			for (int z = -h + 1; z <= h - 1; z++) {
				if (!Colossus.inside(form, x, z, h - 1) || Colossus.inside(form, x, z, h - 2)) {
					continue;
				}
				if (great && Math.abs(x) <= 5 && Math.abs(z) <= 5) {
					continue;
				}
				int u = Math.abs(x) >= Math.abs(z) ? z : x;
				if (Math.abs(u) <= 2) {
					continue;
				}
				Direction o = Colossus.out(x, z);
				if (!f.has(x, y0, z) || f.has(x, y, z) || f.has(x, y + 1, z)) {
					continue;
				}
				if (!f.has(x + o.getStepX(), y, z + o.getStepZ()) || f.has(x - o.getStepX(), y, z - o.getStepZ())) {
					continue;
				}
				spots.add(new int[] {x, z, o.ordinal()});
			}
		}
		int stores = theme == STORE ? 3 : 1 + (r.nextInt(3) == 0 ? 1 : 0);
		Set<Long> taken = new HashSet<>();
		for (int[] s : spots) {
			int x = s[0], z = s[1];
			Direction in = Direction.values()[s[2]].getOpposite();
			boolean beside = taken.contains(key(x + 1, z)) || taken.contains(key(x - 1, z)) || taken.contains(key(x, z + 1)) || taken.contains(key(x, z - 1));
			if (r.nextInt(5) >= 3 || beside && r.nextInt(3) > 0) {
				continue;
			}
			int roll = r.nextInt(100);
			// a store only where it stands apart (two chests side by side would join)
			boolean store = stores > 0 && !beside && (theme == STORE ? roll < 60 : roll < 18);
			if (store) {
				boolean placed = store(f, x, y, z, theme == LIBRARY || theme == STORE && r.nextBoolean() ? chest(in) : barrel(), r);
				if (placed) {
					stores--;
					taken.add(key(x, z));
				}
				continue;
			}
			switch (theme) {
				case LIBRARY -> {
					if (roll < 55) {
						put(f, x, y, z, mc("bookshelf"));
						put(f, x, y + 1, z, ruin && r.nextInt(3) == 0 ? Blocks.COBWEB.defaultBlockState() : mc("bookshelf"));
					} else if (roll < 80) {
						put(f, x, y, z, faced(mc("chiseled_bookshelf", mc("bookshelf")), in));
						put(f, x, y + 1, z, candles(r));
					} else {
						put(f, x, y, z, faced(mc("lectern", mc("bookshelf")), in));
					}
				}
				case WEAVERY -> {
					if (roll < 45) {
						put(f, x, y, z, faced(mc("loom", silk()), in));
					} else if (roll < 80) {
						put(f, x, y, z, silk());
						put(f, x, y + 1, z, ruin ? Blocks.COBWEB.defaultBlockState() : candles(r));
					} else {
						put(f, x, y, z, faced(mc("decorated_pot", barrel()), in));
					}
				}
				case STORE -> {
					if (roll < 70) {
						// barrels stacked, nothing in them now
						put(f, x, y, z, barrel());
						if (r.nextBoolean()) {
							put(f, x, y + 1, z, barrel());
						}
					} else {
						put(f, x, y, z, faced(mc("decorated_pot", barrel()), in));
					}
				}
				default -> {
					if (roll < 55) {
						put(f, x, y, z, w.sc.trim(0, r));
						put(f, x, y + 1, z, candles(r));
					} else if (roll < 80) {
						put(f, x, y, z, w.sc.trim(0, r));
						put(f, x, y + 1, z, mc("amethyst_cluster", candles(r)));
					} else {
						put(f, x, y, z, soulLantern());
					}
				}
			}
			taken.add(key(x, z));
		}
		// A rug in the middle, nine across at most (round a great tower's stair)
		int rug = r.nextInt(4), rr = Math.min(h - 3, great ? h - 3 : 4);
		for (int x = -rr; x <= rr; x++) {
			for (int z = -rr; z <= rr; z++) {
				if (!Colossus.inside(form, x, z, rr) || great && Math.abs(x) <= 5 && Math.abs(z) <= 5) {
					continue;
				}
				if (f.has(x, y0, z) && !f.has(x, y, z) && !f.has(x, y + 1, z)) {
					boolean edge = !Colossus.inside(form, x, z, rr - 1);
					f.set(x, y, z, carpet(rug + (edge ? 1 : 0)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ halls

	/** A hall's nave ({@code |x| < w}, from z0 to the door at z1): pews either side of a runner, candles, chests by the door. */
	static void hall(Frame f, int z0, int z1, int w) {
		if (!on() || w < 3 || z1 - z0 < 6) {
			return;
		}
		Random r = stream(f, 2 + z0 * 31 + z1);
		BlockState pew = mc("dark_oak_stairs", planks());
		for (int lz = z0 + 3; lz <= z1 - 3; lz += 2) {
			for (int s = -1; s <= 1; s += 2) {
				for (int k = 2; k <= w - 2; k++) {
					int lx = s * k;
					if (f.has(lx, 0, lz) && !f.has(lx, 1, lz) && !f.has(lx, 2, lz)) {
						// seats facing in, away from the door
						f.set(lx, 1, lz, stairs(pew, Direction.SOUTH, false));
					}
				}
			}
		}
		for (int lz = z0 + 1; lz < z1; lz++) {
			for (int lx = -1; lx <= 1; lx++) {
				if (f.has(lx, 0, lz) && !f.has(lx, 1, lz)) {
					f.set(lx, 1, lz, carpet(lx == 0 ? 0 : 1));
				}
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			int lx = s * (w - 1);
			Direction in = s > 0 ? Direction.WEST : Direction.EAST;
			for (int lz = z0 + 2; lz < z1 - 2; lz += 3) {
				if (f.has(lx, 0, lz) && !f.has(lx, 1, lz)) {
					f.set(lx, 1, lz, candles(r));
				}
			}
			if (f.has(lx, 0, z1 - 1)) {
				store(f, lx, 1, z1 - 1, chest(in), r);
			}
			if (f.has(lx, 0, z0 + 1)) {
				put(f, lx, 1, z0 + 1, faced(mc("lectern", mc("bookshelf")), in));
			}
		}
	}

	// ------------------------------------------------------------------ houses

	/**
	 * A house of the Thinned (half-width w, half-depth d, its door in the middle of the +z wall, a jetty floor at h1 over
	 * half-depth dz): a ladder up through the floor, and a room furnished below and above. The barrel of lore, where the
	 * house has it, stands by the door on the left ({@code lore}).
	 */
	static void house(Frame f, int w, int d, int h1, int dz, boolean lore) {
		if (!on()) {
			return;
		}
		Random r = stream(f, 3);
		// The ladder: in the back corner of a wide house, against the side wall of a narrow one (clear of the windows)
		int lx = w >= 3 ? w - 1 : 1, lz = w >= 3 ? -d + 1 : 0;
		Direction face = w >= 3 ? Direction.SOUTH : Direction.WEST;
		BlockState ladder = faced(mc("ladder", planks()), face);
		for (int ly = 0; ly <= h1; ly++) {
			f.set(lx, ly, lz, ladder);
		}
		// Downstairs: a store, a loom, a table, candles, and a rug down the middle (the way from the door kept clear)
		store(f, -w + 1, 0, 0, barrel(), r);
		if (lore) {
			put(f, w - 1, 0, d - 1, faced(mc("loom", silk()), Direction.WEST));
		} else {
			put(f, -w + 1, 0, d - 1, faced(mc("loom", silk()), Direction.EAST));
			put(f, w - 1, 0, d - 1, mc(r.nextBoolean() ? "crafting_table" : "cartography_table", planks()));
		}
		put(f, w - 1, 0, -d + 1, candles(r));
		int rug = r.nextInt(4);
		for (int x = -w + 2; x <= w - 2; x++) {
			for (int z = -1; z <= 1; z++) {
				if (!f.has(x, 0, z)) {
					f.set(x, 0, z, carpet(rug));
				}
			}
		}
		// Upstairs: a chest, shelves, a bale of silk with a candle on it, and a cobweb up under the roof
		int up = h1 + 1;
		store(f, -w + 1, up, -dz + 1, chest(Direction.EAST), r);
		put(f, -w + 1, up, 0, mc("bookshelf"));
		put(f, -w + 1, up + 1, 0, mc("bookshelf"));
		put(f, -w + 1, up, dz - 2, silk());
		put(f, -w + 1, up + 1, dz - 2, candles(r));
		if (r.nextBoolean()) {
			put(f, 0, up, -dz + 1, faced(mc("decorated_pot", barrel()), Direction.SOUTH));
		}
	}

	// ------------------------------------------------------------------ the loom, the stones

	/**
	 * Barrels of silk round a great loom's floor (its own barrel stands at (sx - 3, 0, -3), the Thinned's loom across from
	 * it), with bales beside them.
	 */
	static void loom(Frame f, int sx) {
		if (!on()) {
			return;
		}
		Random r = stream(f, 4);
		int[][] at = {{-(sx - 5), -3}, {sx - 3, 6}, {-(sx - 3), 6}};
		for (int[] a : at) {
			if (!f.has(a[0], -1, a[1]) || !store(f, a[0], 0, a[1], barrel(), r)) {
				continue;
			}
			int side = a[0] > 0 ? -1 : 1;
			if (f.has(a[0] + side, -1, a[1])) {
				put(f, a[0] + side, 0, a[1], silk());
				put(f, a[0] + side, 1, a[1], candles(r));
			}
		}
	}

	/** Now and then, a cache beside the soul fire of a ring of standing stones. */
	static void cache(Plan p, int cx, int y, int cz) {
		if (!on()) {
			return;
		}
		Random r = new Random(cx * 341873128712L ^ cz * 132897987541L ^ y * 31L ^ 5);
		if (r.nextInt(2) != 0 || p.has(cx + 2, y, cz) || !p.has(cx + 2, y - 1, cz)) {
			return;
		}
		p.set(cx + 2, y, cz, barrel());
		if (r.nextDouble() < Config.SEAM_STOREROOM_CHANCE) {
			p.thenMinor(cx + 2, y, cz, stock(new net.minecraft.core.BlockPos(cx + 2, y, cz), 0));
		}
	}
}
