package net.battlehub.gui;

import java.util.List;
import java.util.function.ToLongFunction;

import net.battlehub.core.GameMode;
import net.battlehub.core.Leaderboard;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Ranks;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Top 10 per ranked mode (ELO), plus wins, level, best streak and kills. */
public final class LeaderboardMenu extends Screen {
	private final Runnable back;

	public LeaderboardMenu(ServerPlayer viewer, Runnable back) {
		super(viewer);
		this.back = back;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.leaderboards.title");
	}

	@Override
	public int rows() {
		return 5;
	}

	@Override
	public void draw(Canvas c) {
		int start = Ranks.startElo();
		GameMode[] duels = GameMode.duels();
		int[] eloSlots = net.battlehub.core.MenuSlots.centered(duels.length, 1, 5);
		for (int i = 0; i < duels.length; i++) {
			GameMode m = duels[i];
			// Only players who played ranked in this mode are listed.
			c.set(eloSlots[i], board(Icons.mode(m), Lang.item(viewer, "menu.leaderboards.elo", Lang.tr(viewer, Icons.modeKey(m))),
					p -> p.elo.containsKey(m.id()) ? p.elo(m, start) : Long.MIN_VALUE, Long.MIN_VALUE, true, m).build());
		}
		c.set(28, board(Items.DIAMOND, Lang.item(viewer, "menu.leaderboards.wins"), p -> p.overall.wins, 0, false, null).build());
		c.set(30, board(Items.EXPERIENCE_BOTTLE, Lang.item(viewer, "menu.leaderboards.level"), p -> p.xp, 0, false, null).build());
		c.set(32, board(Items.BLAZE_POWDER, Lang.item(viewer, "menu.leaderboards.streak"), p -> p.overall.bestStreak, 0, false, null).build());
		c.set(34, board(Items.IRON_SWORD, Lang.item(viewer, "menu.leaderboards.kills"), p -> p.overall.kills, 0, false, null).build());
		c.back(back);
		c.fill();
	}

	private Icon board(Item item, Component name, ToLongFunction<PlayerProfile> value, long floor, boolean elo, GameMode mode) {
		Icon icon = Icon.of(item).name(name).clean();
		List<Leaderboard.Row<PlayerProfile>> rows = Leaderboard.top(PlayerStore.all(), value, 10, floor);
		if (rows.isEmpty()) {
			icon.lore(Lang.lore(viewer, "menu.leaderboards.empty"));
		}
		for (Leaderboard.Row<PlayerProfile> r : rows) {
			PlayerProfile p = r.item();
			Object shown;
			if (elo) {
				shown = Lang.tr(viewer, "menu.leaderboards.elo_value", r.value(), Ranks.tierName(viewer, (int) r.value()));
			} else if (value == null) {
				shown = r.value();
			} else if (item == Items.EXPERIENCE_BOTTLE) {
				shown = Lang.tr(viewer, "menu.leaderboards.level_value", Ranks.curve().levelFor(r.value()));
			} else {
				shown = r.value();
			}
			String key = p.uuid.equals(viewer.getUUID().toString()) ? "menu.leaderboards.row_self" : "menu.leaderboards.row";
			icon.lore(Lang.lore(viewer, key, r.rank(), p.name, shown));
		}
		return icon;
	}
}
