package net.battlehub.games;

import java.util.List;

import net.battlehub.core.GameMode;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Creates the match for a party game. */
public final class PartyGames {
	private PartyGames() {}

	public static Match create(GameMode mode, MinecraftServer server, List<ServerPlayer> players) {
		int id = Matches.newId();
		return switch (mode) {
			case BOW_SPLEEF -> new BowSpleefMatch(id, server, players);
			case TNT_RUN -> new TntRunMatch(id, server, players);
			case SUMO -> new SumoMatch(id, server, players);
			case KOTH -> new KothMatch(id, server, players);
			case CTF -> new CtfMatch(id, server, players);
			case PARKOUR -> new ParkourMatch(id, server, players);
			case LMS -> new LmsMatch(id, server, players);
			default -> null;
		};
	}
}
