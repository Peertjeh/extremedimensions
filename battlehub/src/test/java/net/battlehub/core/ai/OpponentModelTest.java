package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpponentModelTest {
	/** The NPC walks up to someone who stands still, or stands still while someone walks up to it. */
	private static double aggressionSeen(boolean npcWalks) {
		CombatMind mind = new CombatMind(Archetype.BALANCED, Archetype.BALANCED.base, Difficulty.MASTER, 1);
		double me = 0, foe = 12;
		for (int tick = 0; tick < 200; tick++) {
			if (npcWalks) {
				me = Math.min(foe - 3, me + 0.1);
			} else {
				foe = Math.max(me + 3, foe - 0.1);
			}
			V3 mePos = new V3(me, 64, 0), foePos = new V3(foe, 64, 0);
			mind.see(tick, Fixtures.FOE, foePos, foe - me, false);
			mind.tick(new Situation(tick, new Situation.Self(mePos, V3.ZERO, 20, 20, true, 1, false, false, 3.0),
					new Situation.Target(Fixtures.FOE, foePos, V3.ZERO, 20, 20, true, 1, 100, true), null, Fixtures.open(), Situation.Threat.NONE));
		}
		return mind.model(Fixtures.FOE).aggression();
	}

	@Test
	void walkingUpToSomeoneDoesNotMakeThemAggressive() {
		double standing = aggressionSeen(true);
		double rushing = aggressionSeen(false);
		assertTrue(rushing > standing + 0.25, "rushing " + rushing + ", standing " + standing);
	}

	@Test
	void theAveragesStayBoundedAndCanBeReset() {
		OpponentModel m = new OpponentModel();
		for (int i = 0; i < 10_000; i++) m.observe(1, 5, 50, i % 2 == 0);
		assertTrue(m.aggression() <= 1 && m.aggression() >= 0);
		assertTrue(m.predictability() >= 0 && m.predictability() <= 1);
		m.reset();
		assertTrue(!m.known() && m.strafeRate() == 0);
	}
}
