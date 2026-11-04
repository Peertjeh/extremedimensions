package com.extremedimensions.client.state;

/**
 * Whether the wound is after the player right now, so that leaving the game would cost them curses when they come back
 * (sent by the server, see FleeService and DangerPayload): what holds them, how long a blow holds them yet, and what
 * leaving would cost. Shown only in the pause menu (see DangerChip), never during play.
 */
public final class ClientDanger {
	private ClientDanger() {}

	/** What holds them: "swarm", "hunt", "keeper", "raid", "elder" or "blow"; "" when nothing does. */
	private static volatile String why = "";
	/** When a blow's hold ends (ms since the epoch); -1 while what hunts them lasts. */
	private static volatile long until = -1;
	private static volatile int curses = 1;

	public static void set(String what, int seconds, int cost) {
		why = what == null ? "" : what;
		curses = Math.max(0, cost);
		until = seconds < 0 ? -1 : System.currentTimeMillis() + seconds * 1000L;
	}

	public static boolean active() {
		return !why.isEmpty() && curses > 0;
	}

	public static String why() {
		return why;
	}

	public static int curses() {
		return curses;
	}

	/** Seconds a blow still holds them (-1 while what hunts them lasts). */
	public static int seconds() {
		long u = until;
		return u < 0 ? -1 : (int) Math.max(0, Math.ceil((u - System.currentTimeMillis()) / 1000.0));
	}

	public static void reset() {
		why = "";
		until = -1;
	}
}
