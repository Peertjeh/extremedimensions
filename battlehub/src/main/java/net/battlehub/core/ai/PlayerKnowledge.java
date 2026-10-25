package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What bots remember about one player across sessions, if the server allows it: how they fight (rolling averages
 * of the opponent model: movement, how they swing and how often they land), which bot tactics worked against them,
 * and how strong they are against bots: a rating from every fight against a bot (practice, filled queues, party
 * games), each one weighted by how much it says, with the damage share as extra signal next to winning. The
 * adaptive level is the level where that rating expects a fight the player wins a little more often than not.
 * Small and bounded; the player can see it and wipe it. Plain fields, so it saves as JSON.
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
	/** Ticks between their swings, and how often they land (rolling over fights). */
	public double swingGap = 12;
	public double accuracy = 0.6;
	public int swingFights;
	/** Strength against bots (on the scale of {@link #LEARNED}), valid once {@code rated} > 0. */
	public double rating;
	/** Which scale the rating is on: 0 the guessed one of before 1.48 ({@link #GUESS}), {@link #SCALE} the measured one. */
	public int scale;
	/** How much rated play the rating rests on (one duel counts 1). */
	public double rated;
	/** Whether recent results keep beating (positive) or missing (negative) the estimate. */
	public double drift;

	/**
	 * Where each level sits on the rating scale, measured (ML phase 3E): the levels as the mod plays them (their model,
	 * perception delay, aim noise, turn limit and sampling), rated against each other in the simulation, the scripted bot
	 * of skill 0.5 at 1000. Their knobs are set so the steps are even: one level up, a player wins about a quarter less
	 * often. The fight logs agree on the gap between intermediate and master.
	 */
	static final double[] LEARNED = {500, 740, 1020, 1230, 1540, 1770};
	/**
	 * The scripted mace bot (the model does not fight with the mace): placed by the fights of people against it in the
	 * fight logs, from which intermediate and master are known; the levels between are spread evenly. Much weaker than
	 * the learned fight, so a mace win does not lift a rating as a win against the learned fight would.
	 */
	static final double[] MACE = {270, 390, 510, 620, 740, 860};
	/** The scale of before 1.48, a guess: two hundred apart from 700. A stored rating on it moves to the measured one. */
	static final double[] GUESS = {700, 900, 1100, 1300, 1500, 1700};
	/** The scale ratings are on now. */
	public static final int SCALE = 2;

	/** Where each level sits on the rating scale. */
	public static double levelRating(Difficulty d) {
		return LEARNED[d.ordinal()];
	}

	/** Where each level sits, for the mace (its own scripted bot) or the rest. */
	public static double levelRating(Difficulty d, boolean mace) {
		return (mace ? MACE : LEARNED)[d.ordinal()];
	}

	/** The chance a player with this rating beats a bot of this level. */
	public static double expected(double rating, Difficulty d) {
		return expected(rating, d, false);
	}

	public static double expected(double rating, Difficulty d, boolean mace) {
		return 1 / (1 + Math.pow(10, (levelRating(d, mace) - rating) / 400));
	}

	/** Where someone never rated against bots starts: a little below intermediate. */
	public static double start() {
		return LEARNED[Difficulty.INTERMEDIATE.ordinal()] - 50;
	}

	/** A rating on the guessed scale of before 1.48, moved to the measured one (level for level, in between in proportion). */
	static double moved(double old) {
		int n = GUESS.length;
		if (old <= GUESS[0]) return LEARNED[0] + (old - GUESS[0]) * (LEARNED[1] - LEARNED[0]) / (GUESS[1] - GUESS[0]);
		for (int i = 1; i < n; i++) {
			if (old <= GUESS[i]) return LEARNED[i - 1] + (old - GUESS[i - 1]) * (LEARNED[i] - LEARNED[i - 1]) / (GUESS[i] - GUESS[i - 1]);
		}
		return LEARNED[n - 1] + (old - GUESS[n - 1]) * (LEARNED[n - 1] - LEARNED[n - 2]) / (GUESS[n - 1] - GUESS[n - 2]);
	}

	/** The adaptive level aims at fights the player wins a little more often than not. */
	public static final double TARGET = 0.55;

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
		if (m != null && m.swings() >= 5) {
			double w = swingFights == 0 ? 1 : W;
			swingGap += w * (m.swingGap() - swingGap);
			accuracy += w * (m.accuracy() - accuracy);
			swingFights++;
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
		if (swingFights > 0 && m != null) m.seedSwings(swingGap, accuracy, 4 + swingFights * 2);
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

	/** A result against a bot at a level, counted fully (a practice duel). */
	public Change result(boolean won, Difficulty level) {
		return result(won, won ? 0.6 : 0.4, level, 1, start());
	}

	/**
	 * One fight against a bot of {@code level}: won or not, the share of the damage the player did (0..1), how much
	 * the fight says ({@code weight}: 1 for a duel against one bot, less when more people fought) and the starting
	 * rating for a player never rated before ({@code prior}, e.g. from their ranked results). Returns the step of the
	 * adaptive level and why, or null when it stays.
	 */
	public Change result(boolean won, double share, Difficulty level, double weight, double prior) {
		return result(won, share, level, weight, prior, false);
	}

	/**
	 * Like {@link #result(boolean, double, Difficulty, double, double)}; {@code mace}: against the scripted mace bot, whose
	 * levels sit lower on the scale ({@link #MACE}), so beating it says less.
	 */
	public Change result(boolean won, double share, Difficulty level, double weight, double prior, boolean mace) {
		results.add((won ? "W:" : "L:") + level.id());
		while (results.size() > RESULTS) results.remove(0);
		if (rated <= 0) {
			rating = prior;
			scale = SCALE;
		}
		double before = rating;
		// winning counts most; the damage share tells a close loss from a beating
		double score = 0.75 * (won ? 1 : 0) + 0.25 * Math.max(0, Math.min(1, share));
		double surprise = score - expected(rating, level, mace);
		// results that keep missing the estimate the same way mean the estimate is off: move faster; results that
		// scatter round it mean it is about right: stay calm
		drift = 0.6 * drift + 0.4 * surprise;
		double k = (rated < 8 ? 100 : rated < 20 ? 60 : 40) * (1 + 2 * Math.abs(drift)) * Math.max(0, Math.min(1, weight));
		rating = Math.max(300, Math.min(2200, rating + k * surprise));
		rated += Math.max(0, Math.min(1, weight));
		Difficulty current = Difficulty.parse(adaptiveLevel, Difficulty.INTERMEDIATE);
		Difficulty next = pick(rating, current);
		if (next == current) return null;
		adaptiveLevel = next.id();
		return new Change(current, next, before, rating, expected(rating, next));
	}

	/**
	 * The level whose expected win chance is closest to {@link #TARGET}; the current one stays unless another is
	 * clearly closer (no switching back and forth on a single fight).
	 */
	public static Difficulty pick(double rating, Difficulty current) {
		Difficulty best = current;
		for (Difficulty d : Difficulty.values()) {
			if (Math.abs(expected(rating, d) - TARGET) < Math.abs(expected(rating, best) - TARGET)) best = d;
		}
		return Math.abs(expected(rating, best) - TARGET) < Math.abs(expected(rating, current) - TARGET) - 0.02 ? best : current;
	}

	/** A step of the adaptive level: from, to, the rating before and after, and the expected win chance there. */
	public record Change(Difficulty from, Difficulty to, double before, double after, double chance) {}

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
		swingGap = finite(swingGap, 12, 2, 40);
		accuracy = finite(accuracy, 0.6, 0, 1);
		swingFights = Math.max(0, swingFights);
		rated = finite(rated, 0, 0, 1e6);
		rating = finite(rating, start(), 300, 2200);
		// a rating from before 1.48 is on the guessed scale: moved to the measured one, once
		if (rated > 0 && scale < SCALE) rating = Math.max(300, Math.min(2200, moved(rating)));
		scale = SCALE;
		drift = finite(drift, 0, -1, 1);
		return this;
	}

	private static double finite(double v, double dflt, double lo, double hi) {
		return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : dflt;
	}
}
