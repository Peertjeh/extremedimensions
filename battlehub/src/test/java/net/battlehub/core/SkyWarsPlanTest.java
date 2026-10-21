package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyWarsPlanTest {
	@Test
	void theMiddleIsTheBiggestIslandByFar() {
		for (int players = 2; players <= 12; players++) {
			SkyWarsPlan p = SkyWarsPlan.of(players);
			assertTrue(p.center.r() >= 15, players + " players: middle radius " + p.center.r());
			for (SkyWarsPlan.Island m : p.mids) assertTrue(m.r() * 3 < p.center.r(), "middle islands are much smaller");
			for (SkyWarsPlan.Island s : p.spawns) assertTrue(s.r() * 3 < p.center.r());
		}
	}

	@Test
	void oneSpawnIslandPerPlayerAndAtLeastFourMiddleIslands() {
		for (int players = 2; players <= 12; players++) {
			SkyWarsPlan p = SkyWarsPlan.of(players);
			assertEquals(Math.max(4, players), p.spawns.size());
			assertTrue(p.mids.size() >= 4, players + " players: " + p.mids.size() + " middle islands");
		}
	}

	@Test
	void everyBridgeHasALengthThatSuitsIt() {
		for (int players = 2; players <= 12; players++) {
			SkyWarsPlan p = SkyWarsPlan.of(players);
			for (SkyWarsPlan.Island m : p.mids) {
				// a short bridge from every middle island to the middle
				assertEquals(SkyWarsPlan.MID_GAP, m.gap(p.center), 0.01);
				for (SkyWarsPlan.Island o : p.mids) {
					if (o != m) assertTrue(m.gap(o) >= 3, players + " players: middle islands " + m.gap(o) + " apart");
				}
			}
			for (SkyWarsPlan.Island s : p.spawns) {
				double nearestMid = Double.MAX_VALUE;
				for (SkyWarsPlan.Island m : p.mids) nearestMid = Math.min(nearestMid, s.gap(m));
				assertTrue(nearestMid >= SkyWarsPlan.SPAWN_GAP - 0.01, players + " players: a middle island " + nearestMid + " from a spawn");
				assertTrue(nearestMid <= 18, players + " players: the nearest middle island " + nearestMid + " away");
				assertTrue(s.gap(p.center) >= SkyWarsPlan.MIN_GAP - 0.01, players + " players: the middle " + s.gap(p.center) + " from a spawn");
				// the middle island is the nearer way in, the middle itself further out of reach
				assertTrue(s.gap(p.center) > nearestMid);
				for (SkyWarsPlan.Island o : p.spawns) {
					if (o != s) assertTrue(s.gap(o) >= SkyWarsPlan.MIN_GAP - 0.01, players + " players: spawn islands " + s.gap(o) + " apart");
				}
			}
		}
	}

	@Test
	void everySpawnIslandIsAsFarFromTheMiddleIslandsAsTheOthers() {
		for (int players = 2; players <= 12; players++) {
			SkyWarsPlan p = SkyWarsPlan.of(players);
			double first = -1;
			for (SkyWarsPlan.Island s : p.spawns) {
				double nearest = Double.MAX_VALUE;
				for (SkyWarsPlan.Island m : p.mids) nearest = Math.min(nearest, s.gap(m));
				if (first < 0) first = nearest;
				assertEquals(first, nearest, 0.01, players + " players: a fair start for every island");
			}
		}
	}

	@Test
	void theMapIsBiggerThanBefore() {
		// the old map: a ring of max(20, 3.2 per player) with islands of 3.6 and a middle of 7.5
		for (int players = 2; players <= 12; players++) {
			SkyWarsPlan p = SkyWarsPlan.of(players);
			double old = Math.max(20, Math.max(4, players) * 3.2) + 3.6;
			assertTrue(p.outer > old + 8, players + " players: " + p.outer + " against " + old);
			assertTrue(p.center.r() >= 2 * 7.5, "the middle has four times the room");
		}
	}
}
