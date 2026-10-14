package net.battlehub.command;

import java.util.List;

import net.battlehub.arena.ArenaSlots;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.Menus;
import net.battlehub.util.Msg;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /battlehub matches, endmatch, arena list, coins. */
public final class AdminCommands {
	private AdminCommands() {}

	public enum CoinChange { GIVE, TAKE, SET }

	/** Profile of a player by name, online or not (null when BattleHub never saw them). */
	private static PlayerProfile profile(CommandSourceStack src, String name) {
		ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
		PlayerProfile prof = online != null ? PlayerStore.get(online) : PlayerStore.find(name);
		return prof == null || prof.uuid == null || prof.uuid.isEmpty() ? null : prof;
	}

	/** /battlehub coins <player>: the balance. */
	static int coinsShow(CommandSourceStack src, String name) {
		ServerPlayer viewer = src.getPlayer();
		PlayerProfile prof = profile(src, name);
		if (prof == null) {
			src.sendFailure(Lang.tr(viewer, "admin.coins_unknown", name));
			return 0;
		}
		src.sendSuccess(() -> Lang.tr(viewer, "admin.coins_show", prof.name, prof.coins), false);
		return (int) Math.min(Integer.MAX_VALUE, prof.coins);
	}

	/** /battlehub coins give|take|set <player> <amount>, also for players who are offline. Never below zero. */
	static int coins(CommandSourceStack src, String name, CoinChange change, long amount) {
		ServerPlayer viewer = src.getPlayer();
		PlayerProfile prof = profile(src, name);
		if (prof == null) {
			src.sendFailure(Lang.tr(viewer, "admin.coins_unknown", name));
			return 0;
		}
		long before = prof.coins;
		long after = switch (change) {
			case GIVE -> before + amount;
			case TAKE -> Math.max(0, before - amount);
			case SET -> Math.max(0, amount);
		};
		prof.coins = after;
		java.util.UUID id = java.util.UUID.fromString(prof.uuid);
		PlayerStore.save(id);
		long diff = Math.abs(after - before);
		String key = "admin.coins_" + change.name().toLowerCase(java.util.Locale.ROOT);
		src.sendSuccess(() -> Lang.tr(viewer, key, prof.name, diff, after), true);
		ServerPlayer target = src.getServer().getPlayerList().getPlayer(id);
		if (target != null && target != viewer && diff > 0) {
			Msg.send(target, after > before ? "coins.admin_added" : "coins.admin_removed", diff, after);
		}
		Menus.refreshAll(src.getServer());
		return 1;
	}

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
