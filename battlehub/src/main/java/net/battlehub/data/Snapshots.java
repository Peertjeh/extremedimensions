package net.battlehub.data;

import java.util.UUID;

import net.battlehub.arena.ArenaWorld;
import net.battlehub.lobby.Lobby;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Inventory, health, hunger, XP, effects, game mode and position from before a match or spectating session. Kept
 * in memory and on disk ({@code <world>/battlehub/restore/<uuid>.dat}), so even a crash cannot lose a player's
 * items: an unrestored snapshot is applied the next time that player joins.
 */
public final class Snapshots {
	private Snapshots() {}

	private static final StateStore STORE = new StateStore("restore");

	public static void init(MinecraftServer server) {
		STORE.init(server);
	}

	public static boolean has(UUID id) {
		return STORE.has(id);
	}

	/** Remembers the player's current state. An older, unrestored snapshot is kept (it is the real one). */
	public static void take(ServerPlayer p) {
		STORE.take(p, true);
	}

	/**
	 * Gives the player back everything from the snapshot. With {@code teleport} false (the player is disconnecting)
	 * the position is kept as owed and applied on the next join.
	 */
	public static boolean restore(ServerPlayer p, boolean teleport) {
		return STORE.restore(p, teleport ? StateStore.Restore.FULL : StateStore.Restore.OWE_POSITION,
				Lobby::teleport, level -> !ArenaWorld.is(level));
	}

	public static void discard(UUID id) {
		STORE.discard(id);
	}
}
