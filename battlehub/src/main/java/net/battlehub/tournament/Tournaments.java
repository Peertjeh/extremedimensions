package net.battlehub.tournament;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The tournaments being played and who is in which one. */
public final class Tournaments {
	private Tournaments() {}

	public static final List<String> TEAM_SIZES = List.of("1", "2", "4", "8");
	public static final List<String> KITS = List.of("sword", "axe", "mace", "bow", "crossbow", "random");

	private static final List<Tournament> ACTIVE = new ArrayList<>();
	private static final Map<UUID, Tournament> BY_PLAYER = new HashMap<>();
	private static final Random RANDOM = new Random();
	private static int nextId = 1;

	/** The kit a vote stands for ("random" or nothing: any duel kit). */
	static GameMode kitFor(String vote) {
		GameMode m = GameMode.byId(vote);
		if (m != null && m.isDuel()) {
			return m;
		}
		GameMode[] duels = GameMode.duels();
		return duels[RANDOM.nextInt(duels.length)];
	}

	static int teamSizeFor(String vote) {
		try {
			int n = Integer.parseInt(vote.trim());
			return n == 2 || n == 4 || n == 8 ? n : 1;
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/** Starts a tournament with the lobby's votes ("teams", "kit"); false with fewer than two players. */
	public static boolean start(MinecraftServer server, List<ServerPlayer> players, Map<String, String> votes) {
		if (players.size() < 2) {
			return false;
		}
		Tournament t = new Tournament(nextId++, server, kitFor(votes.getOrDefault("kit", "")),
				teamSizeFor(votes.getOrDefault("teams", "")), players, RANDOM);
		ACTIVE.add(t);
		for (ServerPlayer p : players) {
			BY_PLAYER.put(p.getUUID(), t);
		}
		t.begin();
		return true;
	}

	public static boolean isIn(UUID id) {
		return BY_PLAYER.containsKey(id);
	}

	public static Tournament of(UUID id) {
		return BY_PLAYER.get(id);
	}

	public static List<Tournament> all() {
		return List.copyOf(ACTIVE);
	}

	/** The player gives up their place (gave up a match, /hub, left the server or BattleHub, joined something else). */
	public static void leave(UUID id) {
		Tournament t = BY_PLAYER.remove(id);
		if (t != null) {
			t.leave(id);
		}
	}

	public static void leave(ServerPlayer p) {
		leave(p.getUUID());
	}

	/** Knocked out or the tournament ended: no longer in it. */
	static void forget(UUID id) {
		BY_PLAYER.remove(id);
	}

	public static void tick(MinecraftServer server) {
		for (Tournament t : new ArrayList<>(ACTIVE)) {
			try {
				t.tick();
			} catch (RuntimeException e) {
				net.battlehub.BattleHub.LOGGER.error("Tournament #{} failed; it is cancelled", t.id, e);
				ACTIVE.remove(t);
				BY_PLAYER.values().removeIf(x -> x == t);
				continue;
			}
			if (t.isDone()) {
				ACTIVE.remove(t);
				BY_PLAYER.values().removeIf(x -> x == t);
			}
		}
	}

	public static void clear() {
		ACTIVE.clear();
		BY_PLAYER.clear();
	}
}
