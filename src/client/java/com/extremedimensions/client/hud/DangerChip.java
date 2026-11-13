package com.extremedimensions.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.state.ClientDanger;
import com.extremedimensions.client.ui.Ui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The combat lock, told only when it matters: while the wound is after the player (a swarm, a hunting Lurker, the
 * Keeper, a raid, an Elder, or a blow from one of its creatures lately), leaving the game costs curses, and the pause
 * menu says so across its top, where the button to leave is; that button then asks before it leaves (3.55.0). Nothing
 * shows during play: what comes for the player should still come as a surprise, not be given away by the HUD.
 */
public final class DangerChip {
	private DangerChip() {}

	private static String reason() {
		return Ui.tr("hud.extremedimensions.danger." + ClientDanger.why());
	}

	/** What holds them and what leaving now would cost, in a line. */
	private static String line() {
		int n = ClientDanger.curses();
		return n == 1 ? Ui.tr("hud.extremedimensions.danger.pause.one", reason()) : Ui.tr("hud.extremedimensions.danger.pause.many", reason(), n);
	}

	/** How long it holds them yet (a blow's hold), or that it holds while what hunts them lasts. */
	private static String sub() {
		int secs = ClientDanger.seconds();
		return secs >= 0 ? Ui.tr("hud.extremedimensions.danger.safe_in", secs) : Ui.tr("hud.extremedimensions.danger.while");
	}

	/** Across the top of the pause menu, above its title: what holds them and what leaving now would cost. */
	public static void pause(GuiGraphicsExtractor g, Font font, int screenW) {
		if (!ClientDanger.active()) {
			return;
		}
		String line = line(), sub = sub();
		int w = Math.min(screenW - 16, Math.max(font.width(line), font.width(sub)) + 16);
		int x = (screenW - w) / 2, y = 8;
		Ui.chip(g, x, y, w, 26, Ui.DANGER, 1.0);
		g.text(font, line, (screenW - font.width(line)) / 2, y + 4, Ui.argb(255, Ui.DANGER));
		g.text(font, sub, (screenW - font.width(sub)) / 2, y + 15, Ui.argb(255, Ui.TEXT));
	}

	/**
	 * The pause menu's button to leave (Disconnect, or Save and Quit to Title) asks first while leaving would cost
	 * curses (3.55.0): what holds the player and what it would cost, Leave anyway or Cancel. Safe, it leaves at once as
	 * ever. The game's button is found by its words (their keys) and stands hidden behind one that asks; where it is not
	 * found, the chip above still says what leaving costs.
	 */
	public static void guardLeave(Screen pause) {
		List<AbstractWidget> widgets = Screens.getWidgets(pause);
		List<Button> leave = new ArrayList<>();
		for (AbstractWidget w : widgets) {
			if (w instanceof Button b && b.visible && leaves(b)) {
				leave.add(b);
			}
		}
		for (Button b : leave) {
			Button ask = Button.builder(b.getMessage(), x -> {
				if (!ClientDanger.active()) {
					b.onPress(null);
					return;
				}
				Minecraft mc = Minecraft.getInstance();
				mc.gui.setScreen(new ConfirmScreen(yes -> {
					if (yes) {
						b.onPress(null);
					} else {
						mc.gui.setScreen(pause);
					}
				}, Component.translatable("gui.extremedimensions.leave.title"), Component.literal(line() + " " + sub()),
						Component.translatable("gui.extremedimensions.leave.yes"), Component.translatable("gui.cancel")));
			}).pos(b.getX(), b.getY()).size(b.getWidth(), b.getHeight()).build();
			ask.active = b.active;
			// (the game's own stays, out of sight and out of reach, for the asking one to press)
			b.visible = false;
			b.active = false;
			widgets.add(ask);
		}
	}

	private static boolean leaves(Button b) {
		return b.getMessage().getContents() instanceof TranslatableContents t
				&& ("menu.disconnect".equals(t.getKey()) || "menu.returnToMenu".equals(t.getKey()));
	}
}
