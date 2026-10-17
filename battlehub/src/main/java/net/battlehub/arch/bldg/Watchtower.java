package net.battlehub.arch.bldg;

import java.util.List;

import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mat.Palette;

/** A watchtower: square or round, slits, a door, and a crown of battlements, a cone or a spire. */
public final class Watchtower implements Archetype {
	@Override
	public String id() {
		return "watchtower";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.FORTRESS, Style.TOWN, Style.ARCANE);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		int w = r.pick(5, 7, 7, 9);
		b.size(w, w, (int) Math.round(r.range(14, 22) * scale));
		b.storeys = Math.max(2, b.height / 6);
		b.palette = Archetype.palette(b, district, r);
		b.choose("shape", r.chance(0.5) ? "round" : r.chance(0.5) ? "square" : "octagon");
		b.choose("crown", r.pick("battlement", "machicolation", "cone", "spire"));
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Brief br = b.brief;
		int r = br.width / 2;
		String shape = (String) br.choices.get("shape");
		Footprint fp = switch (shape) {
			case "round" -> Footprint.circle(0, -r, r + 0.4);
			case "octagon" -> Footprint.octagon(0, -r, r);
			default -> Footprint.rect(-r, -2 * r, r, 0);
		};
		String crown = (String) br.choices.get("crown");
		Volume.Roof roof = crown.equals("cone") ? Volume.Roof.CONE : crown.equals("spire") ? Volume.Roof.SPIRE : Volume.Roof.BATTLEMENT;
		Volume v = new Volume("tower", Volume.Role.TOWER, fp, 0, br.height, roof, 1, br.storeys, 0).front(Dir.SOUTH, true);
		v.opt("crown", crown).opt("batter", 2).opt("bands", 6).opt("flag", crown.startsWith("b") || crown.startsWith("m"));
		if (br.width >= 9) v.opt("turrets", rng.chance(0.5) ? 4 : 0);
		b.volumes.add(v);
		b.floorY = 0;
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Towers.build(b, b.volumes.get(0));
	}
}
