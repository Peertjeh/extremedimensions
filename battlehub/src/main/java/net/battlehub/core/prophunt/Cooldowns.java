package net.battlehub.core.prophunt;

import java.util.HashMap;
import java.util.Map;

/** Per-key cooldowns counted in ticks (an ability per player, for example). */
public final class Cooldowns<K> {
	private final Map<K, Long> readyAt = new HashMap<>();

	public boolean ready(K key, long now) {
		return now >= readyAt.getOrDefault(key, Long.MIN_VALUE);
	}

	/** Uses the ability if it is ready: true and starts the cooldown; false while it is cooling down. */
	public boolean use(K key, long now, int cooldownTicks) {
		if (!ready(key, now)) {
			return false;
		}
		readyAt.put(key, now + Math.max(0, cooldownTicks));
		return true;
	}

	public long ticksLeft(K key, long now) {
		return Math.max(0, readyAt.getOrDefault(key, Long.MIN_VALUE) - now);
	}

	public void reset(K key) {
		readyAt.remove(key);
	}

	public void clear() {
		readyAt.clear();
	}
}
