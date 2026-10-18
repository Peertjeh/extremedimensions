package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The server's think budget: slower thinking under load, a cap per tick, and the mind obeying it. */
class ThinkBudgetTest {
	@Test
	void quickTicksMeanNormalThinking() {
		ThinkBudget b = new ThinkBudget(35);
		for (int i = 0; i < 100; i++) b.tick(12, 10);
		assertEquals(1, b.stretch());
	}

	@Test
	void slowTicksStretchTheTimeBetweenThinksUpToALimit() {
		ThinkBudget b = new ThinkBudget(35);
		for (int i = 0; i < 200; i++) b.tick(44, 10);
		assertEquals(3, b.stretch());
		for (int i = 0; i < 200; i++) b.tick(120, 10);
		assertEquals(ThinkBudget.MAX_STRETCH, b.stretch());
	}

	@Test
	void itRecoversStepByStepWithoutFlapping() {
		ThinkBudget b = new ThinkBudget(35);
		for (int i = 0; i < 200; i++) b.tick(60, 10);
		int high = b.stretch();
		b.tick(10, 10);
		// one quick tick is not enough to drop (the average is still high)
		assertEquals(high, b.stretch());
		for (int i = 0; i < 300; i++) b.tick(10, 10);
		assertEquals(1, b.stretch());
		// round the threshold it does not jump up and down every tick
		ThinkBudget c = new ThinkBudget(35);
		int changes = 0, last = 1;
		for (int i = 0; i < 400; i++) {
			c.tick(i % 2 == 0 ? 33 : 39, 10);
			if (c.stretch() != last) changes++;
			last = c.stretch();
		}
		assertTrue(changes <= 2, changes + " changes");
	}

	@Test
	void thinksPerTickAreCappedAndCounted() {
		ThinkBudget b = new ThinkBudget(35);
		b.tick(10, 40);
		int yes = 0;
		for (int i = 0; i < 100; i++) if (b.allow()) yes++;
		assertEquals(20, yes);
		assertEquals(20, b.granted());
		assertEquals(80, b.deferred());
		// few bots: still a few thinks per tick
		b.tick(10, 1);
		assertTrue(b.allow() && b.allow() && b.allow());
		assertFalse(b.allow());
	}

	@Test
	void aMindKeepsItsPlanWhenTheBudgetSaysNoButDangerStillCounts() {
		CombatMind mind = new CombatMind(Archetype.BALANCED, Archetype.BALANCED.base, Difficulty.INTERMEDIATE, 1);
		boolean[] open = {true};
		mind.gate(() -> open[0]);
		Situation s = Fixtures.at(0, Fixtures.me(new V3(0, 64, 0), 20, 1), Fixtures.foe(new V3(6, 64, 0), 20), Fixtures.open());
		mind.see(0, Fixtures.FOE, new V3(6, 64, 0), 6, false);
		mind.tick(s);
		int before = mind.thinks();
		open[0] = false;
		for (int t = 1; t < 60; t++) {
			mind.see(t, Fixtures.FOE, new V3(6, 64, 0), 6, false);
			mind.tick(Fixtures.at(t, Fixtures.me(new V3(0, 64, 0), 20, 1), Fixtures.foe(new V3(6, 64, 0), 20), Fixtures.open()));
		}
		assertEquals(before, mind.thinks());
		assertTrue(mind.deferred() > 0);
		// an edge right behind with the target close: it thinks anyway
		mind.see(60, Fixtures.FOE, new V3(1.5, 64, 0), 1.5, false);
		mind.tick(Fixtures.at(60, Fixtures.me(new V3(0, 64, 0), 20, 1), Fixtures.foe(new V3(1.5, 64, 0), 20),
				Fixtures.dropAt(new V3(-1, 0, 0), 0.5)));
		assertEquals(before + 1, mind.thinks());
	}

	@Test
	void stretchMakesTheMindThinkLessOften() {
		int normal = thinksIn(1), slow = thinksIn(3);
		assertTrue(slow * 2 < normal, normal + " vs " + slow);
	}

	private static int thinksIn(int stretch) {
		CombatMind mind = new CombatMind(Archetype.BALANCED, Archetype.BALANCED.base, Difficulty.INTERMEDIATE, 1);
		mind.stretch(stretch);
		for (int t = 0; t < 300; t++) {
			mind.see(t, Fixtures.FOE, new V3(8, 64, 0), 8, false);
			mind.tick(Fixtures.at(t, Fixtures.me(new V3(0, 64, 0), 20, 1), Fixtures.foe(new V3(8, 64, 0), 20), Fixtures.open()));
		}
		return mind.thinks();
	}
}
