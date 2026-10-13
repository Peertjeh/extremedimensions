package net.battlehub.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parkour Race progress. Checkpoints are numbered 1..finish; reaching the finish index records the time.
 * Ranking: finishers by time, then everybody else by furthest checkpoint.
 */
public final class ParkourRace {
	private final int finishIndex;
	private final long startTick;
	private final Map<UUID, Integer> reached = new HashMap<>();
	private final Map<UUID, Long> finished = new HashMap<>();
	private final Map<UUID, Long> reachedAt = new HashMap<>();

	public ParkourRace(int finishIndex, long startTick) {
		this.finishIndex = Math.max(1, finishIndex);
		this.startTick = startTick;
	}

	public enum Reach { NOTHING, CHECKPOINT, FINISH }

	public Reach reach(UUID player, int index, long now) {
		if (finished.containsKey(player) || index <= reached.getOrDefault(player, 0)) {
			return Reach.NOTHING;
		}
		reached.put(player, index);
		reachedAt.put(player, now);
		if (index >= finishIndex) {
			finished.put(player, now - startTick);
			return Reach.FINISH;
		}
		return Reach.CHECKPOINT;
	}

	public int checkpoint(UUID player) {
		return reached.getOrDefault(player, 0);
	}

	public boolean hasFinished(UUID player) {
		return finished.containsKey(player);
	}

	/** Finish time in ticks, or -1. */
	public long time(UUID player) {
		return finished.getOrDefault(player, -1L);
	}

	public int finishers() {
		return finished.size();
	}

	public boolean allFinished(Collection<UUID> players) {
		for (UUID p : players) {
			if (!finished.containsKey(p)) {
				return false;
			}
		}
		return !players.isEmpty();
	}

	public List<UUID> ranking(Collection<UUID> players) {
		List<UUID> list = new ArrayList<>(players);
		list.sort((a, b) -> {
			boolean fa = finished.containsKey(a);
			boolean fb = finished.containsKey(b);
			if (fa && fb) {
				return Long.compare(finished.get(a), finished.get(b));
			}
			if (fa != fb) {
				return fa ? -1 : 1;
			}
			int c = Integer.compare(checkpoint(b), checkpoint(a));
			if (c != 0) {
				return c;
			}
			return Long.compare(reachedAt.getOrDefault(a, Long.MAX_VALUE), reachedAt.getOrDefault(b, Long.MAX_VALUE));
		});
		return list;
	}

	public int finishIndex() {
		return finishIndex;
	}
}
