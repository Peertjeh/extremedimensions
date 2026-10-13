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
	/** The ward on the button: a cracked diamond, two pixels per cell. */
	private static final String[] SIGIL = {
		"...#...",
		"..#.#..",
		".#.#.#.",
		"#.###.#",
		".#.#.#.",
		"..#.#..",
		"...#...",
	};
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
		int total = 0;
		for (ClientState.Mark m : ClientState.marks) {
			total += m.stacks();
		}
		// A dark socket with an ember rim: the rune breathes red while curses are carried
		double breath = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 600.0);
		boolean hot = this.isHovered();
		int rim = total > 0 ? Ui.mix(Ui.EMBER_DEEP, Ui.DANGER, 0.3 + 0.4 * breath) : Ui.mix(Ui.MUTED, Ui.EMBER_DEEP, 0.35);
		int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
		g.fill(x, y, x + w, y + h, Ui.argb(255, Ui.SHADOW));
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Ui.argb(255, hot ? Ui.mix(rim, 0xFFFFFF, 0.3) : rim));
		g.fill(x + 2, y + 2, x + w - 2, y + h - 2, Ui.argb(255, Ui.mix(Ui.INK, rim, hot ? 0.3 : 0.16)));
		g.fill(x + 2, y + 2, x + w - 2, y + 3, Ui.argb(0.18, 0xFFFFFF));
		int ink = Ui.argb(255, Ui.mix(rim, 0xFFFFFF, hot ? 0.55 : 0.3));
		for (int row = 0; row < SIGIL.length; row++) {
			for (int col = 0; col < SIGIL[row].length(); col++) {
				if (SIGIL[row].charAt(col) == '#') {
					g.fill(x + 3 + col * 2, y + 2 + row * 2, x + 5 + col * 2, y + 4 + row * 2, ink);
				}
			}
		}
		if (total > 0) {
			// The count sits on its own dark plate in the corner, clear of the sigil
			Minecraft mc = Minecraft.getInstance();
			String n = total > 99 ? "99" : String.valueOf(total);
			int tw = mc.font.width(n);
			int tx = x + w - tw, ty = y + h - 7;
			g.fill(tx - 1, ty - 1, x + w + 1, y + h + 1, Ui.argb(220, Ui.SHADOW));
			g.text(mc.font, n, tx, ty, Ui.argb(255, Ui.PARCHMENT), false);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
