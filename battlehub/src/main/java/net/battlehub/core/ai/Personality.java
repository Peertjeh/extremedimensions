package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * How an NPC likes to fight, in plain numbers the decisions really use (0 = not at all, 1 = very):
 * <ul>
 * <li>{@code aggression}: how much it wants to close in and hit;</li>
 * <li>{@code risk}: how much danger it accepts (low health, edges) before it backs off;</li>
 * <li>{@code patience}: how willing it is to wait for a better moment (its own cooldown, the other's mistake);</li>
 * <li>{@code range}: the distance it likes to hold while waiting, in blocks (1.5 .. 6);</li>
 * <li>{@code adaptability}: how strongly results change its choices (a tactic that keeps failing is dropped);</li>
 * <li>{@code variability}: how much its choices vary between equal options (never over safety);</li>
 * <li>{@code defensive}: how much it values not being hit over hitting;</li>
 * <li>{@code objective}: how much the game's objective counts against fighting.</li>
 * </ul>
 */
public record Personality(double aggression, double risk, double patience, double range, double adaptability, double variability,
		double defensive, double objective) {

	/** What is out of range (empty when valid). */
	public List<String> problems() {
		List<String> out = new ArrayList<>();
		check(out, "aggression", aggression);
		check(out, "risk", risk);
		check(out, "patience", patience);
		check(out, "adaptability", adaptability);
		check(out, "variability", variability);
		check(out, "defensive", defensive);
		check(out, "objective", objective);
		if (!(range >= 1.5 && range <= 6)) out.add("range " + range + " is not between 1.5 and 6");
		return out;
	}

	private static void check(List<String> out, String name, double v) {
		if (!(v >= 0 && v <= 1)) out.add(name + " " + v + " is not between 0 and 1");
	}

	/** The same values brought into range (for settings from a file). */
	public Personality clamped() {
		return new Personality(c(aggression), c(risk), c(patience), Math.max(1.5, Math.min(6, Double.isNaN(range) ? 3 : range)),
				c(adaptability), c(variability), c(defensive), c(objective));
	}

	private static double c(double v) {
		return Double.isNaN(v) ? 0.5 : Math.max(0, Math.min(1, v));
	}

	/**
	 * A personal variation of this personality: every value moves a little, the same way every time for the same
	 * seed (a bot's name), so two bots of one archetype are recognisably different but neither becomes another kind.
	 */
	public Personality individual(long seed, double amount) {
		Random r = new Random(seed);
		return new Personality(aggression + j(r, amount), risk + j(r, amount), patience + j(r, amount), range + j(r, amount) * 3,
				adaptability + j(r, amount), variability + j(r, amount), defensive + j(r, amount), objective + j(r, amount)).clamped();
	}

	private static double j(Random r, double amount) {
		return (r.nextDouble() * 2 - 1) * amount;
	}
}
