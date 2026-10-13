package net.battlehub.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Menus;
import net.battlehub.session.Sessions;
import net.minecraft.server.MinecraftServer;

/** All running matches. Every match is its own instance in its own arena slot. */
public final class Matches {
	private Matches() {}

	private static final Map<Integer, Match> MATCHES = new LinkedHashMap<>();
	private static int nextId = 1;

	public static int newId() {
		return nextId++;
	}

	/** Starts building the match; false when no arena slot is free. */
	public static boolean start(Match m) {
		if (!m.begin()) {
			return false;
		}
		MATCHES.put(m.id, m);
		BattleHub.LOGGER.info("Match #{} started: {} ({} players{}) in slot {} ({})", m.id, m.mode.id(), m.participants().size(),
				m.ranked ? ", ranked" : "", m.arena().slot(), m.arena().theme().id());
		Menus.refreshAll(BattleHub.server());
		return true;
	}

	public static void tick() {
		for (Match m : new ArrayList<>(MATCHES.values())) {
			try {
				m.tick();
			} catch (Exception e) {
				BattleHub.LOGGER.error("Match #{} crashed; ending it and restoring its players", m.id, e);
				try {
					m.abort("match.cancelled_error");
				} catch (Exception e2) {
					BattleHub.LOGGER.error("Could not abort match #{}", m.id, e2);
				}
			}
			if (m.phase() == Match.Phase.CLOSED) {
				MATCHES.remove(m.id);
			}
		}
	}

	public static Match get(int id) {
		return MATCHES.get(id);
	}

	/** The match this player plays in or watches. */
	public static Match of(UUID player) {
		Sessions.Session s = Sessions.get(player);
		Match m = MATCHES.get(s.matchId);
		return m != null && m.involves(player) ? m : null;
	}

	public static List<Match> all() {
		return new ArrayList<>(MATCHES.values());
	}

	/** Matches people can watch right now (countdown or running). */
	public static List<Match> watchable() {
		List<Match> out = new ArrayList<>();
		for (Match m : MATCHES.values()) {
			if (m.canWatch()) {
				out.add(m);
			}
		}
		return out;
	}

	public static int playing(GameMode mode) {
		int n = 0;
		for (Match m : MATCHES.values()) {
			if (m.mode == mode && m.phase() != Match.Phase.CLOSED) {
				n++;
			}
		}
		return n;
	}

	public static int count() {
		return MATCHES.size();
	}

	/** Server stop or reload: end everything without results and give everybody their things back. */
	public static void shutdown(MinecraftServer server) {
		if (!MATCHES.isEmpty()) {
			BattleHub.LOGGER.info("Server stopping: ending {} running match(es) and restoring their players", MATCHES.size());
		}
		for (Match m : new ArrayList<>(MATCHES.values())) {
			try {
				m.abort("match.cancelled_shutdown");
			} catch (Exception e) {
				BattleHub.LOGGER.error("Could not stop match #{}", m.id, e);
			}
		}
		MATCHES.clear();
	}

	public static boolean end(int id) {
		Match m = MATCHES.get(id);
		if (m == null) {
			return false;
		}
		m.abort("match.cancelled_admin");
		MATCHES.remove(id);
		return true;
	}
}
