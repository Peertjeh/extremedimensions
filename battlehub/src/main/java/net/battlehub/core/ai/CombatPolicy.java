package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores every tactic that makes sense right now (utility AI). Each score is built from a few considerations
 * (health, distance, attack charge, edges, the opponent's habits, the personality, what worked lately) and keeps
 * the reasons, so a decision can always be explained. Tactics that make no sense are left out.
 */
public final class CombatPolicy {
	private CombatPolicy() {}

	public static List<Score> score(Situation s, OpponentModel om, OutcomeMemory mem, Personality p, Difficulty d) {
		return score(s, om, mem, p, d, 0);
	}

	/**
	 * {@code quiet}: ticks since anyone in this fight was hit. Waiting is a tactic, not a way of life: the longer
	 * nothing happens, the more pressing in wins (how long it takes depends on patience), so even a counter-puncher
	 * goes in against an opponent who only stands there.
	 */
	public static List<Score> score(Situation s, OpponentModel om, OutcomeMemory mem, Personality p, Difficulty d, int quiet) {
		List<Score> out = new ArrayList<>();
		double impatience = Math.max(0, Math.min(0.5, (quiet - 40) / 120.0)) * (1 - 0.6 * p.patience());
		Situation.Self me = s.self();
		Situation.Target t = s.target();
		double hp = me.healthShare();
		double dist = s.distance();
		double reach = me.reach();
		double ready = me.attackReady();
		boolean seen = t != null && t.visible();
		double thp = t == null ? 1 : t.healthShare();

		// ---- safety first: a hit from the target would push us off a deadly edge
		if (seen && dist < reach + 2) {
			V3 push = me.pos().sub(t.pos()).flat().unit();
			double behind = s.ground(push);
			double limit = 1.0 + 0.6 * (1 - p.risk());
			if (behind < limit) {
				List<String> why = new ArrayList<>();
				why.add(String.format(java.util.Locale.ROOT, "edge %.1f behind", behind));
				out.add(new Score(Tactic.RECOVER, 1.2 + (limit - behind) * 0.5, behind < 1.0, why));
			}
		} else if (s.nearestEdge() < 0.5) {
			out.add(new Score(Tactic.RECOVER, 0.7, false, List.of("standing on an edge")));
		}

		if (s.threat() != null && s.threat().incoming() > 0) {
			out.add(new Score(Tactic.EVADE, 0.9 + 0.1 * Math.min(3, s.threat().incoming()), false, List.of(s.threat().incoming() + " incoming")));
		}

		if (me.canHeal() && d.healBelow > 0 && me.health() < d.healBelow) {
			List<String> why = new ArrayList<>(List.of(String.format(java.util.Locale.ROOT, "health %.0f%%", hp * 100)));
			double v = 0.55 + 0.6 * (1 - hp);
			if (dist > 5) {
				v += 0.25;
				why.add("room to eat");
			} else if (dist < reach + 0.5) {
				v -= 0.15 * (1 - p.risk());
				why.add("target close");
			}
			out.add(new Score(Tactic.HEAL, v, false, why));
		}

		// a bow without a sword: melee is a poor idea, distance is everything
		double noBlade = me.hasMelee() ? 0 : 0.5;
		if (seen && me.hasBow() && dist > 3.5 && dist < 45) {
			double v = 0.35 + 0.4 * (p.range() / 6) + (dist >= 8 && dist <= 28 ? 0.25 : 0) + noBlade * 0.6;
			List<String> why = new ArrayList<>(List.of(String.format(java.util.Locale.ROOT, "%.0f blocks", dist)));
			if (dist < 6 && me.hasMelee()) {
				v -= 0.45;
				why.add("too close for the bow");
			}
			if (me.drawTicks() > 0) {
				v += 0.15;
				why.add("already drawing");
			}
			double trend = mem.trend(Tactic.SHOOT);
			if (trend != 0) {
				v += 0.3 * p.adaptability() * trend;
				why.add(trend < 0 ? "arrows miss" : "arrows land");
			}
			out.add(new Score(Tactic.SHOOT, v, false, why));
		}

		if (seen) {
			// ---- back off when losing (or, with only a bow, when they get close)
			if (dist < 8) {
				double v = 0.15 + (1 - hp) * (0.9 - 0.7 * p.risk()) + 0.25 * p.defensive() * Math.max(0, thp - hp);
				if (me.hasBow() && !me.hasMelee() && dist < 7) v += 0.45;
				v += 0.3 * p.adaptability() * Math.min(0, mem.trend(Tactic.STRIKE)) * -1 * (1 - hp);
				out.add(new Score(Tactic.RETREAT, v, false, List.of(String.format(java.util.Locale.ROOT, "health %.0f%% vs %.0f%%", hp * 100, thp * 100))));
			}
			// ---- close in
			if (dist > reach + 0.25) {
				double v = 0.35 + 0.45 * p.aggression() + 0.25 * (hp - thp) - noBlade;
				List<String> why = new ArrayList<>(List.of(String.format(java.util.Locale.ROOT, "%.1f away", dist)));
				if (dist < p.range() + 1) {
					v -= 0.3 * p.patience();
					why.add("near my range");
				}
				if (impatience > 0) {
					v += impatience;
					why.add("nothing happens");
				}
				out.add(new Score(Tactic.APPROACH, v, false, why));
			}
			// ---- trade hits
			if (dist <= reach + 0.8) {
				double v = 0.45 + 0.35 * p.aggression() + 0.25 * ready + 0.2 * (hp - thp) - 0.25 * p.patience() * (1 - ready) - noBlade;
				List<String> why = new ArrayList<>(List.of(String.format(java.util.Locale.ROOT, "charge %.0f%%", ready * 100)));
				if (dist > reach) {
					// just outside reach: hitting means stepping in, which patient styles leave to the other
					v -= 0.3 * p.patience();
					why.add("must step in");
				}
				if (t.swung() < 8) {
					v += 0.25 * (0.5 + p.adaptability() * 0.5);
					why.add("they just swung");
				}
				if (thp < 0.3) {
					v += 0.2 * (0.5 + p.aggression() * 0.5);
					why.add("they are low");
				}
				double trend = mem.trend(Tactic.STRIKE);
				if (trend != 0) {
					v += 0.3 * p.adaptability() * trend;
					why.add(trend < 0 ? "trading badly" : "trading well");
				}
				if (impatience > 0) {
					v += impatience;
					why.add("nothing happens");
				}
				out.add(new Score(Tactic.STRIKE, v, false, why));
			}
			// ---- hold just outside their reach and punish
			if (dist <= p.range() + 2.5) {
				double v = 0.25 + 0.45 * p.patience() + 0.25 * p.defensive() + 0.3 * (1 - ready) - 0.35 * p.aggression() - noBlade;
				List<String> why = new ArrayList<>();
				if (om.known() && om.aggression() > 0.5) {
					v += 0.3 * om.aggression() * p.patience();
					why.add("they rush in");
				}
				double trend = mem.trend(Tactic.SPACE);
				if (trend != 0) {
					v += 0.3 * p.adaptability() * trend;
					why.add(trend < 0 ? "spacing fails" : "spacing works");
				}
				if (impatience > 0) {
					v -= impatience;
					why.add("waited long enough");
				}
				out.add(new Score(Tactic.SPACE, v, false, why));
			}
		} else if (t != null && t.confidence() > 0.15) {
			out.add(new Score(Tactic.SEARCH, 0.3 + 0.4 * t.confidence() * (0.5 + 0.5 * p.aggression()), false,
					List.of(String.format(java.util.Locale.ROOT, "last seen, %.0f%% sure", t.confidence() * 100))));
		}

		if (s.objective() != null) {
			double v = 0.25 + 0.55 * p.objective() + (t == null || (!seen && t.confidence() < 0.15) ? 0.4 : dist > 12 ? 0.25 : -0.2);
			out.add(new Score(Tactic.OBJECTIVE, v, false, List.of("objective")));
		}
		out.add(new Score(Tactic.IDLE, 0.05, false, List.of()));
		return out;
	}
}
