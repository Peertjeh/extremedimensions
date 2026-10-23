package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A person's step of a tick becomes the velocity the game keeps (what a bot's getDeltaMovement() is): a steady sprint,
 * standing still, a jump, the air and ice. ml/tests/test_fights.py checks that train/fights.py turns the steps of
 * older logs into the same numbers.
 */
class VelocityTest {
	@Test
	void aStepBecomesTheVelocityAfterFrictionAndGravity() {
		// a steady sprint on the ground: 0.13 a tick from the keys, kept 0.546 of: a step of 0.2863, a velocity of 0.1563
		double step = 0.13 / (1 - 0.546);
		double[] v = Velocity.fromStep(step, 0, 0, true, Velocity.FRICTION, Velocity.GRAVITY);
		assertEquals(step * 0.546, v[0], 1e-12);
		assertEquals(0.1563, v[0], 1e-4);
		// standing still on the ground: the game's resting -0.0784
		assertArrayEquals(new double[] {0, -0.0784, 0}, Velocity.fromStep(0, 0, 0, true, Velocity.FRICTION, Velocity.GRAVITY), 1e-12);
		// the first tick of a jump: up 0.42, then the gravity off
		assertEquals((0.42 - 0.08) * 0.98, Velocity.fromStep(0, 0.42, 0, false, Velocity.FRICTION, Velocity.GRAVITY)[1], 1e-12);
		// in the air 0.91 of the step is kept, on ice 0.98 * 0.91
		assertEquals(0.91 * 0.3, Velocity.fromStep(0.3, 0, -0.3, false, Velocity.FRICTION, Velocity.GRAVITY)[0], 1e-12);
		assertEquals(-0.91 * 0.3, Velocity.fromStep(0.3, 0, -0.3, false, Velocity.FRICTION, Velocity.GRAVITY)[2], 1e-12);
		assertEquals(0.98 * 0.91 * 0.3, Velocity.fromStep(0.3, 0, 0, true, 0.98, Velocity.GRAVITY)[0], 1e-12);
		// a lower gravity (a Chaos event) takes less off
		assertEquals((0 - 0.02) * 0.98, Velocity.fromStep(0, 0, 0, false, Velocity.FRICTION, 0.02)[1], 1e-12);
	}
}
