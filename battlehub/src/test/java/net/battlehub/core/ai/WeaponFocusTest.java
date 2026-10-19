package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.best;
import static net.battlehub.core.ai.Fixtures.foe;
import static net.battlehub.core.ai.Fixtures.open;
import static net.battlehub.core.ai.Fixtures.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The weapon a fight is about: the bow in a bow fight, the mace (and its smash) in a mace fight. */
class WeaponFocusTest {
	private static final V3 O = new V3(0, 64, 0);

	private static Situation.Self me(Weapon focus, boolean bow, boolean launch) {
		return new Situation.Self(O, V3.ZERO, 20, 20, true, 1, false, false, 3.0, true, bow, 0, focus, launch);
	}

	private static List<Score> score(Situation.Self me, double dist, Archetype a) {
		Situation s = new Situation(0, me, foe(new V3(dist, 64, 0), 20), null, open(), Situation.Threat.NONE);
		return CombatPolicy.score(s, new OpponentModel(), new OutcomeMemory(), a.base, Difficulty.EXPERT);
	}

	@Test
	void inABowFightTheBowIsTheWeapon() {
		for (Archetype a : new Archetype[] {Archetype.BALANCED, Archetype.RUSHER, Archetype.DUELIST}) {
			// five blocks away with a bow and a sword in the hotbar: a bow fight shoots
			assertEquals(Tactic.SHOOT, best(score(me(Weapon.BOW, true, false), 5, a)), a.id());
			// in a mixed kit the bow counts for much less at that distance
			assertTrue(value(score(me(Weapon.BOW, true, false), 5, a), Tactic.SHOOT) > value(score(me(Weapon.ANY, true, false), 5, a), Tactic.SHOOT) + 0.5);
		}
		// right on top of them the sword still comes out
		assertTrue(best(score(me(Weapon.BOW, true, false), 2.2, Archetype.BALANCED)) != Tactic.SHOOT);
	}

	@Test
	void inAMaceFightItFliesUpOnAWindChargeNowAndThen() {
		Situation near = new Situation(0, me(Weapon.MACE, false, true), foe(new V3(3.5, 64, 0), 20), null, open(), Situation.Threat.NONE);
		CombatSteering st = new CombatSteering();
		Random r = new Random(4);
		int launches = 0, last = -1000, minGap = Integer.MAX_VALUE;
		for (int t = 0; t < 2000; t++) {
			Situation s = new Situation(t, near.self(), near.target(), null, open(), Situation.Threat.NONE);
			if (st.launch(Tactic.STRIKE, s, Difficulty.EXPERT, r)) {
				launches++;
				minGap = Math.min(minGap, t - last);
				last = t;
				// the steering remembers the launch through steer(); here the gap rule is checked by hand
				st = new CombatSteering();
				for (int k = 0; k < 59; k++) st.launch(Tactic.STRIKE, s, Difficulty.EXPERT, r);
			}
		}
		assertTrue(launches > 3, launches + " launches");
		// not near an edge, not without a wind charge, not in a sword fight, not from far away
		CombatSteering fresh = new CombatSteering();
		for (int t = 0; t < 2000; t++) {
			assertTrue(!fresh.launch(Tactic.STRIKE, new Situation(t, me(Weapon.MACE, false, true), foe(new V3(3.5, 64, 0), 20), null,
					Fixtures.dropAt(new V3(-1, 0, 0), 1.0), Situation.Threat.NONE), Difficulty.EXPERT, r));
			assertTrue(!fresh.launch(Tactic.STRIKE, new Situation(t, me(Weapon.MACE, false, false), foe(new V3(3.5, 64, 0), 20), null, open(),
					Situation.Threat.NONE), Difficulty.EXPERT, r));
			assertTrue(!fresh.launch(Tactic.STRIKE, new Situation(t, me(Weapon.SWORD, false, true), foe(new V3(3.5, 64, 0), 20), null, open(),
					Situation.Threat.NONE), Difficulty.EXPERT, r));
			assertTrue(!fresh.launch(Tactic.STRIKE, new Situation(t, me(Weapon.MACE, false, true), foe(new V3(12, 64, 0), 20), null, open(),
					Situation.Threat.NONE), Difficulty.EXPERT, r));
		}
	}

	@Test
	void launchesAreSpacedOut() {
		CombatSteering st = new CombatSteering();
		Random r = new Random(8);
		int last = -1000, launches = 0;
		for (int t = 0; t < 3000; t++) {
			Situation s = new Situation(t, me(Weapon.MACE, false, true), foe(new V3(3.5, 64, 0), 20), null, open(), Situation.Threat.NONE);
			Intent in = st.steer(Tactic.STRIKE, s, null, Archetype.BALANCED.base, Difficulty.EXPERT, r);
			if (in.launch()) {
				assertTrue(t - last >= 60, "two launches " + (t - last) + " ticks apart");
				last = t;
				launches++;
			}
		}
		assertTrue(launches >= 5, launches + " launches");
	}

	@Test
	void theKitNamesTheWeapon() {
		assertEquals(Weapon.MACE, Weapon.ofKit("mace"));
		assertEquals(Weapon.BOW, Weapon.ofKit("Bow"));
		assertEquals(Weapon.ANY, Weapon.ofKit("ffa"));
		assertEquals(Weapon.ANY, Weapon.ofKit(null));
		assertTrue(Weapon.CROSSBOW.ranged() && !Weapon.MACE.ranged());
	}
}
