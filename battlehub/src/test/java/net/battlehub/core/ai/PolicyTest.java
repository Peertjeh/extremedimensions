package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.at;
import static net.battlehub.core.ai.Fixtures.best;
import static net.battlehub.core.ai.Fixtures.foe;
import static net.battlehub.core.ai.Fixtures.me;
import static net.battlehub.core.ai.Fixtures.meWith;
import static net.battlehub.core.ai.Fixtures.open;
import static net.battlehub.core.ai.Fixtures.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolicyTest {
	private static final V3 O = new V3(0, 64, 0);
	private final OpponentModel calm = new OpponentModel();
	private final OutcomeMemory fresh = new OutcomeMemory();

	private List<Score> score(Situation s, Archetype a) {
		return CombatPolicy.score(s, calm, fresh, a.base, Difficulty.EXPERT);
	}

	@Test
	void farAwayARusherClosesIn() {
		Situation s = at(0, me(O, 20, 1), foe(new V3(10, 64, 0), 20), open());
		assertEquals(Tactic.APPROACH, best(score(s, Archetype.RUSHER)));
	}

	@Test
	void inReachWithAChargedAttackEveryoneButTheCounterStrikes() {
		Situation s = at(0, me(O, 20, 1), foe(new V3(2.6, 64, 0), 20), open());
		assertEquals(Tactic.STRIKE, best(score(s, Archetype.RUSHER)));
		assertEquals(Tactic.STRIKE, best(score(s, Archetype.BALANCED)));
	}

	@Test
	void aCounterHoldsDistanceWhileItsAttackRecharges() {
		Situation s = at(0, me(O, 20, 0.2), foe(new V3(3.4, 64, 0), 20), open());
		assertEquals(Tactic.SPACE, best(score(s, Archetype.COUNTER)));
		// the same moment, a rusher keeps pressing
		Tactic r = best(score(s, Archetype.RUSHER));
		assertTrue(r == Tactic.STRIKE || r == Tactic.APPROACH, r.toString());
	}

	@Test
	void evenACounterGoesInWhenNothingHappens() {
		// a charged attack at the edge of reach against an opponent who just stands there
		Situation s = at(0, me(O, 20, 1), foe(new V3(3.6, 64, 0), 20), open());
		List<Score> fresh = CombatPolicy.score(s, calm, this.fresh, Archetype.COUNTER.base, Difficulty.EXPERT, 0);
		List<Score> later = CombatPolicy.score(s, calm, this.fresh, Archetype.COUNTER.base, Difficulty.EXPERT, 200);
		assertEquals(Tactic.SPACE, best(fresh));
		Tactic t = best(later);
		assertTrue(t == Tactic.STRIKE || t == Tactic.APPROACH, t.toString());
		// a patient style waits longer than an impatient one
		double counterGain = value(later, Tactic.STRIKE) - value(fresh, Tactic.STRIKE);
		double rusherGain = value(CombatPolicy.score(s, calm, this.fresh, Archetype.RUSHER.base, Difficulty.EXPERT, 200), Tactic.STRIKE)
				- value(CombatPolicy.score(s, calm, this.fresh, Archetype.RUSHER.base, Difficulty.EXPERT, 0), Tactic.STRIKE);
		assertTrue(rusherGain > counterGain, rusherGain + " vs " + counterGain);
	}

	@Test
	void lowOnHealthItHealsWhenItCanOrBacksOff() {
		Situation canHeal = at(0, meWith(O, 5, 0.5, true, false), foe(new V3(6, 64, 0), 18), open());
		assertEquals(Tactic.HEAL, best(score(canHeal, Archetype.BALANCED)));
		Situation cannot = at(0, me(O, 4, 0.5), foe(new V3(4, 64, 0), 18), open());
		assertEquals(Tactic.RETREAT, best(score(cannot, Archetype.EVASIVE)));
		// a reckless rusher does not run
		assertTrue(value(score(cannot, Archetype.RUSHER), Tactic.RETREAT) < value(score(cannot, Archetype.EVASIVE), Tactic.RETREAT));
	}

	@Test
	void anEdgeBehindIsAnEmergency() {
		// the foe is east; a hit pushes west, where the ground ends after half a block
		Situation s = at(0, me(O, 20, 1), foe(new V3(2.5, 64, 0), 20), Fixtures.dropAt(new V3(-1, 0, 0), 0.5));
		List<Score> sc = score(s, Archetype.RUSHER);
		assertEquals(Tactic.RECOVER, best(sc));
		assertTrue(sc.stream().anyMatch(x -> x.tactic() == Tactic.RECOVER && x.critical()));
		// ground behind: no emergency
		Situation safe = at(0, me(O, 20, 1), foe(new V3(2.5, 64, 0), 20), Fixtures.dropAt(new V3(1, 0, 0), 0.5));
		assertFalse(score(safe, Archetype.RUSHER).stream().anyMatch(x -> x.tactic() == Tactic.RECOVER && x.critical()));
	}

	@Test
	void anUnseenTargetIsSearchedWhereItWasLastSeen() {
		Situation.Target lost = new Situation.Target(Fixtures.FOE, new V3(12, 64, 3), V3.ZERO, 20, 20, false, 0.7, 100, false);
		List<Score> sc = score(at(0, me(O, 20, 1), lost, open()), Archetype.BALANCED);
		assertEquals(Tactic.SEARCH, best(sc));
		assertTrue(sc.stream().noneMatch(x -> x.tactic() == Tactic.STRIKE || x.tactic() == Tactic.APPROACH), "no fighting what it cannot see");
	}

	@Test
	void punishingAMissedSwing() {
		Situation.Target swung = new Situation.Target(Fixtures.FOE, new V3(2.8, 64, 0), V3.ZERO, 20, 20, true, 1, 2, true);
		double after = value(score(at(0, me(O, 20, 1), swung, open()), Archetype.OPPORTUNIST), Tactic.STRIKE);
		double before = value(score(at(0, me(O, 20, 1), foe(new V3(2.8, 64, 0), 20), open()), Archetype.OPPORTUNIST), Tactic.STRIKE);
		assertTrue(after > before + 0.1, before + " -> " + after);
	}

	@Test
	void anAdaptableMindDropsATacticThatKeepsFailing() {
		OutcomeMemory bad = new OutcomeMemory();
		for (int i = 0; i < 6; i++) bad.record(Tactic.STRIKE, 0, 6);
		Situation s = at(0, me(O, 20, 0.95), foe(new V3(2.9, 64, 0), 20), open());
		double normal = value(CombatPolicy.score(s, calm, fresh, Archetype.OPPORTUNIST.base, Difficulty.EXPERT), Tactic.STRIKE);
		double learned = value(CombatPolicy.score(s, calm, bad, Archetype.OPPORTUNIST.base, Difficulty.EXPERT), Tactic.STRIKE);
		assertTrue(learned < normal - 0.15, normal + " -> " + learned);
	}

	@Test
	void theObjectiveWinsWhenNobodyIsAround() {
		Situation s = new Situation(0, me(O, 20, 1), null, Objective.attack(new V3(20, 64, 0), "the goal"), open(), Situation.Threat.NONE);
		assertEquals(Tactic.OBJECTIVE, best(score(s, Archetype.BALANCED)));
	}

	@Test
	void everyScoreExplainsItself() {
		Situation s = at(0, me(O, 12, 0.5), foe(new V3(3, 64, 0), 15), open());
		for (Score x : score(s, Archetype.DUELIST)) {
			if (x.tactic() != Tactic.IDLE) assertFalse(x.why().isEmpty() && x.tactic() != Tactic.SPACE, x.toString());
		}
	}
}
