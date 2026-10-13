package net.battlehub.queue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.Matchmaker;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Icons;
import net.battlehub.gui.Menus;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.DuelMatch;
import net.battlehub.match.Matches;
import net.battlehub.progression.Ranks;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
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

	static String key(GameMode mode, boolean ranked) {
		return (ranked ? "ranked:" : "") + mode.id();
	}

	private static Matchmaker queue(GameMode mode, boolean ranked) {
		return QUEUES.computeIfAbsent(key(mode, ranked), k -> new Matchmaker());
	}

	public static int waiting(GameMode mode, boolean ranked) {
		Matchmaker q = QUEUES.get(key(mode, ranked));
		return q == null ? 0 : q.size();
	}

	public static boolean isQueued(UUID id, GameMode mode, boolean ranked) {
		Matchmaker q = QUEUES.get(key(mode, ranked));
		return q != null && q.contains(id);
	}

	public static void toggle(ServerPlayer p, GameMode mode, boolean ranked) {
		if (isQueued(p.getUUID(), mode, ranked)) {
			leaveAll(p);
			Msg.send(p, "queue.left", Lang.tr(p, Icons.modeKey(mode)));
		} else {
			join(p, mode, ranked);
		}
	}

	public static boolean join(ServerPlayer p, GameMode mode, boolean ranked) {
		if (!canQueue(p, mode)) {
			return false;
		}
		leaveAll(p);
		int rating = ranked ? PlayerStore.get(p).elo(mode, Ranks.startElo()) : 0;
		queue(mode, ranked).add(p.getUUID(), rating, Tasks.now());
		SINCE.put(p.getUUID(), Tasks.now());
		Sessions.toQueue(p.getUUID(), key(mode, ranked));
		HubItems.ensure(p, true);
		Msg.send(p, ranked ? "queue.joined_ranked" : "queue.joined", Lang.tr(p, Icons.modeKey(mode)), waiting(mode, ranked));
		Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.2f);
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
		HubConfig.Game g = ConfigManager.config().game(mode);
		if (!g.enabled) {
			Msg.send(p, "queue.disabled", Lang.tr(p, Icons.modeKey(mode)));
			return false;
		}
		return true;
	}

	/** Leaves every queue, party lobby and outgoing challenge. */
	public static void leaveAll(ServerPlayer p) {
		UUID id = p.getUUID();
		boolean was = false;
		for (Matchmaker q : QUEUES.values()) {
			was |= q.remove(id);
		}
		was |= Parties.leave(id);
		SINCE.remove(id);
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
		if (tick % 20 != 0) {
			return;
		}
		HubConfig.Elo e = ConfigManager.config().elo;
		Matchmaker.Window window = new Matchmaker.Window(e.windowBase, e.windowPerSecond, e.windowMax);
		for (GameMode mode : GameMode.duels()) {
			for (boolean ranked : new boolean[] {false, true}) {
				Matchmaker q = QUEUES.get(key(mode, ranked));
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
					if (!startDuel(server, mode, ranked, a, b)) {
						q.add(a.getUUID(), rating(a, mode, ranked), SINCE.getOrDefault(a.getUUID(), Tasks.now()));
						q.add(b.getUUID(), rating(b, mode, ranked), SINCE.getOrDefault(b.getUUID(), Tasks.now()));
						Msg.send(a, "queue.arenas_full");
						Msg.send(b, "queue.arenas_full");
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
			boolean ranked = s.queue.startsWith("ranked:");
			GameMode mode = GameMode.byId(ranked ? s.queue.substring(7) : s.queue);
			if (mode == null) continue;
			long secs = (Tasks.now() - SINCE.get(p.getUUID())) / 20;
			Msg.actionBar(p, ranked ? "queue.actionbar_ranked" : "queue.actionbar", Lang.tr(p, Icons.modeKey(mode)), waiting(mode, ranked),
					String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60));
		}
	}

	private static int rating(ServerPlayer p, GameMode mode, boolean ranked) {
		return ranked ? PlayerStore.get(p).elo(mode, Ranks.startElo()) : 0;
	}

	/** Creates and starts a duel between two players. False when every arena is busy. */
	public static boolean startDuel(MinecraftServer server, GameMode mode, boolean ranked, ServerPlayer a, ServerPlayer b) {
		SINCE.remove(a.getUUID());
		SINCE.remove(b.getUUID());
		DuelMatch m = new DuelMatch(Matches.newId(), mode, ranked, server, a, b);
		if (!Matches.start(m)) {
			Sessions.toLobby(a.getUUID());
			Sessions.toLobby(b.getUUID());
			BattleHub.LOGGER.warn("No free arena slot for a {} duel", mode.id());
			return false;
		}
		for (ServerPlayer p : List.of(a, b)) {
			ServerPlayer other = p == a ? b : a;
			Msg.send(p, ranked ? "queue.found_ranked" : "queue.found", other.getGameProfile().name(), Lang.tr(p, Icons.modeKey(mode)));
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
	}
}
