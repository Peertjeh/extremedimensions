package net.battlehub.core.hunger;

/**
 * How much the sponsors like a tribute (without Minecraft): points for kills, damage, time alive, the first blood,
 * an event survived and the Feast. Every threshold reached sends a silver parachute; the thresholds grow apart, so a
 * tribute gets a few gifts in a match, not a stream.
 */
public final class Favour {
	private Favour() {}

	public static final int KILL = 40;
	/** Per full ten points of damage dealt to other tributes. */
	public static final int DAMAGE_TEN = 10;
	public static final int MINUTE_ALIVE = 6;
	public static final int FIRST_BLOOD = 30;
	public static final int EVENT_SURVIVED = 12;
	public static final int FEAST = 20;

	/** The points at which the n-th gift comes (n from 0): 80, 200, 360, 560, ... */
	public static int threshold(int n) {
		return 80 + 120 * n + 40 * n * (n - 1);
	}

	/** How many gifts a tribute with these points should have had by now. */
	public static int giftsEarned(int points) {
		int n = 0;
		while (threshold(n) <= points) n++;
		return n;
	}

	/** Points still needed for the next gift, after {@code given} gifts. */
	public static int toNext(int points, int given) {
		return Math.max(0, threshold(given) - points);
	}
}
