package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** Bots loot at a player's pace: seconds per chest, not all at once, and slower on the lower levels. */
class LootPaceTest {
	@Test
	void aChestTakesSecondsAndLowerLevelsAreSlower() {
		LootPace.Pace beginner = LootPace.of(0), master = LootPace.of(5);
		// a SkyWars chest with eight stacks
		assertTrue(LootPace.chestTicks(beginner, 8) >= 120, "a beginner needs about six seconds");
		assertTrue(LootPace.chestTicks(master, 8) >= 60, "even a master needs about three");
		for (int l = 1; l <= 5; l++) {
			assertTrue(LootPace.of(l).item() <= LootPace.of(l - 1).item());
			assertTrue(LootPace.of(l).equip() <= LootPace.of(l - 1).equip());
		}
		assertTrue(master.equip() >= 15, "putting on a piece of armor takes most of a second or more");
		assertEquals(LootPace.of(5), LootPace.of(9));
	}

	@Test
	void everyStepVariesALittle() {
		Random rng = new Random(3);
		int lo = Integer.MAX_VALUE, hi = 0;
		for (int i = 0; i < 500; i++) {
			int t = LootPace.vary(8, rng);
			lo = Math.min(lo, t);
			hi = Math.max(hi, t);
		}
		assertEquals(6, lo);
		assertEquals(10, hi);
		assertEquals(2, LootPace.vary(1, rng), "never under two ticks");
	}
}
