package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EloTest {
	@Test
	void equalRatingsSplitTheKFactor() {
		assertEquals(0.5, Elo.expected(1000, 1000), 1e-9);
		int[] r = Elo.afterWin(1000, 1000, 32, 0);
		assertEquals(1016, r[0]);
		assertEquals(984, r[1]);
	}

	@Test
	void upsetGainsMoreThanExpectedWin() {
		int upset = Elo.delta(1000, 1400, 1.0, 32);
		int expectedWin = Elo.delta(1400, 1000, 1.0, 32);
		assertTrue(upset > 25, "upset gain " + upset);
		assertTrue(expectedWin < 7, "favourite gain " + expectedWin);
		assertEquals(32, upset + expectedWin);
	}

	@Test
	void ratingsAreZeroSumAndRespectTheFloor() {
		int[] r = Elo.afterWin(1234, 987, 32, 0);
		assertEquals(1234 + 987, r[0] + r[1]);
		int[] floored = Elo.afterWin(20, 20, 32, 10);
		assertEquals(10, floored[1]);
		assertEquals(36, floored[0]);
	}

	@Test
	void drawMovesTowardsTheMiddle() {
		assertTrue(Elo.delta(1000, 1200, 0.5, 32) > 0);
		assertTrue(Elo.delta(1200, 1000, 0.5, 32) < 0);
	}

	@Test
	void tiersFollowThresholds() {
		RankTiers tiers = new RankTiers(RankTiers.defaults());
		assertEquals("bronze", tiers.tierFor(0).id());
		assertEquals("bronze", tiers.tierFor(1099).id());
		assertEquals("silver", tiers.tierFor(1100).id());
		assertEquals("gold", tiers.tierFor(1300).id());
		assertEquals("diamond", tiers.tierFor(1500).id());
		assertEquals("netherite", tiers.tierFor(3000).id());
		assertEquals("silver", tiers.nextTier(1000).id());
		assertEquals(null, tiers.nextTier(5000));
	}
}
