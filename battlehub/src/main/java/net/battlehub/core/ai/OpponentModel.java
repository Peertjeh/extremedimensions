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
	private boolean wasRising;

	/**
	 * One look at the opponent, once per think: the distance, how fast they themselves move towards the NPC (their
	 * own speed along the line to it, in blocks per tick; the NPC walking up to them does not count), their sideways
	 * direction changes in the last second and whether they rise (a jump).
	 */
	public void observe(double dist, double approachSpeed, int strafeChangesPerSecond, boolean rising) {
		// pushing forward only shows while there is room to come closer; standing in sword range says nothing
		if (dist > 3.5) closing += W * (approachSpeed - closing);
		if (dist < 8) distance += W * (dist - distance);
		strafeRate += W * (strafeChangesPerSecond - strafeRate);
		jumpRate += W * (((rising && !wasRising) ? 1 : 0) * 20 - jumpRate) * 0.2;
		wasRising = rising;
		samples++;
	}

	/**
	 * How strongly they push forward, 0..1: half from walking at the NPC (0.1 blocks a tick, a normal walk, counts
	 * fully), half from how close they like to fight.
	 */
	public double aggression() {
		double pushing = Math.max(0, Math.min(1, closing / 0.1));
		double close = samples > 20 ? Math.max(0, Math.min(1, (4.5 - distance) / 3)) : 0.3;
		return 0.5 * pushing + 0.5 * close;
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

	/** The rolling approach speed (for saving). */
	public double closing() {
		return closing;
	}

	public int samples() {
		return samples;
	}

	/** Starts from what was learned before (another session), counted as a few looks so new ones still matter. */
	public void seed(double closing, double distance, double strafeRate, double jumpRate, int samples) {
		this.closing = closing;
		this.distance = distance;
		this.strafeRate = strafeRate;
		this.jumpRate = jumpRate;
		this.samples = Math.min(samples, 40);
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
		wasRising = false;
	}
}
