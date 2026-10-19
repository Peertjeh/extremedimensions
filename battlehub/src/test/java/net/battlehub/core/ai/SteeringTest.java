package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.at;
import static net.battlehub.core.ai.Fixtures.foe;
import static net.battlehub.core.ai.Fixtures.me;
import static net.battlehub.core.ai.Fixtures.open;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class SteeringTest {
	private static final V3 O = new V3(0, 64, 0);

	@Test
	void itOnlySwingsInReachInSightAndCharged() {
		Personality p = Archetype.BALANCED.base;
		Random r = new Random(1);
		int hits = 0;
		for (int i = 0; i < 200; i++) {
			CombatSteering st = new CombatSteering();
			if (st.steer(Tactic.STRIKE, at(0, me(O, 20, 1), foe(new V3(2.8, 64, 0), 20), open()), null, p, Difficulty.EXPERT, r).attack()) hits++;
		}
		// charged and in reach: the finger presses soon, but not on every single tick
		assertTrue(hits > 120 && hits < 200, hits + " of 200");
		for (int i = 0; i < 200; i++) {
			CombatSteering st = new CombatSteering();
			assertFalse(st.steer(Tactic.STRIKE, at(0, me(O, 20, 0.2), foe(new V3(2.8, 64, 0), 20), open()), null, p, Difficulty.EXPERT, r).attack(),
					"not charged");
			assertFalse(st.steer(Tactic.STRIKE, at(0, me(O, 20, 1), foe(new V3(3.6, 64, 0), 20), open()), null, p, Difficulty.EXPERT, r).attack(),
					"out of reach");
			Situation.Target hidden = new Situation.Target(Fixtures.FOE, new V3(2.5, 64, 0), V3.ZERO, 20, 20, false, 0.9, 100, false);
			assertFalse(st.steer(Tactic.STRIKE, at(0, me(O, 20, 1), hidden, open()), null, p, Difficulty.EXPERT, r).attack(), "not in sight");
		}
	}

	@Test
	void itNeverStrafesTowardsAnEdge() {
		Random r = new Random(5);
		Personality p = Archetype.EVASIVE.base;
		// foe east (+x); our left is north (-z) when facing east? left() of (1,0,0) is (0,0,-1)
		double[] edge = Fixtures.dropAt(new V3(0, 0, -1), 0.8);
		for (int i = 0; i < 500; i++) {
			CombatSteering st = new CombatSteering();
			Intent in = st.steer(Tactic.STRIKE, at(i, me(O, 20, 0.5), foe(new V3(2.5, 64, 0), 20), edge), null, p, Difficulty.MASTER, r);
			assertTrue(in.strafe() <= 0, "strafed left into the drop: " + in.strafe());
		}
	}

	@Test
	void itBacksOffAwayFromTheTargetOrSidewaysWhenThereIsNoRoom() {
		CombatSteering st = new CombatSteering();
		Random r = new Random(1);
		Intent back = st.steer(Tactic.RETREAT, at(0, me(O, 5, 0.3), foe(new V3(2.5, 64, 0), 20), open()), null, Archetype.EVASIVE.base,
				Difficulty.EXPERT, r);
		assertEquals(-1, back.forward(), 1e-9);
		Intent side = st.steer(Tactic.RETREAT, at(0, me(O, 5, 0.3), foe(new V3(2.5, 64, 0), 20), Fixtures.dropAt(new V3(-1, 0, 0), 0.6)), null,
				Archetype.EVASIVE.base, Difficulty.EXPERT, r);
		assertEquals(0, side.forward(), 1e-9);
		assertTrue(side.strafe() != 0);
	}

	@Test
	void itLeadsAMovingTargetAsFarAsItsLevelAllows() {
		Track t = new Track();
		for (int i = 0; i <= 10; i++) t.observe(i, new V3(5 + i * 0.25, 64, 0));
		Situation s = at(10, me(O, 20, 1), foe(new V3(7.5, 64, 0), 20), open());
		V3 expert = CombatSteering.aimPoint(s, t, Difficulty.EXPERT);
		V3 beginner = CombatSteering.aimPoint(s, t, Difficulty.BEGINNER);
		assertEquals(7.5, beginner.x(), 1e-9, "no prediction");
		assertEquals(7.5 + 0.75, expert.x(), 1e-9, "three ticks ahead");
		Intent approach = new CombatSteering().steer(Tactic.APPROACH, s, t, Archetype.RUSHER.base, Difficulty.EXPERT, new Random(1));
		assertNotNull(approach.moveTo());
		assertTrue(approach.moveTo().x() > 7.5, "runs to where they will be");
	}
}
