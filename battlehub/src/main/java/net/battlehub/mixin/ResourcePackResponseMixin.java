package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.fx.ResourcePack;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Tells BattleHub what a player did with its resource pack (only reached on the server thread). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackResponseMixin {
	@Inject(method = "handleResourcePackResponse", at = @At("TAIL"), require = 0)
	private void battlehub$packResponse(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game) {
			ResourcePack.response(game.player, packet.id(), packet.action().name());
		}
	}
}
