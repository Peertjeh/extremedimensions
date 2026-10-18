package net.battlehub.core.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Picks the tactic from the scores and owns every switch. It keeps a choice for a while unless another is clearly
 * better (no dithering), lets safety tactics interrupt at once, adds a little personal variation between close
 * options, and at lower levels sometimes takes a worse option on purpose; never instead of a safety tactic. Every
 * switch is remembered with its reason (the last twelve).
 */
public final class Decider {
	public record Transition(int tick, Tactic from, Tactic to, String why) {}

	public record Decision(Tactic tactic, List<Score> ranked, String reason, boolean changed) {}

	private static final int KEEP = 12;
	private Tactic current = Tactic.IDLE;
	private int since;
	private final Deque<Transition> transitions = new ArrayDeque<>();

	public Tactic current() {
		return current;
	}

	public List<Transition> transitions() {
		return List.copyOf(transitions);
	}

	public Decision decide(int tick, List<Score> scores, Personality p, Difficulty d, Random rng) {
		List<Score> ranked = new ArrayList<>(scores.size());
		for (Score s : scores) {
			double noise = s.critical() || s.tactic().safety ? 0 : (rng.nextDouble() * 2 - 1) * p.variability() * 0.06;
			ranked.add(new Score(s.tactic(), s.value() + noise, s.critical(), s.why()));
		}
		// a critical score (a deadly edge) always comes first, whatever the variation does to the others
		ranked.sort(Comparator.comparing((Score x) -> !x.critical()).thenComparing(Comparator.comparingDouble(Score::value).reversed()));
		if (ranked.isEmpty()) {
			ranked.add(new Score(Tactic.IDLE, 0, false, List.of()));
		}
		Score best = ranked.get(0);
		Score cur = null;
		for (Score s : ranked) {
			if (s.tactic() == current) cur = s;
		}
		Score pick;
		String reason;
		if (best.critical() || (best.tactic().safety && best.tactic() != current)) {
			pick = best;
			reason = "interrupt: " + String.join(", ", best.why());
		} else if (cur != null && cur.tactic() != best.tactic() && !cur.tactic().safety) {
			double margin = d.switchMargin + (tick - since < d.commitTicks ? 0.1 : 0);
			if (best.value() - cur.value() < margin) {
				pick = cur;
				reason = "keep " + name(cur.tactic()) + " (" + name(best.tactic()) + " not clearly better)";
			} else {
				pick = best;
				reason = String.join(", ", best.why());
			}
		} else {
			pick = best;
			reason = cur == null && current != Tactic.IDLE ? name(current) + " no longer possible" : String.join(", ", best.why());
		}
		// a misjudgement happens at a moment of choice (when switching), never midway through a kept choice
		boolean choosing = pick.tactic() != current;
		if (choosing && !pick.critical() && !pick.tactic().safety && ranked.size() > 1 && rng.nextDouble() < d.mistakeRate) {
			for (Score s : ranked) {
				if (s.tactic() == pick.tactic() || s.tactic().safety || s.critical() || s.tactic() == Tactic.IDLE) continue;
				if (s.value() > pick.value() - 0.4) {
					pick = s;
					reason = "misjudged: " + name(s.tactic()) + " over the better choice";
				}
				break;
			}
		}
		boolean changed = pick.tactic() != current;
		if (changed) {
			transitions.addLast(new Transition(tick, current, pick.tactic(), reason));
			while (transitions.size() > KEEP) transitions.removeFirst();
			current = pick.tactic();
			since = tick;
		}
		return new Decision(current, ranked, reason, changed);
	}

	/** Forget the current choice (a new target, a new round). */
	public void reset(int tick) {
		current = Tactic.IDLE;
		since = tick;
	}

	private static String name(Tactic t) {
		return t.name().toLowerCase(java.util.Locale.ROOT);
	}
}
