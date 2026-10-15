package net.battlehub.bot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.BotLevel;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.core.SideQueue;
import net.battlehub.hub.Travel;
import net.battlehub.lang.Lang;
import net.battlehub.match.Matches;
import net.battlehub.match.TeamFightMatch;
import net.battlehub.queue.Parties;
import net.battlehub.queue.Queues;
import net.battlehub.queue.TeamFights;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/**
 * The bots: spawned when a player would otherwise wait too long (a duel queue, a party lobby, 1v2 and 1v3) or
 * asked for (practice), gone again a few seconds after their match. Their names all start with "Bot_", they never
 * play ranked, nothing about them is saved, and matches with bots do not count for anyone's stats.
 */
public final class Bots {
	private Bots() {}

	private static final String[] NAMES = {"Aiden", "Blaze", "Cosmo", "Dex", "Echo", "Finn", "Ghost", "Hex", "Iris",
			"Jinx", "Kai", "Luna", "Milo", "Nova", "Onyx", "Pixel", "Quinn", "Rex", "Sky", "Tess", "Ula", "Vex", "Wren",
			"Xeno", "Yuki", "Zed", "Ace", "Bolt", "Cleo", "Dash", "Ember", "Frost", "Gizmo", "Hawk", "Ivy", "Jett",
			"Koda", "Lux", "Mars", "Nyx", "Orbit", "Pip", "Rune", "Sage", "Turbo", "Volt", "Wisp", "Zara"};
	public static final String PREFIX = "Bot_";
	/** Every possible bot id, known up front, so a bot that already left is still recognised. */
	private static final Map<UUID, String> POOL = new LinkedHashMap<>();

	static {
		for (String n : NAMES) {
			POOL.put(uuid(PREFIX + n), PREFIX + n);
		}
	}

	private static final Map<UUID, BotPlayer> ONLINE = new LinkedHashMap<>();
	private static final Map<UUID, Integer> IDLE = new HashMap<>();
	private static final Map<GameMode, Long> PARTY_SINCE = new EnumMap<>(GameMode.class);
	private static final Map<String, Long> SIDE_SINCE = new HashMap<>();
	private static int tick;

	public static UUID uuid(String name) {
		return UUID.nameUUIDFromBytes(("BattleHubBot:" + name).getBytes(StandardCharsets.UTF_8));
	}

	public static boolean isBot(UUID id) {
		return id != null && POOL.containsKey(id);
	}

	public static boolean isBot(ServerPlayer p) {
		return p instanceof BotPlayer || (p != null && isBot(p.getUUID()));
	}

	public static int online() {
		return ONLINE.size();
	}

	public static List<BotPlayer> all() {
		return List.copyOf(ONLINE.values());
	}

	private static HubConfig.Bots cfg() {
		return ConfigManager.config().bots;
	}

	/** A new bot in the lobby, or null (bots off, too many, or the server is nearly full). */
	public static BotPlayer spawn(MinecraftServer server, BotLevel skill) {
		HubConfig.Bots c = cfg();
		if (!c.enabled || ONLINE.size() >= c.maxBots || server.getPlayerCount() >= server.getMaxPlayers() - 1) {
			return null;
		}
		String name = null;
		List<String> free = new ArrayList<>();
		for (String n : POOL.values()) {
			if (server.getPlayerList().getPlayerByName(n) == null) free.add(n);
		}
		if (free.isEmpty()) {
			return null;
		}
		name = free.get((int) (Math.random() * free.size()));
		GameProfile profile = new GameProfile(uuid(name), name);
		BotPlayer p = new BotPlayer(server, server.overworld(), profile, skill);
		try {
			server.getPlayerList().placeNewPlayer(new BotConnection(), p, new CommonListenerCookie(profile, 0, p.clientInformation(), false));
		} catch (RuntimeException e) {
			BattleHub.LOGGER.warn("Could not add a bot: {}", e.toString());
			return null;
		}
		ONLINE.put(p.getUUID(), p);
		IDLE.remove(p.getUUID());
		if (!Travel.inBattleHub(p)) {
			Travel.enter(p);
		}
		return p;
	}

	/** Takes a bot off the server. */
	public static void remove(BotPlayer p) {
		ONLINE.remove(p.getUUID());
		IDLE.remove(p.getUUID());
		if (p.hasDisconnected()) {
			return;
		}
		Queues.leaveAll(p);
		p.connection.onDisconnect(new DisconnectionDetails(Component.literal("Bot")));
	}

	/** Join and leave messages and advancements of bots are not shown in chat. */
	public static boolean silenced(Component message) {
		if (message == null || !(message.getContents() instanceof TranslatableContents tc)) {
			return false;
		}
		String key = tc.getKey();
		if (!key.startsWith("multiplayer.player.") && !key.startsWith("chat.type.advancement.")) {
			return false;
		}
		Object[] args = tc.getArgs();
		if (args.length == 0) {
			return false;
		}
		String who = args[0] instanceof Component c ? c.getString() : String.valueOf(args[0]);
		return POOL.containsValue(who);
	}

	// ------------------------------------------------------------------ practice

	/** A duel against a bot right now. */
	public static boolean practice(ServerPlayer p, GameMode mode, BotLevel skill) {
		if (!mode.isDuel() || !Queues.canQueue(p, mode)) {
			return false;
		}
		MinecraftServer server = p.level().getServer();
		BotPlayer bot = spawn(server, skill);
		if (bot == null) {
			Msg.send(p, "bots.unavailable");
			return false;
		}
		Queues.leaveAll(p);
		if (!Queues.startDuel(server, mode, false, DuelFormat.BO1, true, p, bot)) {
			remove(bot);
			Msg.send(p, "queue.arenas_full");
			return false;
		}
		Msg.send(p, "bots.practice_start", Lang.tr(p, "bots.level." + skill.id()), Lang.tr(p, net.battlehub.gui.Icons.modeKey(mode)));
		return true;
	}

	/** 1v2 or 1v3 alone against a team of bots. */
	public static boolean practiceTeam(ServerPlayer p, GameMode mode, GameMode kit, BotLevel skill) {
		if (!mode.isTeamFight() || !Queues.canQueue(p, mode)) {
			return false;
		}
		MinecraftServer server = p.level().getServer();
		List<ServerPlayer> team = new ArrayList<>();
		for (int i = 0; i < mode.opponents(); i++) {
			BotPlayer bot = spawn(server, skill);
			if (bot == null) {
				for (ServerPlayer b : team) remove((BotPlayer) b);
				Msg.send(p, "bots.unavailable");
				return false;
			}
			team.add(bot);
		}
		Queues.leaveAll(p);
		TeamFightMatch m = new TeamFightMatch(Matches.newId(), mode, TeamFights.kitOrDefault(kit), server, p, team);
		if (!Matches.start(m)) {
			for (ServerPlayer b : team) remove((BotPlayer) b);
			Msg.send(p, "queue.arenas_full");
			return false;
		}
		Msg.send(p, "bots.practice_start", Lang.tr(p, "bots.level." + skill.id()), Lang.tr(p, net.battlehub.gui.Icons.modeKey(mode)));
		return true;
	}

	// ------------------------------------------------------------------ every tick

	public static void tick(MinecraftServer server) {
		tick++;
		if (tick % 20 != 0) {
			return;
		}
		for (BotPlayer b : List.copyOf(ONLINE.values())) {
			if (b.hasDisconnected() || server.getPlayerList().getPlayer(b.getUUID()) != b) {
				ONLINE.remove(b.getUUID());
				IDLE.remove(b.getUUID());
				continue;
			}
			// Back in the lobby without a queue: gone after a few seconds
			if (Sessions.isFree(b.getUUID())) {
				int idle = IDLE.merge(b.getUUID(), 1, Integer::sum);
				if (idle >= Math.max(1, cfg().idleSeconds) || !cfg().enabled) {
					remove(b);
				}
			} else {
				IDLE.remove(b.getUUID());
			}
		}
		HubConfig.Bots c = cfg();
		if (c.enabled && c.fillQueues) {
			try {
				fillDuels(server);
				fillParties(server);
				fillTeamFights(server);
			} catch (RuntimeException e) {
				BattleHub.LOGGER.warn("Bots could not fill a queue: {}", e.toString());
			}
		}
	}

	private static BotLevel fillLevel() {
		return BotLevel.parse(cfg().fillLevel, BotLevel.NORMAL);
	}

	private static long waitTicks() {
		return Math.max(0, cfg().fillAfterSeconds) * 20L;
	}

	/** One player alone in a casual duel queue for a while: a bot joins it. Two players: the bots step out. */
	private static void fillDuels(MinecraftServer server) {
		for (GameMode mode : GameMode.duels()) {
			for (DuelFormat f : DuelFormat.values()) {
				List<UUID> waiting = Queues.waitingIds(mode, false, f);
				List<UUID> humans = new ArrayList<>();
				List<UUID> bots = new ArrayList<>();
				for (UUID id : waiting) {
					(isBot(id) ? bots : humans).add(id);
				}
				if (humans.size() != 1) {
					// Nobody to play with, or enough people: no bots needed
					for (UUID b : bots) leaveQueues(server, b);
					continue;
				}
				if (!bots.isEmpty()) continue;
				long since = Queues.queuedSince(humans.get(0));
				if (since < 0 || Tasks.now() - since < waitTicks()) continue;
				BotPlayer bot = spawn(server, fillLevel());
				if (bot != null) {
					Queues.join(bot, mode, false, f);
				}
			}
		}
	}

	/** Party lobbies with players waiting are filled up to the configured size; empty of players, the bots leave. */
	private static void fillParties(MinecraftServer server) {
		for (GameMode mode : GameMode.parties()) {
			List<UUID> members = Parties.members(mode);
			int humans = 0;
			for (UUID id : members) {
				if (!isBot(id)) humans++;
			}
			if (humans == 0) {
				PARTY_SINCE.remove(mode);
				for (UUID id : members) {
					if (isBot(id)) leaveQueues(server, id);
				}
				continue;
			}
			long since = PARTY_SINCE.computeIfAbsent(mode, m -> Tasks.now());
			if (Tasks.now() - since < waitTicks()) continue;
			HubConfig.Game g = ConfigManager.config().game(mode);
			int wanted = Math.min(g.maxPlayers, Math.max(g.minPlayers, cfg().partyFillTo));
			if (mode == GameMode.TOURNAMENT) {
				wanted = Math.min(g.maxPlayers, Math.max(wanted, 4));
			}
			if (members.size() >= wanted) continue;
			BotPlayer bot = spawn(server, fillLevel());
			if (bot != null) {
				Parties.join(bot, mode);
			}
		}
	}

	/** Someone waiting for 1v2 or 1v3: bots take the places left. */
	private static void fillTeamFights(MinecraftServer server) {
		for (GameMode mode : GameMode.teamFights()) {
			for (GameMode kit : GameMode.meleeKits()) {
				String key = mode.id() + ":" + kit.id();
				List<UUID> waiting = TeamFights.waitingIds(mode, kit);
				boolean human = false;
				for (UUID id : waiting) {
					if (!isBot(id)) human = true;
				}
				if (!human) {
					SIDE_SINCE.remove(key);
					for (UUID id : waiting) leaveQueues(server, id);
					continue;
				}
				long since = SIDE_SINCE.computeIfAbsent(key, k -> Tasks.now());
				if (Tasks.now() - since < waitTicks() || waiting.size() >= mode.opponents() + 1) continue;
				BotPlayer bot = spawn(server, fillLevel());
				if (bot != null) {
					TeamFights.join(bot, mode, kit, SideQueue.Side.ANY);
				}
			}
		}
	}

	private static void leaveQueues(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null && isBot(id)) {
			Queues.leaveAll(p);
		}
	}

	/** Server stopping: forget everything (the players list disconnects the bots itself). */
	public static void clear() {
		ONLINE.clear();
		IDLE.clear();
		PARTY_SINCE.clear();
		SIDE_SINCE.clear();
	}
}
