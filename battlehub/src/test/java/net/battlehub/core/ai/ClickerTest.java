package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** Clicking like a human: beginners spam, experts wait, nobody is exact, and swings start from a little too far. */
class ClickerTest {
	private static double clickRate(Difficulty d, double charge, double dist) {
		Random r = new Random(3);
		int n = 0;
		for (int i = 0; i < 2000; i++) {
			Clicker c = new Clicker(d, r);
			if (c.wants(charge, dist, 3.0, true)) n++;
		}
		return n / 2000.0;
	}

	@Test
	void beginnersSpamExpertsWait() {
		// at 60% charge a beginner often clicks already, an expert never
		assertTrue(clickRate(Difficulty.BEGINNER, 0.6, 2.8) > 0.1);
		assertTrue(clickRate(Difficulty.EXPERT, 0.6, 2.8) == 0);
		// fully charged both click, the expert more promptly
		assertTrue(clickRate(Difficulty.EXPERT, 1, 2.8) > clickRate(Difficulty.BEGINNER, 1, 2.8));
	}

	@Test
	void theChargeWaitedForDiffersFromClickToClick() {
		Clicker c = new Clicker(Difficulty.INTERMEDIATE, new Random(9));
		double lo = 1, hi = 0;
		for (int i = 0; i < 200; i++) {
			lo = Math.min(lo, c.threshold());
			hi = Math.max(hi, c.threshold());
			c.clicked();
		}
		assertTrue(hi - lo > 0.15, lo + " .. " + hi);
		assertTrue(lo >= 0.3 && hi <= 1.0);
	}

	@Test
	void lowerLevelsSwingFromFurtherAway() {
		// 3.5 blocks with a reach of 3: a beginner sometimes swings at the air, a master does not
		assertTrue(clickRate(Difficulty.BEGINNER, 1, 3.5) > 0.05);
		assertTrue(clickRate(Difficulty.MASTER, 1, 3.5) == 0);
	}
}
