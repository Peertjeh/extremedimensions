package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What bots remember about one player across sessions, if the server allows it: how they fight (rolling averages
 * of the opponent model), which bot tactics worked against them, and their last results against each level (for
 * the adaptive level). Small and bounded (a few numbers, the last eight results); the player can see it and wipe
 * it. Plain fields, so it saves as JSON.
 */
public final class PlayerKnowledge {
	/** Weight of one new fight in the stored averages. */
	static final double W = 0.3;
	static final int RESULTS = 8;

	public double closing;
	public double distance = 4;
	public double strafeRate;
	public double jumpRate;
	public int fights;
	/** Bot tactic -> how well it worked against this player (damage dealt minus taken, rolling). */
	public Map<String, Double> tactics = new java.util.HashMap<>();
	/** Last results against bots, newest last: "W:expert" or "L:casual". */
	public List<String> results = new ArrayList<>();
	/** The level the adaptive setting would pick now. */
	public String adaptiveLevel = Difficulty.INTERMEDIATE.id();

	/** Folds one fight into what is remembered. */
	public void learn(OpponentModel m, OutcomeMemory outcomes) {
		if (m != null && m.known()) {
			double w = fights == 0 ? 1 : W;
			closing += w * (m.closing() - closing);
			distance += w * (m.preferredDistance() - distance);
			strafeRate += w * (m.strafeRate() - strafeRate);
			jumpRate += w * (m.jumpRate() - jumpRate);
			fights++;
		}
		if (outcomes != null) {
			for (Tactic t : Tactic.values()) {
				if (outcomes.samples(t) < 3) continue;
				String k = t.name().toLowerCase(java.util.Locale.ROOT);
				double old = tactics.getOrDefault(k, 0.0);
				tactics.put(k, tactics.containsKey(k) ? old + W * (outcomes.value(t) - old) : outcomes.value(t));
			}
		}
	}

	/** Gives a new fight's models a head start from what is remembered. */
	public void seed(OpponentModel m, OutcomeMemory outcomes) {
		if (fights > 0 && m != null) m.seed(closing, distance, strafeRate, jumpRate, 15 + fights * 5);
		if (outcomes != null) {
			for (Map.Entry<String, Double> e : tactics.entrySet()) {
				Tactic t;
				try {
					t = Tactic.valueOf(e.getKey().toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException ex) {
					continue;
				}
				outcomes.seed(t, e.getValue(), 3);
			}
		}
	}

	/** A result against bots at a level; returns the new adaptive level and why (or null when it stays). */
	public Change result(boolean won, Difficulty level) {
		results.add((won ? "W:" : "L:") + level.id());
		while (results.size() > RESULTS) results.remove(0);
		Difficulty current = Difficulty.parse(adaptiveLevel, Difficulty.INTERMEDIATE);
		// only results at the current adaptive level count for the next step
		int wins = 0, losses = 0, n = 0;
		for (int i = results.size() - 1; i >= 0 && n < 4; i--) {
			String r = results.get(i);
			if (!r.endsWith(":" + current.id())) break;
			n++;
			if (r.startsWith("W")) wins++;
			else losses++;
		}
		Difficulty[] all = Difficulty.values();
		Difficulty next = current;
		if (n >= 3 && wins >= 3 && current.ordinal() < all.length - 1) next = all[current.ordinal() + 1];
		if (n >= 3 && losses >= 3 && current.ordinal() > 0) next = all[current.ordinal() - 1];
		if (next == current) return null;
		adaptiveLevel = next.id();
		return new Change(current, next, wins, losses, n);
	}

	/** A step of the adaptive level, with the results that caused it. */
	public record Change(Difficulty from, Difficulty to, int wins, int losses, int of) {}

	/** Wins and losses against bots, per level id. */
	public Map<String, int[]> record() {
		Map<String, int[]> out = new java.util.LinkedHashMap<>();
		for (String r : results) {
			int[] wl = out.computeIfAbsent(r.substring(2), k -> new int[2]);
			wl[r.startsWith("W") ? 0 : 1]++;
		}
		return out;
	}

	/** Brings stored values back in range (a file edited by hand). */
	public PlayerKnowledge clean() {
		closing = finite(closing, 0, -1, 1);
		distance = finite(distance, 4, 0, 16);
		strafeRate = finite(strafeRate, 0, 0, 20);
		jumpRate = finite(jumpRate, 0, 0, 20);
		fights = Math.max(0, fights);
		if (tactics == null) tactics = new java.util.HashMap<>();
		tactics.replaceAll((k, v) -> finite(v, 0, -20, 20));
		if (results == null) results = new ArrayList<>();
		results.removeIf(r -> r == null || r.length() < 3 || !(r.startsWith("W:") || r.startsWith("L:")));
		while (results.size() > RESULTS) results.remove(0);
		adaptiveLevel = Difficulty.parse(adaptiveLevel, Difficulty.INTERMEDIATE).id();
		return this;
	}

	private static double finite(double v, double dflt, double lo, double hi) {
		return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : dflt;
	}
}
