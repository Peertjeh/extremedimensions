package net.battlehub.arch.mod;

import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;

/**
 * Roof modules for use on their own (a building that draws several roofs together uses {@link RoofField}
 * directly so they join). The frame's origin is the footprint's corner at the eaves (the top of the walls): the
 * footprint is x 0..w-1, z 0..d-1. Options: pitch, over (overhang), alongZ.
 */
public final class Roofs {
	private Roofs() {}

	static void register(Library l) {
		l.register(new FieldRoof("roof.gable", Set.of(), "gable"));
		l.register(new FieldRoof("roof.hip", Set.of("town", "palace", "castle", "classical"), "hip"));
		l.register(new FieldRoof("roof.cone", Set.of(), "cone"));
		l.register(new FieldRoof("roof.spire", Set.of("gothic", "arcane", "sacred", "tall"), "spire"));
		l.register(new FieldRoof("roof.dome", Set.of("arcane", "classical", "palace", "sacred"), "dome"));
		l.register(new Flat());
	}

	static final class FieldRoof extends Base {
		private final String kind;

		FieldRoof(String id, Set<String> styles, String kind) {
			super(id, Category.ROOF, styles, size(2, 1, 2), ANY);
			this.kind = kind;
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), d = s.d();
			int pitch = s.i("pitch", c.style().pitch);
			int over = s.i("over", 1);
			RoofField f = new RoofField();
			double cx = (w - 1) / 2.0, cz = (d - 1) / 2.0;
			double r = Math.min(w, d) / 2.0;
			switch (kind) {
				case "gable" -> f.gable(0, 0, w - 1, d - 1, s.opts().containsKey("alongZ") ? s.b("alongZ") : d >= w, 0, pitch, over);
				case "hip" -> f.hip(0, 0, w - 1, d - 1, 0, pitch, over);
				case "cone" -> f.cone(cx, cz, r - 0.5, 0, Math.max(1.5, pitch), over);
				case "spire" -> f.spire(cx, cz, r - 0.5, 0, Math.max(3, pitch + 1), over);
				default -> f.dome(cx, cz, r, 0, s.num("stretch", 1));
			}
			f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
			if (!kind.equals("gable") && !kind.equals("hip")) {
				int tip = f.at((int) Math.round(cx), (int) Math.round(cz));
				c.place("orn.finial", Xform.at((int) Math.round(cx), tip + 1, (int) Math.round(cz)), Spec.of(1, 1, 1));
			}
		}
	}

	/** A flat roof with a low parapet (or battlements). Option battlement. */
	static final class Flat extends Base {
		Flat() {
			super("roof.flat", Category.ROOF, Set.of(), size(3, 1, 3), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), d = s.d();
			boolean crenel = s.b("battlement");
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					if (!edge) {
						c.set(x, 0, z, Cell.of(Slot.FLOOR, Cell.BODY));
						continue;
					}
					c.set(x, 0, z, Cell.of(Slot.WALL, Cell.BODY));
					c.set(x, 1, z, Cell.of(Slot.WALL, Cell.DETAIL));
					if (crenel && ((x + z) & 1) == 0) {
						c.set(x, 2, z, Cell.of(Slot.WALL, Cell.DETAIL));
					} else if (!crenel) {
						c.set(x, 2, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
					}
				}
			}
		}
	}
}
