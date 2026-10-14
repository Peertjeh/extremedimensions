package net.battlehub.lobby;

import net.battlehub.config.ConfigManager;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Decides which inventory clicks and drops are blocked (hub items, and dropping in matches or the lobby). */
public final class Locks {
	private Locks() {}

	/** Players who may not drop anything right now. */
	public static boolean dropsLocked(ServerPlayer p) {
		net.battlehub.match.Match m = net.battlehub.HubEvents.matchOf(p);
		if (m != null) {
			return !m.allowsDrops(p);
		}
		if (!ConfigManager.config().protection.noItemDrops || p.isCreative()) {
			return Sessions.state(p.getUUID()) == Sessions.State.MATCH;
		}
		Sessions.State s = Sessions.state(p.getUUID());
		return s == Sessions.State.MATCH || s == Sessions.State.SPECTATING || Lobby.inArea(p);
	}

	public static boolean blocksClick(AbstractContainerMenu menu, int slot, int button, ContainerInput input, ServerPlayer p) {
		ItemStack carried = menu.getCarried();
		if (Icon.isHubItem(carried)) {
			return true;
		}
		if (slot >= 0 && slot < menu.slots.size()) {
			if (Icon.isHubItem(menu.getSlot(slot).getItem())) {
				return true;
			}
		}
		if (input == ContainerInput.SWAP) {
			// button = hotbar index 0-8, or 40 for the offhand
			int inv = button;
			if (inv >= 0 && inv < p.getInventory().getContainerSize() && Icon.isHubItem(p.getInventory().getItem(inv))) {
				return true;
			}
		}
		if (dropsLocked(p)) {
			if (input == ContainerInput.THROW) {
				return true;
			}
			if (slot == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && !carried.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public static boolean blocksDrop(ServerPlayer p) {
		return Icon.isHubItem(p.getInventory().getSelectedItem()) || dropsLocked(p);
	}
}
