package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FormatAndVotesTest {
	@Test
	void formatIds() {
		assertEquals(DuelFormat.BO3, DuelFormat.byId("bo3"));
		assertEquals(DuelFormat.BO3, DuelFormat.byId(" BO3 "));
		assertEquals(DuelFormat.BO3, DuelFormat.byId("3"));
		assertEquals(DuelFormat.BO1, DuelFormat.byId("bo5"));
		assertEquals(DuelFormat.BO1, DuelFormat.byId(null));
		assertEquals(DuelFormat.BO3, DuelFormat.BO1.other());
	}

	@Test
	void seriesWinner() {
		assertEquals(0, DuelFormat.BO1.winnerIndex(1, 0));
		assertEquals(-1, DuelFormat.BO3.winnerIndex(1, 0));
		assertEquals(-1, DuelFormat.BO3.winnerIndex(1, 1));
		assertEquals(1, DuelFormat.BO3.winnerIndex(1, 2));
		assertEquals(0, DuelFormat.BO3.winnerIndex(2, 1));
	}

	@Test
	void majorityWins() {
		Random r = new Random(1);
		assertEquals("hard", Votes.tally(List.of("easy", "hard", "hard", ""), r));
		assertEquals("", Votes.tally(List.of("", ""), r));
		assertEquals("", Votes.tally(List.of(), r));
	}

	@Test
	void tieIsRandomBetweenTheTop() {
		Random r = new Random(7);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String v = Votes.tally(List.of("easy", "hard", "medium", "medium", "easy"), r);
			assertTrue(v.equals("easy") || v.equals("medium"), v);
			seen.add(v);
		}
		assertEquals(2, seen.size());
	}
}
