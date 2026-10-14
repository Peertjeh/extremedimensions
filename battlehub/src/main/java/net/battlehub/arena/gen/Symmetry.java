package net.battlehub.arena.gen;

/** How one half of a generated arena is copied to the other half, so every side is equally good. */
public enum Symmetry {
	/** Rotated half a turn around the centre: duels (spawns on opposite sides). */
	POINT,
	/** Mirrored across the Z axis: Capture The Flag (bases west and east). */
	MIRROR_X;

	public int x(int x, int z) {
		return -x;
	}

	public int z(int x, int z) {
		return this == POINT ? -z : z;
	}
}
