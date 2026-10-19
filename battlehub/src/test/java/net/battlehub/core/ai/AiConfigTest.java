package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.battlehub.core.BotLevel;

class AiConfigTest {
	@Test
	void everyArchetypeIsValidAndTheyDiffer() {
		for (Archetype a : Archetype.values()) {
			assertEquals(List.of(), a.base.problems(), a.id());
		}
		assertTrue(Archetype.RUSHER.base.aggression() > Archetype.COUNTER.base.aggression());
		assertTrue(Archetype.COUNTER.base.patience() > Archetype.RUSHER.base.patience());
		assertTrue(Archetype.ARCHER.base.range() > Archetype.RUSHER.base.range());
		assertEquals(Archetype.EVASIVE, Archetype.parse("Evasive", Archetype.BALANCED));
		assertEquals(Archetype.BALANCED, Archetype.parse("nope", Archetype.BALANCED));
	}

	@Test
	void badValuesAreReportedAndClamped() {
		Personality bad = new Personality(1.5, -1, 0.5, 9, 0.5, Double.NaN, 0.5, 0.5);
		assertEquals(4, bad.problems().size(), bad.problems().toString());
		assertEquals(List.of(), bad.clamped().problems());
	}

	@Test
	void individualsAreStableRecognisableAndDifferent() {
		Personality base = Archetype.DUELIST.base;
		Personality a = base.individual(1, 0.08), a2 = base.individual(1, 0.08), b = base.individual(2, 0.08);
		assertEquals(a, a2, "same seed, same individual");
		assertNotEquals(a, b);
		assertEquals(List.of(), a.problems());
		assertTrue(Math.abs(a.aggression() - base.aggression()) <= 0.08 + 1e-9, "stays recognisable");
		// a rusher individual is still more aggressive than any counter individual
		for (long s = 0; s < 50; s++) {
			assertTrue(Archetype.RUSHER.base.individual(s, 0.08).aggression() > Archetype.COUNTER.base.individual(s + 99, 0.08).aggression());
		}
	}

	@Test
	void higherLevelsPlayBetterNotStronger() {
		Difficulty[] all = Difficulty.values();
		for (int i = 1; i < all.length; i++) {
			Difficulty lo = all[i - 1], hi = all[i];
			assertTrue(hi.reactionTicks <= lo.reactionTicks && hi.thinkTicks <= lo.thinkTicks, hi.id());
			assertTrue(hi.aimError < lo.aimError && hi.mistakeRate < lo.mistakeRate, hi.id());
			assertTrue(hi.predictTicks >= lo.predictTicks && hi.strafeSkill >= lo.strafeSkill, hi.id());
			// clicking and edges: higher levels wait for fuller hits, press more promptly, swing less from too far,
			// spread less and mind the edges more; nobody is perfect
			assertTrue(hi.readyToHit > lo.readyToHit && hi.clickRate > lo.clickRate && hi.earlyReach < lo.earlyReach, hi.id());
			assertTrue(hi.readyJitter < lo.readyJitter && hi.edgeCare > lo.edgeCare, hi.id());
			AimModel.Params ap = AimModel.Params.of(hi), lp = AimModel.Params.of(lo);
			assertTrue(ap.lagTicks() <= lp.lagTicks() && ap.wobble() < lp.wobble() && ap.maxTurn() > lp.maxTurn(), hi.id());
		}
		assertTrue(Difficulty.MASTER.clickRate < 1 && Difficulty.MASTER.edgeCare < 1 && Difficulty.MASTER.readyJitter > 0);
		assertTrue(Difficulty.MASTER.reactionTicks >= 2, "never faster than a quick human");
		assertEquals(Difficulty.CASUAL, Difficulty.of(BotLevel.EASY));
		assertEquals(Difficulty.EXPERT, Difficulty.of(BotLevel.HARD));
		assertEquals(Difficulty.INTERMEDIATE, Difficulty.parse("normal", Difficulty.BEGINNER));
		assertEquals(Difficulty.MASTER, Difficulty.parse("master", Difficulty.BEGINNER));
	}

	@Test
	void archetypeForASeedIsStable() {
		List<Archetype> pool = List.of(Archetype.values());
		assertEquals(Archetype.forSeed(42, pool), Archetype.forSeed(42, pool));
		java.util.Set<Archetype> seen = new java.util.HashSet<>();
		for (long s = 0; s < 400; s++) seen.add(Archetype.forSeed(s, pool));
		assertEquals(pool.size(), seen.size(), "every archetype is handed out");
		assertFalse(seen.contains(null));
	}
}
