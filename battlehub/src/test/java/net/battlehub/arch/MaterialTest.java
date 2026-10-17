package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.bldg.Style;
import net.battlehub.arch.check.Report;
import net.battlehub.arch.check.Validator;
import net.battlehub.arch.mat.Catalog;
import net.battlehub.arch.mat.Gradient;
import net.battlehub.arch.mat.Material;
import net.battlehub.arch.mat.MaterialContext;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mat.Probe;
import net.battlehub.arch.mat.Ramp;
import net.battlehub.arch.mat.Resolver;

/** Materials: gradients run on across modules, palettes inherit, ramps climb, names exist in 26.3. */
class MaterialTest {
	@Test
	void gradientsAreContinuousAcrossModuleBoundaries() {
		// The facade of a big building is drawn by many modules; the material field is evaluated in building
		// coordinates, so neighbouring blocks on either side of a module seam get nearly the same value.
		Building b = BuildingTest.build("cathedral", 11);
		MaterialContext mc = b.materials();
		Gradient g = mc.field(Slot.WALL);
		Probe p = new Probe();
		double worst = 0;
		for (int y = b.floorY; y < b.plan.maxY(); y++) {
			for (int x = b.plan.minX(); x < b.plan.maxX(); x++) {
				double a = g.at(p.set(x, y, 0, x, y, 0, Cell.of(Slot.WALL)));
				double c = g.at(p.set(x + 1, y, 0, x + 1, y, 0, Cell.of(Slot.WALL)));
				worst = Math.max(worst, Math.abs(a - c));
			}
		}
		assertTrue(worst < 0.08, "a one-block step changes the gradient by " + worst);
	}

	@Test
	void stoneByStoneMixingFormsPatchesNotNoise() {
		// share of neighbouring wall blocks with different materials: some variation, but mostly runs of one stone
		Building b = BuildingTest.build("castle", 3);
		Voxels v = BuildingTest.emit(b);
		int[] same = {0, 0};
		v.forEach((x, y, z, id) -> {
			Material m = Catalog.ofBlock(id);
			if (m == null || !m.is("stone") && !m.is("sand") && !m.is("brick")) return;
			String right = v.get(x + 1, y, z);
			if (right == null) return;
			Material n = Catalog.ofBlock(right);
			if (n == null || !n.is("stone") && !n.is("sand") && !n.is("brick")) return;
			same[1]++;
			if (n == m) same[0]++;
		});
		double share = (double) same[0] / Math.max(1, same[1]);
		assertTrue(share > 0.6, "neighbouring stones agree only " + share);
		assertTrue(share < 0.995, "no variation at all");
	}

	@Test
	void palettesInheritFromTheirParents() {
		Palette dim = TestPalettes.dimension();
		Palette district = dim.child("district").set(Slot.ROOF, "deepslate_tiles");
		Palette building = district.child("building").set(Slot.WALL, "bricks");
		assertEquals("deepslate_tiles", building.ramp(Slot.ROOF).get(0).name());
		assertEquals("bricks", building.ramp(Slot.WALL).get(0).name());
		assertEquals(dim.ramp(Slot.GROUND), building.ramp(Slot.GROUND));
		// a role without a ramp falls back along its chain (ARCH → TRIM)
		assertNotNull(building.ramp(Slot.ARCH));
		assertEquals(2, building.depth());
	}

	@Test
	void styleRecipesClimbWithoutBigJumps() {
		Palette dim = TestPalettes.dimension();
		for (Style s : Style.all()) {
			for (Style.PaletteRecipe r : s.palettes()) {
				Palette p = s.palette(dim, r.name(), new Rng(1));
				Report rep = new Report();
				Validator.palette(p, rep);
				assertTrue(rep.issues.isEmpty(), s.name + ":" + r.name() + " " + rep.issues);
				Ramp wall = p.ramp(Slot.WALL);
				assertTrue(wall.ascending(0.08), s.name + ":" + r.name() + " wall ramp is not ordered dark to light");
			}
		}
	}

	@Test
	void missingBlocksFallBackSafely() {
		Material saved = Catalog.get("tuff_bricks");
		try {
			Catalog.forget("tuff_bricks");
			Ramp r = Ramp.of("tuff_bricks", "stone_bricks");
			assertEquals(1, r.size(), "a missing family is skipped in ramps");
			assertEquals(null, Ramp.of("tuff_bricks"), "and a ramp of only missing families is no ramp");
			Palette p = new Palette("test", null).set(Slot.WALL, Ramp.of("tuff_bricks"));
			MaterialContext mc = new MaterialContext(1, p);
			String id = Resolver.resolve(Cell.of(Slot.WALL), new Probe().set(0, 0, 0, 0, 0, 0, Cell.of(Slot.WALL)), mc);
			assertFalse(id.isEmpty());
			assertFalse(id.contains("tuff_brick"));
		} finally {
			Catalog.add(saved);
		}
	}

	@Test
	void formsOnlyFromFamiliesThatHaveThem() {
		Palette p = new Palette("test", null).set(Slot.ROOF, "calcite", "polished_diorite");
		MaterialContext mc = new MaterialContext(2, p);
		Cell stairs = Cell.stairs(Slot.ROOF, Dir.NORTH, false, Cell.BODY);
		for (int y = 0; y < 20; y++) {
			String id = Resolver.resolve(stairs, new Probe().set(0, y, 0, 0, y, 0, stairs), mc);
			assertTrue(id.startsWith("polished_diorite_stairs"), "calcite has no stairs: " + id);
		}
	}

	static Set<String> blocks26() throws Exception {
		Set<String> out = new HashSet<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(MaterialTest.class.getResourceAsStream("blocks-26.3.txt"), StandardCharsets.UTF_8))) {
			String l;
			while ((l = r.readLine()) != null) out.add(l.trim());
		}
		return out;
	}

	private static final List<String> COLOURS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
			"blue", "brown", "green", "red", "black");

	@Test
	void everyCatalogBlockExistsInMinecraft26() throws Exception {
		Set<String> known = blocks26();
		List<String> unknown = new java.util.ArrayList<>();
		for (String name : Catalog.blockNames()) {
			if (known.contains(name)) continue;
			// colour families are one field in the block list (stained_glass, terracotta, wool...)
			String rest = name;
			for (String c : COLOURS) {
				if (name.startsWith(c + "_") && known.contains(name.substring(c.length() + 1).replace("terracotta", "dyed_terracotta"))) rest = null;
				if (name.startsWith(c + "_") && known.contains(name.substring(c.length() + 1))) rest = null;
			}
			if (rest == null) continue;
			// copper families: weathering states and wax share one field
			String base = name.replace("waxed_", "").replace("exposed_", "").replace("weathered_", "").replace("oxidized_", "");
			if (name.contains("copper") && (known.contains(base) || known.contains(base.replace("copper", "copper_block")))) continue;
			unknown.add(name);
		}
		assertTrue(unknown.isEmpty(), "not in Minecraft 26.3: " + unknown);
	}
}
