package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.penalty.Dwindling;
import com.extremedimensions.penalty.SealedPockets;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Sealed Pockets: no menu lets an item into a sealed slot (clicks, shift-clicks, drags and number keys alike).
 * Dwindling: a slot of the player's inventory holds no more than the curse allows.
 */
@Mixin(Slot.class)
public abstract class SealedSlotMixin {
	@Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
	private void extremedimensions$dwindling(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		Slot self = (Slot) (Object) this;
		if (self.container instanceof Inventory inv) {
			cir.setReturnValue(Dwindling.limit(inv.player, cir.getReturnValueI()));
		}
	}

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$sealed(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Slot self = (Slot) (Object) this;
		if (self.container instanceof Inventory inv && SealedPockets.isSealed(inv.player, self.getContainerSlot())) {
			cir.setReturnValue(false);
		}
	}
}
