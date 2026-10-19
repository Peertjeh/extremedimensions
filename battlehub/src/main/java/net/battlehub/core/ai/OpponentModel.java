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
	/** Ticks between their swings (rolling): spam is short, timed hits are long. */
	private double swingGap = 12;
	private int swings;
	private int lastSwing = Integer.MIN_VALUE;
	/** Their swings and their hits on this NPC, both fading, for how often they land. */
	private double swingWeight;
	private double hitWeight;

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

	/** They were seen swinging (their attack charge dropped). */
	public void swing(int tick) {
		if (lastSwing != Integer.MIN_VALUE && tick - lastSwing > 0 && tick - lastSwing < 40) {
			swingGap += 0.15 * ((tick - lastSwing) - swingGap);
		}
		lastSwing = tick;
		swings++;
		swingWeight = swingWeight * 0.95 + 1;
		hitWeight *= 0.95;
	}

	/** One of their hits landed on this NPC. */
	public void hit() {
		hitWeight = Math.min(swingWeight, hitWeight + 1);
	}

	/** Ticks between their swings, on average. */
	public double swingGap() {
		return swingGap;
	}

	public int swings() {
		return swings;
	}

	/** How often their swings land on this NPC, 0..1 (0.6 until a few swings were seen). */
	public double accuracy() {
		return swingWeight < 3 ? 0.6 : Math.min(1, hitWeight / swingWeight);
	}

	/** They click as fast as they can (weak hits): out-trading them with full ones pays. */
	public boolean spams() {
		return swings >= 5 && swingGap < 8.5;
	}

	/** They wait for full hits: spacing and stepping in after their swing pays. */
	public boolean timed() {
		return swings >= 5 && swingGap >= 11;
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

	/** Also how they swing and how often they land (from earlier fights), counted as a handful of swings. */
	public void seedSwings(double swingGap, double accuracy, int swings) {
		if (swings <= 0) return;
		this.swingGap = swingGap;
		this.swings = Math.min(swings, 12);
		this.swingWeight = Math.min(swings, 8);
		this.hitWeight = this.swingWeight * Math.max(0, Math.min(1, accuracy));
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
		swingGap = 12;
		swings = 0;
		lastSwing = Integer.MIN_VALUE;
		swingWeight = 0;
		hitWeight = 0;
	}
}
