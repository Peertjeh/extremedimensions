package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * What a bot tells a player after a practice fight: what it noticed about how they fight, how the fight went, and
 * one concrete tip. Lines are language keys with arguments (the world layer translates them), so this can be
 * tested on its own.
 */
public final class Debrief {
	private Debrief() {}

	public record Line(String key, Object... args) {}

	/**
	 * @param style       the bot's archetype id
	 * @param level       the bot's difficulty id
	 * @param model       what the bot learned about this player (may be null)
	 * @param dealtByBot  damage the bot did to this player
	 * @param takenByBot  damage this player did to the bot
	 * @param tactics     share of time per tactic in this fight (0..1)
	 */
	public static List<Line> lines(String botName, String style, String level, OpponentModel model, double dealtByBot, double takenByBot,
			java.util.Map<Tactic, Double> tactics) {
		List<Line> out = new ArrayList<>();
		out.add(new Line("debrief.header", botName, style, level));
		out.add(new Line("debrief.damage", Math.round(takenByBot), Math.round(dealtByBot)));
		Tactic main = Tactic.IDLE;
		double mainShare = 0;
		for (var e : tactics.entrySet()) {
			if (e.getKey() != Tactic.IDLE && e.getValue() > mainShare) {
				main = e.getKey();
				mainShare = e.getValue();
			}
		}
		if (main != Tactic.IDLE) {
			out.add(new Line("debrief.bot_plan", "debrief.tactic." + main.name().toLowerCase(java.util.Locale.ROOT), Math.round(mainShare * 100)));
		}
		if (model != null && model.known()) {
			out.add(new Line("debrief.you", Math.round(model.aggression() * 100), String.format(java.util.Locale.ROOT, "%.1f", model.preferredDistance()),
					String.format(java.util.Locale.ROOT, "%.1f", model.strafeRate())));
			out.add(new Line(tip(style, model, dealtByBot, takenByBot)));
		} else {
			out.add(new Line("debrief.tip.short"));
		}
		return out;
	}

	/** The one thing most worth working on, from what the bot saw. */
	static String tip(String style, OpponentModel m, double dealtByBot, double takenByBot) {
		boolean lost = dealtByBot > takenByBot;
		if (m.strafeRate() < 0.4 && lost) return "debrief.tip.strafe";
		if (m.aggression() > 0.65 && (style.equals("counter") || style.equals("opportunist")) && lost) return "debrief.tip.patience";
		if (m.aggression() < 0.3 && lost) return "debrief.tip.pressure";
		if (m.preferredDistance() < 2.2) return "debrief.tip.spacing";
		if (!lost) return "debrief.tip.next_level";
		return "debrief.tip.trade";
	}
}
