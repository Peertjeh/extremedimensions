package net.battlehub.arch.bldg;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Spec;

/**
 * A castle as a grammar: Castle → curtain wall + corner towers (+ towers along long walls) + gatehouse + keep +
 * great hall + courtyard. One tower type per castle (round, square or octagonal; crenellated, machicolated or
 * with cones), the keep set back and off-centre (a dominant mass with intentional asymmetry), the hall along a
 * side wall, stairs up to the wall walks, a well and trees in the yard. The gate faces local +z.
 */
public final class Castle implements Archetype {
	@Override
	public String id() {
		return "castle";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.FORTRESS);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		double s = b.randomness.spread;
		int w = (int) Math.round(r.range(34, 34 + 14 * s) * scale);
		int d = (int) Math.round(r.range(30, 30 + 12 * s) * scale);
		b.size(w | 1, d, 0);
		b.palette = Archetype.palette(b, district, r);
		b.choose("plan", r.chance(0.3) ? "octagon" : "rect");
		b.choose("towers", r.pick("round", "round", "square", "octagon"));
		b.choose("crown", r.pick("battlement", "machicolation", "cone", "cone"));
		b.choose("keep", r.chance(0.65) ? "square" : "round");
		b.choose("keepSide", r.chance(0.5) ? 1 : -1);
		b.choose("wall", r.range(9, 12));
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Brief br = b.brief;
		int hw = br.width / 2, d = br.depth;
		int wallTop = (Integer) br.choices.get("wall");
		Footprint curtain = "octagon".equals(br.choices.get("plan")) ? Footprint.octagon(-hw, -d + 1, hw, 0, 6) : Footprint.rect(-hw, -d + 1, hw, 0);
		b.volumes.add(new Volume("curtain", Volume.Role.CURTAIN, curtain, 0, wallTop, Volume.Roof.BATTLEMENT, 3, 1, 2));
		String tshape = (String) br.choices.get("towers");
		String crown = (String) br.choices.get("crown");
		int tr = br.width > 42 ? 5 : 4;
		int th = wallTop + rng.range(7, 10);
		List<int[]> corners = new ArrayList<>();
		if ("octagon".equals(br.choices.get("plan"))) {
			corners.add(new int[] {-hw + 3, -d + 4});
			corners.add(new int[] {hw - 3, -d + 4});
			corners.add(new int[] {-hw + 3, -3});
			corners.add(new int[] {hw - 3, -3});
		} else {
			corners.add(new int[] {-hw, -d + 1});
			corners.add(new int[] {hw, -d + 1});
			corners.add(new int[] {-hw, 0});
			corners.add(new int[] {hw, 0});
		}
		// Towers half way along long side walls
		if (d > 34) {
			corners.add(new int[] {-hw, -d / 2});
			corners.add(new int[] {hw, -d / 2});
		}
		if (br.width > 44) corners.add(new int[] {0, -d + 1});
		int i = 0;
		for (int[] p : corners) {
			Footprint fp = shape(tshape, p[0], p[1], tr);
			Volume t = new Volume("tower" + i++, Volume.Role.TOWER, fp, 0, th, crownRoof(crown), 2, Math.max(2, th / 6), 1);
			t.opt("crown", crown).opt("bands", 0).opt("batter", 2).opt("openings", "slit").opt("stair", "none");
			b.volumes.add(t);
		}
		// Gatehouse: two towers flanking the gate on the front wall
		int gw = 5;
		for (int side = -1; side <= 1; side += 2) {
			int gx = side * (gw / 2 + 3);
			Footprint fp = shape(tshape.equals("round") ? "round" : "square", gx, 0, 3);
			Volume g = new Volume("gate" + (side < 0 ? "L" : "R"), Volume.Role.GATEHOUSE, fp, 0, wallTop + 6, Volume.Roof.BATTLEMENT, 2, 2, 1);
			g.opt("crown", "machicolation").opt("openings", "slit").opt("stair", "none").opt("flag", true).opt("banner", "red");
			b.volumes.add(g);
		}
		// Keep: set back and off to one side
		int side = (Integer) br.choices.get("keepSide");
		int kh = Math.max(th + 8, rng.range(26, 34));
		Footprint keepFp;
		if ("round".equals(br.choices.get("keep"))) {
			int kr = 7;
			keepFp = Footprint.circle(side * (hw / 2 - 2), -d + 1 + kr + 4, kr + 0.4);
		} else {
			int kw = rng.range(6, 8), kd = rng.range(6, 9);
			int kx = side * (hw / 2 - 3), kz = -d + 1 + kd + 4;
			keepFp = Footprint.rect(kx - kw, kz - kd, kx + kw, kz + kd);
		}
		Volume keep = new Volume("keep", Volume.Role.KEEP, keepFp, 0, kh, Volume.Roof.BATTLEMENT, 2, Math.max(3, kh / 6), 0).front(Dir.SOUTH, true);
		keep.opt("crown", "machicolation").opt("turrets", "square".equals(br.choices.get("keep")) ? 4 : 0).opt("bands", 6).opt("batter", 3)
				.opt("openings", "glass").opt("flag", true).opt("banner", "red");
		b.volumes.add(keep);
		// Great hall along the opposite side wall
		int hallW = 10, hallL = Math.min(d - 14, rng.range(16, 22));
		int hx1 = -side * (hw - 4), hx0 = hx1 + side * hallW;
		int hz0 = -d + 1 + 6, hz1 = hz0 + hallL - 1;
		Footprint hallFp = Footprint.rect(Math.min(hx0, hx1), hz0, Math.max(hx0, hx1), hz1);
		Volume hall = new Volume("hall", Volume.Role.HALL, hallFp, 0, 9, Volume.Roof.GABLE, 1, 2, 2).front(side > 0 ? Dir.EAST : Dir.WEST, true);
		b.volumes.add(hall);
		b.floorY = 0;
	}

	static Volume.Roof crownRoof(String crown) {
		return crown.equals("cone") ? Volume.Roof.CONE : Volume.Roof.BATTLEMENT;
	}

	static Footprint shape(String kind, int cx, int cz, int r) {
		return switch (kind) {
			case "square" -> Footprint.rect(cx - r, cz - r, cx + r, cz + r);
			case "octagon" -> Footprint.octagon(cx, cz, r);
			default -> Footprint.circle(cx, cz, r + 0.4);
		};
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Volume curtain = b.volume("curtain");
		Ctx c = Assembler.ctx(b, "castle");
		int top = curtain.top;
		// Courtyard ground and the curtain's foundation
		Footprint yard = curtain.fp;
		for (int x = yard.minX(); x <= yard.maxX(); x++) {
			for (int z = yard.minZ(); z <= yard.maxZ(); z++) {
				if (yard.contains(x, z)) c.set(x, 0, z, Cell.of(Slot.PAVING, Cell.FILL));
			}
		}
		Assembler.foundation(b, curtain.fp, 0, 2);
		Fort.curtain(c, curtain.fp, 1, top, curtain.wall);
		// The gate through the front wall, under a gatehouse block
		int gw = 5;
		int gh = 7;
		Ctx gate = new Ctx(b, Xform.facing(Dir.SOUTH, -(gw + 2) / 2, 1, 1), rng.stream("gate"), rng.stream("decor:gate"));
		for (int x = -(gw / 2 + 1); x <= gw / 2 + 1; x++) {
			for (int y = 1; y <= top + 4; y++) {
				for (int z = -curtain.wall + 1; z <= 1; z++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
			}
		}
		gate.place("door.gate", Xform.ID, Spec.of(gw + 2, gh + 2, curtain.wall + 1));
		for (int x = -(gw / 2 + 1); x <= gw / 2 + 1; x++) {
			c.set(x, top + 5, 2, Cell.of(Slot.WALL, Cell.DETAIL));
			c.set(x, top + 4, 2, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			if ((x & 1) == 0) c.set(x, top + 6, 2, Cell.of(Slot.WALL, Cell.DETAIL));
			c.set(x, top + 4, 1, Cell.of(Slot.FLOOR, Cell.BODY));
		}
		c.place("orn.banner", Xform.facing(Dir.SOUTH, 0, top + 2, 2), Spec.of(1, 1, 1).with("colour", "red"));
		c.place("orn.banner", Xform.facing(Dir.SOUTH, 0, top + 1, 2), Spec.of(1, 1, 1).with("colour", "red"));
		// Towers: clear their insides through the curtain, then build them
		for (Volume v : b.volumes) {
			if (v.role != Volume.Role.TOWER && v.role != Volume.Role.GATEHOUSE && v.role != Volume.Role.KEEP) continue;
			if (v.role == Volume.Role.KEEP) continue;
			Assembler.clear(b, v.fp.inset(v.wall), 1, v.top);
			if (v.front == null) v.front = towardsCentre(v.fp, curtain.fp);
			Towers.build(b, v);
			Fort.walkDoors(c, v.fp, v.wall, top);
		}
		// Keep, hall
		Volume keep = b.volume("keep");
		keep.front = keep.fp.centerZ() < curtain.fp.centerZ() ? Dir.SOUTH : Dir.NORTH;
		Towers.build(b, keep);
		Volume hall = b.volume("hall");
		Assembler.foundation(b, hall.fp, 0, 1);
		Facade.Rules hr = Assembler.rules(b, hall);
		hr.windows = new String[] {"window.round", "window.round"};
		hr.door = "door.double";
		hr.pier = "pier.buttress";
		hr.pierDepth = 1;
		Assembler.walls(b, hall, hr);
		Assembler.floors(b, hall, "border");
		Assembler.Roofs roofs = new Assembler.Roofs();
		roofs.gable(hall.fp, hall.top, 1, 1, hall.fp.depth() >= hall.fp.width());
		int chx = (int) Math.round(hall.fp.centerX());
		roofs.mask(Footprint.rect(chx, hall.fp.minZ() + 2, chx, hall.fp.minZ() + 2));
		Assembler.finishRoofs(b, roofs);
		c.place("orn.chimney", Xform.at(chx, 1, hall.fp.minZ() + 2), Spec.of(1, hall.top + Math.min(hall.fp.width(), hall.fp.depth()) / 2 + 3, 1));
		Assembler.interior(b, hall, "int.hall", hall.y0, 5);
		// Stairs up to the wall walk along the inside of the back wall and one side wall
		Footprint inner = curtain.fp.inset(curtain.wall);
		int sz = inner.minZ();
		Fort.stairRun(c, inner.maxX() - top - 2, 1, sz, Dir.EAST, Dir.SOUTH, top - 1, 2);
		Fort.stairRun(c, inner.minX(), 1, inner.maxZ() - 2 - top, Dir.SOUTH, Dir.EAST, top - 1, 2);
		// The yard: a path from the gate, a well, trees, lamp posts
		for (int z = 0; z >= (int) Math.round(keep.fp.centerZ()); z--) {
			for (int x = -2; x <= 2; x++) c.set(x, 0, z, Cell.of(Slot.PATH, Cell.DETAIL).flag(Cell.KEEP));
		}
		int wx = -(Integer) b.brief.choices.get("keepSide") * 3, wz = (int) Math.round(curtain.fp.centerZ()) + 3;
		Fort.well(c, wx, 1, wz);
		Rng trees = b.rng.stream("decor:trees");
		for (int t = 0; t < 3; t++) {
			int tx = trees.range(inner.minX() + 2, inner.maxX() - 2), tz = trees.range(inner.minZ() + 2, inner.maxZ() - 4);
			if (occupied(b, tx, tz, 3)) continue;
			c.place("orn.tree", Xform.at(tx, 1, tz), Spec.of(1, 5, 1).with("height", trees.range(4, 6)).with("crown", 2.3));
		}
		for (int z = -4; z >= -8; z -= 4) {
			c.place("orn.lamppost", Xform.at(-3, 1, z), Spec.of(1, 3, 1));
			c.place("orn.lamppost", Xform.at(3, 1, z), Spec.of(1, 3, 1));
		}
	}

	static boolean occupied(Building b, int x, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int y = 1; y <= 3; y++) {
					if (b.plan.filled(x + dx, y, z + dz)) return true;
				}
			}
		}
		return false;
	}

	static Dir towardsCentre(Footprint fp, Footprint whole) {
		double dx = whole.centerX() - fp.centerX(), dz = whole.centerZ() - fp.centerZ();
		return Dir.of((int) Math.signum(Math.round(dx)), (int) Math.signum(Math.round(dz)));
	}
}
