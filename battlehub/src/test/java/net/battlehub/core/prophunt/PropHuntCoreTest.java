package net.battlehub.core.prophunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PropHuntCoreTest {
	@Test
	void alwaysAtLeastOneHunterAndOneProp() {
		assertEquals(1, Roles.hunterCount(2, 0.25));
		assertEquals(1, Roles.hunterCount(4, 0.25));
		assertEquals(2, Roles.hunterCount(8, 0.25));
		assertEquals(3, Roles.hunterCount(4, 0.9), "one prop is always left");
		assertEquals(1, Roles.hunterCount(3, 0));
		assertEquals(0, Roles.hunterCount(1, 0.5));
	}

	@Test
	void everyoneHuntsInTurn() {
		List<String> players = List.of("a", "b", "c", "d");
		Map<String, Integer> hunted = new HashMap<>();
		Random r = new Random(4);
		for (int round = 0; round < 4; round++) {
			Set<String> h = Roles.pickHunters(players, 0.25, hunted, r);
			assertEquals(1, h.size());
			for (String p : h) hunted.merge(p, 1, Integer::sum);
		}
		for (String p : players) assertEquals(1, hunted.get(p), p + " hunted once in four rounds");
	}

	@Test
	void cooldowns() {
		Cooldowns<String> c = new Cooldowns<>();
		assertTrue(c.use("taunt", 0, 100));
		assertFalse(c.use("taunt", 50, 100));
		assertEquals(50, c.ticksLeft("taunt", 50));
		assertTrue(c.use("taunt", 100, 100));
		assertTrue(c.use("decoy", 100, 100), "separate keys");
		c.reset("taunt");
		assertTrue(c.ready("taunt", 101));
	}

	@Test
	void pointsAndWinners() {
		Scoreboard<String> s = new Scoreboard<>();
		assertTrue(s.winners(List.of("a", "b")).isEmpty(), "nobody scored");
		s.add("a", 100);
		s.add("b", 40);
		s.add("b", 60);
		s.add("c", -5);
		assertEquals(List.of("a", "b"), s.winners(List.of("a", "b", "c")));
		assertEquals("c", s.ranking(List.of("c", "a", "b")).get(2));
	}
}
