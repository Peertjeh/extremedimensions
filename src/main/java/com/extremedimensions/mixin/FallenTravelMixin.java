package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.coop.FallenService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

/** A fallen player cannot be moved to another world, by a command, a portal or anything else. */
@Mixin(ServerPlayer.class)
public abstract class FallenTravelMixin {
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"),
			cancellable = true)
	private void extremedimensions$fallenStay(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
		if (FallenService.refuseTravel((ServerPlayer) (Object) this, transition.newLevel())) {
			cir.setReturnValue(null);
		}
	}
}
