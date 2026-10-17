package net.battlehub.arch.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Noise3;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.mat.Connect;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Library;

/**
 * What surrounds an arena, out of play. Round a walled arena on the ground: stands, as round a real arena —
 * rows of seats rising outwards, a promenade with lamps and braziers, an outer wall with windows and banners, a
 * gatehouse with towers behind each spawn side, and landmarks of the theme on rocks beyond (where the spectators
 * look). Round an arena over the void, and in the open themes: small islands at different heights with trees
 * and lamps, and landmarks further out. Nothing is built over a column the arena itself uses.
 */
public final class Surroundings {
	private Surroundings() {}

	public static final class Settings {
		public long seed = 1;
		public String theme = "castle";
		/** The ring the old scenery used: from here out. */
		public int rIn = 24, rOut = 32;
		/** The height of the arena's floor (stands stand on it). */
		public int groundY = 0;
		/** Columns the arena uses; nothing is drawn over them. */
		public BiPredicate<Integer, Integer> reserved = (x, z) -> false;
		/** The furthest a reserved column lies from the middle. */
		public int reach = 0;
		/** Banner colours, the first for the west side, the second for the east. */
		public String[] colours = {"red", "blue"};
		public int detail = 1;
		public boolean landmarks = true;
	}

	/** Rows of seats in the stands, and the podium wall they start on (so they show over the arena's walls). */
	static final int TIERS = 4;
	static final int BASE = 3;

	public static ArenaPlan generate(Settings s, Library lib) {
		ArenaPlan out = new ArenaPlan();
		Dress d = Dress.of(s.theme, s.seed);
		out.dress = d;
		Rng root = new Rng(s.seed);
		if (!d.open && s.groundY >= -1) {
			stands(out, s, d, lib, root);
		} else {
			islets(out, s, d, lib, root);
		}
		Connect.apply(out.voxels);
		out.log.add("surroundings " + d + " " + (!d.open && s.groundY >= -1 ? "stands" : "islands") + ", " + out.voxels.size() + " blocks");
		return out;
	}

	/** Where the stands begin (past the arena and its walls) and end (the outer face of the outer wall). */
	public static int start(Settings s) {
		return Math.max(s.rIn, s.reach + 3);
	}

	public static int end(Settings s) {
		return start(s) + TIERS + 3;
	}

	/** The top of the stands' outer wall above the arena floor. */
	public static int height() {
		return BASE + TIERS + 9;
	}

	static void stands(ArenaPlan out, Settings s, Dress d, Library lib, Rng root) {
		int r0 = start(s), rw = r0 + TIERS + 2, rEnd = rw + 1;
		long seed = root.stream("stands").seed();
		Building b = BedWars.container("stands", d, lib, Xform.at(0, s.groundY, 0), seed, s.detail);
		Ctx c = new Ctx(b, Xform.ID, root.stream("geo"), root.stream("decor"));
		Noise3 under = new Noise3(seed ^ 0xDEE9L);
		int inner = Math.max(0, s.reach - 1);
		for (int x = -rEnd - 1; x <= rEnd + 1; x++) {
			for (int z = -rEnd - 1; z <= rEnd + 1; z++) {
				double dd = Math.hypot(x, z);
				if (dd < inner || dd >= rEnd || s.reserved.test(x, z)) continue;
				int k = (int) Math.floor(dd - r0);
				Dir outward = Dir.of((int) Math.round(x * 10 / Math.max(1, dd)), (int) Math.round(z * 10 / Math.max(1, dd)));
				// the ground ring and the rock under it
				int depth = 2 + (int) Math.round((rEnd - dd) * 0.25 + under.at(x, 0, z, 6, 2) * 3);
				c.set(x, 0, z, Cell.of(k < 0 ? Slot.PATH : Slot.PAVING, Cell.TERRAIN).flag(Cell.FLOAT));
				for (int y = 1; y <= depth; y++) c.set(x, -y, z, Cell.of(y <= 1 ? Slot.SOIL : Slot.ROCK, Cell.TERRAIN).flag(Cell.FLOAT));
				if (k < 0) continue;
				int top = BASE + TIERS;
				if (dd >= rw - 0.5) {
					// the outer wall, thick enough to close on the diagonals
					for (int y = 1; y <= top + 4; y++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
					double a = Math.atan2(z, x);
					int arc = (int) Math.floor((a + Math.PI) * rw);
					if (Math.floorMod(arc, 7) < 2 && dd < rw + 0.5) {
						for (int y = top + 2; y <= top + 3; y++) c.set(x, y, z, Cell.air(Cell.OPEN));
					}
					c.set(x, top + 5, z, Math.floorMod(arc, 2) == 0 ? Cell.of(Slot.TRIM, Cell.BODY) : Cell.slab(Slot.TRIM, false, Cell.BODY));
				} else if (k < TIERS) {
					for (int y = 1; y <= BASE + k; y++) c.set(x, y, z, Cell.of(k == 0 ? Slot.WALL : Slot.FOUNDATION, Cell.BODY));
					c.set(x, BASE + k + 1, z, Cell.stairs(Slot.WOOD, outward, false, Cell.BODY));
				} else {
					for (int y = 1; y < top; y++) c.set(x, y, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
					c.set(x, top, z, Cell.of(Slot.FLOOR, Cell.BODY));
				}
			}
		}
		// gatehouses behind the spawn sides (west and east): two towers and an arch through the outer wall
		for (int side = -1; side <= 1; side += 2) {
			String colour = s.colours[side < 0 ? 0 : 1 % s.colours.length];
			gatehouse(c, d, side, rw, colour);
		}
		// banners on the inner face of the wall, lamps and braziers on the promenade
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8 + Math.PI / 16;
			if (Math.abs(Math.cos(a)) > 0.93) continue;
			int x = (int) Math.round(Math.cos(a) * (rw - 1)), z = (int) Math.round(Math.sin(a) * (rw - 1));
			Dir in = Dir.of((int) Math.round(-Math.cos(a) * 10), (int) Math.round(-Math.sin(a) * 10));
			int by = BASE + TIERS + 3;
			Cell behind = c.get(x - in.dx, by, z - in.dz);
			if (behind != null && behind.slot() == Slot.WALL && c.get(x, by, z) == null) {
				String colour = s.colours[Math.cos(a) < 0 ? 0 : 1 % s.colours.length];
				c.set(x, by, z, Kit.team(colour, "wall_banner[facing=" + in.id() + "]", Cell.DETAIL));
			}
		}
		String fire = d.lamp.equals("soul_lantern") ? "soul_campfire[lit=true]" : "campfire[lit=true]";
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			if (Math.abs(Math.cos(a)) > 0.95) continue;
			int x = (int) Math.round(Math.cos(a) * (r0 + TIERS)), z = (int) Math.round(Math.sin(a) * (r0 + TIERS));
			int top = BASE + TIERS;
			if (s.reserved.test(x, z) || c.get(x, top, z) == null) continue;
			if (i % 3 == 0) {
				c.set(x, top + 1, z, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.set(x, top + 2, z, Cell.literal(fire, Cell.DETAIL));
			} else {
				Kit.post(c, d, x, top + 1, z, 2);
			}
		}
		BedWars.earth(b, seed);
		b.emit(out.voxels);
		out.buildings.add(b);
		if (s.landmarks) {
			// landmarks where the spectators look (they float over the north stands, looking south)
			List<Backdrop.Taken> taken = new ArrayList<>();
			Rng rng = root.stream("landmarks");
			double[] at = {Math.PI / 2 - 0.85, Math.PI / 2 + 0.05, Math.PI / 2 + 0.9};
			for (int i = 0; i < at.length; i++) {
				String arch = d.landmarks.get(i % d.landmarks.size());
				Backdrop.landmark(out, d, lib, arch, rng.stream("landmark", i).seed(), at[i] + rng.range(-0.15, 0.15), rEnd + 6, false,
						s.groundY + rng.range(-10, -2), s.detail, taken);
			}
			for (int k = 0; k < 6; k++) {
				double a = k * Math.PI / 3 + rng.range(-0.3, 0.3);
				double r = rng.range(2.5, 4.5);
				double dist = rEnd + 6 + r + rng.range(0, 10);
				int x = (int) Math.round(Math.cos(a) * dist), z = (int) Math.round(Math.sin(a) * dist);
				if (Backdrop.clash(taken, x, z, r + 3)) continue;
				taken.add(new Backdrop.Taken(x, z, r));
				Backdrop.islet(out, d, lib, rng.stream("islet", k), x, s.groundY + rng.range(-14, 6), z, r, s.detail);
			}
		}
	}

	/** Two towers flanking an arch through the outer wall on the west (side -1) or east (side 1). */
	static void gatehouse(Ctx c, Dress d, int side, int rw, String colour) {
		int floor = BASE + TIERS;
		int top = floor + 8;
		for (int tz = -3; tz <= 3; tz += 6) {
			int cx = side * rw;
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int z = tz - 1; z <= tz + 1; z++) {
					boolean corner = Math.abs(x - cx) == 1 && Math.abs(z - tz) == 1;
					for (int y = 1; y <= top; y++) c.force(x, y, z, Cell.of(corner ? Slot.QUOIN : Slot.WALL, Cell.FEATURE));
					c.force(x, top + 1, z, corner ? Cell.of(Slot.TRIM, Cell.FEATURE) : Cell.slab(Slot.TRIM, false, Cell.FEATURE));
				}
			}
			c.set(cx, top + 2, tz, Cell.literal(d.lamp, Cell.DETAIL));
			c.set(cx - side * 2, top - 2, tz, Kit.team(colour, "wall_banner[facing=" + (side < 0 ? "east" : "west") + "]", Cell.DETAIL));
			// a window in each tower towards the arena
			c.force(cx - side, floor + 5, tz, Cell.air(Cell.FORCE));
		}
		// the arch between the towers, through the wall, looking out into the sky
		for (int x = side * rw - 1; x != side * rw + 2 * side; x += side) {
			for (int z = -2; z <= 2; z++) {
				for (int y = floor + 1; y <= floor + 4; y++) c.force(x, y, z, Cell.air(Cell.FORCE));
				c.force(x, floor + 5, z, Cell.of(Math.abs(z) == 2 ? Slot.ARCH : Slot.WALL, Cell.FEATURE));
				if (Math.abs(z) == 2) c.force(x, floor + 4, z, Cell.stairs(Slot.ARCH, z < 0 ? Dir.NORTH : Dir.SOUTH, true, Cell.FEATURE));
			}
		}
	}

	/** Small islands round an arena over the void, and landmarks further out. */
	static void islets(ArenaPlan out, Settings s, Dress d, Library lib, Rng root) {
		List<Backdrop.Taken> taken = new ArrayList<>();
		Rng rng = root.stream("islets");
		double mid = (s.rIn + s.rOut) / 2.0;
		int count = Math.max(6, (int) (mid * Math.PI * 2 / 13));
		for (int k = 0; k < count; k++) {
			double a = k * Math.PI * 2 / count + rng.range(-0.25, 0.25);
			// keep the north clear: the spectators start there
			if (Math.abs(Math.sin(a) + 1) < 0.08) continue;
			double r = rng.range(2.5, 4.6);
			double dist = mid + rng.range(-2, 4);
			int x = (int) Math.round(Math.cos(a) * dist), z = (int) Math.round(Math.sin(a) * dist);
			if (Backdrop.clash(taken, x, z, r + 2) || near(s, x, z, (int) Math.ceil(r) + 2)) continue;
			taken.add(new Backdrop.Taken(x, z, r));
			Backdrop.islet(out, d, lib, rng.stream("islet", k), x, s.groundY + rng.range(-6, 10), z, r, s.detail);
		}
		if (s.landmarks) {
			Rng lr = root.stream("landmarks");
			double[] at = {Math.PI / 2 - 0.7, Math.PI / 2 + 0.7};
			for (int i = 0; i < at.length; i++) {
				String arch = d.landmarks.get(i % d.landmarks.size());
				Backdrop.landmark(out, d, lib, arch, lr.stream("landmark", i).seed(), at[i] + lr.range(-0.2, 0.2), s.rOut + 8, false,
						s.groundY + lr.range(-12, -2), s.detail, taken);
			}
		}
	}

	/** A reserved column within {@code r} of (x, z). */
	static boolean near(Settings s, int x, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (s.reserved.test(x + dx, z + dz)) return true;
			}
		}
		return false;
	}
}
