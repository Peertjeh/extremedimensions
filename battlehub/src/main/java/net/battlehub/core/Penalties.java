package net.battlehub.core;

import java.util.List;

/**
 * Leaving matches early: every time within a day the wait before you may queue again grows (30 seconds, 2
 * minutes, 5 minutes, 15 minutes, ...). Pure numbers; the server keeps the times.
 */
public final class Penalties {
	private Penalties() {}

	public static final long DAY_MS = 24L * 60 * 60 * 1000;

	/** Seconds of waiting for the n-th leave within a day (n starts at 1). */
	public static int cooldownSeconds(int n, List<Integer> steps) {
		if (n <= 0 || steps == null || steps.isEmpty()) {
			return 0;
		}
		Integer s = steps.get(Math.min(n, steps.size()) - 1);
		return s == null ? 0 : Math.max(0, s);
	}

	/** How many leaves still count: they are forgotten a day after the last one. */
	public static int stillCounting(int leaves, long lastLeaveMs, long nowMs) {
		return nowMs - lastLeaveMs > DAY_MS ? 0 : Math.max(0, leaves);
	}
}
