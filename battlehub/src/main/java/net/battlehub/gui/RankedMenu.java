package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Ranks;
import net.battlehub.queue.Queues;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Ranked: pick a duel mode and enter its ranked queue. Only ranked duels change ELO. */
public final class RankedMenu extends Screen {
	public RankedMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.ranked.title");
	}

	@Override
	public int rows() {
		return 5;
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		GameMode[] modes = GameMode.duels();
		int[] slots = net.battlehub.core.MenuSlots.centered(modes.length, 1, 5);
		for (int i = 0; i < modes.length; i++) {
			GameMode m = modes[i];
			int elo = prof.elo(m, Ranks.startElo());
			int placements = net.battlehub.progression.Seasons.placementsLeft(prof, m);
			boolean queued = Queues.isQueued(viewer.getUUID(), m, true);
			Icon icon = Icon.of(Icons.mode(m)).name(Lang.item(viewer, "menu.ranked.mode", Lang.tr(viewer, Icons.modeKey(m))))
					.lore(placements > 0 ? Lang.lore(viewer, "menu.ranked.placement", placements)
							: Lang.lore(viewer, "menu.ranked.elo", elo, net.battlehub.progression.Seasons.rankName(viewer, elo)))
					.lore(Lang.lore(viewer, "menu.ranked.record", prof.rankedStats(m).wins, prof.rankedStats(m).losses))
					.lore(Lang.lore(viewer, "menu.mode.queued", Queues.waiting(m, true, Queues.formatOf(viewer))))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, queued ? "menu.ranked.click_leave" : "menu.ranked.click_join"))
					.glint(queued).clean();
			c.set(slots[i], icon.build(), click -> {
				Queues.toggle(viewer, m, true);
				if (Queues.isQueued(viewer.getUUID(), m, true)) {
					viewer.closeContainer();
				} else {
					new RankedMenu(viewer).open();
				}
			});
		}
		if (net.battlehub.progression.Seasons.enabled()) {
			Icon season = Icon.of(Items.CLOCK).name(Lang.item(viewer, "menu.ranked.season", net.battlehub.progression.Seasons.current()))
					.lore(Lang.lore(viewer, "menu.ranked.season_left", net.battlehub.progression.Seasons.daysLeft()))
					.lore(Lang.loreLines(viewer, "menu.ranked.season.lore"));
			if (!prof.pastSeasons.isEmpty()) {
				net.battlehub.core.SeasonRecord last = prof.pastSeasons.get(prof.pastSeasons.size() - 1);
				season.lore(Component.empty()).lore(Lang.lore(viewer, "menu.ranked.season_last", last.season,
						net.battlehub.progression.Seasons.rankName(viewer, last.peakElo)));
			}
			c.set(4, season.build());
		}
		c.set(30, FormatButton.icon(viewer), click -> FormatButton.toggle(viewer, () -> new RankedMenu(viewer).open()));
		c.set(32, Icon.of(Items.GOLD_INGOT).name(Lang.item(viewer, "menu.leaderboards"))
				.lore(Lang.loreLines(viewer, "menu.leaderboards.lore")).build(),
				click -> new LeaderboardMenu(viewer, () -> new RankedMenu(viewer).open()).open());
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
