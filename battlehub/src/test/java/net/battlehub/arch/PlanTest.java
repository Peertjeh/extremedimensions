package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.bldg.Architect;
import net.battlehub.arch.bldg.Archetype;
import net.battlehub.arch.bldg.Brief;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.bldg.Style;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mod.Library;

/** The plan store, priorities and failure handling. */
class PlanTest {
	@Test
	void prioritiesProtectOpenings() {
		Plan p = new Plan();
		p.put(0, 0, 0, Cell.air(Cell.OPEN));
		p.put(0, 0, 0, Cell.of(Slot.WALL, Cell.BODY));
		assertTrue(p.get(0, 0, 0).air(), "wall drawn later does not fill a window");
		p.put(0, 0, 0, Cell.of(Slot.GLASS, Cell.OPEN));
		assertEquals(Slot.GLASS, p.get(0, 0, 0).slot());
		p.force(0, 0, 0, Cell.of(Slot.WALL, Cell.BODY));
		assertEquals(Slot.WALL, p.get(0, 0, 0).slot());
	}

	@Test
	void storesNegativeAndFarCoordinates() {
		Plan p = new Plan(4);
		for (int i = -300; i <= 300; i += 7) p.put(i, -i / 4, -i, Cell.of(Slot.WALL));
		for (int i = -300; i <= 300; i += 7) assertEquals(Slot.WALL, p.get(i, -i / 4, -i).slot());
		assertNull(p.get(1, 1, 1));
		p.remove(7, -1, -7);
		assertFalse(p.has(7, -1, -7));
		assertEquals(-300, p.minX());
		assertEquals(300, p.maxZ());
	}

	@Test
	void aFailingArchetypeLeavesNothingBehind() {
		Archetype broken = new Archetype() {
			@Override
			public String id() {
				return "broken";
			}

			@Override
			public java.util.List<Style> styles() {
				return java.util.List.of(Style.TOWN);
			}

			@Override
			public Brief brief(Style style, long seed, Palette district, double scale) {
				Brief b = new Brief(id(), style, seed);
				b.palette = district;
				return b;
			}

			@Override
			public void plan(Building b, Rng rng) {
				throw new IllegalStateException("no plan");
			}

			@Override
			public void assemble(Building b, Rng rng) {
			}
		};
		Architect.register(broken);
		Voxels out = new Voxels();
		try {
			Architect.build(broken.brief(Style.TOWN, 1, TestPalettes.dimension(), 1), Library.defaults(), Xform.ID, null).emit(out);
		} catch (IllegalStateException expected) {
			// generation fails before anything is written
		}
		assertEquals(0, out.size());
	}
}
