package net.battlehub.queue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.gui.PlayerPickerMenu;
import net.battlehub.lang.Lang;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Duel challenges between two players ("/duel <player>", or Challenge in a mode menu). */
public final class Challenges {
	private Challenges() {}

	public record Challenge(UUID from, UUID to, String fromName, GameMode mode, DuelFormat format, long expires) {}

	private static final List<Challenge> PENDING = new ArrayList<>();

	public static void openPicker(ServerPlayer p, GameMode mode) {
		new PlayerPickerMenu(p, mode).open();
	}

	/** Sends a challenge (duel modes) or an invite (party games). */
	/** Sends a challenge in the sender's chosen format (duel modes) or an invite (party games). */
	public static void send(ServerPlayer from, ServerPlayer to, GameMode mode) {
		send(from, to, mode, Queues.formatOf(from));
	}

	public static void send(ServerPlayer from, ServerPlayer to, GameMode mode, DuelFormat format) {
		if (from == to) {
			Msg.send(from, "challenge.self");
			return;
		}
		if (mode.isParty()) {
			Parties.invite(from, to, mode);
			return;
		}
		if (!Queues.canQueue(from, mode)) {
			return;
		}
		if (!Sessions.inLobby(to.getUUID())) {
			Msg.send(from, "challenge.busy", to.getGameProfile().name());
			return;
		}
		PENDING.removeIf(c -> c.from().equals(from.getUUID()) && c.to().equals(to.getUUID()));
		long expires = Tasks.now() + ConfigManager.config().timings.challengeExpireSeconds * 20L;
		PENDING.add(new Challenge(from.getUUID(), to.getUUID(), from.getGameProfile().name(), mode, format, expires));
		PlayAgain.forget(from.getUUID());
		Msg.send(from, "challenge.sent", to.getGameProfile().name(), Queues.modeLabel(from, mode, format));
		String name = from.getGameProfile().name();
		MutableComponent accept = Lang.tr(to, "challenge.accept_button").withStyle(s -> s
				.withClickEvent(new ClickEvent.RunCommand("/duel accept " + name))
				.withHoverEvent(new HoverEvent.ShowText(Lang.tr(to, "challenge.accept_hover"))));
		MutableComponent deny = Lang.tr(to, "challenge.deny_button").withStyle(s -> s
				.withClickEvent(new ClickEvent.RunCommand("/duel deny " + name))
				.withHoverEvent(new HoverEvent.ShowText(Lang.tr(to, "challenge.deny_hover"))));
		Msg.send(to, "challenge.received", name, Queues.modeLabel(to, mode, format));
		Msg.raw(to, Component.empty().append(Component.literal("  ")).append(accept).append(Component.literal("  ")).append(deny));
		Msg.sound(to, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.2f);
	}

	public static boolean accept(ServerPlayer to, ServerPlayer from) {
		Challenge c = find(from.getUUID(), to.getUUID());
		if (c == null) {
			Msg.send(to, "challenge.none", from.getGameProfile().name());
			return false;
		}
		PENDING.remove(c);
		if (!Sessions.inLobby(from.getUUID()) || !net.battlehub.hub.Travel.inBattleHub(from)) {
			Msg.send(to, "challenge.busy", from.getGameProfile().name());
			return false;
		}
		if (!Queues.canQueue(to, c.mode()) || !Queues.canQueue(from, c.mode())) {
			return false;
		}
		Queues.leaveAll(to);
		Queues.leaveAll(from);
		PENDING.removeIf(x -> x.to().equals(to.getUUID()) || x.to().equals(from.getUUID()));
		MinecraftServer server = to.level().getServer();
		if (!Queues.startDuel(server, c.mode(), false, c.format(), true, from, to)) {
			Msg.send(to, "queue.arenas_full_challenge");
			Msg.send(from, "queue.arenas_full_challenge");
			return false;
		}
		return true;
	}

	public static boolean deny(ServerPlayer to, ServerPlayer from) {
		Challenge c = find(from.getUUID(), to.getUUID());
		if (c == null) {
			Msg.send(to, "challenge.none", from.getGameProfile().name());
			return false;
		}
		PENDING.remove(c);
		Msg.send(to, "challenge.denied_you", from.getGameProfile().name());
		Msg.send(from, "challenge.denied", to.getGameProfile().name());
		return true;
	}

	private static Challenge find(UUID from, UUID to) {
		for (Challenge c : PENDING) {
			if (c.from().equals(from) && c.to().equals(to) && c.expires() > Tasks.now()) {
				return c;
			}
		}
		return null;
	}

	public static boolean hasPending(UUID from, UUID to) {
		return find(from, to) != null;
	}

	static void tick(MinecraftServer server) {
		Iterator<Challenge> it = PENDING.iterator();
		while (it.hasNext()) {
			Challenge c = it.next();
			if (c.expires() <= Tasks.now()) {
				it.remove();
				ServerPlayer from = server.getPlayerList().getPlayer(c.from());
				ServerPlayer to = server.getPlayerList().getPlayer(c.to());
				if (from != null && to != null) {
					Msg.send(from, "challenge.expired", to.getGameProfile().name());
				}
			}
		}
	}

	static void forget(UUID player) {
		PENDING.removeIf(c -> c.from().equals(player) || c.to().equals(player));
	}

	static void clear() {
		PENDING.clear();
	}
}
