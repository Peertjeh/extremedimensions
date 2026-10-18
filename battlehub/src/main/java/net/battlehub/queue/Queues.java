package net.battlehub.queue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.core.Matchmaker;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Icons;
import net.battlehub.gui.Menus;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.DuelMatch;
import net.battlehub.match.Duels;
import net.battlehub.match.Matches;
import net.battlehub.progression.Ranks;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Duel queues (casual and ranked per mode). A player waits in at most one queue; joining another one leaves the
 * first. Party lobbies and challenges are handled here too.
 */
public final class Queues {
	private Queues() {}

	private static final Map<String, Matchmaker> QUEUES = new HashMap<>();
	private static final Map<UUID, Long> SINCE = new HashMap<>();
	private static int tick;

	/** Queue key, e.g. "sword", "ranked:bow", "axe:bo3", "ranked:mace:bo3". */
	static String key(GameMode mode, boolean ranked, DuelFormat format) {
		return (ranked ? "ranked:" : "") + mode.id() + (format == DuelFormat.BO3 ? ":bo3" : "");
	}

	/** What a queue key stands for: mode, ranked, format. Null for keys that are not duel queues. */
	record QueueKey(GameMode mode, boolean ranked, DuelFormat format) {}

	static QueueKey parse(String key) {
		if (key == null || key.startsWith("party:")) {
			return null;
		}
		boolean ranked = key.startsWith("ranked:");
		String rest = ranked ? key.substring(7) : key;
		DuelFormat format = rest.endsWith(":bo3") ? DuelFormat.BO3 : DuelFormat.BO1;
		if (format == DuelFormat.BO3) {
			rest = rest.substring(0, rest.length() - 4);
		}
		GameMode mode = GameMode.byId(rest);
		return mode == null ? null : new QueueKey(mode, ranked, format);
	}

	private static Matchmaker queue(GameMode mode, boolean ranked, DuelFormat format) {
		return QUEUES.computeIfAbsent(key(mode, ranked, format), k -> new Matchmaker());
	}

	public static DuelFormat formatOf(ServerPlayer p) {
		return PlayerStore.get(p).format();
	}

	public static int waiting(GameMode mode, boolean ranked, DuelFormat format) {
		Matchmaker q = QUEUES.get(key(mode, ranked, format));
		return q == null ? 0 : q.size();
	}

	/** Who waits in this queue, in order. */
	public static List<UUID> waitingIds(GameMode mode, boolean ranked, DuelFormat format) {
		Matchmaker q = QUEUES.get(key(mode, ranked, format));
		List<UUID> out = new java.util.ArrayList<>();
		if (q != null) {
			for (Matchmaker.Entry e : q.entries()) out.add(e.id());
		}
		return out;
	}

	/** Server tick at which the player joined their duel queue, or -1. */
	public static long queuedSince(UUID id) {
		Long t = SINCE.get(id);
		return t == null ? -1 : t;
	}

	/** Waiting in this mode's queue (either format). */
	public static boolean isQueued(UUID id, GameMode mode, boolean ranked) {
		for (DuelFormat f : DuelFormat.values()) {
			Matchmaker q = QUEUES.get(key(mode, ranked, f));
			if (q != null && q.contains(id)) {
				return true;
			}
		}
		return false;
	}

	public static void toggle(ServerPlayer p, GameMode mode, boolean ranked) {
		if (isQueued(p.getUUID(), mode, ranked)) {
			leaveAll(p);
			Msg.send(p, "queue.left", Lang.tr(p, Icons.modeKey(mode)));
		} else {
			join(p, mode, ranked);
		}
	}

	/** Joins the queue in the player's chosen format (best of 1 or 3). */
	public static boolean join(ServerPlayer p, GameMode mode, boolean ranked) {
		return join(p, mode, ranked, formatOf(p));
	}

	public static boolean join(ServerPlayer p, GameMode mode, boolean ranked, DuelFormat format) {
		if (!canQueue(p, mode)) {
			return false;
		}
		// Test modes are never ranked
		ranked = ranked && !mode.isUnranked();
		leaveAll(p);
		PlayAgain.forget(p.getUUID());
		int rating = ranked ? PlayerStore.get(p).elo(mode, Ranks.startElo()) : 0;
		queue(mode, ranked, format).add(p.getUUID(), rating, Tasks.now());
		SINCE.put(p.getUUID(), Tasks.now());
		Sessions.toQueue(p.getUUID(), key(mode, ranked, format));
		HubItems.ensure(p, true);
		Msg.send(p, ranked ? "queue.joined_ranked" : "queue.joined", modeLabel(p, mode, format), waiting(mode, ranked, format));
		Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.2f);
		if (!net.battlehub.bot.Bots.isBot(p)) net.battlehub.bot.Bots.hint(p);
		Menus.refreshAll(p.level().getServer());
		return true;
	}

	/** May this player queue for this mode right now? Sends the reason if not. */
	public static boolean canQueue(ServerPlayer p, GameMode mode) {
		Sessions.State s = Sessions.state(p.getUUID());
		if (s == Sessions.State.MATCH) {
			Msg.send(p, "queue.in_match");
			return false;
		}
		if (s == Sessions.State.SPECTATING) {
			Msg.send(p, "queue.spectating");
			return false;
		}
		if (net.battlehub.tournament.Tournaments.isIn(p.getUUID())) {
			Msg.send(p, "tournament.busy");
			return false;
		}
		if (net.battlehub.fair.LeavePenalty.blocks(p)) {
			return false;
		}
		HubConfig.Game g = ConfigManager.config().game(mode);
		if (!g.enabled) {
			Msg.send(p, "queue.disabled", Lang.tr(p, Icons.modeKey(mode)));
			return false;
		}
		// Starting something from the normal world takes the player into BattleHub first.
		return net.battlehub.hub.Travel.ensureInHub(p);
	}

	/** Leaves every queue, party lobby and outgoing challenge. */
	public static void leaveAll(ServerPlayer p) {
		UUID id = p.getUUID();
		boolean was = false;
		for (Matchmaker q : QUEUES.values()) {
			was |= q.remove(id);
		}
		was |= Parties.leave(id);
		was |= TeamFights.leave(id);
		if (net.battlehub.tournament.Tournaments.isIn(id)) {
			net.battlehub.tournament.Tournaments.leave(id);
			was = true;
		}
		SINCE.remove(id);
		Challenges.forget(id);
		if (Sessions.state(id) == Sessions.State.QUEUED) {
			Sessions.toLobby(id);
		}
		if (was) {
			HubItems.ensure(p, false);
			Menus.refreshAll(p.level().getServer());
		}
	}

	public static void tick(MinecraftServer server) {
		tick++;
		Parties.tick(server);
		net.battlehub.tournament.Tournaments.tick(server);
		if (tick % 20 != 0) {
			return;
		}
		Challenges.tick(server);
		PlayAgain.tick(server);
		TeamFights.tick(server);
		HubConfig.Elo e = ConfigManager.config().elo;
		Matchmaker.Window window = new Matchmaker.Window(e.windowBase, e.windowPerSecond, e.windowMax);
		for (GameMode mode : GameMode.duels()) {
			for (boolean ranked : new boolean[] {false, true}) {
			for (DuelFormat format : DuelFormat.values()) {
				Matchmaker q = QUEUES.get(key(mode, ranked, format));
				if (q == null || q.size() == 0) {
					continue;
				}
				// Drop anybody who is gone or busy
				for (Matchmaker.Entry en : q.entries()) {
					ServerPlayer p = server.getPlayerList().getPlayer(en.id());
					if (p == null || Sessions.state(en.id()) != Sessions.State.QUEUED) {
						q.remove(en.id());
					}
				}
				List<Matchmaker.Pair> pairs = q.match(Tasks.now(), ranked ? window : null);
				for (Matchmaker.Pair pair : pairs) {
					ServerPlayer a = server.getPlayerList().getPlayer(pair.first());
					ServerPlayer b = server.getPlayerList().getPlayer(pair.second());
					if (a == null || b == null) {
						if (a != null) q.add(a.getUUID(), rating(a, mode, ranked), SINCE.getOrDefault(a.getUUID(), Tasks.now()));
						if (b != null) q.add(b.getUUID(), rating(b, mode, ranked), SINCE.getOrDefault(b.getUUID(), Tasks.now()));
						continue;
					}
					if (!startDuel(server, mode, ranked, format, false, a, b)) {
						q.add(a.getUUID(), rating(a, mode, ranked), SINCE.getOrDefault(a.getUUID(), Tasks.now()));
						q.add(b.getUUID(), rating(b, mode, ranked), SINCE.getOrDefault(b.getUUID(), Tasks.now()));
						Msg.send(a, "queue.arenas_full");
						Msg.send(b, "queue.arenas_full");
					}
				}
			}
			}
		}
		// Action bar for everyone still waiting
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Sessions.Session s = Sessions.get(p.getUUID());
			if (s.state() != Sessions.State.QUEUED || !SINCE.containsKey(p.getUUID())) {
				continue;
			}
			QueueKey k = parse(s.queue);
			if (k == null) continue;
			long secs = (Tasks.now() - SINCE.get(p.getUUID())) / 20;
			Msg.actionBar(p, k.ranked() ? "queue.actionbar_ranked" : "queue.actionbar", modeLabel(p, k.mode(), k.format()),
					waiting(k.mode(), k.ranked(), k.format()), String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60));
		}
	}

	private static int rating(ServerPlayer p, GameMode mode, boolean ranked) {
		return ranked ? PlayerStore.get(p).elo(mode, Ranks.startElo()) : 0;
	}

	/** "Sword PvP" or "Sword PvP (Best of 3)". */
	public static Component modeLabel(ServerPlayer p, GameMode mode, DuelFormat format) {
		Component name = Lang.tr(p, Icons.modeKey(mode));
		return format == DuelFormat.BO3 ? Lang.tr(p, "format.with_bo3", name) : name;
	}

	/** Creates and starts a duel between two players. False when every arena is busy. */
	public static boolean startDuel(MinecraftServer server, GameMode mode, boolean ranked, DuelFormat format, boolean challenge,
			ServerPlayer a, ServerPlayer b) {
		SINCE.remove(a.getUUID());
		SINCE.remove(b.getUUID());
		PlayAgain.forget(a.getUUID());
		PlayAgain.forget(b.getUUID());
		DuelMatch m = Duels.create(Matches.newId(), mode, ranked, format, challenge, server, a, b);
		if (!Matches.start(m)) {
			Sessions.toLobby(a.getUUID());
			Sessions.toLobby(b.getUUID());
			BattleHub.LOGGER.warn("No free arena slot for a {} duel", mode.id());
			return false;
		}
		for (ServerPlayer p : List.of(a, b)) {
			ServerPlayer other = p == a ? b : a;
			Msg.send(p, ranked ? "queue.found_ranked" : "queue.found", other.getGameProfile().name(), modeLabel(p, mode, format));
			HubItems.strip(p);
			HubItems.ensure(p, false);
		}
		return true;
	}

	// ---------------------------------------------------------------- challenges and parties

	public static void challengeMenu(ServerPlayer p, GameMode mode) {
		Challenges.openPicker(p, mode);
	}

	public static int partyCount(GameMode mode) {
		return Parties.count(mode);
	}

	public static boolean inParty(UUID id, GameMode mode) {
		return Parties.in(id, mode);
	}

	public static int partySecondsLeft(GameMode mode) {
		return Parties.secondsLeft(mode);
	}

	public static void toggleParty(ServerPlayer p, GameMode mode) {
		Parties.toggle(p, mode);
	}

	public static void clear() {
		QUEUES.clear();
		SINCE.clear();
		Parties.clear();
		Challenges.clear();
		PlayAgain.clear();
		TeamFights.clear();
		net.battlehub.tournament.Tournaments.clear();
	}
}
