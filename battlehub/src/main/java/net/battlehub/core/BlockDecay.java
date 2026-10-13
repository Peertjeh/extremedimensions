package net.battlehub.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** TNT Run: a block someone stepped on disappears a fixed number of ticks later. Positions are packed longs. */
public final class BlockDecay {
	private final Map<Long, Long> due = new HashMap<>();
	private final int delay;

	public BlockDecay(int delayTicks) {
		this.delay = Math.max(0, delayTicks);
	}

	/** Schedules removal unless it is already scheduled. Returns true if newly scheduled. */
	public boolean step(long pos, long now) {
		return due.putIfAbsent(pos, now + delay) == null;
	}

	public boolean scheduled(long pos) {
		return due.containsKey(pos);
	}

	/** Removes and returns every position whose time has come. */
	public List<Long> collect(long now) {
		List<Long> out = new ArrayList<>();
		Iterator<Map.Entry<Long, Long>> it = due.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Long> e = it.next();
			if (e.getValue() <= now) {
				out.add(e.getKey());
				it.remove();
			}
		}
		return out;
	}

	public int pending() {
		return due.size();
	}

	public void clear() {
		due.clear();
	}
}
