package net.battlehub.gui;

import net.battlehub.BattleHub;
import net.battlehub.util.Icon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps menu items inside BattleHub menus, whatever the client sends. Clicks in a menu never move anything
 * (see {@link HubMenu}); creative clients, which may set their own inventory slots directly, are refused while a
 * menu is open and for any BattleHub item; and anything that still slips through is swept out of the inventory.
 */
public final class MenuGuard {
	private MenuGuard() {}

	public static boolean isMenuItem(ItemStack stack) {
		return Icon.MENU.equals(Icon.tagOf(stack));
	}

	/** Removes menu items from the player's inventory and cursor; returns how many were removed. */
	public static int sweep(ServerPlayer p) {
		int removed = 0;
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isMenuItem(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
				removed++;
			}
		}
		if (isMenuItem(p.containerMenu.getCarried())) {
			p.containerMenu.setCarried(ItemStack.EMPTY);
			removed++;
		}
		if (p.containerMenu != p.inventoryMenu && isMenuItem(p.inventoryMenu.getCarried())) {
			p.inventoryMenu.setCarried(ItemStack.EMPTY);
			removed++;
		}
		if (removed > 0) {
			BattleHub.LOGGER.info("Removed {} BattleHub menu item(s) from the inventory of {}", removed, p.getScoreboardName());
			resync(p);
		}
		return removed;
	}

	/** Sends the server's real inventory and menu contents, so the client keeps no items it only predicted. */
	public static void resync(ServerPlayer p) {
		p.inventoryMenu.sendAllDataToRemote();
		if (p.containerMenu != p.inventoryMenu) {
			p.containerMenu.sendAllDataToRemote();
		}
	}

	/**
	 * A creative client asks to put {@code stack} in inventory slot {@code slot} (an inventory-menu index; negative
	 * means dropping it). Refused while a BattleHub menu is open, for BattleHub items (menu items, compass copies),
	 * and when it would overwrite or clear a BattleHub item such as the compass.
	 */
	public static boolean blocksCreativeSlot(ServerPlayer p, int slot, ItemStack stack) {
		if (p.containerMenu instanceof HubMenu) {
			return true;
		}
		if (Icon.isHubItem(stack)) {
			return true;
		}
		return slot >= 0 && slot < p.inventoryMenu.slots.size() && Icon.isHubItem(p.inventoryMenu.getSlot(slot).getItem());
	}
}
