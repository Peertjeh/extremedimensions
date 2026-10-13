package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.lobby.Locks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/** Hub items (the compass) cannot be picked up, swapped or thrown; no dropping in matches or the lobby. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void battlehub$lockItems(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp && Locks.blocksClick((AbstractContainerMenu) (Object) this, slot, button, input, sp)) {
			ci.cancel();
		}
	}
}
