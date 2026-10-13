package net.battlehub.lobby;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The BattleHub Compass and the other lobby items. They are locked: they cannot be moved or dropped. */
public final class HubItems {
	private HubItems() {}

	public static final String COMPASS = "compass";
	public static final String LEAVE_QUEUE = "leave_queue";
	/** Hotbar slot of the "leave queue" item. */
	public static final int LEAVE_SLOT = 8;

	public static ItemStack compass(ServerPlayer p) {
		HubConfig.Compass c = ConfigManager.config().compass;
		return Icon.of(Ids.item(c.item)).name(Lang.item(p, "item.compass.name"))
				.lore(Lang.loreLines(p, "item.compass.lore")).glint(c.glint).tag(COMPASS).clean().build();
	}

	public static ItemStack leaveQueue(ServerPlayer p) {
		return Icon.of("minecraft:red_dye").name(Lang.item(p, "item.leave_queue.name"))
				.lore(Lang.loreLines(p, "item.leave_queue.lore")).tag(LEAVE_QUEUE).build();
	}

	/**
	 * Puts the compass (and, while queued, the leave item) in their fixed slots and removes stray copies anywhere
	 * else, including the cursor. Items in the target slot are moved out of the way, never deleted.
	 */
	public static void ensure(ServerPlayer p, boolean queued) {
		Inventory inv = p.getInventory();
		int compassSlot = ConfigManager.config().compass.slot;
		int leaveSlot = queued && compassSlot != LEAVE_SLOT ? LEAVE_SLOT : -1;
		if (Icon.isHubItem(p.containerMenu.getCarried())) {
			p.containerMenu.setCarried(ItemStack.EMPTY);
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String tag = Icon.tagOf(inv.getItem(i));
			if (tag.isEmpty()) {
				continue;
			}
			boolean keep = (i == compassSlot && tag.equals(COMPASS)) || (i == leaveSlot && tag.equals(LEAVE_QUEUE));
			if (!keep) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		place(p, compassSlot, COMPASS);
		if (leaveSlot >= 0) {
			place(p, leaveSlot, LEAVE_QUEUE);
		}
	}

	private static void place(ServerPlayer p, int slot, String tag) {
		Inventory inv = p.getInventory();
		ItemStack current = inv.getItem(slot);
		if (Icon.tagOf(current).equals(tag)) {
			// Refresh the name when the language changed, cheap enough every few seconds
			return;
		}
		if (!current.isEmpty()) {
			inv.setItem(slot, ItemStack.EMPTY);
			if (!inv.add(current)) {
				// Inventory full: put it back where the hub item would go is impossible, so drop nothing and
				// keep the player's item; the hub item waits until there is room.
				inv.setItem(slot, current);
				return;
			}
		}
		inv.setItem(slot, tag.equals(COMPASS) ? compass(p) : leaveQueue(p));
	}

	/** Removes every BattleHub item (before a kit is given). */
	public static void strip(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (Icon.isHubItem(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}
}
