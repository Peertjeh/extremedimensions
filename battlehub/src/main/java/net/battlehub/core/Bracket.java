package net.battlehub.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A single-elimination bracket for 2 to 16 teams, numbered 0 .. n-1 in seeding order. When the number of teams is
 * not a power of two, the first teams get a bye in round one, so every later round is a power of two.
 */
public final class Bracket {
	public record Pairing(int a, int b) {
		public boolean has(int team) {
			return a == team || b == team;
		}
	}

	private List<Integer> left;
	private final List<Integer> byes = new ArrayList<>();
	private final List<Pairing> current = new ArrayList<>();
	private final Map<Integer, Integer> winners = new HashMap<>();
	private int round;
	private int roundSize;

	public Bracket(int teams) {
		if (teams < 2) {
			throw new IllegalArgumentException("a bracket needs at least two teams");
		}
		left = new ArrayList<>();
		for (int i = 0; i < teams; i++) {
			left.add(i);
		}
	}

	/** Number of teams for this many players when teams should have {@code teamSize} players (2 to 16 teams). */
	public static int teamCount(int players, int teamSize) {
		if (players < 2) {
			return players;
		}
		return Math.max(2, Math.min(16, players / Math.max(1, teamSize)));
	}

	/** Random teams: as many as {@link #teamCount} gives, sizes differing by at most one. */
	public static <T> List<List<T>> teams(List<T> players, int teamSize, Random random) {
		List<T> pool = new ArrayList<>(players);
		Collections.shuffle(pool, random);
		int count = teamCount(pool.size(), teamSize);
		List<List<T>> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			out.add(new ArrayList<>());
		}
		for (int i = 0; i < pool.size(); i++) {
			out.get(i % count).add(pool.get(i));
		}
		return out;
	}

	private static int nextPowerOfTwo(int n) {
		int p = Integer.highestOneBit(Math.max(1, n));
		return p < n ? p << 1 : p;
	}

	/** Pairs the teams that are left; teams without an opponent this round go through (byes). */
	public List<Pairing> nextRound() {
		if (finished()) {
			throw new IllegalStateException("the bracket is finished");
		}
		round++;
		current.clear();
		winners.clear();
		byes.clear();
		roundSize = nextPowerOfTwo(left.size());
		int byeCount = roundSize - left.size();
		for (int i = 0; i < byeCount; i++) {
			byes.add(left.get(i));
		}
		for (int i = byeCount; i + 1 < left.size(); i += 2) {
			current.add(new Pairing(left.get(i), left.get(i + 1)));
		}
		return List.copyOf(current);
	}

	public void result(int pairing, int winner) {
		Pairing p = current.get(pairing);
		if (!p.has(winner)) {
			throw new IllegalArgumentException("team " + winner + " is not in " + p);
		}
		winners.put(pairing, winner);
	}

	public boolean hasResult(int pairing) {
		return winners.containsKey(pairing);
	}

	public boolean roundDone() {
		return winners.size() == current.size();
	}

	/** After the last result of a round: the byes and the winners go on, in bracket order. */
	public void advance() {
		if (!roundDone()) {
			throw new IllegalStateException("the round is not finished");
		}
		List<Integer> next = new ArrayList<>(byes);
		for (int i = 0; i < current.size(); i++) {
			next.add(winners.get(i));
		}
		left = next;
		current.clear();
		byes.clear();
	}

	public boolean finished() {
		return left.size() == 1 && current.isEmpty();
	}

	public int champion() {
		if (!finished()) {
			throw new IllegalStateException("no champion yet");
		}
		return left.get(0);
	}

	public int round() {
		return round;
	}

	/** Teams in the current round counting byes: 2 is the final, 4 the semi-finals, 8 the quarter-finals. */
	public int roundSize() {
		return roundSize;
	}

	public List<Pairing> pairings() {
		return List.copyOf(current);
	}

	public List<Integer> byes() {
		return List.copyOf(byes);
	}

	public List<Integer> teamsLeft() {
		return List.copyOf(left);
	}

	/** Rounds needed for this many teams. */
	public static int rounds(int teams) {
		return Integer.numberOfTrailingZeros(nextPowerOfTwo(teams));
	}
}
