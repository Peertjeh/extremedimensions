package net.battlehub.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.battlehub.HubEvents;
import net.battlehub.bot.Bots;
import net.battlehub.hub.HubWorld;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * A player leaving the server (disconnect, kick, server stop). Runs before vanilla saves the player file, so the
 * inventory BattleHub gives back is the one that gets saved. Bots stay out of chat and leave no files, and chat stays
 * in its world.
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

	/**
	 * Chat stays in its world (1.50): a message is only sent to the players who share the sender's world, BattleHub or
	 * the normal world, unless it starts with the global prefix ({@link HubWorld#hearsChat}). Chat, /me and /say all pass
	 * here; the server log still gets every message. Private and team messages do not come through here and reach their
	 * players anywhere.
	 */
	@Redirect(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
	private void battlehub$chatInItsWorld(ServerPlayer receiver, OutgoingChatMessage message, boolean filtered, ChatType.Bound type,
			PlayerChatMessage original, Predicate<ServerPlayer> filter, ServerPlayer sender) {
		if (HubWorld.hearsChat(sender, receiver, original.signedContent())) {
			receiver.sendChatMessage(message, filtered, type);
		}
	}
}
