package net.battlehub.match;

import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.games.duel.BoxingMatch;
import net.battlehub.games.duel.BridgeMatch;
import net.battlehub.games.duel.BuildUhcMatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Creates the match for a 1v1: a plain kit duel, or a duel game with rules of its own. */
public final class Duels {
	private Duels() {}

	public static DuelMatch create(int id, GameMode mode, boolean ranked, DuelFormat format, boolean challenge, MinecraftServer server,
			ServerPlayer a, ServerPlayer b) {
		return switch (mode) {
			// The Bridge is played to a number of goals, never in rounds
			case BRIDGE -> new BridgeMatch(id, ranked, challenge, server, a, b);
			case BOXING -> new BoxingMatch(id, ranked, format, challenge, server, a, b);
			case BUILD_UHC -> new BuildUhcMatch(id, ranked, format, challenge, server, a, b);
			default -> new DuelMatch(id, mode, ranked, format, challenge, server, a, b);
		};
	}
}
