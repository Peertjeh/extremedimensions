package com.extremedimensions.penalty;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Sealed Pockets on both sides: which slots of a player's main inventory are sealed, from the last one backwards (the
 * hotbar never). The server reads the curse from the player's data; the client keeps the local player's count from
 * the curse sync, so menus agree with the server and nothing is ever predicted into a sealed slot.
 */
public final class SealedPockets {
	private SealedPockets() {}

	/** The local player's sealed slots, set on the client from the curse sync (unused on a server). */
	public static volatile int client = 0;

	/** How many slots this player has sealed right now (none in creative or spectator). */
	public static int count(Player player) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return 0;
		}
		if (player instanceof ServerPlayer) {
			PlayerData d = DataStore.peek(player.getUUID());
			return d == null ? 0 : CurseEffects.sealedSlots(d);
		}
		return client;
	}

	/** The first sealed slot index; {@link Inventory#INVENTORY_SIZE} when nothing is sealed. */
	public static int firstSealed(Player player) {
		return Inventory.INVENTORY_SIZE - count(player);
	}

	public static boolean isSealed(Player player, int slot) {
		return slot >= firstSealed(player) && slot < Inventory.INVENTORY_SIZE;
	}
}
