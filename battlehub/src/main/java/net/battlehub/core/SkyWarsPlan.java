package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the islands of a SkyWars map go (positions in blocks from the middle, radii in blocks):
 * <ul>
 * <li>a super big island in the middle with the best loot;</li>
 * <li>a ring of smaller middle islands round it, a short bridge from the middle: in small games (up to five) one
 * straight in front of every spawn island, in bigger ones one between every two spawn islands (or between every two
 * neighbours when that would make fewer than four, or the number is odd);</li>
 * <li>the spawn islands on the outside, each with a middle island at a bridge of about twelve blocks and the next
 * spawn island and the centre further.</li>
 * </ul>
 * Pure geometry, so the distances can be tested without a world.
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
	}

	public static final double SPAWN_R = 4.5;
	public static final double MID_R = 4.0;
	/** Air between the middle island and the ring of middle islands. */
	public static final double MID_GAP = 6;
	/** Air a spawn island has to its nearest middle island. */
	public static final double SPAWN_GAP = 12;
	/** At least this much air between two spawn islands and between a spawn island and the middle. */
	public static final double MIN_GAP = 12;

	public final Island center;
	public final List<Island> mids;
	public final List<Island> spawns;
	/** The furthest block of the map from the middle. */
	public final double outer;

	private SkyWarsPlan(Island center, List<Island> mids, List<Island> spawns) {
		this.center = center;
		this.mids = List.copyOf(mids);
		this.spawns = List.copyOf(spawns);
		double far = center.r();
		for (Island i : spawns) far = Math.max(far, Math.hypot(i.x(), i.z()) + i.r());
		for (Island i : mids) far = Math.max(far, Math.hypot(i.x(), i.z()) + i.r());
		this.outer = far;
	}

	/** The map for this many players (at least four spawn islands). */
	public static SkyWarsPlan of(int players) {
		int n = Math.max(4, players);
		double centerR = 14 + n / 3;
		double ring = centerR + MID_R + MID_GAP;
		double half = Math.PI / n;
		// few islands are far apart round the ring: a middle island to the side would be further than the middle itself
		boolean inFront = n <= 5;
		int k = inFront ? n : n % 2 == 0 && n / 2 >= 4 ? n / 2 : n;
		double offset = inFront ? 0 : half;
		double toMid = SPAWN_R + MID_R + SPAWN_GAP;
		double along = ring * Math.cos(offset), side = ring * Math.sin(offset);
		double forMid = toMid > side ? along + Math.sqrt(toMid * toMid - side * side) : along;
		double forCenter = centerR + SPAWN_R + MIN_GAP;
		double forNeighbours = (MIN_GAP + 2 * SPAWN_R) / (2 * Math.sin(half));
		double spawnRing = Math.max(forMid, Math.max(forCenter, forNeighbours));
		List<Island> spawns = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			spawns.add(new Island(Math.cos(a) * spawnRing, Math.sin(a) * spawnRing, SPAWN_R));
		}
		List<Island> mids = new ArrayList<>();
		for (int j = 0; j < k; j++) {
			double a = j * 2 * Math.PI / k + offset;
			mids.add(new Island(Math.cos(a) * ring, Math.sin(a) * ring, MID_R));
		}
		return new SkyWarsPlan(new Island(0, 0, centerR), mids, spawns);
	}
}
