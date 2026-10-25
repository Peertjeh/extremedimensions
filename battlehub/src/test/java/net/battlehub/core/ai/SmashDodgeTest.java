package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.best;
import static net.battlehub.core.ai.Fixtures.open;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** A bot sees a mace smash coming down on it and gets out from under it (1.49). */
class SmashDodgeTest {
	static final V3 ME = new V3(0, 64, 0);

	static Situation.Self self(boolean shield) {
		return new Situation.Self(ME, V3.ZERO, 20, 20, true, 1, false, shield, 3.0, true, false, 0, Weapon.MACE, true, 0);
	}

	static Situation with(Situation.Self me, Situation.Target t, double[] edges, MaceFight.Smash smash, V3 attacker) {
		return new Situation(100, me, t, null, edges, new Situation.Threat(0, V3.ZERO, smash, attacker));
	}

	/** Where the keys take the bot in the world (forward along where it looks, strafe to its left). */
	static V3 walked(Intent in, V3 look) {
		return look.scale(in.forward()).add(look.left().scale(in.strafe())).flat();
	}

	@Test
	void someoneFallingOntoItIsSeenComing() {
		// right above, on the way down with a fall behind them: lands on its head in a few ticks
		MaceFight.Smash s = MaceFight.incoming(ME, new V3(0.4, 72, 0.2), new V3(0, -0.6, 0), 5, MaceFight.SMASH_HORIZON);
		assertNotNull(s);
		assertTrue(s.in() >= 1 && s.in() <= 12, "in " + s.in());
		assertTrue(s.at().flatDistance(ME) < 1, "at " + s.at());
		// a wind charge that just went off beside it: up first, then down on it, still within the horizon
		MaceFight.Smash launched = MaceFight.incoming(ME, new V3(1.5, 65.2, 0), new V3(-0.05, 1.2, 0), 0, MaceFight.SMASH_HORIZON);
		assertNotNull(launched, "the launch is seen coming");
		assertTrue(launched.in() > 12, "most of it is the way up: " + launched.in());
	}

	@Test
	void aJumpOrSomeoneFarAwayIsNoSmash() {
		// an ordinary jump next to it: not enough fall for the smash
		assertNull(MaceFight.incoming(ME, new V3(1, 65.1, 0), new V3(0, 0.3, 0), 0, MaceFight.SMASH_HORIZON));
		// on the ground: no flight at all
		assertNull(MaceFight.incoming(ME, new V3(1, 64, 0), new V3(0.2, 0, 0), 0, MaceFight.SMASH_HORIZON));
		// coming down far to the side
		assertNull(MaceFight.incoming(ME, new V3(8, 72, 0), new V3(0.1, -0.6, 0), 5, MaceFight.SMASH_HORIZON));
		// flying off away from it
		assertNull(MaceFight.incoming(ME, new V3(2, 70, 0), new V3(0.9, -0.2, 0), 3, MaceFight.SMASH_HORIZON));
		// too high to come down within the horizon
		assertNull(MaceFight.incoming(ME, new V3(0, 120, 0), new V3(0, -0.1, 0), 2, MaceFight.SMASH_HORIZON));
	}

	@Test
	void itRunsAwayFromWhereItComesDown() {
		MaceFight.Smash s = new MaceFight.Smash(new V3(1, 64, 0), 6);
		V3 away = MaceFight.away(ME, s, new V3(1, 70, 0), new V3(0, 0, 1));
		assertTrue(away.x() < -0.99, "away from where it lands: " + away);
		// right under it: away from where the attacker is now
		MaceFight.Smash over = new MaceFight.Smash(new V3(0.1, 64, 0.1), 6);
		V3 fromThem = MaceFight.away(ME, over, new V3(0, 70, 3), new V3(1, 0, 0));
		assertTrue(fromThem.z() < -0.99, "away from them: " + fromThem);
		// right under them as well: along the fallback, never nothing
		V3 along = MaceFight.away(ME, over, new V3(0, 70, 0), new V3(1, 0, 0));
		assertEquals(1, along.length(), 1e-9);
	}

	@Test
	void aBeginnerOftenMissesItAMasterNearlyNever() {
		int beginner = 0, master = 0;
		for (int i = 0; i < 1000; i++) {
			double roll = i / 1000.0;
			if (MaceFight.reacts(Difficulty.BEGINNER, 40, roll)) beginner++;
			if (MaceFight.reacts(Difficulty.MASTER, 40, roll)) master++;
		}
		assertTrue(beginner < 300, "beginner noticed " + beginner);
		assertTrue(master > 950, "master noticed " + master);
		// and nobody reacts before their reaction time
		assertFalse(MaceFight.reacts(Difficulty.MASTER, Difficulty.MASTER.reactionTicks - 1, 0));
		assertTrue(MaceFight.reacts(Difficulty.MASTER, Difficulty.MASTER.reactionTicks, 0));
		assertFalse(MaceFight.reacts(Difficulty.BEGINNER, Difficulty.MASTER.reactionTicks, 0));
		// the levels in order
		double last = -1;
		for (Difficulty d : Difficulty.values()) {
			int n = 0;
			for (int i = 0; i < 1000; i++) if (MaceFight.reacts(d, 40, i / 1000.0)) n++;
			assertTrue(n >= last, d + " notices less than the level below");
			last = n;
		}
	}

	@Test
	void nothingGoesBeforeGettingOutFromUnder() {
		V3 attacker = new V3(0.5, 71, 0);
		Situation.Target them = new Situation.Target(Fixtures.FOE, attacker, new V3(0, -0.7, 0), 20, 20, true, 1, 100, false);
		MaceFight.Smash s = MaceFight.incoming(ME, attacker, new V3(0, -0.7, 0), 4, MaceFight.SMASH_HORIZON);
		assertNotNull(s);
		Situation sit = with(self(false), them, open(), s, attacker);
		assertEquals(Tactic.EVADE, best(CombatPolicy.score(sit, new OpponentModel(), new OutcomeMemory(), Archetype.BALANCED.base, Difficulty.EXPERT)));
		// the keys take it away from where the smash lands, eyes still on them
		CombatSteering st = new CombatSteering();
		Intent in = st.steer(Tactic.EVADE, sit, null, Archetype.BALANCED.base, Difficulty.EXPERT, new Random(1));
		V3 look = attacker.sub(ME).flat().unit();
		V3 went = walked(in, look);
		V3 away = MaceFight.away(ME, s, attacker, look.left());
		assertTrue(went.unit().dot(away) > 0.7, "went " + went + ", away is " + away);
		assertNotNull(in.look());
	}

	@Test
	void notOverAnEdgeToGetAway() {
		V3 attacker = new V3(1.5, 71, 0);
		Situation.Target them = new Situation.Target(Fixtures.FOE, attacker, V3.ZERO, 20, 20, true, 1, 100, false);
		MaceFight.Smash s = new MaceFight.Smash(new V3(1, 64, 0), 8);
		// away would be -x, where the ground ends at once
		Situation sit = with(self(false), them, Fixtures.dropAt(new V3(-1, 0, 0), 0.5), s, attacker);
		Intent in = new CombatSteering().steer(Tactic.EVADE, sit, null, Archetype.BALANCED.base, Difficulty.EXPERT, new Random(2));
		V3 went = walked(in, attacker.sub(ME).flat().unit()).unit();
		assertTrue(went.x() > -0.3, "not off the edge: " + went);
		assertTrue(Math.abs(went.z()) > 0.7, "sideways instead: " + went);
	}

	@Test
	void withAShieldAnExpertRaisesItAtTheLastMoment() {
		V3 attacker = new V3(0.3, 67, 0);
		Situation.Target them = new Situation.Target(Fixtures.FOE, attacker, new V3(0, -1, 0), 20, 20, true, 1, 100, false);
		CombatSteering st = new CombatSteering();
		Random r = new Random(5);
		int blocked = 0;
		for (int i = 0; i < 100; i++) {
			if (st.steer(Tactic.EVADE, with(self(true), them, open(), new MaceFight.Smash(new V3(0.3, 64, 0), 3), attacker), null, Archetype.BALANCED.base,
					Difficulty.EXPERT, r).block()) blocked++;
		}
		// as often as its shield skill says (an expert: 0.3 + 0.7 x 0.6 of the time)
		double expected = 100 * (0.3 + 0.7 * Difficulty.EXPERT.shieldSkill);
		assertTrue(Math.abs(blocked - expected) < 15, "blocked " + blocked + ", expected about " + expected);
		// long before it lands it runs instead
		assertFalse(st.steer(Tactic.EVADE, with(self(true), them, open(), new MaceFight.Smash(new V3(0.3, 64, 0), 15), attacker), null,
				Archetype.BALANCED.base, Difficulty.EXPERT, r).block());
	}

	@Test
	void aSmashFromSomeoneItWasNotFightingIsDodgedToo() {
		V3 attacker = new V3(0.6, 70, 0.6);
		MaceFight.Smash s = new MaceFight.Smash(new V3(0.6, 64, 0.6), 7);
		Intent in = new CombatSteering().steer(Tactic.EVADE, with(self(false), null, open(), s, attacker), null, Archetype.BALANCED.base,
				Difficulty.INTERMEDIATE, new Random(3));
		assertTrue(Math.abs(in.forward()) + Math.abs(in.strafe()) > 0.5, "it moves");
		assertNotNull(in.look(), "eyes on the one coming down");
		assertFalse(in.attack());
	}
}
