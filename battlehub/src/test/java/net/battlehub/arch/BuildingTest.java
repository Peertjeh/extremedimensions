package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.bldg.Architect;
import net.battlehub.arch.bldg.Archetype;
import net.battlehub.arch.bldg.Brief;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.check.Report;
import net.battlehub.arch.check.Validator;
import net.battlehub.arch.mat.Catalog;
import net.battlehub.arch.mat.Material;
import net.battlehub.arch.mat.Probe;
import net.battlehub.arch.mat.Ramp;
import net.battlehub.arch.mat.Resolver;
import net.battlehub.arch.mod.Library;

/** Whole buildings: reproducible, varied, complete, sound, with roles kept and decoration optional. */
class BuildingTest {
	static final Library LIB = Library.defaults();

	static Building build(String archetype, long seed) {
		return build(archetype, seed, 2);
	}

	static Building build(String archetype, long seed, int detail) {
		Brief br = Architect.brief(archetype, seed, TestPalettes.dimension(), 1.0);
		br.detail = detail;
		return Architect.build(br, LIB, Xform.ID, null);
	}

	static Voxels emit(Building b) {
		Voxels v = new Voxels();
		b.emit(v);
		return v;
	}

	static long fingerprint(Voxels v) {
		long[] h = {17};
		v.forEach((x, y, z, id) -> h[0] += Rng.mix(Plan.key(x, y, z) * 31 + id.hashCode()));
		return h[0];
	}

	@Test
	void sameSeedSameBuilding() {
		for (Archetype a : Architect.all()) {
			long f1 = fingerprint(emit(build(a.id(), 42)));
			long f2 = fingerprint(emit(build(a.id(), 42)));
			assertEquals(f1, f2, a.id() + " is not reproducible");
		}
	}

	@Test
	void differentSeedsGiveDifferentDesigns() {
		for (Archetype a : Architect.all()) {
			Set<Long> prints = new HashSet<>();
			Set<String> choices = new HashSet<>();
			Set<Integer> heights = new HashSet<>();
			for (long seed = 1; seed <= 8; seed++) {
				Building b = build(a.id(), seed);
				prints.add(fingerprint(emit(b)));
				choices.add(b.brief.choices.toString() + b.brief.palette.name() + b.brief.width + "x" + b.brief.depth + "x" + b.brief.height);
				heights.add(b.plan.maxY() - b.floorY);
			}
			assertEquals(8, prints.size(), a.id() + " repeats itself");
			assertTrue(choices.size() >= 4, a.id() + " varies too little in its choices: " + choices);
			assertTrue(heights.size() >= 2 || a.id().equals("market_hall"), a.id() + " always has the same height");
		}
	}

	@Test
	void everyArchetypePassesItsChecks() {
		for (Archetype a : Architect.all()) {
			for (long seed = 1; seed <= 6; seed++) {
				Building b = build(a.id(), seed);
				Report r = Validator.building(b);
				assertTrue(r.ok(), a.id() + " seed " + seed + ": " + r.errors());
				assertTrue(Validator.floating(b) <= Math.max(8, b.plan.size() / 500), a.id() + " seed " + seed + " has " + Validator.floating(b) + " floating blocks");
			}
		}
	}

	@Test
	void requiredComponentsAreThere() {
		for (long seed = 1; seed <= 6; seed++) {
			Building c = build("cathedral", seed);
			Map<String, Integer> m = c.modulesUsed();
			assertTrue(m.containsKey("door.portal"), "a cathedral has a portal");
			assertTrue(m.containsKey("int.nave"), "a cathedral has pews and an altar");
			assertTrue(m.getOrDefault("pier.buttress", 0) >= 4, "a cathedral has buttresses");
			assertTrue(c.volume("nave") != null);
			if (Boolean.TRUE.equals(c.brief.choices.get("transept"))) assertTrue(c.volume("transept") != null);
			Building k = build("castle", seed);
			assertTrue(k.volume("keep") != null && k.volume("curtain") != null && k.volume("hall") != null);
			assertTrue(k.modulesUsed().containsKey("door.gate"), "a castle has a gate");
			assertTrue(k.volumes.stream().filter(v -> v.role == net.battlehub.arch.bldg.Volume.Role.TOWER).count() >= 4, "four towers at least");
			Building t = build("mage_tower", seed);
			assertTrue(t.modulesUsed().containsKey("door.arcane") || !t.attachments(net.battlehub.arch.mod.Attachment.Kind.ENTRANCE).isEmpty());
			assertFalse(t.attachments(net.battlehub.arch.mod.Attachment.Kind.LANDING).isEmpty(), "a mage tower has a stair");
		}
	}

	@Test
	void stairsConnectTheirLevels() {
		for (String a : List.of("mage_tower", "watchtower", "chapel")) {
			for (long seed = 1; seed <= 6; seed++) {
				Report r = Validator.building(build(a, seed));
				assertEquals(0, r.count("stairs"), a + " seed " + seed + ": " + r.issues);
			}
		}
	}

	@Test
	void roofsCoverTheirWalls() {
		for (String a : List.of("townhouse", "cathedral", "chapel", "market_hall", "castle")) {
			for (long seed = 1; seed <= 6; seed++) {
				Report r = Validator.building(build(a, seed));
				assertEquals(0, r.count("roof"), a + " seed " + seed + ": " + r.issues + " " + r.metrics);
			}
		}
	}

	@Test
	void rolesKeepTheirMaterials() {
		Building b = build("cathedral", 3);
		var mc = b.materials();
		Probe p = new Probe();
		int[] checked = {0};
		b.plan.forEach((x, y, z, c) -> {
			if (c.air() || c.slot() == Slot.LITERAL || c.slot() == Slot.WATER) return;
			p.set(x, y, z, x, y, z, c);
			String id = Resolver.resolve(c, p, mc);
			Material m = Catalog.ofBlock(id);
			if (c.slot() == Slot.GLASS || c.slot() == Slot.STAINED) {
				assertTrue(m != null && m.is("glass"), "glass stays glass, got " + id);
			}
			Ramp ramp = b.brief.palette.ramp(c.slot());
			if (ramp != null && m != null && c.slot() != Slot.LEAVES) {
				boolean inRamp = false;
				for (Ramp.Entry e : ramp.entries()) {
					Material rm = e.material();
					inRamp |= rm == m || rm.aged().contains(m.name()) || m.name().equals(rm.alt()) || rm.name().equals(m.alt());
					// the stand-in family for a missing form, or a weathered variant of one
					Material alt = rm.alt() == null ? null : Catalog.get(rm.alt());
					inRamp |= alt != null && (alt == m || alt.aged().contains(m.name()));
				}
				// or the nearest-looking family that has the form the ramp lacks
				boolean nearest = false;
				for (Ramp.Entry e : ramp.entries()) nearest |= Catalog.nearestWith(e.material(), c.shape()) == m;
				assertTrue(inRamp || nearest, c.slot() + " became " + id + ", not from its ramp");
			}
			checked[0]++;
		});
		assertTrue(checked[0] > 5000);
	}

	@Test
	void decorationCanBeLeftOutWithoutTouchingTheStructure() {
		for (String a : List.of("cathedral", "townhouse", "mage_tower", "castle")) {
			Building bare = build(a, 5, 0);
			Building rich = build(a, 5, 2);
			int[] decorBare = {0};
			bare.plan.forEach((x, y, z, c) -> {
				if (c.is(Cell.DECOR)) decorBare[0]++;
			});
			assertEquals(0, decorBare[0], a + ": decoration at detail 0");
			// every structural block of the bare building is the same in the rich one
			int[] differ = {0};
			bare.plan.forEach((x, y, z, c) -> {
				if (c.air() || c.slot() == Slot.LITERAL) return;
				Cell r = rich.plan.get(x, y, z);
				if (r == null || r.slot() != c.slot() && !r.is(Cell.DECOR)) differ[0]++;
			});
			assertTrue(differ[0] <= bare.plan.size() / 200, a + ": " + differ[0] + " structural blocks change with the detail");
			assertTrue(rich.plan.size() > bare.plan.size(), a + ": more detail adds blocks");
		}
	}

	@Test
	void moreDetailKeepsTheSilhouette() {
		for (String a : List.of("cathedral", "castle")) {
			Building low = build(a, 8, 1);
			Building high = build(a, 8, 2);
			assertEquals(low.volumes.size(), high.volumes.size());
			for (int i = 0; i < low.volumes.size(); i++) {
				assertEquals(low.volumes.get(i).toString(), high.volumes.get(i).toString(), a + ": massing changed with detail");
			}
		}
	}

	@Test
	void conditionTurnsABuildingIntoARuinButKeepsItsPlan() {
		Brief intact = Architect.brief("temple_ruin", 4, TestPalettes.dimension(), 1.0);
		intact.condition(net.battlehub.arch.bldg.Condition.NEW);
		Building a = Architect.build(intact, LIB, Xform.ID, null);
		Building r = build("temple_ruin", 4);
		assertNotEquals(a.brief.condition, r.brief.condition);
		assertEquals(a.volumes.toString(), r.volumes.toString(), "the same temple, only aged");
		assertTrue(emit(r).size() < emit(a).size() + 400, "a ruin has lost stone");
	}
}
