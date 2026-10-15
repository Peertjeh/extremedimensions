package net.battlehub.social;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Parties of friends who play together (/party): the leader joins a game and everyone free in the party joins
 * the same lobby; team games put the party on one team. Kept in memory; leaving the server leaves the party.
 */
public final class Groups {
	private Groups() {}

	public static final int MAX = 8;
	private static final long INVITE_TICKS = 60 * 20;

	public static final class Group {
		UUID leader;
		final Set<UUID> members = new LinkedHashSet<>();
		final Map<UUID, Long> invites = new HashMap<>();

		Group(UUID leader) {
			this.leader = leader;
			members.add(leader);
		}

		public UUID leader() {
			return leader;
		}

		public Set<UUID> members() {
			return Set.copyOf(members);
		}
	}

	private static final Map<UUID, Group> BY_MEMBER = new HashMap<>();

	public static Group of(UUID id) {
		return BY_MEMBER.get(id);
	}

	public static boolean isLeader(UUID id) {
		Group g = BY_MEMBER.get(id);
		return g != null && g.leader.equals(id);
	}

	private static void tell(MinecraftServer server, Group g, String key, Object... args) {
		for (UUID id : g.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) Msg.send(p, key, args);
		}
	}

	public static void invite(ServerPlayer from, ServerPlayer to) {
		if (to == from || net.battlehub.bot.Bots.isBot(to)) {
			Msg.send(from, "group.cannot_invite");
			return;
		}
		Group g = BY_MEMBER.computeIfAbsent(from.getUUID(), Group::new);
		if (!g.leader.equals(from.getUUID())) {
			Msg.send(from, "group.not_leader");
			return;
		}
		if (g.members.contains(to.getUUID())) {
			Msg.send(from, "group.already_in", to.getGameProfile().name());
			return;
		}
		if (g.members.size() >= MAX) {
			Msg.send(from, "group.full", MAX);
			return;
		}
		g.invites.put(to.getUUID(), Tasks.now() + INVITE_TICKS);
		String leader = from.getGameProfile().name();
		Msg.send(from, "group.invited", to.getGameProfile().name());
		Msg.send(to, "group.invite", leader);
		Msg.raw(to, Component.empty().append(Component.literal("  ")).append(Lang.tr(to, "group.accept_button").withStyle(s -> s
				.withClickEvent(new ClickEvent.RunCommand("/party accept " + leader))
				.withHoverEvent(new HoverEvent.ShowText(Lang.tr(to, "group.accept_hover", leader))))));
		Msg.sound(to, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.2f);
	}

	public static void accept(ServerPlayer p, ServerPlayer leader) {
		Group g = leader == null ? null : BY_MEMBER.get(leader.getUUID());
		Long until = g == null ? null : g.invites.remove(p.getUUID());
		if (until == null || until < Tasks.now()) {
			Msg.send(p, "group.no_invite");
			return;
		}
		if (g.members.size() >= MAX) {
			Msg.send(p, "group.full", MAX);
			return;
		}
		leave(p, true);
		g.members.add(p.getUUID());
		BY_MEMBER.put(p.getUUID(), g);
		tell(p.level().getServer(), g, "group.joined", p.getGameProfile().name(), g.members.size(), MAX);
	}

	/** Leaves the party; the next one becomes leader, a party of one is gone. */
	public static void leave(ServerPlayer p, boolean quiet) {
		Group g = BY_MEMBER.remove(p.getUUID());
		if (g == null) {
			if (!quiet) Msg.send(p, "group.none");
			return;
		}
		g.members.remove(p.getUUID());
		MinecraftServer server = p.level().getServer();
		if (!quiet) Msg.send(p, "group.left");
		if (g.members.size() <= 1) {
			for (UUID id : g.members) {
				BY_MEMBER.remove(id);
				ServerPlayer o = server.getPlayerList().getPlayer(id);
				if (o != null) Msg.send(o, "group.disbanded");
			}
			g.members.clear();
			return;
		}
		if (g.leader.equals(p.getUUID())) {
			g.leader = g.members.iterator().next();
			ServerPlayer nl = server.getPlayerList().getPlayer(g.leader);
			tell(server, g, "group.new_leader", nl == null ? "?" : nl.getGameProfile().name());
		}
		tell(server, g, "group.member_left", p.getGameProfile().name());
	}

	public static void kick(ServerPlayer leader, ServerPlayer target) {
		Group g = BY_MEMBER.get(leader.getUUID());
		if (g == null || !g.leader.equals(leader.getUUID())) {
			Msg.send(leader, "group.not_leader");
			return;
		}
		if (target == null || !g.members.contains(target.getUUID()) || target == leader) {
			Msg.send(leader, "group.not_member");
			return;
		}
		leave(target, true);
		Msg.send(target, "group.kicked");
	}

	public static void disband(ServerPlayer leader) {
		Group g = BY_MEMBER.get(leader.getUUID());
		if (g == null || !g.leader.equals(leader.getUUID())) {
			Msg.send(leader, "group.not_leader");
			return;
		}
		MinecraftServer server = leader.level().getServer();
		tell(server, g, "group.disbanded");
		for (UUID id : g.members) BY_MEMBER.remove(id);
		g.members.clear();
	}

	public static void chat(ServerPlayer from, String message) {
		Group g = BY_MEMBER.get(from.getUUID());
		if (g == null) {
			Msg.send(from, "group.none");
			return;
		}
		MinecraftServer server = from.level().getServer();
		for (UUID id : g.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) Msg.raw(p, Lang.tr(p, "group.chat", from.getGameProfile().name(), message));
		}
	}

	/** The leader joined a game: everyone else in the party who is free comes along. */
	public static List<ServerPlayer> followers(ServerPlayer leader) {
		Group g = BY_MEMBER.get(leader.getUUID());
		List<ServerPlayer> out = new ArrayList<>();
		if (g == null || !g.leader.equals(leader.getUUID())) {
			return out;
		}
		MinecraftServer server = leader.level().getServer();
		for (UUID id : g.members) {
			if (id.equals(leader.getUUID())) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && net.battlehub.hub.Travel.inBattleHub(p) && net.battlehub.session.Sessions.inLobby(id)) out.add(p);
		}
		return out;
	}

	/** The parties among these players (two or more of them), for keeping them on one team. */
	public static Collection<Set<UUID>> among(Collection<UUID> players) {
		List<Set<UUID>> out = new ArrayList<>();
		Set<Group> seen = new java.util.HashSet<>();
		for (UUID id : players) {
			Group g = BY_MEMBER.get(id);
			if (g != null && seen.add(g)) {
				Set<UUID> in = new LinkedHashSet<>();
				for (UUID m : g.members) {
					if (players.contains(m)) in.add(m);
				}
				if (in.size() >= 2) out.add(in);
			}
		}
		return out;
	}

	/** A player left the server. */
	public static void forget(ServerPlayer p) {
		if (BY_MEMBER.containsKey(p.getUUID())) {
			leave(p, true);
		}
	}

	public static void clear() {
		BY_MEMBER.clear();
	}
}
