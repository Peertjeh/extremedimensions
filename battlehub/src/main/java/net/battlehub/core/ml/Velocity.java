package net.battlehub.core.ml;

/**
 * A player's velocity as the game keeps it, from how far they moved in a tick. The game moves a player by their
 * velocity, then multiplies it by the friction (the block's 0.6 times 0.91 on the ground, 0.91 in the air) and takes
 * the gravity off the vertical (times 0.98): that is what {@code getDeltaMovement()} is for a bot, and what the
 * simulation and the spec call a velocity. A person moves on their client, so on the server only their positions say
 * how they move; their step of a tick becomes that velocity here (a sprint of 0.28 a tick on the ground is a velocity of
 * 0.153, standing still -0.0784 downwards). ml/train/fights.py turns the people's steps of older fight logs into
 * velocities the same way.
 */
public final class Velocity {
	private Velocity() {}

	/** What most blocks give (ice 0.98, slime 0.8). */
	public static final double FRICTION = 0.6;
	public static final double AIR = 0.91;
	public static final double DRAG_Y = 0.98;
	public static final double GRAVITY = 0.08;

	/**
	 * The velocity after a step of (dx, dy, dz) in a tick.
	 *
	 * @param ground   whether the player is on the ground
	 * @param friction the friction of the block under them (on the ground)
	 * @param gravity  their gravity (the attribute: 0.08)
	 */
	public static double[] fromStep(double dx, double dy, double dz, boolean ground, double friction, double gravity) {
		double keep = ground ? friction * AIR : AIR;
		return new double[] {dx * keep, (dy - gravity) * DRAG_Y, dz * keep};
	}
}
