package net.battlehub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.battlehub.HubEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * A player leaving the server (disconnect, kick, server stop). Runs before vanilla saves the player file, so the
 * inventory BattleHub gives back is the one that gets saved.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void battlehub$leaving(ServerPlayer player, CallbackInfo ci) {
		HubEvents.leaving(player);
	}
}
