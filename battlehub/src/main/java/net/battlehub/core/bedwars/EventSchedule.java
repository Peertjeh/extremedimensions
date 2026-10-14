package net.battlehub.core.bedwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Timed events of a Bed Wars match ("diamond generators tier II at 6:00", "sudden death at 20:00"), in order.
 * Asking for the due events advances the schedule, so every event fires exactly once.
 */
public final class EventSchedule {
	public record Event(int atSecond, String type, int value) {}

	private final List<Event> events;
	private int next;

	public EventSchedule(List<Event> events) {
		this.events = new ArrayList<>(events);
		this.events.sort(Comparator.comparingInt(Event::atSecond));
	}

	/** Events whose time has come by {@code second} (each only once). */
	public List<Event> due(int second) {
		List<Event> out = new ArrayList<>();
		while (next < events.size() && events.get(next).atSecond() <= second) {
			out.add(events.get(next++));
		}
		return out;
	}

	/** The next event still to come, or null. */
	public Event upcoming() {
		return next < events.size() ? events.get(next) : null;
	}

	public List<Event> all() {
		return List.copyOf(events);
	}
}
