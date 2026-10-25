package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremedimensions.binding.Charms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Sated and the Curse of the Starved: the hunger a player is caused, less or more (see Charms). */
@Mixin(Player.class)
public abstract class SatedMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float extremedimensions$sated(float amount) {
		return (Object) this instanceof ServerPlayer p && amount > 0 ? amount * Charms.exhaustion(p) : amount;
	}
}
