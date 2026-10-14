package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SideQueueTest {
	private static UUID u(int n) {
		return new UUID(0, n);
	}

	@Test
	void soloAndTeamPreferencesAreRespected() {
		SideQueue q = new SideQueue(2);
		q.add(u(1), SideQueue.Side.TEAM, 0);
		q.add(u(2), SideQueue.Side.ANY, 1);
		q.add(u(3), SideQueue.Side.SOLO, 2);
		q.add(u(4), SideQueue.Side.TEAM, 3);
		List<SideQueue.Group> g = q.match();
		assertEquals(1, g.size());
		assertEquals(u(3), g.get(0).solo(), "the one who asked to play alone");
		assertEquals(List.of(u(1), u(4)), g.get(0).team(), "team players before players who do not mind");
		assertEquals(1, q.size());
		assertTrue(q.contains(u(2)));
	}

	@Test
	void nobodyWantsToBeAloneThenSomeoneWhoDoesNotMind() {
		SideQueue q = new SideQueue(3);
		q.add(u(1), SideQueue.Side.TEAM, 0);
		q.add(u(2), SideQueue.Side.TEAM, 1);
		q.add(u(3), SideQueue.Side.TEAM, 2);
		assertTrue(q.match().isEmpty(), "three team players and nobody alone: wait");
		q.add(u(4), SideQueue.Side.ANY, 3);
		List<SideQueue.Group> g = q.match();
		assertEquals(1, g.size());
		assertEquals(u(4), g.get(0).solo());
		assertEquals(0, q.size());
	}

	@Test
	void twoSolosNeedTwoTeams() {
		SideQueue q = new SideQueue(2);
		q.add(u(1), SideQueue.Side.SOLO, 0);
		q.add(u(2), SideQueue.Side.SOLO, 1);
		q.add(u(3), SideQueue.Side.TEAM, 2);
		q.add(u(4), SideQueue.Side.TEAM, 3);
		List<SideQueue.Group> g = q.match();
		assertEquals(1, g.size());
		assertEquals(u(1), g.get(0).solo(), "first come, first served");
		assertTrue(q.contains(u(2)));
		assertFalse(q.contains(u(3)));
	}

	@Test
	void changingSideKeepsThePlace() {
		SideQueue q = new SideQueue(2);
		q.add(u(1), SideQueue.Side.SOLO, 0);
		q.add(u(2), SideQueue.Side.TEAM, 5);
		q.add(u(1), SideQueue.Side.TEAM, 9);
		assertEquals(SideQueue.Side.TEAM, q.side(u(1)));
		assertEquals(0, q.entries().get(0).since());
		assertEquals(2, q.count(SideQueue.Side.TEAM));
		assertTrue(q.remove(u(2)));
		assertFalse(q.remove(u(2)));
	}

	@Test
	void randomQueuesNeverUseAPlayerTwice() {
		Random r = new Random(5);
		for (int run = 0; run < 300; run++) {
			int teamSize = 2 + r.nextInt(2);
			SideQueue q = new SideQueue(teamSize);
			int n = r.nextInt(14);
			for (int i = 0; i < n; i++) {
				q.add(u(i), SideQueue.Side.values()[r.nextInt(3)], i);
			}
			Set<UUID> used = new HashSet<>();
			for (SideQueue.Group g : q.match()) {
				assertEquals(teamSize, g.team().size());
				assertTrue(used.add(g.solo()));
				for (UUID t : g.team()) assertTrue(used.add(t));
			}
			assertEquals(n, used.size() + q.size());
			// Whatever is left cannot form a group
			assertTrue(q.match().isEmpty());
		}
	}

	@Test
	void sideIds() {
		assertEquals(SideQueue.Side.SOLO, SideQueue.Side.byId("solo"));
		assertEquals(SideQueue.Side.ANY, SideQueue.Side.byId("nonsense"));
	}
}
