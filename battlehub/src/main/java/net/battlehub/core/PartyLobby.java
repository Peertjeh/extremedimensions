package net.battlehub.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Waiting room for one party game. Once the minimum is reached a countdown starts; when the room fills up the
 * countdown is shortened. Dropping below the minimum cancels it. Call {@link #tick()} once per server tick.
 */
public final class PartyLobby {
	public enum JoinResult { JOINED, ALREADY, FULL }

	public enum Event { NONE, COUNTDOWN_STARTED, COUNTDOWN, CANCELLED, START }

	/** {@code secondsLeft} is set when a whole second passes; {@code players} only on START. */
	public record Tick(Event event, int secondsLeft, List<UUID> players) {
		static final Tick NONE = new Tick(Event.NONE, -1, List.of());
	}

	private final Set<UUID> players = new LinkedHashSet<>();
	private int min;
	private int max;
	private int countdownTicks;
	private int fullCountdownTicks;
	private int remaining = -1;

	public PartyLobby(int min, int max, int countdownSeconds, int fullCountdownSeconds) {
		configure(min, max, countdownSeconds, fullCountdownSeconds);
	}

	public void configure(int min, int max, int countdownSeconds, int fullCountdownSeconds) {
		this.min = Math.max(1, min);
		this.max = Math.max(this.min, max);
		this.countdownTicks = Math.max(1, countdownSeconds) * 20;
		this.fullCountdownTicks = Math.max(1, Math.min(countdownSeconds, fullCountdownSeconds)) * 20;
	}

	public JoinResult join(UUID id) {
		if (players.contains(id)) {
			return JoinResult.ALREADY;
		}
		if (players.size() >= max) {
			return JoinResult.FULL;
		}
		players.add(id);
		return JoinResult.JOINED;
	}

	public boolean leave(UUID id) {
		return players.remove(id);
	}

	public boolean contains(UUID id) {
		return players.contains(id);
	}

	public int size() {
		return players.size();
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

	public List<UUID> players() {
		return List.copyOf(players);
	}

	public boolean counting() {
		return remaining >= 0;
	}

	public int secondsLeft() {
		return remaining < 0 ? -1 : (remaining + 19) / 20;
	}

	public Tick tick() {
		if (remaining < 0) {
			if (players.size() >= min) {
				remaining = players.size() >= max ? fullCountdownTicks : countdownTicks;
				return new Tick(Event.COUNTDOWN_STARTED, secondsLeft(), List.of());
			}
			return Tick.NONE;
		}
		if (players.size() < min) {
			remaining = -1;
			return new Tick(Event.CANCELLED, -1, List.of());
		}
		if (players.size() >= max && remaining > fullCountdownTicks) {
			remaining = fullCountdownTicks;
		}
		remaining--;
		if (remaining <= 0) {
			remaining = -1;
			List<UUID> start = new ArrayList<>(players);
			players.clear();
			return new Tick(Event.START, 0, start);
		}
		if (remaining % 20 == 0) {
			return new Tick(Event.COUNTDOWN, remaining / 20, List.of());
		}
		return Tick.NONE;
	}
}
