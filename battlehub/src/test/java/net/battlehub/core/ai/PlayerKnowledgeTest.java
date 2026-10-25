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

	/**
	 * A simulated player with a true strength plays against whatever level the adaptive setting picks: wins with the
	 * chance that strength gives, and a damage share around it. Returns the knowledge after {@code fights}.
	 */
	private static PlayerKnowledge play(double trueRating, int fights, long seed) {
		java.util.Random r = new java.util.Random(seed);
		PlayerKnowledge k = new PlayerKnowledge();
		for (int i = 0; i < fights; i++) {
			Difficulty level = Difficulty.parse(k.adaptiveLevel, Difficulty.INTERMEDIATE);
			double p = PlayerKnowledge.expected(trueRating, level);
			boolean won = r.nextDouble() < p;
			double share = Math.max(0, Math.min(1, p + r.nextGaussian() * 0.12));
			k.result(won, share, level, 1, PlayerKnowledge.start());
		}
		return k;
	}

	@Test
	void theRatingFindsHowStrongSomeoneIsAndTheLevelThatSuitsThem() {
		for (double truth : new double[] {500, 900, 1300, 1750}) {
			double err = 0;
			int fit = 0;
			for (long seed = 0; seed < 20; seed++) {
				PlayerKnowledge k = play(truth, 30, seed);
				err += Math.abs(k.rating - truth);
				double chance = PlayerKnowledge.expected(truth, Difficulty.parse(k.adaptiveLevel, null));
				// the level it ends at gives the player a real fight: not a walkover, not hopeless
				if (chance > 0.3 && chance < 0.85) fit++;
			}
			assertTrue(err / 20 < 170, truth + ": off by " + err / 20);
			assertTrue(fit >= 17, truth + ": a fitting level in " + fit + " of 20");
		}
	}

	@Test
	void everyFightCountsWeightedByHowMuchItSays() {
		PlayerKnowledge duel = new PlayerKnowledge(), party = new PlayerKnowledge();
		duel.result(true, 0.7, Difficulty.INTERMEDIATE, 1, 1050);
		party.result(true, 0.7, Difficulty.INTERMEDIATE, 0.5, 1050);
		assertEquals(duel.rating - 1050, 2 * (party.rating - 1050), 1e-6);
		// a close loss costs less than a beating
		PlayerKnowledge close = new PlayerKnowledge(), beaten = new PlayerKnowledge();
		close.result(false, 0.45, Difficulty.INTERMEDIATE, 1, 1050);
		beaten.result(false, 0.05, Difficulty.INTERMEDIATE, 1, 1050);
		assertTrue(close.rating > beaten.rating);
		// someone never rated starts from what is known of them (ranked results)
		PlayerKnowledge ranked = new PlayerKnowledge();
		ranked.result(true, 0.5, Difficulty.EXPERT, 1, 1400);
		assertTrue(ranked.rating > 1400);
	}

	@Test
	void itStepsUpAfterWinsDownAfterLossesAndSaysWhy() {
		PlayerKnowledge k = new PlayerKnowledge();
		PlayerKnowledge.Change up = null;
		for (int i = 0; i < 8 && up == null; i++) up = k.result(true, 0.8, Difficulty.parse(k.adaptiveLevel, null), 1, 1050);
		assertNotNull(up);
		assertTrue(up.to().ordinal() > up.from().ordinal() && up.after() > up.before());
		assertTrue(up.chance() > 0.4 && up.chance() < 0.7, "expected win at the new level " + up.chance());
		PlayerKnowledge.Change down = null;
		for (int i = 0; i < 12 && down == null; i++) down = k.result(false, 0.2, Difficulty.parse(k.adaptiveLevel, null), 1, 1050);
		assertNotNull(down);
		assertTrue(down.to().ordinal() < down.from().ordinal());
	}

	@Test
	void itNeverGoesBeyondTheEndsAndStaysSmall() {
		PlayerKnowledge k = new PlayerKnowledge();
		for (int i = 0; i < 40; i++) k.result(true, 1, Difficulty.parse(k.adaptiveLevel, null), 1, 1050);
		assertEquals("master", k.adaptiveLevel);
		assertEquals(PlayerKnowledge.RESULTS, k.results.size());
		for (int i = 0; i < 80; i++) k.result(false, 0, Difficulty.parse(k.adaptiveLevel, null), 1, 1050);
		assertEquals("beginner", k.adaptiveLevel);
		assertTrue(k.rating >= 300);
	}

	@Test
	void howTheyClickIsRememberedToo() {
		OpponentModel spammer = new OpponentModel();
		for (int t = 0; t < 60; t += 6) spammer.swing(t);
		assertTrue(spammer.spams());
		PlayerKnowledge k = new PlayerKnowledge();
		k.learn(spammer, null);
		OpponentModel next = new OpponentModel();
		k.seed(next, null);
		assertTrue(next.spams(), "the next bot knows they spam: gap " + next.swingGap());
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
		k.rating = Double.POSITIVE_INFINITY;
		k.accuracy = -3;
		k.clean();
		assertEquals(PlayerKnowledge.start(), k.rating, 1e-9);
		assertEquals(0, k.accuracy, 1e-9);
		assertEquals(4, k.distance, 1e-9);
		assertEquals(20, k.strafeRate, 1e-9);
		assertEquals(0, k.fights);
		assertEquals(java.util.List.of("W:expert"), k.results);
		assertEquals("intermediate", k.adaptiveLevel);
		assertTrue(k.tactics.isEmpty());
	}

	/**
	 * The measured scale (ML phase 3E): the levels as the mod plays them, rated in the simulation with even steps; the
	 * mace's scripted bot lower; a rating stored on the old guessed scale moves to the measured one once, level for level.
	 */
	@Test
	void theLevelsSitWhereTheyWereMeasuredAndOldRatingsMoveOnce() {
		double step = (PlayerKnowledge.LEARNED[5] - PlayerKnowledge.LEARNED[0]) / 5;
		for (int i = 1; i < 6; i++) {
			double d = PlayerKnowledge.LEARNED[i] - PlayerKnowledge.LEARNED[i - 1];
			// within a fifth of the even step in the league; a little more after rounding to tens
			assertTrue(Math.abs(d - step) <= step * 0.25, "step " + i + ": " + d);
			assertTrue(PlayerKnowledge.MACE[i] > PlayerKnowledge.MACE[i - 1]);
			assertTrue(PlayerKnowledge.MACE[i] < PlayerKnowledge.LEARNED[i], "the mace bot is weaker at " + Difficulty.values()[i]);
		}
		// one level up: about a quarter fewer wins
		double atInter = PlayerKnowledge.expected(PlayerKnowledge.levelRating(Difficulty.INTERMEDIATE), Difficulty.ADVANCED);
		assertTrue(atInter > 0.15 && atInter < 0.35, "against the next level up " + atInter);
		// beating the mace master says less than beating the learned master
		PlayerKnowledge sword = new PlayerKnowledge(), mace = new PlayerKnowledge();
		sword.result(true, 0.6, Difficulty.MASTER, 1, 1100);
		mace.result(true, 0.6, Difficulty.MASTER, 1, 1100, true);
		assertTrue(sword.rating > mace.rating + 20, sword.rating + " vs " + mace.rating);
		// a rating of before 1.48 (intermediate was 1100 then) moves to where intermediate is now, once
		PlayerKnowledge old = new PlayerKnowledge();
		old.rated = 12;
		old.rating = 1100;
		old.clean();
		assertEquals(PlayerKnowledge.levelRating(Difficulty.INTERMEDIATE), old.rating, 1e-9);
		assertEquals(PlayerKnowledge.SCALE, old.scale);
		old.clean();
		assertEquals(PlayerKnowledge.levelRating(Difficulty.INTERMEDIATE), old.rating, 1e-9);
		// halfway between two levels stays halfway, and beyond the ends it goes on with the end's step
		assertEquals((PlayerKnowledge.LEARNED[3] + PlayerKnowledge.LEARNED[4]) / 2, PlayerKnowledge.moved(1400), 1e-9);
		assertTrue(PlayerKnowledge.moved(1900) > PlayerKnowledge.LEARNED[5]);
		// someone new is rated from the start on the new scale
		PlayerKnowledge fresh = new PlayerKnowledge();
		fresh.result(true, 0.6, Difficulty.INTERMEDIATE, 1, PlayerKnowledge.start());
		assertEquals(PlayerKnowledge.SCALE, fresh.scale);
	}

	/** The scale is the one measured: the ratings written into difficulties_v3.json by ml/train/levels.py scale. */
	@Test
	void theScaleIsTheOneInTheDifficultiesFile() throws Exception {
		String json;
		try (java.io.InputStream in = PlayerKnowledgeTest.class.getResourceAsStream("/assets/battlehub/ml/difficulties_v3.json")) {
			assertNotNull(in);
			json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		java.util.Map<String, Object> levels = net.battlehub.core.ml.MiniJson.object(net.battlehub.core.ml.MiniJson.parse(json));
		for (Difficulty d : Difficulty.values()) {
			java.util.Map<String, Object> lv = net.battlehub.core.ml.MiniJson.object(levels.get(d.id()));
			assertEquals(((Number) lv.get("rating")).doubleValue(), PlayerKnowledge.levelRating(d), 1e-9, d.id());
			assertEquals(((Number) lv.get("rating_mace")).doubleValue(), PlayerKnowledge.levelRating(d, true), 1e-9, d.id());
		}
	}
}
