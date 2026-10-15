package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SocialTest {
	private static List<UUID> ids(int n) {
		List<UUID> l = new ArrayList<>();
		for (int i = 0; i < n; i++) l.add(UUID.nameUUIDFromBytes(("p" + i).getBytes()));
		return l;
	}

	@Test
	void partiesStayTogether() {
		List<UUID> p = ids(8);
		Set<UUID> duo = Set.of(p.get(0), p.get(1));
		Set<UUID> trio = Set.of(p.get(2), p.get(3), p.get(4));
		for (int seed = 0; seed < 20; seed++) {
			List<List<UUID>> teams = TeamSplit.split(p, 2, List.of(duo, trio), new Random(seed));
			assertEquals(4, teams.get(0).size());
			assertEquals(4, teams.get(1).size());
			assertTrue(sameTeam(teams, duo));
			assertTrue(sameTeam(teams, trio));
		}
		// Four teams of two: a trio cannot fit, so it is split, but sizes stay equal
		List<List<UUID>> four = TeamSplit.split(p, 4, List.of(trio), new Random(1));
		for (List<UUID> t : four) assertEquals(2, t.size());
	}

	private static boolean sameTeam(List<List<UUID>> teams, Set<UUID> party) {
		for (List<UUID> t : teams) {
			if (t.containsAll(party)) return true;
		}
		return false;
	}

	@Test
	void interleaveForTwoTeams() {
		List<UUID> p = ids(5);
		List<UUID> order = TeamSplit.interleave(List.of(List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3), p.get(4))));
		assertEquals(List.of(p.get(2), p.get(0), p.get(3), p.get(1), p.get(4)), order);
	}

	@Test
	void codesAndClans() {
		String c = TeamSplit.code(new Random(3), 4);
		assertEquals(4, c.length());
		assertFalse(c.contains("I") || c.contains("O") || c.contains("L"));
		assertTrue(ClanRules.validName("Night_Owls"));
		assertFalse(ClanRules.validName("ab"));
		assertFalse(ClanRules.validName("bad name"));
		assertTrue(ClanRules.validTag("NO1"));
		assertFalse(ClanRules.validTag("TOOLONG"));
		assertEquals(3, ClanRules.points(true, true));
		assertEquals(0, ClanRules.points(false, true));
	}
}
