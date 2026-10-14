package net.battlehub.gui;

import java.util.List;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.core.LevelCurve;
import net.battlehub.core.ModeStats;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Ranks;
import net.battlehub.progression.TitleService;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Wins, losses, kills, deaths, streaks, level, coins, title and ELO per mode, for yourself or another player. */
public final class StatsMenu extends Screen {
	private final UUID target;

	public StatsMenu(ServerPlayer viewer, UUID target) {
		super(viewer);
		this.target = target;
	}

	@Override
	public Component title() {
		PlayerProfile p = PlayerStore.get(target);
		return Lang.tr(viewer, "menu.stats.title", p.name);
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile p = PlayerStore.get(target);
		boolean self = target.equals(viewer.getUUID());
		LevelCurve.Progress lvl = Ranks.curve().progress(p.xp);
		Component title = TitleService.display(p);
		ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(target);
		Icon head = online != null ? Icon.head(online) : Icon.head(p.name);
		head.name(Lang.item(viewer, "menu.stats.head", p.name))
				.lore(Lang.lore(viewer, "menu.stats.title_line", title == null ? Lang.tr(viewer, "menu.stats.no_title") : title))
				.lore(Lang.lore(viewer, "menu.stats.level", lvl.level()))
				.lore(Lang.lore(viewer, "menu.stats.xp", Lang.colored(bar(lvl.fraction())), lvl.xpIntoLevel(), lvl.maxed() ? "-" : String.valueOf(lvl.xpForNext())))
				.lore(Lang.lore(viewer, "menu.stats.coins", p.coins));
		c.set(4, head.build());

		ModeStats o = p.overall;
		c.set(20, Icon.of(Items.DIAMOND).name(Lang.item(viewer, "menu.stats.overall"))
				.lore(lines(o)).build());
		Icon ranked = Icon.of(Items.NETHER_STAR).name(Lang.item(viewer, "menu.stats.ranked"));
		for (GameMode m : GameMode.duels()) {
			int elo = p.elo(m, Ranks.startElo());
			ranked.lore(Lang.lore(viewer, "menu.stats.ranked_line", Lang.tr(viewer, Icons.modeKey(m)), elo, Ranks.tierName(viewer, elo)));
		}
		c.set(22, ranked.clean().build());
		Icon streak = Icon.of(Items.BLAZE_POWDER).name(Lang.item(viewer, "menu.stats.streaks"))
				.lore(Lang.lore(viewer, "menu.stats.streak_now", o.streak))
				.lore(Lang.lore(viewer, "menu.stats.streak_best", o.bestStreak));
		c.set(24, streak.build());

		// Duels, then 1v2 and 1v3
		List<GameMode> fights = new java.util.ArrayList<>(List.of(GameMode.duels()));
		fights.addAll(List.of(GameMode.teamFights()));
		int fightStart = 31 - fights.size() / 2;
		for (int i = 0; i < fights.size(); i++) {
			c.set(fightStart + i, modeIcon(p, fights.get(i)).build());
		}
		GameMode[] parties = GameMode.parties();
		int start = 40 - parties.length / 2;
		for (int i = 0; i < parties.length; i++) {
			c.set(start + i, modeIcon(p, parties[i]).build());
		}

		if (self) {
			c.set(47, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.titles"))
					.lore(Lang.loreLines(viewer, "menu.titles.lore")).build(),
					click -> new TitlesMenu(viewer).open());
		}
		c.set(51, Icon.of(Items.GOLD_INGOT).name(Lang.item(viewer, "menu.leaderboards"))
				.lore(Lang.loreLines(viewer, "menu.leaderboards.lore")).build(),
				click -> new LeaderboardMenu(viewer, () -> new StatsMenu(viewer, target).open()).open());
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}

	private java.util.List<Component> lines(ModeStats s) {
		return java.util.List.of(
				Lang.lore(viewer, "menu.stats.wins", s.wins),
				Lang.lore(viewer, "menu.stats.losses", s.losses),
				Lang.lore(viewer, "menu.stats.kills", s.kills),
				Lang.lore(viewer, "menu.stats.deaths", s.deaths),
				Lang.lore(viewer, "menu.stats.kdr", String.format(java.util.Locale.ROOT, "%.2f", s.kdr())),
				Lang.lore(viewer, "menu.stats.played", s.played),
				Lang.lore(viewer, "menu.stats.streak_now", s.streak),
				Lang.lore(viewer, "menu.stats.streak_best", s.bestStreak));
	}

	private Icon modeIcon(PlayerProfile p, GameMode m) {
		ModeStats s = p.peek(m);
		Icon icon = Icon.of(Icons.mode(m)).name(Lang.item(viewer, Icons.modeKey(m))).lore(lines(s));
		if (m == GameMode.TOURNAMENT) {
			icon.lore(Component.empty()).lore(Lang.lore(viewer, "menu.stats.tournaments_won", p.tournamentsWon));
		}
		if (m.isDuel()) {
			int elo = p.elo(m, Ranks.startElo());
			ModeStats r = p.ranked.get(m.id());
			icon.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.stats.elo", elo, Ranks.tierName(viewer, elo)))
					.lore(Lang.lore(viewer, "menu.ranked.record", r == null ? 0 : r.wins, r == null ? 0 : r.losses));
		}
		if (m == GameMode.PARKOUR) {
			for (net.battlehub.core.ParkourCourse.Difficulty d : net.battlehub.core.ParkourCourse.Difficulty.values()) {
				Long best = p.bestTimes.get("parkour_" + d.id());
				if (best != null) {
					icon.lore(Lang.lore(viewer, "menu.stats.best_time_difficulty", Lang.tr(viewer, "parkour.difficulty." + d.id()), time(best)));
				}
			}
			if (p.bestTimes.containsKey("parkour")) {
				icon.lore(Lang.lore(viewer, "menu.stats.best_time", time(p.bestTimes.get("parkour"))));
			}
		}
		return icon.clean();
	}

	static String time(long millis) {
		long s = millis / 1000;
		return String.format(java.util.Locale.ROOT, "%d:%02d.%01d", s / 60, s % 60, (millis % 1000) / 100);
	}

	/** Ten-segment progress bar. */
	static String bar(double fraction) {
		int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
		return "&a" + "■".repeat(filled) + "&8" + "■".repeat(10 - filled);
	}
}
