package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.extremedimensions.penalty.AbyssalTolls;

import net.minecraft.world.entity.LivingEntity;

/** Bitter Cure: Instant Health and Regeneration (and so golden apples) heal a cursed player only part of the way. */
@Mixin(targets = {"net.minecraft.world.effect.HealOrHarmMobEffect", "net.minecraft.world.effect.RegenerationMobEffect"})
public abstract class BitterCureMixin {
	@WrapOperation(method = {"applyEffectTick", "applyInstantaneousEffect"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
	private void extremedimensions$bitterCure(LivingEntity entity, float amount, Operation<Void> heal) {
		heal.call(entity, AbyssalTolls.cure(entity, amount));
	}
}
