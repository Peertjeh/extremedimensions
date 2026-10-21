package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.tether.TetherService;

import net.minecraft.world.item.ItemStack;

/** Tethers stack, but the enchanting table takes them one at a time: a stack of them is not enchanted at once. */
@Mixin(ItemStack.class)
public abstract class TetherEnchantMixin {
	@Inject(method = "isEnchantable", at = @At("RETURN"), cancellable = true)
	private void extremedimensions$oneAtATime(CallbackInfoReturnable<Boolean> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (cir.getReturnValueZ() && self.getCount() > 1 && TetherService.isTether(self.getItem())) {
			cir.setReturnValue(false);
		}
	}
}
