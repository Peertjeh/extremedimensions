package net.battlehub.core.bedwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Who is still in, who has won, and how a Bed Wars match is decided when time runs out. */
public final class BedWarsRules {
	private BedWarsRules() {}

	/** Everyone for themselves. */
	public static final String SOLO = "solo";
	/** Teams of (at most) two. */
	public static final String DUO = "duo";

	/** Players per team for a format: 1 for solo, {@code duoSize} for duo. */
	public static int teamSize(String format, int duoSize) {
		return SOLO.equals(format) ? 1 : Math.max(1, duoSize);
	}

	/**
	 * Teams for this many players with at most {@code teamSize} each: at least 2, at most {@code maxTeams} and never
	 * more than there are players. When there are more players than fit, teams grow beyond {@code teamSize}.
	 */
	public static int teamCount(int players, int teamSize, int maxTeams) {
		if (players < 2) {
			return players;
		}
		int size = Math.max(1, teamSize);
		int needed = (players + size - 1) / size;
		return Math.min(players, Math.max(2, Math.min(Math.max(2, maxTeams), needed)));
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
