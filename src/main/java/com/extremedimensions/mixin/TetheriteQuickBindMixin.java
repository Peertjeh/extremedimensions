package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.tetherite.TetheriteBinding;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The binding on a shift-click (3.26): the table moves a copy of the result before it is taken, so the roll comes
 * first. A failed binding moves nothing; one that held moves as usual, and its take does not roll again. And where the
 * metal will not bind, the result cannot be taken at all.
 */
@Mixin(ItemCombinerMenu.class)
public abstract class TetheriteQuickBindMixin {
	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremedimensions$bindFirst(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof SmithingMenu menu && index == SmithingMenu.RESULT_SLOT && player instanceof ServerPlayer p
				&& TetheriteBinding.pending(menu) && !TetheriteBinding.quickMove(p, menu)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	/** Where the metal will not bind (3.26.1), the result shows but cannot be taken: the server says where to go. */
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremedimensions$bindHere(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof SmithingMenu menu && TetheriteBinding.pending(menu)) {
			if (player instanceof ServerPlayer p ? !TetheriteBinding.mayTake(p)
					: player.level().isClientSide() && TetheriteBinding.clientBlocked != 0) {
				cir.setReturnValue(false);
			}
		}
	}

	@Inject(method = "quickMoveStack", at = @At("RETURN"), require = 1)
	private void extremedimensions$bindDone(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof SmithingMenu) {
			TetheriteBinding.quickMoveDone();
		}
	}
}
