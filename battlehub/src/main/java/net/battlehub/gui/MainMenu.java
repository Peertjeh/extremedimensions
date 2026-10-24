package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The BattleHub compass menu, in groups: playing (two rows), you, together and the shop, each with its heading on
 * the left in its own colour. Your profile is at the top, leaving or entering BattleHub at the bottom.
 */
public final class MainMenu extends Screen {
	/** Where the buttons sit (slots), group by group. */
	public static final int[] PLAY = {10, 12, 14, 16, 19, 21, 23, 25};
	public static final int[] YOU = {29, 30, 31, 32, 33, 34};
	public static final int[] TOGETHER = {38, 39};
	public static final int SHOP = 42;
	public static final int PROFILE = 4;
	public static final int TRAVEL = 45;

	public MainMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.main.title");
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public int refreshTicks() {
		return 40;
	}

	@Override
	public Style.Section section() {
		return Style.Section.PLAY;
	}

	/** A button: its name, what it is, and what a click does. */
	private Icon button(Item item, String key) {
		return Icon.of(item).name(Lang.item(viewer, key)).lore(Lang.loreLines(viewer, key + ".lore")).clean()
				.action(Lang.tr(viewer, "menu.click_to_open"));
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		// you, at the top
		c.set(PROFILE, Icon.head(viewer).name(Lang.item(viewer, "menu.main.profile", viewer.getGameProfile().name()))
				.fact(Lang.lore(viewer, "menu.wallet.level", net.battlehub.progression.Ranks.curve().levelFor(prof.xp)))
				.fact(Lang.lore(viewer, "menu.main.profile.coins", prof.coins))
				.fact(Lang.lore(viewer, "menu.wallet.prestige", prof.prestige))
				.action(Lang.tr(viewer, "menu.main.profile.click")).build(),
				click -> new StatsMenu(viewer, viewer.getUUID()).open());
		// the headings of the groups
		c.set(9, Style.label(Style.Section.PLAY, Lang.item(viewer, "menu.main.group.play")));
		c.set(18, Style.label(Style.Section.PLAY, Lang.item(viewer, "menu.main.group.play")));
		c.set(27, Style.label(Style.Section.PROFILE, Lang.item(viewer, "menu.main.group.you")));
		c.set(36, Style.label(Style.Section.SOCIAL, Lang.item(viewer, "menu.main.group.together")));
		c.set(41, Style.label(Style.Section.SHOP, Lang.item(viewer, "menu.main.group.shop")));
		// playing
		c.set(PLAY[0], button(Items.DIAMOND_SWORD, "menu.main.pvp").build(), click -> new PvpMenu(viewer).open());
		c.set(PLAY[1], button(Items.NETHER_STAR, "menu.main.ranked").build(), click -> new RankedMenu(viewer).open());
		c.set(PLAY[2], button(Items.CAKE, "menu.main.party").build(), click -> new PartyGamesMenu(viewer).open());
		c.set(PLAY[3], button(net.battlehub.util.Ids.item("minecraft:blue_terracotta"), "menu.main.duel_games").build(),
				click -> new ModeListMenu(viewer, ModeListMenu.Kind.GAMES).open());
		c.set(PLAY[4], button(Items.BOW, "menu.main.ranged").build(),
				click -> new ModeListMenu(viewer, ModeListMenu.Kind.RANGED).open());
		c.set(PLAY[5], button(Items.TARGET, "menu.main.practice").build(), click -> new PracticeMenu(viewer).open());
		c.set(PLAY[6], button(Items.NAME_TAG, "menu.main.custom").build(), click -> new CustomGameMenu(viewer).open());
		c.set(PLAY[7], button(Items.ENDER_EYE, "menu.main.spectate").build(), click -> new SpectateMenu(viewer).open());
		// you
		c.set(YOU[0], button(Items.WRITABLE_BOOK, "menu.main.stats").build(), click -> new StatsMenu(viewer, viewer.getUUID()).open());
		c.set(YOU[1], button(Items.MAP, "menu.main.quests").build(), click -> new QuestsMenu(viewer).open());
		c.set(YOU[2], button(Items.EXPERIENCE_BOTTLE, "menu.main.pass").build(), click -> new PassMenu(viewer, 0).open());
		c.set(YOU[3], button(Items.GOLDEN_APPLE, "menu.main.achievements").build(), click -> new AchievementsMenu(viewer, false).open());
		c.set(YOU[4], button(Items.OAK_SIGN, "menu.main.titles").build(), click -> new TitlesMenu(viewer, 0).open());
		c.set(YOU[5], button(Items.CHEST, "menu.main.kits").build(), click -> new KitListMenu(viewer).open());
		// together
		c.set(TOGETHER[0], button(Items.PLAYER_HEAD, "menu.main.friends").build(), click -> new FriendsMenu(viewer).open());
		c.set(TOGETHER[1], button(net.battlehub.util.Ids.item("minecraft:white_banner"), "menu.main.clans").build(), click -> new ClanMenu(viewer).open());
		// the shop
		c.set(SHOP, button(Items.FIREWORK_ROCKET, "menu.main.cosmetics").build(), click -> new CosmeticsMenu(viewer).open());
		Style.footer(c, viewer, section(), null, null, null);
		if (net.battlehub.hub.Travel.inBattleHub(viewer)) {
			c.set(TRAVEL, Icon.of(Items.OAK_DOOR).name(Lang.item(viewer, "menu.main.exit")).lore(Lang.loreLines(viewer, "menu.main.exit.lore"))
					.action(Lang.tr(viewer, "menu.main.exit.click")).build(), click -> {
				viewer.closeContainer();
				net.battlehub.hub.Travel.leave(viewer);
			});
		} else {
			c.set(TRAVEL, Icon.of(Items.ENDER_PEARL).name(Lang.item(viewer, "menu.main.enter")).lore(Lang.loreLines(viewer, "menu.main.enter.lore"))
					.action(Lang.tr(viewer, "menu.main.enter.click")).build(), click -> {
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
