package net.battlehub.core.ai;

/**
 * Does a jump, or a flight after a knock, end on ground? The player's own movement ({@link PlayerSim}) is carried on
 * tick by tick until the feet come down on a surface (under the middle or any corner of the 0.6 wide body), or drop
 * too far below where it started. Used before every jump a bot makes outside parkour, and in the air to know whether
 * it has to steer back.
 */
public final class Falls {
	private Falls() {}

	/** The world under the player. */
	public interface Ground {
		/** The height of the highest surface under (x, z) at or below {@code y}, within a few blocks; NaN for a drop. */
		double surface(double x, double y, double z);
	}

	private static final double[][] FEET = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};

	/**
	 * A player at (x, y, z) moving (vx, vy, vz), pressing towards (dx, dz) (a unit vector or zero), sprinting or not,
	 * jumping off the ground now or already in the air: true when it comes down on a surface no more than
	 * {@code maxDrop} below where it started.
	 */
	public static boolean landsSafely(double x, double y, double z, double vx, double vy, double vz, double dx, double dz, boolean sprint,
			boolean jump, Ground g, double maxDrop) {
		PlayerSim s = new PlayerSim(x, y, z);
		s.vx = vx;
		s.vy = jump ? 0 : vy;
		s.vz = vz;
		s.onGround = jump;
		for (int t = 0; t < 60; t++) {
			double prevY = s.y;
			s.tick(dx, dz, sprint, jump && t == 0);
			if (s.vy > 0 && s.y > prevY) continue;
			double top = Double.NaN;
			for (double[] f : FEET) {
				double h = g.surface(s.x + f[0], prevY + 1e-3, s.z + f[1]);
				if (!Double.isNaN(h) && (Double.isNaN(top) || h > top)) top = h;
			}
			if (!Double.isNaN(top) && s.y <= top) return top >= y - maxDrop;
			if (s.y < y - maxDrop - 1) return false;
		}
		return false;
	}
}
