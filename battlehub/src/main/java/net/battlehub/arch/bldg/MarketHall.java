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
import net.battlehub.arch.mod.RoofField;
import net.battlehub.arch.mod.Spec;

/**
 * A market hall: an open arcade on the ground floor (market stalls under it), a timbered or stone upper hall, a
 * hipped roof and a little lantern (cupola) on top.
 */
public final class MarketHall implements Archetype {
	@Override
	public String id() {
		return "market_hall";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.TOWN);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		int w = r.pick(13, 17), d = r.pick(9, 13);
		b.size(w, d, 10);
		b.storeys = 2;
		b.palette = Archetype.palette(b, district, r);
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		int hw = b.brief.width / 2;
		Footprint fp = Footprint.rect(-hw, -b.brief.depth + 1, hw, 0);
		b.volumes.add(new Volume("hall", Volume.Role.HALL, fp, 0, 9, Volume.Roof.HIP, 1, 2, 0).front(Dir.SOUTH, false));
		b.floorY = 0;
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Volume v = b.volume("hall");
		Ctx c = Assembler.ctx(b, "market");
		Assembler.foundation(b, v.fp, 0, 1);
		for (Facade.Run run : Facade.runs(v.fp, 1)) {
			Ctx rc = new Ctx(b, run.frame(), rng.stream("arc" + run.outward()), rng.stream("decor:arc" + run.outward()));
			int len = run.length();
			rc.place("wall.arcade", Xform.ID, Spec.of(len, 5, 1).with("span", 3));
			rc.place("wall.timber", Xform.at(0, 5, 0), Spec.of(len, 4, 1).with("span", 4));
			for (int x = 2; x < len - 2; x += 4) rc.place("window.rect", Xform.at(x - 1, 5, 0), Spec.of(3, 4, 1));
		}
		Assembler.floor(b, v.fp.inset(1), 0, "checker");
		Assembler.floor(b, v.fp.inset(1), 5, "plain");
		Assembler.hollow(b, v.fp.inset(1), 1, 4);
		Assembler.Roofs roofs = new Assembler.Roofs();
		roofs.field.hip(v.fp.minX(), v.fp.minZ(), v.fp.maxX(), v.fp.maxZ(), v.top, 1, 1);
		Assembler.finishRoofs(b, roofs);
		// a lantern (cupola) on the ridge
		int cx = 0, cz = (int) Math.round(v.fp.centerZ());
		int top = roofs.field.at(cx, cz);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
				c.set(cx + dx, top + 1, cz + dz, Cell.of(Slot.WOOD, Cell.FEATURE));
				if (corner) {
					c.set(cx + dx, top + 2, cz + dz, Cell.log(Slot.TIMBER, 'y', Cell.FEATURE));
					c.set(cx + dx, top + 3, cz + dz, Cell.log(Slot.TIMBER, 'y', Cell.FEATURE));
				}
			}
		}
		c.set(cx, top + 4, cz, Cell.log(Slot.TIMBER, 'x', Cell.FEATURE + 1));
		c.literal(cx, top + 3, cz, "bell[attachment=ceiling]", Cell.FEATURE);
		RoofField f = new RoofField();
		f.hip(cx - 1, cz - 1, cx + 1, cz + 1, top + 4, 1, 1);
		f.draw(c, Slot.ROOF, Slot.ROOF_EDGE, Cell.FEATURE);
		c.place("orn.finial", Xform.at(cx, f.at(cx, cz) + 1, cz), Spec.of(1, 1, 1));
		// market stalls under the arcade: barrels, hay, crates
		String[] goods = {"barrel[facing=up]", "hay_block", "composter", "melon", "pumpkin", "carved_pumpkin[facing=south]"};
		Rng decor = b.rng.stream("decor:stalls");
		Footprint in = v.fp.inset(2);
		for (int x = in.minX(); x <= in.maxX(); x += 3) {
			c.decor(x, 1, in.minZ(), Cell.literal(decor.pick(goods), Cell.DETAIL));
			c.decor(x, 1, in.maxZ(), Cell.literal(decor.pick(goods), Cell.DETAIL));
		}
	}
}
