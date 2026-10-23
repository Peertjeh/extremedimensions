package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A spot holds the feet while their middle or a corner of them (0.25 out) is over ground: walking a one-block bridge
 * a little to the side, and the staircase {@link Bridging} builds at an angle (where its middle alone grazes the void
 * at every step), but never over a hole or past an edge.
 */
class FootingTest {
	/** Ground on the given cells (one block each). */
	private static Footing.Ground cells(Set<Long> c) {
		return (x, z) -> c.contains(key((int) Math.floor(x), (int) Math.floor(z)));
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	@Test
	void aOneBlockBridgeHoldsWhileACornerOfTheFeetIsOnIt() {
		Set<Long> c = new HashSet<>();
		for (int x = 0; x <= 20; x++) c.add(key(x, 0));
		Footing.Ground g = cells(c);
		assertTrue(Footing.holds(g, 5.5, 0.5));
		// the feet span 0.6: at z 1.2 they still stand 0.1 on the bridge, at 1.3 they would touch it only at the rim
		assertTrue(Footing.holds(g, 5.5, 1.2));
		assertFalse(Footing.holds(g, 5.5, 1.3));
		assertTrue(Footing.holds(g, 5.5, -0.2));
		assertFalse(Footing.holds(g, 5.5, -0.3));
	}

	@Test
	void theStaircaseABotBuildsAtAnAngleHoldsTheDiagonalWalk() {
		Set<Long> c = new HashSet<>();
		List<int[]> line = Bridging.line(0, 0, 12, 12, 48);
		for (int[] cell : line) c.add(key(cell[0], cell[1]));
		Footing.Ground g = cells(c);
		// The way from the middle of one end to the other, and up to 0.34 to the side of the notches: the feet are on the
		// staircase all the way, while their middle alone is over the void at once off the exact diagonal
		boolean middleMissed = false;
		for (double t = 1; t <= 11; t += 0.05) {
			for (double e = 0; e <= 0.34; e += 0.02) {
				double x = t - e / Math.sqrt(2), z = t + e / Math.sqrt(2);
				assertTrue(Footing.holds(g, x, z), "at " + x + ", " + z);
				middleMissed |= !g.at(x, z);
			}
		}
		assertTrue(middleMissed, "the middle of the feet alone holds the bot back here");
		// in the middle of a notch beside the staircase nothing holds the feet
		assertFalse(Footing.holds(g, 0.5, 1.6));
		assertFalse(Footing.holds(g, 2.6, 0.5));
	}

	@Test
	void aHoleInTheFloorAndAnEdgeStillStopTheFeet() {
		Set<Long> c = new HashSet<>();
		for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) if (x != 0 || z != 0) c.add(key(x, z));
		Footing.Ground g = cells(c);
		assertFalse(Footing.holds(g, 0.5, 0.5), "a one-block hole under the middle of the feet");
		assertTrue(Footing.holds(g, 0.5, 0.8), "the feet reach over its rim");
		// an edge at x 0 (ground at x < 0): a spot 0.25 past it is the last that holds
		Set<Long> e = new HashSet<>();
		for (int x = -10; x < 0; x++) for (int z = -10; z <= 10; z++) e.add(key(x, z));
		Footing.Ground edge = cells(e);
		assertTrue(Footing.holds(edge, 0.2, 0.5));
		assertFalse(Footing.holds(edge, 0.3, 0.5));
	}
}
