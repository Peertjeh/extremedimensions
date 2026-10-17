package net.battlehub.arch.mod;

import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.geom.ArchCurve;

/**
 * Window modules, drawn in the frame of one bay of one storey: x across the bay (0..w-1), y from the storey floor
 * (0..h-1), z = 0 the outer face and z = -(d-1) the inner face. Openings are cut with explicit air, glass sits in
 * the wall's depth (deep-set where the wall is thick), frames and sills are trim. Stained glass is composed by
 * position (colour variants of the palette), never at random.
 */
public final class Windows {
	private Windows() {}

	static void register(Library l) {
		l.register(new Rect());
		l.register(new Slit());
		l.register(new Lancet());
		l.register(new Twin());
		l.register(new Rose());
		l.register(new Oculus());
		l.register(new RoundArch());
		l.register(new Arcane());
		l.register(new Blind());
		l.register(new Niche());
		l.register(new Oriel());
	}

	/** The depth the glass sits at: the inner face of thin walls, one block in from the outside on thick ones. */
	static int glassZ(int d) {
		return d <= 1 ? 0 : -Math.min(d - 1, 1);
	}

	/** A stained-glass colour variant (1-based) for a composition position. */
	static Cell stained(int variant) {
		return Cell.shape(Slot.STAINED, Shape.PANE, Cell.OPEN).variant(variant);
	}

	static Cell pane(Slot s) {
		return Cell.shape(s, Shape.PANE, Cell.OPEN);
	}

	/** Plain rectangular window with lintel, sill and (in towns) shutters and window boxes. */
	static final class Rect extends Base {
		Rect() {
			super("window.rect", Category.WINDOW, Set.of("town", "castle", "rustic", "classical"), size(2, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int ow = s.i("ow", clamp(w - 2, 1, 3));
			int oh = s.i("oh", clamp(h - 2, 1, 3));
			int x0 = (w - ow) / 2;
			int gz = glassZ(d);
			for (int x = x0; x < x0 + ow; x++) {
				for (int y = 1; y <= oh; y++) {
					for (int z = -(d - 1); z <= 0; z++) c.air(x, y, z);
					c.set(x, y, gz, pane(Slot.GLASS));
				}
				c.set(x, oh + 1, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.decor(x, 0, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			}
			if (c.style().has("town") && c.detail(1)) {
				for (int y = 1; y <= oh; y++) {
					c.set(x0 - 1, y, 1, Cell.shape(Slot.WOOD, Shape.TRAPDOOR, Cell.DETAIL).facing(Dir.SOUTH).variant(1).flag(Cell.DECOR));
					c.set(x0 + ow, y, 1, Cell.shape(Slot.WOOD, Shape.TRAPDOOR, Cell.DETAIL).facing(Dir.SOUTH).variant(1).flag(Cell.DECOR));
				}
				if (c.detail(2) && c.decor.chance(0.55)) {
					String[] pots = {"potted_red_tulip", "potted_poppy", "potted_azure_bluet", "potted_dandelion", "potted_oxeye_daisy", "potted_blue_orchid"};
					for (int x = x0; x < x0 + ow; x++) c.rich(x, 1, 1, Cell.literal(c.decor.pick(pots), Cell.DETAIL));
				}
			}
		}
	}

	/** An arrow slit: one block wide outside, splayed wider inside; a cross slit at rich detail. */
	static final class Slit extends Base {
		Slit() {
			super("window.slit", Category.WINDOW, Set.of("castle", "military"), size(1, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int oh = clamp(h - 2, 2, 4);
			int x = w / 2;
			int y0 = Math.max(1, (h - oh) / 2);
			for (int y = y0; y < y0 + oh; y++) {
				for (int z = -(d - 1); z <= 0; z++) {
					c.air(x, y, z);
					if (z < 0 && w >= 3) {
						c.air(x - 1, y, z);
						c.air(x + 1, y, z);
					}
				}
			}
			if (c.detail(2) && w >= 3 && oh >= 3) {
				c.set(x - 1, y0 + oh / 2, 0, Cell.AIR.flag(Cell.DECOR));
				c.set(x + 1, y0 + oh / 2, 0, Cell.AIR.flag(Cell.DECOR));
			}
		}
	}

	/** A gothic lancet: tall, narrow, pointed, with a trim frame, a hood moulding and composed stained glass. */
	static final class Lancet extends Base {
		Lancet() {
			super("window.lancet", Category.WINDOW, Set.of("gothic", "arcane", "sacred"), size(3, 5, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int ow = s.i("ow", clamp(w - 2, 1, 3));
			ArchCurve curve = s.get("arch", ArchCurve.LANCET);
			int x0 = (w - ow) / 2;
			int total = h - 2;
			int rise = curve.rise(ow);
			int jamb = Math.max(1, total - rise);
			int gz = glassZ(d);
			int[] hs = c.archOpening(x0, 1, ow, jamb, curve, -(d - 1), 0, Cell.air(Cell.OPEN));
			int scheme = s.i("glass", 0);
			for (int i = 0; i < ow; i++) {
				for (int y = 0; y < hs[i]; y++) {
					// Composition: base colour below, a band, the head of the window in a second colour
					double t = (double) y / Math.max(1, total);
					int v = t > 0.78 ? 3 : (y % 4 == 3 ? 2 : 1);
					if (ow >= 3 && i == ow / 2 && t <= 0.78) v = 5;
					c.set(x0 + i, 1 + y, gz, stained(1 + (v - 1 + scheme) % 9));
				}
			}
			// Frame: jambs, ring, sill
			for (int y = 1; y <= jamb; y++) {
				c.set(x0 - 1, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.set(x0 + ow, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			}
			c.archRing(x0, 1, hs, 1, Slot.TRIM, 0, 0, Cell.DETAIL);
			for (int x = x0 - 1; x <= x0 + ow; x++) {
				c.set(x, 0, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.decor(x, 0, 1, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
			}
			// Hood moulding over the arch
			if (c.detail(2)) {
				for (int i = 0; i < ow; i++) c.rich(x0 + i, 1 + hs[i] + 1, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			}
		}
	}

	/** Two lancets under one pointed arch with a mullion and an oculus in the head (tracery). */
	static final class Twin extends Base {
		Twin() {
			super("window.twin", Category.WINDOW, Set.of("gothic", "sacred"), size(5, 7, 2), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int ow = clamp(w - 2, 5, 9);
			if (ow % 2 == 0) ow--;
			int x0 = (w - ow) / 2;
			ArchCurve curve = ArchCurve.POINTED;
			int total = h - 2;
			int jamb = Math.max(2, total - curve.rise(ow));
			int gz = glassZ(d);
			// The whole head is recessed; tracery stone stands in the glass plane
			int[] outer = c.archOpening(x0, 1, ow, jamb, curve, gz + 1, 0, Cell.air(Cell.OPEN));
			for (int i = 0; i < ow; i++) {
				for (int y = 0; y < outer[i]; y++) c.set(x0 + i, 1 + y, gz, Cell.of(Slot.TRIM, Cell.FEATURE));
			}
			int lw = (ow - 1) / 2;
			int ljamb = jamb - 1;
			int scheme = s.i("glass", 0);
			for (int side = 0; side < 2; side++) {
				int lx = x0 + side * (lw + 1);
				int[] lh = curve.heights(lw);
				for (int i = 0; i < lw; i++) {
					int top = ljamb + lh[i];
					for (int y = 0; y < top; y++) {
						int v = y > ljamb ? 3 : (y % 3 == 2 ? 2 : 1);
						c.force(lx + i, 1 + y, gz, stained(1 + (v - 1 + scheme + side) % 9).prio(Cell.FEATURE + 1));
						for (int z = gz - 1; z >= -(d - 1); z--) c.set(lx + i, 1 + y, z, Cell.air(Cell.FEATURE));
					}
				}
			}
			// Oculus in the head between the lancet tops and the outer arch
			int lancetTop = 1 + ljamb + curve.rise(lw);
			int outerTop = 1 + outer[ow / 2] - 1;
			int cx = x0 + ow / 2;
			int room = outerTop - lancetTop - 1;
			if (room >= 3) {
				int cy = lancetTop + 1 + room / 2;
				int[][] plus = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] p : plus) c.force(cx + p[0], cy + p[1], gz, stained(1 + (4 + scheme) % 9).prio(Cell.FEATURE + 1));
			} else if (room >= 1) {
				c.force(cx, lancetTop + 1 + room / 2, gz, stained(1 + (4 + scheme) % 9).prio(Cell.FEATURE + 1));
			}
			c.archRing(x0, 1, outer, 1, Slot.TRIM, 0, 0, Cell.DETAIL);
			for (int y = 1; y <= jamb; y++) {
				c.set(x0 - 1, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.set(x0 + ow, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			}
			for (int x = x0 - 1; x <= x0 + ow; x++) c.decor(x, 0, 1, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
		}
	}

	/** A rose window: radial tracery (spokes and a ring) with colour rings and alternating petals. */
	static final class Rose extends Base {
		Rose() {
			super("window.rose", Category.WINDOW, Set.of("gothic", "sacred"), size(7, 7, 2), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			double r = Math.min(w, h) / 2.0 - 1;
			double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
			int gz = glassZ(d);
			int spokes = r >= 5 ? 12 : 8;
			int scheme = s.i("glass", 0);
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					double dx = x - cx, dy = y - cy;
					double dist = Math.sqrt(dx * dx + dy * dy);
					if (dist > r + 0.5) {
						if (dist <= r + 1.4) {
							c.set(x, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
						} else if (dist <= r + 2.3 && c.detail(2)) {
							c.rich(x, y, 0, Cell.of(Slot.ARCH, Cell.DETAIL));
						}
						continue;
					}
					for (int z = gz + 1; z <= 0; z++) c.air(x, y, z);
					double a = Math.atan2(dy, dx);
					double sector = (a + Math.PI) / (2 * Math.PI) * spokes;
					double toSpoke = Math.abs(sector - Math.round(sector));
					boolean spoke = dist > 1.2 && toSpoke * (2 * Math.PI / spokes) * dist < 0.45;
					boolean ring = Math.abs(dist - r * 0.55) < 0.45;
					if (spoke || ring) {
						c.set(x, y, gz, Cell.of(Slot.TRIM, Cell.FEATURE));
					} else {
						int petal = (int) Math.floor(sector) % 2;
						int v = dist < 1.2 ? 6 : dist < r * 0.55 ? 1 + petal : 3 + petal;
						c.set(x, y, gz, stained(1 + (v - 1 + scheme) % 9).prio(Cell.FEATURE));
					}
					for (int z = gz - 1; z >= -(d - 1); z--) c.set(x, y, z, Cell.air(Cell.FEATURE));
				}
			}
			c.focal((int) Math.round(cx), (int) Math.round(cy), 0);
		}
	}

	/** A small round window. */
	static final class Oculus extends Base {
		Oculus() {
			super("window.oculus", Category.WINDOW, Set.of(), size(3, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			double r = Math.min(Math.min(w, h) / 2.0 - 0.5, 2.2);
			double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
			int gz = glassZ(d);
			boolean sacred = c.style().has("sacred") || c.style().has("arcane");
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					double dist = Math.hypot(x - cx, y - cy);
					if (dist <= r - 0.3) {
						for (int z = -(d - 1); z <= 0; z++) c.air(x, y, z);
						c.set(x, y, gz, sacred ? stained(dist < 0.8 ? 6 : 1) : pane(Slot.GLASS));
					} else if (dist <= r + 0.7) {
						c.set(x, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
					}
				}
			}
		}
	}

	/** A round-arched window with an arch ring and a keystone (castles, temples, houses). */
	static final class RoundArch extends Base {
		RoundArch() {
			super("window.round", Category.WINDOW, Set.of("castle", "classical", "ancient", "town"), size(3, 4, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int ow = s.i("ow", clamp(w - 2, 1, 3));
			int x0 = (w - ow) / 2;
			int total = h - 2;
			int jamb = Math.max(1, total - ArchCurve.ROUND.rise(ow));
			int gz = glassZ(d);
			int[] hs = c.archOpening(x0, 1, ow, jamb, ArchCurve.ROUND, -(d - 1), 0, Cell.air(Cell.OPEN));
			boolean sacred = c.style().has("sacred");
			for (int i = 0; i < ow; i++) {
				for (int y = 0; y < hs[i]; y++) c.set(x0 + i, 1 + y, gz, sacred ? stained(1 + (y > jamb ? 2 : 0)) : pane(Slot.GLASS));
			}
			c.archRing(x0, 1, hs, 1, Slot.ARCH, 0, 0, Cell.DETAIL);
			if (ow % 2 == 1) c.decor(x0 + ow / 2, 1 + hs[ow / 2], 0, Cell.of(Slot.ACCENT, Cell.DETAIL).variant(2));
			for (int x = x0; x < x0 + ow; x++) c.decor(x, 0, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
		}
	}

	/** An arcane window: a sharp lancet with glowing stones at the keystone and sill, coloured glass. */
	static final class Arcane extends Base {
		Arcane() {
			super("window.arcane", Category.WINDOW, Set.of("arcane", "magic"), size(3, 5, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int ow = clamp(w - 2, 1, 3);
			int x0 = (w - ow) / 2;
			int total = h - 2;
			int jamb = Math.max(1, total - ArchCurve.LANCET.rise(ow));
			int gz = glassZ(d);
			int[] hs = c.archOpening(x0, 1, ow, jamb, ArchCurve.LANCET, -(d - 1), 0, Cell.air(Cell.OPEN));
			for (int i = 0; i < ow; i++) {
				for (int y = 0; y < hs[i]; y++) c.set(x0 + i, 1 + y, gz, stained(1 + ((y / 2 + i) % 3)));
			}
			c.archRing(x0, 1, hs, 1, Slot.TRIM, 0, 0, Cell.DETAIL);
			int mid = x0 + ow / 2;
			c.decor(mid, 1 + hs[ow / 2], 0, Cell.of(Slot.MAGIC, Cell.FEATURE));
			c.rich(mid, 1 + hs[ow / 2] + 1, 1, Cell.literal("amethyst_cluster[facing=up]", Cell.DETAIL));
			c.decor(mid, 0, 0, Cell.of(Slot.MAGIC, Cell.FEATURE));
			for (int x = x0; x < x0 + ow; x++) c.decor(x, 0, 1, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
		}
	}

	/** A blind arch: a recessed arched panel with no opening (rhythm without windows). */
	static final class Blind extends Base {
		Blind() {
			super("window.blind", Category.WINDOW, Set.of("gothic", "ancient", "classical", "sacred"), size(3, 4, 2), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h();
			int ow = clamp(w - 2, 1, 5);
			int x0 = (w - ow) / 2;
			ArchCurve curve = c.style().arch;
			int jamb = Math.max(1, h - 2 - curve.rise(ow));
			int[] hs = c.archOpening(x0, 1, ow, jamb, curve, 0, 0, Cell.air(Cell.OPEN));
			for (int i = 0; i < ow; i++) {
				for (int y = 0; y < hs[i]; y++) c.set(x0 + i, 1 + y, -1, Cell.of(Slot.WALL_ALT, Cell.DETAIL));
			}
			c.archRing(x0, 1, hs, 1, Slot.TRIM, 0, 0, Cell.DETAIL);
		}
	}

	/** A small arched niche with a candle, a pot or a crystal in it. */
	static final class Niche extends Base {
		Niche() {
			super("window.niche", Category.WINDOW, Set.of(), size(3, 4, 2), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h();
			int x = w / 2;
			int y0 = Math.max(1, h / 2 - 1);
			c.air(x, y0, 0);
			c.air(x, y0 + 1, 0);
			c.set(x, y0 - 1, 0, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
			c.set(x, y0 + 2, 0, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			c.set(x - 1, y0 + 1, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			c.set(x + 1, y0 + 1, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			String item = c.style().has("arcane") ? "amethyst_cluster[facing=up]" : c.style().has("sacred") ? "candle[candles=3,lit=true]" : "decorated_pot";
			c.decor(x, y0, 0, Cell.literal(item, Cell.FEATURE));
		}
	}

	/** An oriel: a glazed bay standing out from the wall on corbels, with its own little roof (towns, palaces). */
	static final class Oriel extends Base {
		Oriel() {
			super("window.oriel", Category.WINDOW, Set.of("town", "palace"), size(4, 4, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int x0 = 1, x1 = w - 2;
			int top = h - 1;
			for (int x = x0; x <= x1; x++) {
				for (int y = 1; y < top; y++) {
					for (int z = -(d - 1); z <= 0; z++) c.air(x, y, z);
					boolean edge = x == x0 || x == x1;
					c.set(x, y, 2, edge ? Cell.log(Slot.TIMBER, 'y', Cell.DETAIL) : pane(Slot.GLASS));
					c.set(x, y, 1, edge ? pane(Slot.GLASS) : Cell.air(Cell.OPEN));
				}
				c.set(x, 0, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
				c.set(x, 0, 2, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
				c.set(x, top, 1, Cell.of(Slot.ROOF, Cell.DETAIL));
				c.set(x, top, 2, Cell.stairs(Slot.ROOF, Dir.NORTH, false, Cell.DETAIL));
			}
		}
	}
}
