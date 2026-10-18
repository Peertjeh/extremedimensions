package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerKnowledgeTest {
	private static OpponentModel rusher() {
		OpponentModel m = new OpponentModel();
		for (int i = 0; i < 40; i++) m.observe(5, 0.15, 3, false);
		return m;
	}

	@Test
	void aFightIsRememberedAndGivesTheNextFightAHeadStart() {
		PlayerKnowledge k = new PlayerKnowledge();
		OutcomeMemory out = new OutcomeMemory();
		for (int i = 0; i < 5; i++) out.record(Tactic.SPACE, 6, 0);
		k.learn(rusher(), out);
		assertEquals(1, k.fights);
		assertTrue(k.tactics.get("space") > 0);
		OpponentModel fresh = new OpponentModel();
		OutcomeMemory freshOut = new OutcomeMemory();
		assertTrue(!fresh.known());
		k.seed(fresh, freshOut);
		assertTrue(fresh.known(), "the next bot already knows a little");
		assertTrue(fresh.aggression() > 0.4, "and remembers they rush: " + fresh.aggression());
		assertTrue(freshOut.trend(Tactic.SPACE) > 0, "and that spacing worked");
	}

	@Test
	void theAdaptiveLevelStepsAfterThreeOfFourResultsAtTheSameLevel() {
		PlayerKnowledge k = new PlayerKnowledge();
		assertNull(k.result(true, Difficulty.INTERMEDIATE));
		assertNull(k.result(true, Difficulty.INTERMEDIATE));
		PlayerKnowledge.Change up = k.result(true, Difficulty.INTERMEDIATE);
		assertNotNull(up);
		assertEquals(Difficulty.ADVANCED, up.to());
		assertEquals("advanced", k.adaptiveLevel);
		// results at the new level start counting from zero
		assertNull(k.result(false, Difficulty.ADVANCED));
		assertNull(k.result(false, Difficulty.ADVANCED));
		PlayerKnowledge.Change down = k.result(false, Difficulty.ADVANCED);
		assertNotNull(down);
		assertEquals(Difficulty.INTERMEDIATE, down.to());
		// a mixed run keeps the level
		assertNull(k.result(true, Difficulty.INTERMEDIATE));
		assertNull(k.result(false, Difficulty.INTERMEDIATE));
		assertNull(k.result(true, Difficulty.INTERMEDIATE));
	}

	@Test
	void itNeverGoesBeyondTheEndsAndStaysSmall() {
		PlayerKnowledge k = new PlayerKnowledge();
		k.adaptiveLevel = "master";
		for (int i = 0; i < 20; i++) k.result(true, Difficulty.MASTER);
		assertEquals("master", k.adaptiveLevel);
		assertEquals(PlayerKnowledge.RESULTS, k.results.size());
		k.adaptiveLevel = "beginner";
		for (int i = 0; i < 20; i++) k.result(false, Difficulty.BEGINNER);
		assertEquals("beginner", k.adaptiveLevel);
		assertEquals(8, k.record().get("beginner")[1]);
	}

	@Test
	void aHandEditedFileIsBroughtBackInRange() {
		PlayerKnowledge k = new PlayerKnowledge();
		k.distance = Double.NaN;
		k.strafeRate = 999;
		k.fights = -4;
		k.results = new java.util.ArrayList<>(java.util.List.of("bogus", "W:expert"));
		k.adaptiveLevel = "nonsense";
		k.tactics = null;
		k.clean();
		assertEquals(4, k.distance, 1e-9);
		assertEquals(20, k.strafeRate, 1e-9);
		assertEquals(0, k.fights);
		assertEquals(java.util.List.of("W:expert"), k.results);
		assertEquals("intermediate", k.adaptiveLevel);
		assertTrue(k.tactics.isEmpty());
	}
}
