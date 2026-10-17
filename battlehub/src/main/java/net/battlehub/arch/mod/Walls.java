package net.battlehub.arch.mod;

import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Slot;

/**
 * Wall modules. A wall run is drawn in the frame of its outer face: x along the run, y up, z = 0 the outer face
 * and z = -(d-1) the inner face. The body carries a plinth, string courses, a cornice and long-and-short quoins;
 * the timber module frames plaster panels; the parapet module draws battlements, with machicolations if asked.
 */
public final class Walls {
	private Walls() {}

	static void register(Library l) {
		l.register(new Body());
		l.register(new Timber());
		l.register(new Parapet());
		l.register(new Arcade());
	}

	/** The masonry of a run. Options: plinth (rows), bands (int[] rows), cornice, quoins, inside (finish the inner face). */
	static final class Body extends Base {
		Body() {
			super("wall.body", Category.WALL, Set.of(), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					for (int z = -(d - 1); z <= 0; z++) {
						c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
					}
				}
			}
			int plinth = s.i("plinth", 0);
			for (int y = 0; y < plinth; y++) {
				for (int x = 0; x < w; x++) {
					c.set(x, y, 0, Cell.of(Slot.FOUNDATION, Cell.DETAIL));
					if (y < plinth - 1) {
						c.set(x, y, 1, Cell.of(Slot.FOUNDATION, Cell.DETAIL));
					} else {
						// A sloping water table on top of the plinth
						c.stairs(x, y, 1, Slot.FOUNDATION, Dir.NORTH, false, Cell.DETAIL);
					}
				}
			}
			int[] bands = s.get("bands", new int[0]);
			for (int by : bands) {
				if (by <= plinth || by >= h) continue;
				for (int x = 0; x < w; x++) {
					c.set(x, by, 0, Cell.of(Slot.BAND, Cell.DETAIL));
					c.rich(x, by, 1, Cell.slab(Slot.BAND, true, Cell.DETAIL));
				}
			}
			if (s.b("cornice")) {
				for (int x = 0; x < w; x++) {
					c.stairs(x, h - 1, 1, Slot.TRIM, Dir.NORTH, true, Cell.DETAIL);
					if (c.detail(2) && h > 3 && x % 2 == 0) c.set(x, h - 2, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL).flag(Cell.DECOR));
				}
			}
			if (s.b("quoins")) {
				for (int y = plinth; y < h; y++) {
					boolean longCourse = ((y - plinth) / 2) % 2 == 0;
					for (int side = 0; side < 2; side++) {
						int x = side == 0 ? 0 : w - 1;
						int inward = side == 0 ? 1 : -1;
						c.set(x, y, 0, Cell.of(Slot.QUOIN, Cell.DETAIL));
						if (longCourse && w > 4) c.set(x + inward, y, 0, Cell.of(Slot.QUOIN, Cell.DETAIL));
					}
				}
			}
		}
	}

	/** Half-timbering: posts, a sill and a head beam, plaster infill and braces in some panels (town). */
	static final class Timber extends Base {
		Timber() {
			super("wall.timber", Category.WALL, Set.of("town", "rustic", "timber"), size(3, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int span = s.i("span", 3);
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					boolean post = x == 0 || x == w - 1 || x % span == 0;
					boolean beam = y == 0 || y == h - 1;
					Cell cell = beam ? Cell.log(Slot.TIMBER, 'x', Cell.DETAIL) : post ? Cell.log(Slot.TIMBER, 'y', Cell.DETAIL) : Cell.of(Slot.WALL_ALT, Cell.BODY);
					c.set(x, y, 0, cell);
					for (int z = -(d - 1); z < 0; z++) c.set(x, y, z, Cell.of(Slot.WALL_ALT, Cell.BODY));
				}
			}
			// Braces: a diagonal of timber in a few panels, mirrored on both ends of the run
			if (c.detail(1) && h >= 4) {
				int panels = Math.max(1, (w - 1) / span);
				for (int p = 0; p < panels; p++) {
					if (!(p == 0 || p == panels - 1) && c.decor.next() > 0.3) continue;
					int x0 = p * span + 1;
					int x1 = Math.min(w - 2, x0 + span - 2);
					boolean rising = p < panels / 2.0;
					for (int i = 0; i <= x1 - x0; i++) {
						int y = 1 + (int) Math.round((double) i * (h - 3) / Math.max(1, x1 - x0));
						int x = rising ? x0 + i : x1 - i;
						c.set(x, y, 0, Cell.log(Slot.TIMBER, 'x', Cell.DETAIL).flag(Cell.DECOR));
					}
				}
			}
		}
	}

	/**
	 * A parapet on top of a wall (y = 0 is the walkway level): a low wall with merlons. Options: machicolations
	 * (the parapet stands one block out on corbels), merlon (merlon width), gap.
	 */
	static final class Parapet extends Base {
		Parapet() {
			super("wall.parapet", Category.WALL, Set.of(), size(2, 2, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w();
			boolean mach = s.b("machicolations");
			int z = mach ? 1 : 0;
			int merlon = s.i("merlon", 2);
			int gap = s.i("gap", 1);
			int period = merlon + gap;
			for (int x = 0; x < w; x++) {
				if (mach) {
					c.stairs(x, -1, 1, Slot.TRIM, Dir.NORTH, true, Cell.DETAIL);
					if (x % 3 == 1) c.air(x, -1, 1);
				}
				c.set(x, 0, z, Cell.of(Slot.WALL, Cell.DETAIL));
				boolean isMerlon = (x % period) < merlon || x == 0 || x == w - 1;
				if (isMerlon) {
					c.set(x, 1, z, Cell.of(Slot.WALL, Cell.DETAIL));
					c.rich(x, 2, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
				}
			}
		}
	}

	/** An open arcade: piers with arches between them through the whole depth (market halls, cloisters). */
	static final class Arcade extends Base {
		Arcade() {
			super("wall.arcade", Category.WALL, Set.of(), size(5, 4, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int span = s.i("span", 3);
			var curve = s.get("arch", c.style().arch);
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					for (int z = -(d - 1); z <= 0; z++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
				}
			}
			int x = 1;
			while (x + span <= w - 1) {
				int jamb = Math.max(2, h - 1 - curve.rise(span) - 1);
				int[] hs = c.archOpening(x, 0, span, jamb, curve, -(d - 1), 0, Cell.air(Cell.OPEN));
				c.archRing(x, 0, hs, 1, Slot.ARCH, 0, 0, Cell.DETAIL);
				x += span + 1;
			}
			// Piers as columns with a base and a capital on the face
			for (int px = 0; px < w; px += span + 1) {
				for (int y = 0; y < h - 1; y++) c.set(px, y, 0, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
				c.decor(px, 0, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, false, Cell.DETAIL));
			}
		}
	}
}
