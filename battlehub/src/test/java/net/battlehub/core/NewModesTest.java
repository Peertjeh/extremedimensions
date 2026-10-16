package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NewModesTest {
	@Test
	void menuSlotsAreCentredAndUnique() {
		assertArrayEquals(new int[] {11, 12, 13, 14, 15}, MenuSlots.centered(5, 1, 5));
		assertArrayEquals(new int[] {11, 12, 13, 14, 15, 20, 22, 24}, MenuSlots.centered(8, 1, 5));
		assertArrayEquals(new int[] {13}, MenuSlots.centered(1, 1, 7));
		assertArrayEquals(new int[] {12, 14}, MenuSlots.centered(2, 1, 7));
		int[] thirteen = MenuSlots.centered(13, 1, 7);
		assertEquals(10, thirteen[0]);
		assertEquals(16, thirteen[6]);
		assertEquals(19, thirteen[7]);
		Set<Integer> seen = new HashSet<>();
		for (int n = 1; n <= 28; n++) {
			seen.clear();
			for (int s : MenuSlots.centered(n, 1, 7)) {
				assertTrue(seen.add(s));
				int col = s % 9;
				assertTrue(col >= 1 && col <= 7, "inside the border: " + s);
			}
		}
	}

	@Test
	void mapMarkers() {
		assertEquals(new MapMarkers.Marker("spawn", 0), MapMarkers.parse("spawn"));
		assertEquals(new MapMarkers.Marker("spawn", 90), MapMarkers.parse(" Spawn:90 "));
		assertEquals(new MapMarkers.Marker("goal_red", 0), MapMarkers.parse("goal red"));
		assertEquals(new MapMarkers.Marker("spawn", 0), MapMarkers.parse("spawn:west"));
		assertNull(MapMarkers.parse(""));
		assertNull(MapMarkers.parse(null));
		assertNull(MapMarkers.parse(":90"));
		assertEquals("sky_castle_2", MapMarkers.id("Sky Castle-2.nbt"));
	}

	@Test
	void murderRoles() {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 6; i++) ids.add(new UUID(0, i));
		Random rnd = new Random(4);
		for (int i = 0; i < 50; i++) {
			MurderRoles.Roles r = MurderRoles.pick(ids, rnd);
			assertNotEquals(r.murderer(), r.detective());
			assertTrue(ids.contains(r.murderer()) && ids.contains(r.detective()));
		}
		MurderRoles.Roles r = new MurderRoles.Roles(ids.get(0), ids.get(1));
		assertEquals(MurderRoles.Role.MURDERER, r.of(ids.get(0)));
		assertEquals(MurderRoles.Role.DETECTIVE, r.of(ids.get(1)));
		assertEquals(MurderRoles.Role.INNOCENT, r.of(ids.get(2)));
		assertEquals(MurderRoles.Role.INNOCENT, MurderRoles.winner(r, List.of(ids.get(1), ids.get(2))));
		assertEquals(MurderRoles.Role.MURDERER, MurderRoles.winner(r, List.of(ids.get(0))));
		assertNull(MurderRoles.winner(r, List.of(ids.get(0), ids.get(3))));
	}

	@Test
	void lootRollsByWeight() {
		List<Loot.Entry> table = List.of(Loot.Entry.parse("minecraft:stone 16-32 x9"), Loot.Entry.parse("minecraft:diamond 1 x1"));
		assertEquals(new Loot.Entry("minecraft:stone", 16, 32, 9), table.get(0));
		assertEquals(new Loot.Entry("minecraft:diamond", 1, 1, 1), table.get(1));
		assertEquals(new Loot.Entry("minecraft:apple", 1, 1, 1), Loot.Entry.parse("minecraft:apple"));
		Random rnd = new Random(1);
		int stone = 0;
		for (Loot.Pick p : Loot.roll(table, 1000, rnd)) {
			if (p.item().equals("minecraft:stone")) {
				stone++;
				assertTrue(p.count() >= 16 && p.count() <= 32);
			} else {
				assertEquals(1, p.count());
			}
		}
		assertTrue(stone > 850 && stone < 950, "about 90% stone: " + stone);
		assertTrue(Loot.roll(List.of(), 5, rnd).isEmpty());
	}
}
