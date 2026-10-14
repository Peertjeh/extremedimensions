package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Matchmaking for one against a team (1v2, 1v3). Everyone says which side they want: alone, in the team, or
 * either. Groups form in the order people joined; the lone spot goes to someone who asked for it before someone
 * who does not mind, the team spots likewise.
 */
public final class SideQueue {
	public enum Side {
		SOLO, TEAM, ANY;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Side byId(String id) {
			for (Side s : values()) {
				if (s.id().equalsIgnoreCase(id == null ? "" : id.trim())) return s;
			}
			return ANY;
		}
	}

	public record Entry(UUID id, Side side, long since) {}

	public record Group(UUID solo, List<UUID> team) {}

	private final int teamSize;
	private final List<Entry> entries = new ArrayList<>();

	public SideQueue(int teamSize) {
		this.teamSize = Math.max(1, teamSize);
	}

	public int teamSize() {
		return teamSize;
	}

	/** Joins (or changes side, keeping the place in the queue). */
	public void add(UUID id, Side side, long now) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id().equals(id)) {
				entries.set(i, new Entry(id, side, entries.get(i).since()));
				return;
			}
		}
		entries.add(new Entry(id, side, now));
	}

	public boolean remove(UUID id) {
		return entries.removeIf(e -> e.id().equals(id));
	}

	public boolean contains(UUID id) {
		return side(id) != null;
	}

	/** The side this player waits for, or null. */
	public Side side(UUID id) {
		for (Entry e : entries) {
			if (e.id().equals(id)) return e.side();
		}
		return null;
	}

	public int size() {
		return entries.size();
	}

	public int count(Side side) {
		int n = 0;
		for (Entry e : entries) {
			if (e.side() == side) n++;
		}
		return n;
	}

	public List<Entry> entries() {
		return List.copyOf(entries);
	}

	/** Takes as many complete groups (one alone, {@code teamSize} in the team) out of the queue as possible. */
	public List<Group> match() {
		List<Group> out = new ArrayList<>();
		while (true) {
			Entry solo = first(Side.SOLO, null);
			if (solo == null) {
				solo = first(Side.ANY, null);
			}
			if (solo == null) {
				break;
			}
			List<Entry> team = new ArrayList<>();
			for (Side wanted : new Side[] {Side.TEAM, Side.ANY}) {
				for (Entry e : entries) {
					if (team.size() < teamSize && e.side() == wanted && e != solo) team.add(e);
				}
			}
			if (team.size() < teamSize) {
				break;
			}
			entries.remove(solo);
			entries.removeAll(team);
			List<UUID> ids = new ArrayList<>();
			for (Entry e : team) ids.add(e.id());
			out.add(new Group(solo.id(), ids));
		}
		return out;
	}

	private Entry first(Side side, Entry except) {
		for (Entry e : entries) {
			if (e.side() == side && e != except) return e;
		}
		return null;
	}
}
