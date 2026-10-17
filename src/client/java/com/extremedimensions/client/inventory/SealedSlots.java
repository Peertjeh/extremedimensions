package com.extremedimensions.client.inventory;

import com.extremedimensions.client.mixin.ContainerScreenAccess;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.penalty.SealedPockets;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Sealed Pockets in every menu that shows the player's inventory (the inventory itself, chests, furnaces, villagers,
 * crafting tables...): the sealed slots are darkened and crossed, over the menu's background and under anything
 * drawn on top of it.
 */
public final class SealedSlots {
	private SealedSlots() {}

	/** Set if the menu position cannot be read (the accessor did not apply): then nothing is drawn. */
	private static boolean broken;

	public static void attach(AbstractContainerScreen<?> screen) {
		ScreenEvents.afterBackground(screen).register((s, g, mouseX, mouseY, delta) -> draw(screen, g));
	}

	private static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (broken || mc.player == null || SealedPockets.count(mc.player) <= 0) {
			return;
		}
		int left, top;
		try {
			ContainerScreenAccess pos = (ContainerScreenAccess) screen;
			left = pos.extremedimensions$leftPos();
			top = pos.extremedimensions$topPos();
		} catch (ClassCastException | AbstractMethodError e) {
			broken = true;
			return;
		}
		int edge = Ui.argb(255, Ui.mix(Ui.EMBER_DEEP, Ui.SHADOW, 0.35));
		for (Slot slot : screen.getMenu().slots) {
			if (!(slot.container instanceof Inventory inv) || inv.player != mc.player || !slot.isActive()
					|| !SealedPockets.isSealed(mc.player, slot.getContainerSlot())) {
				continue;
			}
			int x = left + slot.x, y = top + slot.y;
			g.fill(x, y, x + 16, y + 16, Ui.argb(200, Ui.INK));
			g.outline(x, y, 16, 16, edge);
			for (int k = 3; k < 13; k++) {
				g.fill(x + k, y + k, x + k + 1, y + k + 1, edge);
				g.fill(x + 15 - k, y + k, x + 16 - k, y + k + 1, edge);
			}
		}
	}
}
