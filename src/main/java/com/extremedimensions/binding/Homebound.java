package com.extremedimensions.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Homebound: gear that comes home with you. Die beyond the wound (or in the Seam) and what you dropped with Homebound
 * on it is taken up before anything else can happen to it (a severed world's hunger included) and given back when you
 * respawn. It waits in the player's data, so a server stopped in between keeps it. Worn or held beyond the wound, each
 * Homebound piece drains a little.
 */
public final class Homebound {
	private Homebound() {}

	/** Drain multiplier for the Homebound pieces a player wears or holds. */
	public static double drainFactor(ServerPlayer p) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (ModEnchantments.level(p.level().registryAccess(), p.getItemBySlot(slot), ModEnchantments.HOMEBOUND) > 0) {
				n++;
			}
		}
		return 1.0 + Math.max(0, Config.ENCH_HOMEBOUND_DRAIN) * n;
	}

	/**
	 * A player died: if beyond the wound, the Homebound things among what they just dropped are taken up to come home
	 * with them. Returns how many stacks were taken.
	 */
	public static int gather(ServerPlayer p) {
		return gather(p, Dims.of(p));
	}

	/** As {@link #gather(ServerPlayer)}, for a player who died in the given world. */
	public static int gather(ServerPlayer p, String dim) {
		if (!Dims.isBeyond(dim) && !Dims.SEAM.equals(dim)) {
			return 0;
		}
		PlayerData d = DataStore.get(p.getUUID());
		int n = 0;
		for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4),
				i -> i.isAlive() && i.tickCount <= 2 && ModEnchantments.level(p.level().registryAccess(), i.getItem(), ModEnchantments.HOMEBOUND) > 0)) {
			String kept = encode(p.level().registryAccess(), item.getItem());
			if (kept == null) {
				continue;
			}
			d.homebound.add(kept);
			item.discard();
			n++;
		}
		if (n > 0) {
			DataStore.markDirty();
		}
		return n;
	}

	/** On respawn: what came home is given back (into the bag, or at the player's feet when it is full). */
	public static void restore(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		if (d.homebound.isEmpty()) {
			return;
		}
		for (ItemStack s : waiting(p)) {
			WorldFx.give(p, s);
		}
		d.homebound.clear();
		DataStore.markDirty();
		WorldFx.soundSelf(p, "minecraft:item.armor.equip_chain", 1.0f, 0.8f);
	}

	/** What waits for a player's respawn. */
	public static List<ItemStack> waiting(ServerPlayer p) {
		PlayerData d = DataStore.peek(p.getUUID());
		List<ItemStack> out = new ArrayList<>();
		if (d == null || d.homebound == null) {
			return out;
		}
		for (String s : d.homebound) {
			ItemStack stack = decode(p.level().registryAccess(), s);
			if (!stack.isEmpty()) {
				out.add(stack);
			}
		}
		return out;
	}

	/** How many stacks wait for a player (for the tests). */
	public static int waitingCount(UUID id) {
		PlayerData d = DataStore.peek(id);
		return d == null || d.homebound == null ? 0 : d.homebound.size();
	}

	public static String encode(HolderLookup.Provider access, ItemStack stack) {
		return ItemStack.CODEC.encodeStart(access.createSerializationContext(JsonOps.INSTANCE), stack).result().map(Object::toString).orElse(null);
	}

	public static ItemStack decode(HolderLookup.Provider access, String json) {
		try {
			return ItemStack.CODEC.parse(access.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}
}
