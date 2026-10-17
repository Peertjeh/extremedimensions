package net.battlehub.arch.bldg;

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
 * A town house: two or three storeys on a stone ground floor, half-timbered or brick above, a gable (to the street
 * or along it) or a hipped roof, dormers, a chimney and a furnished room. A shop variant opens its ground floor
 * with wide windows and an awning. The door faces local +z (the street).
 */
public final class Townhouse implements Archetype {
	@Override
	public String id() {
		return "townhouse";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.TOWN);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		double s = b.randomness.spread;
		int w = r.range(5, (int) Math.round(7 + s));
		int d = r.range(6, (int) Math.round(9 + s));
		b.storeys = r.chance(0.3) ? 3 : 2;
		b.size(w, d, b.storeys * 4 + w);
		b.palette = Archetype.palette(b, district, r);
		b.choose("roof", r.chance(0.2) ? "hip" : r.chance(0.5) ? "gable_street" : "gable_side");
		b.choose("shop", r.chance(0.25));
		b.choose("dormers", r.chance(0.6));
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Brief br = b.brief;
		int w = br.width, d = br.depth;
		int x0 = -(w / 2), z0 = -(d - 1);
		Footprint fp = Footprint.rect(x0, z0, x0 + w - 1, 0);
		int top = br.storeys * 4;
		Volume.Roof roof = "hip".equals(br.choices.get("roof")) ? Volume.Roof.HIP : Volume.Roof.GABLE;
		Volume v = new Volume("house", Volume.Role.HOUSE, fp, 0, top, roof, 1, br.storeys, 0).front(Dir.SOUTH, true);
		b.volumes.add(v);
		b.floorY = 0;
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Volume v = b.volumes.get(0);
		Assembler.foundation(b, v.fp, v.y0, 2);
		Facade.Rules r = Assembler.rules(b, v);
		boolean shop = Boolean.TRUE.equals(b.brief.choices.get("shop"));
		if (shop) r.windows = new String[] {"window.rect", "window.rect"};
		Assembler.walls(b, v, r);
		Assembler.floors(b, v, "plain");
		Assembler.Roofs roofs = new Assembler.Roofs();
		String kind = (String) b.brief.choices.get("roof");
		int eave = v.top;
		if ("hip".equals(kind)) {
			roofs.field.hip(v.fp.minX(), v.fp.minZ(), v.fp.maxX(), v.fp.maxZ(), eave, 1, 1);
		} else {
			roofs.gable(v.fp, eave, 1, 1, "gable_street".equals(kind));
		}
		Ctx c = Assembler.ctx(b, "house");
		// Chimney on one side, through the roof
		int cx = rng.chance(0.5) ? v.fp.minX() + 1 : v.fp.maxX() - 1;
		int cz = v.fp.minZ() + 1;
		int ch = v.top + Math.max(3, Math.min(v.fp.width(), v.fp.depth()) / 2 + 2);
		roofs.mask(Footprint.rect(cx, cz, cx, cz));
		Assembler.finishRoofs(b, roofs);
		c.place("orn.chimney", Xform.at(cx, v.y0 + 1, cz), Spec.of(1, ch - v.y0, 1));
		if (Boolean.TRUE.equals(b.brief.choices.get("dormers")) && "gable_side".equals(kind) && v.fp.width() >= 7) {
			// a dormer on the street side of the roof, in the middle
			int dx = (v.fp.minX() + v.fp.maxX()) / 2 - 1;
			c.place("orn.dormer", Xform.at(dx, eave + 1, v.fp.maxZ()), Spec.of(3, 3, 2));
		}
		if (shop) {
			// an awning of wool over the shop windows
			String[] colours = {"red", "green", "blue", "yellow", "orange", "cyan"};
			String col = colours[rng.nextInt(colours.length)];
			for (int x = v.fp.minX() + 1; x < v.fp.maxX(); x++) {
				c.decor(x, v.y0 + 4, v.fp.maxZ() + 1, Cell.literal((x % 2 == 0 ? col : "white") + "_carpet", Cell.DETAIL));
				c.decor(x, v.y0 + 3, v.fp.maxZ() + 1, Cell.slab(Slot.WOOD, true, Cell.DETAIL));
			}
		}
		Assembler.interior(b, v, "int.house", v.y0, 3);
	}
}
