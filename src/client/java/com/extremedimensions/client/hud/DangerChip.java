package com.extremedimensions.client.hud;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientDanger;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The combat lock made visible: while the wound is after the player (a swarm, a hunting Lurker, the Keeper, a raid, an
 * Elder, or a blow from one of its creatures lately), leaving the game costs curses, and they are told so where they can
 * see it: a red chip at the top of the meters on the HUD, with what holds them and, for a blow, how long yet; and a line
 * across the top of the pause menu, where the button to leave is.
 */
public final class DangerChip {
	private DangerChip() {}

	public static final int H = 21;

	private static String reason() {
		return Ui.tr("hud.extremedimensions.danger." + ClientDanger.why());
	}

	private static String cost() {
		int n = ClientDanger.curses();
		return n == 1 ? Ui.tr("hud.extremedimensions.danger.cost.one") : Ui.tr("hud.extremedimensions.danger.cost.many", n);
	}

	/** Its width on the HUD, for the column of meters it heads. */
	public static int width(Font font) {
		int secs = ClientDanger.seconds();
		int top = font.width(reason()) + (secs >= 0 ? 6 + font.width(secs + "s") : 0);
		return Math.max(104, Math.max(top, font.width(cost())) + 10);
	}

	/** The chip on the HUD at {@code edge} (its left edge, or its right edge when the meters sit on the right). */
	public static void hud(GuiGraphicsExtractor g, Font font, int edge, int y, boolean right, long now) {
		int w = width(font);
		int x = right ? edge - w : edge;
		Ui.chip(g, x, y, w, H, Ui.DANGER, 1.0);
		if (!ClientOptions.reduceFlashing()) {
			// a slow pulse, like a heart that will not settle
			double pulse = 0.5 + 0.5 * Math.sin(now / 260.0);
			g.fill(x + 1, y + 1, x + w - 1, y + H - 1, Ui.argb(pulse * 0.16, Ui.DANGER));
		}
		g.text(font, reason(), x + 5, y + 3, Ui.argb(255, Ui.DANGER));
		int secs = ClientDanger.seconds();
		if (secs >= 0) {
			String s = secs + "s";
			g.text(font, s, x + w - 5 - font.width(s), y + 3, Ui.argb(255, Ui.PARCHMENT));
		}
		g.text(font, cost(), x + 5, y + 12, Ui.argb(255, Ui.TEXT));
	}

	/** Across the top of the pause menu, above its title: what holds them and what leaving now would cost. */
	public static void pause(GuiGraphicsExtractor g, Font font, int screenW) {
		if (!ClientDanger.active()) {
			return;
		}
		int n = ClientDanger.curses();
		String line = n == 1 ? Ui.tr("hud.extremedimensions.danger.pause.one", reason()) : Ui.tr("hud.extremedimensions.danger.pause.many", reason(), n);
		int secs = ClientDanger.seconds();
		String sub = secs >= 0 ? Ui.tr("hud.extremedimensions.danger.safe_in", secs) : Ui.tr("hud.extremedimensions.danger.while");
		int w = Math.min(screenW - 16, Math.max(font.width(line), font.width(sub)) + 16);
		int x = (screenW - w) / 2, y = 8;
		Ui.chip(g, x, y, w, 26, Ui.DANGER, 1.0);
		g.text(font, line, (screenW - font.width(line)) / 2, y + 4, Ui.argb(255, Ui.DANGER));
		g.text(font, sub, (screenW - font.width(sub)) / 2, y + 15, Ui.argb(255, Ui.TEXT));
	}
}
