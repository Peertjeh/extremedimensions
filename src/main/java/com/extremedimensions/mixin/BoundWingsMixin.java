package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.elder.ElderSpells;

import net.minecraft.world.entity.LivingEntity;

/** Wings an Elder has bound do not glide: a flyer cannot open them, and one gliding falls (on both sides). */
@Mixin(LivingEntity.class)
public abstract class BoundWingsMixin {
	@Inject(method = "canGlide", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$boundWings(CallbackInfoReturnable<Boolean> cir) {
		if (ElderSpells.grounded((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
