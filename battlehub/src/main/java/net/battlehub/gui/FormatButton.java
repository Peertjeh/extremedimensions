package net.battlehub.gui;

import net.battlehub.core.DuelFormat;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The "Best of 1 / Best of 3" switch shown in the duel menus; the choice is remembered per player. */
final class FormatButton {
	private FormatButton() {}

	static ItemStack icon(ServerPlayer viewer) {
		DuelFormat f = PlayerStore.get(viewer).format();
		return Icon.of(f == DuelFormat.BO3 ? Items.GOLDEN_SWORD : Items.WOODEN_SWORD)
				.name(Lang.item(viewer, "menu.format", Lang.tr(viewer, "format." + f.id())))
				.lore(Lang.loreLines(viewer, "menu.format.lore")).glint(f == DuelFormat.BO3).clean().build();
	}

	/** Switches the format and reopens the menu with {@code reopen}. */
	static void toggle(ServerPlayer viewer, Runnable reopen) {
		PlayerProfile prof = PlayerStore.get(viewer);
		prof.duelFormat = prof.format().other().id();
		PlayerStore.save(viewer.getUUID());
		reopen.run();
	}
}
