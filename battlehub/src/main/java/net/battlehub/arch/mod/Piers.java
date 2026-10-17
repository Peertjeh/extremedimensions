package net.battlehub.arch.mod;

import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;

/**
 * What stands between bays: pilasters, stepped buttresses (with a pinnacle above the eaves in gothic), engaged
 * columns, or nothing. Drawn in the frame of the facade: x across the pier, y from the ground, z outwards from
 * the wall face (z = 1 is the first block in front of the wall).
 */
public final class Piers {
	private Piers() {}

	static void register(Library l) {
		l.register(new Pilaster());
		l.register(new Buttress());
		l.register(new Column());
		l.register(new None());
	}

	/** A flat pilaster with a base and a capital. */
	static final class Pilaster extends Base {
		Pilaster() {
			super("pier.pilaster", Category.PIER, Set.of("town", "classical", "palace", "castle", "arcane"), size(1, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int h = s.h();
			int base = s.i("plinth", 1);
			for (int x = 0; x < s.w(); x++) {
				for (int y = base; y < h - 1; y++) c.set(x, y, 1, Cell.of(Slot.QUOIN, Cell.DETAIL));
				c.set(x, h - 1, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			}
		}
	}

	/**
	 * A buttress that steps back as it rises (sloped tops on every set-back); in gothic styles it ends in a
	 * pinnacle above the eaves. Options: depth (2-3), pinnacle, top (where it ends; default the wall height).
	 */
	static final class Buttress extends Base {
		Buttress() {
			super("pier.buttress", Category.PIER, Set.of("gothic", "castle", "sacred", "military"), size(1, 5, 2), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int h = s.i("top", s.h());
			int depth = clamp(s.d(), 1, 4);
			int steps = depth;
			int stepH = Math.max(2, h / (steps + 1));
			for (int x = 0; x < s.w(); x++) {
				for (int k = 0; k < depth; k++) {
					// Projection k+1 reaches up to this height
					int reach = h - (k + 1) * stepH + (k == 0 ? stepH / 2 : 0);
					if (k == 0) reach = h - 1;
					for (int y = 0; y < reach; y++) c.set(x, y, k + 1, Cell.of(Slot.WALL, Cell.DETAIL));
					c.set(x, reach, k + 1, Cell.stairs(Slot.WALL, Dir.NORTH, false, Cell.DETAIL));
				}
			}
			if (s.b("pinnacle")) {
				int cx = s.w() / 2;
				for (int y = h; y < h + 2; y++) c.set(cx, y, 1, Cell.of(Slot.QUOIN, Cell.DETAIL));
				c.decor(cx, h + 2, 1, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				c.decor(cx, h + 3, 1, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				c.rich(cx, h + 4, 1, Cell.literal("end_rod", Cell.DETAIL));
			}
		}
	}

	/** An engaged column: a shaft of pillar blocks with a base and a capital standing in front of the wall. */
	static final class Column extends Base {
		Column() {
			super("pier.column", Category.PIER, Set.of("ancient", "classical", "palace"), size(1, 4, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int h = s.h();
			int x = s.w() / 2;
			c.set(x, 0, 1, Cell.of(Slot.TRIM, Cell.DETAIL));
			for (int y = 1; y < h - 1; y++) c.set(x, y, 1, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
			c.set(x, h - 1, 1, Cell.of(Slot.TRIM, Cell.DETAIL));
			c.decor(x, h - 1, 2, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			c.decor(x, 0, 2, Cell.stairs(Slot.TRIM, Dir.NORTH, false, Cell.DETAIL));
		}
	}

	/** No pier: the bays simply follow each other. */
	static final class None extends Base {
		None() {
			super("pier.none", Category.PIER, Set.of("town", "rustic"), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
		}
	}
}
