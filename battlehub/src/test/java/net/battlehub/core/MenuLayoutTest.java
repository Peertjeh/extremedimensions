package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The fixed places of every menu (1.45). */
class MenuLayoutTest {
	@Test
	void theBottomRowIsTheSameInEveryMenu() {
		for (int rows = 3; rows <= 6; rows++) {
			int bottom = (rows - 1) * 9;
			assertEquals(bottom, MenuLayout.back(rows), "back bottom left");
			assertEquals(bottom + 4, MenuLayout.close(rows), "close bottom middle");
			assertEquals(bottom + 3, MenuLayout.previous(rows));
			assertEquals(bottom + 5, MenuLayout.next(rows));
			assertEquals(bottom + 8, MenuLayout.extra(rows));
			Set<Integer> places = Set.of(MenuLayout.back(rows), MenuLayout.close(rows), MenuLayout.previous(rows), MenuLayout.next(rows),
					MenuLayout.extra(rows), MenuLayout.TITLE, MenuLayout.WALLET);
			assertEquals(7, places.size(), "no two buttons on one place");
			for (int slot : places) assertTrue(MenuLayout.frame(slot, rows), "the fixed places are in the frame: " + slot);
		}
	}

	@Test
	void theBodyLeavesTheFrameFree() {
		for (int rows = 3; rows <= 6; rows++) {
			int[] body = MenuLayout.body(rows);
			assertEquals((rows - 2) * 7, body.length);
			Set<Integer> seen = new HashSet<>();
			for (int slot : body) {
				assertFalse(MenuLayout.frame(slot, rows), "body slot in the frame: " + slot);
				assertTrue(slot % 9 != 0 && slot % 9 != 8, "body slot in an outer column: " + slot);
				assertTrue(seen.add(slot));
			}
		}
	}
}
