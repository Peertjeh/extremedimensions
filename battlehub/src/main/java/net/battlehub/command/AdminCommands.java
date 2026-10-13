package net.battlehub.command;

import java.util.List;

import net.battlehub.arena.ArenaSlots;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /battlehub matches, endmatch, arena list. */
public final class AdminCommands {
	private AdminCommands() {}

	static int matches(CommandSourceStack src) {
		ServerPlayer viewer = src.getPlayer();
		List<Match> all = Matches.all();
		if (all.isEmpty()) {
			src.sendSuccess(() -> Lang.tr(viewer, "admin.matches_none"), false);
			return 0;
		}
		src.sendSuccess(() -> Lang.tr(viewer, "admin.matches_header", all.size()), false);
		for (Match m : all) {
			Component line = Lang.tr(viewer, "admin.matches_line", m.id, Lang.tr(viewer, Icons.modeKey(m.mode)),
					m.describe(viewer), m.phase().name().toLowerCase(java.util.Locale.ROOT),
					m.arena() == null ? "-" : m.arena().theme().id(), m.arena() == null ? -1 : m.arena().slot(), m.runSeconds());
			src.sendSuccess(() -> line, false);
		}
		return all.size();
	}

	static int endMatch(CommandSourceStack src, int id) {
		ServerPlayer viewer = src.getPlayer();
		boolean ok = Matches.end(id);
		src.sendSuccess(() -> Lang.tr(viewer, ok ? "admin.match_ended" : "admin.match_unknown", id), true);
		return ok ? 1 : 0;
	}

	static int arenaList(CommandSourceStack src) {
		ServerPlayer viewer = src.getPlayer();
		src.sendSuccess(() -> Lang.tr(viewer, "admin.arena_header", ArenaSlots.busy(), ArenaSlots.capacity()), false);
		for (Theme t : Theme.values()) {
			StringBuilder modes = new StringBuilder();
			for (GameMode m : GameMode.values()) {
				List<String> themes = ConfigManager.config().arenas.themes.get(m.id());
				if (themes != null && themes.contains(t.id())) {
					if (modes.length() > 0) modes.append(", ");
					modes.append(m.id());
				}
			}
			src.sendSuccess(() -> Lang.tr(viewer, "admin.arena_line", Lang.tr(viewer, "theme." + t.id()), modes.toString()), false);
		}
		return Theme.values().length;
	}
}
