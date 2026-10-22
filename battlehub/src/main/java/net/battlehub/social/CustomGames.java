package net.battlehub.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.core.TeamSplit;
import net.battlehub.games.PartyGames;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.DuelMatch;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Private games: someone opens a lobby for a mode and gets a four-letter code; friends join with the code; the
 * owner starts when everyone is there. Private games never count for stats, rewards or ELO.
 */
public final class CustomGames {
	private CustomGames() {}

	public static final class Lobby {
		public final String code;
		public UUID owner;
		public final GameMode mode;
		public final Set<UUID> members = new LinkedHashSet<>();
		/** Votes for the game (the kit in Free for All, the format in Bed Wars, the difficulty in parkour). */
		public final Map<String, String> options = new HashMap<>();
		/** Bots the owner adds at the start (none, a number, or as many as fit), and their level. */
		public net.battlehub.core.BotFill.Wish bots = net.battlehub.core.BotFill.Wish.NONE;
		public net.battlehub.core.ai.Difficulty botLevel = net.battlehub.bot.Bots.defaultLevel();

		Lobby(String code, UUID owner, GameMode mode) {
			this.code = code;
			this.owner = owner;
			this.mode = mode;
			members.add(owner);
		}
	}

	private static final Map<String, Lobby> LOBBIES = new LinkedHashMap<>();
	private static final Random RANDOM = new Random();

	public static Lobby of(UUID id) {
		for (Lobby l : LOBBIES.values()) {
			if (l.members.contains(id)) return l;
		}
		return null;
	}

	public static Lobby byCode(String code) {
		return code == null ? null : LOBBIES.get(code.toUpperCase(Locale.ROOT));
	}

	/** Modes that can be played privately: duels and every party game except the tournament. */
	public static boolean allowed(GameMode m) {
		return m != null && (m.isDuel() || (m.isParty() && m != GameMode.TOURNAMENT));
	}

	public static Lobby create(ServerPlayer p, GameMode mode) {
		if (!allowed(mode)) {
			Msg.send(p, "custom.mode");
			return null;
		}
		if (!Queues.canQueue(p, mode)) {
			return null;
		}
		leave(p, true);
		Queues.leaveAll(p);
		String code;
		do {
			code = TeamSplit.code(RANDOM, 4);
		} while (LOBBIES.containsKey(code));
		Lobby l = new Lobby(code, p.getUUID(), mode);
		LOBBIES.put(code, l);
		Msg.send(p, "custom.created", Lang.tr(p, Icons.modeKey(mode)), code);
		return l;
	}

	/** The most players this private game takes. */
	public static int maxPlayers(Lobby l) {
		return l.mode.isDuel() ? 2 : net.battlehub.config.ConfigManager.config().game(l.mode).maxPlayers;
	}

	public static boolean join(ServerPlayer p, String code) {
		Lobby l = byCode(code);
		if (l == null) {
			Msg.send(p, "custom.unknown", code);
			return false;
		}
		if (l.members.contains(p.getUUID())) {
			return true;
		}
		if (l.members.size() >= maxPlayers(l)) {
			Msg.send(p, "custom.full");
			return false;
		}
		if (!Queues.canQueue(p, l.mode)) {
			return false;
		}
		leave(p, true);
		Queues.leaveAll(p);
		l.members.add(p.getUUID());
		tell(p.level().getServer(), l, "custom.joined", p.getGameProfile().name(), l.members.size());
		return true;
	}

	public static void leave(ServerPlayer p, boolean quiet) {
		Lobby l = of(p.getUUID());
		if (l == null) {
			return;
		}
		l.members.remove(p.getUUID());
		if (!quiet) Msg.send(p, "custom.left");
		MinecraftServer server = p.level().getServer();
		if (l.members.isEmpty()) {
			LOBBIES.remove(l.code);
			return;
		}
		if (l.owner.equals(p.getUUID())) {
			l.owner = l.members.iterator().next();
		}
		tell(server, l, "custom.member_left", p.getGameProfile().name());
	}

	/** The owner starts the game with everyone in the lobby who is still free. */
	public static boolean start(ServerPlayer p) {
		Lobby l = of(p.getUUID());
		if (l == null || !l.owner.equals(p.getUUID())) {
			Msg.send(p, "custom.not_owner");
			return false;
		}
		MinecraftServer server = p.level().getServer();
		List<ServerPlayer> players = new ArrayList<>();
		for (UUID id : l.members) {
			ServerPlayer m = server.getPlayerList().getPlayer(id);
			if (m != null && net.battlehub.hub.Travel.inBattleHub(m) && Sessions.inLobby(id)) players.add(m);
		}
		// the owner's bots take places nobody took
		List<net.battlehub.bot.BotPlayer> bots = new ArrayList<>();
		int wanted = net.battlehub.core.BotFill.custom(l.bots, players.size(), maxPlayers(l));
		for (int i = 0; i < wanted; i++) {
			net.battlehub.bot.BotPlayer bot = net.battlehub.bot.Bots.spawn(server, l.botLevel);
			if (bot == null) {
				Msg.send(p, "custom.bots_limit", bots.size());
				break;
			}
			bots.add(bot);
		}
		players.addAll(bots);
		int min = l.mode == GameMode.PARKOUR ? 1 : 2;
		if (players.size() < min) {
			for (net.battlehub.bot.BotPlayer b : bots) net.battlehub.bot.Bots.remove(b);
			Msg.send(p, "custom.too_few", min);
			return false;
		}
		Match m;
		if (l.mode.isDuel()) {
			m = net.battlehub.match.Duels.create(Matches.newId(), l.mode, false, DuelFormat.BO1, true, server, players.get(0), players.get(1));
		} else {
			m = PartyGames.create(l.mode, server, players, l.options);
		}
		if (m == null) {
			for (net.battlehub.bot.BotPlayer b : bots) net.battlehub.bot.Bots.remove(b);
			return false;
		}
		m.setUnrated(true);
		for (ServerPlayer pl : players) {
			Queues.leaveAll(pl);
		}
		if (!Matches.start(m)) {
			for (net.battlehub.bot.BotPlayer b : bots) net.battlehub.bot.Bots.remove(b);
			Msg.send(p, "queue.arenas_full");
			return false;
		}
		for (ServerPlayer pl : players) {
			if (!net.battlehub.bot.Bots.isBot(pl)) HubItems.ensure(pl, false);
		}
		LOBBIES.remove(l.code);
		return true;
	}

	private static void tell(MinecraftServer server, Lobby l, String key, Object... args) {
		for (UUID id : l.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) Msg.send(p, key, args);
		}
	}

	public static void forget(ServerPlayer p) {
		leave(p, true);
	}

	public static void clear() {
		LOBBIES.clear();
	}
}
