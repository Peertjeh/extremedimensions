package com.extremedimensions.tetherite;

import com.extremedimensions.config.Config;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Tetherite is tethered: what the void takes of it, it gives back. A Tetherite item that falls out of the bottom of a
 * world (off an island of the Seam, into the End's void) is drawn back up its thread to whoever dropped it, or, when
 * they cannot be found, to the nearest player in that world, and failing that it waits at the Overworld's spawn. Like
 * netherite it does not burn.
 *
 * <p>Gear dropped on a death has no thrower of its own: the player who has just died beside it is taken for one.
 */
public final class Tethered {
	private Tethered() {}

	/** Called every tick of a dropped item (server side). */
	public static void tick(ItemEntity item) {
		if (!(item.level() instanceof ServerLevel level) || Config.TETHERITE_TETHERED <= 0) {
			return;
		}
		ItemStack stack = item.getItem();
		if (!Tetherite.isGear(stack)) {
			return;
		}
		if (item.tickCount <= 2 && item.getOwner() == null) {
			// dropped on a death: the dead player beside it is its owner
			for (Player p : level.players()) {
				if (p.isDeadOrDying() && p.distanceToSqr(item) < 9) {
					item.setThrower(p);
					break;
				}
			}
		}
		// (above 64 blocks under the world, where the game itself takes dropped items away)
		if (item.getY() >= level.getMinY() - Math.min(60, Config.TETHERITE_VOID_DEPTH)) {
			return;
		}
		// Its owner died with it (a fall into the void): it waits for their respawn with what Homebound keeps, not
		// with whoever else is about
		if (item.getOwner() instanceof ServerPlayer dead && !dead.isAlive()) {
			String kept = com.extremedimensions.binding.Homebound.encode(level.registryAccess(), stack);
			if (kept != null) {
				com.extremedimensions.data.DataStore.get(dead.getUUID()).homebound.add(kept);
				com.extremedimensions.data.DataStore.markDirty();
				item.discard();
				return;
			}
		}
		ServerPlayer to = owner(level, item);
		if (to != null) {
			back(item, (ServerLevel) to.level(), to.getX(), to.getY() + 0.5, to.getZ());
			to.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.tetherite.returned", stack.getHoverName()), true);
		} else {
			MinecraftServer server = level.getServer();
			ServerLevel home = server.overworld();
			var spawn = home.getRespawnData().pos();
			back(item, home, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
		}
	}

	/** Whoever dropped it, when they are about and alive; else the nearest player in the same world. */
	private static ServerPlayer owner(ServerLevel level, ItemEntity item) {
		Entity thrower = item.getOwner();
		if (thrower instanceof ServerPlayer sp && sp.isAlive() && !sp.isRemoved()) {
			return sp;
		}
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - item.getX(), dz = p.getZ() - item.getZ();
			double d = dx * dx + dz * dz;
			if (p.isAlive() && !p.isSpectator() && d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	/** The item, taken out of the void and set down again where it is going, with a soul's light. */
	private static void back(ItemEntity item, ServerLevel to, double x, double y, double z) {
		ItemStack stack = item.getItem().copy();
		Entity thrower = item.getOwner();
		item.discard();
		ItemEntity fresh = new ItemEntity(to, x, y, z, stack, 0, 0.1, 0);
		if (thrower != null) {
			fresh.setThrower(thrower);
		}
		fresh.setDefaultPickUpDelay();
		fresh.setExtendedLifetime();
		to.addFreshEntity(fresh);
		String where = to.dimension().identifier().toString();
		WorldFx.particleIn(where, "minecraft:soul_fire_flame", x, y, z, 0.3, 0.5, 0.3, 0.03, 24);
		WorldFx.soundIn(where, "minecraft:entity.enderman.teleport", "player", x, y, z, 0.8f, 1.4f);
	}
}
