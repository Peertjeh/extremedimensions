package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.tetherite.Tethered;

import net.minecraft.world.entity.item.ItemEntity;

/** Tetherite that falls out of the bottom of a world is drawn back to its owner (see Tethered). */
@Mixin(ItemEntity.class)
public abstract class TetheredItemMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void extremedimensions$tethered(CallbackInfo ci) {
		Tethered.tick((ItemEntity) (Object) this);
	}
}
