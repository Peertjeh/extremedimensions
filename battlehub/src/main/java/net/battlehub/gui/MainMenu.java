package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The BattleHub Compass menu: games on the first row, practice, kits, quests, pass and achievements on the second. */
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
		return 5;
	}

	@Override
	public void draw(Canvas c) {
		c.set(10, Icon.of(Items.DIAMOND_SWORD).name(Lang.item(viewer, "menu.main.pvp"))
				.lore(Lang.loreLines(viewer, "menu.main.pvp.lore")).clean().build(),
				click -> new PvpMenu(viewer).open());
		c.set(11, Icon.of(Items.BOW).name(Lang.item(viewer, "menu.main.ranged"))
				.lore(Lang.loreLines(viewer, "menu.main.ranged.lore")).clean().build(),
				click -> new ModeListMenu(viewer, ModeListMenu.Kind.RANGED).open());
		c.set(12, Icon.of(Items.CAKE).name(Lang.item(viewer, "menu.main.party"))
				.lore(Lang.loreLines(viewer, "menu.main.party.lore")).clean().build(),
				click -> new PartyGamesMenu(viewer).open());
		c.set(13, Icon.of(Items.FIREWORK_ROCKET).name(Lang.item(viewer, "menu.main.cosmetics"))
				.lore(Lang.loreLines(viewer, "menu.main.cosmetics.lore")).clean().build(),
				click -> new CosmeticsMenu(viewer).open());
		c.set(14, Icon.of(Items.NETHER_STAR).name(Lang.item(viewer, "menu.main.ranked"))
				.lore(Lang.loreLines(viewer, "menu.main.ranked.lore")).clean().build(),
				click -> new RankedMenu(viewer).open());
		c.set(15, Icon.of(Items.ENDER_EYE).name(Lang.item(viewer, "menu.main.spectate"))
				.lore(Lang.loreLines(viewer, "menu.main.spectate.lore")).clean().build(),
				click -> new SpectateMenu(viewer).open());
		c.set(16, Icon.of(Items.WRITABLE_BOOK).name(Lang.item(viewer, "menu.main.stats"))
				.lore(Lang.loreLines(viewer, "menu.main.stats.lore")).clean().build(),
				click -> new StatsMenu(viewer, viewer.getUUID()).open());
		c.set(19, Icon.of(Items.TARGET).name(Lang.item(viewer, "menu.main.practice"))
				.lore(Lang.loreLines(viewer, "menu.main.practice.lore")).clean().build(),
				click -> new PracticeMenu(viewer).open());
		c.set(20, Icon.of(Items.CHEST).name(Lang.item(viewer, "menu.main.kits"))
				.lore(Lang.loreLines(viewer, "menu.main.kits.lore")).clean().build(),
				click -> new KitListMenu(viewer).open());
		c.set(21, Icon.of(Items.WRITABLE_BOOK).name(Lang.item(viewer, "menu.main.quests"))
				.lore(Lang.loreLines(viewer, "menu.main.quests.lore")).clean().build(),
				click -> new QuestsMenu(viewer).open());
		c.set(22, Icon.of(Items.NETHER_STAR).name(Lang.item(viewer, "menu.main.pass"))
				.lore(Lang.loreLines(viewer, "menu.main.pass.lore")).clean().build(),
				click -> new PassMenu(viewer, 0).open());
		c.set(23, Icon.of(Items.GOLDEN_APPLE).name(Lang.item(viewer, "menu.main.achievements"))
				.lore(Lang.loreLines(viewer, "menu.main.achievements.lore")).clean().build(),
				click -> new AchievementsMenu(viewer, false).open());
		c.set(24, Icon.of(Items.PLAYER_HEAD).name(Lang.item(viewer, "menu.main.friends"))
				.lore(Lang.loreLines(viewer, "menu.main.friends.lore")).clean().build(),
				click -> new FriendsMenu(viewer).open());
		c.set(25, Icon.of(net.battlehub.util.Ids.item("minecraft:white_banner")).name(Lang.item(viewer, "menu.main.clans"))
				.lore(Lang.loreLines(viewer, "menu.main.clans.lore")).clean().build(),
				click -> new ClanMenu(viewer).open());
		c.set(31, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.main.custom"))
				.lore(Lang.loreLines(viewer, "menu.main.custom.lore")).clean().build(),
				click -> new CustomGameMenu(viewer).open());
		if (net.battlehub.hub.Travel.inBattleHub(viewer)) {
			c.set(40, Icon.of(Items.OAK_DOOR).name(Lang.item(viewer, "menu.main.exit"))
					.lore(Lang.loreLines(viewer, "menu.main.exit.lore")).build(),
					click -> {
						viewer.closeContainer();
						net.battlehub.hub.Travel.leave(viewer);
					});
		} else {
			c.set(40, Icon.of(Items.ENDER_PEARL).name(Lang.item(viewer, "menu.main.enter"))
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
