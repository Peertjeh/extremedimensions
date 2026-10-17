package net.battlehub.arch.bldg;

import java.util.List;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Xform;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.RoofField;
import net.battlehub.arch.mod.Spec;

/**
 * An ancient temple complex, built whole and then aged: Complex → podium with steps + temple (a colonnade round
 * a walled cella under a low pediment roof, or a round tholos under a dome) + a precinct wall with a colonnaded
 * walk (stoa) and a gateway + an obelisk or two. It is generated intact through the same modules as every other
 * building; the ruin comes from the condition (roofs gone, columns broken, walls breached, plants everywhere).
 * The gateway faces local +z.
 */
public final class TempleRuin implements Archetype {
	@Override
	public String id() {
		return "temple_ruin";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.ANCIENT);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		int cols = r.range(5, 7);
		int span = 4;
		int w = (cols - 1) * span + 1;
		int d = (int) Math.round(w * r.range(1.35, 1.7));
		b.size(w, d, 13);
		b.palette = Archetype.palette(b, district, r);
		b.condition = r.chance(0.5) ? Condition.RUINED : Condition.ANCIENT;
		b.choose("columns", cols);
		b.choose("temple", r.chance(0.3) ? "tholos" : "peripteros");
		b.choose("precinct", r.range(10, 16));
		b.choose("obelisks", r.range(1, 2));
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Brief br = b.brief;
		int w = br.width, d = br.depth;
		int pz0 = -(d + 10);
		boolean tholos = "tholos".equals(br.choices.get("temple"));
		Footprint templeFp = tholos ? Footprint.circle(0, pz0 + d / 2.0, w / 2.0 + 0.4) : Footprint.rect(-w / 2, pz0, w / 2, pz0 + d - 1);
		int podium = 3;
		Volume temple = new Volume("temple", Volume.Role.TEMPLE, templeFp, podium, podium + br.height, tholos ? Volume.Roof.DOME : Volume.Roof.GABLE, 1, 1, 0)
				.front(Dir.SOUTH, true);
		b.volumes.add(temple);
		int m = (Integer) br.choices.get("precinct");
		Footprint precinct = Footprint.rect(-w / 2 - m, pz0 - m / 2, w / 2 + m, 0);
		b.volumes.add(new Volume("precinct", Volume.Role.COURT, precinct, 0, 5, Volume.Roof.NONE, 1, 1, 2));
		b.floorY = 0;
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Ctx c = Assembler.ctx(b, "temple");
		Volume temple = b.volume("temple");
		Volume court = b.volume("precinct");
		Footprint pfp = court.fp;
		// The precinct floor
		for (int x = pfp.minX(); x <= pfp.maxX(); x++) {
			for (int z = pfp.minZ(); z <= pfp.maxZ(); z++) {
				c.set(x, 0, z, Cell.of(Slot.PAVING, Cell.FILL).flag(Cell.KEEP));
			}
		}
		Assembler.foundation(b, pfp, 0, 2);
		// Precinct wall with a stoa (colonnaded walk) along the back and the sides
		precinct(b, c, pfp, rng);
		// Podium with steps all round
		Footprint tfp = temple.fp;
		int podium = temple.y0;
		Footprint pod = tfp.inset(-2);
		for (int k = 0; k <= podium; k++) {
			Footprint step = tfp.inset(-2 - (podium - k));
			for (int x = step.minX(); x <= step.maxX(); x++) {
				for (int z = step.minZ(); z <= step.maxZ(); z++) {
					if (!step.contains(x, z)) continue;
					for (int y = 1; y <= k; y++) c.set(x, y, z, Cell.of(y == k ? Slot.FLOOR : Slot.FOUNDATION, Cell.BODY).flag(y < k ? 0 : Cell.KEEP));
				}
			}
		}
		boolean tholos = temple.roof == Volume.Roof.DOME;
		if (tholos) {
			tholos(b, c, temple, rng);
		} else {
			peripteros(b, c, temple, rng);
		}
		// Obelisks flanking the approach
		int obelisks = (Integer) b.brief.choices.get("obelisks");
		for (int k = 0; k < obelisks; k++) {
			int x = (k == 0 ? -1 : 1) * (tfp.width() / 2 + 4);
			int z = -6;
			obelisk(c, x, 1, z, rng.range(9, 13));
		}
		// a path from the gateway
		for (int z = 0; z >= tfp.maxZ() + 3; z--) {
			for (int x = -2; x <= 2; x++) c.set(x, 0, z, Cell.of(Slot.PATH, Cell.DETAIL).flag(Cell.KEEP));
		}
	}

	private static void column(Ctx c, int x, int y0, int z, int h) {
		c.set(x, y0, z, Cell.of(Slot.TRIM, Cell.DETAIL));
		for (int y = y0 + 1; y < y0 + h - 1; y++) c.set(x, y, z, Cell.log(Slot.PILLAR, 'y', Cell.DETAIL));
		c.set(x, y0 + h - 1, z, Cell.of(Slot.TRIM, Cell.DETAIL));
	}

	/** A rectangular temple: columns all round, a walled cella, an entablature and a low pediment roof. */
	private static void peripteros(Building b, Ctx c, Volume v, Rng rng) {
		Footprint fp = v.fp;
		int y0 = v.y0 + 1, h = v.top - v.y0 - 3;
		int span = 4;
		for (int x = fp.minX(); x <= fp.maxX(); x += span) {
			column(c, x, y0, fp.minZ(), h);
			column(c, x, y0, fp.maxZ(), h);
		}
		for (int z = fp.minZ() + span; z < fp.maxZ(); z += span) {
			column(c, fp.minX(), y0, z, h);
			column(c, fp.maxX(), y0, z, h);
		}
		// Entablature: architrave and frieze with a pattern
		int ey = y0 + h;
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.edge(x, z)) continue;
				c.set(x, ey, z, Cell.of(Slot.TRIM, Cell.BODY));
				c.set(x, ey + 1, z, Cell.of(((x + z) & 1) == 0 ? Slot.BAND : Slot.WALL, Cell.BODY));
			}
		}
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) c.set(x, ey + 2, z, Cell.of(Slot.TRIM, Cell.BODY));
		}
		// The cella: a walled room inside the colonnade with a temple door
		Footprint cella = Footprint.rect(fp.minX() + 3, fp.minZ() + 4, fp.maxX() - 3, fp.maxZ() - 5);
		Volume cv = new Volume("cella", Volume.Role.MAIN, cella, y0 - 1, ey - 1, Volume.Roof.NONE, 1, 1, 1).front(Dir.SOUTH, true);
		Facade.Rules r = Assembler.rules(b, cv);
		r.pier = null;
		r.windows = new String[] {"window.blind"};
		r.door = "door.temple";
		r.plinth = 1;
		Assembler.walls(b, cv, r);
		Assembler.hollow(b, cella.inset(1), y0, ey - 1);
		Assembler.interior(b, cv, "int.shrine", y0 - 1, 3);
		// Pediment roof: a low gable over the whole temple
		Assembler.Roofs roofs = new Assembler.Roofs();
		roofs.gable(fp, ey + 2, 1, 1, true);
		Assembler.finishRoofs(b, roofs);
		// acroteria on the gable corners
		c.place("orn.urn", Xform.at(0, ey + 3 + fp.width() / 2, fp.maxZ() + 1), Spec.of(1, 1, 1));
	}

	/** A round temple: a ring of columns under a ring beam and a dome, a small round cella inside. */
	private static void tholos(Building b, Ctx c, Volume v, Rng rng) {
		Footprint fp = v.fp;
		double cx = fp.centerX(), cz = fp.centerZ();
		double r = fp.width() / 2.0 - 0.5;
		int y0 = v.y0 + 1, h = v.top - v.y0 - 4;
		int n = Math.max(8, (int) Math.round(2 * Math.PI * r / 3));
		for (int k = 0; k < n; k++) {
			double a = k * 2 * Math.PI / n;
			column(c, (int) Math.round(cx + Math.cos(a) * r), y0, (int) Math.round(cz + Math.sin(a) * r), h);
		}
		int ey = y0 + h;
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.contains(x, z)) continue;
				c.set(x, ey, z, Cell.of(fp.edge(x, z) ? Slot.TRIM : Slot.WALL_ALT, Cell.BODY));
				if (fp.edge(x, z)) c.set(x, ey + 1, z, Cell.of(Slot.BAND, Cell.BODY));
			}
		}
		Footprint cella = Footprint.circle(cx, cz, r - 3);
		for (int y = y0; y < ey; y++) {
			for (int x = cella.minX(); x <= cella.maxX(); x++) {
				for (int z = cella.minZ(); z <= cella.maxZ(); z++) {
					if (cella.edge(x, z)) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
				}
			}
		}
		int dz = (int) Math.round(cz + r - 3);
		for (int y = y0; y < y0 + 4; y++) c.set((int) Math.round(cx), y, dz, Cell.air(Cell.FEATURE));
		RoofField f = new RoofField();
		f.dome(cx, cz, r + 0.5, ey + 1, 0.8);
		f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.DETAIL);
		c.place("orn.urn", Xform.at((int) Math.round(cx), f.at((int) Math.round(cx), (int) Math.round(cz)) + 1, (int) Math.round(cz)), Spec.of(1, 1, 1));
	}

	/** The precinct: a low wall round the court, a stoa along the back, a gateway with a pediment at the front. */
	private static void precinct(Building b, Ctx c, Footprint fp, Rng rng) {
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!fp.edge(x, z)) continue;
				boolean gate = z == fp.maxZ() && Math.abs(x) <= 2;
				if (gate) continue;
				for (int y = 1; y <= 4; y++) c.set(x, y, z, Cell.of(Slot.WALL, Cell.BODY));
				c.set(x, 5, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
			}
		}
		// Stoa: a roofed colonnade along the inside of the back wall
		int z0 = fp.minZ() + 1, z1 = fp.minZ() + 4;
		for (int x = fp.minX() + 1; x <= fp.maxX() - 1; x++) {
			if ((x - fp.minX()) % 3 == 0) column(c, x, 1, z1, 5);
			for (int z = z0; z <= z1; z++) c.set(x, 6, z, Cell.of(Slot.TRIM, Cell.BODY));
			c.set(x, 7, z1, Cell.stairs(Slot.ROOF, Dir.NORTH, false, Cell.DETAIL));
			c.set(x, 7, z0 + 1, Cell.of(Slot.ROOF, Cell.DETAIL));
			c.set(x, 7, z0, Cell.of(Slot.ROOF, Cell.DETAIL));
			c.set(x, 7, z0 + 2, Cell.stairs(Slot.ROOF, Dir.NORTH, false, Cell.DETAIL));
		}
		// Gateway: two piers and a lintel with a small pediment; it is the way into the complex
		int gz = fp.maxZ();
		c.attach(new net.battlehub.arch.mod.Attachment("gate", net.battlehub.arch.mod.Attachment.Kind.ENTRANCE, 0, 1, gz + 1, Dir.SOUTH, 5, 6));
		for (int side = -1; side <= 1; side += 2) {
			for (int y = 1; y <= 7; y++) {
				c.set(side * 3, y, gz, Cell.of(Slot.QUOIN, Cell.BODY));
				c.set(side * 3, y, gz - 1, Cell.of(Slot.QUOIN, Cell.BODY));
			}
		}
		for (int x = -3; x <= 3; x++) {
			c.set(x, 8, gz, Cell.of(Slot.TRIM, Cell.BODY));
			c.set(x, 8, gz - 1, Cell.of(Slot.TRIM, Cell.BODY));
		}
		for (int i = 0; i <= 3; i++) {
			for (int x = -3 + i; x <= 3 - i; x++) {
				Cell cell = x == -3 + i ? Cell.stairs(Slot.TRIM, Dir.EAST, false, Cell.DETAIL) : x == 3 - i ? Cell.stairs(Slot.TRIM, Dir.WEST, false, Cell.DETAIL) : Cell.of(Slot.WALL, Cell.DETAIL);
				c.set(x, 9 + i, gz, cell);
			}
		}
	}

	private static void obelisk(Ctx c, int x, int y0, int z, int h) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) c.set(x + dx, y0, z + dz, Cell.of(Slot.FOUNDATION, Cell.BODY));
		}
		for (int y = y0 + 1; y < y0 + h; y++) c.set(x, y, z, Cell.of(Slot.QUOIN, Cell.BODY));
		c.set(x, y0 + h, z, Cell.of(Slot.ACCENT, Cell.DETAIL));
		c.set(x, y0 + h + 1, z, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
	}
}
