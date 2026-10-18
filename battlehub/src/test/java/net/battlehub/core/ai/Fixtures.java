package net.battlehub.core.ai;

import java.util.Arrays;
import java.util.UUID;

/** Small builders for situations in the tests. */
final class Fixtures {
	private Fixtures() {}

	static final UUID FOE = UUID.nameUUIDFromBytes("foe".getBytes());

	static Situation.Self me(V3 pos, double health, double ready) {
		return new Situation.Self(pos, V3.ZERO, health, 20, true, ready, false, false, 3.0);
	}

	static Situation.Self meWith(V3 pos, double health, double ready, boolean canHeal, boolean shield) {
		return new Situation.Self(pos, V3.ZERO, health, 20, true, ready, canHeal, shield, 3.0);
	}

	static Situation.Target foe(V3 pos, double health) {
		return new Situation.Target(FOE, pos, V3.ZERO, health, 20, true, 1, 100, true);
	}

	static double[] open() {
		double[] e = new double[8];
		Arrays.fill(e, Situation.SAFE);
		return e;
	}

	/** Ground everywhere except a drop {@code dist} blocks away in direction {@code dir}. */
	static double[] dropAt(V3 dir, double dist) {
		double[] e = open();
		e[Situation.direction(dir)] = dist;
		return e;
	}

	static Situation at(int tick, Situation.Self me, Situation.Target t, double[] edge) {
		return new Situation(tick, me, t, null, edge, Situation.Threat.NONE);
	}

	static Tactic best(java.util.List<Score> scores) {
		return scores.stream().max(java.util.Comparator.comparingDouble(Score::value)).orElseThrow().tactic();
	}

	static double value(java.util.List<Score> scores, Tactic t) {
		return scores.stream().filter(s -> s.tactic() == t).mapToDouble(Score::value).findFirst().orElse(Double.NaN);
	}
}
