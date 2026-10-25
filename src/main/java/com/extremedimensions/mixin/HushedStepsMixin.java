package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremedimensions.binding.Charms;

import net.minecraft.world.entity.LivingEntity;

/** Hushed Steps: a player in its boots is seen by monsters as if from farther away (see Charms). */
@Mixin(LivingEntity.class)
public abstract class HushedStepsMixin {
	@ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
	private double extremedimensions$hushedSteps(double visibility) {
		return visibility * Charms.visibility((LivingEntity) (Object) this);
	}
}
