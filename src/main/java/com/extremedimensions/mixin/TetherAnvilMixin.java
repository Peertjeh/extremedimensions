package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.tether.TetherService;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

/** The anvil, like the table, takes Tethers one at a time: a stack is renamed, but not enchanted or combined at once. */
@Mixin(AnvilMenu.class)
public abstract class TetherAnvilMixin {
	@Inject(method = "createResult", at = @At("TAIL"), require = 1)
	private void extremedimensions$oneAtATime(CallbackInfo ci) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		ItemStack left = self.getSlot(0).getItem();
		if (left.getCount() > 1 && TetherService.isTether(left.getItem()) && !self.getSlot(1).getItem().isEmpty()) {
			self.getSlot(2).set(ItemStack.EMPTY);
		}
	}
}
