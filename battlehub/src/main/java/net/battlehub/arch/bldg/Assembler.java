package net.battlehub.arch.bldg;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.RoofField;
import net.battlehub.arch.mod.Spec;

/**
 * Fills planned volumes in: foundations down to the ground, walls through the facade composer, floors with
 * patterns, interior space, roofs in one shared height field (so intersecting roofs join) and furnishings. The
 * archetypes decide what to build; the assembler knows how.
 */
public final class Assembler {
	private Assembler() {}

	/** The roofs of one building, drawn together at the end; gable ends to fill and footprints to keep clear. */
	public static final class Roofs {
		public final RoofField field = new RoofField();
		final List<int[]> gableEnds = new ArrayList<>();
		final List<Footprint> masks = new ArrayList<>();

		/** A gable on a rectangle: ridge along the longer side unless told otherwise. */
		public void gable(Footprint fp, int eave, int pitch, int over, Boolean alongZ) {
			boolean z = alongZ != null ? alongZ : fp.depth() >= fp.width();
			field.gable(fp.minX(), fp.minZ(), fp.maxX(), fp.maxZ(), z, eave, pitch, over);
			if (z) {
				gableEnds.add(new int[] {fp.minX(), fp.maxX(), fp.minZ(), 1, eave});
				gableEnds.add(new int[] {fp.minX(), fp.maxX(), fp.maxZ(), 1, eave});
			} else {
				gableEnds.add(new int[] {fp.minZ(), fp.maxZ(), fp.minX(), 0, eave});
				gableEnds.add(new int[] {fp.minZ(), fp.maxZ(), fp.maxX(), 0, eave});
			}
		}

		/** Columns the roof must not cover (towers rising through it). */
		public void mask(Footprint fp) {
			masks.add(fp);
		}
	}

	public static Ctx ctx(Building b, String stream) {
		return new Ctx(b, Xform.ID, b.rng.stream(stream), b.rng.stream("decor:" + stream));
	}

	/**
	 * A foundation under a footprint: from just below the floor down to the ground (or {@code depth} blocks when
	 * the building has no ground under it), one block wider than the walls so the plinth stands on something.
	 */
	public static void foundation(Building b, Footprint fp, int floorY, int depth) {
		Ctx c = ctx(b, "foundation");
		Footprint wide = fp.inset(-1);
		for (int x = wide.minX(); x <= wide.maxX(); x++) {
			for (int z = wide.minZ(); z <= wide.maxZ(); z++) {
				if (!wide.contains(x, z)) continue;
				int g = b.groundAt(x, z);
				int bottom = g == Integer.MIN_VALUE ? floorY - depth : Math.min(g, floorY - 1);
				boolean under = fp.contains(x, z);
				for (int y = floorY - 1; y >= bottom; y--) {
					c.set(x, y, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
				}
				if (!under && g != Integer.MIN_VALUE && g > floorY - 1) {
					// the ground is higher: nothing to fill, the ring is the ground itself
					continue;
				}
				if (!under) c.set(x, floorY - 1, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
			}
		}
	}

	/** Explicit interior space (clears terrain; anything drawn later replaces it). */
	public static void hollow(Building b, Footprint inner, int y0, int y1) {
		Ctx c = ctx(b, "hollow");
		for (int x = inner.minX(); x <= inner.maxX(); x++) {
			for (int z = inner.minZ(); z <= inner.maxZ(); z++) {
				if (!inner.contains(x, z)) continue;
				for (int y = y0; y <= y1; y++) c.set(x, y, z, Cell.air(Cell.FILL));
			}
		}
	}

	/**
	 * A floor at height y over a footprint, with a pattern: "border" (a frame of the second material),
	 * "checker", "aisle" (a band down the middle along z) or "plain".
	 */
	public static void floor(Building b, Footprint fp, int y, String pattern) {
		Ctx c = ctx(b, "floor");
		double cx = fp.centerX();
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.contains(x, z)) continue;
				boolean alt = switch (pattern) {
					case "border" -> fp.edge(x, z) || !fp.inset(1).contains(x, z) ? false : !fp.inset(2).contains(x, z);
					case "checker" -> ((x + z) & 1) == 0;
					case "aisle" -> Math.abs(x - cx) <= 1.0 && ((z & 3) != 0) || Math.abs(x - cx) > 1.0 && ((x + z) & 1) == 0 && !fp.inset(1).contains(x, z);
					default -> false;
				};
				c.set(x, y, z, Cell.of(alt ? Slot.FLOOR_ALT : Slot.FLOOR, Cell.FILL).flag(Cell.INSIDE));
			}
		}
	}

	/** The standard facade rules for a volume of a style (archetypes adjust them). */
	public static Facade.Rules rules(Building b, Volume v) {
		Style st = b.style;
		Facade.Rules r = new Facade.Rules();
		r.bay = st.bay;
		r.floors = new int[v.storeys];
		int sh = Math.max(3, (v.height()) / v.storeys);
		for (int i = 0; i < v.storeys; i++) r.floors[i] = i * sh;
		r.storey = sh;
		if (st.has("gothic")) {
			r.pier = "pier.buttress";
			r.pierDepth = 2;
			r.pinnacles = b.detail >= 1;
			r.windows = new String[] {"window.lancet"};
			r.door = "door.double";
			r.quoins = false;
		} else if (st.has("castle")) {
			r.pier = v.role == Volume.Role.CURTAIN ? null : "pier.pilaster";
			r.windows = new String[] {"window.slit", "window.round"};
			r.door = "door.gate";
			r.cornice = false;
		} else if (st.has("arcane")) {
			r.pier = "pier.pilaster";
			r.windows = new String[] {"window.arcane"};
			r.door = "door.arcane";
		} else if (st.has("ancient")) {
			r.pier = "pier.column";
			r.windows = new String[] {"window.round"};
			r.door = "door.temple";
			r.quoins = false;
		} else {
			r.pier = null;
			r.windows = new String[] {"window.rect"};
			r.door = "door.plain";
			r.timberUpper = st.has("timber") && v.storeys > 1;
			r.bay = 4;
			r.cornice = false;
		}
		return r;
	}

	/** The walls of a rectangular volume: four runs, the entrance on its front. */
	public static void walls(Building b, Volume v, Facade.Rules r) {
		Rng rng = b.rng.stream("walls:" + v.id);
		Rng decor = b.rng.stream("decor:walls:" + v.id);
		for (Facade.Run run : Facade.runs(v.fp, v.y0)) {
			Facade.Rules rr = copy(r);
			rr.entrance = v.entrance && run.outward() == v.front;
			if (!rr.entrance) rr.door = null;
			Facade.compose(b, v, run, rr, rng.stream(run.outward().id()), decor.stream(run.outward().id()));
		}
	}

	static Facade.Rules copy(Facade.Rules r) {
		Facade.Rules c = new Facade.Rules();
		c.bay = r.bay;
		c.pier = r.pier;
		c.pierWidth = r.pierWidth;
		c.pierDepth = r.pierDepth;
		c.cornerPiers = r.cornerPiers;
		c.pinnacles = r.pinnacles;
		c.windows = r.windows;
		c.door = r.door;
		c.doorWidth = r.doorWidth;
		c.entrance = r.entrance;
		c.plinth = r.plinth;
		c.bands = r.bands;
		c.cornice = r.cornice;
		c.quoins = r.quoins;
		c.timberUpper = r.timberUpper;
		c.floors = r.floors;
		c.storey = r.storey;
		c.pierTop = r.pierTop;
		return c;
	}

	/** Upper floors of a volume (the ground floor is drawn with the foundation). */
	public static void floors(Building b, Volume v, String pattern) {
		Footprint inner = v.fp.inset(v.wall);
		hollow(b, inner, v.y0 + 1, v.top);
		floor(b, inner, v.y0, pattern);
		int sh = Math.max(3, v.height() / v.storeys);
		for (int i = 1; i < v.storeys; i++) floor(b, inner, v.y0 + i * sh, "plain");
	}

	/** Places an interior module in a volume's ground floor room, its far end opposite the entrance. */
	public static void interior(Building b, Volume v, String moduleId, int floorY, int height) {
		Footprint in = v.fp.inset(v.wall);
		Dir front = v.front == null ? Dir.SOUTH : v.front;
		// Room frame: z = 0 at the front wall, growing towards the back
		int w, d;
		Xform f;
		switch (front) {
			case SOUTH -> {
				w = in.width();
				d = in.depth();
				f = new Xform(2, false, in.maxX(), floorY + 1, in.maxZ());
			}
			case NORTH -> {
				w = in.width();
				d = in.depth();
				f = new Xform(0, false, in.minX(), floorY + 1, in.minZ());
			}
			case EAST -> {
				w = in.depth();
				d = in.width();
				f = new Xform(1, false, in.maxX(), floorY + 1, in.minZ());
			}
			default -> {
				w = in.depth();
				d = in.width();
				f = new Xform(3, false, in.minX(), floorY + 1, in.maxZ());
			}
		}
		Ctx c = new Ctx(b, f, b.rng.stream("interior:" + v.id), b.rng.stream("decor:interior:" + v.id));
		c.place(moduleId, Xform.ID, Spec.of(w, height, d));
	}

	/** Draws the building's roofs: gable ends filled with wall, masked columns left out, then the field. */
	public static void finishRoofs(Building b, Roofs roofs) {
		Ctx c = ctx(b, "roofs");
		RoofField masked = new RoofField();
		RoofField src = roofs.field;
		for (int x = b.plan.minX() - 3; x <= b.plan.maxX() + 3; x++) {
			for (int z = b.plan.minZ() - 3; z <= b.plan.maxZ() + 3; z++) {
				int h = src.at(x, z);
				if (h == Integer.MIN_VALUE) continue;
				boolean hidden = false;
				for (Footprint m : roofs.masks) hidden |= m.contains(x, z);
				if (!hidden) masked.raise(x, z, h, src.baseAt(x, z));
			}
		}
		for (int[] g : roofs.gableEnds) {
			boolean alongX = g[3] == 1;
			for (int a = g[0]; a <= g[1]; a++) {
				int x = alongX ? a : g[2];
				int z = alongX ? g[2] : a;
				int h = masked.at(x, z);
				if (h == Integer.MIN_VALUE) continue;
				for (int y = g[4] + 1; y < h; y++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
			}
		}
		masked.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
	}

	/** Removes everything planned inside a footprint between two heights (a room cut through floors). */
	public static void clear(Building b, Footprint fp, int y0, int y1) {
		Ctx c = ctx(b, "clear");
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.contains(x, z)) continue;
				for (int y = y0; y <= y1; y++) c.force(x, y, z, Cell.air(Cell.OPEN));
			}
		}
	}
}
