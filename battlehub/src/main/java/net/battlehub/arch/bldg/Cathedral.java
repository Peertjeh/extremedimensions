package net.battlehub.arch.bldg;

import java.util.List;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.geom.ArchCurve;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.RoofField;
import net.battlehub.arch.mod.Spec;

/**
 * A gothic cathedral as a grammar:
 * <pre>
 * Cathedral → West front + Nave + Aisles? + Transept? + Choir + Apse + Roofs + Buttresses + Interior
 * West front → Portal + Rose window + Gable + (twin towers | one tower | a screen with turrets)
 * Nave → Bays × (arcade arch + clerestory window) + vault ribs
 * Aisle → Bays × (window + buttress with pinnacle) + flying buttresses to the clerestory
 * </pre>
 * Not every cathedral has towers, aisles or a transept; spires, crossing towers and apse forms vary. The west
 * front faces local +z; the axis runs along z, symmetric about x = 0.
 */
public final class Cathedral implements Archetype {
	@Override
	public String id() {
		return "cathedral";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.GOTHIC);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		int bays = (int) Math.round(r.range(5, 7) * scale);
		int ni = r.pick(9, 11, 11, 13);
		int h = ni * 2 + r.range(-1, 3);
		b.size(ni + 4, bays * style.bay, h);
		b.palette = Archetype.palette(b, district, r);
		b.choose("bays", bays);
		b.choose("nave", ni);
		b.choose("aisles", r.chance(0.85));
		b.choose("front", r.chance(0.55) ? "twin" : r.chance(0.45) ? "single" : "screen");
		b.choose("spires", r.chance(0.65));
		b.choose("transept", r.chance(0.7));
		b.choose("crossing", r.pick("fleche", "lantern", "none"));
		b.choose("apse", r.chance(0.75) ? "polygon" : "flat");
		return b;
	}

	// Planned dimensions, shared by plan and assemble
	private static final class Dim {
		int hw, nw, t = 2, aw, ha, h, bw, bays, front, zNaveEnd, zCross0, zChoirEnd, ta, tw;
		boolean aisles, transept, apse;
		String frontKind;
	}

	private static Dim dim(Building b) {
		Brief br = b.brief;
		Dim d = new Dim();
		d.nw = (Integer) br.choices.get("nave") + 4;
		d.hw = d.nw / 2;
		d.h = br.height;
		d.bw = br.style.bay;
		d.bays = (Integer) br.choices.get("bays");
		d.aisles = (Boolean) br.choices.get("aisles");
		d.aw = d.aisles ? 7 : 0;
		d.ha = Math.max(10, d.h / 2 + 1);
		d.frontKind = (String) br.choices.get("front");
		d.tw = d.aisles ? d.aw + 3 : 7;
		d.front = d.frontKind.equals("screen") ? 0 : d.tw;
		d.zNaveEnd = -(d.front + d.bays * d.bw);
		d.transept = (Boolean) br.choices.get("transept");
		d.ta = 6;
		d.zCross0 = d.transept ? d.zNaveEnd - d.nw + 1 : d.zNaveEnd + 1;
		d.zChoirEnd = d.zCross0 - 2 * d.bw;
		d.apse = "polygon".equals(br.choices.get("apse"));
		return d;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Dim d = dim(b);
		b.floorY = 0;
		Volume nave = new Volume("nave", Volume.Role.NAVE, Footprint.rect(-d.hw, d.zChoirEnd, d.hw, 0), 0, d.h, Volume.Roof.STEEP_GABLE, d.t, 1, 1)
				.front(Dir.SOUTH, true);
		b.volumes.add(nave);
		if (d.aisles) {
			int z1 = d.front > 0 ? -d.front : 0;
			for (int s = -1; s <= 1; s += 2) {
				int xa = s * (d.hw + 1), xb = s * (d.hw + d.aw);
				b.volumes.add(new Volume(s < 0 ? "aisleW" : "aisleE", Volume.Role.AISLE, Footprint.rect(Math.min(xa, xb), d.zChoirEnd, Math.max(xa, xb), z1), 0,
						d.ha, Volume.Roof.FLAT, d.t, 1, 2));
			}
		}
		if (d.transept) {
			int ext = d.hw + d.aw + d.ta;
			b.volumes.add(new Volume("transept", Volume.Role.TRANSEPT, Footprint.rect(-ext, d.zCross0, ext, d.zNaveEnd), 0, d.h, Volume.Roof.STEEP_GABLE, d.t, 1, 1));
		}
		if (d.apse) {
			int zc = d.zChoirEnd;
			Footprint oct = Footprint.octagon(0, zc, d.hw);
			b.volumes.add(new Volume("apse", Volume.Role.APSE, Footprint.clip(oct, (x, z) -> z <= zc), 0, d.h, Volume.Roof.CONE, d.t, 1, 2));
		}
		int th = d.h + rng.range(14, 22);
		if (d.frontKind.equals("twin")) {
			for (int s = -1; s <= 1; s += 2) {
				int xa = s * (d.hw - 1), xb = s * (d.hw - 1 + d.tw - 1);
				Volume t = new Volume(s < 0 ? "towerW" : "towerE", Volume.Role.FACADE_TOWER, Footprint.rect(Math.min(xa, xb), -(d.tw - 1), Math.max(xa, xb), 1), 0, th,
						Volume.Roof.SPIRE, d.t, 4, 0);
				b.volumes.add(t);
			}
		} else if (d.frontKind.equals("single")) {
			int hx = d.hw + 1;
			b.volumes.add(new Volume("tower", Volume.Role.FACADE_TOWER, Footprint.rect(-hx, -(d.tw + 1), hx, 1), 0, th + 6, Volume.Roof.SPIRE, d.t, 4, 0));
		}
		String crossing = (String) b.brief.choices.get("crossing");
		if (d.transept && crossing.equals("lantern")) {
			b.volumes.add(new Volume("crossing", Volume.Role.CROSSING_TOWER, Footprint.rect(-d.hw, d.zCross0, d.hw, d.zNaveEnd), d.h, d.h + 2 * d.hw + 9,
					Volume.Roof.SPIRE, d.t, 2, d.frontKind.equals("screen") ? 0 : 1));
		}
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Dim d = dim(b);
		Ctx c = Assembler.ctx(b, "cathedral");
		for (Volume v : b.volumes) Assembler.foundation(b, v.fp, 0, 2);
		Volume nave = b.volume("nave");
		Assembler.Roofs roofs = new Assembler.Roofs();
		// --- Nave and choir: side walls with an arcade below and a clerestory above
		naveSides(b, c, d, rng);
		Assembler.hollow(b, nave.fp.inset(d.t), 1, d.h);
		Assembler.floor(b, nave.fp.inset(d.t), 0, "aisle");
		roofs.gable(nave.fp, d.h, 2, 1, true);
		// --- Aisles
		if (d.aisles) {
			for (String id : new String[] {"aisleW", "aisleE"}) {
				Volume a = b.volume(id);
				Dir out = id.equals("aisleW") ? Dir.WEST : Dir.EAST;
				aisle(b, d, a, out, rng);
				if (d.transept) {
					// the aisle roof stops at the transept and starts again behind it
					roofs.field.shed(a.fp.minX(), d.zNaveEnd + 1, a.fp.maxX(), a.fp.maxZ(), out, d.ha, 1, 1);
					roofs.field.shed(a.fp.minX(), a.fp.minZ(), a.fp.maxX(), d.zCross0 - 1, out, d.ha, 1, 1);
				} else {
					roofs.field.shed(a.fp.minX(), a.fp.minZ(), a.fp.maxX(), a.fp.maxZ(), out, d.ha, 1, 1);
				}
			}
		}
		// --- Transept
		if (d.transept) {
			Volume tr = b.volume("transept");
			transept(b, d, tr, rng);
			roofs.gable(tr.fp, d.h, 2, 1, false);
		}
		// --- Apse
		if (d.apse) {
			Volume ap = b.volume("apse");
			apse(b, c, d, ap);
			roofs.field.cone(0, d.zChoirEnd, d.hw + 0.4, d.h, 2, 1);
		} else {
			endWall(b, d);
		}
		// --- West front and towers
		westFront(b, c, d, rng);
		for (Volume v : b.volumes) {
			if (v.role == Volume.Role.FACADE_TOWER || v.role == Volume.Role.CROSSING_TOWER) {
				roofs.mask(v.fp);
				gothicTower(b, v, (Boolean) b.brief.choices.get("spires"), v.role == Volume.Role.CROSSING_TOWER, rng);
			}
		}
		if (d.frontKind.equals("screen")) screenTurrets(b, c, d);
		// --- Flying buttresses
		if (d.aisles && d.h >= 18) flyers(b, c, d);
		Assembler.finishRoofs(b, roofs);
		if (d.transept && "fleche".equals(b.brief.choices.get("crossing"))) fleche(b, c, d);
		// --- Interior
		vault(b, c, d);
		Assembler.interior(b, nave, "int.nave", 0, d.h - 2);
	}

	/** Bays along the nave: the spans between the arcade piers, the same rhythm on both sides. */
	private static int[][] naveBays(Dim d) {
		int len = -d.zChoirEnd - d.front + 1;
		return Facade.layout(len, d.bw - 1, 1, 1);
	}

	private static void naveSides(Building b, Ctx c, Dim d, Rng rng) {
		Volume nave = b.volume("nave");
		for (Facade.Run run : Facade.runs(nave.fp, 0)) {
			if (run.outward() != Dir.EAST && run.outward() != Dir.WEST) continue;
			Ctx rc = new Ctx(b, run.frame(), rng.stream("nave" + run.outward()), rng.stream("decor:nave" + run.outward()));
			int len = run.length();
			rc.place("wall.body", Xform.ID, Spec.of(len, d.h, d.t).with("bands", new int[] {d.ha + 1}).with("cornice", true).with("plinth", d.aisles ? 0 : 2));
			// the run starts at the west end for the east side (frame x grows towards -z) and at the east end for
			// the west side: work in nave coordinates (distance from the west front) instead
			boolean fromWest = run.outward() == Dir.EAST;
			int[][] bays = naveBays(d);
			for (int[] bay : bays) {
				int start = d.front + bay[0], w = bay[1];
				int zWest = -start, zEast = -(start + w - 1);
				boolean crossing = d.transept && zEast <= d.zNaveEnd && zWest >= d.zCross0;
				boolean inCrossing = d.transept && zWest < d.zNaveEnd + 1 && zEast > d.zCross0 - 1;
				int lx = fromWest ? start : len - start - w;
				if (inCrossing || crossing) continue;
				if (d.aisles) {
					int jamb = Math.max(3, d.ha - 3 - ArchCurve.POINTED.rise(w));
					int[] hs = rc.archOpening(lx, 1, w, jamb, ArchCurve.POINTED, -(d.t - 1), 0, Cell.air(Cell.OPEN));
					rc.archRing(lx, 1, hs, 1, Slot.ARCH, -(d.t - 1), 0, Cell.DETAIL);
					// clerestory above the aisle roof
					int cy = d.ha + 3;
					int chh = d.h - cy - 1;
					if (chh >= 5) rc.place(w >= 5 ? "window.twin" : "window.lancet", Xform.at(lx, cy, 0), Spec.of(w, chh, d.t));
				} else {
					rc.place("window.lancet", Xform.at(lx, 3, 0), Spec.of(w, d.h - 6, d.t));
				}
			}
			// piers: columns on the inner face with capitals
			for (int k = 0; k <= bays.length; k++) {
				int start = k < bays.length ? d.front + bays[k][0] - 1 : d.front + bays[bays.length - 1][0] + bays[bays.length - 1][1];
				int lx = fromWest ? start : len - start - 1;
				if (lx < 0 || lx >= len) continue;
				for (int y = 1; y < d.ha - 2; y++) rc.set(lx, y, -(d.t - 1), Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
				rc.decor(lx, d.ha - 2, -d.t, Cell.stairs(Slot.TRIM, Dir.SOUTH, true, Cell.DETAIL));
				// a shaft running up to the vault
				if (d.aisles) for (int y = d.ha - 1; y < d.h - 1; y++) rc.set(lx, y, -d.t, Cell.shape(Slot.PILLAR, Shape.WALL, Cell.DETAIL).flag(Cell.INSIDE));
			}
		}
		// Crossing: open the nave walls to the transept
		if (d.transept) {
			for (int s = -1; s <= 1; s += 2) {
				for (int k = 0; k < d.t; k++) {
					int x = s * (d.hw - k);
					for (int z = d.zCross0 + 1; z <= d.zNaveEnd - 1; z++) {
						for (int y = 1; y < d.h - 2; y++) c.force(x, y, z, Cell.air(Cell.OPEN));
					}
				}
			}
		}
	}

	private static void aisle(Building b, Dim d, Volume a, Dir out, Rng rng) {
		Facade.Rules r = new Facade.Rules();
		r.bay = d.bw - 1;
		r.pier = "pier.buttress";
		r.pierDepth = 3;
		r.pinnacles = true;
		r.windows = new String[] {"window.lancet"};
		r.floors = new int[] {0};
		r.quoins = false;
		r.cornice = true;
		r.plinth = 2;
		Facade.Run outer = null;
		for (Facade.Run run : Facade.runs(a.fp, 0)) {
			if (run.outward() == out) outer = run;
		}
		// Outer wall: windows and buttresses in the nave's rhythm
		Ctx rc = new Ctx(b, outer.frame(), rng.stream("aisle" + out), rng.stream("decor:aisle" + out));
		int len = outer.length();
		rc.place("wall.body", Xform.ID, Spec.of(len, d.ha + 1, d.t).with("plinth", 2).with("cornice", true));
		int[][] bays = naveBays(d);
		boolean fromWest = out == Dir.EAST;
		int zTop = a.fp.maxZ();
		for (int k = 0; k < bays.length; k++) {
			int start = d.front + bays[k][0], w = bays[k][1];
			int zWest = -start, zEast = -(start + w - 1);
			if (zWest > zTop) continue;
			if (d.transept && zWest >= d.zCross0 && zEast <= d.zNaveEnd) continue;
			int lx = fromWest ? (zTop - zWest) : len - 1 - (zTop - zEast);
			if (lx < 0 || lx + w > len) continue;
			rc.place("window.lancet", Xform.at(lx, 3, 0), Spec.of(w, d.ha - 3, d.t).with("glass", k % 2 * 2));
			int px = fromWest ? lx + w : lx - 1;
			if (px > 0 && px < len - 1) {
				rc.place("pier.buttress", Xform.at(px, 0, 0), Spec.of(1, d.ha + 1, 3).with("top", d.ha + 1).with("pinnacle", true));
			}
		}
		// End walls of the aisle (east, and west when no tower stands there)
		for (Facade.Run run : Facade.runs(a.fp, 0)) {
			if (run.outward() == Dir.NORTH || run.outward() == Dir.SOUTH && d.front == 0) {
				Ctx ec = new Ctx(b, run.frame(), rng.stream("aisleEnd" + out + run.outward()), rng.stream("decor:aisleEnd" + out + run.outward()));
				ec.place("wall.body", Xform.ID, Spec.of(run.length(), d.ha + 1, d.t).with("plinth", 2).with("cornice", true));
				if (run.outward() == Dir.SOUTH) {
					ec.place("door.double", Xform.at(1, 1, 0), Spec.of(run.length() - 2, d.ha - 2, d.t));
				} else {
					ec.place("window.lancet", Xform.at(1, 3, 0), Spec.of(run.length() - 2, d.ha - 3, d.t));
				}
			}
		}
		Assembler.hollow(b, a.fp.inset(1), 1, d.ha);
		Footprint floorFp = Footprint.rect(Math.min(a.fp.minX(), a.fp.maxX()), a.fp.minZ() + d.t, a.fp.maxX(), a.fp.maxZ() - (d.front == 0 ? d.t : 0));
		Assembler.floor(b, floorFp, 0, "checker");
	}

	private static void transept(Building b, Dim d, Volume tr, Rng rng) {
		for (Facade.Run run : Facade.runs(tr.fp, 0)) {
			Ctx rc = new Ctx(b, run.frame(), rng.stream("tr" + run.outward()), rng.stream("decor:tr" + run.outward()));
			int len = run.length();
			if (run.outward() == Dir.EAST || run.outward() == Dir.WEST) {
				// The transept fronts: a portal, a rose window, corner buttresses
				rc.place("wall.body", Xform.ID, Spec.of(len, d.h, d.t).with("plinth", 2).with("cornice", true).with("bands", new int[] {d.ha + 1}));
				int pw = Math.min(len - 4, 7);
				rc.place("door.double", Xform.at((len - pw) / 2, 1, 0), Spec.of(pw, Math.min(8, d.ha - 2), d.t));
				int rs = Math.min(len - 4, d.h - d.ha - 2);
				if (rs >= 7) rc.place("window.rose", Xform.at((len - rs) / 2, d.ha, 0), Spec.of(rs, rs, d.t).with("glass", 2));
				rc.place("pier.buttress", Xform.at(0, 0, 0), Spec.of(1, d.h, 3).with("top", d.h).with("pinnacle", true));
				rc.place("pier.buttress", Xform.at(len - 1, 0, 0), Spec.of(1, d.h, 3).with("top", d.h).with("pinnacle", true));
			} else {
				// Side walls of the arms (outside the aisles): one tall lancet per arm
				rc.place("wall.body", Xform.ID, Spec.of(len, d.h, d.t).with("plinth", 2).with("cornice", true));
				int armLen = d.ta + 1;
				for (int s = 0; s < 2; s++) {
					int lx = s == 0 ? 1 : len - 1 - armLen;
					rc.place("window.lancet", Xform.at(lx, 3, 0), Spec.of(armLen, d.h - 5, d.t));
				}
				// open towards the nave and aisles
				int inner = d.hw + d.aw;
				for (int x = -inner; x <= inner; x++) {
					int lx = run.outward() == Dir.SOUTH ? x - tr.fp.minX() : tr.fp.maxX() - x;
					for (int k = 0; k < d.t; k++) {
						for (int y = 1; y < (Math.abs(x) <= d.hw ? d.h - 2 : d.ha); y++) rc.force(lx, y, -k, Cell.air(Cell.OPEN));
					}
				}
			}
		}
		Assembler.hollow(b, tr.fp.inset(d.t), 1, d.h);
		Assembler.floor(b, tr.fp.inset(d.t), 0, "checker");
		// the aisles do not run on through the transept: clear what of them stands inside it
		int inner = d.hw + d.aw;
		Assembler.clear(b, Footprint.rect(-inner, d.zCross0 + d.t, inner, d.zNaveEnd - d.t), 1, d.h - 1);
	}

	private static void apse(Building b, Ctx c, Dim d, Volume ap) {
		Footprint fp = ap.fp;
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.contains(x, z)) continue;
				boolean shell = false;
				for (Dir dd : Dir.values()) {
					int nx = x + dd.dx, nz = z + dd.dz;
					if (!fp.contains(nx, nz) && nz <= d.zChoirEnd) shell = true;
					if (!fp.contains(nx, nz) && nz <= d.zChoirEnd) shell = true;
				}
				boolean shell2 = !fp.inset(d.t).contains(x, z) && z < d.zChoirEnd;
				for (int y = 0; y <= d.h; y++) {
					if (shell || shell2) {
						c.set(x, y, z, Cell.of(y < 2 ? Slot.FOUNDATION : Slot.WALL, Cell.BODY));
					} else if (y == 0) {
						c.set(x, y, z, Cell.of(Slot.FLOOR_ALT, Cell.FILL).flag(Cell.INSIDE));
					} else {
						c.set(x, y, z, Cell.air(Cell.FILL));
					}
				}
			}
		}
		// Tall lancets round the apse, buttresses between them
		for (int k = 0; k < 5; k++) {
			// round the far (north, -z) side of the apse
			double a = -Math.PI / 2 + (k - 2) * Math.PI / 5.2;
			Towers.opening(c, fp, 0, d.zChoirEnd, a, 4, d.h - 8, d.t, "lancet");
			double ab = a + Math.PI / 10.4;
			int bx = (int) Math.round(Math.cos(ab) * (d.hw + 0.5)), bz = d.zChoirEnd + (int) Math.round(Math.sin(ab) * (d.hw + 0.5));
			if (bz < d.zChoirEnd && k < 4) {
				for (int y = -1; y < d.h - 2; y++) c.set(bx, y, bz, Cell.of(Slot.WALL, Cell.DETAIL));
				c.decor(bx, d.h - 2, bz, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
			}
		}
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (fp.edge(x, z) && z < d.zChoirEnd) c.set(x, d.h, z, Cell.of(Slot.BAND, Cell.DETAIL));
			}
		}
	}

	private static void endWall(Building b, Dim d) {
		Volume nave = b.volume("nave");
		for (Facade.Run run : Facade.runs(nave.fp, 0)) {
			if (run.outward() != Dir.NORTH) continue;
			Ctx rc = new Ctx(b, run.frame(), b.rng.stream("end"), b.rng.stream("decor:end"));
			rc.place("wall.body", Xform.ID, Spec.of(run.length(), d.h, d.t).with("plinth", 2).with("cornice", true));
			int ww = run.length() - 4;
			rc.place(ww >= 5 ? "window.twin" : "window.lancet", Xform.at(2, 3, 0), Spec.of(ww, d.h - 4, d.t));
		}
	}

	/** The west front of the nave: portal, rose window and the gable's pinnacles. */
	private static void westFront(Building b, Ctx c, Dim d, Rng rng) {
		Volume nave = b.volume("nave");
		Facade.Run front = null;
		for (Facade.Run run : Facade.runs(nave.fp, 0)) {
			if (run.outward() == Dir.SOUTH) front = run;
		}
		Ctx rc = new Ctx(b, front.frame(), rng.stream("west"), rng.stream("decor:west"));
		int len = front.length();
		rc.place("wall.body", Xform.ID, Spec.of(len, d.h, d.t).with("plinth", 2).with("cornice", true).with("bands", new int[] {d.ha + 1}));
		int free = d.frontKind.equals("twin") ? len - 6 : len - 2;
		int pw = Math.min(free, 11);
		if (pw % 2 == 0) pw--;
		int ph = Math.max(10, d.ha + 2);
		rc.place("door.portal", Xform.at((len - pw) / 2, 1, 0), Spec.of(pw, ph, d.t));
		int portalTop = 1 + net.battlehub.arch.mod.Doors.Portal.height(pw, ph);
		// The rose may rise into the gable: the roof's end wall fills in round it later
		int ceiling = d.h + d.hw - 1;
		int rs = Math.min(free, ceiling - portalTop);
		if (rs % 2 == 0) rs--;
		if (rs >= 7) {
			rc.place("window.rose", Xform.at((len - rs) / 2, portalTop + Math.max(0, (ceiling - portalTop - rs) / 2), 0), Spec.of(rs, rs, d.t));
		} else if (d.h - portalTop >= 6) {
			rc.place("window.twin", Xform.at((len - Math.min(free, 7)) / 2, portalTop, 0), Spec.of(Math.min(free, 7), d.h - portalTop, d.t));
		}
		if (!d.frontKind.equals("twin")) {
			for (int s = 0; s < 2; s++) {
				int x = s == 0 ? 0 : len - 1;
				rc.place("pier.buttress", Xform.at(x, 0, 0), Spec.of(1, d.h, 3).with("top", d.h).with("pinnacle", true));
			}
		}
	}

	/**
	 * A square gothic tower: stacked stages of lancets (the top one a tall belfry), corner buttresses with
	 * pinnacles, then an octagonal spire with corner spirelets, or a flat top with tall pinnacles.
	 */
	private static void gothicTower(Building b, Volume v, boolean spire, boolean crossing, Rng rng) {
		Ctx c = new Ctx(b, Xform.ID, rng.stream("gt" + v.id), rng.stream("decor:gt" + v.id));
		Footprint fp = v.fp;
		// a crossing tower rises from the eaves of the nave: a plain stage through the roofs, then the lantern
		int y0 = crossing ? v.y0 : 0;
		int stages = crossing ? 2 : 4;
		int sh = (v.top - y0) / stages;
		Assembler.clear(b, fp.inset(v.wall), y0 + 1, v.top - 1);
		for (Facade.Run run : Facade.runs(fp, y0)) {
			Ctx rc = new Ctx(b, run.frame(), rng.stream("gtr" + v.id + run.outward()), rng.stream("decor:gtr" + v.id + run.outward()));
			int len = run.length();
			int h = v.top - y0 + 1;
			int[] bands = new int[stages - 1];
			for (int i = 1; i < stages; i++) bands[i - 1] = i * sh;
			rc.place("wall.body", Xform.ID, Spec.of(len, h, v.wall).with("plinth", crossing ? 0 : 2).with("bands", bands).with("cornice", true));
			for (int i = 1; i < stages; i++) {
				int fy = i * sh;
				boolean belfry = i == stages - 1;
				int ww = len - 4;
				String win = belfry ? (ww >= 5 ? "window.twin" : "window.lancet") : i == 1 && !crossing ? "window.oculus" : "window.lancet";
				int wh = belfry ? sh - 1 : sh - 2;
				if (win.equals("window.oculus")) {
					rc.place(win, Xform.at((len - 5) / 2, fy + 1, 0), Spec.of(5, 5, v.wall));
				} else {
					rc.place(win, Xform.at(2, fy + 1, 0), Spec.of(ww, wh, v.wall));
				}
			}
			if (!crossing) {
				rc.place("pier.buttress", Xform.at(0, 0, 0), Spec.of(1, h, 2).with("top", h - 2).with("pinnacle", false));
				rc.place("pier.buttress", Xform.at(len - 1, 0, 0), Spec.of(1, h, 2).with("top", h - 2).with("pinnacle", false));
			}
			// the side towards the nave opens at the ground for the side door
			if (!crossing && run.outward() == Dir.SOUTH) rc.place("door.double", Xform.at((len - 4) / 2, 1, 0), Spec.of(4, 6, v.wall));
		}
		// Floors inside
		for (int i = 1; i < stages; i++) Assembler.floor(b, fp.inset(v.wall), y0 + i * sh, "plain");
		int top = v.top;
		double cx = fp.centerX(), cz = fp.centerZ();
		double r = fp.width() / 2.0;
		// corner pinnacles: small octagonal shafts with spirelets
		int[][] corners = {{fp.minX() + 1, fp.minZ() + 1}, {fp.maxX() - 1, fp.minZ() + 1}, {fp.minX() + 1, fp.maxZ() - 1}, {fp.maxX() - 1, fp.maxZ() - 1}};
		for (int[] k : corners) {
			int ph = spire ? 3 : 5;
			Footprint pin = Footprint.octagon(k[0], k[1], 1);
			for (int y = top + 1; y <= top + ph; y++) {
				for (int px = pin.minX(); px <= pin.maxX(); px++) {
					for (int pz = pin.minZ(); pz <= pin.maxZ(); pz++) {
						if (pin.contains(px, pz)) c.set(px, y, pz, Cell.of(Slot.QUOIN, Cell.DETAIL));
					}
				}
			}
			RoofField pf = new RoofField();
			pf.spire(k[0], k[1], 1.2, top + ph + 1, 3, 0);
			pf.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
			c.place("orn.finial", Xform.at(k[0], pf.at(k[0], k[1]) + 1, k[1]), Spec.of(1, 1, 1));
		}
		// parapet between them
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.contains(x, z)) continue;
				c.set(x, top, z, Cell.of(fp.edge(x, z) ? Slot.WALL : Slot.FLOOR, Cell.BODY));
				if (fp.edge(x, z)) {
					c.set(x, top + 1, z, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				}
			}
		}
		if (spire) {
			RoofField f = new RoofField();
			double sr = v.fp.width() > 11 ? r - 3 : r - 2;
			f.spire(cx, cz, sr, top + 1, v.fp.width() > 11 ? 5 : 4, 0);
			f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
			int tip = f.at((int) Math.round(cx), (int) Math.round(cz));
			c.place("orn.finial", Xform.at((int) Math.round(cx), tip + 1, (int) Math.round(cz)), Spec.of(1, 1, 1).with("gold", true));
		}
	}

	/** Slender turrets beside a screen front (no towers). */
	private static void screenTurrets(Building b, Ctx c, Dim d) {
		for (int s = -1; s <= 1; s += 2) {
			int x = s * (d.hw + 1);
			Footprint fp = Footprint.octagon(x, 0, 2);
			Assembler.clear(b, fp.inset(1), 1, d.h + 6);
			for (int y = 0; y <= d.h + 6; y++) {
				for (int px = fp.minX(); px <= fp.maxX(); px++) {
					for (int pz = fp.minZ(); pz <= fp.maxZ(); pz++) {
						if (fp.contains(px, pz)) c.set(px, y, pz, Cell.of(fp.edge(px, pz) ? Slot.QUOIN : Slot.WALL, Cell.BODY));
					}
				}
			}
			RoofField f = new RoofField();
			f.spire(x, 0, 2, d.h + 7, 3, 1);
			f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
			c.place("orn.finial", Xform.at(x, f.at(x, 0) + 1, 0), Spec.of(1, 1, 1));
		}
	}

	/** Flying buttresses from the aisle piers over the aisle roofs to the clerestory, one per pier. */
	private static void flyers(Building b, Ctx c, Dim d) {
		int[][] bays = naveBays(d);
		int ya = d.ha + 4, yn = d.h - 3;
		for (int k = 0; k < bays.length - 1; k++) {
			int z = -(d.front + bays[k][0] + bays[k][1]);
			if (d.transept && z >= d.zCross0 - 1 && z <= d.zNaveEnd + 1) continue;
			if (d.front > 0 && z > -d.front) continue;
			for (int s = -1; s <= 1; s += 2) {
				int xo = s * (d.hw + d.aw + 2);
				// the pier rising above the aisle roof, with a pinnacle
				for (int y = d.ha; y <= ya + 1; y++) {
					c.set(xo, y, z, Cell.of(Slot.WALL, Cell.DETAIL));
					c.set(xo - s, y, z, Cell.of(Slot.WALL, Cell.DETAIL));
				}
				c.set(xo, ya + 2, z, Cell.of(Slot.QUOIN, Cell.DETAIL));
				c.decor(xo, ya + 3, z, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
				c.decor(xo, ya + 4, z, Cell.literal("end_rod", Cell.DETAIL));
				// the flying arch: a sloping top, an arched underside
				int xa = xo - 2 * s, xn = s * (d.hw + 1);
				int span = Math.abs(xn - xa);
				for (int i = 0; i <= span; i++) {
					int x = xa + (xn - xa) * i / Math.max(1, span) ;
					double t = (double) i / Math.max(1, span);
					int ytop = (int) Math.round(ya + (yn - ya) * t);
					double sag = Math.sin(Math.PI * t) * Math.min(3, span / 3.0);
					int ybot = (int) Math.round(ya - 1 + (yn - 2 - (ya - 1)) * t - sag);
					for (int y = ybot; y <= ytop; y++) {
						Cell cell = y == ytop ? Cell.stairs(Slot.WALL, s < 0 ? Dir.EAST : Dir.WEST, false, Cell.DETAIL)
								: y == ybot && ybot < ytop - 1 ? Cell.stairs(Slot.WALL, s < 0 ? Dir.WEST : Dir.EAST, true, Cell.DETAIL) : Cell.of(Slot.WALL, Cell.DETAIL);
						c.set(x, y, z, cell);
					}
				}
			}
		}
	}

	/** A slender octagonal spire on the roof ridge over the crossing. */
	private static void fleche(Building b, Ctx c, Dim d) {
		int cz = (d.zCross0 + d.zNaveEnd) / 2;
		int ridge = d.h + d.hw * 2 + 1;
		for (int y = ridge - 3; y <= ridge + 2; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					boolean corner = Math.abs(x) == 1 && Math.abs(z - cz) == 1;
					c.set(x, y, z, corner ? Cell.log(Slot.PILLAR, 'y', Cell.DETAIL) : y > ridge ? Cell.air(Cell.DETAIL) : Cell.of(Slot.ROOF, Cell.DETAIL));
				}
			}
		}
		RoofField f = new RoofField();
		f.spire(0, cz, 1.6, ridge + 3, 4, 0);
		f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.FEATURE);
		c.place("orn.finial", Xform.at(0, f.at(0, cz) + 1, cz), Spec.of(1, 1, 1).with("gold", true));
	}

	/** Pointed transverse ribs across the nave at every pier, under a flat ceiling. */
	private static void vault(Building b, Ctx c, Dim d) {
		int ni = d.nw - 2 * d.t;
		int[] hs = ArchCurve.POINTED.heights(ni);
		int rise = ArchCurve.POINTED.rise(ni);
		int spring = d.h - 1 - rise;
		int[][] bays = naveBays(d);
		for (int k = 0; k < bays.length - 1; k++) {
			int z = -(d.front + bays[k][0] + bays[k][1]);
			for (int i = 0; i < ni; i++) {
				int x = -d.hw + d.t + i;
				for (int y = spring + hs[i]; y < d.h; y++) c.set(x, y, z, Cell.of(Slot.ARCH, Cell.DETAIL).flag(Cell.INSIDE));
			}
		}
		for (int x = -d.hw + d.t; x <= d.hw - d.t; x++) {
			for (int z = d.zChoirEnd + 1; z <= -d.t; z++) c.set(x, d.h, z, Cell.of(Slot.WALL_ALT, Cell.BODY).flag(Cell.INSIDE));
		}
	}
}
