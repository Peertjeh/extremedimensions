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

	/** Moving towards an edge that is near: less than a block of ground along its motion. */
	static boolean heading(Situation s) {
		V3 v = s.self().vel();
		return v.flatLength() >= 0.03 && s.ground(v) < 1.0;
	}

	public static List<Score> score(Situation s, OpponentModel om, OutcomeMemory mem, Personality p, Difficulty d) {
		return score(s, om, mem, p, d, 0);
	}

	/**
	 * {@code quiet}: ticks since anyone in this fight was hit. Waiting is a tactic, not a way of life: the longer
	 * nothing happens, the more pressing in wins (how long it takes depends on patience), so even a counter-puncher
	 * goes in against an opponent who only stands there. Close by it keeps growing for about fourteen seconds: the most
	 * patient, most defensive style there is still goes in then (it once held four blocks off for half a minute).
	 * Far away there is no stand-off: an impatient bot would only head for an opponent across the void.
	 */
	public static List<Score> score(Situation s, OpponentModel om, OutcomeMemory mem, Personality p, Difficulty d, int quiet) {
		List<Score> out = new ArrayList<>();
		Situation.Self me = s.self();
		Situation.Target t = s.target();
		double hp = me.healthShare();
		double dist = s.distance();
		// only a stand-off close by: far away (across a gap, on the way to the objective) nobody is waiting for anything
		double impatience = dist <= p.range() + 3 ? Math.max(0, Math.min(2.0, (quiet - 40) / 120.0)) * (1 - 0.6 * p.patience()) : 0;
		double reach = me.reach();
		double ready = me.attackReady();
		boolean seen = t != null && t.visible();
		double thp = t == null ? 1 : t.healthShare();
		Objective g = s.goal();
		boolean carrying = g != null && g.kind() == Objective.Kind.CARRY;
		// enemies far from the hill or the post are left alone
		boolean leashed = g != null && t != null && g.beyondLeash(t.pos());

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
		} else if (s.nearestEdge() < 0.5 && (heading(s) || (g == null && me.vel().flatLength() < 0.03))) {
			// walking along a narrow bridge the edges are at its sides: only an edge it is heading for counts (or,
			// standing about with nothing to do, any)
			out.add(new Score(Tactic.RECOVER, 0.7, false, List.of("standing on an edge")));
		}

		if (s.threat() != null && s.threat().smash() != null) {
			// a mace coming down on it: nothing matters more than not being under it
			out.add(new Score(Tactic.EVADE, 1.6, true, List.of("a smash coming down in " + s.threat().smash().in() + " ticks")));
		} else if (s.threat() != null && s.threat().incoming() > 0) {
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
		// a bow fight (the bow kit): the bow is the weapon, the sword only for when they are right on top
		boolean bowFight = me.focus().ranged() && me.hasBow();
		if (seen && me.hasBow() && dist > (bowFight ? 2.5 : 3.5) && dist < 45) {
			double v = 0.35 + 0.4 * (p.range() / 6) + (dist >= 8 && dist <= 28 ? 0.25 : 0) + noBlade * 0.6;
			List<String> why = new ArrayList<>(List.of(String.format(java.util.Locale.ROOT, "%.0f blocks", dist)));
			if (bowFight) {
				v += 0.35;
				why.add("a bow fight");
			}
			if (dist < (bowFight ? 3.2 : 6) && me.hasMelee()) {
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
			if (carrying) {
				v -= 0.5;
				why.add("carrying " + g.what());
			}
			out.add(new Score(Tactic.SHOOT, v, false, why));
		}

		if (seen) {
			// ---- back off when losing (or, with only a bow, when they get close)
			if (dist < 8) {
				double v = 0.15 + (1 - hp) * (0.9 - 0.7 * p.risk()) + 0.25 * p.defensive() * Math.max(0, thp - hp);
				if (me.hasBow() && !me.hasMelee() && dist < 7) v += 0.45;
				// in a bow fight: back off to make room for the bow
				if (bowFight && dist < 6) v += 0.2;
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
				if (bowFight && dist > 6) {
					v -= 0.3;
					why.add("a bow fight: no need to close in");
				}
				if (g != null && dist > 12) {
					// someone far off (across the void in The Bridge) is not worth leaving the objective for
					v -= Math.min(0.6, 0.2 + 0.02 * (dist - 12));
					why.add("far, the objective first");
				}
				v -= leashPenalty(g, carrying, leashed, why);
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
				// what it learned about how they swing
				if (om.spams()) {
					v += 0.12 + 0.1 * p.adaptability();
					why.add("they spam weak hits");
				}
				if (om.swings() >= 6 && om.accuracy() < 0.4) {
					v += 0.12 * (0.5 + p.adaptability());
					why.add("they miss a lot");
				}
				if (impatience > 0) {
					v += impatience;
					why.add("nothing happens");
				}
				if (carrying) {
					// only who blocks the way
					v -= 0.15;
					why.add("carrying " + g.what());
				}
				if (bowFight && dist > 2.5) {
					v -= 0.15;
					why.add("rather the bow");
				}
				out.add(new Score(Tactic.STRIKE, v, false, why));
			}
			// ---- hold just outside their reach and punish
			if (dist <= p.range() + 2.5) {
				double v = 0.25 + 0.45 * p.patience() + 0.25 * p.defensive() + 0.3 * (1 - ready) - 0.35 * p.aggression() - noBlade;
				// always a reason: recharging out of their reach, or holding the range it likes
				List<String> why = new ArrayList<>(List.of(ready < 0.9 ? String.format(java.util.Locale.ROOT, "recharging (%.0f%%)", ready * 100)
						: String.format(java.util.Locale.ROOT, "holding %.1f blocks", p.range())));
				if (om.known() && om.aggression() > 0.5) {
					v += 0.3 * om.aggression() * p.patience();
					why.add("they rush in");
				}
				if (om.timed()) {
					v += 0.05 + 0.15 * p.patience() * (0.5 + p.adaptability());
					why.add("they time their hits");
				}
				if (om.swings() >= 6 && om.accuracy() > 0.75) {
					v += 0.1 * (0.5 + p.adaptability());
					why.add("they rarely miss");
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
				if (carrying) {
					v -= 0.4;
					why.add("carrying " + g.what());
				}
				out.add(new Score(Tactic.SPACE, v, false, why));
			}
		} else if (t != null && t.confidence() > 0.15) {
			List<String> why = new ArrayList<>(List.of(String.format(java.util.Locale.ROOT, "last seen, %.0f%% sure", t.confidence() * 100)));
			double v = 0.3 + 0.4 * t.confidence() * (0.5 + 0.5 * p.aggression());
			v -= leashPenalty(g, carrying, leashed, why);
			out.add(new Score(Tactic.SEARCH, v, false, why));
		}

		if (g != null) out.add(objective(s, g, p, seen, dist, reach));
		out.add(new Score(Tactic.IDLE, 0.05, false, List.of()));
		return out;
	}

	/** Going after someone costs more while carrying a flag, or when they are far from the hill or the post. */
	private static double leashPenalty(Objective g, boolean carrying, boolean leashed, List<String> why) {
		double v = 0;
		if (carrying) {
			v += 0.6;
			why.add("carrying " + g.what());
		}
		if (leashed) {
			v += 0.5;
			why.add("too far from " + g.what());
		}
		return v;
	}

	/** How much the game's objective counts now, by its kind. */
	static Score objective(Situation s, Objective g, Personality p, boolean seen, double dist, double reach) {
		Situation.Target t = s.target();
		boolean nobody = t == null || (!seen && t.confidence() < 0.15);
		List<String> why = new ArrayList<>();
		double v;
		switch (g.kind()) {
			case CARRY -> {
				v = 1.0 + 0.3 * p.objective();
				why.add("carrying " + g.what() + " home");
				if (seen && dist < reach) {
					v -= 0.35;
					why.add("someone in the way");
				}
			}
			case HOLD -> {
				if (!g.contains(s.self().pos())) {
					v = 0.75 + 0.5 * p.objective() + (nobody ? 0.2 : 0);
					why.add("off " + g.what());
				} else {
					v = 0.35 + 0.3 * p.objective() + (nobody || g.beyondLeash(t.pos()) ? 0.35 : 0);
					why.add("on " + g.what());
				}
			}
			case DEFEND -> {
				double away = s.self().pos().flatDistance(g.point());
				boolean threat = t != null && !g.beyondLeash(t.pos()) && (seen || t.confidence() > 0.3);
				if (threat) {
					v = 0.1 + (away > g.leash() ? 0.5 : 0);
					why.add("someone near " + g.what());
				} else if (away > g.radius()) {
					v = 0.6 + 0.4 * p.objective();
					why.add("back to " + g.what());
				} else {
					v = 0.5 + 0.3 * p.objective();
					why.add("guarding " + g.what());
				}
			}
			default -> {
				v = 0.25 + 0.55 * p.objective() + (nobody ? 0.4 : dist > 12 ? 0.25 : -0.2);
				why.add("going for " + g.what());
				// nearly there (an enemy bed in reach, their goal a step away): finish it before anything else
				double there = s.self().pos().flatDistance(g.point());
				if (there < 6) {
					v += 0.2 + 0.6 * (1 - there / 6);
					why.add("nearly at " + g.what());
				}
			}
		}
		return new Score(Tactic.OBJECTIVE, v, false, why);
	}
}
