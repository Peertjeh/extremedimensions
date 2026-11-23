package com.extremedimensions.client.fx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.extremedimensions.client.ui.Ui;

/**
 * Impact frames: when a boss's blow lands it holds still for an instant before it follows through, so the blow is
 * felt. The renderers slow a boss's action clock almost to a stop for a moment after the server says its blow landed
 * (a harder blow holds a little longer). Calm mode leaves the clock alone.
 */
public final class HitStop {
	private HitStop() {}

	/** Per entity id: when its last blow landed (ms) and how long it holds (ms). */
	private static final Map<Integer, long[]> AT = new ConcurrentHashMap<>();

	public static void mark(int entityId, float strength) {
		long hold = 70 + (long) (Math.max(0F, Math.min(1F, strength)) * 90);
		AT.put(entityId, new long[] {System.currentTimeMillis(), hold});
	}

	/** How fast this entity's action clock runs now: almost still during an impact frame, else 1. */
	public static float factor(int entityId) {
		long[] h = AT.get(entityId);
		if (h == null || Ui.calm()) {
			return 1F;
		}
		long since = System.currentTimeMillis() - h[0];
		if (since > h[1]) {
			if (since > 5000) {
				AT.remove(entityId);
			}
			return 1F;
		}
		return 0.06F;
	}
}
