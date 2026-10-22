package com.extremedimensions.server;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each part of the mod's server tick takes, while the performance tests listen: for each part, its worst single
 * tick and its time in all, by the clock and in CPU time. Off (as it always is in play) it costs a field read per part.
 * While it listens, a watcher also notes where the server thread stood whenever one part ran past {@link #STALL_NANOS}:
 * a part slow by the clock but not in CPU time was waiting on something, and the stack says on what.
 */
public final class TickProfile {
	private TickProfile() {}

	/** How long one part may run before the watcher notes where the server thread is. */
	public static final long STALL_NANOS = 40_000_000L;
	/** The most stalls noted while listening once. */
	private static final int STALLS_KEPT = 6;

	private static volatile boolean on = false;
	/** Per part: {worst nanoseconds in one tick, nanoseconds in all, ticks, worst CPU nanoseconds, CPU nanoseconds in all}. */
	private static final Map<String, long[]> PARTS = new LinkedHashMap<>();
	/** The thread's CPU time at each clock reading handed out this tick, so that a lap knows its part's CPU time. */
	private static final Long2LongOpenHashMap CPU_AT = new Long2LongOpenHashMap();
	/** The clock at the start of the part running now (0 between the mod's ticks), for the watcher. */
	private static volatile long mark = 0;
	/** Raised each time the tests start listening, so that an old watcher knows to stop. */
	private static volatile int generation = 0;
	private static final List<String> STALLS = new ArrayList<>();
	private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();

	static {
		CPU_AT.defaultReturnValue(-1);
	}

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

	/** Starts listening afresh, from the server thread: the parts are timed, and a watcher looks out for stalls. */
	public static void listen() {
		PARTS.clear();
		CPU_AT.clear();
		synchronized (STALLS) {
			STALLS.clear();
		}
		mark = 0;
		int gen = ++generation;
		on = true;
		Thread server = Thread.currentThread();
		Thread watcher = new Thread(() -> watch(server, gen), "Extreme Dimensions tick watcher");
		watcher.setDaemon(true);
		watcher.start();
	}

	/** Stops listening: per part, {worst ms in one tick, ms in all, ticks, worst CPU ms, CPU ms in all}, in the order the tick runs them. */
	public static Map<String, double[]> stop() {
		on = false;
		mark = 0;
		Map<String, double[]> out = new LinkedHashMap<>();
		PARTS.forEach((k, v) -> out.put(k, new double[] {v[0] / 1e6, v[1] / 1e6, v[2], v[3] / 1e6, v[4] / 1e6}));
		return out;
	}

	/** Where the server thread stood in each stall noted while listening (the most recent listen). */
	public static List<String> stalls() {
		synchronized (STALLS) {
			return new ArrayList<>(STALLS);
		}
	}

	/** The clock at the start of a part (0 while nobody listens). */
	public static long start() {
		if (!on) {
			return 0;
		}
		long now = System.nanoTime();
		CPU_AT.put(now, cpu());
		mark = now;
		return now;
	}

	/** A part done that began at {@code since}: its time noted; the clock for the next part. */
	public static long lap(String part, long since) {
		if (!on) {
			return 0;
		}
		long now = System.nanoTime(), c = cpu();
		long[] p = PARTS.computeIfAbsent(part, k -> new long[5]);
		p[0] = Math.max(p[0], now - since);
		p[1] += now - since;
		p[2]++;
		long c0 = CPU_AT.get(since);
		if (c0 >= 0 && c > 0) {
			p[3] = Math.max(p[3], c - c0);
			p[4] += c - c0;
		}
		CPU_AT.put(now, c);
		mark = now;
		return now;
	}

	/** The mod's tick is over: nothing of it runs until the next. */
	public static void rest() {
		if (on) {
			mark = 0;
			CPU_AT.clear();
		}
	}

	private static void watch(Thread server, int gen) {
		long seen = 0;
		while (on && generation == gen) {
			long m = mark;
			long late = System.nanoTime() - m;
			if (m != 0 && m != seen && late > STALL_NANOS) {
				seen = m;
				note(late, server.getStackTrace());
			}
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	/** The top of the stack, down to the first few frames of the mod's own (the code that waited, and why). */
	private static void note(long late, StackTraceElement[] stack) {
		StringBuilder at = new StringBuilder(String.format(java.util.Locale.ROOT, "%.0f ms into a part: ", late / 1e6));
		int ours = 0;
		for (int i = 0; i < stack.length && i < 48 && ours < 4; i++) {
			StackTraceElement f = stack[i];
			if (f.getClassName().startsWith("com.extremedimensions.")) {
				ours++;
			}
			at.append(i == 0 ? "" : " < ").append(f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1)).append('.')
					.append(f.getMethodName()).append(':').append(f.getLineNumber());
		}
		synchronized (STALLS) {
			if (STALLS.size() < STALLS_KEPT) {
				STALLS.add(at.toString());
			}
		}
	}
}
