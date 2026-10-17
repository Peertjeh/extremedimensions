package net.battlehub.arch.bldg;

import java.util.List;

import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mat.Palette;

/**
 * A chapel or parish church: Chapel → nave (steep gable, buttressed lancet bays, a rose or oculus over the door)
 * + bell tower (at the front or beside it, with an open belfry and a spire) + rounded east end + pews. The door
 * faces local +z.
 */
public final class Chapel implements Archetype {
	@Override
	public String id() {
		return "chapel";
	}

	@Override
	public List<Style> styles() {
		return List.of(Style.GOTHIC, Style.TOWN);
	}

	@Override
	public Brief brief(Style style, long seed, Palette district, double scale) {
		Rng r = new Rng(seed).stream("brief");
		Brief b = new Brief(id(), style, seed);
		int w = r.pick(9, 11);
		int d = r.range(15, 21);
		b.size(w, d, r.range(9, 12));
		b.palette = Archetype.palette(b, district, r);
		b.choose("tower", r.pick("front", "side", "side"));
		b.choose("apse", r.chance(0.6));
		return b;
	}

	@Override
	public void plan(Building b, Rng rng) {
		Brief br = b.brief;
		int hw = br.width / 2;
		Footprint nave = Footprint.rect(-hw, -br.depth + 1, hw, 0);
		b.volumes.add(new Volume("nave", Volume.Role.NAVE, nave, 0, br.height, Volume.Roof.STEEP_GABLE, 1, 1, 1).front(Dir.SOUTH, true));
		String tower = (String) br.choices.get("tower");
		int th = br.height + br.width + rng.range(6, 10);
		if (tower.equals("front")) {
			b.volumes.add(new Volume("tower", Volume.Role.TOWER, Footprint.rect(-2, 1, 2, 5), 0, th, Volume.Roof.SPIRE, 1, 3, 0).front(Dir.SOUTH, true)
					.opt("crown", "belfry").opt("openings", b.style.has("gothic") ? "lancet" : "glass").opt("stair", "spiral"));
		} else {
			int tx = hw + 3;
			b.volumes.add(new Volume("tower", Volume.Role.TOWER, Footprint.rect(tx - 2, -6, tx + 2, -2), 0, th, Volume.Roof.SPIRE, 1, 3, 0).front(Dir.SOUTH, true)
					.opt("crown", "belfry").opt("openings", b.style.has("gothic") ? "lancet" : "glass").opt("stair", "spiral"));
		}
		if (Boolean.TRUE.equals(br.choices.get("apse"))) {
			int zc = -br.depth + 1;
			b.volumes.add(new Volume("apse", Volume.Role.APSE, Footprint.clip(Footprint.circle(0, zc, hw + 0.4), (x, z) -> z <= zc), 0, br.height - 2,
					Volume.Roof.CONE, 1, 1, 2));
		}
		b.floorY = 0;
	}

	@Override
	public void assemble(Building b, Rng rng) {
		Volume nave = b.volume("nave");
		Assembler.foundation(b, nave.fp, 0, 2);
		Facade.Rules r = Assembler.rules(b, nave);
		r.bay = 5;
		r.windows = new String[] {b.style.has("gothic") ? "window.lancet" : "window.round"};
		r.door = "door.double";
		Volume tower = b.volume("tower");
		boolean frontTower = tower.fp.minZ() > 0;
		Assembler.walls(b, nave, r);
		Assembler.floors(b, nave, "aisle");
		Assembler.Roofs roofs = new Assembler.Roofs();
		roofs.gable(nave.fp, nave.top, 2, 1, true);
		Volume apse = b.volume("apse");
		if (apse != null) {
			Assembler.foundation(b, apse.fp, 0, 2);
			for (int x = apse.fp.minX(); x <= apse.fp.maxX(); x++) {
				for (int z = apse.fp.minZ(); z <= apse.fp.maxZ(); z++) {
					if (!apse.fp.contains(x, z) || z >= nave.fp.minZ()) continue;
					boolean edge = !apse.fp.inset(1).contains(x, z);
					for (int y = 0; y <= apse.top; y++) {
						Assembler.ctx(b, "apse").set(x, y, z, edge ? net.battlehub.arch.Cell.of(net.battlehub.arch.Slot.WALL, net.battlehub.arch.Cell.BODY)
								: net.battlehub.arch.Cell.air(net.battlehub.arch.Cell.FILL));
					}
				}
			}
			roofs.field.cone(0, nave.fp.minZ(), nave.fp.width() / 2.0, apse.top, 2, 1);
		}
		if (frontTower) {
			// the nave's door is behind the tower: the tower's door leads in
			Assembler.clear(b, Footprint.rect(-1, 0, 1, 0), 1, 3);
		}
		Towers.build(b, tower);
		roofs.mask(tower.fp);
		Assembler.finishRoofs(b, roofs);
		Assembler.interior(b, nave, "int.nave", 0, nave.top - 1);
	}
}
