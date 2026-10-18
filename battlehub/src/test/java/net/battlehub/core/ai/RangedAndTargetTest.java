package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.best;
import static net.battlehub.core.ai.Fixtures.foe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.battlehub.core.BotLevel;

class RangedAndTargetTest {
	private static final V3 O = new V3(0, 64, 0);

	private static Situation.Self archer(boolean sword, int draw) {
		return new Situation.Self(O, V3.ZERO, 20, 20, true, 1, false, false, 3.0, sword, true, draw);
	}

	private static List<Score> score(Situation.Self me, double dist, Archetype a) {
		Situation s = Fixtures.at(0, me, foe(new V3(dist, 64, 0), 20), Fixtures.open());
		return CombatPolicy.score(s, new OpponentModel(), new OutcomeMemory(), a.base, Difficulty.EXPERT);
	}

	@Test
	void aBowOnlyFighterShootsFromAfarAndBacksOffUpClose() {
		assertEquals(Tactic.SHOOT, best(score(archer(false, 0), 15, Archetype.BALANCED)));
		// up close it keeps the bow but backs away while drawing (kiting)
		Tactic close = best(score(archer(false, 0), 4, Archetype.BALANCED));
		assertTrue(close == Tactic.SHOOT || close == Tactic.RETREAT, close.toString());
		Situation near = Fixtures.at(0, archer(false, 0), foe(new V3(4, 64, 0), 20), Fixtures.open());
		Intent kite = new CombatSteering().steer(close, near, null, Archetype.BALANCED.base, Difficulty.EXPERT, new Random(2));
		assertTrue(kite.forward() < 0, "moves away: " + kite.forward());
		// no blade: melee scores are low
		assertTrue(Fixtures.value(score(archer(false, 0), 2.5, Archetype.RUSHER), Tactic.STRIKE) < 0.9);
	}

	@Test
	void withASwordTooItShootsAtRangeAndFightsUpClose() {
		assertEquals(Tactic.SHOOT, best(score(archer(true, 0), 18, Archetype.ARCHER)));
		Tactic close = best(score(archer(true, 0), 2.6, Archetype.BALANCED));
		assertEquals(Tactic.STRIKE, close);
		// a rusher with a bow still prefers to close in from medium range
		assertEquals(Tactic.APPROACH, best(score(archer(true, 0), 7, Archetype.RUSHER)));
	}

	@Test
	void shootingLeadsByTheArrowsFlightAsFarAsTheLevelCan() {
		Track t = new Track();
		for (int i = 0; i <= 10; i++) t.observe(i, new V3(20, 64, i * 0.25));
		Situation s = Fixtures.at(10, archer(false, 0), foe(new V3(20, 64, 2.5), 20), Fixtures.open());
		V3 none = CombatSteering.arrowAim(s, t, Difficulty.BEGINNER);
		V3 master = CombatSteering.arrowAim(s, t, Difficulty.MASTER);
		assertEquals(2.5, none.z(), 1e-9);
		int flight = net.battlehub.core.BotMath.arrowTicks(20, CombatSteering.ARROW_SPEED);
		assertEquals(2.5 + 0.25 * Math.min(flight, 22), master.z(), 1e-6);
		Intent in = new CombatSteering().steer(Tactic.SHOOT, s, t, Archetype.ARCHER.base, Difficulty.MASTER, new Random(1));
		assertTrue(in.bow());
		assertFalse(in.attack());
	}

	@Test
	void itFightsBackAgainstWhoeverHitItAndFinishesWeakTargets() {
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		Personality p = Archetype.BALANCED.base;
		// b is a bit further but just hit us
		Targeting.Candidate near = new Targeting.Candidate(a, 6, true, true, 1, 1, 1000, false);
		Targeting.Candidate attacker = new Targeting.Candidate(b, 9, true, true, 1, 1, 5, false);
		assertEquals(b, Targeting.choose(List.of(near, attacker), p).id());
		// a weak target draws an aggressive fighter
		Targeting.Candidate weak = new Targeting.Candidate(b, 9, true, true, 1, 0.1, 1000, false);
		assertEquals(b, Targeting.choose(List.of(near, weak), Archetype.RUSHER.base).id());
		// an unseen one loses to a seen one at the same distance; the current target is kept when it is close
		Targeting.Candidate hidden = new Targeting.Candidate(b, 5, false, false, 0.6, 1, 1000, false);
		assertEquals(a, Targeting.choose(List.of(near, hidden), p).id());
		Targeting.Candidate current = new Targeting.Candidate(b, 7.5, true, true, 1, 1, 1000, true);
		assertEquals(b, Targeting.choose(List.of(near, current), p).id());
		assertEquals(null, Targeting.choose(List.of(), p));
	}

	@Test
	void theDebriefTellsWhatHappenedAndGivesOneTip() {
		OpponentModel m = new OpponentModel();
		for (int i = 0; i < 40; i++) m.observe(2.0, 0, 0, false);
		List<Debrief.Line> lines = Debrief.lines("Bot_Nova", "counter", "expert", m, 18, 6, Map.of(Tactic.SPACE, 0.6, Tactic.STRIKE, 0.3, Tactic.IDLE, 0.1));
		assertEquals("debrief.header", lines.get(0).key());
		assertEquals("debrief.damage", lines.get(1).key());
		assertEquals(6L, lines.get(1).args()[0]);
		assertEquals("debrief.bot_plan", lines.get(2).key());
		assertEquals("debrief.tactic.space", lines.get(2).args()[0]);
		assertTrue(lines.get(lines.size() - 1).key().startsWith("debrief.tip."));
		// a player who never changes strafe direction and lost is told to strafe
		assertEquals("debrief.tip.strafe", Debrief.tip("balanced", m, 18, 6));
		// a winner is pointed to the next level
		OpponentModel moving = new OpponentModel();
		for (int i = 0; i < 40; i++) moving.observe(3.5, 0.02, 2, false);
		assertEquals("debrief.tip.next_level", Debrief.tip("balanced", moving, 4, 20));
	}

	@Test
	void sixLevelsMapOntoTheOldThree() {
		assertEquals(BotLevel.EASY, Difficulty.BEGINNER.legacy());
		assertEquals(BotLevel.NORMAL, Difficulty.INTERMEDIATE.legacy());
		assertEquals(BotLevel.HARD, Difficulty.MASTER.legacy());
		for (BotLevel l : BotLevel.values()) assertEquals(l, Difficulty.of(l).legacy());
	}
}
