package net.battlehub.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.battlehub.BattleHub;

/** Runs code a number of ticks later on the server thread. Call {@link #tick()} once per server tick. */
public final class Tasks {
	private Tasks() {}

	private record Task(long due, Runnable run) {}

	private static final List<Task> TASKS = new ArrayList<>();
	private static long now;

	public static void later(int ticks, Runnable run) {
		TASKS.add(new Task(now + Math.max(1, ticks), run));
	}

	/** Next tick: used to leave packet handlers (e.g. open another menu after a click). */
	public static void next(Runnable run) {
		later(1, run);
	}

	public static void tick() {
		now++;
		List<Task> due = new ArrayList<>();
		Iterator<Task> it = TASKS.iterator();
		while (it.hasNext()) {
			Task t = it.next();
			if (t.due() <= now) {
				due.add(t);
				it.remove();
			}
		}
		for (Task t : due) {
			try {
				t.run().run();
			} catch (Exception e) {
				BattleHub.LOGGER.error("Scheduled task failed", e);
			}
		}
	}

	public static long now() {
		return now;
	}

	public static void clear() {
		TASKS.clear();
	}
}
