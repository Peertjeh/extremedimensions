package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.lobby.Locks;
import net.minecraft.server.level.ServerPlayer;

/** The drop key (Q) does nothing on hub items, in matches or in the lobby. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void battlehub$noDrop(boolean all, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (Locks.blocksDrop(self)) {
			ci.cancel();
			// The client already removed the item locally: send the real inventory back.
			self.inventoryMenu.sendAllDataToRemote();
		}
	}
}
