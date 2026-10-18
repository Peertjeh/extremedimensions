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
	public static final String EXIT = "exit";
	public static final String AGAIN = "again";
	/** The bots switch while queued: bots welcome, or not. */
	public static final String BOTS_ON = "bots_on";
	public static final String BOTS_OFF = "bots_off";
	/** Hotbar slot of the bots switch while queued (next to the "leave queue" item). */
	public static final int BOTS_SLOT = 7;
	/** Hotbar slot of the "play again" item after a match. */
	public static final int AGAIN_SLOT = 0;
	/** Hotbar slot of the door back to the normal world, or the "leave queue" item while queued. */
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

	/** The bots switch: a name tag while bots may fill the queue, grey dye while they may not. */
	public static ItemStack bots(ServerPlayer p) {
		boolean on = !net.battlehub.data.PlayerStore.get(p).noBots;
		int seconds = ConfigManager.config().bots.fillAfterSeconds;
		return Icon.of(on ? "minecraft:name_tag" : "minecraft:gray_dye").name(Lang.item(p, on ? "item.bots_on.name" : "item.bots_off.name"))
				.lore(Lang.loreLines(p, on ? "item.bots_on.lore" : "item.bots_off.lore", seconds)).tag(on ? BOTS_ON : BOTS_OFF).build();
	}

	public static ItemStack again(ServerPlayer p) {
		net.battlehub.queue.PlayAgain.Offer o = net.battlehub.queue.PlayAgain.get(p.getUUID());
		return Icon.of("minecraft:lime_dye").name(Lang.item(p, "item.again.name"))
				.lore(Lang.loreLines(p, "item.again.lore", net.battlehub.queue.PlayAgain.label(p, o))).glint(true).tag(AGAIN).build();
	}

	public static ItemStack exit(ServerPlayer p) {
		return Icon.of("minecraft:oak_door").name(Lang.item(p, "item.exit.name"))
				.lore(Lang.loreLines(p, "item.exit.lore")).tag(EXIT).build();
	}

	/**
	 * Puts the compass and the item in the last slot (the door back, or the leave item while queued) in place and removes stray copies anywhere
	 * else, including the cursor. Items in the target slot are moved out of the way, never deleted.
	 */
	public static void ensure(ServerPlayer p, boolean queued) {
		Inventory inv = p.getInventory();
		int compassSlot = ConfigManager.config().compass.slot;
		String lastItem = queued ? LEAVE_QUEUE : (ConfigManager.config().hub.exitItem ? EXIT : "");
		int leaveSlot = !lastItem.isEmpty() && compassSlot != LEAVE_SLOT ? LEAVE_SLOT : -1;
		boolean again = !queued && compassSlot != AGAIN_SLOT && net.battlehub.queue.PlayAgain.active(p.getUUID());
		String botsItem = queued && net.battlehub.bot.Bots.mayFill(p.getUUID()) ? (net.battlehub.data.PlayerStore.get(p).noBots ? BOTS_OFF : BOTS_ON) : "";
		int botsSlot = !botsItem.isEmpty() && compassSlot != BOTS_SLOT ? BOTS_SLOT : -1;
		if (Icon.isHubItem(p.containerMenu.getCarried())) {
			p.containerMenu.setCarried(ItemStack.EMPTY);
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String tag = Icon.tagOf(inv.getItem(i));
			if (tag.isEmpty() || net.battlehub.progression.Wardrobe.isPiece(tag)) {
				continue;
			}
			boolean keep = (i == compassSlot && tag.equals(COMPASS)) || (i == leaveSlot && tag.equals(lastItem))
					|| (again && i == AGAIN_SLOT && tag.equals(AGAIN)) || (i == botsSlot && tag.equals(botsItem));
			if (!keep) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		place(p, compassSlot, COMPASS);
		if (leaveSlot >= 0) {
			place(p, leaveSlot, lastItem);
		}
		if (again) {
			place(p, AGAIN_SLOT, AGAIN);
		}
		if (botsSlot >= 0) {
			place(p, botsSlot, botsItem);
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
		inv.setItem(slot, switch (tag) {
			case COMPASS -> compass(p);
			case LEAVE_QUEUE -> leaveQueue(p);
			case AGAIN -> again(p);
			case BOTS_ON, BOTS_OFF -> bots(p);
			default -> exit(p);
		});
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
