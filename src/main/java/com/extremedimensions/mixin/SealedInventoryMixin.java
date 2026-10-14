package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.penalty.Dwindling;
import com.extremedimensions.penalty.SealedPockets;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Sealed Pockets: a sealed slot is never free and never has room. Picking items up, {@code /give} and items put back
 * after closing a menu all skip it, so with only sealed slots left an item simply stays on the ground (no pickup
 * sound, no pickup animation).
 */
@Mixin(Inventory.class)
public abstract class SealedInventoryMixin {
	@Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$freeSlot(CallbackInfoReturnable<Integer> cir) {
		Inventory inv = (Inventory) (Object) this;
		int first = SealedPockets.firstSealed(inv.player);
		if (first >= Inventory.INVENTORY_SIZE) {
			return;
		}
		for (int i = 0; i < first; i++) {
			if (inv.getItem(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	/**
	 * Dwindling: the most a stack of the player's own inventory may hold. Added to the inventory (it only inherits the
	 * container default), so picking up, merging and every menu respect it.
	 */
	public int getMaxStackSize(ItemStack stack) {
		Inventory inv = (Inventory) (Object) this;
		return Dwindling.limit(inv.player, Math.min(inv.getMaxStackSize(), stack.getMaxStackSize()));
	}

	/** Slots are searched in order and the sealed ones come last: a sealed match means there is no unsealed one. */
	@Inject(method = "getSlotWithRemainingSpace", at = @At("RETURN"), cancellable = true)
	private void extremedimensions$roomInSlot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (SealedPockets.isSealed(((Inventory) (Object) this).player, cir.getReturnValueI())) {
			cir.setReturnValue(-1);
		}
	}
}
