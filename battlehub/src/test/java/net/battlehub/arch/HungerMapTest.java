package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.battlehub.arch.check.Walk;
import net.battlehub.arch.mod.Library;
import net.battlehub.arch.town.HungerMap;
import net.battlehub.arch.town.LobbyThemes;
import net.battlehub.core.GridPath;

/** The Hunger Games map: the lobby city of the week, with spawns, a cornucopia and chests everyone can reach. */
class HungerMapTest {
	static HungerMap map;

	@BeforeAll
	static void generate() {
		map = HungerMap.generate(HungerMap.weekSeed(20261017L, LocalDate.of(2026, 10, 21)), 150, 2, 0.85, Library.defaults());
	}

	@Test
	void theSameCityAllWeekAndAnotherOneNextWeek() {
		long base = 20261017L;
		long mon = HungerMap.weekSeed(base, LocalDate.of(2026, 10, 19));
		long sun = HungerMap.weekSeed(base, LocalDate.of(2026, 10, 25));
		long next = HungerMap.weekSeed(base, LocalDate.of(2026, 10, 26));
		assertEquals(mon, sun);
		assertNotEquals(mon, next);
		assertNotEquals(mon, HungerMap.weekSeed(base + 1, LocalDate.of(2026, 10, 19)));
		assertEquals("2026-W43", HungerMap.weekLabel(LocalDate.of(2026, 10, 21)));
		// the week of New Year belongs to the year its Thursday is in
		assertEquals("2026-W53", HungerMap.weekLabel(LocalDate.of(2027, 1, 1)));
	}

	@Test
	void sixteenSpawnsRoundTheCornucopiaOnThePlaza() {
		assertEquals(HungerMap.SPAWNS, map.spawns.size());
		assertEquals(HungerMap.CORNUCOPIA, map.cornucopia.size());
		Set<String> seen = new HashSet<>();
		for (HungerMap.Spot s : map.spawns) {
			assertTrue(seen.add(s.x() + "," + s.z()), "two spawns on one spot");
			double d = Math.hypot(s.x(), s.z());
			assertTrue(d > HungerMap.CORNUCOPIA_RING + 3 && d < 20, "spawn at " + d);
			assertTrue(stands(s), "nothing to stand on at spawn " + s);
		}
		for (HungerMap.Spot c : map.cornucopia) {
			assertTrue(map.voxels.get(c.x(), c.y(), c.z()).startsWith("chest"), "no chest at " + c);
			assertTrue(Math.hypot(c.x(), c.z()) < HungerMap.SPAWN_RING - 3);
		}
	}

	@Test
	void chestsInTheHousesAndTheLandmarks() {
		assertTrue(map.houseChests.size() >= 60, "house chests: " + map.houseChests.size());
		assertTrue(map.landmarkChests.size() >= 12, "landmark chests: " + map.landmarkChests.size());
		for (HungerMap.Spot s : map.landmarkChests) {
			assertTrue(map.voxels.get(s.x(), s.y(), s.z()).startsWith("chest"));
			assertTrue(HungerMap.full(map.voxels.get(s.x(), s.y() - 1, s.z())), "landmark chest floats: " + s);
		}
		assertTrue(map.doorsRemoved > 50, "doors: " + map.doorsRemoved);
		map.voxels.forEach((x, y, z, id) -> assertTrue(!id.contains("_door["), "a door is left at " + x + "," + y + "," + z));
	}

	@Test
	void fromTheSpawnsYouCanWalkToTheLandmarkChestsAndMostHouseChests() {
		GridPath.Terrain walk = Walk.of(map.voxels);
		HungerMap.Spot from = map.spawns.get(0);
		GridPath.Node start = new GridPath.Node(from.x(), from.y(), from.z());
		int reached = 0;
		for (HungerMap.Spot c : map.landmarkChests) {
			if (GridPath.find(walk, start, new GridPath.Node(c.x(), c.y(), c.z()), 2.5, 400_000) != null) reached++;
		}
		assertTrue(reached >= map.landmarkChests.size() * 2 / 3, "landmark chests reached: " + reached + " of " + map.landmarkChests.size());
		int houses = 0, tried = 0;
		for (int i = 0; i < map.houseChests.size(); i += 6) {
			HungerMap.Spot c = map.houseChests.get(i);
			tried++;
			if (GridPath.find(walk, start, new GridPath.Node(c.x(), c.y(), c.z()), 2.5, 400_000) != null) houses++;
		}
		assertTrue(houses >= tried * 2 / 3, "house chests reached: " + houses + " of " + tried);
	}

	@Test
	void everyThemeKeepsTheLayoutAndUsesRealBlocks() throws Exception {
		Set<String> blocks = MaterialTest.blocks26();
		List<String> names = map.voxels.names();
		Set<String> unknown = new TreeSet<>();
		for (String theme : LobbyThemes.THEMES) {
			int changed = 0;
			for (String id : names) {
				String t = LobbyThemes.block(id, theme);
				assertNotNull(t);
				if (!t.equals(id)) changed++;
				String base = t.indexOf('[') < 0 ? t : t.substring(0, t.indexOf('['));
				if (!t.equals(id) && !t.equals("air") && !blocks.contains(base) && !COLOURED.matcher(base).matches()) unknown.add(theme + ": " + id + " -> " + t);
				// same shape: stairs stay stairs, slabs stay slabs, a leaf stays a leaf or becomes a block, never air for stone
				if (id.contains("_stairs") && blocks.contains(base)) assertTrue(t.contains("_stairs") || !t.contains("["), theme + ": " + id + " -> " + t);
				if (id.contains("_slab[") && !t.equals("air")) assertTrue(t.contains("_slab"), theme + ": " + id + " -> " + t);
				if (id.startsWith("stone_bricks") || id.startsWith("cobblestone") || id.startsWith("spruce_planks")) assertNotEquals("air", t);
			}
			if (theme.equals("castle")) assertEquals(0, changed);
			else assertTrue(changed >= 15, theme + " changes only " + changed + " blocks");
		}
		assertTrue(unknown.isEmpty(), "blocks that do not exist: " + unknown);
	}

	@Test
	void themesDressTheCityToMatch() {
		assertEquals("sand", LobbyThemes.block("grass_block", "desert"));
		assertEquals("sandstone_stairs[facing=north,half=bottom,shape=straight]",
				LobbyThemes.block("stone_brick_stairs[facing=north,half=bottom,shape=straight]", "desert"));
		assertEquals("snow_block", LobbyThemes.block("grass_block", "ice"));
		assertEquals("spruce_leaves[persistent=true]", LobbyThemes.block("oak_leaves[persistent=true]", "ice"));
		assertEquals("crimson_stem[axis=y]", LobbyThemes.block("spruce_log[axis=y]", "nether"));
		assertEquals("stripped_warped_stem[axis=x]", LobbyThemes.block("stripped_dark_oak_log[axis=x]", "nether"));
		assertEquals("nether_brick_wall[up=true]", LobbyThemes.block("stone_brick_wall[up=true]", "nether"));
		assertEquals("cherry_trapdoor[facing=east,half=bottom,open=true]", LobbyThemes.block("spruce_trapdoor[facing=east,half=bottom,open=true]", "cherry"));
		assertEquals("end_stone_brick_slab[type=top]", LobbyThemes.block("stone_brick_slab[type=top]", "end"));
		assertEquals("purpur_block", LobbyThemes.block("smooth_stone", "end"));
		assertEquals("chest[facing=east]", LobbyThemes.block("chest[facing=east]", "nether"));
		assertEquals("water", LobbyThemes.block("water", "desert"));
		assertTrue(LobbyThemes.supports("deep_dark"));
		assertTrue(!LobbyThemes.supports("sky"));
	}

	/** The block list leaves out the sixteen colours of terracotta (and the like). */
	private static final java.util.regex.Pattern COLOURED = java.util.regex.Pattern.compile(
			"(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)_terracotta");

	private static boolean stands(HungerMap.Spot s) {
		String below = map.voxels.get(s.x(), s.y() - 1, s.z());
		return below != null && !below.equals("air") && !map.voxels.filled(s.x(), s.y(), s.z()) && !map.voxels.filled(s.x(), s.y() + 1, s.z());
	}
}
