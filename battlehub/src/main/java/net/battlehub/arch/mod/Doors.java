package net.battlehub.arch.mod;

import java.util.List;
import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Half;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.geom.ArchCurve;

/**
 * Doors, gates and portals, drawn in the frame of a ground-storey bay like windows. Every one leaves at least
 * two blocks of clear height, offers an {@link Attachment.Kind#ENTRANCE} just outside it, and the grand ones
 * (portals) mark a focal point and stand out from the wall as a porch with stepped archivolts.
 */
public final class Doors {
	private Doors() {}

	static void register(Library l) {
		l.register(new Plain());
		l.register(new Double());
		l.register(new Portal());
		l.register(new Gate());
		l.register(new Temple());
		l.register(new ArcaneDoor());
	}

	static Cell door(Dir facing, boolean upper, boolean rightHinge) {
		return new Cell(Slot.WOOD, Shape.DOOR, facing, upper ? Half.TOP : Half.BOTTOM, (char) 0, Cell.FEATURE, 0, rightHinge ? 1 : 0, null);
	}

	/** One door with a frame and a lantern beside it. */
	static final class Plain extends Base {
		Plain() {
			super("door.plain", Category.DOOR, Set.of(), size(3, 3, 1), ANY);
		}

		@Override
		public List<Attachment> attachments(Spec s) {
			return List.of(new Attachment("entrance", Attachment.Kind.ENTRANCE, s.w() / 2, 0, 1, Dir.SOUTH, 1, 2));
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), d = s.d();
			int x = w / 2;
			int dz = -(d - 1);
			for (int z = dz; z <= 0; z++) {
				c.set(x, 0, z, Cell.air(Cell.FEATURE));
				c.set(x, 1, z, Cell.air(Cell.FEATURE));
			}
			c.set(x, 0, dz, door(Dir.NORTH, false, false));
			c.set(x, 1, dz, door(Dir.NORTH, true, false));
			for (int y = 0; y <= 2; y++) {
				c.set(x - 1, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.set(x + 1, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			}
			c.set(x, 2, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			if (c.detail(1) && w >= 3) {
				c.decor(x + 1, 3, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
				c.decor(x + 1, 2, 1, Cell.literal("lantern[hanging=true]", Cell.DETAIL));
			}
		}
	}

	/** Double doors under an arch with a glazed transom. */
	static final class Double extends Base {
		Double() {
			super("door.double", Category.DOOR, Set.of(), size(4, 4, 1), ANY);
		}

		@Override
		public List<Attachment> attachments(Spec s) {
			return List.of(new Attachment("entrance", Attachment.Kind.ENTRANCE, s.w() / 2 - 1, 0, 1, Dir.SOUTH, 2, 2));
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int x0 = w / 2 - 1;
			ArchCurve curve = c.style().has("gothic") ? ArchCurve.POINTED : ArchCurve.ROUND;
			int jamb = Math.min(3, h - 2);
			int[] hs = c.archOpening(x0, 0, 2, jamb, curve, -(d - 1), 0, Cell.air(Cell.FEATURE));
			int dz = -(d - 1);
			c.set(x0, 0, dz, door(Dir.NORTH, false, false));
			c.set(x0, 1, dz, door(Dir.NORTH, true, false));
			c.set(x0 + 1, 0, dz, door(Dir.NORTH, false, true));
			c.set(x0 + 1, 1, dz, door(Dir.NORTH, true, true));
			for (int i = 0; i < 2; i++) {
				for (int y = 2; y < hs[i]; y++) c.set(x0 + i, y, dz, Windows.pane(Slot.GLASS).prio(Cell.FEATURE));
			}
			c.archRing(x0, 0, hs, 1, Slot.ARCH, 0, 0, Cell.DETAIL);
			for (int y = 0; y < jamb; y++) {
				c.set(x0 - 1, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
				c.set(x0 + 2, y, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			}
		}
	}

	/**
	 * A gothic portal: a porch standing out from the wall with stepped pointed archivolts, a carved tympanum,
	 * double doors (split by a trumeau when wide), a gable (wimperg) with a finial and pinnacles at its corners.
	 */
	public static final class Portal extends Base {
		Portal() {
			super("door.portal", Category.DOOR, Set.of("gothic", "sacred"), size(7, 8, 1), ANY);
		}

		private static int rings(int w) {
			return clamp((w - 3) / 2, 1, 3);
		}

		/** How tall the portal stands, gable and finial included, for a width and the height it was given. */
		public static int height(int w, int h) {
			if (w % 2 == 0) w--;
			int jamb = clamp(h / 3, 3, 5);
			int porchTop = jamb + ArchCurve.POINTED.rise(w - 2) + 1;
			return porchTop + 1 + (w - 1) / 2 + 2;
		}

		@Override
		public List<Attachment> attachments(Spec s) {
			int r = rings(s.w());
			int iw = s.w() - 2 - 2 * (r - 1) - 2;
			return List.of(new Attachment("entrance", Attachment.Kind.ENTRANCE, s.w() / 2, 0, r + 1, Dir.SOUTH, Math.max(1, iw), 3));
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			if (w % 2 == 0) w--;
			int r = rings(w);
			int jamb = clamp(h / 3, 3, 5);
			ArchCurve curve = ArchCurve.POINTED;
			int outerW = w - 2;
			int porchTop = jamb + curve.rise(outerW) + 1;
			// The porch mass
			for (int x = 0; x < w; x++) {
				for (int y = 0; y <= porchTop; y++) {
					for (int z = 1; z <= r; z++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.DETAIL));
				}
			}
			// Archivolts: each ring one step narrower and one step deeper
			for (int k = 0; k < r; k++) {
				int ow = outerW - 2 * k;
				int x0 = (w - ow) / 2;
				int z = r - k;
				int[] hs = c.archOpening(x0, 0, ow, jamb, curve, z, z, Cell.air(Cell.FEATURE));
				c.archRing(x0, 0, hs, 1, k % 2 == 0 ? Slot.ARCH : Slot.TRIM, z, z, Cell.FEATURE);
				for (int y = 0; y < jamb; y++) {
					c.set(x0 - 1, y, z, Cell.log(Slot.PILLAR, 'y', Cell.FEATURE));
					c.set(x0 + ow, y, z, Cell.log(Slot.PILLAR, 'y', Cell.FEATURE));
				}
			}
			// Through the wall: the innermost opening, doors and the tympanum above them
			int iw = outerW - 2 * r;
			int x0 = (w - iw) / 2;
			int doorH = Math.min(jamb, 4);
			int[] hs = curve.heights(iw);
			for (int i = 0; i < iw; i++) {
				int top = jamb + hs[i];
				for (int y = 0; y < top; y++) {
					for (int z = -(d - 1); z <= 0; z++) {
						if (y < doorH) {
							c.set(x0 + i, y, z, Cell.air(Cell.FEATURE));
						} else {
							c.set(x0 + i, y, z, z == 0 ? Cell.of(Slot.TRIM, Cell.FEATURE) : Cell.air(Cell.FEATURE));
						}
					}
				}
			}
			int dz = 0;
			boolean trumeau = iw >= 5;
			for (int i = 0; i < iw; i++) {
				int x = x0 + i;
				if (trumeau && i == iw / 2) {
					for (int y = 0; y < doorH; y++) c.set(x, y, dz, Cell.log(Slot.PILLAR, 'y', Cell.FEATURE + 1));
					continue;
				}
				boolean right = i >= iw / 2;
				c.set(x, 0, dz, door(Dir.NORTH, false, right).prio(Cell.FEATURE + 1));
				c.set(x, 1, dz, door(Dir.NORTH, true, right).prio(Cell.FEATURE + 1));
				for (int y = 2; y < doorH; y++) c.set(x, y, dz, Cell.of(Slot.WOOD, Cell.FEATURE + 1));
			}
			// Tympanum: a carved centre stone and a gold accent
			int ty = doorH + Math.max(0, (jamb + hs[iw / 2] - doorH) / 2);
			c.decor(x0 + iw / 2, ty, 0, Cell.of(Slot.ACCENT, Cell.FEATURE + 1));
			// Gable over the porch with a finial, pinnacles at the corners
			int gz = r;
			for (int i = 0; ; i++) {
				int lo = i, hi = w - 1 - i;
				if (lo > hi) break;
				int y = porchTop + 1 + i;
				if (lo == hi) {
					c.set(lo, y, gz, Cell.of(Slot.TRIM, Cell.FEATURE));
					c.decor(lo, y + 1, gz, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
					c.decor(lo, y + 2, gz, Cell.literal("lightning_rod", Cell.DETAIL));
					break;
				}
				c.stairs(lo, y, gz, Slot.TRIM, Dir.EAST, false, Cell.FEATURE);
				c.stairs(hi, y, gz, Slot.TRIM, Dir.WEST, false, Cell.FEATURE);
				for (int x = lo + 1; x < hi; x++) {
					for (int z = 1; z <= gz; z++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.DETAIL));
				}
				if (i == 1 && hi - lo > 4) c.rich((lo + hi) / 2, y + 1, gz, Cell.of(Slot.ACCENT, Cell.FEATURE + 1));
			}
			for (int side = 0; side < 2; side++) {
				int x = side == 0 ? 0 : w - 1;
				c.decor(x, porchTop + 1, gz, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				c.decor(x, porchTop + 2, gz, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				c.decor(x, porchTop + 3, gz, Cell.literal("end_rod", Cell.DETAIL));
			}
			c.focal(w / 2, jamb, 0);
		}
	}

	/** A castle gate: a tall round arch through the full wall, the portcullis half raised in its groove. */
	static final class Gate extends Base {
		Gate() {
			super("door.gate", Category.DOOR, Set.of("castle", "military"), size(5, 6, 2), ANY);
		}

		@Override
		public List<Attachment> attachments(Spec s) {
			int gw = clamp(s.w() - 2, 3, 5);
			return List.of(new Attachment("entrance", Attachment.Kind.ENTRANCE, s.w() / 2, 0, 1, Dir.SOUTH, gw, 3));
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int gw = clamp(w - 2, 3, 5);
			int x0 = (w - gw) / 2;
			int jamb = clamp(h - 2 - ArchCurve.ROUND.rise(gw), 3, 6);
			int[] hs = c.archOpening(x0, 0, gw, jamb, ArchCurve.ROUND, -(d - 1), 0, Cell.air(Cell.FEATURE));
			c.archRing(x0, 0, hs, 1, Slot.ARCH, 0, 0, Cell.FEATURE);
			c.archRing(x0, 0, hs, 1, Slot.ARCH, -(d - 1), -(d - 1), Cell.FEATURE);
			int pz = -(d - 1) / 2;
			if (d >= 3) pz = -1;
			for (int i = 0; i < gw; i++) {
				int top = hs[i];
				for (int y = Math.max(jamb - 1, 2); y < top; y++) c.set(x0 + i, y, pz, Cell.shape(Slot.METAL, Shape.PANE, Cell.FEATURE + 1));
			}
			for (int y = 0; y < jamb; y++) {
				c.set(x0 - 1, y, 0, Cell.of(Slot.QUOIN, Cell.DETAIL));
				c.set(x0 + gw, y, 0, Cell.of(Slot.QUOIN, Cell.DETAIL));
			}
		}
	}

	/** A temple door: a tall square opening under a heavy lintel and a low pediment. */
	static final class Temple extends Base {
		Temple() {
			super("door.temple", Category.DOOR, Set.of("ancient", "classical"), size(5, 6, 1), ANY);
		}

		@Override
		public List<Attachment> attachments(Spec s) {
			return List.of(new Attachment("entrance", Attachment.Kind.ENTRANCE, s.w() / 2, 0, 1, Dir.SOUTH, clamp(s.w() - 2, 1, 3), 3));
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int ow = clamp(w - 2, 1, 3);
			int oh = clamp(h - 3, 3, 5);
			int x0 = (w - ow) / 2;
			for (int x = x0; x < x0 + ow; x++) {
				for (int y = 0; y < oh; y++) {
					for (int z = -(d - 1); z <= 0; z++) c.set(x, y, z, Cell.air(Cell.FEATURE));
				}
			}
			for (int x = x0 - 1; x <= x0 + ow; x++) {
				c.set(x, oh, 0, Cell.of(Slot.TRIM, Cell.FEATURE));
				c.set(x, oh, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.FEATURE));
			}
			for (int y = 0; y < oh; y++) {
				c.set(x0 - 1, y, 0, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
				c.set(x0 + ow, y, 0, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
			}
			for (int i = 0; ; i++) {
				int lo = x0 - 1 + i, hi = x0 + ow - i;
				if (lo > hi) break;
				int y = oh + 1 + i;
				if (lo == hi) {
					c.decor(lo, y, 0, Cell.of(Slot.ACCENT, Cell.DETAIL));
					break;
				}
				c.decor(lo, y, 0, Cell.stairs(Slot.TRIM, Dir.EAST, false, Cell.DETAIL));
				c.decor(hi, y, 0, Cell.stairs(Slot.TRIM, Dir.WEST, false, Cell.DETAIL));
			}
		}
	}

	/** An arcane door: a sharp arch with glowing stones and crystals beside it. */
	static final class ArcaneDoor extends Base {
		ArcaneDoor() {
			super("door.arcane", Category.DOOR, Set.of("arcane", "magic"), size(5, 5, 1), ANY);
		}

		@Override
		public List<Attachment> attachments(Spec s) {
			return List.of(new Attachment("entrance", Attachment.Kind.ENTRANCE, s.w() / 2 - 1, 0, 1, Dir.SOUTH, 2, 3));
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), h = s.h(), d = s.d();
			int x0 = w / 2 - 1;
			int jamb = Math.min(3, h - 3);
			int[] hs = c.archOpening(x0, 0, 2, jamb, ArchCurve.LANCET, -(d - 1), 0, Cell.air(Cell.FEATURE));
			c.archRing(x0, 0, hs, 1, Slot.TRIM, 0, 0, Cell.FEATURE);
			int dz = -(d - 1);
			c.set(x0, 0, dz, door(Dir.NORTH, false, false));
			c.set(x0, 1, dz, door(Dir.NORTH, true, false));
			c.set(x0 + 1, 0, dz, door(Dir.NORTH, false, true));
			c.set(x0 + 1, 1, dz, door(Dir.NORTH, true, true));
			for (int i = 0; i < 2; i++) {
				for (int y = 2; y < hs[i]; y++) c.set(x0 + i, y, dz, Windows.stained(1).prio(Cell.FEATURE));
			}
			for (int y = 0; y < jamb; y++) {
				c.set(x0 - 1, y, 0, Cell.of(y == jamb - 1 ? Slot.MAGIC : Slot.TRIM, Cell.FEATURE));
				c.set(x0 + 2, y, 0, Cell.of(y == jamb - 1 ? Slot.MAGIC : Slot.TRIM, Cell.FEATURE));
			}
			c.decor(x0 - 1, jamb, 1, Cell.literal("amethyst_cluster[facing=up]", Cell.DETAIL));
			c.decor(x0 + 2, jamb, 1, Cell.literal("amethyst_cluster[facing=up]", Cell.DETAIL));
			c.focal(w / 2, jamb, 0);
		}
	}
}
