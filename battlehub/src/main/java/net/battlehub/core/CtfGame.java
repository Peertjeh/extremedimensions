package net.battlehub.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capture The Flag rules without any world: the Minecraft side reports who touched which flag or base,
 * this class decides what happens. A capture only counts while your own flag is at home.
 */
public final class CtfGame {
	public enum Team {
		RED, BLUE;

		public Team other() {
			return this == RED ? BLUE : RED;
		}
	}

	public enum FlagState { HOME, CARRIED, DROPPED }

	public enum EventType { NONE, PICKED_UP, RETURNED, CAPTURED, DROPPED, AUTO_RETURNED, WON }

	public record Event(EventType type, Team flag, UUID player) {
		public static final Event NONE = new Event(EventType.NONE, null, null);
	}

	private static final class Flag {
		FlagState state = FlagState.HOME;
		UUID carrier;
		long droppedAt;
	}

	private final Map<UUID, Team> teams = new LinkedHashMap<>();
	private final Map<Team, Flag> flags = new EnumMap<>(Team.class);
	private final Map<Team, Integer> captures = new EnumMap<>(Team.class);
	private final int capturesToWin;
	private Team winner;

	public CtfGame(int capturesToWin) {
		this.capturesToWin = Math.max(1, capturesToWin);
		for (Team t : Team.values()) {
			flags.put(t, new Flag());
			captures.put(t, 0);
		}
	}

	/** Splits players into two teams, alternating; shuffle beforehand for random teams. */
	public void assign(List<UUID> players) {
		teams.clear();
		for (int i = 0; i < players.size(); i++) {
			teams.put(players.get(i), i % 2 == 0 ? Team.RED : Team.BLUE);
		}
	}

	public Team teamOf(UUID player) {
		return teams.get(player);
	}

	public List<UUID> members(Team team) {
		List<UUID> out = new ArrayList<>();
		teams.forEach((id, t) -> {
			if (t == team) out.add(id);
		});
		return out;
	}

	public void removePlayer(UUID player) {
		Team carried = carriedBy(player);
		if (carried != null) {
			Flag f = flags.get(carried);
			f.state = FlagState.HOME;
			f.carrier = null;
		}
		teams.remove(player);
	}

	public FlagState state(Team flag) {
		return flags.get(flag).state;
	}

	public UUID carrier(Team flag) {
		return flags.get(flag).carrier;
	}

	/** Which flag the player carries, or null. */
	public Team carriedBy(UUID player) {
		for (Map.Entry<Team, Flag> e : flags.entrySet()) {
			if (player.equals(e.getValue().carrier)) {
				return e.getKey();
			}
		}
		return null;
	}

	public int captures(Team team) {
		return captures.get(team);
	}

	public Team winner() {
		return winner;
	}

	/** A player stands on the flag of {@code flagTeam} (at home or dropped). */
	public Event touchFlag(UUID player, Team flagTeam) {
		Team team = teams.get(player);
		if (team == null || winner != null) {
			return Event.NONE;
		}
		Flag f = flags.get(flagTeam);
		if (team == flagTeam) {
			if (f.state == FlagState.DROPPED) {
				f.state = FlagState.HOME;
				f.carrier = null;
				return new Event(EventType.RETURNED, flagTeam, player);
			}
			return Event.NONE;
		}
		if (f.state == FlagState.CARRIED || carriedBy(player) != null) {
			return Event.NONE;
		}
		f.state = FlagState.CARRIED;
		f.carrier = player;
		return new Event(EventType.PICKED_UP, flagTeam, player);
	}

	/** A player stands in their own base. Scores if they carry the enemy flag and their own flag is home. */
	public Event touchBase(UUID player) {
		Team team = teams.get(player);
		if (team == null || winner != null) {
			return Event.NONE;
		}
		Team enemy = team.other();
		Flag theirs = flags.get(enemy);
		if (!player.equals(theirs.carrier) || flags.get(team).state != FlagState.HOME) {
			return Event.NONE;
		}
		theirs.state = FlagState.HOME;
		theirs.carrier = null;
		int c = captures.merge(team, 1, Integer::sum);
		if (c >= capturesToWin) {
			winner = team;
			return new Event(EventType.WON, enemy, player);
		}
		return new Event(EventType.CAPTURED, enemy, player);
	}

	/** The player died or left: a carried flag falls where they were. */
	public Event drop(UUID player, long now) {
		Team carried = carriedBy(player);
		if (carried == null) {
			return Event.NONE;
		}
		Flag f = flags.get(carried);
		f.state = FlagState.DROPPED;
		f.carrier = null;
		f.droppedAt = now;
		return new Event(EventType.DROPPED, carried, player);
	}

	/** Dropped flags go home on their own after {@code returnTicks}. */
	public List<Event> tick(long now, int returnTicks) {
		List<Event> out = new ArrayList<>();
		for (Map.Entry<Team, Flag> e : flags.entrySet()) {
			Flag f = e.getValue();
			if (f.state == FlagState.DROPPED && now - f.droppedAt >= returnTicks) {
				f.state = FlagState.HOME;
				out.add(new Event(EventType.AUTO_RETURNED, e.getKey(), null));
			}
		}
		return out;
	}

	/** Team with more captures, or null on a tie (used when time runs out). */
	public Team leading() {
		int r = captures.get(Team.RED);
		int b = captures.get(Team.BLUE);
		return r == b ? null : (r > b ? Team.RED : Team.BLUE);
	}
}
