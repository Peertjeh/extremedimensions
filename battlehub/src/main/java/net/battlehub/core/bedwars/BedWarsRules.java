package net.battlehub.core.bedwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Who is still in, who has won, and how a Bed Wars match is decided when time runs out. */
public final class BedWarsRules {
	private BedWarsRules() {}

	/** Teams for this many players: 2 to 4 teams of at most {@code maxTeamSize}; {@code wanted} > 0 forces a number. */
	public static int teamCount(int players, int wanted, int maxTeamSize) {
		if (players < 2) {
			return players;
		}
		if (wanted >= 2) {
			return Math.min(Math.min(4, wanted), players);
		}
		int size = Math.max(1, maxTeamSize);
		int needed = (players + size - 1) / size;
		int preferred = players >= 9 ? 4 : players >= 6 ? 3 : 2;
		return Math.min(players, Math.max(2, Math.min(4, Math.max(needed, preferred))));
	}

	public static List<TeamState> stillIn(List<TeamState> teams) {
		List<TeamState> out = new ArrayList<>();
		for (TeamState t : teams) {
			if (!t.isOut()) out.add(t);
		}
		return out;
	}

	/** The winner once only one team is left; null while two or more are in (or when none is). */
	public static TeamState winner(List<TeamState> teams) {
		List<TeamState> in = stillIn(teams);
		return in.size() == 1 ? in.get(0) : null;
	}

	public static boolean over(List<TeamState> teams) {
		return stillIn(teams).size() <= 1;
	}

	/**
	 * Time is up: the team with the most players still in wins, then the one with its bed, then the most final
	 * kills; null on a full tie (a draw).
	 */
	public static TeamState onTimeUp(List<TeamState> teams) {
		Comparator<TeamState> order = Comparator.<TeamState>comparingInt(t -> t.playing().size())
				.thenComparing(t -> t.bedAlive() ? 1 : 0)
				.thenComparingInt(TeamState::finalKills);
		List<TeamState> in = stillIn(teams);
		if (in.isEmpty()) {
			return null;
		}
		in.sort(order.reversed());
		if (in.size() > 1 && order.compare(in.get(0), in.get(1)) == 0) {
			return null;
		}
		return in.get(0);
	}
}
