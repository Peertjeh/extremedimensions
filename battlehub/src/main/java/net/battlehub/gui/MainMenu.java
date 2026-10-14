package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The BattleHub Compass menu: Duels, Ranged, Party Games, Ranked, Spectate, Stats. */
public final class MainMenu extends Screen {
	public MainMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.main.title");
	}

	@Override
	public int rows() {
		return 3;
	}

	@Override
	public void draw(Canvas c) {
		c.set(10, Icon.of(Items.DIAMOND_SWORD).name(Lang.item(viewer, "menu.main.duels"))
				.lore(Lang.loreLines(viewer, "menu.main.duels.lore")).clean().build(),
				click -> new ModeListMenu(viewer, ModeListMenu.Kind.MELEE).open());
		c.set(11, Icon.of(Items.BOW).name(Lang.item(viewer, "menu.main.ranged"))
				.lore(Lang.loreLines(viewer, "menu.main.ranged.lore")).clean().build(),
				click -> new ModeListMenu(viewer, ModeListMenu.Kind.RANGED).open());
		c.set(12, Icon.of(Items.CAKE).name(Lang.item(viewer, "menu.main.party"))
				.lore(Lang.loreLines(viewer, "menu.main.party.lore")).clean().build(),
				click -> new PartyGamesMenu(viewer).open());
		c.set(14, Icon.of(Items.NETHER_STAR).name(Lang.item(viewer, "menu.main.ranked"))
				.lore(Lang.loreLines(viewer, "menu.main.ranked.lore")).clean().build(),
				click -> new RankedMenu(viewer).open());
		c.set(15, Icon.of(Items.ENDER_EYE).name(Lang.item(viewer, "menu.main.spectate"))
				.lore(Lang.loreLines(viewer, "menu.main.spectate.lore")).clean().build(),
				click -> new SpectateMenu(viewer).open());
		c.set(16, Icon.of(Items.WRITABLE_BOOK).name(Lang.item(viewer, "menu.main.stats"))
				.lore(Lang.loreLines(viewer, "menu.main.stats.lore")).clean().build(),
				click -> new StatsMenu(viewer, viewer.getUUID()).open());
		if (net.battlehub.hub.Travel.inBattleHub(viewer)) {
			c.set(22, Icon.of(Items.OAK_DOOR).name(Lang.item(viewer, "menu.main.exit"))
					.lore(Lang.loreLines(viewer, "menu.main.exit.lore")).build(),
					click -> {
						viewer.closeContainer();
						net.battlehub.hub.Travel.leave(viewer);
					});
		} else {
			c.set(22, Icon.of(Items.ENDER_PEARL).name(Lang.item(viewer, "menu.main.enter"))
					.lore(Lang.loreLines(viewer, "menu.main.enter.lore")).build(),
					click -> {
						viewer.closeContainer();
						net.battlehub.hub.Travel.enter(viewer);
					});
		}
		c.fill();
	}

	/** Helper for submenus: the coloured mode name. */
	static Component modeName(ServerPlayer p, GameMode mode) {
		return Lang.tr(p, Icons.modeKey(mode));
	}
}
