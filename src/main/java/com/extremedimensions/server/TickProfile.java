package com.extremedimensions.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each part of the mod's server tick takes, while the performance tests listen: for each part, its worst single
 * tick and its time in all. Off (as it always is in play) it costs a field read per part.
 */
public final class TickProfile {
	private TickProfile() {}

	private static boolean on = false;
	/** Per part: {worst nanoseconds in one tick, nanoseconds in all, ticks}. */
	private static final Map<String, long[]> PARTS = new LinkedHashMap<>();
	private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();

	/** Whether the tests are listening. */
	public static boolean on() {
		return on;
	}

	/**
	 * The CPU time this thread has had, in nanoseconds (0 if the JVM cannot tell): set against the clock, it shows
	 * whether a slow stretch was work, or the thread waiting its turn on a busy machine.
	 */
	public static long cpu() {
		return THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : 0;
	}

	/** Starts listening afresh. */
	public static void listen() {
		PARTS.clear();
		on = true;
	}

	/** Stops listening: per part, {worst ms in one tick, ms in all, ticks}, in the order the tick runs them. */
	public static Map<String, double[]> stop() {
		on = false;
		Map<String, double[]> out = new LinkedHashMap<>();
		PARTS.forEach((k, v) -> out.put(k, new double[] {v[0] / 1e6, v[1] / 1e6, v[2]}));
		return out;
	}

	/** The clock at the start of a part (0 while nobody listens). */
	public static long start() {
		return on ? System.nanoTime() : 0;
	}

	/** A part done that began at {@code since}: its time noted; the clock for the next part. */
	public static long lap(String part, long since) {
		if (!on) {
			return 0;
		}
		long now = System.nanoTime();
		long[] p = PARTS.computeIfAbsent(part, k -> new long[3]);
		p[0] = Math.max(p[0], now - since);
		p[1] += now - since;
		p[2]++;
		return now;
	}
}
