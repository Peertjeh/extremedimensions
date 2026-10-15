package net.battlehub.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Friends: a request that the other accepts (also when they were offline: it waits in their profile), a list
 * that shows who is online and what they are doing, and a note to your friends when you come into BattleHub.
 */
public final class Friends {
	private Friends() {}

	public static final int MAX = 100;

	private static UUID id(PlayerProfile p) {
		try {
			return UUID.fromString(p.uuid);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** /friend add: a request, or accepting theirs when they already asked. */
	public static void add(ServerPlayer from, String name) {
		PlayerProfile target = PlayerStore.find(name);
		PlayerProfile me = PlayerStore.get(from);
		if (target == null || target.uuid.equals(me.uuid) || Bots.isBot(id(target))) {
			Msg.send(from, "friend.unknown", name);
			return;
		}
		if (me.friends.contains(target.uuid)) {
			Msg.send(from, "friend.already", target.name);
			return;
		}
		if (me.friendRequests.contains(target.uuid)) {
			accept(from, target.name);
			return;
		}
		if (me.friends.size() >= MAX) {
			Msg.send(from, "friend.full", MAX);
			return;
		}
		target.friendRequests.add(me.uuid);
		PlayerStore.save(id(target));
		Msg.send(from, "friend.sent", target.name);
		ServerPlayer t = online(from.level().getServer(), target);
		if (t != null) {
			tellRequest(t, me.name);
		}
	}

	private static void tellRequest(ServerPlayer to, String fromName) {
		Msg.send(to, "friend.request", fromName);
		Msg.raw(to, Component.empty().append(Component.literal("  "))
				.append(Lang.tr(to, "friend.accept_button").withStyle(s -> s
						.withClickEvent(new ClickEvent.RunCommand("/friend accept " + fromName))
						.withHoverEvent(new HoverEvent.ShowText(Lang.tr(to, "friend.accept_hover", fromName)))))
				.append(Component.literal(" "))
				.append(Lang.tr(to, "friend.deny_button").withStyle(s -> s
						.withClickEvent(new ClickEvent.RunCommand("/friend deny " + fromName)))));
		Msg.sound(to, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.4f);
	}

	public static void accept(ServerPlayer p, String name) {
		PlayerProfile me = PlayerStore.get(p);
		PlayerProfile other = PlayerStore.find(name);
		if (other == null || !me.friendRequests.remove(other.uuid)) {
			Msg.send(p, "friend.no_request", name);
			return;
		}
		me.friends.add(other.uuid);
		other.friends.add(me.uuid);
		other.friendRequests.remove(me.uuid);
		PlayerStore.save(p.getUUID());
		PlayerStore.save(id(other));
		Msg.send(p, "friend.added", other.name);
		ServerPlayer o = online(p.level().getServer(), other);
		if (o != null) {
			Msg.send(o, "friend.added", me.name);
		}
	}

	public static void deny(ServerPlayer p, String name) {
		PlayerProfile me = PlayerStore.get(p);
		PlayerProfile other = PlayerStore.find(name);
		if (other != null && me.friendRequests.remove(other.uuid)) {
			PlayerStore.save(p.getUUID());
			Msg.send(p, "friend.denied", other.name);
		} else {
			Msg.send(p, "friend.no_request", name);
		}
	}

	public static void remove(ServerPlayer p, String name) {
		PlayerProfile me = PlayerStore.get(p);
		PlayerProfile other = PlayerStore.find(name);
		if (other == null || !me.friends.remove(other.uuid)) {
			Msg.send(p, "friend.not_friends", name);
			return;
		}
		other.friends.remove(me.uuid);
		PlayerStore.save(p.getUUID());
		PlayerStore.save(id(other));
		Msg.send(p, "friend.removed", other.name);
	}

	public static boolean areFriends(UUID a, UUID b) {
		return PlayerStore.get(a).friends.contains(b.toString());
	}

	/** Friends of a player, online ones first. */
	public static List<PlayerProfile> list(MinecraftServer server, ServerPlayer p) {
		List<PlayerProfile> online = new ArrayList<>();
		List<PlayerProfile> offline = new ArrayList<>();
		for (String f : PlayerStore.get(p).friends) {
			try {
				PlayerProfile prof = PlayerStore.get(UUID.fromString(f));
				(server.getPlayerList().getPlayer(UUID.fromString(f)) != null ? online : offline).add(prof);
			} catch (IllegalArgumentException ignored) {
				// skip broken ids
			}
		}
		online.addAll(offline);
		return online;
	}

	/** What a friend is doing: offline, in the normal world, in the hub, waiting for a mode, playing a mode. */
	public static Component status(ServerPlayer viewer, MinecraftServer server, PlayerProfile friend) {
		UUID id = id(friend);
		ServerPlayer f = id == null ? null : server.getPlayerList().getPlayer(id);
		if (f == null) {
			return Lang.tr(viewer, "friend.status.offline");
		}
		if (!net.battlehub.hub.Travel.inBattleHub(f)) {
			return Lang.tr(viewer, "friend.status.world");
		}
		return switch (Sessions.state(id)) {
			case QUEUED -> Lang.tr(viewer, "friend.status.queued");
			case MATCH, SPECTATING -> {
				Match m = Matches.of(id);
				yield m == null ? Lang.tr(viewer, "friend.status.hub")
						: Lang.tr(viewer, "friend.status.playing", Lang.tr(viewer, Icons.modeKey(m.mode)));
			}
			default -> Lang.tr(viewer, "friend.status.hub");
		};
	}

	/** A player came into BattleHub: their friends hear it, and they hear about waiting requests. */
	public static void entered(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		PlayerProfile me = PlayerStore.get(p);
		for (String f : me.friends) {
			try {
				ServerPlayer o = server.getPlayerList().getPlayer(UUID.fromString(f));
				if (o != null && o != p) {
					Msg.send(o, "friend.online", me.name);
				}
			} catch (IllegalArgumentException ignored) {
				// skip broken ids
			}
		}
		for (String r : me.friendRequests) {
			try {
				tellRequest(p, PlayerStore.get(UUID.fromString(r)).name);
			} catch (IllegalArgumentException ignored) {
				// skip broken ids
			}
		}
	}

	static ServerPlayer online(MinecraftServer server, PlayerProfile prof) {
		UUID id = id(prof);
		return id == null ? null : server.getPlayerList().getPlayer(id);
	}
}
