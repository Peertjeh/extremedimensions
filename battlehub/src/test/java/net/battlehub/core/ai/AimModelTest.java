package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** The hand on the mouse: it follows, lags, wobbles and overshoots; better levels are on target more often. */
class AimModelTest {
	/** Half the width of a player (0.3) seen from {@code dist} blocks, in degrees: the yaw error a hit allows. */
	private static double halfWidth(double dist) {
		return Math.toDegrees(Math.atan(0.3 / dist));
	}

	/**
	 * Share of ticks the crosshair is on a target 2.8 blocks away (close combat) that strafes sideways at
	 * {@code speed} blocks a tick within a block and a half of the middle, changing direction every 8 to 24 ticks
	 * the way players strafe. A click at a random moment lands with about this chance.
	 */
	static double onTarget(Difficulty d, double speed, long seed) {
		AimModel aim = new AimModel(AimModel.Params.of(d), seed);
		Random r = new Random(seed * 7 + 1);
		double z = 0, dir = 1;
		int hold = 10, on = 0, n = 0;
		aim.sync(0, 0);
		for (int t = 0; t < 800; t++) {
			if (--hold <= 0 || Math.abs(z) > 1.5) {
				dir = Math.abs(z) > 1.5 ? -Math.signum(z) : -dir;
				hold = 8 + r.nextInt(17);
			}
			z += dir * speed;
			float targetYaw = (float) Math.toDegrees(Math.atan2(-z, 2.8));
			float[] a = aim.step(targetYaw, 15);
			if (t > 40) {
				n++;
				if (Math.abs(AimModel.wrap(a[0] - targetYaw)) < halfWidth(Math.hypot(2.8, z))) on++;
			}
		}
		return on / (double) n;
	}

	private static double avg(Difficulty d, double speed) {
		double s = 0;
		for (long seed = 0; seed < 20; seed++) s += onTarget(d, speed, seed);
		return s / 20;
	}

	@Test
	void againstAStrafingTargetBetterLevelsAreOnTargetMoreButNobodyAlways() {
		// sprint-strafing (0.22 blocks a tick) and walk-strafing (0.13)
		for (double speed : new double[] {0.22, 0.13}) {
			double beginner = avg(Difficulty.BEGINNER, speed), intermediate = avg(Difficulty.INTERMEDIATE, speed), master = avg(Difficulty.MASTER, speed);
			assertTrue(master > intermediate && intermediate > beginner, beginner + " < " + intermediate + " < " + master);
			assertTrue(master < 0.95, "master hardly misses: " + master);
			assertTrue(beginner < 0.6, "beginner too accurate: " + beginner);
			assertTrue(beginner > 0.1, "beginner never on target: " + beginner);
		}
		assertTrue(avg(Difficulty.MASTER, 0.22) > 0.65);
	}

	@Test
	void aStillTargetIsHitMostOfTheTime() {
		assertTrue(avg(Difficulty.MASTER, 0) > 0.95, "master " + avg(Difficulty.MASTER, 0));
		assertTrue(avg(Difficulty.BEGINNER, 0) > 0.5, "beginner " + avg(Difficulty.BEGINNER, 0));
	}

	@Test
	void aBigTurnOvershootsAndSettles() {
		AimModel aim = new AimModel(AimModel.Params.of(Difficulty.CASUAL), 4);
		aim.sync(0, 0);
		float max = -1000;
		float[] a = null;
		for (int t = 0; t < 60; t++) {
			a = aim.step(90, 0);
			max = Math.max(max, a[0]);
		}
		assertTrue(max > 92, "no overshoot: " + max);
		assertTrue(Math.abs(a[0] - 90) < 8, "did not settle: " + a[0]);
	}

	@Test
	void itTakesTimeToTurnAndAHitThrowsTheAimOff() {
		AimModel aim = new AimModel(AimModel.Params.of(Difficulty.INTERMEDIATE), 2);
		aim.sync(0, 0);
		float[] first = aim.step(120, 0);
		assertTrue(Math.abs(first[0]) < 40, "turned " + first[0] + " in one tick");
		for (int t = 0; t < 40; t++) aim.step(120, 0);
		aim.disturb(15);
		float[] knocked = aim.step(120, 0);
		float err = Math.abs(AimModel.wrap(knocked[0] - 120));
		float[] later = null;
		for (int t = 0; t < 30; t++) later = aim.step(120, 0);
		assertTrue(Math.abs(AimModel.wrap(later[0] - 120)) < Math.max(4, err), err + " then " + later[0]);
	}

	@Test
	void whenSomethingElseTurnsTheHeadTheHandStartsFromThere() {
		AimModel aim = new AimModel(AimModel.Params.of(Difficulty.EXPERT), 5);
		aim.sync(0, 0);
		for (int t = 0; t < 20; t++) aim.step(10, 0);
		aim.sync(170, 0);
		float[] a = aim.step(10, 0);
		// from 170 towards 10: still far away after one tick, not jumped back
		assertTrue(Math.abs(AimModel.wrap(a[0] - 10)) > 100, "jumped to " + a[0]);
	}

	/** On target against a target strafing with holds of {@code holdMin} + up to {@code span} ticks, knowing (or not) how predictable it is. */
	private static double versus(Difficulty d, double learned, int holdMin, int span) {
		double sum = 0;
		for (long seed = 0; seed < 30; seed++) {
			AimModel aim = new AimModel(AimModel.Params.of(d), seed);
			if (learned >= 0) aim.adapt(learned);
			Random r = new Random(seed * 7 + 1);
			double z = 0, dir = 1;
			int hold = 10, on = 0, n = 0;
			aim.sync(0, 0);
			for (int t = 0; t < 800; t++) {
				if (--hold <= 0 || Math.abs(z) > 2.5) {
					dir = Math.abs(z) > 2.5 ? -Math.signum(z) : -dir;
					hold = holdMin + r.nextInt(span);
				}
				z += dir * 0.2;
				float targetYaw = (float) Math.toDegrees(Math.atan2(-z, 2.8));
				float[] a = aim.step(targetYaw, 15);
				if (t > 40) {
					n++;
					if (Math.abs(AimModel.wrap(a[0] - targetYaw)) < halfWidth(Math.hypot(2.8, z))) on++;
				}
			}
			sum += on / (double) n;
		}
		return sum / 30;
	}

	@Test
	void knowingHowSomeoneStrafesMakesTheAimBetter() {
		// an erratic strafer (direction changes every 3 to 6 ticks: unpredictable) is aimed at better once that is known
		for (Difficulty d : new Difficulty[] {Difficulty.CASUAL, Difficulty.INTERMEDIATE, Difficulty.EXPERT}) {
			assertTrue(versus(d, 0, 3, 4) > versus(d, -1, 3, 4) + 0.02, d.id());
		}
		// a steady strafer (long holds) is led all the way: a big help for the lower levels
		assertTrue(versus(Difficulty.CASUAL, 0.8, 25, 15) > versus(Difficulty.CASUAL, -1, 25, 15) + 0.08);
	}

	@Test
	void itsOwnMovementDoesNotThrowItOff() {
		// the NPC strafes round a target that stands still: a player knows how they move themselves, so the
		// crosshair stays on (aiming from positions); only the target's own movement is followed with a lag
		for (Difficulty d : new Difficulty[] {Difficulty.INTERMEDIATE, Difficulty.EXPERT}) {
			AimModel aim = new AimModel(AimModel.Params.of(d), 3);
			Random r = new Random(5);
			double x = 0, dir = 1;
			int hold = 10, on = 0, n = 0;
			aim.sync(0, 0);
			for (int t = 0; t < 600; t++) {
				if (--hold <= 0 || Math.abs(x) > 1.5) {
					dir = Math.abs(x) > 1.5 ? -Math.signum(x) : -dir;
					hold = 6 + r.nextInt(14);
				}
				x += dir * 0.22;
				// eye at (x, 1.62, 0), target chest at (0, 1.2, 2.8)
				float[] a = aim.stepAt(x, 1.62, 0, 0, 1.2, 2.8);
				float want = (float) Math.toDegrees(Math.atan2(x, 2.8));
				if (t > 40) {
					n++;
					if (Math.abs(AimModel.wrap(a[0] - want)) < halfWidth(Math.hypot(2.8, x))) on++;
				}
			}
			assertTrue(on / (double) n > 0.85, d.id() + ": on target " + on / (double) n);
		}
	}
}
