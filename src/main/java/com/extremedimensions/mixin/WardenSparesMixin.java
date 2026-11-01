package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.lurker.SculkLurker;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * The Warden spares the Sculk Lurker that serves it: it never takes it for a target, and so never sniffs it out, never
 * grows angry at it and never heeds a vibration it makes.
 */
@Mixin(Warden.class)
public abstract class WardenSparesMixin {
	@Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$sparesItsLurker(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof SculkLurker) {
			cir.setReturnValue(false);
		}
	}
}
