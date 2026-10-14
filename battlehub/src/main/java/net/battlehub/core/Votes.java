package net.battlehub.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Majority vote: the option with the most votes wins, a tie is decided at random; empty votes do not count. */
public final class Votes {
	private Votes() {}

	/** The winning option, or "" when nobody voted. */
	public static String tally(Collection<String> votes, Random random) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String v : votes) {
			if (v != null && !v.isBlank()) {
				counts.merge(v, 1, Integer::sum);
			}
		}
		if (counts.isEmpty()) {
			return "";
		}
		int best = counts.values().stream().max(Integer::compare).orElse(0);
		List<String> top = new ArrayList<>();
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			if (e.getValue() == best) {
				top.add(e.getKey());
			}
		}
		return top.get(random.nextInt(top.size()));
	}
}
