package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GridPathTest {
	/** A flat floor at y=0 (stand at y=1) from -20 to 20, with walls (two high) and raised blocks where asked. */
	private static final class Flat implements GridPath.Terrain {
		final Set<Long> walls = new HashSet<>();
		final Set<Long> steps = new HashSet<>();

		static long k(int x, int z) {
			return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		}

		int ground(int x, int z) {
			return steps.contains(k(x, z)) ? 2 : 1;
		}

		@Override
		public boolean open(int x, int y, int z) {
			if (Math.abs(x) > 20 || Math.abs(z) > 20) return true;
			if (walls.contains(k(x, z)) && y >= 1 && y <= 4) return false;
			return y >= ground(x, z);
		}

		@Override
		public boolean stand(int x, int y, int z) {
			if (Math.abs(x) > 20 || Math.abs(z) > 20) return false;
			return y == ground(x, z) && open(x, y, z) && open(x, y + 1, z);
		}
	}

	@Test
	void straightOnTheFlat() {
		Flat f = new Flat();
		List<GridPath.Node> p = GridPath.find(f, new GridPath.Node(-8, 1, 0), new GridPath.Node(8, 1, 0), 1.5, 3000);
		assertNotNull(p);
		assertTrue(p.size() >= 14 && p.size() <= 16, "about 15 steps: " + p.size());
	}

	@Test
	void roundALongWall() {
		Flat f = new Flat();
		for (int z = -6; z <= 6; z++) f.walls.add(Flat.k(0, z));
		List<GridPath.Node> p = GridPath.find(f, new GridPath.Node(-8, 1, 0), new GridPath.Node(8, 1, 0), 1.5, 5000);
		assertNotNull(p, "goes round the wall");
		for (GridPath.Node n : p) {
			assertTrue(!f.walls.contains(Flat.k(n.x(), n.z())), "never through the wall: " + n);
		}
		assertTrue(p.stream().anyMatch(n -> Math.abs(n.z()) >= 7), "past the end of the wall");
	}

	@Test
	void upAStepAndNotThroughCorners() {
		Flat f = new Flat();
		for (int x = 2; x <= 5; x++) for (int z = -1; z <= 1; z++) f.steps.add(Flat.k(x, z));
		List<GridPath.Node> p = GridPath.find(f, new GridPath.Node(0, 1, 0), new GridPath.Node(4, 2, 0), 0.5, 3000);
		assertNotNull(p);
		assertEquals(2, p.get(p.size() - 1).y(), "ends on the step");
		// A gap only diagonal: two walls touching at a corner
		Flat g = new Flat();
		g.walls.add(Flat.k(1, 0));
		g.walls.add(Flat.k(0, 1));
		List<GridPath.Node> q = GridPath.find(g, new GridPath.Node(0, 1, 0), new GridPath.Node(1, 1, 1), 0.1, 3000);
		assertNotNull(q);
		assertTrue(q.size() > 1, "no squeezing between the corners");
	}

	@Test
	void walledInHasNoWay() {
		Flat f = new Flat();
		for (int x = -2; x <= 2; x++) {
			f.walls.add(Flat.k(x, -2));
			f.walls.add(Flat.k(x, 2));
			f.walls.add(Flat.k(-2, x));
			f.walls.add(Flat.k(2, x));
		}
		assertNull(GridPath.find(f, new GridPath.Node(0, 1, 0), new GridPath.Node(10, 1, 0), 1.5, 4000));
	}

	/**
	 * A chest inside a house, against the wall with the window; the doorway on the far side. A route that only has to
	 * come near the chest ends outside, two steps from it with the wall in between (the bots that circled at a window);
	 * one that has to see it goes round through the doorway and ends inside.
	 */
	@Test
	void aRouteToAChestEndsWhereTheChestIsInSightNotOutsideTheWindow() {
		Flat f = new Flat();
		for (int i = 0; i <= 6; i++) {
			f.walls.add(Flat.k(0, i));
			f.walls.add(Flat.k(i, 0));
			f.walls.add(Flat.k(i, 6));
			if (i != 3) f.walls.add(Flat.k(6, i));
		}
		f.walls.add(Flat.k(1, 3));
		GridPath.Node start = new GridPath.Node(-5, 1, 3), chest = new GridPath.Node(1, 1, 3);
		List<GridPath.Node> near = GridPath.find(f, start, chest, 2.5, 5000);
		assertNotNull(near);
		assertTrue(near.get(near.size() - 1).x() < 0, "near enough, but outside: " + near.get(near.size() - 1));
		List<GridPath.Node> seen = GridPath.find(f, start, chest, 3.8, 5000, n -> sees(f, n, chest));
		assertNotNull(seen);
		GridPath.Node end = seen.get(seen.size() - 1);
		assertTrue(end.x() >= 2 && end.x() <= 5 && end.z() >= 1 && end.z() <= 5, "inside the house: " + end);
		assertTrue(seen.stream().anyMatch(n -> n.x() == 6 && n.z() == 3), "through the doorway");
	}

	/** Flat line of sight from the middle of {@code n} to the middle of {@code c}, within 3.3 blocks, no wall between. */
	private static boolean sees(Flat f, GridPath.Node n, GridPath.Node c) {
		double ax = n.x() + 0.5, az = n.z() + 0.5, bx = c.x() + 0.5, bz = c.z() + 0.5;
		if (Math.hypot(bx - ax, bz - az) > 3.3) return false;
		for (int i = 1; i < 100; i++) {
			double t = i / 100.0;
			int x = (int) Math.floor(ax + (bx - ax) * t), z = (int) Math.floor(az + (bz - az) * t);
			if (x == c.x() && z == c.z()) return true;
			if (f.walls.contains(Flat.k(x, z))) return false;
		}
		return true;
	}
}
