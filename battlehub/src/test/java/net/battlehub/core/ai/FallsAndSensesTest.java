package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Looking before jumping, steering back after a knock, and what a bot can see and hear. */
class FallsAndSensesTest {
	/** A floor at y 64 for x below {@code edge} (the block from x to x+1 is there when x < edge), nothing beyond. */
	private static Falls.Ground platform(double edge) {
		return (x, y, z) -> x < edge && y >= 64 ? 64 : Double.NaN;
	}

	@Test
	void aJumpTowardsTheVoidIsSeenToEndBadly() {
		Falls.Ground g = platform(5);
		// a walking jump in the middle of the platform lands on it
		assertTrue(Falls.landsSafely(0.5, 64, 0, 0.1, 0, 0, 1, 0, false, true, g, 1.5));
		// a sprint jump from near the edge, towards it, does not
		assertFalse(Falls.landsSafely(4.0, 64, 0, 0.28, 0, 0, 1, 0, true, true, g, 1.5));
		// the same jump the other way is fine
		assertTrue(Falls.landsSafely(4.0, 64, 0, -0.28, 0, 0, -1, 0, true, true, g, 1.5));
	}

	@Test
	void afterAKnockHoldingBackSavesWhatLettingGoLoses() {
		Falls.Ground g = platform(5);
		// knocked towards the edge (a typical hit: 0.4 sideways and up, slowed once by the ground)
		double x = 4.0, vx = 0.22, vy = 0.36;
		assertFalse(Falls.landsSafely(x, 64.4, 0, vx, vy, 0, 0, 0, false, false, g, 1.5), "should fall without doing anything");
		assertTrue(Falls.landsSafely(x, 64.4, 0, vx, vy, 0, -1, 0, false, false, g, 1.5), "holding back should save it");
	}

	@Test
	void aSmallStepDownOrWaterIsFineADeepDropIsNot() {
		Falls.Ground lower = (x, y, z) -> x < 5 ? 64 : 62;
		assertTrue(Falls.landsSafely(4.0, 64, 0, 0.28, 0, 0, 1, 0, true, true, lower, 2.5));
		Falls.Ground deep = (x, y, z) -> x < 5 ? 64 : 56;
		assertFalse(Falls.landsSafely(4.0, 64, 0, 0.28, 0, 0, 1, 0, true, true, deep, 2.5));
	}

	@Test
	void sightCoversWhatIsInFrontHearingWhatIsCloseAndMoving() {
		V3 eye = new V3(0, 65.6, 0);
		// yaw 0 looks south (+z)
		assertTrue(Senses.inView(eye, 0, 0, new V3(0, 65, 5)));
		assertFalse(Senses.inView(eye, 0, 0, new V3(0, 65, -5)), "behind");
		assertTrue(Senses.inView(eye, 0, 0, new V3(-4, 65, 5)), "a little to the side");
		assertFalse(Senses.inView(eye, 0, 0, new V3(-5, 65.6, 1)), "far to the side");
		assertTrue(Senses.inView(eye, 90, 0, new V3(-5, 65.6, 0)), "turned towards it");
		assertTrue(Senses.heard(6, false, 0.2, 1000));
		assertFalse(Senses.heard(6, true, 0.2, 1000), "sneaking is silent");
		assertFalse(Senses.heard(6, false, 0, 1000), "standing still is silent");
		assertFalse(Senses.heard(20, false, 0.2, 1000), "too far");
		assertTrue(Senses.heard(20, true, 0, 5), "but who just hit me is known");
	}
}
