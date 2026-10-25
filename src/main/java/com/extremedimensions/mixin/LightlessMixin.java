package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;

/** Lightless: round a cursed player, monsters judge a spawn spot as if its light were dimmer. */
@Mixin(Monster.class)
public abstract class LightlessMixin {
	@ModifyExpressionValue(method = "isDarkEnoughToSpawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/ServerLevelAccessor;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", ordinal = 1))
	private static int extremedimensions$lightlessBlock(int light, @Local(argsOnly = true) ServerLevelAccessor level, @Local(argsOnly = true) BlockPos pos) {
		return Math.max(0, light - AbyssalWorld.darker(level, pos));
	}

	@ModifyExpressionValue(method = "isDarkEnoughToSpawn", at = {
			@At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"),
			@At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;I)I")})
	private static int extremedimensions$lightlessRaw(int light, @Local(argsOnly = true) ServerLevelAccessor level, @Local(argsOnly = true) BlockPos pos) {
		return Math.max(0, light - AbyssalWorld.darker(level, pos));
	}
}
