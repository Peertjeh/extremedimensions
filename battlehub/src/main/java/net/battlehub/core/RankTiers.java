package net.battlehub.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ELO thresholds to tier names (Bronze, Silver, ...). Thresholds come from the config. */
public final class RankTiers {
	public record Tier(String id, int minElo) {}

	private final List<Tier> tiers;

	public RankTiers(Map<String, Integer> thresholds) {
		List<Tier> list = new ArrayList<>();
		thresholds.forEach((id, min) -> list.add(new Tier(id, min == null ? 0 : min)));
		list.sort(Comparator.comparingInt(Tier::minElo));
		if (list.isEmpty()) {
			list.add(new Tier("bronze", 0));
		}
		this.tiers = List.copyOf(list);
	}

	public static Map<String, Integer> defaults() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("bronze", 0);
		m.put("silver", 1100);
		m.put("gold", 1250);
		m.put("diamond", 1450);
		m.put("netherite", 1700);
		return m;
	}

	/** Highest tier whose threshold the rating reaches; the lowest tier for anything below. */
	public Tier tierFor(int elo) {
		Tier best = tiers.get(0);
		for (Tier t : tiers) {
			if (elo >= t.minElo()) {
				best = t;
			}
		}
		return best;
	}

	/** The next tier up, or null at the top. */
	public Tier nextTier(int elo) {
		for (Tier t : tiers) {
			if (t.minElo() > elo) {
				return t;
			}
		}
		return null;
	}

	public List<Tier> all() {
		return tiers;
	}
}
