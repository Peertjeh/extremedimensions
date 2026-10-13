package net.battlehub.games;

import java.util.List;

import net.battlehub.core.GameMode;
import net.battlehub.match.Match;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Creates the match for a party game. */
public final class PartyGames {
	private PartyGames() {}

	public static Match create(GameMode mode, MinecraftServer server, List<ServerPlayer> players) {
		return null;
	}
}
