package net.battlehub.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.ui.Sidebar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The last kills of a match on the right of the screen (the sidebar), newest on top, with an icon for how it
 * happened. Games with their own sidebar (Bed Wars, Prop Hunt) keep theirs.
 */
public final class Killfeed {
	private Killfeed() {}

	/** A line: who, whom, the icon of how, and the killer's own kill message ("" none). */
	private record Entry(String killer, String victim, String icon, String message) {}

	private static final class Feed {
		final Sidebar sidebar;
		final Deque<Entry> lines = new ArrayDeque<>();

		Feed(int matchId) {
			sidebar = new Sidebar("kf" + matchId);
		}
	}

	private static final Map<Integer, Feed> FEEDS = new HashMap<>();

	/** The icon for how someone died: melee, arrow, fall or other. */
	public static String icon(String cause) {
		return switch (cause) {
			case "melee" -> "sword";
			case "arrow" -> "bow";
			case "fall" -> "skull";
			default -> "star";
		};
	}

	public static void kill(Match m, UUID victim, UUID killer, String cause) {
		kill(m, victim, killer, cause, "");
	}

	/** A kill in the feed; {@code message} is the killer's kill message cosmetic ("" none). */
	public static void kill(Match m, UUID victim, UUID killer, String cause, String message) {
		if (!ConfigManager.config().effects.killfeed) {
			return;
		}
		Feed f = FEEDS.computeIfAbsent(m.id, Feed::new);
		f.lines.addFirst(new Entry(killer == null ? "" : m.name(killer), m.name(victim), icon(cause), killer == null || message == null ? "" : message));
		while (f.lines.size() > ConfigManager.config().effects.killfeedLines) {
			f.lines.removeLast();
		}
		if (m.ownSidebar()) {
			return;
		}
		List<UUID> viewers = new ArrayList<>(m.participants());
		viewers.addAll(m.watchers());
		for (UUID id : viewers) {
			ServerPlayer p = m.player(id);
			if (p != null && !m.leftPlayers().contains(id)) {
				show(f, m, p);
			}
		}
	}

	private static void show(Feed f, Match m, ServerPlayer p) {
		List<Component> lines = new ArrayList<>();
		for (Entry e : f.lines) {
			Component icon = Glyphs.icon(p, e.icon());
			lines.add(e.killer().isEmpty() ? Lang.tr(p, "killfeed.line_alone", icon, e.victim())
					: !e.message().isEmpty() ? Lang.tr(p, "killmsg." + e.message() + ".feed", e.victim(), e.killer())
					: Lang.tr(p, "killfeed.line", e.killer(), icon, e.victim()));
		}
		lines.add(Component.empty());
		if (m.participants().contains(p.getUUID())) {
			lines.add(Lang.tr(p, "killfeed.your_kills", m.kills(p.getUUID())));
		}
		f.sidebar.show(p, Lang.tr(p, "killfeed.title"), lines);
	}

	/** A spectator came to watch: they see the feed so far. */
	public static void watcherAdded(Match m, ServerPlayer p) {
		Feed f = FEEDS.get(m.id);
		if (f != null && !f.lines.isEmpty() && !m.ownSidebar()) {
			show(f, m, p);
		}
	}

	public static void hide(Match m, ServerPlayer p) {
		Feed f = FEEDS.get(m.id);
		if (f != null) {
			f.sidebar.hide(p);
		}
	}

	/** The match is over: the feed goes from every screen. */
	public static void close(Match m) {
		Feed f = FEEDS.remove(m.id);
		if (f == null) {
			return;
		}
		for (UUID id : f.sidebar.viewers()) {
			ServerPlayer p = m.player(id);
			if (p != null) {
				f.sidebar.hide(p);
			}
		}
	}

	/** For the self-test: the lines of a match's feed, newest first. */
	public static List<String> lines(int matchId) {
		Feed f = FEEDS.get(matchId);
		List<String> out = new ArrayList<>();
		if (f != null) {
			for (Entry e : f.lines) out.add(e.killer() + " " + e.icon() + " " + e.victim() + (e.message().isEmpty() ? "" : " [" + e.message() + "]"));
		}
		return out;
	}

	public static boolean showing(int matchId, UUID viewer) {
		Feed f = FEEDS.get(matchId);
		return f != null && f.sidebar.viewers().contains(viewer);
	}

	public static void clear() {
		FEEDS.clear();
	}
}
