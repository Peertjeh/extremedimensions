package net.battlehub.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One queue for one mode. Casual queues pair players in arrival order; ranked queues only pair players whose
 * ratings are close, with a search window that widens the longer someone waits.
 */
public final class Matchmaker {
	public record Entry(UUID id, int rating, long since) {}

	public record Pair(UUID first, UUID second) {}

	/** Ranked search window: {@code base + perSecond * secondsWaited}, capped at {@code max}. */
	public record Window(int base, int perSecond, int max) {
		public int at(long waitedTicks) {
			long w = base + (long) perSecond * Math.max(0, waitedTicks / 20);
			return (int) Math.min(max, w);
		}
	}

	private final List<Entry> entries = new ArrayList<>();

	public boolean add(UUID id, int rating, long now) {
		if (contains(id)) {
			return false;
		}
		entries.add(new Entry(id, rating, now));
		return true;
	}

	public boolean remove(UUID id) {
		return entries.removeIf(e -> e.id().equals(id));
	}

	public boolean contains(UUID id) {
		for (Entry e : entries) {
			if (e.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public int size() {
		return entries.size();
	}

	public List<Entry> entries() {
		return List.copyOf(entries);
	}

	public void clear() {
		entries.clear();
	}

	/** Takes every pair that can play now out of the queue. {@code window == null} means casual (first come, first served). */
	public List<Pair> match(long now, Window window) {
		List<Pair> pairs = new ArrayList<>();
		if (window == null) {
			while (entries.size() >= 2) {
				Entry a = entries.remove(0);
				Entry b = entries.remove(0);
				pairs.add(new Pair(a.id(), b.id()));
			}
			return pairs;
		}
		List<Entry> byWait = new ArrayList<>(entries);
		byWait.sort(Comparator.comparingLong(Entry::since));
		List<Entry> taken = new ArrayList<>();
		for (Entry a : byWait) {
			if (taken.contains(a)) {
				continue;
			}
			Entry best = null;
			int bestDiff = Integer.MAX_VALUE;
			for (Entry b : byWait) {
				if (b == a || taken.contains(b)) {
					continue;
				}
				int diff = Math.abs(a.rating() - b.rating());
				int allowed = Math.max(window.at(now - a.since()), window.at(now - b.since()));
				if (diff <= allowed && diff < bestDiff) {
					best = b;
					bestDiff = diff;
				}
			}
			if (best != null) {
				taken.add(a);
				taken.add(best);
				pairs.add(new Pair(a.id(), best.id()));
			}
		}
		entries.removeAll(taken);
		return pairs;
	}
}
