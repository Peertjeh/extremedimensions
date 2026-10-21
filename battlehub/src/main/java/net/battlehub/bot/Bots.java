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
	public static BotPlayer spawn(MinecraftServer server, net.battlehub.core.ai.Difficulty skill) {
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
	public static boolean practice(ServerPlayer p, GameMode mode, net.battlehub.core.ai.Difficulty skill) {
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
	public static boolean practiceTeam(ServerPlayer p, GameMode mode, GameMode kit, net.battlehub.core.ai.Difficulty skill) {
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
		if (tickStart > 0) budget().tick((System.nanoTime() - tickStart) / 1e6, ONLINE.size());
		if (tick % 20 != 0) {
			return;
		}
		long thinks = 0;
		for (BotPlayer b : ONLINE.values()) thinks += b.brain.mind().thinks();
		thinkRate = Math.max(0, thinks - lastThinks);
		lastThinks = thinks;
		SHOW.entrySet().removeIf(e -> !ONLINE.containsKey(e.getValue()) || server.getPlayerList().getPlayer(e.getKey()) == null);
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

	private static net.battlehub.core.ai.Difficulty fillLevel() {
		return net.battlehub.core.ai.Difficulty.parse(cfg().fillLevel, net.battlehub.core.ai.Difficulty.INTERMEDIATE);
	}

	/** Bots may fill a queue or lobby this player waits in (players can say no: /bots, the hotbar button). */
	public static boolean welcome(UUID id) {
		return isBot(id) || !net.battlehub.data.PlayerStore.get(id).noBots;
	}

	/** Can bots ever fill the queue this player waits in now? (Never ranked, never a running tournament.) */
	public static boolean mayFill(UUID id) {
		HubConfig.Bots c = cfg();
		String q = net.battlehub.session.Sessions.get(id).queue;
		return c.enabled && c.fillQueues && !q.startsWith("ranked:") && !q.equals(net.battlehub.tournament.Tournament.QUEUE);
	}

	/** A player says yes or no to bots: remembered, confirmed in chat, the hotbar button swapped. */
	public static void choose(ServerPlayer p, boolean wanted) {
		net.battlehub.core.PlayerProfile prof = net.battlehub.data.PlayerStore.get(p);
		prof.noBots = !wanted;
		net.battlehub.data.PlayerStore.save(p.getUUID());
		Msg.send(p, wanted ? "bots.choice_on" : "bots.choice_off", cfg().fillAfterSeconds);
		Msg.sound(p, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, 0.5f, wanted ? 1.4f : 0.9f);
		net.battlehub.lobby.HubItems.ensure(p, net.battlehub.session.Sessions.state(p.getUUID()) == net.battlehub.session.Sessions.State.QUEUED);
		if (!wanted) {
			// bots already waiting with this player step out at once
			MinecraftServer server = p.level().getServer();
			for (List<UUID> group : groupsOf(p.getUUID())) {
				for (UUID id : group) leaveQueues(server, id);
			}
		}
	}

	/** Right after joining a queue bots may fill: one line on how it works, with a button to switch. */
	public static void hint(ServerPlayer p) {
		if (!mayFill(p.getUUID()) || net.battlehub.session.Sessions.state(p.getUUID()) != net.battlehub.session.Sessions.State.QUEUED) return;
		boolean on = welcome(p.getUUID());
		net.minecraft.network.chat.MutableComponent line = net.minecraft.network.chat.Component.empty()
				.append(net.battlehub.lang.Lang.tr(p, Msg.PREFIX_KEY))
				.append(net.battlehub.lang.Lang.tr(p, on ? "bots.hint_on" : "bots.hint_off", cfg().fillAfterSeconds)).append(" ")
				.append(Msg.button(p, on ? "bots.off_button" : "bots.on_button", on ? "bots.off_hover" : "bots.on_hover",
						on ? "/bots off" : "/bots on"));
		Msg.raw(p, line);
	}

	/** The queues and lobbies this player waits in, with everyone in them. */
	private static List<List<UUID>> groupsOf(UUID id) {
		List<List<UUID>> out = new ArrayList<>();
		for (GameMode mode : GameMode.duels()) {
			for (DuelFormat f : DuelFormat.values()) {
				List<UUID> w = Queues.waitingIds(mode, false, f);
				if (w.contains(id)) out.add(w);
			}
		}
		for (GameMode mode : GameMode.parties()) {
			List<UUID> m = new ArrayList<>(Parties.members(mode));
			if (m.contains(id)) out.add(m);
		}
		for (GameMode mode : GameMode.teamFights()) {
			for (GameMode kit : GameMode.meleeKits()) {
				List<UUID> w = TeamFights.waitingIds(mode, kit);
				if (w.contains(id)) out.add(w);
			}
		}
		return out;
	}

	private static boolean anyoneRefuses(List<UUID> group) {
		for (UUID id : group) {
			if (!welcome(id)) return true;
		}
		return false;
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
				if (humans.size() != 1 || !welcome(humans.get(0))) {
					// Nobody to play with, enough people, or someone who wants no bots: no bots needed
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
			if (anyoneRefuses(members)) {
				// someone in the lobby wants no bots: they wait for people, and bots already there leave
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
				if (!human || anyoneRefuses(waiting)) {
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

	// ------------------------------------------------------------------ the fighting mind

	/**
	 * A fighting mind for a bot: its archetype (given, or picked from {@code bots.archetypes} by its name, the same
	 * every time), a personal variation of that personality, and the level behind its easy/normal/hard skill.
	 */
	static net.battlehub.core.ai.CombatMind newMind(BotPlayer bot, net.battlehub.core.ai.Archetype archetype) {
		long seed = bot.getUUID().getMostSignificantBits() ^ bot.getUUID().getLeastSignificantBits();
		java.util.List<net.battlehub.core.ai.Archetype> pool = new ArrayList<>();
		for (String id : cfg().archetypes) {
			net.battlehub.core.ai.Archetype a = net.battlehub.core.ai.Archetype.parse(id, null);
			if (a != null && !pool.contains(a)) pool.add(a);
		}
		net.battlehub.core.ai.Archetype a = archetype != null ? archetype : net.battlehub.core.ai.Archetype.forSeed(seed, pool);
		net.battlehub.core.ai.CombatMind mind = new net.battlehub.core.ai.CombatMind(a, a.base.individual(seed, cfg().personalityVariation),
				bot.difficulty, seed ^ System.nanoTime());
		mind.gate(Bots::allowThink);
		return mind;
	}

	// ------------------------------------------------------------------ load: think budget and metrics

	private static net.battlehub.core.ai.ThinkBudget budget;
	private static long tickStart;
	private static long lastThinks;
	private static long thinkRate;

	private static net.battlehub.core.ai.ThinkBudget budget() {
		if (budget == null) budget = new net.battlehub.core.ai.ThinkBudget(cfg().slowAboveMspt);
		return budget;
	}

	/** The start of a server tick (to measure how long ticks take). */
	public static void tickStart() {
		tickStart = System.nanoTime();
	}

	/** May a bot think this tick (the server's budget)? */
	static boolean allowThink() {
		return budget().allow();
	}

	/** How many times less often bots think right now (1 = normal). */
	static int thinkStretch() {
		return budget().stretch();
	}

	/** Thinks of all bots together in the last second. */
	public static long thinksPerSecond() {
		return thinkRate;
	}

	/** The load bots put on the server, for {@code /battlehub bots}. */
	public static List<Component> metrics() {
		int inMatch = 0;
		long thinks = 0, nanos = 0, deferred = 0;
		double max = 0;
		for (BotPlayer b : ONLINE.values()) {
			if (Matches.of(b.getUUID()) != null) inMatch++;
			net.battlehub.core.ai.CombatMind m = b.brain.mind();
			thinks += m.thinks();
			nanos += m.thinkNanos();
			deferred += m.deferred();
			max = Math.max(max, m.maxMicros());
		}
		List<Component> out = new ArrayList<>();
		out.add(Lang.tr("bots.metrics.count", ONLINE.size(), inMatch));
		out.add(Lang.tr("bots.metrics.thinking", thinkRate, String.format(java.util.Locale.ROOT, "%.1f", thinks == 0 ? 0 : nanos / 1000.0 / thinks),
				String.format(java.util.Locale.ROOT, "%.0f", max), deferred));
		out.add(Lang.tr("bots.metrics.server", String.format(java.util.Locale.ROOT, "%.1f", budget().mspt()), budget().stretch()));
		return out;
	}

	// ------------------------------------------------------------------ seeing what a bot thinks

	/** Admin -> the bot whose route, target and plans they see as particles. */
	private static final Map<UUID, UUID> SHOW = new HashMap<>();

	/** Shows (or stops showing) {@code viewer} what this bot is doing; true when now on. */
	public static boolean toggleShow(ServerPlayer viewer, BotPlayer b) {
		if (b.getUUID().equals(SHOW.get(viewer.getUUID()))) {
			SHOW.remove(viewer.getUUID());
			return false;
		}
		SHOW.put(viewer.getUUID(), b.getUUID());
		return true;
	}

	/** The admins watching this bot. */
	static List<ServerPlayer> watchers(BotPlayer b) {
		List<ServerPlayer> out = new ArrayList<>();
		for (Map.Entry<UUID, UUID> e : SHOW.entrySet()) {
			if (!e.getValue().equals(b.getUUID())) continue;
			ServerPlayer p = b.level().getServer().getPlayerList().getPlayer(e.getKey());
			if (p != null && p.level() == b.level()) out.add(p);
		}
		return out;
	}

	/** The bot a player is watching, or null. */
	public static UUID showing(UUID viewer) {
		return SHOW.get(viewer);
	}

	/** Damage between players: a bot's mind learns from what it dealt and what it took. */
	public static void onDamage(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (victim instanceof BotPlayer b) {
			b.brain.taken(amount);
			if (source.getEntity() instanceof ServerPlayer from && from != victim) b.brain.hitBy(from.getUUID());
		}
		if (source.getEntity() instanceof BotPlayer a && a != victim) a.brain.dealt(amount);
	}

	private static final Map<UUID, List<String>> LAST_DEBRIEF = new HashMap<>();

	/**
	 * After a match with bots: every player who fought one hears from the bot they traded the most with what it
	 * noticed about their play, how the fight went and one tip.
	 */
	public static void debrief(net.battlehub.match.Match m) {
		for (UUID id : m.participants()) {
			if (isBot(id) || m.leftPlayers().contains(id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null) continue;
			BotPlayer best = null;
			double most = -1;
			for (UUID other : m.participants()) {
				if (!(m.player(other) instanceof BotPlayer b) || !m.isEnemy(id, other)) continue;
				double traded = b.brain.mind().totalDealt() + b.brain.mind().totalTaken();
				if (traded > most) {
					most = traded;
					best = b;
				}
			}
			if (best == null || most <= 0) continue;
			List<String> sent = new ArrayList<>();
			for (net.battlehub.core.ai.Debrief.Line line : best.brain.mind().debrief(best.getGameProfile().name(), id)) {
				Object[] args = line.args().clone();
				for (int i = 0; i < args.length; i++) {
					if (args[i] instanceof String s && s.startsWith("debrief.")) args[i] = Lang.tr(p, s);
				}
				net.minecraft.network.chat.Component c = Lang.tr(p, line.key(), args);
				Msg.raw(p, c);
				sent.add(c.getString());
			}
			LAST_DEBRIEF.put(id, sent);
			learn(m, p, best);
		}
	}

	/** After a fight: what the bot saw is remembered for next time, and the adaptive level takes a step if earned. */
	private static void learn(net.battlehub.match.Match m, ServerPlayer p, BotPlayer bot) {
		if (!AiMemory.learning()) return;
		MinecraftServer server = p.level().getServer();
		net.battlehub.core.ai.PlayerKnowledge k = AiMemory.get(server, p.getUUID());
		net.battlehub.core.ai.CombatMind mind = bot.brain.mind();
		k.learn(mind.model(p.getUUID()), mind.outcomes());
		// every fight against a bot counts for the rating: a duel against one bot fully, a fight with more people half
		double byPlayer = mind.totalTaken(), byBot = mind.totalDealt();
		double share = byPlayer + byBot <= 0 ? 0.5 : byPlayer / (byPlayer + byBot);
		double weight = m.participants().size() <= 2 ? 1 : 0.5;
		net.battlehub.core.ai.PlayerKnowledge.Change c = k.result(m.outcome(p.getUUID()) == net.battlehub.core.Outcome.WIN, share, bot.difficulty,
				weight, ratingPrior(p));
		if (c != null && net.battlehub.gui.PracticeMenu.adaptive(p.getUUID())) {
			Msg.send(p, c.to().ordinal() > c.from().ordinal() ? "bots.adaptive_up" : "bots.adaptive_down",
					Lang.tr(p, "bots.level." + c.to().id()), Math.round(c.before()), Math.round(c.after()), Math.round(c.chance() * 100));
		}
		AiMemory.save(server, p.getUUID());
	}

	/**
	 * Where someone never rated against bots starts: from their ranked rating against people when they have one (a
	 * hint, not more: half of how far it is from the start rating), otherwise in the middle (intermediate).
	 */
	private static double ratingPrior(ServerPlayer p) {
		net.battlehub.core.PlayerProfile prof = net.battlehub.data.PlayerStore.get(p.getUUID());
		if (prof == null || prof.elo.isEmpty()) return 1050;
		double avg = prof.elo.values().stream().mapToInt(Integer::intValue).average().orElse(net.battlehub.progression.Ranks.startElo());
		return Math.max(800, Math.min(1500, 1050 + (avg - net.battlehub.progression.Ranks.startElo()) * 0.5));
	}

	/** What bots know about a player, as chat lines (/bots memory). */
	public static List<net.minecraft.network.chat.Component> memory(ServerPlayer p) {
		List<net.minecraft.network.chat.Component> out = new ArrayList<>();
		if (!AiMemory.learning()) {
			out.add(Lang.tr(p, "bots.memory.off"));
			return out;
		}
		net.battlehub.core.ai.PlayerKnowledge k = AiMemory.get(p.level().getServer(), p.getUUID());
		out.add(Lang.tr(p, "bots.memory.header"));
		if (k.fights == 0 && k.results.isEmpty()) {
			out.add(Lang.tr(p, "bots.memory.none"));
			return out;
		}
		net.battlehub.core.ai.OpponentModel m = new net.battlehub.core.ai.OpponentModel();
		k.seed(m, null);
		out.add(Lang.tr(p, "bots.memory.style", k.fights, Math.round(m.aggression() * 100), String.format(java.util.Locale.ROOT, "%.1f", k.distance),
				String.format(java.util.Locale.ROOT, "%.1f", k.strafeRate)));
		for (Map.Entry<String, int[]> e : k.record().entrySet()) {
			out.add(Lang.tr(p, "bots.memory.record", Lang.tr(p, "bots.level." + e.getKey()), e.getValue()[0], e.getValue()[1]));
		}
		if (k.swingFights > 0) {
			out.add(Lang.tr(p, "bots.memory.swings", Math.round(k.swingGap), Math.round(k.accuracy * 100)));
		}
		net.battlehub.core.ai.Difficulty adaptive = net.battlehub.core.ai.Difficulty.parse(k.adaptiveLevel, net.battlehub.core.ai.Difficulty.INTERMEDIATE);
		if (k.rated > 0) {
			out.add(Lang.tr(p, "bots.memory.rating", Math.round(k.rating), String.format(java.util.Locale.ROOT, "%.1f", k.rated),
					Lang.tr(p, "bots.level." + adaptive.id()), Math.round(net.battlehub.core.ai.PlayerKnowledge.expected(k.rating, adaptive) * 100)));
		} else {
			out.add(Lang.tr(p, "bots.memory.adaptive", Lang.tr(p, "bots.level." + adaptive.id())));
		}
		out.add(Lang.tr(p, "bots.memory.forget_hint"));
		return out;
	}

	/** The last debrief a player got (self-test). */
	public static List<String> lastDebrief(UUID player) {
		return LAST_DEBRIEF.getOrDefault(player, List.of());
	}

	/** The personality of a bot that is online, or null (people have none). */
	public static net.battlehub.core.ai.Personality personality(UUID id) {
		BotPlayer b = ONLINE.get(id);
		return b == null ? null : b.brain.mind().personality();
	}

	/** Arrows a bot fired, and wind charges it threw to fly up (for the self-test and inspect). */
	public static int shots(BotPlayer b) {
		return b.brain.shots();
	}

	public static int launches(BotPlayer b) {
		return b.brain.launches();
	}

	/** Would a bot bridge with this (a full block to stand on)? */
	public static boolean buildBlock(net.minecraft.world.item.ItemStack s) {
		return BotBrain.isBuildBlock(s);
	}

	/** Blocks a bot placed in its current match (building across gaps). */
	public static int placed(BotPlayer b) {
		return b.brain.placed();
	}

	public static BotPlayer byName(String name) {
		for (BotPlayer b : ONLINE.values()) {
			if (b.getGameProfile().name().equalsIgnoreCase(name)) return b;
		}
		return null;
	}

	public static List<String> names() {
		List<String> out = new ArrayList<>();
		for (BotPlayer b : ONLINE.values()) out.add(b.getGameProfile().name());
		return out;
	}

	/** What the bot's mind is doing and why (the inspect command). */
	public static List<String> inspect(BotPlayer b) {
		List<String> out = new ArrayList<>();
		net.battlehub.match.Match m = Matches.of(b.getUUID());
		out.add(b.getGameProfile().name() + " (" + b.difficulty.id() + ")" + (m == null ? ", not in a match" : ", match #" + m.id + " " + m.mode.id()));
		out.addAll(b.brain.mind().report().lines());
		if (!b.brain.parkourNote().isEmpty()) out.add("parkour: " + b.brain.parkourNote());
		if (m != null && m.botWeapon() != net.battlehub.core.ai.Weapon.ANY) {
			out.add("weapon of this fight: " + m.botWeapon().name().toLowerCase(java.util.Locale.ROOT) + ", holds "
					+ net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(b.getMainHandItem().getItem()).getPath() + ", arrows shot "
					+ b.brain.shots() + ", wind charges " + b.brain.launches());
		}
		net.battlehub.core.ai.Objective g = m == null ? null : m.botGoal(b);
		if (g != null) {
			out.add("goal: " + g.kind().name().toLowerCase(java.util.Locale.ROOT) + " " + g.what() + String.format(java.util.Locale.ROOT, " (%.0f blocks away)",
					Math.hypot(g.point().x() - b.getX(), g.point().z() - b.getZ())) + (b.brain.placed() > 0 ? ", placed " + b.brain.placed() + " blocks" : ""));
		}
		return out;
	}

	/** The bot's mind, explained (self-test, inspect). */
	public static net.battlehub.core.ai.CombatMind.Report report(BotPlayer b) {
		return b.brain.mind().report();
	}

	/** Gives a bot another fighting style; false for an unknown one. */
	public static boolean profile(BotPlayer b, String archetype) {
		net.battlehub.core.ai.Archetype a = net.battlehub.core.ai.Archetype.parse(archetype, null);
		if (a == null) return false;
		b.brain.profile(a);
		return true;
	}

	/** Logs this bot's decisions (every switch with its reason) until called again; true when now on. */
	public static boolean toggleDebug(BotPlayer b) {
		boolean on = DEBUG.add(b.getUUID());
		if (!on) DEBUG.remove(b.getUUID());
		String name = b.getGameProfile().name();
		b.brain.mind().debug(on ? line -> BattleHub.LOGGER.info("[AI {}] {}", name, line) : null);
		return on;
	}

	private static final java.util.Set<UUID> DEBUG = new java.util.HashSet<>();

	/** Server stopping: forget everything (the players list disconnects the bots itself). */
	public static void clear() {
		ONLINE.clear();
		IDLE.clear();
		DEBUG.clear();
		AiMemory.clear();
		LAST_DEBRIEF.clear();
		PARTY_SINCE.clear();
		SIDE_SINCE.clear();
		SHOW.clear();
		budget = null;
		tickStart = 0;
		lastThinks = 0;
		thinkRate = 0;
	}
}
