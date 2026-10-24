package net.battlehub.gui;

import java.util.List;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.social.Clans;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Your clan (members, points, the weekly war) or how to start or join one, and the clan leaderboard. */
public final class ClanMenu extends Screen {
	public ClanMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.clan.title");
	}

	@Override
	public Style.Section section() {
		return Style.Section.SOCIAL;
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		Clans.Clan clan = Clans.of(viewer.getUUID());
		PlayerProfile me = PlayerStore.get(viewer);
		if (clan == null) {
			c.set(4, Icon.of(net.battlehub.util.Ids.item("minecraft:white_banner")).name(Lang.item(viewer, "menu.clan.none"))
					.lore(Lang.loreLines(viewer, "menu.clan.none.lore", ConfigManager.config().clans.createCost)).build());
			int slot = 10;
			for (String inv : me.clanInvites) {
				Clans.Clan ic = Clans.byName(inv);
				if (ic == null || slot > 16) continue;
				c.set(slot++, Icon.of(Items.PAPER).name(Lang.item(viewer, "menu.clan.invite", ic.name, ic.tag))
						.lore(Lang.lore(viewer, "menu.clan.invite_click")).build(), click -> {
							Clans.join(viewer, ic.name);
							new ClanMenu(viewer).open();
						});
			}
		} else {
			c.set(4, Icon.of(net.battlehub.util.Ids.item("minecraft:white_banner")).name(Lang.item(viewer, "menu.clan.info", clan.name, clan.tag))
					.lore(Lang.lore(viewer, "menu.clan.members", clan.members.size(), ConfigManager.config().clans.maxMembers))
					.lore(Lang.lore(viewer, "menu.clan.points", clan.points, clan.weeklyPoints))
					.lore(Lang.lore(viewer, "menu.clan.wars", clan.warsWon))
					.lore(Lang.loreLines(viewer, "menu.clan.info.lore")).glint(true).build());
			int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
			int i = 0;
			for (String m : clan.members) {
				if (i >= slots.length) break;
				PlayerProfile p = PlayerStore.get(UUID.fromString(m));
				c.set(slots[i++], Icon.head(p.name).name(Lang.item(viewer, m.equals(clan.owner) ? "menu.clan.owner" : "menu.clan.member", p.name)).build());
			}
			c.set(net.battlehub.core.MenuLayout.extra(rows()), Icon.of(Items.LAVA_BUCKET).name(Lang.item(viewer, "menu.clan.leave")).build(), click -> {
				viewer.closeContainer();
				Clans.leave(viewer);
			});
		}
		List<Clans.Clan> top = Clans.top(true);
		Icon board = Icon.of(Items.GOLDEN_HELMET).name(Lang.item(viewer, "menu.clan.top"));
		for (int i = 0; i < Math.min(10, top.size()); i++) {
			Clans.Clan t = top.get(i);
			board.lore(Lang.lore(viewer, "menu.clan.top_row", i + 1, t.name, t.tag, t.weeklyPoints, t.points));
		}
		if (top.isEmpty()) board.lore(Lang.lore(viewer, "menu.leaderboards.empty"));
		c.set(40, board.build());
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
