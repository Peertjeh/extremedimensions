package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.HubEvents;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Arm swings, also at thin air (Prop Hunt hunters pay for missing). The packet arrives on the network thread;
 * the game hears about it on the server thread, after any attack sent just before it.
 */
@Mixin(ServerboundSwingPacket.class)
public abstract class SwingPacketMixin {
	@Inject(method = "handle(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V", at = @At("HEAD"), require = 0)
	private void battlehub$swing(ServerGamePacketListener listener, CallbackInfo ci) {
		if (listener instanceof ServerGamePacketListenerImpl impl) {
			ServerPlayer p = impl.player;
			MinecraftServer server = p.level().getServer();
			// The packet is handled twice: first here on the network thread, then again on the server thread.
			// React once, queued in order with the packets before it.
			if (server != null && !server.isSameThread()) {
				server.execute(() -> HubEvents.swung(p));
			}
		}
	}
}
