package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.tetherite.TetheriteBinding;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The binding (3.26): taking a Tetherite upgrade from the smithing table rolls whether the metal holds. If it fails,
 * nothing of the table's usual take happens: the result is gone, the ingot shattered, the piece worn and scarred (see
 * TetheriteBinding). The client takes it as usual until the server sets it right.
 */
@Mixin(SmithingMenu.class)
public abstract class TetheriteBindMixin {
	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremedimensions$bind(Player player, ItemStack carried, CallbackInfo ci) {
		SmithingMenu self = (SmithingMenu) (Object) this;
		if (player instanceof ServerPlayer p && TetheriteBinding.isBinding(self.getSlot(SmithingMenu.BASE_SLOT).getItem(), carried)
				&& !TetheriteBinding.take(p, self, carried)) {
			ci.cancel();
		}
	}
}
