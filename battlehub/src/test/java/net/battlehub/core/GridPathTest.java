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
}
