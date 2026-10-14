package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BracketTest {
	@Test
	void teamCounts() {
		assertEquals(16, Bracket.teamCount(16, 1));
		assertEquals(8, Bracket.teamCount(16, 2));
		assertEquals(4, Bracket.teamCount(16, 4));
		assertEquals(2, Bracket.teamCount(16, 8));
		// Fewer players than two full teams: still two teams
		assertEquals(2, Bracket.teamCount(6, 4));
		assertEquals(2, Bracket.teamCount(3, 8));
		// Leftover players are spread over the teams
		assertEquals(3, Bracket.teamCount(7, 2));
		assertEquals(2, Bracket.teamCount(10, 4));
		assertEquals(5, Bracket.teamCount(5, 1));
		assertEquals(1, Bracket.teamCount(1, 1));
	}

	@Test
	void teamsUseEveryPlayerOnceAndAreBalanced() {
		Random r = new Random(3);
		for (int players = 2; players <= 16; players++) {
			for (int size : new int[] {1, 2, 4, 8}) {
				List<Integer> ps = new ArrayList<>();
				for (int i = 0; i < players; i++) ps.add(i);
				List<List<Integer>> teams = Bracket.teams(ps, size, r);
				assertEquals(Bracket.teamCount(players, size), teams.size());
				Set<Integer> seen = new HashSet<>();
				int min = Integer.MAX_VALUE;
				int max = 0;
				for (List<Integer> t : teams) {
					assertFalse(t.isEmpty());
					min = Math.min(min, t.size());
					max = Math.max(max, t.size());
					for (int p : t) assertTrue(seen.add(p), "player twice");
				}
				assertEquals(players, seen.size());
				assertTrue(max - min <= 1, "unbalanced: " + players + " players, size " + size);
			}
		}
	}

	@Test
	void everyBracketEndsWithOneChampion() {
		Random r = new Random(11);
		for (int teams = 2; teams <= 16; teams++) {
			for (int run = 0; run < 20; run++) {
				Bracket b = new Bracket(teams);
				int matches = 0;
				while (!b.finished()) {
					List<Bracket.Pairing> ps = b.nextRound();
					assertTrue(b.round() == 1 || b.byes().isEmpty(), "byes only in round one");
					assertEquals(Integer.bitCount(b.roundSize()), 1, "round size is a power of two");
					Set<Integer> playing = new HashSet<>(b.byes());
					for (int i = 0; i < ps.size(); i++) {
						Bracket.Pairing p = ps.get(i);
						assertTrue(playing.add(p.a()) && playing.add(p.b()), "a team twice in one round");
						b.result(i, r.nextBoolean() ? p.a() : p.b());
						matches++;
					}
					assertEquals(b.teamsLeft().size(), playing.size());
					assertTrue(b.roundDone());
					b.advance();
				}
				assertEquals(teams - 1, matches, "every team but the champion loses once");
				assertEquals(Bracket.rounds(teams), b.round());
				assertTrue(b.champion() >= 0 && b.champion() < teams);
			}
		}
	}

	@Test
	void roundSizes() {
		Bracket b = new Bracket(5);
		b.nextRound();
		assertEquals(8, b.roundSize());
		assertEquals(3, b.byes().size());
		assertEquals(1, b.pairings().size());
		b.result(0, b.pairings().get(0).a());
		b.advance();
		b.nextRound();
		assertEquals(4, b.roundSize());
		assertEquals(2, b.pairings().size());
	}

	@Test
	void rejectsWrongWinners() {
		Bracket b = new Bracket(4);
		List<Bracket.Pairing> ps = b.nextRound();
		int outsider = ps.get(1).a();
		assertThrows(IllegalArgumentException.class, () -> b.result(0, outsider));
		assertThrows(IllegalStateException.class, b::advance);
		assertThrows(IllegalArgumentException.class, () -> new Bracket(1));
	}
}
