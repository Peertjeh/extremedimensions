package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.bldg.Architect;
import net.battlehub.arch.bldg.Brief;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.bldg.Style;
import net.battlehub.arch.geom.ArchCurve;
import net.battlehub.arch.mod.Category;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Library;
import net.battlehub.arch.mod.Module;
import net.battlehub.arch.mod.RoofField;
import net.battlehub.arch.mod.Spec;

/** The module library is open for new modules, and its geometry primitives behave. */
class LibraryTest {
	@Test
	void aNewModuleIsUsedWithoutTouchingTheEngine() {
		Library lib = Library.defaults();
		int before = lib.size();
		// a "porthole" window replaces the standard rectangular window of the town style, by id
		Module porthole = new Module() {
			@Override
			public String id() {
				return "window.rect";
			}

			@Override
			public Category category() {
				return Category.WINDOW;
			}

			@Override
			public Set<String> styles() {
				return Set.of("town");
			}

			@Override
			public void build(Ctx c, Spec s) {
				c.set(s.w() / 2, 2, 0, Cell.literal("glowstone", Cell.FEATURE));
			}
		};
		lib.register(porthole);
		assertEquals(before, lib.size(), "replacing by id keeps the count");
		Brief br = Architect.brief("townhouse", 3, TestPalettes.dimension(), 1.0);
		Building b = Architect.build(br, lib, Xform.ID, null);
		int[] glow = {0};
		b.plan.forEach((x, y, z, c) -> {
			if ("glowstone".equals(c.literal())) glow[0]++;
		});
		assertTrue(glow[0] > 0, "the new module was placed");
	}

	@Test
	void pickHonoursStylesAndSizes() {
		Library lib = Library.defaults();
		Rng rng = new Rng(5);
		for (int i = 0; i < 20; i++) {
			Module m = lib.pick(Category.WINDOW, Style.GOTHIC, Spec.of(5, 9, 2), rng);
			assertTrue(m.weight(Style.GOTHIC) > 0 && m.fits(Spec.of(5, 9, 2)), m.id());
		}
		assertEquals(null, lib.pick(Category.WINDOW, Style.GOTHIC, Spec.of(1, 1, 1), rng));
	}

	@Test
	void archesAreSymmetricAndRise() {
		for (ArchCurve c : ArchCurve.values()) {
			for (int w = 1; w <= 11; w++) {
				int[] h = c.heights(w);
				for (int i = 0; i < w; i++) assertEquals(h[i], h[w - 1 - i], c + " width " + w + " is not symmetric");
				if (c != ArchCurve.FLAT && w >= 3) assertTrue(c.rise(w) >= 1, c + " width " + w + " does not rise");
			}
		}
		assertTrue(ArchCurve.LANCET.rise(5) > ArchCurve.ROUND.rise(5), "a lancet is sharper than a round arch");
	}

	@Test
	void crossingRoofsJoinWithoutHoles() {
		RoofField f = new RoofField();
		f.gable(-4, -12, 4, 12, true, 10, 1, 1);
		f.gable(-12, -4, 12, 4, false, 10, 1, 1);
		// over the crossing the roof is as high as the ridges and has no column missing inside the union
		for (int x = -12; x <= 12; x++) {
			for (int z = -12; z <= 12; z++) {
				boolean inside = Math.abs(x) <= 5 || Math.abs(z) <= 5;
				assertEquals(inside, f.at(x, z) != Integer.MIN_VALUE, "column " + x + "," + z);
			}
		}
		assertEquals(15, f.at(0, 0), "the ridges meet at the top");
	}

	@Test
	void theDocumentedExampleProfilesWork() {
		Library lib = Library.defaults();
		var dim = TestPalettes.dimension();
		Brief castle = Architect.brief("castle", 7, dim, 1.1);
		castle.choose("plan", "octagon");
		castle.choose("towers", "round");
		castle.choose("crown", "cone");
		castle.choose("keep", "round");
		Brief cathedral = Architect.brief("cathedral", 2, dim, 1.0).palette("limestone");
		cathedral.choose("front", "twin");
		cathedral.choose("spires", true);
		cathedral.choose("crossing", "lantern");
		Brief tower = Architect.brief("mage_tower", 9, dim, 1.0).palette("amethyst");
		tower.choose("stages", 5);
		tower.choose("crown", "crystal");
		Brief ruin = Architect.brief("temple_ruin", 6, dim, 1.0).palette("marble").condition(net.battlehub.arch.bldg.Condition.ANCIENT);
		ruin.choose("temple", "tholos");
		for (Brief b : new Brief[] {castle, cathedral, tower, ruin}) {
			Building built = Architect.build(b, lib, Xform.facing(Dir.SOUTH, 0, 64, 0), null);
			var report = net.battlehub.arch.check.Validator.building(built);
			assertTrue(report.ok(), b.archetype + ": " + report.errors());
			if (b.paletteRecipe != null) assertTrue(built.brief.palette.name().endsWith(":" + b.paletteRecipe), built.brief.palette.name());
		}
	}
}
