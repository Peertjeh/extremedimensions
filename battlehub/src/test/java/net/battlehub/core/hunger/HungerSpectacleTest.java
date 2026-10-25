package net.battlehub.core.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.battlehub.core.Orbit;

/** 1.47: the Gamemakers raise the stakes, and the night's tribute to the fallen. */
class HungerSpectacleTest {
	@Test
	void everyEightMinutesTheStakesRise() {
		int lvl = HgSchedule.TIER_TICKS;
		assertEquals(1, HgSchedule.tier(0));
		assertEquals(1, HgSchedule.tier(lvl - 1));
		assertEquals(2, HgSchedule.tier(lvl));
		assertEquals(3, HgSchedule.tier(2 * lvl));
		assertEquals(3, HgSchedule.tier(10 * lvl));
		assertTrue(HgSchedule.minGap(2 * lvl) < HgSchedule.minGap(lvl) && HgSchedule.minGap(lvl) < HgSchedule.minGap(0));
	}

	@Test
	void everythingHitsHarderAtAHigherLevel() {
		for (int t = 1; t < 3; t++) {
			int u = t + 1;
			assertTrue(Escalation.fireTicks(u) < Escalation.fireTicks(t), "faster fire");
			assertTrue(Escalation.fireWidth(u) > Escalation.fireWidth(t) && Escalation.fireDamage(u) > Escalation.fireDamage(t));
			assertTrue(Escalation.fogRadius(u) > Escalation.fogRadius(t) && Escalation.fogDrift(u) > Escalation.fogDrift(t));
			assertTrue(Escalation.muttsEach(u) > Escalation.muttsEach(t) && Escalation.muttTicks(u) >= Escalation.muttTicks(t));
			assertTrue(Escalation.swarms(u) > Escalation.swarms(t) && Escalation.stingDamage(u) > Escalation.stingDamage(t));
			assertTrue(Escalation.strikeEvery(u) < Escalation.strikeEvery(t) && Escalation.strikeDamage(u) > Escalation.strikeDamage(t));
			assertTrue(Escalation.acidEvery(u) < Escalation.acidEvery(t) && Escalation.acidDamage(u) > Escalation.acidDamage(t));
			assertTrue(Escalation.meteorEvery(u) < Escalation.meteorEvery(t) && Escalation.meteorPower(u) > Escalation.meteorPower(t));
			assertTrue(Escalation.shockEvery(u) < Escalation.shockEvery(t) && Escalation.shockDamage(u) > Escalation.shockDamage(t));
		}
		// even the first level hurts more than the old events (fire 1 every half second, poison I, a sting of 2)
		assertTrue(Escalation.fireDamage(1) > 1 && Escalation.stingDamage(1) > 2);
		assertTrue(!Escalation.twoFronts(2) && Escalation.twoFronts(3));
	}

	@Test
	void laterInTheMatchTheEventsComeCloserTogether() {
		int limit = 24 * 60 * 20, grace = 30 * 20;
		long early = 0, late = 0;
		int ne = 0, nl = 0;
		for (long seed = 0; seed < 200; seed++) {
			List<HgSchedule.Entry> plan = HgSchedule.plan(seed, grace, limit, -1);
			for (int i = 1; i < plan.size(); i++) {
				int gap = plan.get(i).tick() - plan.get(i - 1).tick();
				if (HgSchedule.tier(plan.get(i - 1).tick()) == 1) {
					early += gap;
					ne++;
				} else if (HgSchedule.tier(plan.get(i - 1).tick()) == 3) {
					late += gap;
					nl++;
				}
			}
		}
		assertTrue(ne > 0 && nl > 0);
		assertTrue(late / (double) nl < 0.75 * early / ne, "gaps late " + late / nl + " early " + early / ne);
		assertTrue(HgSchedule.known("meteors") && HgSchedule.known("earthquake"));
	}

	@Test
	void theNightTakesAsLongAsItsFallenNeed() {
		NightPlan none = new NightPlan(0);
		assertEquals(NightPlan.EMPTY, none.length);
		assertEquals(-1, none.faceShown(NightPlan.INTRO + 5));
		NightPlan one = new NightPlan(1);
		assertEquals(NightPlan.LONGEST, one.each);
		assertEquals(NightPlan.INTRO + NightPlan.LONGEST + NightPlan.OUTRO, one.length);
		assertEquals(0, one.faceShown(NightPlan.INTRO));
		assertEquals(-1, one.faceShown(NightPlan.INTRO + NightPlan.LONGEST));
		// many fallen: shorter each, but never shorter than the shortest, and the night stays within the anthem then
		NightPlan many = new NightPlan(12);
		assertTrue(many.each >= NightPlan.SHORTEST && many.each < NightPlan.LONGEST, "each " + many.each);
		assertTrue(many.length <= NightPlan.ANTHEM);
		for (int i = 0; i < 12; i++) assertEquals(i, many.faceShown(many.faceAt(i) + 1));
		NightPlan crowd = new NightPlan(30);
		assertEquals(NightPlan.SHORTEST, crowd.each);
	}

	@Test
	void theCameraRisesFromTheEyesLooksUpAtTheStageAndComesBack() {
		NightPlan p = new NightPlan(3);
		double x = 10, y = 70, z = -4;
		float yaw = 90f;
		Orbit.Pose start = p.camera(x, y, z, yaw, 0);
		assertEquals(x, start.x(), 1e-6);
		assertEquals(y, start.y(), 1e-6);
		assertEquals(yaw, start.yaw(), 1e-3);
		Orbit.Pose mid = p.camera(x, y, z, yaw, p.length / 2);
		assertTrue(mid.y() > y + NightPlan.CAMERA_UP - 0.5, "up in the air: " + mid.y());
		assertTrue(mid.pitch() < -20, "looking up at the stage: " + mid.pitch());
		double[] sky = NightPlan.sky(x, y, z, yaw);
		// the stage is ahead of where the viewer looked (yaw 90: towards -x)
		assertTrue(sky[0] < x - 10 && sky[1] > y + 20);
		Orbit.Pose end = p.camera(x, y, z, yaw, p.length);
		assertEquals(x, end.x(), 1e-6);
		assertEquals(z, end.z(), 1e-6);
	}
}
