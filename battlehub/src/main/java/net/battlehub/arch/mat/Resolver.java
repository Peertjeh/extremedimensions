package net.battlehub.arch.mat;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Half;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;

/**
 * Decides the block for a planned cell, in layers: (1) its role and form, (2) the role's ramp in the palette,
 * (3) the gradient value at its building position, (4) the ramp entry with a coherent per-stone random number in
 * the borders, (5) a form the chosen material really has, (6) weathering where it shows, (7) the final name
 * with its block-state properties. Roles never mix: a roof cell only ever gets roof materials.
 */
public final class Resolver {
	private Resolver() {}

	/** Last resort per form when a palette has nothing that fits. */
	private static String fallback(Shape s) {
		return switch (s) {
			case STAIRS -> "stone_brick_stairs";
			case SLAB -> "stone_brick_slab";
			case WALL -> "stone_brick_wall";
			case FENCE -> "oak_fence";
			case PANE -> "glass_pane";
			case TRAPDOOR -> "spruce_trapdoor";
			case DOOR -> "spruce_door";
			case LOG -> "oak_log";
			default -> "stone_bricks";
		};
	}

	/** {@code c} must already be turned into world directions. */
	public static String resolve(Cell c, Probe p, MaterialContext mc) {
		if (c.slot() == Slot.AIR) return "air";
		if (c.slot() == Slot.LITERAL) return c.literal();
		if (c.slot() == Slot.WATER) return "water";
		Ramp ramp = mc.palette.ramp(c.slot());
		Shape shape = c.shape();
		Material m;
		if (ramp == null) {
			return withProps(fallback(shape), c);
		}
		int idx;
		if (c.variant() > 0) {
			idx = (c.variant() - 1) % ramp.size();
		} else {
			double t = mc.field(c.slot()).at(p);
			// Frames, courses and edges change material at clear lines, never stone by stone
			idx = ramp.index(t, crisp(c.slot()) ? 0.5 : stone(p, c, mc));
		}
		int ok = ramp.nearestWith(idx, shape);
		if (ok >= 0) {
			m = ramp.get(ok);
		} else {
			m = standIn(ramp.get(idx), shape);
			if (m == null) m = Catalog.nearestWith(ramp.get(idx), shape);
			if (m == null) return withProps(fallback(shape), c);
		}
		if (c.slot().weathers() && !c.is(Cell.INSIDE)) {
			m = weather(m, shape, p, mc);
		}
		String id = m.id(shape);
		if (id == null) id = fallback(shape);
		if (m.is("leaves")) return id + "[persistent=true]";
		// a "log" of a family without a pillar block is its plain block, which has no axis
		if (shape == Shape.LOG && m.pillar() == null) return id;
		return withProps(id, c);
	}

	static boolean crisp(Slot s) {
		return switch (s) {
			case TRIM, ARCH, QUOIN, PILLAR, BAND, ROOF_EDGE, ACCENT, FLOOR_ALT, TIMBER -> true;
			default -> false;
		};
	}

	/** The material's stand-in chain for a form it lacks. */
	private static Material standIn(Material m, Shape shape) {
		for (int i = 0; i < 4 && m != null; i++) {
			if (m.has(shape)) return m;
			m = m.alt() == null ? null : Catalog.get(m.alt());
		}
		return null;
	}

	/**
	 * A random number that is the same for every block of one "stone" (two blocks long, rows offset like a running
	 * bond), mixed with smooth noise so borders between materials form patches, not speckles.
	 */
	static double stone(Probe p, Cell c, MaterialContext mc) {
		int off = p.by & 1;
		int ux = Math.floorDiv(p.bx + off, 2);
		int uz = Math.floorDiv(p.bz + off, 2);
		double u = Rng.hash(mc.seed ^ (c.slot().ordinal() * 0x9E37L), ux, p.by, uz);
		double n = mc.patch.at(p.bx, p.by * 1.3, p.bz, 6, 2);
		// mostly the patch noise (stretched to 0..1), a little of the per-stone number for ragged edges
		return Math.max(0, Math.min(0.999, 0.3 * u + 0.7 * Math.max(0, Math.min(1, (n - 0.5) * 2.6 + 0.5))));
	}

	private static Material weather(Material m, Shape shape, Probe p, MaterialContext mc) {
		if (m.aged().isEmpty()) return m;
		double w = mc.weatherAt(p);
		if (w <= 0) return m;
		double n = mc.weatherNoise.at(p.bx, p.by, p.bz, 4.5, 2);
		if (n > w * 0.95 + 0.05 * Rng.hash(mc.seed, p.bx, p.by, p.bz)) return m;
		// Low down moss, higher up cracks
		Material pick = null;
		boolean low = p.by - mc.groundY < 3 + 5 * Rng.hash(mc.seed + 7, p.bx >> 1, p.by, p.bz >> 1);
		for (String a : m.aged()) {
			Material am = Catalog.get(a);
			if (am == null || !am.has(shape)) continue;
			if (pick == null || (low ? am.is("mossy") : am.is("cracked"))) pick = am;
		}
		return pick == null ? m : pick;
	}

	/** The block-state properties for a form. */
	public static String withProps(String id, Cell c) {
		return switch (c.shape()) {
			case STAIRS -> id + "[facing=" + (c.facing() == null ? "north" : c.facing().id()) + ",half=" + (c.half() == Half.TOP ? "top" : "bottom") + "]";
			case SLAB -> id + "[type=" + (c.half() == null ? "bottom" : c.half().id()) + "]";
			case LOG -> c.axis() == 0 || c.axis() == 'y' ? id : id + "[axis=" + c.axis() + "]";
			case TRAPDOOR -> id + "[facing=" + (c.facing() == null ? "north" : c.facing().id()) + ",half=" + (c.half() == Half.TOP ? "top" : "bottom")
					+ ",open=" + (c.variant() == 1 ? "true" : "false") + "]";
			case DOOR -> id + "[facing=" + (c.facing() == null ? "north" : c.facing().id()) + ",half=" + (c.half() == Half.TOP ? "upper" : "lower")
					+ ",hinge=" + ((c.variant() & 1) == 1 ? "right" : "left") + "]";
			default -> id;
		};
	}
}
