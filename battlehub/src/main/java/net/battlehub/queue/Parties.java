package net.battlehub.queue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.Votes;
import net.battlehub.core.PartyLobby;
import net.battlehub.games.PartyGames;
import net.battlehub.gui.Icons;
import net.battlehub.gui.Menus;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
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
 * One waiting lobby per party game. Players stay in the hub while they wait; when the minimum is reached a
 * countdown starts (shorter when the lobby is full), then a new match is created and a fresh lobby opens.
 */
public final class Parties {
	private Parties() {}

	private static final Map<GameMode, PartyLobby> LOBBIES = new EnumMap<>(GameMode.class);
	/** Votes of the players waiting in each lobby (the parkour difficulty). */
	private static final Map<GameMode, Map<UUID, String>> VOTES = new EnumMap<>(GameMode.class);
	/** Each player's last vote per game, for "play again". */
	private static final Map<UUID, Map<GameMode, String>> LAST_VOTE = new HashMap<>();
	private static final java.util.Random RANDOM = new java.util.Random();

	private static PartyLobby lobby(GameMode mode) {
		HubConfig c = ConfigManager.config();
		HubConfig.Game g = c.game(mode);
		PartyLobby l = LOBBIES.computeIfAbsent(mode, m -> new PartyLobby(g.minPlayers, g.maxPlayers,
				c.timings.partyCountdownSeconds, c.timings.partyFullCountdownSeconds));
		l.configure(g.minPlayers, g.maxPlayers, c.timings.partyCountdownSeconds, c.timings.partyFullCountdownSeconds);
		return l;
	}

	static int count(GameMode mode) {
		PartyLobby l = LOBBIES.get(mode);
		return l == null ? 0 : l.size();
	}

	static boolean in(UUID id, GameMode mode) {
		PartyLobby l = LOBBIES.get(mode);
		return l != null && l.contains(id);
	}

	static int secondsLeft(GameMode mode) {
		PartyLobby l = LOBBIES.get(mode);
		return l == null ? -1 : l.secondsLeft();
	}

	public static List<UUID> members(GameMode mode) {
		PartyLobby l = LOBBIES.get(mode);
		return l == null ? List.of() : l.players();
	}

	static void toggle(ServerPlayer p, GameMode mode) {
		if (in(p.getUUID(), mode)) {
			Queues.leaveAll(p);
			Msg.send(p, "party.left", Lang.tr(p, Icons.modeKey(mode)));
		} else {
			join(p, mode);
		}
	}

	public static boolean join(ServerPlayer p, GameMode mode) {
		return join(p, mode, "");
	}

	/** Joins the lobby; {@code vote} is the player's choice for this game (parkour difficulty) or "". */
	public static boolean join(ServerPlayer p, GameMode mode, String vote) {
		if (!mode.isParty() || !Queues.canQueue(p, mode)) {
			return false;
		}
		if (vote != null && !vote.isBlank()) {
			VOTES.computeIfAbsent(mode, m -> new HashMap<>()).put(p.getUUID(), vote);
			LAST_VOTE.computeIfAbsent(p.getUUID(), u -> new EnumMap<>(GameMode.class)).put(mode, vote);
		}
		if (in(p.getUUID(), mode)) {
			return true;
		}
		PlayAgain.forget(p.getUUID());
		Queues.leaveAll(p);
		PartyLobby l = lobby(mode);
		PartyLobby.JoinResult r = l.join(p.getUUID());
		if (r == PartyLobby.JoinResult.FULL) {
			Msg.send(p, "party.full", Lang.tr(p, Icons.modeKey(mode)));
			return false;
		}
		Sessions.toQueue(p.getUUID(), "party:" + mode.id());
		HubItems.ensure(p, true);
		MinecraftServer server = p.level().getServer();
		for (UUID id : l.players()) {
			ServerPlayer o = server.getPlayerList().getPlayer(id);
			if (o != null) {
				Msg.send(o, "party.joined", p.getGameProfile().name(), Lang.tr(o, Icons.modeKey(mode)), l.size(), l.max(), l.min());
			}
		}
		Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.2f);
		Menus.refreshAll(server);
		return true;
	}

	/** Removes the player from every lobby; true if they were in one. */
	static boolean leave(UUID id) {
		boolean was = false;
		for (PartyLobby l : LOBBIES.values()) {
			was |= l.leave(id);
		}
		for (Map<UUID, String> v : VOTES.values()) {
			v.remove(id);
		}
		return was;
	}

	/** This player's vote in the lobby they are waiting in, or "". */
	public static String vote(UUID id, GameMode mode) {
		Map<UUID, String> v = VOTES.get(mode);
		return v == null ? "" : v.getOrDefault(id, "");
	}

	/** The player's last vote for this game, or "". */
	public static String lastVote(UUID id, GameMode mode) {
		Map<GameMode, String> m = LAST_VOTE.get(id);
		return m == null ? "" : m.getOrDefault(mode, "");
	}

	/** Votes of everyone waiting in the lobby. */
	public static java.util.Collection<String> votes(GameMode mode) {
		Map<UUID, String> v = VOTES.get(mode);
		return v == null ? List.of() : List.copyOf(v.values());
	}

	static void invite(ServerPlayer from, ServerPlayer to, GameMode mode) {
		if (!Sessions.inLobby(to.getUUID())) {
			Msg.send(from, "challenge.busy", to.getGameProfile().name());
			return;
		}
		if (!in(from.getUUID(), mode)) {
			join(from, mode);
		}
		Msg.send(from, "party.invite_sent", to.getGameProfile().name(), Lang.tr(from, Icons.modeKey(mode)));
		Msg.send(to, "party.invite_received", from.getGameProfile().name(), Lang.tr(to, Icons.modeKey(mode)));
		Msg.raw(to, Component.empty().append(Component.literal("  ")).append(Lang.tr(to, "party.join_button").withStyle(s -> s
				.withClickEvent(new ClickEvent.RunCommand("/battlehub join " + mode.id()))
				.withHoverEvent(new HoverEvent.ShowText(Lang.tr(to, "party.join_hover", Lang.tr(to, Icons.modeKey(mode))))))));
		Msg.sound(to, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.2f);
	}

	static void tick(MinecraftServer server) {
		for (GameMode mode : GameMode.parties()) {
			PartyLobby l = LOBBIES.get(mode);
			if (l == null) {
				continue;
			}
			for (UUID id : l.players()) {
				if (server.getPlayerList().getPlayer(id) == null || Sessions.state(id) != Sessions.State.QUEUED) {
					l.leave(id);
				}
			}
			PartyLobby.Tick t = l.tick();
			switch (t.event()) {
				case COUNTDOWN_STARTED -> tell(server, l, "party.countdown", mode, t.secondsLeft());
				case COUNTDOWN -> {
					int s = t.secondsLeft();
					if (s == 20 || s == 10 || s <= 5) {
						tell(server, l, "party.countdown", mode, s);
					}
				}
				case CANCELLED -> tell(server, l, "party.cancelled", mode, l.min());
				case START -> start(server, mode, t.players());
				default -> { }
			}
			if (server.getTickCount() % 20 == 0) {
				for (UUID id : l.players()) {
					ServerPlayer p = server.getPlayerList().getPlayer(id);
					if (p == null) continue;
					if (l.counting()) {
						Msg.actionBar(p, "party.actionbar_counting", Lang.tr(p, Icons.modeKey(mode)), l.size(), l.max(), l.secondsLeft());
					} else {
						Msg.actionBar(p, "party.actionbar_waiting", Lang.tr(p, Icons.modeKey(mode)), l.size(), l.max(), Math.max(0, l.min() - l.size()));
					}
				}
			}
		}
	}

	private static void tell(MinecraftServer server, PartyLobby l, String key, GameMode mode, int value) {
		for (UUID id : l.players()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				Msg.send(p, key, Lang.tr(p, Icons.modeKey(mode)), value);
				if (key.equals("party.countdown") && value <= 5) {
					Msg.sound(p, SoundEvents.NOTE_BLOCK_HAT, 1f, 1.2f);
				}
			}
		}
	}

	private static void start(MinecraftServer server, GameMode mode, List<UUID> ids) {
		List<ServerPlayer> players = new ArrayList<>();
		for (UUID id : ids) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && Sessions.state(id) == Sessions.State.QUEUED) {
				players.add(p);
			}
		}
		PartyLobby l = lobby(mode);
		if (players.size() < l.min()) {
			for (ServerPlayer p : players) l.join(p.getUUID());
			return;
		}
		List<String> votes = new ArrayList<>();
		Map<UUID, String> lobbyVotes = VOTES.computeIfAbsent(mode, k -> new HashMap<>());
		for (ServerPlayer p : players) {
			votes.add(lobbyVotes.getOrDefault(p.getUUID(), ""));
		}
		String option = Votes.tally(votes, RANDOM);
		for (ServerPlayer p : players) {
			lobbyVotes.remove(p.getUUID());
		}
		Match m = PartyGames.create(mode, server, players, option);
		if (m == null || !Matches.start(m)) {
			BattleHub.LOGGER.warn("Could not start a {} match (no free arena?)", mode.id());
			for (ServerPlayer p : players) {
				l.join(p.getUUID());
				Msg.send(p, "queue.arenas_full");
			}
			return;
		}
		for (ServerPlayer p : players) {
			HubItems.ensure(p, false);
		}
	}

	static void clear() {
		LOBBIES.clear();
		VOTES.clear();
		LAST_VOTE.clear();
	}
}
