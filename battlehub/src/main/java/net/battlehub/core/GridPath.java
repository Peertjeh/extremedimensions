package net.battlehub.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * A walking route over blocks (A*): where a player can stand, step up one block, drop down a few and go round
 * corners without cutting them. The world is asked through {@link Terrain}, so this can be tested on its own.
 */
public final class GridPath {
	private GridPath() {}

	public interface Terrain {
		/** A player fits here (feet and head) with something solid under the feet. */
		boolean stand(int x, int y, int z);

		/** Feet or head can pass through here. */
		boolean open(int x, int y, int z);
	}

	public record Node(int x, int y, int z) {}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final int[] STEPS = {0, 1, -1, -2, -3};

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
	}

	private record Open(Node node, double f, double g) {}

	/**
	 * The nodes from just after {@code start} to within {@code reach} blocks of {@code goal} (horizontally, and at
	 * most a block higher or lower), or null when there is no way within {@code maxNodes} steps of searching.
	 */
	public static List<Node> find(Terrain t, Node start, Node goal, double reach, int maxNodes) {
		return find(t, start, goal, reach, maxNodes, null);
	}

	/**
	 * Like {@link #find(Terrain, Node, Node, double, int)}, but the last node must also pass {@code there} (when given):
	 * a chest has to be in sight from where the route ends, not behind a wall or a window a step away. Then the end
	 * may also be two blocks higher or lower than the goal.
	 */
	public static List<Node> find(Terrain t, Node start, Node goal, double reach, int maxNodes, Predicate<Node> there) {
		if (!t.stand(start.x(), start.y(), start.z())) {
			return null;
		}
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Long, Double> best = new HashMap<>();
		Map<Long, Node> from = new HashMap<>();
		open.add(new Open(start, h(start, goal), 0));
		best.put(key(start.x(), start.y(), start.z()), 0.0);
		int expanded = 0;
		while (!open.isEmpty() && expanded < maxNodes) {
			Open cur = open.poll();
			Node n = cur.node();
			long nk = key(n.x(), n.y(), n.z());
			if (cur.g() > best.getOrDefault(nk, Double.MAX_VALUE)) {
				continue;
			}
			expanded++;
			double hx = n.x() - goal.x();
			double hz = n.z() - goal.z();
			if (hx * hx + hz * hz <= reach * reach && Math.abs(n.y() - goal.y()) <= (there == null ? 1 : 2) && (there == null || there.test(n))) {
				List<Node> path = new ArrayList<>();
				Node at = n;
				while (!at.equals(start)) {
					path.add(at);
					at = from.get(key(at.x(), at.y(), at.z()));
				}
				Collections.reverse(path);
				return path;
			}
			for (int[] d : DIRS) {
				boolean diagonal = d[0] != 0 && d[1] != 0;
				if (diagonal && !(t.open(n.x() + d[0], n.y(), n.z()) && t.open(n.x() + d[0], n.y() + 1, n.z())
						&& t.open(n.x(), n.y(), n.z() + d[1]) && t.open(n.x(), n.y() + 1, n.z() + d[1]))) {
					continue;
				}
				for (int dy : STEPS) {
					int x = n.x() + d[0];
					int y = n.y() + dy;
					int z = n.z() + d[1];
					if (dy == 1 && !t.open(n.x(), n.y() + 2, n.z())) continue;
					if (dy < 0 && !(t.open(x, n.y(), z) && t.open(x, n.y() + 1, z))) continue;
					if (!t.stand(x, y, z)) continue;
					double g = cur.g() + (diagonal ? 1.414 : 1.0) + (dy > 0 ? 0.6 : dy < 0 ? -0.2 * dy : 0);
					long k = key(x, y, z);
					if (g < best.getOrDefault(k, Double.MAX_VALUE)) {
						best.put(k, g);
						Node next = new Node(x, y, z);
						from.put(k, n);
						open.add(new Open(next, g + h(next, goal), g));
					}
					break;
				}
			}
		}
		return null;
	}

	private static double h(Node a, Node b) {
		double dx = Math.abs(a.x() - b.x());
		double dz = Math.abs(a.z() - b.z());
		return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + Math.abs(a.y() - b.y()) * 0.5;
	}
}
