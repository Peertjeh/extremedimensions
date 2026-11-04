package com.extremedimensions.client.hud;

import com.extremedimensions.client.state.ClientDanger;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The combat lock, told only when it matters: while the wound is after the player (a swarm, a hunting Lurker, the
 * Keeper, a raid, an Elder, or a blow from one of its creatures lately), leaving the game costs curses, and the pause
 * menu says so across its top, where the button to leave is. Nothing shows during play: what comes for the player should
 * still come as a surprise, not be given away by the HUD.
 */
public final class DangerChip {
	private DangerChip() {}

	private static String reason() {
		return Ui.tr("hud.extremedimensions.danger." + ClientDanger.why());
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
