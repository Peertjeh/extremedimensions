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
 * A magical tower as a grammar: Tower → plinth terrace + stages (each narrower, a balcony and a rune band at
 * every step) + crown (crystal cage, copper spire or dome) + turrets attached at different heights (asymmetric,
 * balanced round the axis) + an annex + floating crystals and rune rings (meant to float). Inside: a spiral stair
 * and a study. The door faces local +z.
 */
public final class MageTower implements Archetype {
	@Override
	public String id() {
		return "mage_tower";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.ARCANE);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		int radius = r.range(6, 8);
		int stages = r.range(3, 5);
		b.size(radius * 2 + 1, radius * 2 + 1, (int) Math.round(stages * r.range(10, 13) * scale));
		b.palette = Archetype.palette(b, district, r);
		b.choose("radius", radius);
		b.choose("stages", stages);
		b.choose("shape", r.chance(0.6) ? "round" : "octagon");
		b.choose("crown", r.pick("crystal", "spire", "spire", "dome"));
		b.choose("turrets", r.range(2, 4));
		b.choose("annex", r.chance(0.6));
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Brief br = b.brief;
		int r = (Integer) br.choices.get("radius");
		int stages = (Integer) br.choices.get("stages");
		boolean round = "round".equals(br.choices.get("shape"));
		int cz = -r - 4;
		int y = 1;
		int stageH = br.height / stages;
		for (int i = 0; i < stages; i++) {
			int ri = Math.max(3, r - i);
			Footprint fp = round ? Footprint.circle(0, cz, ri + 0.4) : Footprint.octagon(0, cz, ri);
			int top = y + stageH - 1;
			boolean last = i == stages - 1;
			Volume v = new Volume("stage" + i, Volume.Role.TOWER, fp, y, top, last ? Volume.Roof.SPIRE : Volume.Roof.FLAT, 2, Math.max(1, stageH / 6), i == 0 ? 1 : 0);
			v.opt("crown", last ? br.choices.get("crown") : "none").opt("openings", "arcane").opt("bands", 0).opt("stair", "well");
			if (i == 0) v.front(Dir.SOUTH, true);
			b.volumes.add(v);
			y = top + 1;
		}
		// Turrets round the tower at different heights
		int turrets = (Integer) br.choices.get("turrets");
		// spread over the sides and the back (the front with the door stays clear): from 20 degrees past one side
		// round the back to 20 degrees past the other
		double from = Math.toRadians(160), to = Math.toRadians(380);
		for (int t = 0; t < turrets; t++) {
			double a = from + (to - from) * (t + 0.5) / turrets + rng.range(-0.25, 0.25);
			int stage = Math.min(stages - 2, rng.range(0, stages - 2));
			Volume host = b.volumes.get(Math.max(0, stage));
			double hr = Math.max(host.fp.width(), host.fp.depth()) / 2.0;
			int tr = rng.range(2, 3);
			double tx = Math.cos(a) * (hr + tr - 1), tz = cz + Math.sin(a) * (hr + tr - 1);
			int ty0 = host.y0 + rng.range(2, 6);
			int ty1 = host.top + rng.range(6, 12);
			Volume tv = new Volume("turret" + t, Volume.Role.TURRET, Footprint.circle(tx, tz, tr + 0.4), ty0, ty1, Volume.Roof.CONE, 1, 1, 2);
			tv.opt("crown", "cone").opt("openings", "arcane").opt("stair", "none").opt("corbel", ty0 > 1);
			b.volumes.add(tv);
		}
		if (Boolean.TRUE.equals(br.choices.get("annex"))) {
			int side = rng.chance(0.5) ? 1 : -1;
			int ax0 = side * (r + 1), ax1 = side * (r + 9);
			b.volumes.add(new Volume("annex", Volume.Role.WING, Footprint.rect(Math.min(ax0, ax1), cz - 4, Math.max(ax0, ax1), cz + 4), 1, 8, Volume.Roof.HIP, 1, 1, 2)
					.front(Dir.SOUTH, true));
		}
		b.floorY = 1;
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Ctx c = Assembler.ctx(b, "mage");
		Volume base = b.volume("stage0");
		double cx = base.fp.centerX(), cz = base.fp.centerZ();
		int r = (Integer) b.brief.choices.get("radius");
		// A round terrace with steps to the door
		Footprint terrace = Footprint.circle(cx, cz, r + 4.4);
		for (int x = terrace.minX(); x <= terrace.maxX(); x++) {
			for (int z = terrace.minZ(); z <= terrace.maxZ(); z++) {
				if (!terrace.contains(x, z)) continue;
				c.set(x, 0, z, Cell.of(terrace.edge(x, z) ? Slot.FOUNDATION : Slot.PAVING, Cell.FILL));
				if (terrace.edge(x, z) && Math.abs(x - cx) > 1.5) c.decor(x, 1, z, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
			}
		}
		Assembler.foundation(b, terrace, 0, 3);
		for (int x = -1; x <= 1; x++) c.set(x, 0, (int) Math.round(cz + r + 4.4) + 1, Cell.stairs(Slot.FOUNDATION, Dir.NORTH, false, Cell.DETAIL));
		// A ring of runes in the terrace
		Footprint runes = Footprint.circle(cx, cz, r + 2.4);
		for (int x = runes.minX(); x <= runes.maxX(); x++) {
			for (int z = runes.minZ(); z <= runes.maxZ(); z++) {
				if (runes.edge(x, z) && ((x + z) & 1) == 0 && !base.fp.contains(x, z)) c.decor(x, 0, z, Cell.of(Slot.MAGIC, Cell.DETAIL));
			}
		}
		int stages = (Integer) b.brief.choices.get("stages");
		for (int i = 0; i < stages; i++) {
			Volume v = b.volume("stage" + i);
			Towers.build(b, v);
			// The step to the next stage: a balcony on corbels and a band of glowing runes
			if (i < stages - 1) {
				Volume next = b.volume("stage" + (i + 1));
				ledge(c, v.fp, next.fp, v.top);
				// open the floor so the spiral continues
				int scx = (int) Math.round(cx), scz = (int) Math.round(cz);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) c.force(scx + dx, v.top + 1, scz + dz, Cell.air(Cell.FEATURE));
					}
				}
			}
			runeBand(c, v.fp, v.y0 + 1);
		}
		// Turrets
		for (Volume v : b.volumes) {
			if (v.role != Volume.Role.TURRET) continue;
			if (v.b("corbel")) corbel(c, v.fp, v.y0);
			Towers.build(b, v);
		}
		// One spiral stair from the door up through every stage to the top floor
		Volume topStage = b.volume("stage" + (stages - 1));
		int topFloor = topStage.y0 + (topStage.storeys - 1) * Math.max(4, topStage.height() / topStage.storeys);
		Towers.spiral(c, (int) Math.round(cx), (int) Math.round(cz), base.y0 + 1, topFloor);
		Volume annex = b.volume("annex");
		if (annex != null) {
			Assembler.foundation(b, annex.fp, annex.y0, 1);
			Facade.Rules ar = Assembler.rules(b, annex);
			ar.door = "door.arcane";
			Assembler.walls(b, annex, ar);
			Assembler.floors(b, annex, "checker");
			Assembler.Roofs roofs = new Assembler.Roofs();
			roofs.field.hip(annex.fp.minX(), annex.fp.minZ(), annex.fp.maxX(), annex.fp.maxZ(), annex.top, 1, 1);
			roofs.mask(base.fp);
			Assembler.finishRoofs(b, roofs);
			Assembler.interior(b, annex, "int.library", annex.y0, 4);
		}
		Assembler.interior(b, base, "int.arcane", base.y0, 4);
		// Floating crystals and a rune ring round the upper tower
		Rng decor = b.rng.stream("decor:crystals");
		int top = b.volume("stage" + (stages - 1)).top;
		for (int k = 0; k < 6; k++) {
			double a = k * Math.PI / 3 + decor.range(0, 0.5);
			double d = r + decor.range(5, 9);
			int x = (int) Math.round(cx + Math.cos(a) * d), z = (int) Math.round(cz + Math.sin(a) * d);
			int y = decor.range(base.top, top);
			c.place("orn.crystal", Xform.at(x, y, z), Spec.of(1, 1, 1).with("size", decor.range(1, 2)));
		}
		Footprint ring = Footprint.circle(cx, cz, r + 3.5);
		int ry = (base.top + top) / 2;
		for (int x = ring.minX(); x <= ring.maxX(); x++) {
			for (int z = ring.minZ(); z <= ring.maxZ(); z++) {
				if (ring.edge(x, z) && ((x * 3 + z) % 4 == 0)) c.decor(x, ry, z, Cell.of(Slot.MAGIC, Cell.DETAIL).flag(Cell.FLOAT));
			}
		}
	}

	/** A balcony where a stage steps in: slabs on upside-down stairs, a railing of bars. */
	static void ledge(Ctx c, Footprint lower, Footprint upper, int y) {
		Footprint out = lower.inset(-1);
		for (int x = out.minX(); x <= out.maxX(); x++) {
			for (int z = out.minZ(); z <= out.maxZ(); z++) {
				if (!out.contains(x, z) || upper.contains(x, z)) continue;
				double cx = lower.centerX(), cz = lower.centerZ();
				if (!lower.contains(x, z)) {
					c.set(x, y, z, Cell.stairs(Slot.TRIM, Towers.inward(x, z, cx, cz), true, Cell.DETAIL));
					c.set(x, y + 1, z, Cell.shape(Slot.METAL, Shape.PANE, Cell.DETAIL));
				} else {
					c.set(x, y + 1, z, Cell.of(Slot.FLOOR_ALT, Cell.DETAIL));
					if (lower.edge(x, z) && !out.contains(x + 1, z) == false && ((x + z) % 4 == 0)) {
						c.decor(x, y + 2, z, Cell.literal("soul_lantern", Cell.DETAIL));
					}
				}
			}
		}
	}

	/** Glowing runes in every other block round a stage, just above its floor. */
	static void runeBand(Ctx c, Footprint fp, int y) {
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				// short runs of glowing runes with gaps (a band that reads as a band, not as dots)
				if (fp.edge(x, z) && Math.floorMod(x * 2 + z * 3, 7) < 2) c.decor(x, y, z, Cell.of(Slot.MAGIC, Cell.DETAIL));
			}
		}
	}

	/** Corbelling under a turret that starts above the ground: rings of stone stepping in. */
	static void corbel(Ctx c, Footprint fp, int y0) {
		double cx = fp.centerX(), cz = fp.centerZ();
		double r = fp.width() / 2.0;
		for (int k = 1; k <= 3; k++) {
			Footprint ring = Footprint.circle(cx, cz, Math.max(0.6, r - 0.4 - k * 0.7));
			for (int x = ring.minX(); x <= ring.maxX(); x++) {
				for (int z = ring.minZ(); z <= ring.maxZ(); z++) {
					if (!ring.contains(x, z)) continue;
					boolean edge = ring.edge(x, z);
					c.set(x, y0 - k, z, edge ? Cell.stairs(Slot.TRIM, Towers.inward(x, z, cx, cz), true, Cell.DETAIL) : Cell.of(Slot.WALL, Cell.DETAIL));
				}
			}
		}
		c.decor((int) Math.round(cx), y0 - 4, (int) Math.round(cz), Cell.literal("soul_lantern[hanging=true]", Cell.DETAIL));
	}
}
