package net.battlehub.core.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The real Hunger Games' rules without Minecraft: the Gamemakers' plan, sponsors, districts, crates and gifts. */
class HungerRulesTest {
	private static final int GRACE = 30 * 20, LIMIT = 20 * 60 * 20, FEAST = 8 * 60 * 20;

	@Test
	void eventsComeEveryFewMinutesAfterThePeaceWithTheFeastOnceAndNoneAtTheEnd() {
		for (long seed = 0; seed < 200; seed++) {
			List<HgSchedule.Entry> plan = HgSchedule.plan(seed, GRACE, LIMIT, FEAST);
			int feasts = 0;
			for (int i = 0; i < plan.size(); i++) {
				HgSchedule.Entry e = plan.get(i);
				assertTrue(e.tick() > GRACE, "nothing during the peace");
				assertTrue(e.tick() <= LIMIT - HgSchedule.QUIET_END, "nothing in the last minute");
				assertTrue(HgSchedule.known(e.id()));
				if (e.id().equals(HgSchedule.FEAST)) {
					feasts++;
					assertEquals(FEAST, e.tick());
				}
				if (i > 0) {
					HgSchedule.Entry prev = plan.get(i - 1);
					assertNotEquals(prev.id(), e.id(), "never the same twice in a row");
					boolean nearFeast = prev.id().equals(HgSchedule.FEAST) || e.id().equals(HgSchedule.FEAST);
					// the Feast keeps its minute (and half a minute after) free; other events are 2.5 to 3.5 minutes apart
					assertTrue(e.tick() - prev.tick() >= (nearFeast ? HgSchedule.FEAST_WARNING_TICKS : HgSchedule.MIN_GAP),
							"seed " + seed + ": " + prev + " then " + e);
				}
			}
			assertEquals(1, feasts, "the Feast comes once");
			assertTrue(plan.size() >= 5, "a twenty minute match has several events");
		}
	}

	@Test
	void everyKindIsDrawnAndTheFeastFallsAwayWhenTheMatchIsTooShort() {
		Map<String, Integer> seen = new HashMap<>();
		for (long seed = 0; seed < 300; seed++) {
			for (HgSchedule.Entry e : HgSchedule.plan(seed, GRACE, LIMIT, FEAST)) seen.merge(e.id(), 1, Integer::sum);
		}
		for (HgSchedule.Kind k : HgSchedule.KINDS) assertTrue(seen.getOrDefault(k.id(), 0) > 0, k.id());
		// a five minute match: no Feast at minute eight
		assertTrue(HgSchedule.plan(1, GRACE, 5 * 60 * 20, FEAST).stream().noneMatch(e -> e.id().equals(HgSchedule.FEAST)));
		assertFalse(HgSchedule.known("nonsense"));
	}

	@Test
	void sponsorsSendAFewGiftsThatGetHarderToEarn() {
		assertEquals(0, Favour.giftsEarned(79));
		assertEquals(1, Favour.giftsEarned(80));
		assertEquals(2, Favour.giftsEarned(Favour.threshold(1)));
		for (int n = 1; n < 8; n++) {
			assertTrue(Favour.threshold(n) - Favour.threshold(n - 1) > Favour.threshold(n - 1) - (n >= 2 ? Favour.threshold(n - 2) : 0),
					"each gift is further away than the one before");
		}
		// two kills and some damage: the first parachute, not yet the second
		int points = 2 * Favour.KILL + 3 * Favour.DAMAGE_TEN;
		assertEquals(1, Favour.giftsEarned(points));
		assertEquals(Favour.threshold(1) - points, Favour.toNext(points, 1));
	}

	@Test
	void twoTributesPerDistrictAndScoresFromOneToTwelve() {
		Random r = new Random(5);
		List<Integer> d = Districts.assign(16, r);
		Map<Integer, Integer> per = new HashMap<>();
		for (int x : d) per.merge(x, 1, Integer::sum);
		assertEquals(8, per.size());
		per.values().forEach(c -> assertEquals(2, c));
		assertEquals(3, new java.util.HashSet<>(Districts.assign(5, r)).size(), "an odd one shares no district");
		for (int i = 0; i < 500; i++) {
			int s = Districts.trainingScore(r.nextInt(20), r.nextInt(500), r.nextInt(100), r);
			assertTrue(s >= 1 && s <= 12);
		}
		double newcomer = 0, veteran = 0;
		for (int i = 0; i < 400; i++) {
			newcomer += Districts.trainingScore(0, 0, 1, r);
			veteran += Districts.trainingScore(10, 400, 80, r);
		}
		assertTrue(newcomer / 400 < 5 && veteran / 400 > 9, "a veteran scores higher than a newcomer");
		assertTrue(Districts.botScore(5, r) > Districts.botScore(0, r));
	}

	@Test
	void aCrateOpensAfterFiveSecondsOfSneakingAndAHitStartsItOver() {
		Crack c = new Crack();
		for (int i = 0; i < Crack.TICKS - 1; i++) assertFalse(c.tick(true, false));
		assertFalse(c.tick(true, true), "hurt on the last tick");
		assertEquals(0, c.progress());
		for (int i = 0; i < Crack.TICKS - 1; i++) c.tick(true, false);
		assertFalse(c.tick(false, false), "stood up");
		for (int i = 0; i < Crack.TICKS - 1; i++) assertFalse(c.tick(true, false));
		assertTrue(c.tick(true, false));
		assertTrue(c.open());
		assertFalse(c.tick(true, false), "it opens once");
	}

	@Test
	void aParachuteBringsWhatTheTributeNeedsMost() {
		assertEquals(GiftPick.Kind.HEAL, GiftPick.pick(0.3, 5, false));
		assertEquals(GiftPick.Kind.FOOD, GiftPick.pick(0.9, 8, false));
		assertEquals(GiftPick.Kind.WEAPON, GiftPick.pick(0.9, 18, false));
		assertEquals(GiftPick.Kind.POWER, GiftPick.pick(0.9, 18, true));
	}
}
