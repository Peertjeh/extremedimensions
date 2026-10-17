package net.battlehub.arch.mod;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.bldg.Style;
import net.battlehub.arch.geom.ArchCurve;

/**
 * What a module draws with: its frame inside the building, the building itself, and two random streams of its
 * own (one for geometry, one for decoration, so more or less decoration never moves a wall). Coordinates here are
 * the module's local ones; cells are turned into the building's frame as they are drawn.
 */
public final class Ctx {
	public final Building b;
	public final Xform f;
	public final Rng rng;
	public final Rng decor;

	public Ctx(Building b, Xform f, Rng rng, Rng decor) {
		this.b = b;
		this.f = f;
		this.rng = rng;
		this.decor = decor;
	}

	public Style style() {
		return b.style;
	}

	public Library lib() {
		return b.lib;
	}

	public int bx(int x, int z) {
		return f.x(x, z);
	}

	public int by(int y) {
		return f.y(y);
	}

	public int bz(int x, int z) {
		return f.z(x, z);
	}

	/** Draws a cell (respecting priorities). */
	public void set(int x, int y, int z, Cell c) {
		b.plan.put(f.x(x, z), f.y(y), f.z(x, z), c.in(f));
	}

	public void force(int x, int y, int z, Cell c) {
		b.plan.force(f.x(x, z), f.y(y), f.z(x, z), c.in(f));
	}

	public void setIfEmpty(int x, int y, int z, Cell c) {
		b.plan.putIfEmpty(f.x(x, z), f.y(y), f.z(x, z), c.in(f));
	}

	public void remove(int x, int y, int z) {
		b.plan.remove(f.x(x, z), f.y(y), f.z(x, z));
	}

	/** The cell here, in the building's frame (directions as the building sees them). */
	public Cell get(int x, int y, int z) {
		return b.plan.get(f.x(x, z), f.y(y), f.z(x, z));
	}

	public boolean filled(int x, int y, int z) {
		return b.plan.filled(f.x(x, z), f.y(y), f.z(x, z));
	}

	/** An opening: explicit air that later wall drawing will not fill. */
	public void air(int x, int y, int z) {
		set(x, y, z, Cell.AIR);
	}

	public void box(int x0, int y0, int z0, int x1, int y1, int z1, Cell c) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, c);
				}
			}
		}
	}

	public void airBox(int x0, int y0, int z0, int x1, int y1, int z1) {
		box(x0, y0, z0, x1, y1, z1, Cell.AIR);
	}

	/** Stairs facing a local direction (the tall side), upside down when {@code top}. */
	public void stairs(int x, int y, int z, Slot s, Dir facing, boolean top, int prio) {
		set(x, y, z, Cell.stairs(s, facing, top, prio));
	}

	public void slab(int x, int y, int z, Slot s, boolean top, int prio) {
		set(x, y, z, Cell.slab(s, top, prio));
	}

	public void literal(int x, int y, int z, String id, int prio) {
		set(x, y, z, Cell.literal(id, prio));
	}

	/** Decoration: only at detail 1 and up, marked so it can be told apart from structure. */
	public void decor(int x, int y, int z, Cell c) {
		if (b.detail >= 1) set(x, y, z, c.flag(Cell.DECOR));
	}

	/** Rich decoration: only at detail 2. */
	public void rich(int x, int y, int z, Cell c) {
		if (b.detail >= 2) set(x, y, z, c.flag(Cell.DECOR));
	}

	public boolean detail(int level) {
		return b.detail >= level;
	}

	/** A context for a part placed in this module's frame. */
	public Ctx sub(Xform child) {
		return new Ctx(b, child.in(f), rng.stream("sub", child.hashCode()), decor.stream("sub", child.hashCode()));
	}

	public Ctx sub(Xform child, String stream) {
		return new Ctx(b, child.in(f), rng.stream(stream), decor.stream(stream));
	}

	/** Places a module in a child frame. */
	public void place(Module m, Xform child, Spec s) {
		if (m == null) return;
		Ctx c = sub(child, m.id() + "@" + child.ox() + "," + child.oy() + "," + child.oz());
		m.build(c, s);
		for (Attachment a : m.attachments(s)) {
			c.attach(a);
		}
		b.used(m.id());
	}

	public void place(String id, Xform child, Spec s) {
		place(lib().get(id), child, s);
	}

	public void attach(Attachment a) {
		b.attach(a.in(f));
	}

	/** A focal point for gradients and decoration (a portal, a rose window). */
	public void focal(int x, int y, int z) {
		b.focal(f.x(x, z), f.y(y), f.z(x, z));
	}

	/** Ground height under a local column in local y, or Integer.MIN_VALUE when the building floats. */
	public int ground(int x, int z) {
		int g = b.groundAt(f.x(x, z), f.z(x, z));
		return g == Integer.MIN_VALUE ? g : g - f.oy();
	}

	// ------------------------------------------------------------------ shapes

	/**
	 * An arched opening {@code w} wide in the wall plane (local x = x0..x0+w-1, from y0 up), cut through z from
	 * {@code z0} to {@code z1}: straight jambs for {@code jamb} blocks, then the curve. Returns the column heights
	 * of the opening above y0.
	 */
	public int[] archOpening(int x0, int y0, int w, int jamb, ArchCurve curve, int z0, int z1, Cell fill) {
		int[] h = curve.heights(w);
		int[] total = new int[w];
		for (int i = 0; i < w; i++) {
			total[i] = jamb + h[i];
			for (int y = 0; y < total[i]; y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x0 + i, y0 + y, z, fill);
				}
			}
		}
		return total;
	}

	/**
	 * The ring of an arch over an opening (heights from {@link #archOpening}): {@code ring} blocks of {@code slot}
	 * above each column, with upside-down stairs smoothing the steps of the curve.
	 */
	public void archRing(int x0, int y0, int[] heights, int ring, Slot slot, int zFrom, int zTo, int prio) {
		int w = heights.length;
		for (int i = 0; i < w; i++) {
			for (int k = 0; k < ring; k++) {
				for (int z = Math.min(zFrom, zTo); z <= Math.max(zFrom, zTo); z++) {
					set(x0 + i, y0 + heights[i] + k, z, Cell.of(slot, prio));
				}
			}
		}
		// Smooth the curve: where the opening steps up towards the middle, an upside-down stair on the lower side
		for (int i = 0; i < w; i++) {
			boolean left = i < w / 2.0 - 0.5;
			int nb = left ? i + 1 : i - 1;
			if (nb < 0 || nb >= w || (w % 2 == 1 && i == w / 2)) continue;
			if (heights[nb] > heights[i] && heights[nb] - heights[i] <= 2) {
				int y = y0 + heights[i];
				Dir face = left ? Dir.WEST : Dir.EAST;
				for (int z = Math.min(zFrom, zTo); z <= Math.max(zFrom, zTo); z++) {
					set(x0 + i, y, z, Cell.stairs(slot, face, true, prio));
				}
			}
		}
	}

	/** A filled disc in the local xz plane (centre cx, cz). */
	public void disc(double cx, int y, double cz, double r, Cell c) {
		int ir = (int) Math.ceil(r);
		for (int x = (int) Math.floor(cx) - ir; x <= (int) Math.ceil(cx) + ir; x++) {
			for (int z = (int) Math.floor(cz) - ir; z <= (int) Math.ceil(cz) + ir; z++) {
				double dx = x - cx, dz = z - cz;
				if (dx * dx + dz * dz <= r * r + 0.3) set(x, y, z, c);
			}
		}
	}

	/** A ring between two radii. */
	public void ring(double cx, int y, double cz, double rIn, double rOut, Cell c) {
		int ir = (int) Math.ceil(rOut);
		for (int x = (int) Math.floor(cx) - ir; x <= (int) Math.ceil(cx) + ir; x++) {
			for (int z = (int) Math.floor(cz) - ir; z <= (int) Math.ceil(cz) + ir; z++) {
				double d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
				if (d2 <= rOut * rOut + 0.3 && d2 > rIn * rIn + 0.3) set(x, y, z, c);
			}
		}
	}

	public static Cell cell(Slot s, int prio) {
		return Cell.of(s, prio);
	}

	public static Cell wallPost(Slot s, int prio) {
		return Cell.shape(s, Shape.WALL, prio);
	}
}
