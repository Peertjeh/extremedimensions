package net.battlehub.core.ai;

import java.util.List;
import java.util.UUID;

/**
 * Which enemy to fight. Every candidate gets a cost: distance, plus a penalty for not being seen (only located by
 * name tag, or only remembered), minus a bonus for whoever hit this NPC lately (fight back), for a weak target
 * (aggressive and opportunist styles finish people off) and for the current target (no flicking between two).
 * The cheapest wins.
 */
public final class Targeting {
	private Targeting() {}

	/**
	 * One enemy as the NPC knows it. {@code hitMeAgo}: ticks since they last hurt this NPC (large when never);
	 * {@code healthShare} is only meaningful when {@code seen}.
	 */
	public record Candidate(UUID id, double distance, boolean seen, boolean located, double confidence, double healthShare, int hitMeAgo,
			boolean current) {}

	public static double cost(Candidate c, Personality p) {
		double cost = c.distance();
		if (!c.seen()) cost += c.located() ? 6 : 14 + (1 - c.confidence()) * 10;
		if (c.hitMeAgo() < 60) cost -= 6 * (1 - c.hitMeAgo() / 60.0) * (0.5 + 0.5 * p.defensive());
		if (c.seen() && c.healthShare() < 0.5) cost -= 8 * (0.5 - c.healthShare()) * (0.5 + 0.5 * Math.max(p.aggression(), p.adaptability()));
		if (c.current()) cost -= 3;
		return cost;
	}

	/** The best candidate, or null when there is none. */
	public static Candidate choose(List<Candidate> all, Personality p) {
		Candidate best = null;
		double bestCost = Double.MAX_VALUE;
		for (Candidate c : all) {
			double k = cost(c, p);
			if (k < bestCost) {
				bestCost = k;
				best = c;
			}
		}
		return best;
	}
}
