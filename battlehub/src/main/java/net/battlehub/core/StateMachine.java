package net.battlehub.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small finite state machine for the stages inside a game: one state at a time, ticks counted per state,
 * optional allowed transitions, timed transitions and listeners on entering a state. Keeping every stage change
 * in one place makes the game logic easy to follow and to test.
 */
public final class StateMachine<S extends Enum<S>> {
	/** Called after the state changed. */
	public interface Listener<S> {
		void entered(S state, S from);
	}

	private record Timed<S>(int ticks, S next) {}

	private final Class<S> type;
	private final Map<S, Set<S>> allowed;
	private final Map<S, Timed<S>> timed;
	private final List<Listener<S>> listeners = new ArrayList<>();
	private S state;
	private int ticks;

	public StateMachine(Class<S> type, S initial) {
		this.type = type;
		this.allowed = new EnumMap<>(type);
		this.timed = new EnumMap<>(type);
		this.state = initial;
	}

	public S state() {
		return state;
	}

	public boolean is(S s) {
		return state == s;
	}

	public int ticksInState() {
		return ticks;
	}

	/** Only these states may follow {@code from} (without a rule for {@code from}, any state may). */
	@SafeVarargs
	public final StateMachine<S> allow(S from, S... to) {
		Set<S> set = allowed.computeIfAbsent(from, k -> EnumSet.noneOf(type));
		for (S s : to) set.add(s);
		return this;
	}

	/** After {@code ticks} ticks in {@code from}, move on to {@code next} by itself. */
	public StateMachine<S> after(S from, int ticks, S next) {
		timed.put(from, new Timed<>(Math.max(0, ticks), next));
		return allow(from, next);
	}

	public StateMachine<S> onEnter(Listener<S> listener) {
		listeners.add(listener);
		return this;
	}

	public boolean canGo(S next) {
		if (next == state) {
			return false;
		}
		Set<S> ok = allowed.get(state);
		return ok == null || ok.contains(next);
	}

	/** Changes the state; false when it is not allowed (or already the state). */
	public boolean go(S next) {
		if (!canGo(next)) {
			return false;
		}
		S from = state;
		state = next;
		ticks = 0;
		for (Listener<S> l : List.copyOf(listeners)) {
			l.entered(next, from);
		}
		return true;
	}

	/** One tick; a timed state moves on when its time is up. */
	public void tick() {
		ticks++;
		Timed<S> t = timed.get(state);
		if (t != null && ticks >= t.ticks()) {
			go(t.next());
		}
	}

	/** Ticks until a timed state moves on, -1 for a state without a timer. */
	public int ticksLeft() {
		Timed<S> t = timed.get(state);
		return t == null ? -1 : Math.max(0, t.ticks() - ticks);
	}
}
