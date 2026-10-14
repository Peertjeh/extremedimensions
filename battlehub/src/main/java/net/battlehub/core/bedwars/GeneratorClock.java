package net.battlehub.core.bedwars;

/**
 * When a generator drops its next resource. Every tier has its own interval (higher tiers are faster); a
 * generator holds at most {@code cap} items around it so idle bases do not pile up thousands of entities.
 */
public final class GeneratorClock {
	private final int[] intervals;
	private final int amount;
	private final int cap;
	private int tier;
	private int ticks;

	/** @param intervals ticks between drops for tier 1, 2, ... (at least one) */
	public GeneratorClock(int[] intervals, int amount, int cap) {
		if (intervals == null || intervals.length == 0) {
			throw new IllegalArgumentException("a generator needs at least one interval");
		}
		this.intervals = intervals.clone();
		for (int i = 0; i < this.intervals.length; i++) {
			this.intervals[i] = Math.max(1, this.intervals[i]);
		}
		this.amount = Math.max(1, amount);
		this.cap = Math.max(1, cap);
	}

	public int tier() {
		return tier + 1;
	}

	public int maxTier() {
		return intervals.length;
	}

	/** Tier 1 is the first; asking for more than there are gives the highest. */
	public void setTier(int t) {
		tier = Math.max(0, Math.min(intervals.length - 1, t - 1));
	}

	public int interval() {
		return intervals[tier];
	}

	public int ticksUntilNext() {
		return Math.max(0, interval() - ticks);
	}

	/**
	 * One tick. Returns how many items to drop now, given how many already lie around the generator (0 when it is
	 * not time yet or the generator is full).
	 */
	public int tick(int lying) {
		ticks++;
		if (ticks < interval()) {
			return 0;
		}
		ticks = 0;
		return Math.max(0, Math.min(amount, cap - lying));
	}
}
