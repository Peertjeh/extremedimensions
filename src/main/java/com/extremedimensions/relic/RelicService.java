package com.extremedimensions.relic;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.config.Config;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Binding relics to the Tether. Using a relic binds it (the one bound before comes back to the hand); the Curses
 * screen unbinds it again. A bound relic is kept in the player's data, not in the inventory: it cannot be lost to a
 * death, a Lurker or a full bag.
 */
public final class RelicService {
	private RelicService() {}

	public static Item item(Relic r) {
		return ModItems.RELICS.get(r);
	}

	/** The relic an item stack is, or null. */
	public static Relic of(ItemStack stack) {
		for (Relic r : Relic.values()) {
			if (stack.is(item(r))) {
				return r;
			}
		}
		return null;
	}

	public static Relic bound(PlayerData d) {
		return Relic.byId(d.relic);
	}

	/** Whether the relic bound to the Tether acts: it sleeps with the curses in another mod's world. */
	public static boolean has(PlayerData d, Relic r) {
		return d != null && !d.away && r.id.equals(d.relic);
	}

	/** For hot paths that also see players without data (never creates it). */
	public static boolean has(ServerPlayer p, Relic r) {
		return has(DataStore.peek(p.getUUID()), r);
	}

	/** Using a relic: it is bound to the Tether, and the one bound before (if any) comes back. */
	public static InteractionResult bind(ServerPlayer p, ItemStack stack) {
		Relic r = of(stack);
		if (r == null) {
			return InteractionResult.PASS;
		}
		PlayerData d = DataStore.get(p.getUUID());
		Relic before = bound(d);
		if (before == r) {
			return InteractionResult.FAIL;
		}
		if (locked(p, d)) {
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		d.relic = r.id;
		if (before != null) {
			WorldFx.give(p, new ItemStack(item(before)));
		}
		DataStore.markDirty();
		CurseEffects.applyEffects(p, d);
		Sync.curses(p, d, false);
		WorldFx.soundSelf(p, "minecraft:block.chain.place", 1.0f, 0.7f);
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.resonate", 0.8f, 0.8f);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.5, 0.0, 20);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.bound", Component.translatable(r.nameKey())), true);
		Advancements.grant(p, Advancements.RELIC_BOUND);
		return InteractionResult.SUCCESS;
	}

	/** The relic comes off the Tether and back to the player (or falls at their feet with a full bag). */
	public static void unbind(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		Relic r = bound(d);
		if (r == null || locked(p, d)) {
			return;
		}
		d.relic = null;
		WorldFx.give(p, new ItemStack(item(r)));
		DataStore.markDirty();
		CurseEffects.applyEffects(p, d);
		Sync.curses(p, d, false);
		WorldFx.soundSelf(p, "minecraft:block.chain.break", 1.0f, 0.9f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.unbound", Component.translatable(r.nameKey())), true);
	}

	/**
	 * A relic is bound or taken off only at home and on one's feet: not on an expedition, in the Seam or fallen (its gift
	 * and its price are paid for the whole of it, not swapped for the moment that suits). The player is told so.
	 */
	private static boolean locked(ServerPlayer p, PlayerData d) {
		if (!d.inRun && !d.inSeam && !com.extremedimensions.coop.FallenService.fallen(p)) {
			return false;
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.locked"), true);
		return true;
	}

	/** Drain multiplier from the bound relic in this world. */
	public static double drainFactor(PlayerData d, String dim) {
		Relic r = bound(d);
		if (r == Relic.EMBER_HEART) {
			return Dims.NETHER.equals(dim) ? Config.RELIC_EMBER_HEART_NETHER : Dims.END.equals(dim) ? Config.RELIC_EMBER_HEART_END : 1.0;
		}
		if (r == Relic.PALE_SHARD) {
			return Dims.END.equals(dim) ? Config.RELIC_PALE_SHARD_END : Dims.NETHER.equals(dim) ? Config.RELIC_PALE_SHARD_NETHER : 1.0;
		}
		return 1.0;
	}

	/** A random relic, as found. */
	public static ItemStack random() {
		Relic[] all = Relic.values();
		return new ItemStack(item(all[ThreadLocalRandom.current().nextInt(all.length)]));
	}
}
