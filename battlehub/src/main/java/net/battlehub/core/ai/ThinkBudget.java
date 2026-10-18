package net.battlehub.core.ai;

/**
 * Keeps the bots' thinking within what the server can carry. Every server tick it hears how long ticks take
 * (smoothed) and how many bots there are, and from that sets:
 * <ul>
 * <li>a <b>stretch</b> for the time between thinks: 1 while ticks are quick, growing by one for every few
 * milliseconds above the threshold, at most {@link #MAX_STRETCH} (a bot then decides more slowly, it never acts
 * without a decision, and dangers still make it think at once);</li>
 * <li>a number of <b>thinks per tick</b> for all bots together, so a crowd of bots that happen to think in the same
 * tick is spread over the next ones instead.</li>
 * </ul>
 * It also counts thinks granted and put off, for the metrics.
 */
public final class ThinkBudget {
	public static final int MAX_STRETCH = 4;
	static final int MIN_PER_TICK = 3;
	/** Milliseconds above the threshold per extra step of stretch. */
	static final double STEP_MS = 5;
	/** How far below a step's threshold ticks must be before the step is taken back. */
	static final double MARGIN_MS = 2;

	private final double slowAboveMs;
	private double mspt;
	private int stretch = 1;
	private int tokens = MIN_PER_TICK;
	private long granted;
	private long deferred;

	/** {@code slowAboveMs}: from how long a tick (in ms; 50 is the most a tick may take) bots start to slow down. */
	public ThinkBudget(double slowAboveMs) {
		this.slowAboveMs = slowAboveMs;
	}

	/** A new server tick: how long the last one took, and how many bots there are. */
	public void tick(double tickMs, int bots) {
		mspt = mspt == 0 ? tickMs : mspt * 0.9 + tickMs * 0.1;
		int target = mspt <= slowAboveMs ? 1 : 1 + (int) Math.ceil((mspt - slowAboveMs) / STEP_MS);
		target = Math.max(1, Math.min(MAX_STRETCH, target));
		// up at once, down one step at a time once there is clearly room again (no flapping round the threshold)
		if (target > stretch) stretch = target;
		else if (target < stretch && mspt < slowAboveMs + (stretch - 2) * STEP_MS - MARGIN_MS) stretch--;
		tokens = Math.max(MIN_PER_TICK, (int) Math.ceil(bots / (2.0 * stretch)));
	}

	/** May a bot think now? (Each yes uses up one of this tick's thinks.) */
	public boolean allow() {
		if (tokens > 0) {
			tokens--;
			granted++;
			return true;
		}
		deferred++;
		return false;
	}

	public int stretch() {
		return stretch;
	}

	public double mspt() {
		return mspt;
	}

	public long granted() {
		return granted;
	}

	public long deferred() {
		return deferred;
	}
}
