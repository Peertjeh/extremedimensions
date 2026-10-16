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

	/**
	 * {@code votes}: what the lobby voted for per question ("" the parkour difficulty, "kit" the FFA kit, "format"
	 * solo or duo in Bed Wars).
	 */
	public static Match create(GameMode mode, MinecraftServer server, List<ServerPlayer> players, java.util.Map<String, String> votes) {
		String option = votes.getOrDefault("", "");
		int id = Matches.newId();
		Match m = switch (mode) {
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
			case BED_WARS -> new net.battlehub.games.bedwars.BedWarsMatch(id, server, players, votes.getOrDefault("format", ""));
			case PROP_HUNT -> new net.battlehub.games.prophunt.PropHuntMatch(id, server, players);
			case SPLEEF -> new SpleefMatch(id, server, players);
			case SKYWARS -> new SkyWarsMatch(id, server, players);
			case MURDER_MYSTERY -> new MurderMysteryMatch(id, server, players);
			case BRIDGE_DUOS, BRIDGE_FOUR -> new BridgePartyMatch(id, mode, server, players);
			default -> null;
		};
		if (m != null) {
			m.chooseMap(votes.getOrDefault("map", ""));
		}
		return m;
	}
}
