package net.battlehub.hub;

import java.util.EnumMap;
import java.util.Map;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Invitations from BattleHub to the normal world (1.51): when someone has waited a while in a party game's lobby, or its
 * countdown starts with room left, the players in the normal world get a line with a [Join] button that takes them to
 * BattleHub and into that lobby ({@code /battlehub join <mode>}). At most once per game per
 * {@code hub.inviteCooldownMinutes}, and never to bots or to players already in BattleHub (they see the lobby there).
 */
public final class Invites {
	private Invites() {}

	/** When each game last invited the normal world (server ticks). */
	private static final Map<GameMode, Long> LAST = new EnumMap<>(GameMode.class);
	/** Since when each game's lobby has had someone waiting without a countdown (server ticks). */
	private static final Map<GameMode, Long> WAITING = new EnumMap<>(GameMode.class);

	/** Once a second for every party lobby: someone waiting long enough without a countdown invites the normal world. */
	public static void lobby(MinecraftServer server, GameMode mode, int size, int min, boolean counting) {
		if (size == 0 || counting) {
			WAITING.remove(mode);
			return;
		}
		long now = server.getTickCount();
		long since = WAITING.computeIfAbsent(mode, m -> now);
		if (now - since >= ConfigManager.config().hub.inviteAfterSeconds * 20L) {
			send(server, mode, "invite.waiting", size, Math.max(1, min - size));
		}
	}

	/** A party lobby's countdown started: with room left, the normal world is invited. */
	public static void countdown(MinecraftServer server, GameMode mode, int size, int max, int seconds) {
		if (size < max) {
			send(server, mode, "invite.countdown", seconds, size, max);
		}
	}

	private static void send(MinecraftServer server, GameMode mode, String key, Object... args) {
		var hub = ConfigManager.config().hub;
		if (!hub.invites) return;
		long now = server.getTickCount();
		Long last = LAST.get(mode);
		if (last != null && now - last < hub.inviteCooldownMinutes * 1200L) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(p) || Travel.inBattleHub(p)) continue;
			Object[] all = new Object[args.length + 1];
			all[0] = Lang.tr(p, Icons.modeKey(mode));
			System.arraycopy(args, 0, all, 1, args.length);
			Msg.send(p, key, all);
			Msg.raw(p, Msg.button(p, "invite.join_button", "invite.join_hover", "/battlehub join " + mode.id()));
			Msg.sound(p, SoundEvents.NOTE_BLOCK_BELL, 0.6f, 1.4f);
			// the cooldown only counts when someone was invited: a game started while nobody was outside invites the next one
			LAST.put(mode, now);
		}
	}

	public static void clear() {
		LAST.clear();
		WAITING.clear();
	}
}
