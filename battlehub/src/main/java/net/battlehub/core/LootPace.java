package net.battlehub.core;

import java.util.Random;

/**
 * How fast a bot loots a chest, like a player at its level (without Minecraft, so it can be tested on its own): a
 * moment after opening before the first click, then one shift-click per stack, and after closing the chest one armor
 * piece at a time (open the inventory, find it, shift-click). Lower levels are slower; every step varies a little.
 */
public final class LootPace {
	private LootPace() {}

	/** Ticks per step at one level: before the first click, per stack, per armor piece. */
	public record Pace(int open, int item, int equip) {}

	// beginner .. master
	private static final int[] OPEN = {14, 12, 10, 9, 8, 7};
	private static final int[] ITEM = {10, 8, 7, 6, 5, 4};
	private static final int[] EQUIP = {30, 26, 22, 18, 15, 12};

	/** The pace of a level (0 beginner .. 5 master; others are clamped). */
	public static Pace of(int level) {
		int i = Math.max(0, Math.min(OPEN.length - 1, level));
		return new Pace(OPEN[i], ITEM[i], EQUIP[i]);
	}

	/** One step of {@code base} ticks, a little longer or shorter (about a quarter either way), never under 2. */
	public static int vary(int base, Random rng) {
		int spread = Math.max(1, base / 4);
		return Math.max(2, base - spread + rng.nextInt(2 * spread + 1));
	}

	/** About how long a chest with this many stacks takes to empty (ticks, without the variation). */
	public static int chestTicks(Pace p, int stacks) {
		return p.open() + p.item() * Math.max(0, stacks);
	}
}
