package net.battlehub.core.ai;

import java.util.EnumMap;
import java.util.Map;

/**
 * How well each tactic worked lately: damage dealt minus damage taken while it was active, as a rolling average.
 * An adaptable NPC drops a tactic that keeps costing more than it brings. Bounded (one number per tactic) and
 * reset at the start of every session.
 */
public final class OutcomeMemory {
	private static final double W = 0.2;
	private final Map<Tactic, Double> value = new EnumMap<>(Tactic.class);
	private final Map<Tactic, Integer> count = new EnumMap<>(Tactic.class);

	/** {@code dealt} and {@code taken} in health points while {@code t} was active. */
	public void record(Tactic t, double dealt, double taken) {
		double v = value.getOrDefault(t, 0.0);
		value.put(t, v + W * ((dealt - taken) - v));
		count.merge(t, 1, Integer::sum);
	}

	/** -1 (keeps failing) .. 1 (keeps working); 0 when unknown. */
	public double trend(Tactic t) {
		if (count.getOrDefault(t, 0) < 3) return 0;
		return Math.max(-1, Math.min(1, value.getOrDefault(t, 0.0) / 4));
	}

	public int samples(Tactic t) {
		return count.getOrDefault(t, 0);
	}

	/** Starts from what worked before against this player (another session), as at most a few samples. */
	public void seed(Tactic t, double v, int samples) {
		this.value.put(t, v);
		count.put(t, Math.min(samples, 5));
	}

	/** The raw rolling value (for saving). */
	public double value(Tactic t) {
		return value.getOrDefault(t, 0.0);
	}

	/** Takes over everything another memory holds. */
	public void copyFrom(OutcomeMemory other) {
		value.clear();
		count.clear();
		value.putAll(other.value);
		count.putAll(other.count);
	}

	public void reset() {
		value.clear();
		count.clear();
	}
}
