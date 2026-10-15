package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BotMathTest {
	@Test
	void lookDirections() {
		assertEquals(0f, BotMath.yaw(0, 1), 1e-4);
		assertEquals(90f, BotMath.yaw(-1, 0), 1e-4);
		assertEquals(-90f, BotMath.yaw(1, 0), 1e-4);
		assertEquals(180f, Math.abs(BotMath.yaw(0, -1)), 1e-4);
		assertEquals(-45f, BotMath.pitch(1, 1), 1e-4);
		assertEquals(0f, BotMath.pitch(5, 0), 1e-4);
	}

	@Test
	void turning() {
		assertEquals(-20f, BotMath.wrap(340f), 1e-4);
		assertEquals(170f, BotMath.turn(170f, -170f, 30f) - 30f + 10f, 1e-3);
		assertEquals(10f, BotMath.turn(0f, 90f, 10f), 1e-4);
	}

	@Test
	void arrowsComeDownOnTheTarget() {
		for (double h : new double[] {8, 20, 35}) {
			for (double dy : new double[] {-3, 0, 4}) {
				float pitch = BotMath.arrowPitch(h, dy, 3.0);
				double y = BotMath.arrowHeightAt(h, -pitch, 3.0);
				assertEquals(dy, y, 0.15, "h=" + h + " dy=" + dy);
			}
		}
		// Far away targets need the bow pointed up
		assertTrue(BotMath.arrowPitch(40, 0, 3.0) < 0);
		assertTrue(BotMath.arrowTicks(30, 3.0) >= 10);
	}

	@Test
	void jumpsLandOnThePlatform() {
		double[][] cases = {{3, 0, 0}, {0, 0, 4}, {2, 1, 2}, {-3, -2, 1}, {1.5, 1, 0}, {4, 0, 1}};
		for (double[] c : cases) {
			BotMath.Jump j = BotMath.jump(c[0], c[1], c[2]);
			double[] end = BotMath.simulate(j, c[1]);
			double miss = Math.hypot(end[0] - c[0], end[2] - c[2]);
			assertTrue(miss < 0.3, "missed by " + miss + " for " + java.util.Arrays.toString(c));
		}
		// From ice (little friction on the first tick) the same jump needs less speed
		assertTrue(Math.abs(BotMath.jump(4, 0, 0, 0.98 * 0.91).vx()) < Math.abs(BotMath.jump(4, 0, 0).vx()));
		double[] ice = BotMath.simulate(BotMath.jump(4, 0, 0, 0.98 * 0.91), 0);
		assertEquals(4, ice[0], 0.3);
		// A one-block step up needs no more than a normal jump; two blocks need extra lift
		assertEquals(BotMath.JUMP, BotMath.jump(2, 1, 0).vy(), 1e-9);
		assertTrue(BotMath.jump(2, 2, 0).vy() > BotMath.JUMP);
	}

	@Test
	void levels() {
		assertEquals(BotLevel.NORMAL, BotLevel.parse("medium", BotLevel.EASY));
		assertEquals(BotLevel.HARD, BotLevel.parse("HARD", BotLevel.EASY));
		assertEquals(BotLevel.EASY, BotLevel.parse("x", BotLevel.EASY));
		assertTrue(BotLevel.HARD.reactionTicks < BotLevel.EASY.reactionTicks);
		assertTrue(BotLevel.HARD.aimError < BotLevel.NORMAL.aimError);
	}

	@Test
	void modeOfTheDayGoesThroughEveryMode() {
		java.util.List<String> pool = java.util.List.of("a", "b", "c", "d", "e");
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (long day = 20000; day < 20005; day++) {
			seen.add(Featured.pick(pool, day));
		}
		assertEquals(5, seen.size());
		assertEquals(Featured.pick(pool, 20000), Featured.pick(pool, 20005));
		assertEquals(null, Featured.pick(java.util.List.<String>of(), 1));
	}
}
