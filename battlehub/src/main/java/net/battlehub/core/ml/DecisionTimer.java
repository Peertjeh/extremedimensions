package net.battlehub.core.ml;

import java.util.Arrays;
import java.util.Locale;

/**
 * What a learned decision costs the server tick: per decision the time to build the observation, run the model and
 * choose (and separately the action masks), over all bots of a model family. Kept for the last {@link #WINDOW}
 * decisions (average, 99th percentile, maximum) and in total; shown by /battlehub bots inspect and logged by the
 * self-test.
 */
public final class DecisionTimer {
	public static final int WINDOW = 2048;

	private final long[] recent = new long[WINDOW];
	private final long[] recentMasks = new long[WINDOW];
	private int next;
	private long count, totalNanos, maxNanos;

	/** One decision: all of it, and the part the masks took. */
	public synchronized void record(long nanos, long maskNanos) {
		recent[next] = nanos;
		recentMasks[next] = maskNanos;
		next = (next + 1) % WINDOW;
		count++;
		totalNanos += nanos;
		maxNanos = Math.max(maxNanos, nanos);
	}

	public synchronized long count() {
		return count;
	}

	/** Average milliseconds per decision, over all of them. */
	public synchronized double averageMs() {
		return count == 0 ? 0 : totalNanos / 1e6 / count;
	}

	/** The slowest decision so far, in milliseconds. */
	public synchronized double maxMs() {
		return maxNanos / 1e6;
	}

	/** The p-th percentile (0..100) of the recent decisions, in milliseconds. */
	public synchronized double percentileMs(double p) {
		return percentile(recent, p);
	}

	/** Average milliseconds the masks took per recent decision. */
	public synchronized double maskMs() {
		int n = (int) Math.min(count, WINDOW);
		if (n == 0) return 0;
		long sum = 0;
		for (int i = 0; i < n; i++) sum += recentMasks[i];
		return sum / 1e6 / n;
	}

	private double percentile(long[] values, double p) {
		int n = (int) Math.min(count, WINDOW);
		if (n == 0) return 0;
		long[] v = Arrays.copyOf(values, n);
		Arrays.sort(v);
		int k = (int) Math.min(n - 1, Math.max(0, Math.ceil(p / 100.0 * n) - 1));
		return v[k] / 1e6;
	}

	/** "0.21 ms per decision (p99 0.40, max 1.20; masks 0.004) over 1234 decisions". */
	public synchronized String summary() {
		return String.format(Locale.ROOT, "%.3f ms per decision (p99 %.3f, max %.3f; masks %.4f) over %d decisions", averageMs(),
				percentileMs(99), maxMs(), maskMs(), count);
	}

	public synchronized void reset() {
		Arrays.fill(recent, 0);
		Arrays.fill(recentMasks, 0);
		next = 0;
		count = totalNanos = maxNanos = 0;
	}
}
