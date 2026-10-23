package net.battlehub.core.ai;

/**
 * Whether a spot holds a player up. The feet are 0.6 wide, so a spot whose middle is over the void still holds them
 * while a corner of the feet is over ground: a narrow bridge walked at an angle, the corner of a staircase that
 * {@link Bridging} built. The corners are looked at 0.25 out, 0.05 short of where the feet would slip.
 */
public final class Footing {
	private Footing() {}

	/** Ground under (x, z) (at the height in question). */
	public interface Ground {
		boolean at(double x, double z);
	}

	static final double CORNER = 0.25;

	public static boolean holds(Ground g, double x, double z) {
		if (g.at(x, z)) return true;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				if (g.at(x + sx * CORNER, z + sz * CORNER)) return true;
			}
		}
		return false;
	}
}
