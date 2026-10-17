package net.battlehub.arch.mod;

import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;

/**
 * Furnishing for rooms. A room is drawn in its own frame: x across (0..w-1), y = 0 the first block above the
 * floor, z = 0 the side with the way in and z = d-1 the far end where the focus is (altar, throne, enchanting
 * table). The walls are just outside the box. Every interior keeps a clear way from the door to its focus, and
 * only puts furniture against solid wall (never in front of windows or doors).
 */
public final class Interiors {
	private Interiors() {}

	static void register(Library l) {
		l.register(new Nave());
		l.register(new Hall());
		l.register(new LibraryRoom());
		l.register(new House());
		l.register(new ArcaneRoom());
		l.register(new Shrine());
	}

	static final int P = Cell.DETAIL;

	static Cell in(Cell c) {
		return c.flag(Cell.INSIDE);
	}

	/** True when the wall block next to the room at this spot is solid masonry (not glass, not an opening). */
	static boolean solidWall(Ctx c, int x, int y, int z) {
		Cell cell = c.get(x, y, z);
		return cell != null && !cell.air() && cell.slot() != Slot.GLASS && cell.slot() != Slot.STAINED && cell.shape() != Shape.DOOR
				&& cell.shape() != Shape.PANE;
	}

	/** A chandelier hanging from the ceiling above y = top (found upwards), {@code drop} links down from y = top. */
	static void chandelier(Ctx c, int x, int top, int z, int drop) {
		int ceiling = top + 1;
		while (ceiling < top + 12 && !c.filled(x, ceiling, z)) ceiling++;
		if (ceiling >= top + 12) return;
		for (int y = ceiling - 1; y > top - drop; y--) c.decor(x, y, z, in(Cell.literal("iron_chain", P)));
		int y = top - drop;
		c.decor(x, y, z, in(Cell.literal(c.style().has("arcane") ? "soul_lantern[hanging=true]" : "lantern[hanging=true]", P)));
	}

	/** Pews facing the altar, a carpet down the aisle, the altar on a stepped platform, candles and chandeliers. */
	static final class Nave extends Base {
		Nave() {
			super("int.nave", Category.INTERIOR, Set.of("gothic", "sacred"), size(7, 5, 12), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int mid = w / 2;
			int aisle = 1;
			for (int z = 3; z < d - 7; z += 2) {
				for (int x = 1; x < w - 1; x++) {
					if (Math.abs(x - mid) <= aisle) continue;
					c.decor(x, 0, z, in(Cell.stairs(Slot.WOOD, Dir.NORTH, false, P)));
				}
			}
			for (int z = 0; z < d - 4; z++) {
				for (int x = mid - aisle + 1; x <= mid + aisle - 1; x++) c.decor(x, 0, z, in(Cell.literal("red_carpet", P)));
			}
			// Altar platform: two steps up, the altar and candles
			int pz = d - 4;
			for (int x = 1; x < w - 1; x++) {
				c.set(x, 0, pz, in(Cell.stairs(Slot.FLOOR_ALT, Dir.SOUTH, false, P)));
				for (int z = pz + 1; z < d; z++) c.set(x, 0, z, in(Cell.of(Slot.FLOOR_ALT, P)));
			}
			for (int x = mid - 1; x <= mid + 1; x++) {
				c.set(x, 1, d - 2, in(Cell.of(Slot.TRIM, Cell.FEATURE)));
				c.decor(x, 2, d - 2, in(Cell.literal(x == mid ? "candle[candles=4,lit=true]" : "candle[candles=2,lit=true]", P)));
			}
			c.decor(mid, 1, d - 1, in(Cell.of(Slot.ACCENT, P)));
			for (int z = 4; z < d - 4; z += 6) chandelier(c, mid, h - 1, z, Math.max(1, h / 3));
		}
	}

	/** A great hall: a long table with chairs, a throne on a dais at the end, banners on the walls. */
	static final class Hall extends Base {
		Hall() {
			super("int.hall", Category.INTERIOR, Set.of("castle", "palace", "military"), size(7, 4, 9), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int mid = w / 2;
			for (int z = 2; z < d - 4; z++) {
				c.decor(mid, 0, z, in(Cell.slab(Slot.WOOD, true, P)));
				c.decor(mid - 1, 0, z, in(Cell.stairs(Slot.WOOD, Dir.WEST, false, P)));
				c.decor(mid + 1, 0, z, in(Cell.stairs(Slot.WOOD, Dir.EAST, false, P)));
				if (z % 3 == 0) c.decor(mid, 1, z, in(Cell.literal("candle[candles=3,lit=true]", P)));
			}
			for (int x = mid - 1; x <= mid + 1; x++) {
				c.set(x, 0, d - 2, in(Cell.of(Slot.FLOOR_ALT, P)));
				c.set(x, 0, d - 1, in(Cell.of(Slot.FLOOR_ALT, P)));
				c.decor(x, 0, d - 3, in(Cell.literal("red_carpet", P)));
			}
			c.decor(mid, 1, d - 1, in(Cell.stairs(Slot.ACCENT, Dir.SOUTH, false, P)));
			c.decor(mid, 2, d - 1, in(Cell.literal("red_wall_banner[facing=north]", P)));
			for (int z = 2; z < d - 2; z += 3) {
				if (solidWall(c, -1, 2, z)) c.decor(0, 2, z, in(Cell.literal("red_wall_banner[facing=east]", P)));
				if (solidWall(c, w, 2, z)) c.decor(w - 1, 2, z, in(Cell.literal("red_wall_banner[facing=west]", P)));
			}
			for (int z = 3; z < d - 3; z += 5) chandelier(c, mid, h - 1, z, 1);
		}
	}

	/** Bookshelves along every solid stretch of wall, reading desks and a chandelier. */
	static final class LibraryRoom extends Base {
		LibraryRoom() {
			super("int.library", Category.INTERIOR, Set.of(), size(4, 3, 4), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int shelf = Math.min(3, h - 1);
			for (int z = 1; z < d; z++) {
				for (int y = 0; y < shelf; y++) {
					if (solidWall(c, -1, y, z) && solidWall(c, -1, 1, z)) c.decor(0, y, z, in(Cell.literal("bookshelf", P)));
					if (solidWall(c, w, y, z) && solidWall(c, w, 1, z)) c.decor(w - 1, y, z, in(Cell.literal("bookshelf", P)));
				}
			}
			for (int x = 1; x < w - 1; x++) {
				for (int y = 0; y < shelf; y++) {
					if (solidWall(c, x, y, d) && solidWall(c, x, 1, d)) c.decor(x, y, d - 1, in(Cell.literal("bookshelf", P)));
				}
			}
			if (w >= 5 && d >= 5) {
				c.decor(w / 2, 0, d / 2, in(Cell.literal("lectern[facing=south]", P)));
				c.decor(w / 2, 0, d / 2 + 1, in(Cell.literal("candle[candles=2,lit=true]", P)));
			}
			chandelier(c, w / 2, h - 1, d / 2, 1);
		}
	}

	/** A home: a bed, a table with a chair, a chest, a barrel, a furnace and a plant. */
	static final class House extends Base {
		House() {
			super("int.house", Category.INTERIOR, Set.of(), size(3, 3, 3), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), d = s.d();
			String[] beds = {"red", "blue", "green", "yellow", "light_blue", "white", "brown"};
			String bed = c.decor.pick(beds) + "_bed";
			if (solidWall(c, w, 1, d - 1)) {
				c.decor(w - 1, 0, d - 1, in(Cell.literal(bed + "[facing=south,part=head]", P)));
				if (d >= 3) c.decor(w - 1, 0, d - 2, in(Cell.literal(bed + "[facing=south,part=foot]", P)));
			}
			c.decor(0, 0, d - 1, in(Cell.literal("chest[facing=east]", P)));
			if (d >= 3) c.decor(0, 0, d - 2, in(Cell.literal("barrel[facing=up]", P)));
			if (w >= 4 && d >= 4) {
				c.decor(w / 2, 0, d / 2, in(Cell.slab(Slot.WOOD, true, P)));
				c.decor(w / 2, 0, d / 2 - 1, in(Cell.stairs(Slot.WOOD, Dir.NORTH, false, P)));
				c.decor(w / 2, 1, d / 2, in(Cell.literal("potted_red_tulip", P)));
			}
			c.decor(w - 1, 0, 0, in(Cell.literal("furnace[facing=west]", P)));
		}
	}

	/** A study of magic: an enchanting table ringed by bookshelves, a brewing stand, crystals and candles. */
	static final class ArcaneRoom extends Base {
		ArcaneRoom() {
			super("int.arcane", Category.INTERIOR, Set.of("arcane", "magic"), size(5, 3, 5), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int cx = w / 2, cz = d / 2;
			c.decor(cx, 0, cz, in(Cell.literal("enchanting_table", Cell.FEATURE)));
			if (w >= 7 && d >= 7) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || dz == -2 && Math.abs(dx) <= 1) continue;
						c.decor(cx + dx, 0, cz + dz, in(Cell.literal("bookshelf", P)));
					}
				}
			}
			c.decor(0, 0, d - 1, in(Cell.literal("brewing_stand", P)));
			c.decor(w - 1, 0, d - 1, in(Cell.literal("cauldron", P)));
			c.decor(0, 0, 0, in(Cell.literal("amethyst_cluster[facing=up]", P)));
			c.decor(w - 1, 0, 0, in(Cell.literal("candle[candles=3,lit=true]", P)));
			chandelier(c, cx, h - 1, cz, 1);
		}
	}

	/** An ancient shrine: an altar with braziers on either side. */
	static final class Shrine extends Base {
		Shrine() {
			super("int.shrine", Category.INTERIOR, Set.of("ancient", "classical", "sacred"), size(3, 3, 3), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), d = s.d();
			int mid = w / 2;
			c.set(mid, 0, d - 1, in(Cell.of(Slot.TRIM, Cell.FEATURE)));
			c.decor(mid, 1, d - 1, in(Cell.of(Slot.ACCENT, P)));
			if (w >= 5) {
				c.decor(mid - 2, 0, d - 1, in(Cell.of(Slot.TRIM, P)));
				c.decor(mid - 2, 1, d - 1, in(Cell.literal("campfire[lit=true,signal_fire=false]", P)));
				c.decor(mid + 2, 0, d - 1, in(Cell.of(Slot.TRIM, P)));
				c.decor(mid + 2, 1, d - 1, in(Cell.literal("campfire[lit=true,signal_fire=false]", P)));
			}
		}
	}
}
