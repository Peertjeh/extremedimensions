package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What a learned decision costs: average, 99th percentile and maximum, and the part the masks took. */
class DecisionTimerTest {
	@Test
	void averagePercentileMaximumAndMasks() {
		DecisionTimer t = new DecisionTimer();
		assertEquals(0, t.averageMs());
		for (int i = 1; i <= 100; i++) t.record(i * 10_000L, 1_000L);
		assertEquals(100, t.count());
		assertEquals(0.505, t.averageMs(), 1e-9);
		assertEquals(1.0, t.maxMs(), 1e-9);
		assertEquals(0.99, t.percentileMs(99), 1e-9);
		assertEquals(0.5, t.percentileMs(50), 1e-9);
		assertEquals(0.001, t.maskMs(), 1e-9);
		assertTrue(t.summary().startsWith("0.505 ms per decision (p99 0.990, max 1.000; masks 0.0010) over 100"), t.summary());
	}

	@Test
	void theWindowKeepsTheRecentOnesAndTheTotalsKeepAll() {
		DecisionTimer t = new DecisionTimer();
		t.record(50_000_000L, 0);
		for (int i = 0; i < DecisionTimer.WINDOW; i++) t.record(100_000L, 0);
		// the slow one left the window but stays the maximum and in the average
		assertEquals(0.1, t.percentileMs(99), 1e-9);
		assertEquals(50.0, t.maxMs(), 1e-9);
		assertTrue(t.averageMs() > 0.1);
		t.reset();
		assertEquals(0, t.count());
	}
}
