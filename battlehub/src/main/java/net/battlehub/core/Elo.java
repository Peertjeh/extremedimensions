package net.battlehub.core;

/** Classic ELO rating maths. Only ranked duels call this. */
public final class Elo {
	private Elo() {}

	/** Probability that a player rated {@code ra} beats one rated {@code rb}. */
	public static double expected(int ra, int rb) {
		return 1.0 / (1.0 + Math.pow(10.0, (rb - ra) / 400.0));
	}

	/**
	 * Rating change for a player rated {@code ra} against {@code rb}.
	 *
	 * @param score 1 for a win, 0.5 for a draw, 0 for a loss
	 */
	public static int delta(int ra, int rb, double score, int k) {
		return (int) Math.round(k * (score - expected(ra, rb)));
	}

	/** New ratings {winner, loser} after a decisive game; ratings never drop below {@code floor}. */
	public static int[] afterWin(int winner, int loser, int k, int floor) {
		int gain = delta(winner, loser, 1.0, k);
		int loss = delta(loser, winner, 0.0, k);
		return new int[] {Math.max(floor, winner + gain), Math.max(floor, loser + loss)};
	}
}
