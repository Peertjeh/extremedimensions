package net.battlehub.match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.core.MatchRecap;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;

/** The recap after a match: each player's own numbers, and the MVP for everyone. */
final class RecapMessages {
	private RecapMessages() {}

	private static String hearts(double damage) {
		return String.format(java.util.Locale.ROOT, "%.1f", damage / 2);
	}

	static void send(Match m) {
		MatchRecap r = m.recap();
		List<UUID> players = new ArrayList<>(m.participants());
		UUID mvp = players.size() >= 2 ? r.mvp(players) : null;
		for (UUID id : players) {
			ServerPlayer p = m.player(id);
			if (p == null || Bots.isBot(id) || m.leftPlayers().contains(id)) continue;
			MatchRecap.Line l = r.line(id);
			Msg.raw(p, Lang.tr(p, "recap.header"));
			Msg.raw(p, Lang.tr(p, "recap.damage", hearts(l.dealt), hearts(l.taken)));
			if (l.swings > 0 || l.hits > 0) {
				Msg.raw(p, Lang.tr(p, "recap.hits", l.hits, l.swings, l.accuracy(), l.bestCombo));
			}
			if (l.shots > 0) {
				Msg.raw(p, Lang.tr(p, "recap.arrows", l.arrowHits, l.shots, l.bowAccuracy()));
			}
			if (mvp != null) {
				MatchRecap.Line best = r.line(mvp);
				Msg.raw(p, Lang.tr(p, mvp.equals(id) ? "recap.mvp_you" : "recap.mvp", m.name(mvp), best.kills, hearts(best.dealt)));
			}
		}
	}
}
