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
	public Style.Section section() {
		return Style.Section.PLAY;
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
			bots(c, l, owner);
			c.set(41, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.custom.leave")).build(), click -> {
				CustomGames.leave(viewer, false);
				new CustomGameMenu(viewer).open();
			});
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}

	/**
	 * The bots of a private game (the owner sets them; the others see them): none, a number or as many as fit
	 * (left-click more, right-click fewer), and their level (a click: the next one).
	 */
	private void bots(Canvas c, CustomGames.Lobby l, boolean owner) {
		int room = Math.max(1, CustomGames.maxPlayers(l) - l.members.size());
		int adds = net.battlehub.core.BotFill.custom(l.bots, l.members.size(), CustomGames.maxPlayers(l));
		Component now = l.bots.kind() == net.battlehub.core.BotFill.Kind.FILL ? Lang.tr(viewer, "bots.wish.fill_max", CustomGames.maxPlayers(l))
				: net.battlehub.bot.Bots.wishText(viewer, l.bots, 0);
		Icon bots = Icon.of(Items.ARMOR_STAND).name(Lang.item(viewer, "menu.custom.bots", now))
				.lore(Lang.lore(viewer, "menu.custom.bots.adds", adds));
		if (owner) bots = bots.lore(Lang.loreLines(viewer, "menu.custom.bots.lore"));
		c.set(37, bots.glint(adds > 0).build(), owner ? click -> {
			net.battlehub.core.BotFill.Wish w = click.right() ? l.bots.previous(room) : l.bots.next(room);
			// in a private game the round goes none, 1, 2, ..., as many as fit, none
			l.bots = w.kind() == net.battlehub.core.BotFill.Kind.STANDARD ? net.battlehub.core.BotFill.Wish.NONE : w;
			new CustomGameMenu(viewer).open();
		} : click -> {});
		net.battlehub.core.ai.Difficulty[] levels = net.battlehub.core.ai.Difficulty.values();
		Icon level = Icon.of(Items.EXPERIENCE_BOTTLE).name(Lang.item(viewer, "menu.custom.bot_level", Lang.tr(viewer, "bots.level." + l.botLevel.id())));
		if (owner) level = level.lore(Lang.loreLines(viewer, "menu.custom.bot_level.lore"));
		c.set(43, level.build(), owner ? click -> {
			int i = l.botLevel.ordinal() + (click.right() ? levels.length - 1 : 1);
			l.botLevel = levels[i % levels.length];
			new CustomGameMenu(viewer).open();
		} : click -> {});
	}
}
