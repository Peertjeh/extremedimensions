package net.battlehub.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a fighter can remember, in server ticks, for spec v3's memory and the fight logs (the same for a bot as for a
 * person): when each player last swung (people from the network, bots from their own swing), when each was last hurt by
 * another player, whom each last hurt and when, and when a fighter last saw another (a bot while it fights, a person
 * at the logs' decisions). The spec's memory reaches {@link #CAP} ticks; longer ago, or never, reads as that.
 */
public final class CombatMemory {
	private CombatMemory() {}

	/** 5 seconds: the spec's memory goes no further back. */
	static final int CAP = 100;

	private static long now;
	private static final Map<UUID, Long> SWUNG = new HashMap<>();
	private static final Map<UUID, Long> HURT = new HashMap<>();
	/** attacker -> whom it last hurt, and when. */
	private static final Map<UUID, UUID> HIT_WHOM = new HashMap<>();
	private static final Map<UUID, Long> HIT_WHEN = new HashMap<>();
	/** watcher -> other -> when it last saw it. */
	private static final Map<UUID, Map<UUID, Long>> SEEN = new HashMap<>();

	public static void tick(MinecraftServer server) {
		now++;
		if (now % 200 == 0) {
			java.util.function.Predicate<UUID> gone = id -> server.getPlayerList().getPlayer(id) == null;
			SWUNG.keySet().removeIf(gone);
			HURT.keySet().removeIf(gone);
			HIT_WHOM.keySet().removeIf(gone);
			HIT_WHEN.keySet().retainAll(HIT_WHOM.keySet());
			SEEN.keySet().removeIf(gone);
		}
	}

	/** An arm swing (a person's from the network, a bot's when it swings). */
	public static void swung(ServerPlayer p) {
		SWUNG.put(p.getUUID(), now);
	}

	/** A player hurt another (HubEvents, also the killing blow). */
	public static void hurt(ServerPlayer victim, ServerPlayer attacker) {
		if (attacker == null || attacker == victim) return;
		HURT.put(victim.getUUID(), now);
		HIT_WHOM.put(attacker.getUUID(), victim.getUUID());
		HIT_WHEN.put(attacker.getUUID(), now);
	}

	/** {@code watcher} sees {@code other} now. */
	static void saw(UUID watcher, UUID other) {
		SEEN.computeIfAbsent(watcher, k -> new HashMap<>()).put(other, now);
	}

	static double sinceSwing(UUID id) {
		return since(SWUNG.get(id));
	}

	/** Ticks since another player last hurt this one. */
	static double sinceHurt(UUID id) {
		return since(HURT.get(id));
	}

	/** Ticks since {@code attacker} last hurt {@code victim} (someone else in between: not remembered). */
	static double sinceHit(UUID attacker, UUID victim) {
		return victim.equals(HIT_WHOM.get(attacker)) ? since(HIT_WHEN.get(attacker)) : CAP;
	}

	/** Ticks since {@code watcher} last saw {@code other}. */
	static double unseen(UUID watcher, UUID other) {
		Map<UUID, Long> m = SEEN.get(watcher);
		return since(m == null ? null : m.get(other));
	}

	private static double since(Long t) {
		return t == null ? CAP : Math.min(CAP, now - t);
	}

	static void clear() {
		SWUNG.clear();
		HURT.clear();
		HIT_WHOM.clear();
		HIT_WHEN.clear();
		SEEN.clear();
	}
}
