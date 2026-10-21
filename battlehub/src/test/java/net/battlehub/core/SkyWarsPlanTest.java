package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SkyWarsPlanTest {
	/** Every map a match can get: 2 to 12 players, the smallest, a middle and the biggest size. */
	private static List<SkyWarsPlan> maps() {
		List<SkyWarsPlan> out = new ArrayList<>();
		for (int players = 2; players <= 12; players++) {
			for (double f : new double[] {SkyWarsPlan.MIN_FACTOR, 2.0, SkyWarsPlan.MAX_FACTOR}) out.add(SkyWarsPlan.of(players, f));
		}
		return out;
	}

	@Test
	void theMapIsOneAndAHalfToTwoAndAHalfTimesAsWideAsIn129() {
		// the map of 1.29, as it was measured then
		assertEquals(50.0, SkyWarsPlan.of129(4).outer, 0.1);
		assertEquals(42.9, SkyWarsPlan.of129(6).outer, 0.1);
		assertEquals(50.7, SkyWarsPlan.of129(12).outer, 0.1);
		for (int players = 2; players <= 12; players++) {
			double old = SkyWarsPlan.of129(players).outer;
			for (double f : new double[] {1.5, 1.8, 2.2, 2.5}) {
				assertEquals(f * old, SkyWarsPlan.of(players, f).outer, 0.05, players + " players at " + f);
			}
		}
		assertEquals(1.5, SkyWarsPlan.factor(0), 1e-9);
		assertEquals(2.5, SkyWarsPlan.factor(1), 1e-9);
		assertEquals(2.0, SkyWarsPlan.factor(0.5), 1e-9);
	}

	@Test
	void theMiddleIsTheBiggestIslandByFar() {
		for (SkyWarsPlan p : maps()) {
			assertTrue(p.center.r() >= 15, "middle radius " + p.center.r());
			for (SkyWarsPlan.Island m : p.mids) assertTrue(m.r() * 3 < p.center.r(), "middle islands are much smaller");
			for (SkyWarsPlan.Island s : p.spawns) assertTrue(s.r() * 3 < p.center.r());
		}
	}

	@Test
	void oneSpawnIslandPerPlayerAndAtLeastFourMiddleIslands() {
		for (int players = 2; players <= 12; players++) {
			SkyWarsPlan p = SkyWarsPlan.of(players, 2);
			assertEquals(Math.max(4, players), p.spawns.size());
			assertTrue(p.mids.size() >= 4, players + " players: " + p.mids.size() + " middle islands");
		}
	}

	@Test
	void noMiddleIslandLiesOnTheWayFromASpawnIslandToTheMiddle() {
		for (SkyWarsPlan p : maps()) {
			for (SkyWarsPlan.Island s : p.spawns) {
				for (SkyWarsPlan.Island m : p.mids) {
					// a straight bridge from the spawn island to the middle passes the middle island with room to spare
					assertTrue(m.offWay(s, p.center) >= m.r() + 2, p.spawns.size() + " players at " + p.scale + ": "
							+ m.offWay(s, p.center) + " off the way, radius " + m.r());
				}
			}
		}
	}

	@Test
	void everyBridgeHasALengthThatSuitsIt() {
		for (SkyWarsPlan p : maps()) {
			double s = p.scale;
			String where = p.spawns.size() + " players at size " + s;
			for (SkyWarsPlan.Island m : p.mids) {
				// a short bridge from every middle island to the middle
				assertEquals(SkyWarsPlan.MID_GAP * s, m.gap(p.center), 0.01, where);
				for (SkyWarsPlan.Island o : p.mids) {
					if (o != m) assertTrue(m.gap(o) >= 3 * s, where + ": middle islands " + m.gap(o) + " apart");
				}
			}
			for (SkyWarsPlan.Island sp : p.spawns) {
				double nearestMid = Double.MAX_VALUE;
				for (SkyWarsPlan.Island m : p.mids) nearestMid = Math.min(nearestMid, sp.gap(m));
				assertTrue(nearestMid >= SkyWarsPlan.SPAWN_GAP * s - 0.01, where + ": a middle island " + nearestMid + " from a spawn");
				assertTrue(nearestMid <= 18 * s, where + ": the nearest middle island " + nearestMid + " away");
				assertTrue(sp.gap(p.center) >= SkyWarsPlan.MIN_GAP * s - 0.01, where + ": the middle " + sp.gap(p.center) + " from a spawn");
				for (SkyWarsPlan.Island o : p.spawns) {
					if (o != sp) assertTrue(sp.gap(o) >= SkyWarsPlan.MIN_GAP * s - 0.01, where + ": spawn islands " + sp.gap(o) + " apart");
				}
			}
		}
	}

	@Test
	void everySpawnIslandIsAsFarFromTheMiddleIslandsAsTheOthers() {
		for (SkyWarsPlan p : maps()) {
			double first = -1;
			for (SkyWarsPlan.Island s : p.spawns) {
				double nearest = Double.MAX_VALUE;
				for (SkyWarsPlan.Island m : p.mids) nearest = Math.min(nearest, s.gap(m));
				if (first < 0) first = nearest;
				assertEquals(first, nearest, 0.01, p.spawns.size() + " players: a fair start for every island");
			}
		}
	}
}
