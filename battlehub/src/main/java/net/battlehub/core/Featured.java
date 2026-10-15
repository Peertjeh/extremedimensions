package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The mode of the day: every day another mode from the list, going through all of them before one comes back.
 * The order is shuffled once with a fixed seed, so every server shows the same mode on the same day.
 */
public final class Featured {
	private Featured() {}

	/** The mode for this day (days since 1970-01-01), or null when the list is empty. */
	public static <T> T pick(List<T> pool, long epochDay) {
		if (pool == null || pool.isEmpty()) {
			return null;
		}
		List<T> order = new ArrayList<>(pool);
		java.util.Collections.shuffle(order, new Random(20261015L));
		return order.get((int) Math.floorMod(epochDay, (long) order.size()));
	}
}
