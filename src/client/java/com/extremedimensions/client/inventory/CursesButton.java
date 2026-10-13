package com.extremedimensions.client.inventory;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The Curses, one click away: a button right of the journal button in the inventory, with the curse's sigil and a
 * count of the stacks carried. It follows the recipe book button when the book opens or closes.
 */
public final class CursesButton extends AbstractButton {
	private final AbstractWidget anchor;

	private CursesButton(AbstractWidget anchor) {
		super(anchor.getX() + anchor.getWidth() + 24, anchor.getY(), 20, 18, Component.translatable("gui.extremedimensions.curses.title"));
		this.anchor = anchor;
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.curses.button")));
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
			Screens.getWidgets(screen).add(new CursesButton(book));
		}
	}

	/** Set when the Curses were asked for from the inventory, so closing them goes back there. */
	private static long requestedAt;

	@Override
	public void onPress(InputWithModifiers input) {
		requestedAt = System.currentTimeMillis();
		ClientPlayNetworking.send(new ModPackets.RequestCursesPayload());
	}

	/** The screen to return to when the Curses screen that is about to open closes. */
	public static Screen takeReturn() {
		Minecraft mc = Minecraft.getInstance();
		boolean fromButton = System.currentTimeMillis() - requestedAt < 3000;
		requestedAt = 0;
		return fromButton && mc.player != null ? new InventoryScreen(mc.player) : null;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Stay beside the journal button wherever the recipe book moves it
		this.setX(anchor.getX() + anchor.getWidth() + 24);
		this.setY(anchor.getY());
		this.extractDefaultSprite(g);
		int total = 0;
		for (ClientState.Mark m : ClientState.marks) {
			total += m.stacks();
		}
		double breath = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 600.0);
		int color = total > 0 ? Ui.mix(Ui.EMBER_DEEP, Ui.DANGER, 0.3 + 0.4 * breath) : Ui.MUTED;
		Ui.rune(g, "curses", this.getX() + 3, this.getY() + 2, color, this.isHovered() ? 1.0 : 0.85);
		if (total > 0) {
			Minecraft mc = Minecraft.getInstance();
			String n = total > 99 ? "99" : String.valueOf(total);
			int tx = this.getX() + this.getWidth() - mc.font.width(n);
			g.text(mc.font, n, tx, this.getY() + this.getHeight() - 8, Ui.argb(255, Ui.PARCHMENT), true);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
