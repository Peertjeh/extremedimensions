package com.extremedimensions.server;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;

/**
 * A plain limit on what a client may ask of the server: each kind of request at most so many times a second per
 * player. A modified client that floods requests is ignored past the limit instead of making the server build screens,
 * lists and trades for it hundreds of times a tick. An honest client never comes near it.
 */
public final class Requests {
	private Requests() {}

	/** "uuid|kind" -> {second, requests in it}. */
	private static final Map<String, long[]> SEEN = new HashMap<>();

	/** Whether this request is let through (at most {@code perSecond} of its kind a second for this player). */
	public static boolean allow(ServerPlayer p, String kind, int perSecond) {
		long second = p.level().getServer().getTickCount() / 20;
		long[] seen = SEEN.computeIfAbsent(p.getStringUUID() + "|" + kind, k -> new long[] {second, 0});
		if (seen[0] != second) {
			seen[0] = second;
			seen[1] = 0;
		}
		return ++seen[1] <= perSecond;
	}

	/** A player left: their counts go. */
	public static void forget(ServerPlayer p) {
		String prefix = p.getStringUUID() + "|";
		SEEN.keySet().removeIf(k -> k.startsWith(prefix));
	}

	public static void clear() {
		SEEN.clear();
	}
}
