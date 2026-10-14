package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.gui.MenuGuard;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Creative clients set their own inventory slots directly; inventory-sorting mods use that to pull items out of
 * open menus. BattleHub refuses those packets while one of its menus is open and for its own items.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void battlehub$creativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
		MinecraftServer server = player.level().getServer();
		if (server == null || Thread.currentThread() != server.getRunningThread()) {
			// Vanilla first hands the packet over to the server thread; decide there.
			return;
		}
		if (MenuGuard.blocksCreativeSlot(player, packet.slotNum(), packet.itemStack())) {
			ci.cancel();
			MenuGuard.resync(player);
		}
	}
}
