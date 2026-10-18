package net.battlehub.core.ai;

import static net.battlehub.core.ai.Fixtures.best;
import static net.battlehub.core.ai.Fixtures.foe;
import static net.battlehub.core.ai.Fixtures.me;
import static net.battlehub.core.ai.Fixtures.open;
import static net.battlehub.core.ai.Fixtures.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Objectives (hold a hill, guard a post, carry a flag), team roles and building across a gap. */
class ObjectiveTest {
	private static final V3 O = new V3(0, 64, 0);

	private static Situation with(Objective g, Situation.Self me, Situation.Target t) {
		return new Situation(0, me, t, g, open(), Situation.Threat.NONE);
	}

	private static List<Score> score(Situation s, Archetype a) {
		return CombatPolicy.score(s, new OpponentModel(), new OutcomeMemory(), a.base, Difficulty.EXPERT);
	}

	@Test
	void aFlagCarrierRunsHomeInsteadOfChasing() {
		Objective home = Objective.carry(new V3(30, 64, 0), "the flag");
		Situation s = with(home, me(O, 20, 1), foe(new V3(-6, 64, 0), 20));
		assertEquals(Tactic.OBJECTIVE, best(score(s, Archetype.RUSHER)));
	}

	@Test
	void aFlagCarrierStillHitsWhoBlocksTheWay() {
		Objective home = Objective.carry(new V3(30, 64, 0), "the flag");
		double free = value(score(with(home, me(O, 20, 1), foe(new V3(-6, 64, 0), 20)), Archetype.BALANCED), Tactic.OBJECTIVE);
		List<Score> blocked = score(with(home, me(O, 20, 1), foe(new V3(2.5, 64, 0), 20)), Archetype.BALANCED);
		assertTrue(value(blocked, Tactic.OBJECTIVE) < free - 0.3);
		assertTrue(value(blocked, Tactic.STRIKE) > 0.5, blocked.toString());
	}

	@Test
	void offTheHillTheNpcGoesBackOnIt() {
		Objective hill = Objective.hold(new V3(20, 64, 0), 2.6, "the hill");
		Situation s = with(hill, me(O, 20, 1), foe(new V3(-14, 64, 0), 20));
		assertEquals(Tactic.OBJECTIVE, best(score(s, Archetype.BALANCED)));
	}

	@Test
	void onTheHillItFightsWhoComesUpButDoesNotChaseAnyoneOff() {
		Objective hill = Objective.hold(O, 2.6, "the hill");
		Situation contest = with(hill, me(O, 20, 1), foe(new V3(2.5, 64, 0), 20));
		assertNotEquals(Tactic.OBJECTIVE, best(score(contest, Archetype.BALANCED)));
		Situation away = with(hill, me(O, 20, 1), foe(new V3(12, 64, 0), 20));
		List<Score> sc = score(away, Archetype.RUSHER);
		assertEquals(Tactic.OBJECTIVE, best(sc), sc.toString());
		assertTrue(sc.stream().filter(x -> x.tactic() == Tactic.APPROACH).anyMatch(x -> x.why().contains("too far from the hill")));
	}

	@Test
	void aGuardStaysAtItsPostUntilSomeoneComesClose() {
		Objective post = Objective.defend(O, 3, 10, "our bed");
		Situation far = with(post, me(O, 20, 1), foe(new V3(25, 64, 0), 20));
		assertEquals(Tactic.OBJECTIVE, best(score(far, Archetype.RUSHER)));
		Situation close = with(post, me(O, 20, 1), foe(new V3(4, 64, 0), 20));
		assertNotEquals(Tactic.OBJECTIVE, best(score(close, Archetype.BALANCED)));
	}

	@Test
	void aGuardAwayFromItsPostGoesBack() {
		Objective post = Objective.defend(new V3(15, 64, 0), 3, 10, "our bed");
		Situation s = with(post, me(O, 20, 1), null);
		List<Score> sc = score(s, Archetype.BALANCED);
		assertEquals(Tactic.OBJECTIVE, best(sc));
		Intent in = new CombatSteering().steer(Tactic.OBJECTIVE, s, null, Archetype.BALANCED.base, Difficulty.EXPERT, new java.util.Random(1));
		assertEquals(post.point(), in.moveTo());
	}

	@Test
	void onTheHillItStaysInsteadOfWalkingToTheMiddleForever() {
		Objective hill = Objective.hold(O, 2.6, "the hill");
		Situation s = with(hill, me(new V3(0.5, 64, 0.5), 20, 1), null);
		Intent in = new CombatSteering().steer(Tactic.OBJECTIVE, s, null, Archetype.BALANCED.base, Difficulty.EXPERT, new java.util.Random(1));
		assertNull(in.moveTo());
		assertEquals(0, in.forward());
		assertTrue(hill.contains(new V3(2, 65, -1)) && !hill.contains(new V3(4, 64, 0)) && !hill.contains(new V3(0, 70, 0)));
	}

	@Test
	void attackingWithNobodyAroundGoesForTheTarget() {
		Situation s = with(Objective.attack(new V3(40, 64, 0), "the enemy bed"), me(O, 20, 1), null);
		List<Score> sc = score(s, Archetype.COUNTER);
		assertEquals(Tactic.OBJECTIVE, best(sc));
		assertTrue(sc.stream().anyMatch(x -> x.why().contains("going for the enemy bed")));
	}

	// ---- roles

	private static UUID id(String s) {
		return UUID.nameUUIDFromBytes(s.getBytes());
	}

	@Test
	void theMostDefensiveNpcStaysHome() {
		List<Roles.Member> team = List.of(new Roles.Member(id("r"), Archetype.RUSHER.base, 5), new Roles.Member(id("c"), Archetype.COUNTER.base, 5));
		Map<UUID, Roles.Role> r = Roles.assign(team, 2, true);
		assertEquals(Roles.Role.DEFENDER, r.get(id("c")));
		assertEquals(Roles.Role.ATTACKER, r.get(id("r")));
		// the same split whatever the order
		assertEquals(r, Roles.assign(List.of(team.get(1), team.get(0)), 2, true));
	}

	@Test
	void rolesDependOnTheTeamAndWhatItDefends() {
		List<Roles.Member> one = List.of(new Roles.Member(id("a"), Archetype.RUSHER.base, 5));
		// alone: attack; with a person on the team the NPC keeps home
		assertEquals(Roles.Role.ATTACKER, Roles.assign(one, 1, true).get(id("a")));
		assertEquals(Roles.Role.DEFENDER, Roles.assign(one, 2, true).get(id("a")));
		// nothing to defend (a bed gone): everybody attacks
		assertEquals(Roles.Role.ATTACKER, Roles.assign(one, 2, false).get(id("a")));
		List<Roles.Member> four = new ArrayList<>();
		for (int i = 0; i < 4; i++) four.add(new Roles.Member(id("n" + i), Archetype.BALANCED.base, i));
		Map<UUID, Roles.Role> r = Roles.assign(four, 4, true);
		assertEquals(2, r.values().stream().filter(x -> x == Roles.Role.DEFENDER).count());
		// equal personalities: the ones closest to home stay
		assertEquals(Roles.Role.DEFENDER, r.get(id("n0")));
		assertEquals(Roles.Role.DEFENDER, r.get(id("n1")));
	}

	// ---- bridging

	@Test
	void theLineGoesFaceToFace() {
		List<int[]> straight = Bridging.line(0, 0, 5, 0, 64);
		assertEquals(6, straight.size());
		List<int[]> diag = Bridging.line(0, 0, 4, 3, 64);
		assertEquals(8, diag.size());
		for (int i = 1; i < diag.size(); i++) {
			int step = Math.abs(diag.get(i)[0] - diag.get(i - 1)[0]) + Math.abs(diag.get(i)[1] - diag.get(i - 1)[1]);
			assertEquals(1, step);
		}
		assertEquals(4, diag.get(diag.size() - 1)[0]);
		assertEquals(3, diag.get(diag.size() - 1)[1]);
		// backwards works too, and the length is capped
		List<int[]> back = Bridging.line(0, 0, -3, -2, 64);
		assertEquals(-3, back.get(back.size() - 1)[0]);
		assertEquals(4, Bridging.line(0, 0, 50, 0, 4).size());
	}

	@Test
	void theNextBlockGoesIntoTheFirstGapAgainstTheLastFloor() {
		// floor from x = 0 to 3, then a gap
		Bridging.Floor floor = new Bridging.Floor() {
			public boolean solid(int x, int z) {
				return x <= 3 && z == 0;
			}

			public boolean buildable(int x, int z) {
				return x < 20;
			}
		};
		Bridging.Step st = Bridging.next(Bridging.line(0, 0, 10, 0, 64), floor);
		assertNotNull(st);
		assertEquals(4, st.x());
		assertEquals(3, st.fromX());
		assertEquals(1, st.dx());
		assertEquals(0, st.dz());
		assertEquals(4, st.index());
		// whole floor: nothing to build; outside the build area: nothing either
		assertNull(Bridging.next(Bridging.line(0, 0, 3, 0, 64), floor));
		Bridging.Floor walled = new Bridging.Floor() {
			public boolean solid(int x, int z) {
				return x <= 3;
			}

			public boolean buildable(int x, int z) {
				return false;
			}
		};
		assertNull(Bridging.next(Bridging.line(0, 0, 10, 0, 64), walled));
		assertFalse(floor.solid(4, 0));
	}
}
