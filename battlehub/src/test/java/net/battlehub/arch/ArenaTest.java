package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.arena.ArenaCheck;
import net.battlehub.arch.arena.ArenaPlan;
import net.battlehub.arch.arena.BedWars;
import net.battlehub.arch.arena.Dress;
import net.battlehub.arch.check.Report;
import net.battlehub.arch.mod.Library;

/** Bed Wars maps from the architecture engine: sound, fair, reproducible, varied, and dressed in every theme. */
class ArenaTest {
	static final Library LIB = Library.defaults();

	static ArenaPlan map(String theme, int teams, long seed, boolean backdrop) {
		BedWars.Settings s = new BedWars.Settings();
		s.theme = theme;
		s.teams = teams;
		s.seed = seed;
		s.backdrop = backdrop;
		return BedWars.generate(s, LIB);
	}

	@Test
	void everyThemeMakesASoundFourTeamMap() {
		List<String> problems = new ArrayList<>();
		for (String t : Dress.ids()) {
			Report r = ArenaCheck.bedwars(map(t, 4, 31, true), 4);
			if (!r.ok()) problems.add(t + ": " + r.errors());
		}
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(13, Dress.ids().size());
		assertTrue(Dress.unknown().isEmpty(), "unknown blocks in the themes: " + Dress.unknown());
	}

	@Test
	void everyTeamCountWorks() {
		for (int teams = 2; teams <= 8; teams++) {
			ArenaPlan p = map("castle", teams, 40 + teams, false);
			Report r = ArenaCheck.bedwars(p, teams);
			assertTrue(r.ok(), teams + " teams: " + r.errors());
			assertEquals(teams, p.spawns.size());
			for (int i = 0; i < teams; i++) {
				for (String m : new String[] {"spawn_", "bed_", "gen_", "shop_", "shopblock_"}) assertNotNull(p.mark(m + i), m + i);
			}
			assertEquals(Math.min(teams, 4), p.marks("gen_diamond").size());
			assertEquals(2, p.marks("gen_emerald").size());
		}
	}

	@Test
	void allTeamsGetTheSameIslandAlsoWhenMirrored() {
		for (int teams : new int[] {2, 4, 8}) {
			ArenaPlan p = map("cherry", teams, 7, false);
			Report r = ArenaCheck.bedwars(p, teams);
			assertEquals(0.0, r.metrics.get("unfair:team"), teams + " teams");
			assertEquals(0.0, r.metrics.get("unfair:diamond"), teams + " teams");
		}
		// eight teams sit on an octagon: half the islands are mirror images of the other half
		ArenaPlan eight = map("forest", 8, 3, false);
		long mirrored = eight.isles("team").stream().filter(i -> i.frame().mirror()).count();
		assertEquals(4, mirrored);
	}

	@Test
	void theBedHasRoomToBuildRoundItAndTheGeneratorIsCoveredButOpen() {
		ArenaPlan p = map("desert", 4, 12, false);
		Report r = ArenaCheck.bedwars(p, 4);
		assertEquals(0.0, r.metrics.get("zoneBlocks"));
		assertTrue(r.metrics.get("coverAtGenerator") > 40, "cover " + r.metrics.get("coverAtGenerator"));
		assertTrue(r.metrics.get("highGround") >= 4, "high ground " + r.metrics.get("highGround"));
		int[] bed = p.mark("bed_0");
		String foot = p.voxels.get(bed[0], bed[1], bed[2]);
		assertTrue(foot.startsWith("red_bed") && foot.contains("part=foot"), foot);
		// nothing over the bed for four blocks: it can be roofed over by its defenders
		for (int y = 1; y <= 4; y++) assertTrue(!p.voxels.filled(bed[0], bed[1] + y, bed[2]) || p.voxels.get(bed[0], bed[1] + y, bed[2]).equals("air"));
	}

	@Test
	void everyLaneHasAFreeCoastToBridgeFrom() {
		for (int teams : new int[] {2, 3, 4, 6, 8}) {
			Report r = ArenaCheck.bedwars(map("jungle", teams, 77 + teams, false), teams);
			assertEquals(0.0, r.metrics.get("lanesBlocked"), teams + " teams");
		}
	}

	@Test
	void theSameSeedGivesTheSameMapAndOtherSeedsDiffer() {
		ArenaPlan a = map("nether", 4, 5, true), b = map("nether", 4, 5, true), c = map("nether", 4, 6, true);
		assertEquals(a.voxels.size(), b.voxels.size());
		a.voxels.forEach((x, y, z, id) -> assertEquals(id, b.voxels.get(x, y, z)));
		assertNotEquals(a.voxels.counts(), c.voxels.counts());
	}

	@Test
	void theBackdropStaysOutOfThePlayArea() {
		ArenaPlan p = map("castle", 4, 9, true);
		int edge = BedWars.teamRadius(4) + BedWars.BUILD_MARGIN;
		int[] stray = {0};
		p.voxels.forEach((x, y, z, id) -> {
			if (Math.abs(x) > edge + 2 || Math.abs(z) > edge + 2 || id.equals("air")) return;
			boolean onIsle = false;
			for (ArenaPlan.Isle i : p.isles) onIsle |= Math.hypot(x - i.cx(), z - i.cz()) <= i.radius() * 1.3 + 2;
			if (!onIsle && y > -40) stray[0]++;
		});
		assertEquals(0, stray[0], "backdrop blocks inside the build box");
		assertTrue(p.backdrop.size() >= 4, "landmarks and islets: " + p.backdrop.size());
	}

	@Test
	void everyBlockNameExistsInMinecraft26() throws Exception {
		Set<String> known = MaterialTest.blocks26();
		Set<String> unknown = new HashSet<>();
		for (String t : Dress.ids()) {
			map(t, 4, 3, true).voxels.forEach((x, y, z, id) -> {
				String base = Props.base(id);
				if (known.contains(base)) return;
				// colour families are one field in the list (red_wool → wool, red_wall_banner → wall_banner)
				for (String c : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
						"brown", "green", "red", "black")) {
					if (base.startsWith(c + "_") && (known.contains(base.substring(c.length() + 1)) || known.contains(base.substring(c.length() + 1).replace("terracotta", "dyed_terracotta")))) return;
				}
				String copper = base.replace("waxed_", "").replace("exposed_", "").replace("weathered_", "").replace("oxidized_", "");
				if (base.contains("copper") && (known.contains(copper) || known.contains(copper.replace("copper", "copper_block")))) return;
				unknown.add(base);
			});
		}
		assertTrue(unknown.isEmpty(), "not in Minecraft 26.3: " + unknown);
	}

	@Test
	void themesHaveTheirOwnMaterialsAndVariants() {
		Set<String> grounds = new HashSet<>();
		for (String t : Dress.ids()) {
			Dress d = Dress.of(t, 1);
			for (Slot s : new Slot[] {Slot.GROUND, Slot.ROCK, Slot.PAVING, Slot.WALL, Slot.ROOF, Slot.PILLAR, Slot.TRIM}) {
				assertNotNull(d.palette.ramp(s), t + " has no " + s);
			}
			grounds.add(d.palette.ramp(Slot.GROUND).get(0).name() + "/" + d.palette.ramp(Slot.WALL).get(0).name() + "/"
					+ (d.palette.ramp(Slot.LEAVES) == null ? "-" : d.palette.ramp(Slot.LEAVES).get(0).name()));
			assertTrue(d.variants().size() >= 2, t + " variants");
		}
		assertEquals(13, grounds.size(), "every theme looks different: " + grounds);
	}
}
