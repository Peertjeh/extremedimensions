package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import com.extremedimensions.penalty.ExtremeCurses;
import com.extremedimensions.relic.RelicService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * Unhealing and the Hollow Crown: a full stomach no longer heals, or heals only the Hollow Crown's share (potions, golden
 * apples and the like still do). One condition for both, so the Hollow Crown's share never slips past Unhealing.
 */
@Mixin(FoodData.class)
public abstract class UnhealingMixin {
	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private boolean extremedimensions$unhealing(ServerPlayer player, float amount) {
		return ExtremeCurses.mayRegenerate(player) && RelicService.regenerates(player, amount);
	}
}
