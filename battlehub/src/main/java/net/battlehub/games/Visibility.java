package net.battlehub.games;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Who sees what, and which game entities are protected. A disguised prop is hidden from everyone but itself (it
 * is only seen as its block); the hitbox of a disguise is hidden from its own prop so it never gets in the way of
 * its clicks; shopkeepers cannot be hurt. Games register and unregister here and always clean up when they end.
 */
public final class Visibility {
	private Visibility() {}

	/** Hidden entity -> the only viewer who may still see it. */
	private static final Map<UUID, UUID> ONLY = new ConcurrentHashMap<>();
	/** Entity -> the one viewer who may not see it. */
	private static final Map<UUID, UUID> NOT = new ConcurrentHashMap<>();
	private static final Set<UUID> NPCS = ConcurrentHashMap.newKeySet();
	/** Set once the server asked about an entity: the mixin that hides players works. */
	private static volatile boolean consulted;

	/** The player is only seen by themselves from now on. */
	public static void hide(ServerPlayer p) {
		ONLY.put(p.getUUID(), p.getUUID());
		refresh(p);
	}

	public static void show(ServerPlayer p) {
		if (ONLY.remove(p.getUUID()) != null) {
			refresh(p);
		}
	}

	/** For players who are gone: forget them without sending anything. */
	public static void forget(UUID id) {
		ONLY.remove(id);
		NOT.remove(id);
	}

	public static boolean isHidden(UUID id) {
		return ONLY.containsKey(id);
	}

	/** {@code viewer} does not see {@code e} (its own disguise hitbox). */
	public static void hideFrom(Entity e, UUID viewer) {
		NOT.put(e.getUUID(), viewer);
	}

	/** Makes the server re-check who sees this player right away instead of on their next step. */
	private static void refresh(ServerPlayer p) {
		if (p.level() instanceof ServerLevel level && !p.hasDisconnected()) {
			level.getChunkSource().move(p);
		}
	}

	/** Called for every entity a player could see (Entity.broadcastToPlayer). */
	public static boolean hidden(Entity entity, ServerPlayer viewer) {
		consulted = true;
		if (ONLY.isEmpty() && NOT.isEmpty()) {
			return false;
		}
		UUID id = entity.getUUID();
		UUID only = ONLY.get(id);
		if (only != null && !only.equals(viewer.getUUID())) {
			return true;
		}
		UUID not = NOT.get(id);
		return not != null && not.equals(viewer.getUUID());
	}

	/** True once the hiding mixin is known to work. */
	public static boolean active() {
		return consulted;
	}

	public static void release(Entity e) {
		NOT.remove(e.getUUID());
		ONLY.remove(e.getUUID());
	}

	public static void protectNpc(Entity e) {
		NPCS.add(e.getUUID());
	}

	public static void releaseNpc(Entity e) {
		NPCS.remove(e.getUUID());
	}

	public static boolean isProtectedNpc(Entity e) {
		return !NPCS.isEmpty() && NPCS.contains(e.getUUID());
	}

	/** Server stop: nothing stays hidden or protected. */
	public static void clear() {
		ONLY.clear();
		NOT.clear();
		NPCS.clear();
	}

	public static Set<UUID> hiddenPlayers() {
		return new HashSet<>(ONLY.keySet());
	}
}
