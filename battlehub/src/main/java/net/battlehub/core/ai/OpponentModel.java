package net.battlehub.core.ai;

/**
 * What an NPC learns about one opponent during a session, from what it can see: how fast they tend to close in,
 * the distance they like, how often they change strafe direction and jump. Rolling averages (bounded, with a
 * fixed weight), so old behaviour fades and nothing grows without limit. Reset with {@link #reset()}.
 */
public final class OpponentModel {
	/** Weight of a new observation in the rolling averages. */
	private static final double W = 0.05;

	private double closing;
	private double distance = 4;
	private double strafeRate;
	private double jumpRate;
	private int samples;
	private double lastDistance = Double.NaN;
	private boolean wasRising;

	/** One look at the opponent: the distance now, their sideways changes in the last second and whether they rise. */
	public void observe(double dist, int strafeChangesPerSecond, boolean rising) {
		if (!Double.isNaN(lastDistance)) {
			closing += W * ((lastDistance - dist) - closing);
		}
		lastDistance = dist;
		if (dist < 8) distance += W * (dist - distance);
		strafeRate += W * (strafeChangesPerSecond - strafeRate);
		jumpRate += W * (((rising && !wasRising) ? 1 : 0) * 20 - jumpRate) * 0.2;
		wasRising = rising;
		samples++;
	}

	/** Lost sight: the next distance should not count as closing in. */
	public void lostSight() {
		lastDistance = Double.NaN;
	}

	/** How strongly they push forward, 0..1 (closing about 0.15 blocks per think counts as fully aggressive). */
	public double aggression() {
		return Math.max(0, Math.min(1, closing / 0.15 * 0.5 + 0.5 * (samples > 20 ? Math.max(0, (4.5 - distance) / 3) : 0.3)));
	}

	/** The distance they like to fight at (blocks). */
	public double preferredDistance() {
		return distance;
	}

	/** Strafe direction changes per second. */
	public double strafeRate() {
		return strafeRate;
	}

	/** How readable their movement is, 0..1 (few direction changes: readable). */
	public double predictability() {
		return Math.max(0, Math.min(1, 1 - strafeRate / 3));
	}

	public double jumpRate() {
		return jumpRate;
	}

	/** Enough looks to trust the averages. */
	public boolean known() {
		return samples >= 20;
	}

	public void reset() {
		closing = 0;
		distance = 4;
		strafeRate = 0;
		jumpRate = 0;
		samples = 0;
		lastDistance = Double.NaN;
		wasRising = false;
	}
}
