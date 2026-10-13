package net.battlehub.queue;

import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;

/** Duel queues, ranked queues and party lobbies. (Phase 1: placeholder, filled in by later phases.) */
public final class Queues {
	private Queues() {}

	public static int waiting(GameMode mode, boolean ranked) {
		return 0;
	}

	public static boolean isQueued(UUID id, GameMode mode, boolean ranked) {
		return false;
	}

	public static void toggle(ServerPlayer p, GameMode mode, boolean ranked) {
		Msg.send(p, "general.coming_soon");
	}

	public static void challengeMenu(ServerPlayer p, GameMode mode) {
		Msg.send(p, "general.coming_soon");
	}

	public static int partyCount(GameMode mode) {
		return 0;
	}

	public static boolean inParty(UUID id, GameMode mode) {
		return false;
	}

	public static int partySecondsLeft(GameMode mode) {
		return -1;
	}

	public static void toggleParty(ServerPlayer p, GameMode mode) {
		Msg.send(p, "general.coming_soon");
	}

	public static void leaveAll(ServerPlayer p) {
	}
}
