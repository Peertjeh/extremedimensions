package net.battlehub.arch.bldg;

/**
 * Checks a building's volumes before assembly: the dominant volume must stand highest (a hierarchy reads from far
 * away), secondary volumes above accents, and every volume must have a sensible size. Small breaches are corrected
 * (a dominant tower is raised), so the massing always has a clear hierarchy.
 */
public final class Massing {
	private Massing() {}

	public static void check(Building b) {
		int highest = Integer.MIN_VALUE;
		Volume dominant = null;
		for (Volume v : b.volumes) {
			if (v.fp.width() < 1 || v.fp.depth() < 1 || v.top < v.y0) throw new IllegalStateException("bad volume " + v);
			if (v.rank == 0 && (dominant == null || v.top > dominant.top)) dominant = v;
		}
		if (dominant == null) return;
		for (Volume v : b.volumes) {
			if (v != dominant && v.rank > 0) highest = Math.max(highest, v.top + roofRise(v));
		}
		int domTop = dominant.top + roofRise(dominant);
		if (highest != Integer.MIN_VALUE && domTop <= highest) {
			dominant.top += highest - domTop + 2;
		}
	}

	/** About how high a volume's roof rises above its walls. */
	static int roofRise(Volume v) {
		int half = Math.min(v.fp.width(), v.fp.depth()) / 2;
		return switch (v.roof) {
			case GABLE, HIP, PYRAMID -> half;
			case STEEP_GABLE, MANSARD -> half * 2;
			case CONE -> half * 2;
			case SPIRE -> half * 3;
			case DOME -> half;
			case BATTLEMENT -> 2;
			default -> 0;
		};
	}
}
