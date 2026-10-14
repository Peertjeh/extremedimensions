package net.battlehub.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.core.GameMode;
import net.battlehub.core.SideQueue;
import net.battlehub.gui.Icons;
import net.battlehub.gui.Menus;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.Matches;
import net.battlehub.match.TeamFightMatch;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Queues for 1v2 and 1v3, one per kit; players choose to play alone, in the team, or either. */
public final class TeamFights {
	private TeamFights() {}

	private static final Map<String, SideQueue> QUEUES = new LinkedHashMap<>();
	private static final Map<UUID, SideQueue.Side> LAST_SIDE = new HashMap<>();

	public static String key(GameMode mode, GameMode kit) {
		return mode.id() + ":" + kit.id();
	}

	/** Sword, axe or mace (anything else: sword). */
	public static GameMode kitOrDefault(GameMode kit) {
		for (GameMode k : GameMode.meleeKits()) {
			if (k == kit) return k;
		}
		return GameMode.SWORD;
	}

	private static SideQueue queue(GameMode mode, GameMode kit) {
		return QUEUES.computeIfAbsent(key(mode, kit), k -> new SideQueue(mode.opponents()));
	}

	public static Component label(ServerPlayer p, GameMode mode, GameMode kit) {
		return Lang.tr(p, "teamfight.label", Lang.tr(p, Icons.modeKey(mode)), Lang.tr(p, Icons.modeKey(kit)));
	}

	/** Joins the queue for this side, or changes side when already waiting in it. */
	public static boolean join(ServerPlayer p, GameMode mode, GameMode kit, SideQueue.Side side) {
		if (mode == null || !mode.isTeamFight()) {
			return false;
		}
		kit = kitOrDefault(kit);
		SideQueue q = queue(mode, kit);
		UUID id = p.getUUID();
		if (q.contains(id)) {
			q.add(id, side, Tasks.now());
			LAST_SIDE.put(id, side);
			Msg.send(p, "teamfight.side_changed", Lang.tr(p, "teamfight.side." + side.id()));
			Menus.refreshAll(p.level().getServer());
			return true;
		}
		if (!Queues.canQueue(p, mode)) {
			return false;
		}
		Queues.leaveAll(p);
		PlayAgain.forget(id);
		q.add(id, side, Tasks.now());
		LAST_SIDE.put(id, side);
		Sessions.toQueue(id, key(mode, kit));
		HubItems.ensure(p, true);
		Msg.send(p, "teamfight.joined", label(p, mode, kit), Lang.tr(p, "teamfight.side." + side.id()));
		Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.2f);
		Menus.refreshAll(p.level().getServer());
		return true;
	}

	/** Out of every 1v2 / 1v3 queue; true if the player was in one. */
	static boolean leave(UUID id) {
		boolean was = false;
		for (SideQueue q : QUEUES.values()) {
			was |= q.remove(id);
		}
		return was;
	}

	/** The side this player waits for in this queue, or null. */
	public static SideQueue.Side sideOf(UUID id, GameMode mode, GameMode kit) {
		SideQueue q = QUEUES.get(key(mode, kitOrDefault(kit)));
		return q == null ? null : q.side(id);
	}

	public static int count(GameMode mode, GameMode kit, SideQueue.Side side) {
		SideQueue q = QUEUES.get(key(mode, kitOrDefault(kit)));
		return q == null ? 0 : q.count(side);
	}

	public static int waiting(GameMode mode, GameMode kit) {
		SideQueue q = QUEUES.get(key(mode, kitOrDefault(kit)));
		return q == null ? 0 : q.size();
	}

	/** The side the player chose last time (for "play again"). */
	public static SideQueue.Side lastSide(UUID id) {
		return LAST_SIDE.getOrDefault(id, SideQueue.Side.ANY);
	}

	/** Once a second: drop players who are gone or busy, start every complete group, show the queue. */
	static void tick(MinecraftServer server) {
		for (Map.Entry<String, SideQueue> e : QUEUES.entrySet()) {
			SideQueue q = e.getValue();
			for (SideQueue.Entry en : q.entries()) {
				if (server.getPlayerList().getPlayer(en.id()) == null || Sessions.state(en.id()) != Sessions.State.QUEUED) {
					q.remove(en.id());
				}
			}
			String[] parts = e.getKey().split(":");
			GameMode mode = GameMode.byId(parts[0]);
			GameMode kit = GameMode.byId(parts[1]);
			Map<UUID, SideQueue.Entry> before = new HashMap<>();
			for (SideQueue.Entry en : q.entries()) before.put(en.id(), en);
			for (SideQueue.Group g : q.match()) {
				ServerPlayer solo = server.getPlayerList().getPlayer(g.solo());
				List<ServerPlayer> team = new ArrayList<>();
				for (UUID id : g.team()) team.add(server.getPlayerList().getPlayer(id));
				TeamFightMatch m = new TeamFightMatch(Matches.newId(), mode, kit, server, solo, team);
				if (!Matches.start(m)) {
					BattleHub.LOGGER.warn("Could not start a {} match (no free arena?)", e.getKey());
					List<UUID> all = new ArrayList<>(g.team());
					all.add(0, g.solo());
					for (UUID id : all) {
						SideQueue.Entry old = before.get(id);
						q.add(id, old.side(), old.since());
						ServerPlayer p = server.getPlayerList().getPlayer(id);
						if (p != null) Msg.send(p, "queue.arenas_full");
					}
				}
			}
			for (SideQueue.Entry en : q.entries()) {
				ServerPlayer p = server.getPlayerList().getPlayer(en.id());
				if (p != null) {
					Msg.actionBar(p, "teamfight.actionbar", label(p, mode, kit), q.count(SideQueue.Side.SOLO),
							q.count(SideQueue.Side.TEAM), q.teamSize(), q.count(SideQueue.Side.ANY));
				}
			}
		}
	}

	static void clear() {
		QUEUES.clear();
		LAST_SIDE.clear();
	}
}
