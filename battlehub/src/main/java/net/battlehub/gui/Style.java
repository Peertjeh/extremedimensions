package net.battlehub.gui;

import net.battlehub.core.MenuLayout;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One look for every menu: the top row says where you are, the bottom row goes places, and the frame has the
 * colour of the part of BattleHub you are in (playing green, you light blue, together yellow, the shop magenta).
 * Places are in {@link MenuLayout}.
 */
public final class Style {
	private Style() {}

	public enum Section {
		PLAY("minecraft:lime_stained_glass_pane"),
		PROFILE("minecraft:light_blue_stained_glass_pane"),
		SOCIAL("minecraft:yellow_stained_glass_pane"),
		SHOP("minecraft:magenta_stained_glass_pane"),
		ADMIN("minecraft:red_stained_glass_pane"),
		NONE("minecraft:gray_stained_glass_pane");

		final String pane;

		Section(String pane) {
			this.pane = pane;
		}
	}

	/** A pane in the section's colour without a tooltip. */
	public static ItemStack pane(Section s) {
		return Icon.of(s.pane).hidden().build();
	}

	/** A pane in the section's colour with a name: the heading of a group of buttons. */
	public static ItemStack label(Section s, Component name) {
		return Icon.of(s.pane).name(name).build();
	}

	/** Your coins, level and prestige (top right of every menu). */
	public static ItemStack wallet(ServerPlayer viewer) {
		PlayerProfile prof = PlayerStore.get(viewer);
		int level = net.battlehub.progression.Ranks.curve().levelFor(prof.xp);
		return Icon.of(Items.SUNFLOWER).name(Lang.item(viewer, "menu.wallet", prof.coins))
				.fact(Lang.lore(viewer, "menu.wallet.level", level))
				.fact(Lang.lore(viewer, "menu.wallet.prestige", prof.prestige)).build();
	}

	/** The menu's own icon in the top middle: what it is, with the explanation of its main-menu button. */
	public static void title(Canvas c, ServerPlayer viewer, net.minecraft.world.item.Item item, String key) {
		c.set(MenuLayout.TITLE, Icon.of(item).name(Lang.item(viewer, key)).lore(Lang.loreLines(viewer, key + ".lore")).clean().build());
	}

	/** The top row: the menu's icon in the middle, the wallet on the right, the section's colour round them. */
	public static void header(Canvas c, ServerPlayer viewer, Section s, ItemStack icon) {
		for (int i = 0; i < 9; i++) c.set(i, pane(s));
		c.set(MenuLayout.TITLE, icon);
		c.set(MenuLayout.WALLET, wallet(viewer));
	}

	/**
	 * The bottom row: back (left, when there is somewhere to go back to), the pages either side of the middle (when
	 * there are), close in the middle; the section's colour round them.
	 */
	public static void footer(Canvas c, ServerPlayer viewer, Section s, Runnable back, Runnable previous, Runnable next) {
		int rows = c.rows();
		for (int i = (rows - 1) * 9; i < rows * 9; i++) c.set(i, pane(s));
		if (back != null) {
			c.set(MenuLayout.back(rows), Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.back")).build(), click -> back.run());
		}
		if (previous != null) {
			c.set(MenuLayout.previous(rows), Icon.of(Items.SPECTRAL_ARROW).name(Lang.item(viewer, "menu.page.previous")).build(), click -> previous.run());
		}
		if (next != null) {
			c.set(MenuLayout.next(rows), Icon.of(Items.SPECTRAL_ARROW).name(Lang.item(viewer, "menu.page.next")).build(), click -> next.run());
		}
		c.set(MenuLayout.close(rows), Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.close")).build(), click -> viewer.closeContainer());
	}
}
