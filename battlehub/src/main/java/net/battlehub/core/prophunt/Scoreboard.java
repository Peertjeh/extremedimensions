package net.battlehub.core.prophunt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Points across the rounds of a Prop Hunt match; the most points win (all of them on a tie). */
public final class Scoreboard<K> {
	private final Map<K, Integer> points = new LinkedHashMap<>();

	public int add(K player, int delta) {
		return points.merge(player, delta, Integer::sum);
	}

	public int get(K player) {
		return points.getOrDefault(player, 0);
	}

	public Map<K, Integer> all() {
		return Map.copyOf(points);
	}

	/** Players ordered by points, highest first. */
	public List<K> ranking(List<K> players) {
		List<K> out = new ArrayList<>(players);
		out.sort((a, b) -> Integer.compare(get(b), get(a)));
		return out;
	}

	/** The leaders among these players (empty when nobody has scored). */
	public List<K> winners(List<K> players) {
		int best = Integer.MIN_VALUE;
		for (K p : players) best = Math.max(best, get(p));
		List<K> out = new ArrayList<>();
		if (best <= 0) {
			return out;
		}
		for (K p : players) {
			if (get(p) == best) out.add(p);
		}
		return out;
	}
}
