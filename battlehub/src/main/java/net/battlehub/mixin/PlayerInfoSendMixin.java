package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.battlehub.hub.TabListWorlds;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The tab list per world (1.52): a player info packet that says who is listed is set right for the player it goes to,
 * the one packet the server sends everyone included (hub.TabListWorlds).
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PlayerInfoSendMixin {
	@ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), argsOnly = true)
	private Packet<?> battlehub$listedInItsWorld(Packet<?> packet) {
		if (packet instanceof ClientboundPlayerInfoUpdatePacket info && (Object) this instanceof ServerGamePacketListenerImpl game
				&& game.player != null) {
			return TabListWorlds.forViewer(info, game.player);
		}
		return packet;
	}
}
