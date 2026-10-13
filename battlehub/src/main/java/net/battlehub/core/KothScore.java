package net.battlehub.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * King of the Hill scoring. Call {@link #second(Collection)} once per second with the players inside the zone.
 * A lone king scores; a contested hill scores for nobody unless {@code shareContested} is set.
 */
public final class KothScore {
	private final Map<UUID, Integer> points = new HashMap<>();
	private final int perSecond;
	private final boolean shareContested;

	public KothScore(int perSecond, boolean shareContested) {
		this.perSecond = Math.max(1, perSecond);
		this.shareContested = shareContested;
	}

	/** Returns who scored this second (empty when nobody or contested). */
	public List<UUID> second(Collection<UUID> inZone) {
		if (inZone.isEmpty() || (inZone.size() > 1 && !shareContested)) {
			return List.of();
		}
		for (UUID id : inZone) {
			points.merge(id, perSecond, Integer::sum);
		}
		return List.copyOf(inZone);
	}

	public int points(UUID id) {
		return points.getOrDefault(id, 0);
	}

	/** Highest scorer, or null if nobody scored. Ties go to whoever reached the score first is not tracked: null on tie. */
	public UUID leader() {
		UUID best = null;
		int bestPts = 0;
		boolean tie = false;
		for (Map.Entry<UUID, Integer> e : points.entrySet()) {
			if (e.getValue() > bestPts) {
				best = e.getKey();
				bestPts = e.getValue();
				tie = false;
			} else if (e.getValue() == bestPts && bestPts > 0) {
				tie = true;
			}
		}
		return tie ? null : best;
	}

	/** The player who reached {@code target}, or null. */
	public UUID reached(int target) {
		for (Map.Entry<UUID, Integer> e : points.entrySet()) {
			if (e.getValue() >= target) {
				return e.getKey();
			}
		}
		return null;
	}

	public Map<UUID, Integer> all() {
		return Map.copyOf(points);
	}
}
