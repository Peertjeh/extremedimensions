package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MatchmakerTest {
	private static UUID id(int n) {
		return new UUID(0, n);
	}

	@Test
	void casualPairsInArrivalOrder() {
		Matchmaker q = new Matchmaker();
		q.add(id(1), 1000, 0);
		q.add(id(2), 2000, 1);
		q.add(id(3), 500, 2);
		assertFalse(q.add(id(1), 1000, 3), "no double entries");
		List<Matchmaker.Pair> pairs = q.match(10, null);
		assertEquals(1, pairs.size());
		assertEquals(new Matchmaker.Pair(id(1), id(2)), pairs.get(0));
		assertEquals(1, q.size());
		assertTrue(q.contains(id(3)));
	}

	@Test
	void rankedWaitsForCloseRatingsThenWidens() {
		Matchmaker.Window w = new Matchmaker.Window(100, 10, 500);
		Matchmaker q = new Matchmaker();
		q.add(id(1), 1000, 0);
		q.add(id(2), 1300, 0);
		assertTrue(q.match(0, w).isEmpty(), "300 apart is outside the start window");
		// After 20 seconds the window is 100 + 200 = 300
		assertEquals(1, q.match(20 * 20, w).size());
		assertEquals(0, q.size());
	}

	@Test
	void rankedPrefersTheClosestOpponent() {
		Matchmaker.Window w = new Matchmaker.Window(200, 0, 200);
		Matchmaker q = new Matchmaker();
		q.add(id(1), 1000, 0);
		q.add(id(2), 1150, 1);
		q.add(id(3), 1020, 2);
		List<Matchmaker.Pair> pairs = q.match(5, w);
		assertEquals(1, pairs.size());
		assertEquals(new Matchmaker.Pair(id(1), id(3)), pairs.get(0));
		assertTrue(q.contains(id(2)));
	}

	@Test
	void removeLeavesQueue() {
		Matchmaker q = new Matchmaker();
		q.add(id(1), 1000, 0);
		assertTrue(q.remove(id(1)));
		assertFalse(q.remove(id(1)));
		assertEquals(0, q.size());
	}

	@Test
	void windowIsCapped() {
		Matchmaker.Window w = new Matchmaker.Window(50, 25, 300);
		assertEquals(50, w.at(0));
		assertEquals(75, w.at(20));
		assertEquals(300, w.at(20 * 1000));
	}
}
