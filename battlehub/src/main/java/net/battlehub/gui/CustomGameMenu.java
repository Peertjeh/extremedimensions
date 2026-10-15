package net.battlehub.gui;

import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.social.CustomGames;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Private games: pick a mode to open a lobby with a code, or see your lobby (code, players, start, leave). */
public final class CustomGameMenu extends Screen {
	public CustomGameMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.custom.title");
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
		CustomGames.Lobby l = CustomGames.of(viewer.getUUID());
		if (l == null) {
			c.set(4, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.custom.info"))
					.lore(Lang.loreLines(viewer, "menu.custom.info.lore")).build());
			int[] slots = c.inner();
			int i = 0;
			for (GameMode m : GameMode.values()) {
				if (!CustomGames.allowed(m) || i >= slots.length) continue;
				c.set(slots[i++], Icon.of(Icons.mode(m)).name(Lang.item(viewer, Icons.modeKey(m)))
						.lore(Lang.lore(viewer, "menu.custom.create")).clean().build(), click -> {
							CustomGames.create(viewer, m);
							new CustomGameMenu(viewer).open();
						});
			}
		} else {
			boolean owner = l.owner.equals(viewer.getUUID());
			c.set(4, Icon.of(Icons.mode(l.mode)).name(Lang.item(viewer, "menu.custom.lobby", Lang.tr(viewer, Icons.modeKey(l.mode))))
					.lore(Lang.lore(viewer, "menu.custom.code", l.code))
					.lore(Lang.lore(viewer, "menu.custom.players", l.members.size())).glint(true).build());
			int slot = 19;
			for (UUID id : l.members) {
				if (slot > 25) break;
				String name = PlayerStore.get(id).name;
				c.set(slot++, Icon.head(name).name(Lang.item(viewer, id.equals(l.owner) ? "menu.custom.owner" : "menu.custom.member", name)).build());
			}
			if (owner) {
				c.set(39, Icon.of(Items.EMERALD).name(Lang.item(viewer, "menu.custom.start")).build(), click -> {
					viewer.closeContainer();
					CustomGames.start(viewer);
				});
			}
			c.set(41, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.custom.leave")).build(), click -> {
				CustomGames.leave(viewer, false);
				new CustomGameMenu(viewer).open();
			});
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
