package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.binding.WoundLens;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Whatever a player is told on the action bar keeps the Wound Lens's readout off it for a moment, so it can be read. */
@Mixin(ServerPlayer.class)
public abstract class ActionBarMixin {
	@Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), require = 1)
	private void extremedimensions$told(Component message, boolean overlay, CallbackInfo ci) {
		if (overlay) {
			WoundLens.told((ServerPlayer) (Object) this);
		}
	}
}
