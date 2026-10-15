package net.battlehub.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What everyone did in one match, for the recap at the end: damage dealt and taken, hits and swings (accuracy),
 * arrows shot and hit, the longest combo (hits in a row without being hit), kills, and the MVP.
 */
public final class MatchRecap {
	public static final class Line {
		public double dealt;
		public double taken;
		public int hits;
		public int swings;
		public int shots;
		public int arrowHits;
		public int combo;
		public int bestCombo;
		public int kills;

		/** Hits per swing in percent (0 without swings). */
		public int accuracy() {
			return swings == 0 ? 0 : Math.min(100, (int) Math.round(hits * 100.0 / swings));
		}

		public int bowAccuracy() {
			return shots == 0 ? 0 : Math.min(100, (int) Math.round(arrowHits * 100.0 / shots));
		}

		/** Ranking value for the MVP: kills count most, then damage. */
		public double score() {
			return kills * 10 + dealt + bestCombo;
		}
	}

	private final Map<UUID, Line> lines = new LinkedHashMap<>();

	public Line line(UUID id) {
		return lines.computeIfAbsent(id, k -> new Line());
	}

	public boolean has(UUID id) {
		return lines.containsKey(id);
	}

	public void swing(UUID who) {
		line(who).swings++;
	}

	public void shot(UUID who) {
		line(who).shots++;
	}

	/** {@code attacker} hit {@code victim} for {@code amount} (null attacker: the void, fire, ...). */
	public void hit(UUID attacker, UUID victim, double amount, boolean arrow) {
		double dmg = Math.max(0, amount);
		Line v = line(victim);
		v.taken += dmg;
		v.combo = 0;
		if (attacker == null || attacker.equals(victim)) {
			return;
		}
		Line a = line(attacker);
		a.dealt += dmg;
		if (arrow) {
			a.arrowHits++;
		} else {
			a.hits++;
			// A swing goes with every hit; count one if the client did not report it
			a.swings = Math.max(a.swings, a.hits);
		}
		a.combo++;
		a.bestCombo = Math.max(a.bestCombo, a.combo);
	}

	public void kill(UUID killer) {
		if (killer != null) {
			line(killer).kills++;
		}
	}

	/** The player with the highest score among these, or null when nobody did anything. */
	public UUID mvp(List<UUID> players) {
		UUID best = null;
		double bestScore = 0;
		for (UUID id : players) {
			Line l = lines.get(id);
			if (l != null && l.score() > bestScore) {
				bestScore = l.score();
				best = id;
			}
		}
		return best;
	}
}
