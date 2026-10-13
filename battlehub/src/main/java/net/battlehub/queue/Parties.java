package net.battlehub.queue;

import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.util.Msg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Party game lobbies. (Phase 2 placeholder; the party games arrive in phase 5.) */
public final class Parties {
	private Parties() {}

	static boolean leave(UUID id) {
		return false;
	}

	static void tick(MinecraftServer server) {
	}

	static int count(GameMode mode) {
		return 0;
	}

	static boolean in(UUID id, GameMode mode) {
		return false;
	}

	static int secondsLeft(GameMode mode) {
		return -1;
	}

	static void toggle(ServerPlayer p, GameMode mode) {
		Msg.send(p, "general.coming_soon");
	}

	static void invite(ServerPlayer from, ServerPlayer to, GameMode mode) {
		Msg.send(from, "general.coming_soon");
	}

	static void clear() {
	}
}
