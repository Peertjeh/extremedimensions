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
		return create(mode, server, players, "");
	}

	public static Match create(GameMode mode, MinecraftServer server, List<ServerPlayer> players, String option) {
		return create(mode, server, players, java.util.Map.of("", option == null ? "" : option));
	}

	/** {@code votes}: what the lobby voted for per question ("" the parkour difficulty, "kit" the FFA kit). */
	public static Match create(GameMode mode, MinecraftServer server, List<ServerPlayer> players, java.util.Map<String, String> votes) {
		String option = votes.getOrDefault("", "");
		int id = Matches.newId();
		return switch (mode) {
			case BOW_SPLEEF -> new BowSpleefMatch(id, server, players);
			case TNT_RUN -> new TntRunMatch(id, server, players);
			case SUMO -> new SumoMatch(id, server, players);
			case KOTH -> new KothMatch(id, server, players);
			case CTF -> new CtfMatch(id, server, players);
			case PARKOUR -> new ParkourMatch(id, server, players, option);
			case LMS -> new LmsMatch(id, server, players);
			case FFA -> new FfaMatch(id, server, players, votes.getOrDefault("kit", ""));
			// Started by Tournaments, which runs several matches
			case TOURNAMENT -> null;
			default -> null;
		};
	}
}
