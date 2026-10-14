package net.battlehub.core;

import java.util.Locale;

/** How many rounds a duel has: one round, or the first to two round wins. */
public enum DuelFormat {
	BO1(1),
	BO3(2);

	private final int roundsToWin;

	DuelFormat(int roundsToWin) {
		this.roundsToWin = roundsToWin;
	}

	public int roundsToWin() {
		return roundsToWin;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public DuelFormat other() {
		return this == BO1 ? BO3 : BO1;
	}

	/** "bo1"/"bo3" (also "1"/"3"); anything else is best of 1. */
	public static DuelFormat byId(String id) {
		if (id == null) {
			return BO1;
		}
		String s = id.trim().toLowerCase(Locale.ROOT);
		return s.equals("bo3") || s.equals("3") ? BO3 : BO1;
	}

	/** Who has won the series after these round wins, or null while it is still going. */
	public int winnerIndex(int winsA, int winsB) {
		if (winsA >= roundsToWin) {
			return 0;
		}
		if (winsB >= roundsToWin) {
			return 1;
		}
		return -1;
	}
}
