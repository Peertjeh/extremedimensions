package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.best;
import static net.battlehub.core.ai.Fixtures.foe;
import static net.battlehub.core.ai.Fixtures.open;
import static net.battlehub.core.ai.Fixtures.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
				assertTrue(t - last >= CombatSteering.LAUNCH_GAP, "two launches " + (t - last) + " ticks apart");
				last = t;
				launches++;
			}
		}
		assertTrue(launches >= 5, launches + " launches");
	}

	/** In the air: at (x, y, 0), moving (vx, vy, 0), {@code fall} fallen so far, with the mace kit. */
	private static Situation.Self air(double x, double y, double vx, double vy, double fall) {
		return new Situation.Self(new V3(x, y, 0), new V3(vx, vy, 0), 20, 20, false, 1, false, false, 3.0, true, false, 0, Weapon.MACE, true, fall);
	}

	@Test
	void theSwordOnTheGroundTheMaceInTheAir() {
		// on the ground: the sword, also right after a launch (the jump comes the tick after the throw)
		assertFalse(MaceFight.maceInHand(true, 0, 0));
		assertFalse(MaceFight.maceInHand(true, 0, 1000));
		// up in the air after a launch: the mace, from the first tick on
		assertTrue(MaceFight.maceInHand(false, 0, 2));
		assertTrue(MaceFight.maceInHand(false, 0, MaceFight.FLIGHT));
		// an ordinary jump falls 1.25 at most and a knock little more: the sword stays (switching costs its charge)
		assertFalse(MaceFight.maceInHand(false, 1.25, 1000));
		// falling further (off a ledge, a long knock): the mace smashes, so the mace
		assertTrue(MaceFight.maceInHand(false, 2.0, 1000));
	}

	@Test
	void theReachFromAboveIsMeasuredToTheirHead() {
		V3 them = new V3(0, 64, 0);
		// 3 blocks above their feet, right over them: the eyes are 2.82 above the top of their head
		assertEquals(64 + 3 + 1.62 - 64 - 1.8, MaceFight.reachTo(new V3(0, 67, 0), them), 1e-9);
		// at the same height the reach is to their side
		assertEquals(2.7, MaceFight.reachTo(new V3(3, 64, 0), them), 1e-9);
		// eyes within their body (standing in them): no distance at all
		assertEquals(0, MaceFight.reachTo(new V3(0.1, 64, 0), them), 1e-9);
	}

	@Test
	void theSmashComesOnTheWayDownWithinReach() {
		V3 them = new V3(0, 64, 0);
		// six blocks fallen, 3 above their feet, coming down: now
		assertTrue(MaceFight.smash(air(0.5, 67, 0, -0.9, 6), them));
		// not yet fallen far enough for the bonus (a jump), not on the way up, not out of reach, not on the ground
		assertFalse(MaceFight.smash(air(0.5, 67, 0, -0.9, 1.2), them));
		assertFalse(MaceFight.smash(air(0.5, 67, 0, 0.5, 6), them));
		assertFalse(MaceFight.smash(air(0.5, 70, 0, -0.9, 6), them));
		assertFalse(MaceFight.smash(new Situation.Self(new V3(0.5, 64, 0), new V3(0, -0.9, 0), 20, 20, true, 1, false, false, 3.0, true, false, 0,
				Weapon.MACE, true, 6), them));
	}

	@Test
	void itDriftsOverThemAndStopsThere() {
		// still far: forward
		assertEquals(1, MaceFight.diveForward(4, 0));
		// a block away but moving fast enough to drift there by itself: let go
		assertEquals(0, MaceFight.diveForward(1.0, 0.1));
		// it would sail far past them: back
		assertEquals(-1, MaceFight.diveForward(0.8, 0.2));
		// right above them: still, or braking when it still moves on
		assertEquals(0, MaceFight.diveForward(0.2, 0));
		assertEquals(-1, MaceFight.diveForward(0.2, 0.1));
	}

	@Test
	void afterALaunchItComesDownOnThemWithTheMace() {
		CombatSteering st = new CombatSteering();
		Random r = new Random(3);
		Situation.Target foe = foe(new V3(3.5, 64, 0), 20);
		// on the ground, close: sooner or later a launch
		int t = 0;
		while (t < 2000 && !st.steer(Tactic.STRIKE, new Situation(t, me(Weapon.MACE, false, true), foe, null, open(), Situation.Threat.NONE), null,
				Archetype.BALANCED.base, Difficulty.EXPERT, r).launch()) t++;
		assertTrue(t < 2000, "no launch");
		// up in the air, still to the side of them: towards them, whatever the tactic says on the ground (even backing off)
		Intent up = st.steer(Tactic.RETREAT, new Situation(t + 8, air(0, 70, 0, 0.6, 0), foe, null, open(), Situation.Threat.NONE), null,
				Archetype.BALANCED.base, Difficulty.EXPERT, r);
		assertEquals(1, up.forward());
		assertFalse(up.attack(), "no swing on the way up");
		assertTrue(up.look() != null && up.look().y() < 70, "eyes down on them");
		// over them on the way down, within reach and far enough fallen: the smash (an expert does not let it pass)
		boolean smashed = false;
		for (int k = 0; k < 3 && !smashed; k++) {
			smashed = st.steer(Tactic.STRIKE, new Situation(t + 30 + k, air(3.3, 67, 0, -0.9, 6), foe, null, open(), Situation.Threat.NONE), null,
					Archetype.BALANCED.base, Difficulty.EXPERT, r).attack();
		}
		assertTrue(smashed, "no smash");
		// back on the ground the tactic decides again: no dive any more (backing off backs off)
		Intent ground = st.steer(Tactic.RETREAT, new Situation(t + 45, me(Weapon.MACE, false, true), foe, null, open(), Situation.Threat.NONE), null,
				Archetype.BALANCED.base, Difficulty.EXPERT, r);
		assertEquals(-1, ground.forward());
		// in a sword fight a high jump is no dive
		Situation.Self swordAir = new Situation.Self(new V3(0, 70, 0), new V3(0, -0.9, 0), 20, 20, false, 1, false, false, 3.0, true, false, 0,
				Weapon.SWORD, false, 6);
		assertFalse(MaceFight.diving(swordAir, foe, 5));
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
