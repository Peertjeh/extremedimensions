package net.battlehub.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every online player is doing right now. A player is in exactly one state, which is how BattleHub
 * guarantees that nobody is ever in two matches (or a match and a queue) at the same time.
 */
public final class Sessions {
	private Sessions() {}

	public enum State {
		/** Free in the lobby. */
		LOBBY,
		/** In a duel queue or party lobby; still physically in the lobby. */
		QUEUED,
		/** Playing a match (including countdown and the short end phase). */
		MATCH,
		/** Watching a match. */
		SPECTATING
	}

	public static final class Session {
		public final UUID id;
		private State state = State.LOBBY;
		/** Match id while playing or spectating, -1 otherwise. */
		public int matchId = -1;
		/** Queue key while queued (e.g. "sword", "ranked:bow", "party:sumo"). */
		public String queue = "";

		Session(UUID id) {
			this.id = id;
		}

		public State state() {
			return state;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	public static Session get(UUID id) {
		return SESSIONS.computeIfAbsent(id, Session::new);
	}

	public static State state(UUID id) {
		Session s = SESSIONS.get(id);
		return s == null ? State.LOBBY : s.state;
	}

	public static boolean isFree(UUID id) {
		return state(id) == State.LOBBY;
	}

	/** In the lobby world (free or waiting in a queue). */
	public static boolean inLobby(UUID id) {
		State s = state(id);
		return s == State.LOBBY || s == State.QUEUED;
	}

	public static void toLobby(UUID id) {
		Session s = get(id);
		s.state = State.LOBBY;
		s.matchId = -1;
		s.queue = "";
	}

	public static void toQueue(UUID id, String queue) {
		Session s = get(id);
		s.state = State.QUEUED;
		s.matchId = -1;
		s.queue = queue;
	}

	public static void toMatch(UUID id, int matchId) {
		Session s = get(id);
		s.state = State.MATCH;
		s.matchId = matchId;
		s.queue = "";
	}

	public static void toSpectating(UUID id, int matchId) {
		Session s = get(id);
		s.state = State.SPECTATING;
		s.matchId = matchId;
		s.queue = "";
	}

	public static void remove(UUID id) {
		SESSIONS.remove(id);
	}

	public static void clear() {
		SESSIONS.clear();
	}
}
