package net.battlehub.export;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.Outcome;
import net.battlehub.match.Match;

/** The last matches that ended (in memory), for the website export. */
public final class MatchLog {
	private MatchLog() {}

	public record Player(String name, String uuid, String outcome, int kills, int deaths, boolean bot) {}

	public record Entry(int id, String mode, boolean ranked, boolean unrated, long ended, int seconds, String map,
			List<Player> players, List<String> winners) {}

	private static final Deque<Entry> RECENT = new ArrayDeque<>();

	public static void record(Match m) {
		int keep = ConfigManager.config().export.recentMatches;
		if (keep <= 0) {
			return;
		}
		List<Player> players = new ArrayList<>();
		List<String> winners = new ArrayList<>();
		for (UUID id : m.participants()) {
			Outcome o = m.outcome(id);
			players.add(new Player(m.name(id), id.toString(), o.name().toLowerCase(java.util.Locale.ROOT), m.kills(id), m.deaths(id),
					Bots.isBot(id)));
			if (o == Outcome.WIN) winners.add(m.name(id));
		}
		String map = m.customMap() != null ? "custom:" + m.customMap().id : m.arena() == null ? "" : "theme:" + m.arena().theme().id();
		synchronized (RECENT) {
			RECENT.addFirst(new Entry(m.id, m.mode.id(), m.ranked, m.unrated(), System.currentTimeMillis(), m.runSeconds(), map,
					players, winners));
			while (RECENT.size() > keep) RECENT.removeLast();
		}
	}

	public static List<Entry> recent() {
		synchronized (RECENT) {
			return new ArrayList<>(RECENT);
		}
	}

	public static void clear() {
		synchronized (RECENT) {
			RECENT.clear();
		}
	}
}
