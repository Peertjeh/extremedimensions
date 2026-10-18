package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class JumpPlannerTest {
	@Test
	void walkingAndSprintingReachTheGamesSpeeds() {
		PlayerSim walk = new PlayerSim(0, 64, 0), sprint = new PlayerSim(0, 64, 0);
		for (int i = 0; i < 60; i++) {
			walk.tick(1, 0, false, false);
			sprint.tick(1, 0, true, false);
		}
		double w0 = walk.x, s0 = sprint.x;
		walk.tick(1, 0, false, false);
		sprint.tick(1, 0, true, false);
		// the distance covered per tick: 4.317 and 5.612 blocks a second
		assertEquals(4.317 / 20, walk.x - w0, 0.002);
		assertEquals(5.612 / 20, sprint.x - s0, 0.002);
	}

	@Test
	void aSprintJumpFliesAboutThreeAndAHalfBlocks() {
		PlayerSim s = new PlayerSim(0, 64, 0);
		for (int i = 0; i < 40; i++) s.tick(1, 0, true, false);
		double start = s.x;
		s.tick(1, 0, true, true);
		while (s.y > 64 || s.vy > 0) s.tick(1, 0, true, false);
		double flown = s.x - start;
		// about 3.6 blocks in the air; with the feet allowed 0.3 over both edges that clears a four-block gap
		assertTrue(flown > 3.4 && flown < 3.9, "flew " + flown);
	}

	/** Two platforms of {@code size} blocks along x, {@code gap} air blocks between them, the second {@code rise} higher. */
	private static JumpPlanner.Pad[] pads(int size, int gap, double rise) {
		JumpPlanner.Pad a = new JumpPlanner.Pad(0, 0, size, size, 64, 0.6);
		JumpPlanner.Pad b = new JumpPlanner.Pad(size + gap, 0, size + gap + size, size, 64 + rise, 0.6);
		return new JumpPlanner.Pad[] {a, b};
	}

	@Test
	void gapsUpToFourAreJumpableFiveIsNot() {
		for (int gap = 1; gap <= 4; gap++) {
			JumpPlanner.Pad[] p = pads(3, gap, 0);
			assertNotNull(JumpPlanner.plan(p[0], p[1], Set.of()), "gap " + gap);
		}
		JumpPlanner.Pad[] five = pads(3, 5, 0);
		assertNull(JumpPlanner.plan(five[0], five[1], Set.of()), "a five-block gap is not possible");
	}

	@Test
	void jumpingUpABlockIsShorter() {
		JumpPlanner.Pad[] up2 = pads(3, 2, 1);
		assertNotNull(JumpPlanner.plan(up2[0], up2[1], Set.of()));
		JumpPlanner.Pad[] up4 = pads(3, 4, 1);
		assertNull(JumpPlanner.plan(up4[0], up4[1], Set.of()), "4 blocks across and 1 up is too far");
		JumpPlanner.Pad[] tooHigh = pads(3, 1, 1.5);
		assertNull(JumpPlanner.plan(tooHigh[0], tooHigh[1], Set.of()), "a jump does not reach 1.5 blocks up");
	}

	@Test
	void aMissedWayIsNotTriedAgain() {
		JumpPlanner.Pad[] p = pads(3, 2, 0);
		Set<String> failed = new HashSet<>();
		JumpPlanner.Plan first = JumpPlanner.plan(p[0], p[1], failed);
		assertNotNull(first);
		failed.add(first.variant());
		JumpPlanner.Plan second = JumpPlanner.plan(p[0], p[1], failed);
		assertNotNull(second);
		assertNotEquals(first.variant(), second.variant());
		for (String v : JumpPlanner.VARIANTS) failed.add(v);
		assertNull(JumpPlanner.plan(p[0], p[1], failed), "everything failed: no plan rather than the same mistake");
	}

	@Test
	void theLandingIsOnTheTargetWithRoom() {
		JumpPlanner.Pad[] p = pads(3, 3, 0);
		JumpPlanner.Plan plan = JumpPlanner.plan(p[0], p[1], Set.of());
		assertNotNull(plan);
		assertTrue(p[1].supports(plan.landing().x(), plan.landing().z()));
		assertTrue(plan.margin() > 0, "margin " + plan.margin());
		assertTrue(plan.sprint(), "three blocks across needs a sprint");
		// a one-block hop can be walked
		JumpPlanner.Pad[] hop = pads(3, 1, 0);
		assertNotNull(JumpPlanner.plan(hop[0], hop[1], Set.of("sprint-long", "sprint-short", "sprint-standing")));
	}
}
