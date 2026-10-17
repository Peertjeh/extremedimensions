package net.battlehub.arch.bldg;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mod.Attachment;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.RoofField;
import net.battlehub.arch.mod.Spec;

/**
 * Towers as one vertical composition: a battered base, a shaft with string courses and openings that turn from
 * storey to storey, floors, a spiral stair from the door to the top, and a crown (battlements, machicolations, a
 * cone, a spire, a dome, an open belfry or a crystal cage), with corbelled turrets and a balcony where asked.
 * Round, octagonal and square plans all go through the same code.
 *
 * <p>Volume options: crown, openings ("slit", "lancet", "glass", "arcane"), turrets (count), balcony (height above
 * y0), batter (rows), stair (spiral), bands (every n rows), pitch (roof).
 */
public final class Towers {
	private Towers() {}

	public static void build(Building b, Volume v) {
		Rng rng = b.rng.stream("tower:" + v.id);
		Rng decor = b.rng.stream("decor:tower:" + v.id);
		Ctx c = new Ctx(b, Xform.ID, rng, decor);
		Footprint fp = v.fp;
		double cx = fp.centerX(), cz = fp.centerZ();
		double r = Math.max(fp.width(), fp.depth()) / 2.0;
		int wall = v.wall;
		int top = v.top;
		// Only towers that stand on the ground get a foundation (not stages stacked on others, not corbelled turrets)
		if (v.y0 <= b.floorY + 1 && !v.b("corbel")) Assembler.foundation(b, fp, v.y0, 3);
		// Battered base: one block wider for a few rows, sloping in at the top
		int batter = v.i("batter", 0);
		Footprint wide = fp.inset(-1);
		for (int y = v.y0; y < v.y0 + batter; y++) {
			for (int x = wide.minX(); x <= wide.maxX(); x++) {
				for (int z = wide.minZ(); z <= wide.maxZ(); z++) {
					if (!wide.contains(x, z) || fp.contains(x, z)) continue;
					if (y == v.y0 + batter - 1) {
						Dir in = inward(x, z, cx, cz);
						c.set(x, y, z, Cell.stairs(Slot.FOUNDATION, in, false, Cell.DETAIL));
					} else {
						c.set(x, y, z, Cell.of(Slot.FOUNDATION, Cell.BODY));
					}
				}
			}
		}
		// The shaft
		int bands = v.i("bands", 0);
		for (int y = v.y0; y <= top; y++) {
			for (int x = fp.minX(); x <= fp.maxX(); x++) {
				for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
					if (!fp.contains(x, z)) continue;
					if (fp.shell(x, z, wall)) {
						boolean band = bands > 0 && y > v.y0 + 2 && (y - v.y0) % bands == 0 && fp.edge(x, z);
						c.set(x, y, z, Cell.of(band ? Slot.BAND : Slot.WALL, Cell.BODY));
					} else {
						c.set(x, y, z, Cell.air(Cell.FILL));
					}
				}
			}
		}
		// Floors every storey, with the stair ring left open
		int sh = Math.max(4, v.height() / v.storeys);
		Footprint inner = fp.inset(wall);
		boolean stair = inner.width() >= 3 && !"none".equals(v.s("stair", "spiral"));
		// "well": floors leave the stair ring open, but the stair itself is drawn by whoever stacks the towers
		boolean drawStair = stair && !"well".equals(v.s("stair", "spiral"));
		int scx = (int) Math.round(cx), scz = (int) Math.round(cz);
		for (int i = 0; i < v.storeys; i++) {
			int fy = v.y0 + i * sh;
			for (int x = inner.minX(); x <= inner.maxX(); x++) {
				for (int z = inner.minZ(); z <= inner.maxZ(); z++) {
					if (!inner.contains(x, z)) continue;
					boolean ring = stair && (i > 0 || "well".equals(v.s("stair", "spiral")) && v.y0 > b.floorY + 1) && Math.max(Math.abs(x - scx), Math.abs(z - scz)) == 1;
					if (ring) continue;
					c.set(x, fy, z, Cell.of(i == 0 ? Slot.FLOOR : Slot.FLOOR_ALT, Cell.FILL).flag(Cell.INSIDE));
				}
			}
		}
		int stairTop = v.y0 + (v.storeys - 1) * sh;
		if (drawStair) spiral(c, scx, scz, v.y0 + 1, stairTop);
		// Openings: per storey, turned half a step from the storey below
		String kind = v.s("openings", b.style.has("castle") ? "slit" : b.style.has("arcane") ? "arcane" : b.style.has("gothic") ? "lancet" : "glass");
		int per = r >= 5 ? 8 : 4;
		for (int i = 0; i < v.storeys; i++) {
			int fy = v.y0 + i * sh;
			if (i == 0 && v.front != null) continue;
			for (int k = 0; k < per; k++) {
				double a = (k + (i % 2) * 0.5) * 2 * Math.PI / per;
				opening(c, fp, cx, cz, a, fy + 2, Math.min(sh - 3, kind.equals("slit") ? 3 : 3), wall, kind);
			}
		}
		// The door
		if (v.front != null) {
			door(c, b, fp, cx, cz, v.front, v.y0, wall);
		}
		// Balcony
		int balcony = v.i("balcony", 0);
		if (balcony > 0) balcony(c, fp, cx, cz, v.y0 + balcony);
		crown(b, c, v, fp, cx, cz, r, top, rng);
		int turrets = v.i("turrets", 0);
		for (int t = 0; t < turrets; t++) {
			double a = Math.PI / 4 + t * 2 * Math.PI / Math.max(1, turrets);
			turret(b, c, cx, cz, r, a, top - 5, top + 2, rng);
		}
	}

	static Dir inward(double x, double z, double cx, double cz) {
		return Dir.of((int) Math.signum(Math.round(cx - x)), (int) Math.signum(Math.round(cz - z)));
	}

	/** A spiral stair round a central post: one step up per block, a full turn every eight. */
	public static void spiral(Ctx c, int cx, int cz, int y0, int y1) {
		int[][] ring = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
		for (int y = y0 - 1; y <= y1 + 1; y++) c.set(cx, y, cz, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL).flag(Cell.INSIDE));
		int steps = y1 - y0 + 1;
		if (steps < 1) return;
		for (int i = 0; i < steps; i++) {
			int[] p = ring[i % 8];
			int[] next = ring[(i + 1) % 8];
			Dir up = Dir.of(next[0] - p[0], next[1] - p[1]);
			int y = y0 + i;
			c.set(cx + p[0], y, cz + p[1], Cell.stairs(Slot.FLOOR_ALT, up, false, Cell.FEATURE).flag(Cell.INSIDE).flag(Cell.KEEP));
			// headroom above each step
			for (int hy = 1; hy <= 3; hy++) c.set(cx + p[0], y + hy, cz + p[1], Cell.air(Cell.FEATURE - 1));
		}
		c.attach(new Attachment("stair_bottom", Attachment.Kind.LANDING, cx + ring[0][0], y0, cz + ring[0][1], Dir.NORTH, 1, 2));
		int[] last = ring[(steps - 1) % 8];
		c.attach(new Attachment("stair_top", Attachment.Kind.LANDING, cx + last[0], y0 + steps - 1, cz + last[1], Dir.NORTH, 1, 2));
	}

	/** An opening through a curved wall at angle a: cut along the ray, framed on the outside. */
	static void opening(Ctx c, Footprint fp, double cx, double cz, double a, int y, int h, int wall, String kind) {
		double dx = Math.cos(a), dz = Math.sin(a);
		int ox = Integer.MIN_VALUE, oz = 0;
		// Walk outwards to the last cell inside the footprint
		for (double t = 0; t < 40; t += 0.25) {
			int x = (int) Math.round(cx + dx * t), z = (int) Math.round(cz + dz * t);
			if (!fp.contains(x, z)) break;
			ox = x;
			oz = z;
		}
		if (ox == Integer.MIN_VALUE) return;
		boolean slit = kind.equals("slit");
		for (int k = 0; k < wall; k++) {
			int x = (int) Math.round(ox - dx * k), z = (int) Math.round(oz - dz * k);
			for (int yy = y; yy < y + h; yy++) {
				Cell cell;
				if (k == Math.min(1, wall - 1) && !slit) {
					cell = kind.equals("glass") ? Cell.shape(Slot.GLASS, Shape.PANE, Cell.OPEN)
							: Cell.shape(Slot.STAINED, Shape.PANE, Cell.OPEN).variant(1 + (yy - y) % 3);
				} else {
					cell = Cell.air(Cell.OPEN);
				}
				c.set(x, yy, z, cell);
			}
		}
		if (!slit) {
			// lintel and sill in trim on the outer face; the arcane kind gets a glowing keystone
			c.set(ox, y + h, oz, Cell.of(kind.equals("arcane") ? Slot.MAGIC : Slot.TRIM, Cell.DETAIL));
			c.set(ox, y - 1, oz, Cell.of(Slot.TRIM, Cell.DETAIL));
		}
	}

	static void door(Ctx c, Building b, Footprint fp, double cx, double cz, Dir front, int y0, int wall) {
		int x = (int) Math.round(cx), z = (int) Math.round(cz);
		while (fp.contains(x + front.dx, z + front.dz)) {
			x += front.dx;
			z += front.dz;
		}
		// x,z: the outermost wall cell on the front axis
		for (int k = 0; k < wall; k++) {
			int px = x - front.dx * k, pz = z - front.dz * k;
			for (int y = y0 + 1; y <= y0 + 3; y++) c.set(px, y, pz, Cell.air(Cell.FEATURE));
			if (k == wall - 1) {
				c.set(px, y0 + 1, pz, new Cell(Slot.WOOD, Shape.DOOR, front.back(), net.battlehub.arch.Half.BOTTOM, (char) 0, Cell.FEATURE, 0, 0, null));
				c.set(px, y0 + 2, pz, new Cell(Slot.WOOD, Shape.DOOR, front.back(), net.battlehub.arch.Half.TOP, (char) 0, Cell.FEATURE, 0, 0, null));
			}
		}
		// frame
		Dir side = front.right();
		for (int y = y0 + 1; y <= y0 + 3; y++) {
			c.set(x + side.dx, y, z + side.dz, Cell.of(Slot.TRIM, Cell.DETAIL));
			c.set(x - side.dx, y, z - side.dz, Cell.of(Slot.TRIM, Cell.DETAIL));
		}
		c.set(x, y0 + 4, z, Cell.of(Slot.TRIM, Cell.DETAIL));
		c.decor(x + front.dx + side.dx, y0 + 4, z + front.dz + side.dz, Cell.stairs(Slot.TRIM, front.back(), true, Cell.DETAIL));
		c.decor(x + front.dx + side.dx, y0 + 3, z + front.dz + side.dz, Cell.literal("lantern[hanging=true]", Cell.DETAIL));
		// a step down outside if the ground is lower
		c.attach(new Attachment("entrance", Attachment.Kind.ENTRANCE, x + front.dx, y0 + 1, z + front.dz, front, 1, 2));
	}

	static void balcony(Ctx c, Footprint fp, double cx, double cz, int y) {
		Footprint out = fp.inset(-2);
		Footprint mid = fp.inset(-1);
		for (int x = out.minX(); x <= out.maxX(); x++) {
			for (int z = out.minZ(); z <= out.maxZ(); z++) {
				if (!out.contains(x, z) || fp.contains(x, z)) continue;
				c.set(x, y, z, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
				if (!mid.contains(x, z)) {
					c.decor(x, y + 1, z, Cell.shape(Slot.METAL, Shape.PANE, Cell.DETAIL));
				} else {
					c.set(x, y - 1, z, Cell.stairs(Slot.TRIM, inward(x, z, cx, cz), true, Cell.DETAIL));
				}
			}
		}
	}

	/** The crown of a tower (top = the last row of the shaft). */
	static void crown(Building b, Ctx c, Volume v, Footprint fp, double cx, double cz, double r, int top, Rng rng) {
		String crown = v.s("crown", "battlement");
		Footprint inner = fp.inset(v.wall);
		switch (crown) {
			case "battlement", "machicolation" -> {
				boolean mach = crown.equals("machicolation");
				Footprint ring = mach ? fp.inset(-1) : fp;
				for (int x = ring.minX(); x <= ring.maxX(); x++) {
					for (int z = ring.minZ(); z <= ring.maxZ(); z++) {
						if (!ring.contains(x, z)) continue;
						if (!fp.contains(x, z)) {
							// corbels under the overhanging parapet, with a gap now and then (machicolations)
							Dir in = inward(x, z, cx, cz);
							c.set(x, top, z, ((x + z) & 3) == 0 ? Cell.air(Cell.DETAIL) : Cell.stairs(Slot.TRIM, in, true, Cell.DETAIL));
						}
						if (ring.edge(x, z)) {
							c.set(x, top + 1, z, Cell.of(Slot.WALL, Cell.DETAIL));
							double a = Math.atan2(z - cz, x - cx);
							int seg = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * Math.max(8, (int) (r * 4)));
							if (seg % 2 == 0) c.set(x, top + 2, z, Cell.of(Slot.WALL, Cell.DETAIL));
						} else if (inner.contains(x, z) || fp.contains(x, z)) {
							c.set(x, top + 1, z, Cell.air(Cell.FILL));
						}
					}
				}
				for (int x = fp.minX(); x <= fp.maxX(); x++) {
					for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
						if (fp.contains(x, z) && !fp.shell(x, z, 1)) c.set(x, top, z, Cell.of(Slot.FLOOR, Cell.BODY));
					}
				}
				if (v.b("flag")) flag(c, (int) Math.round(cx), top + 1, (int) Math.round(cz), v.s("banner", "red"));
			}
			case "cone", "spire" -> {
				RoofField f = new RoofField();
				double pitch = crown.equals("spire") ? v.i("pitch", 3) : v.i("pitch", 2);
				if (crown.equals("spire") && !(fp instanceof Footprint.Circle)) {
					f.spire(cx, cz, r - 0.5, top + 1, pitch, 1);
				} else {
					f.cone(cx, cz, r - 0.5, top + 1, pitch, 1);
				}
				f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
				int tip = f.at((int) Math.round(cx), (int) Math.round(cz));
				c.place("orn.finial", Xform.at((int) Math.round(cx), tip + 1, (int) Math.round(cz)), Spec.of(1, 1, 1).with("gold", b.style.has("gothic")));
				// a ring of trim under the eaves
				for (int x = fp.minX(); x <= fp.maxX(); x++) {
					for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
						if (fp.edge(x, z)) c.set(x, top, z, Cell.of(Slot.BAND, Cell.DETAIL));
					}
				}
			}
			case "dome" -> {
				RoofField f = new RoofField();
				f.dome(cx, cz, r, top + 1, v.i("stretch", 1));
				f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
				int tip = f.at((int) Math.round(cx), (int) Math.round(cz));
				c.set((int) Math.round(cx), tip + 1, (int) Math.round(cz), Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				c.place("orn.finial", Xform.at((int) Math.round(cx), tip + 2, (int) Math.round(cz)), Spec.of(1, 1, 1).with("gold", true));
			}
			case "belfry" -> {
				int bh = 5;
				int x0 = fp.minX(), x1 = fp.maxX(), z0 = fp.minZ(), z1 = fp.maxZ();
				for (int y = top + 1; y <= top + bh; y++) {
					for (int x = x0; x <= x1; x++) {
						for (int z = z0; z <= z1; z++) {
							if (!fp.edge(x, z)) continue;
							boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1) || fp.width() <= 5;
							boolean opening = !corner && y < top + bh && Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z)) == 0
									&& Math.abs(x - cx) + Math.abs(z - cz) < r + 0.5 - 1.0 && y > top + 1;
							c.set(x, y, z, opening ? Cell.air(Cell.OPEN) : Cell.of(corner ? Slot.QUOIN : Slot.WALL, Cell.BODY));
						}
					}
				}
				c.literal((int) Math.round(cx), top + bh - 1, (int) Math.round(cz), "bell[attachment=ceiling]", Cell.FEATURE);
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						if (fp.contains(x, z)) c.set(x, top + bh, z, Cell.of(Slot.WALL, Cell.BODY));
					}
				}
				RoofField f = new RoofField();
				f.spire(cx, cz, r - 0.5, top + bh + 1, v.i("pitch", 3), 1);
				f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
				int tip = f.at((int) Math.round(cx), (int) Math.round(cz));
				c.place("orn.finial", Xform.at((int) Math.round(cx), tip + 1, (int) Math.round(cz)), Spec.of(1, 1, 1).with("gold", true));
			}
			case "crystal" -> {
				// an open crown of posts round a floating crystal
				for (int x = fp.minX(); x <= fp.maxX(); x++) {
					for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
						if (fp.contains(x, z) && !fp.shell(x, z, 1)) c.set(x, top, z, Cell.of(Slot.FLOOR_ALT, Cell.BODY));
						if (!fp.edge(x, z)) continue;
						double a = Math.atan2(z - cz, x - cx);
						int seg = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * 8);
						if (seg % 2 == 0 && Math.abs(((a + Math.PI) / (2 * Math.PI) * 8) - seg - 0.5) < 0.3) {
							for (int y = top + 1; y <= top + 5; y++) c.set(x, y, z, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
							c.set(x, top + 6, z, Cell.of(Slot.MAGIC, Cell.DETAIL));
						} else {
							c.set(x, top + 1, z, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
						}
					}
				}
				int icx = (int) Math.round(cx), icz = (int) Math.round(cz);
				c.place("orn.crystal", Xform.at(icx, top + 4, icz), Spec.of(1, 1, 1).with("size", 3));
				c.set(icx, top + 1, icz, Cell.literal("end_rod", Cell.DETAIL));
			}
			default -> {
			}
		}
	}

	static void flag(Ctx c, int x, int y, int z, String colour) {
		for (int i = 0; i < 5; i++) c.decor(x, y + i, z, Cell.shape(Slot.WOOD, Shape.FENCE, Cell.DETAIL));
		c.decor(x + 1, y + 4, z, Cell.literal(colour + "_wool", Cell.DETAIL));
		c.decor(x + 2, y + 4, z, Cell.literal(colour + "_wool", Cell.DETAIL));
		c.decor(x + 1, y + 3, z, Cell.literal(colour + "_wool", Cell.DETAIL));
		c.decor(x + 2, y + 3, z, Cell.literal(colour + "_wool", Cell.DETAIL));
	}

	/** A small round turret corbelled out from the tower at angle a, from y0 to y1, with a cone. */
	static void turret(Building b, Ctx c, double cx, double cz, double r, double a, int y0, int y1, Rng rng) {
		double tr = 1.6;
		double tx = cx + Math.cos(a) * (r + 0.3), tz = cz + Math.sin(a) * (r + 0.3);
		Footprint fp = Footprint.circle(tx, tz, tr);
		// corbelling: a cone of upside-down stairs below
		for (int k = 0; k < 3; k++) {
			Footprint ring = Footprint.circle(tx, tz, tr - k * 0.6);
			for (int x = ring.minX(); x <= ring.maxX(); x++) {
				for (int z = ring.minZ(); z <= ring.maxZ(); z++) {
					if (ring.contains(x, z)) c.set(x, y0 - 1 - k, z, k == 0 ? Cell.stairs(Slot.TRIM, inward(x, z, tx, tz), true, Cell.DETAIL) : Cell.of(Slot.WALL, Cell.DETAIL));
				}
			}
		}
		for (int y = y0; y <= y1; y++) {
			for (int x = fp.minX(); x <= fp.maxX(); x++) {
				for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
					if (!fp.contains(x, z)) continue;
					boolean slit = y == y0 + 2 && fp.edge(x, z) && ((x + z) & 1) == 0;
					c.set(x, y, z, slit ? Cell.air(Cell.OPEN) : Cell.of(Slot.WALL, Cell.DETAIL));
				}
			}
		}
		RoofField f = new RoofField();
		f.cone(tx, tz, tr, y1 + 1, 2.2, 1);
		f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
		int tip = f.at((int) Math.round(tx), (int) Math.round(tz));
		if (tip != Integer.MIN_VALUE) c.place("orn.finial", Xform.at((int) Math.round(tx), tip + 1, (int) Math.round(tz)), Spec.of(1, 1, 1));
	}
}
