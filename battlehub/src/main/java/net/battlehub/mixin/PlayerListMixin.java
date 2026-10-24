package net.battlehub.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.battlehub.HubEvents;
import net.battlehub.bot.Bots;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * A player leaving the server (disconnect, kick, server stop). Runs before vanilla saves the player file, so the
 * inventory BattleHub gives back is the one that gets saved. Bots stay out of chat and leave no files.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void battlehub$leaving(ServerPlayer player, CallbackInfo ci) {
		HubEvents.leaving(player);
	}

	/** Bots come and go without "joined the game" messages (and earn no advancement announcements). */
	@Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void battlehub$quietBots(Component message, boolean overlay, CallbackInfo ci) {
		if (Bots.silenced(message)) {
			ci.cancel();
		}
	}

	/**
	 * Bots take no place a person could join in: the "server is full" check of a login counts people only, so bots may
	 * fill lobbies past the server's player limit (a world opened to LAN has eight places).
	 */
	@Redirect(method = "canPlayerLogin", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
	private int battlehub$peopleOnly(List<ServerPlayer> players) {
		return Bots.people(players);
	}

	/** The self-test's player limit (a world opened to LAN has eight places). */
	@Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
	private void battlehub$testLimit(CallbackInfoReturnable<Integer> cir) {
		if (net.battlehub.debug.SelfTest.playerLimit > 0) {
			cir.setReturnValue(net.battlehub.debug.SelfTest.playerLimit);
		}
	}

	/** Bots leave no player files behind. */
	@Inject(method = "save(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void battlehub$noBotFiles(ServerPlayer player, CallbackInfo ci) {
		if (Bots.isBot(player.getUUID())) {
			ci.cancel();
		}
	}
}
