package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class DeciderTest {
	private static final Personality STEADY = new Personality(0.5, 0.5, 0.5, 3, 0.5, 0, 0.5, 0.5);

	/** No noise and no misjudgements: only the rules of the decider itself. */
	private static Random calm() {
		return new Random() {
			@Override
			public double nextDouble() {
				return 0.5;
			}
		};
	}

	private static Score s(Tactic t, double v) {
		return new Score(t, v, false, List.of("test"));
	}

	@Test
	void itDoesNotDitherBetweenCloseOptions() {
		Decider d = new Decider();
		Random r = new Random(1);
		d.decide(0, List.of(s(Tactic.STRIKE, 0.80), s(Tactic.SPACE, 0.70)), STEADY, Difficulty.MASTER, r);
		int switches = 0;
		for (int tick = 1; tick < 200; tick++) {
			// the two options swap places by a hair every tick
			double wobble = (tick % 2 == 0 ? 0.03 : -0.03);
			Decider.Decision dec = d.decide(tick, List.of(s(Tactic.STRIKE, 0.75 + wobble), s(Tactic.SPACE, 0.75 - wobble)), STEADY, Difficulty.MASTER, r);
			if (dec.changed()) switches++;
		}
		assertEquals(0, switches);
	}

	@Test
	void aClearlyBetterOptionWinsAfterTheCommitment() {
		Decider d = new Decider();
		Random r = calm();
		d.decide(0, List.of(s(Tactic.APPROACH, 0.8)), STEADY, Difficulty.MASTER, r);
		Decider.Decision early = d.decide(2, List.of(s(Tactic.APPROACH, 0.6), s(Tactic.STRIKE, 0.75)), STEADY, Difficulty.MASTER, r);
		assertEquals(Tactic.APPROACH, early.tactic(), "within the commitment a small gain is not enough");
		Decider.Decision later = d.decide(40, List.of(s(Tactic.APPROACH, 0.6), s(Tactic.STRIKE, 0.75)), STEADY, Difficulty.MASTER, r);
		assertEquals(Tactic.STRIKE, later.tactic());
	}

	@Test
	void safetyInterruptsAtOnce() {
		Decider d = new Decider();
		Random r = new Random(1);
		d.decide(0, List.of(s(Tactic.STRIKE, 0.9)), STEADY, Difficulty.BEGINNER, r);
		Decider.Decision dec = d.decide(1, List.of(s(Tactic.STRIKE, 0.95), new Score(Tactic.RECOVER, 1.3, true, List.of("edge"))), STEADY,
				Difficulty.BEGINNER, r);
		assertEquals(Tactic.RECOVER, dec.tactic());
		assertTrue(dec.reason().startsWith("interrupt"));
	}

	@Test
	void mistakesNeverReplaceSafety() {
		Random r = new Random(7);
		Personality wild = new Personality(0.5, 0.5, 0.5, 3, 0.5, 1, 0.5, 0.5);
		for (int i = 0; i < 2000; i++) {
			Decider d = new Decider();
			Decider.Decision dec = d.decide(0, List.of(new Score(Tactic.RECOVER, 1.3, true, List.of("edge")), s(Tactic.STRIKE, 1.25),
					s(Tactic.APPROACH, 1.2)), wild, Difficulty.BEGINNER, r);
			assertEquals(Tactic.RECOVER, dec.tactic());
		}
	}

	@Test
	void lowerLevelsMisjudgeMoreOften() {
		int beginner = 0, master = 0;
		Random r = new Random(3);
		for (int i = 0; i < 4000; i++) {
			if (new Decider().decide(0, List.of(s(Tactic.STRIKE, 0.9), s(Tactic.SPACE, 0.7)), STEADY, Difficulty.BEGINNER, r).tactic() != Tactic.STRIKE) beginner++;
			if (new Decider().decide(0, List.of(s(Tactic.STRIKE, 0.9), s(Tactic.SPACE, 0.7)), STEADY, Difficulty.MASTER, r).tactic() != Tactic.STRIKE) master++;
		}
		assertTrue(beginner > 1000 && beginner < 1800, "beginner " + beginner);
		assertTrue(master < 100, "master " + master);
	}

	@Test
	void switchesAreRememberedWithReasonsAndBounded() {
		Decider d = new Decider();
		Random r = new Random(1);
		Tactic[] cycle = {Tactic.APPROACH, Tactic.STRIKE, Tactic.RETREAT};
		for (int i = 0; i < 30; i++) {
			d.decide(i * 100, List.of(s(cycle[i % 3], 2.0)), STEADY, Difficulty.MASTER, r);
		}
		assertEquals(12, d.transitions().size());
		assertTrue(d.transitions().stream().allMatch(t -> !t.why().isEmpty()));
	}
}
