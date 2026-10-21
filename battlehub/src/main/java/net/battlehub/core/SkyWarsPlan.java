package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the islands of a SkyWars map go (positions in blocks from the middle, radii in blocks):
 * <ul>
 * <li>a super big island in the middle with the best loot;</li>
 * <li>a ring of smaller middle islands round it, a short bridge from the middle, never on the way from a spawn
 * island to the middle: one between every two spawn islands (or between every two neighbours when that would make
 * fewer than four, or the number is odd);</li>
 * <li>the spawn islands on the outside, each the same bridge away from its nearest middle island, the next spawn
 * island and the centre further.</li>
 * </ul>
 * Every map is drawn at a size: {@link #of(int, double)} makes it a given number of times as wide as the map of
 * 1.29, everything grown alike (islands, the middle, the bridges). Pure geometry, so the distances can be tested
 * without a world.
 */
public final class SkyWarsPlan {
	public record Island(double x, double z, double r) {
		/** Air between the edges of two islands. */
		public double gap(Island o) {
			return Math.hypot(x - o.x, z - o.z) - r - o.r;
		}

		/** The angle of this island seen from the middle (radians). */
		public double angle() {
			return Math.atan2(z, x);
		}

		/** How far the middle of this island is from the straight way between two others (centre to centre). */
		public double offWay(Island from, Island to) {
			double dx = to.x - from.x, dz = to.z - from.z;
			double len2 = dx * dx + dz * dz;
			double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - from.x) * dx + (z - from.z) * dz) / len2));
			return Math.hypot(x - (from.x + t * dx), z - (from.z + t * dz));
		}
	}

	/** At size 1 (everything grows with the size). */
	public static final double SPAWN_R = 4.5;
	public static final double MID_R = 4.0;
	/** Air between the middle island and the ring of middle islands. */
	public static final double MID_GAP = 6;
	/** Air a spawn island has to its nearest middle island. */
	public static final double SPAWN_GAP = 12;
	/** At least this much air between two spawn islands and between a spawn island and the middle. */
	public static final double MIN_GAP = 12;
	/** How much wider than the map of 1.29 a new map is: a random size in this range per match. */
	public static final double MIN_FACTOR = 1.5, MAX_FACTOR = 2.5;

	public final Island center;
	public final List<Island> mids;
	public final List<Island> spawns;
	/** The furthest block of the map from the middle. */
	public final double outer;
	/** The size the map is drawn at (1: the base distances). */
	public final double scale;

	private SkyWarsPlan(Island center, List<Island> mids, List<Island> spawns, double scale) {
		this.center = center;
		this.mids = List.copyOf(mids);
		this.spawns = List.copyOf(spawns);
		this.scale = scale;
		double far = center.r();
		for (Island i : spawns) far = Math.max(far, Math.hypot(i.x(), i.z()) + i.r());
		for (Island i : mids) far = Math.max(far, Math.hypot(i.x(), i.z()) + i.r());
		this.outer = far;
	}

	/** The map at size 1. */
	public static SkyWarsPlan of(int players) {
		return layout(Math.max(4, players), 1, false);
	}

	/** The map {@code factor} times as wide as the map of 1.29 for this many players. */
	public static SkyWarsPlan of(int players, double factor) {
		int n = Math.max(4, players);
		double want = factor * of129(players).outer;
		// the width grows with the size: find the size that gives the width wanted
		double lo = 0.5, hi = 12;
		for (int i = 0; i < 50; i++) {
			double mid = (lo + hi) / 2;
			if (layout(n, mid, false).outer < want) lo = mid;
			else hi = mid;
		}
		return layout(n, (lo + hi) / 2, false);
	}

	/** A random size between {@link #MIN_FACTOR} and {@link #MAX_FACTOR} from a number in 0..1. */
	public static double factor(double unit) {
		return MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * Math.max(0, Math.min(1, unit));
	}

	/** The map of 1.29 (with up to five players the middle islands lay straight in front of the spawn islands). */
	public static SkyWarsPlan of129(int players) {
		return layout(Math.max(4, players), 1, true);
	}

	private static SkyWarsPlan layout(int n, double s, boolean inFrontWhenFew) {
		double centerR = (14 + n / 3) * s, midR = MID_R * s, spawnR = SPAWN_R * s;
		double ring = centerR + midR + MID_GAP * s;
		double half = Math.PI / n;
		boolean inFront = inFrontWhenFew && n <= 5;
		int k = inFront ? n : n % 2 == 0 && n / 2 >= 4 ? n / 2 : n;
		double offset = inFront ? 0 : half;
		double toMid = spawnR + midR + SPAWN_GAP * s;
		double along = ring * Math.cos(offset), side = ring * Math.sin(offset);
		double forMid = toMid > side ? along + Math.sqrt(toMid * toMid - side * side) : along;
		double forCenter = centerR + spawnR + MIN_GAP * s;
		double forNeighbours = (MIN_GAP * s + 2 * spawnR) / (2 * Math.sin(half));
		double spawnRing = Math.max(forMid, Math.max(forCenter, forNeighbours));
		List<Island> spawns = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			spawns.add(new Island(Math.cos(a) * spawnRing, Math.sin(a) * spawnRing, spawnR));
		}
		List<Island> mids = new ArrayList<>();
		for (int j = 0; j < k; j++) {
			double a = j * 2 * Math.PI / k + offset;
			mids.add(new Island(Math.cos(a) * ring, Math.sin(a) * ring, midR));
		}
		return new SkyWarsPlan(new Island(0, 0, centerR), mids, spawns, s);
	}
}
