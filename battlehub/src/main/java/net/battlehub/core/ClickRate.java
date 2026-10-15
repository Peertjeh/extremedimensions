package net.battlehub.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Clicks per second over a sliding window (for the click-speed alert). */
public final class ClickRate {
	private final Deque<Long> clicks = new ArrayDeque<>();
	private final long windowTicks;

	public ClickRate(long windowTicks) {
		this.windowTicks = Math.max(1, windowTicks);
	}

	public void click(long tick) {
		clicks.addLast(tick);
		trim(tick);
	}

	/** Clicks per second in the window ending at {@code tick}. */
	public double perSecond(long tick) {
		trim(tick);
		return clicks.size() * 20.0 / windowTicks;
	}

	private void trim(long tick) {
		while (!clicks.isEmpty() && clicks.peekFirst() <= tick - windowTicks) {
			clicks.removeFirst();
		}
	}
}
