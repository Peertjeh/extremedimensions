package com.extremedimensions.penalty;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Dwindling, on both sides: a stack in the player's own inventory holds half as much per stack of the curse (64, 32,
 * 16, 8), and so does a stack inside a shulker box or bundle the player carries (see CarriedContainers). Chests and
 * other containers are untouched. The client keeps the local player's stacks from the curse sync, so menus agree with
 * the server.
 */
public final class Dwindling {
	private Dwindling() {}

	/** The local player's Dwindling stacks and the server's value per stack, set on the client from the curse sync. */
	public static volatile int client = 0;
	public static volatile double clientValue = 0;

	public static int stacks(Player player) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return 0;
		}
		if (player instanceof ServerPlayer) {
			PlayerData d = DataStore.peek(player.getUUID());
			return d == null ? 0 : d.stacks(Penalty.DWINDLING);
		}
		return client;
	}

	/** The most a stack of the player's inventory may hold, from what it could hold otherwise. */
	public static int limit(Player player, int normal) {
		int n = stacks(player);
		if (n <= 0 || normal <= 1) {
			return normal;
		}
		double value = player instanceof ServerPlayer || clientValue <= 0 ? Penalty.DWINDLING.value : clientValue;
		// As a double: with a high maximum the divisor runs past what an int holds
		double divisor = Math.pow(Math.max(1, value), n);
		return Math.max(1, (int) (normal / divisor));
	}

	/** Stacks above the limit are split: the rest goes to free room in the inventory, or falls out. */
	public static void enforce(ServerPlayer p) {
		if (stacks(p) <= 0) {
			return;
		}
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			int max = inv.getMaxStackSize(s);
			if (s.getCount() <= max) {
				continue;
			}
			give(p, s.split(s.getCount() - max));
		}
		// The stacks inside a carried box or bundle shrink too
		CarriedContainers.dwindle(p, rest -> give(p, rest));
	}

	/** What a split leaves over goes to free room in the inventory; whatever still finds none falls out. */
	private static void give(ServerPlayer p, ItemStack rest) {
		// add() says whether anything went in, not whether all of it did: what is left over must not be lost
		p.getInventory().add(rest);
		if (!rest.isEmpty()) {
			ItemEntity item = new ItemEntity(p.level(), p.getX(), p.getEyeY() - 0.3, p.getZ(), rest);
			item.setPickUpDelay(40);
			p.level().addFreshEntity(item);
		}
	}
}
