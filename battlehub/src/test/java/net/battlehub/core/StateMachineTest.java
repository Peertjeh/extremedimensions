package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class StateMachineTest {
	enum S { A, B, C, D }

	@Test
	void timedTransitionsAndListeners() {
		List<String> log = new ArrayList<>();
		StateMachine<S> m = new StateMachine<>(S.class, S.A).after(S.A, 3, S.B).after(S.B, 2, S.C)
				.onEnter((s, from) -> log.add(from + ">" + s));
		m.tick();
		m.tick();
		assertEquals(S.A, m.state());
		assertEquals(1, m.ticksLeft());
		m.tick();
		assertEquals(S.B, m.state());
		assertEquals(0, m.ticksInState());
		m.tick();
		m.tick();
		assertEquals(S.C, m.state());
		assertEquals(-1, m.ticksLeft());
		assertEquals(List.of("A>B", "B>C"), log);
	}

	@Test
	void onlyAllowedTransitions() {
		StateMachine<S> m = new StateMachine<>(S.class, S.A).allow(S.A, S.B).allow(S.B, S.D);
		assertFalse(m.go(S.C), "A may only go to B");
		assertFalse(m.go(S.A), "already A");
		assertTrue(m.go(S.B));
		assertFalse(m.go(S.A));
		assertTrue(m.go(S.D));
		assertTrue(m.go(S.A), "no rule for D: anything goes");
	}
}
