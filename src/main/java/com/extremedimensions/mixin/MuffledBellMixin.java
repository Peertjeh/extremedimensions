package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremedimensions.relic.RelicService;

import net.minecraft.world.entity.Entity;

/**
 * The Muffled Bell: its bearer's vibrations are dampened as wool dampens them, so the sculk's sensors and shriekers and
 * the Warden do not hear them (see RelicService).
 */
@Mixin(Entity.class)
public abstract class MuffledBellMixin {
	@ModifyReturnValue(method = "dampensVibrations", at = @At("RETURN"))
	private boolean extremedimensions$muffledBell(boolean dampens) {
		return dampens || RelicService.muffled((Entity) (Object) this);
	}
}
