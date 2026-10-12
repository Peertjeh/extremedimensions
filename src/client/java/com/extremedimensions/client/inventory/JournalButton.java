package com.extremedimensions.client.inventory;

import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The Tether Journal, one click away: a small button right of the recipe book in the inventory. It follows the recipe
 * book button when the book opens or closes, and opens the journal without having to carry it.
 */
public final class JournalButton extends AbstractButton {
	private static final ItemStack ICON = new ItemStack(ModItems.TETHER_JOURNAL);
	private final AbstractWidget anchor;

	private JournalButton(AbstractWidget anchor) {
		super(anchor.getX() + anchor.getWidth() + 2, anchor.getY(), 20, 18, Component.translatable("item.extremedimensions.tether_journal"));
		this.anchor = anchor;
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.journal_button")));
	}

	public static void attach(InventoryScreen screen) {
		AbstractWidget book = null;
		for (AbstractWidget w : Screens.getWidgets(screen)) {
			if (w instanceof ImageButton) {
				book = w;
				break;
			}
		}
		if (book != null) {
			Screens.getWidgets(screen).add(new JournalButton(book));
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		ClientPlayNetworking.send(new ModPackets.RequestJournalPayload());
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Stay beside the recipe book button wherever it moves
		this.setX(anchor.getX() + anchor.getWidth() + 2);
		this.setY(anchor.getY());
		this.extractDefaultSprite(g);
		g.fakeItem(ICON, this.getX() + 2, this.getY() + 1);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
