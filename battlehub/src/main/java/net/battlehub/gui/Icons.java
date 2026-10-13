package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.battlehub.util.Ids;

/** The vanilla item that represents each mode in menus. */
public final class Icons {
	private Icons() {}

	public static Item mode(GameMode mode) {
		return switch (mode) {
			case SWORD -> Items.DIAMOND_SWORD;
			case AXE -> Items.DIAMOND_AXE;
			case MACE -> Items.MACE;
			case BOW -> Items.BOW;
			case CROSSBOW -> Items.CROSSBOW;
			case BOW_SPLEEF -> Items.TARGET;
			case TNT_RUN -> Items.TNT;
			case SUMO -> Items.SLIME_BALL;
			case KOTH -> Items.GOLDEN_HELMET;
			case CTF -> Ids.item("minecraft:red_banner");
			case PARKOUR -> Items.FEATHER;
			case LMS -> Items.TOTEM_OF_UNDYING;
		};
	}

	/** Lang key of the display name, e.g. "mode.bow_spleef". */
	public static String modeKey(GameMode mode) {
		return "mode." + mode.id();
	}
}
