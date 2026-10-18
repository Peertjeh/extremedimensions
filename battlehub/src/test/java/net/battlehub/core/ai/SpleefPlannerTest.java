package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SpleefPlannerTest {
	/** A full square floor from (0,0) to (size-1, size-1), with some cells removed. */
	private static SpleefPlanner.Floor floor(int size, int[]... holes) {
		boolean[] p = new boolean[size * size];
		java.util.Arrays.fill(p, true);
		for (int[] h : holes) p[h[1] * size + h[0]] = false;
		return new SpleefPlanner.Floor(0, 0, size, p);
	}

	private static final Personality P = Archetype.BALANCED.base;

	@Test
	void itDigsTheLastBlockUnderAnOpponentInReach() {
		// the opponent stands in the middle of cell (8,5); its neighbours are gone except that one
		SpleefPlanner.Floor f = floor(12, new int[] {7, 5}, new int[] {9, 5}, new int[] {8, 4}, new int[] {8, 6});
		SpleefPlanner.Plan plan = SpleefPlanner.plan(f, 5.5, 5.5, List.of(new SpleefPlanner.Foe(8.5, 5.5, 0, 0)), 4.5, P, Difficulty.EXPERT);
		assertNotNull(plan.dig());
		assertArrayEquals(new int[] {8, 5}, plan.dig());
		assertTrue(plan.why().contains("last block"), plan.why());
	}

	@Test
	void itNeverDigsUnderItselfOrNextToItself() {
		SpleefPlanner.Floor f = floor(12);
		for (double fx = 3; fx <= 8; fx += 0.25) {
			SpleefPlanner.Plan plan = SpleefPlanner.plan(f, 5.5, 5.5, List.of(new SpleefPlanner.Foe(fx, 5.5, 0, 0)), 4.5, P, Difficulty.MASTER);
			if (plan.dig() == null) continue;
			double d = Math.hypot(plan.dig()[0] + 0.5 - 5.5, plan.dig()[1] + 0.5 - 5.5);
			assertTrue(d >= 1.1, "dug next to itself at " + plan.dig()[0] + "," + plan.dig()[1] + " for foe at " + fx);
		}
	}

	@Test
	void itDoesNotCutItselfOffOnASmallIsland() {
		// the NPC stands on a 2x3 island joined to the rest only through cell (3,5); the foe stands right on it
		boolean[] p = new boolean[12 * 12];
		java.util.Arrays.fill(p, false);
		for (int x = 0; x <= 1; x++) for (int z = 4; z <= 6; z++) p[z * 12 + x] = true;
		for (int x = 2; x <= 3; x++) p[5 * 12 + x] = true;
		for (int x = 4; x < 12; x++) for (int z = 0; z < 12; z++) p[z * 12 + x] = true;
		SpleefPlanner.Floor f = new SpleefPlanner.Floor(0, 0, 12, p);
		SpleefPlanner.Plan plan = SpleefPlanner.plan(f, 0.5, 5.5, List.of(new SpleefPlanner.Foe(3.5, 5.5, 0, 0)), 4.5, P, Difficulty.MASTER);
		if (plan.dig() != null) {
			assertFalse(plan.dig()[0] == 2 && plan.dig()[1] == 5, "cut its only way off");
			assertFalse(plan.dig()[0] == 3 && plan.dig()[1] == 5, "cut its only way off");
		}
	}

	@Test
	void higherLevelsDigWhereARunningOpponentIsGoing() {
		SpleefPlanner.Floor f = floor(16);
		SpleefPlanner.Foe running = new SpleefPlanner.Foe(9.5, 5.5, 0, 0.25);
		SpleefPlanner.Plan master = SpleefPlanner.plan(f, 6.5, 5.5, List.of(running), 4.5, P, Difficulty.MASTER);
		SpleefPlanner.Plan beginner = SpleefPlanner.plan(f, 6.5, 5.5, List.of(running), 4.5, P, Difficulty.BEGINNER);
		assertNotNull(master.dig());
		assertTrue(master.dig()[1] > 5, "master digs ahead: " + master.dig()[1]);
		assertNotNull(beginner.dig());
		assertTrue(beginner.dig()[1] == 5, "beginner digs where they are");
	}

	@Test
	void outOfReachItMovesOnFirmGroundTowardsThemOrAwayWhenTooClose() {
		SpleefPlanner.Floor f = floor(20);
		SpleefPlanner.Plan far = SpleefPlanner.plan(f, 3.5, 10.5, List.of(new SpleefPlanner.Foe(16.5, 10.5, 0, 0)), 4.5, Archetype.RUSHER.base, Difficulty.EXPERT);
		assertNull(far.dig());
		assertNotNull(far.stand());
		assertTrue(far.stand()[0] > 3, "goes towards them");
		SpleefPlanner.Plan close = SpleefPlanner.plan(f, 10.5, 10.5, List.of(new SpleefPlanner.Foe(11.5, 10.5, 0, 0)), 4.5, P, Difficulty.EXPERT);
		assertNotNull(close.stand());
		assertTrue(Math.hypot(close.stand()[0] + 0.5 - 11.5, close.stand()[1] + 0.5 - 10.5) >= 2.2, "does not stay next to them");
	}

	@Test
	void standingOnNothingMeansFalling() {
		SpleefPlanner.Floor f = floor(8, new int[] {3, 3});
		assertTrue(SpleefPlanner.plan(f, 3.5, 3.5, List.of(), 4.5, P, Difficulty.EXPERT).why().equals("falling"));
	}
}
