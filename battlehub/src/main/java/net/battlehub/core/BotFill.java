package net.battlehub.core;

import java.util.Collection;
import java.util.Locale;

/**
 * How many bots a lobby gets, without Minecraft (so it can be tested on its own). Every player says what they want
 * (a {@link Wish}): the server's standard (the lobby filled up to {@code bots.partyFillTo} after a wait), the lobby
 * filled to the game's maximum, or a number of bots. In a party lobby the biggest wish of the people in it counts;
 * a private game takes its owner's.
 */
public final class BotFill {
	private BotFill() {}

	public enum Kind {
		/** The server decides: up to bots.partyFillTo players, after bots.fillAfterSeconds. */
		STANDARD,
		/** As many bots as the game takes. */
		FILL,
		/** This many bots. */
		COUNT,
		/** No bots (private games). */
		NONE
	}

	/** The most bots one player can ask for by number (the game's maximum and bots.maxBots still apply). */
	public static final int MAX_COUNT = 15;

	public record Wish(Kind kind, int count) {
		public static final Wish STANDARD = new Wish(Kind.STANDARD, 0);
		public static final Wish FILL = new Wish(Kind.FILL, 0);
		public static final Wish NONE = new Wish(Kind.NONE, 0);

		public static Wish count(int n) {
			return n <= 0 ? NONE : new Wish(Kind.COUNT, Math.min(MAX_COUNT, n));
		}

		/** "", "standard", "fill", "none" or a number; anything else is the standard. */
		public static Wish parse(String s) {
			if (s == null) return STANDARD;
			String t = s.trim().toLowerCase(Locale.ROOT);
			switch (t) {
				case "fill":
					return FILL;
				case "none":
				case "0":
					return NONE;
				default:
					try {
						return count(Integer.parseInt(t));
					} catch (NumberFormatException e) {
						return STANDARD;
					}
			}
		}

		/** How it is stored ("" for the standard). */
		public String id() {
			return switch (kind) {
				case STANDARD -> "";
				case FILL -> "fill";
				case NONE -> "none";
				case COUNT -> Integer.toString(count);
			};
		}

		/** The next wish of a menu button: standard, 1, 2, ... up to {@code most} bots, fill, and round again. */
		public Wish next(int most) {
			int top = Math.max(1, Math.min(MAX_COUNT, most));
			return switch (kind) {
				case STANDARD, NONE -> count(1);
				case COUNT -> count >= top ? FILL : count(count + 1);
				case FILL -> STANDARD;
			};
		}

		/** The one before (right-click): the other way round. */
		public Wish previous(int most) {
			int top = Math.max(1, Math.min(MAX_COUNT, most));
			return switch (kind) {
				case STANDARD, NONE -> FILL;
				case FILL -> count(top);
				case COUNT -> count <= 1 ? STANDARD : count(Math.min(top, count - 1));
			};
		}
	}

	/** How big a party lobby is filled with bots, and whether at once (someone asked) or after the wait (the standard). */
	public record Target(int size, boolean now) {}

	/**
	 * The size a party lobby is filled to: with a lobby-filling wish the game's maximum; with numbers the people
	 * plus the biggest number (at least the game's minimum, so it can start); else the standard.
	 *
	 * @param wishes   what the people in the lobby want
	 * @param humans   people in the lobby
	 * @param standard bots.partyFillTo, already within the game's limits
	 */
	public static Target party(Collection<Wish> wishes, int humans, int min, int max, int standard) {
		int most = -1;
		for (Wish w : wishes) {
			if (w.kind() == Kind.FILL) return new Target(max, true);
			if (w.kind() == Kind.COUNT) most = Math.max(most, w.count());
		}
		if (most > 0) return new Target(Math.min(max, Math.max(min, humans + most)), true);
		return new Target(Math.min(max, Math.max(min, standard)), false);
	}

	/** The bots a private game gets at its start: none, a number, or as many as fit (the standard counts as none). */
	public static int custom(Wish w, int humans, int max) {
		int room = Math.max(0, max - humans);
		return switch (w.kind()) {
			case FILL -> room;
			case COUNT -> Math.min(room, w.count());
			default -> 0;
		};
	}
}
