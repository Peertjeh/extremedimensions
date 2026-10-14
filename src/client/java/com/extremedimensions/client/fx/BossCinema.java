package com.extremedimensions.client.fx;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * The cinema of an Elder's fight, drawn over the game. As an Elder arrives its name is struck across the screen with
 * its title beneath; the names of its great blows flash up under the health bars as it calls them; when it unleashes
 * its ultimate black bars close in from above and below, the ultimate's name is slashed across the middle, the edges
 * of the screen darken in its colour and a thin line in the lower bar runs out with its time; when it breaks, a flash,
 * and the bars lift. Calm mode keeps the bars and names but drops the flashes and the pulsing.
 */
public class BossCinema implements HudElement {
	private static final String[] NAMES = {"matriarch", "ferryman", "weaver"};
	private static final int[] COLOURS = {0xB66BFF, 0x6FE3D0, 0xE6D4FF};

	private static long introAt = -1;
	private static int introBoss;
	private static long moveAt = -1;
	private static String moveKey = "";
	private static int moveBoss;
	private static long ultAt = -1, ultEnd = -1, ultLength = 1;
	private static String ultKey = "";
	private static String ultLabel = "gui.extremedimensions.elder.ultimate_label";
	private static int ultBoss;
	private static long flashAt = -1;
	private static float flashStrength;

	/** A piece of cinema from the server. */
	public static void handle(ModPackets.BossFxPayload fx) {
		long now = System.currentTimeMillis();
		int boss = Math.max(0, Math.min(2, fx.boss()));
		switch (fx.kind()) {
			case ModPackets.BossFxPayload.INTRO -> {
				introAt = now;
				introBoss = boss;
			}
			case ModPackets.BossFxPayload.SHAKE -> CameraShake.add(fx.strength(), fx.value());
			case ModPackets.BossFxPayload.MOVE -> {
				// A blow called while the title card still shows: the card makes way
				if (introAt >= 0 && now - introAt < 4100) {
					introAt = now - 4100;
				}
				moveAt = now;
				moveKey = fx.key();
				moveBoss = boss;
			}
			case ModPackets.BossFxPayload.ULTIMATE -> {
				// The ultimate takes the whole screen: a title card or a called blow still showing makes way
				introAt = -1;
				moveAt = -1;
				ultAt = now;
				ultEnd = -1;
				ultLength = Math.max(1, fx.value()) * 50L;
				ultKey = fx.key();
				ultLabel = "gui.extremedimensions.elder.ultimate_label";
				ultBoss = boss;
			}
			case ModPackets.BossFxPayload.DEATH -> {
				// It dies as it fought its ultimate: the bars close in on its name, and the view is held on it
				introAt = -1;
				moveAt = -1;
				ultAt = now;
				ultEnd = -1;
				ultLength = (long) (Math.max(1, fx.strength()) * 50L);
				ultKey = fx.key();
				ultLabel = "gui.extremedimensions.elder.vanquished_label";
				ultBoss = boss;
				CameraLock.lock(fx.value(), ultLength);
			}
			case ModPackets.BossFxPayload.FLASH -> {
				flashAt = now;
				flashStrength = Math.max(0, Math.min(1, fx.strength()));
			}
			case ModPackets.BossFxPayload.ULTIMATE_END -> {
				if (ultAt >= 0 && ultEnd < 0) {
					ultEnd = now;
				}
			}
			default -> {
			}
		}
	}

	/** Whether an ultimate's bars are on the screen (for tests). */
	public static boolean ultimateShowing() {
		return ultAt >= 0 && (ultEnd < 0 || System.currentTimeMillis() - ultEnd < 700);
	}

	public static void reset() {
		introAt = -1;
		moveAt = -1;
		ultAt = -1;
		ultEnd = -1;
		flashAt = -1;
		CameraLock.release();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth(), h = g.guiHeight();
		long now = System.currentTimeMillis();
		boolean calm = Ui.calm();
		ultimate(g, font, w, h, now, calm);
		intro(g, font, w, h, now);
		move(g, font, w, now);
		if (flashAt >= 0 && !calm) {
			double t = (now - flashAt) / 700.0;
			if (t >= 1) {
				flashAt = -1;
			} else {
				g.fill(0, 0, w, h, Ui.argb((1 - Ui.ease(t)) * 0.85 * flashStrength, 0xFFFFFF));
			}
		}
	}

	private static void scaled(GuiGraphicsExtractor g, Font font, String text, int cx, int y, float scale, int rgb, double alpha) {
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		Ui.centered(g, font, text, Math.round(cx / scale), Math.round(y / scale), rgb, alpha);
		g.pose().popMatrix();
	}

	private static void intro(GuiGraphicsExtractor g, Font font, int w, int h, long now) {
		if (introAt < 0) {
			return;
		}
		double t = (now - introAt) / 1000.0;
		if (t > 5.0) {
			introAt = -1;
			return;
		}
		double alpha = Math.min(Ui.ease(Math.min(1, t / 0.6)), Ui.ease(Math.min(1, (5.0 - t) / 0.9)));
		int colour = COLOURS[introBoss];
		String title = Ui.tr("gui.extremedimensions.elder.title." + NAMES[introBoss]);
		String sub = Ui.tr("gui.extremedimensions.elder.sub." + NAMES[introBoss]);
		int cy = (int) (h * 0.32);
		// A line slashes across, the name settles onto it
		double grow = Ui.ease(Math.min(1, t / 0.8));
		int half = (int) (w * 0.36 * grow);
		g.fill(w / 2 - half, cy + 22, w / 2 + half, cy + 23, Ui.argb(alpha * 0.9, colour));
		g.fill(w / 2 - half / 2, cy + 24, w / 2 + half / 2, cy + 25, Ui.argb(alpha * 0.5, colour));
		float scale = (float) (3.6 - 0.4 * Ui.ease(Math.min(1, t / 1.2)));
		scaled(g, font, title, w / 2 + 2, cy + 2, scale, 0x000000, alpha * 0.6);
		scaled(g, font, title, w / 2, cy, scale, Ui.mix(0xFFFFFF, colour, 0.25), alpha);
		scaled(g, font, sub, w / 2, cy + 30, 1.25F, Ui.mix(Ui.TEXT, colour, 0.4), alpha * Math.min(1, Math.max(0, (t - 0.5) / 0.6)));
	}

	private static void move(GuiGraphicsExtractor g, Font font, int w, long now) {
		if (moveAt < 0) {
			return;
		}
		double t = (now - moveAt) / 1000.0;
		if (t > 2.2) {
			moveAt = -1;
			return;
		}
		double alpha = Math.min(Ui.ease(Math.min(1, t / 0.25)), Ui.ease(Math.min(1, (2.2 - t) / 0.5)));
		int slide = (int) ((1 - Ui.ease(Math.min(1, t / 0.35))) * 40);
		String name = "« " + Ui.tr(moveKey) + " »";
		int y = 58;
		int colour = COLOURS[moveBoss];
		int tw = (int) (font.width(name) * 1.6F);
		g.fill(w / 2 - tw / 2 - 10 + slide, y - 4, w / 2 + tw / 2 + 10 + slide, y + 17, Ui.argb(alpha * 0.55, Ui.INK));
		g.fill(w / 2 - tw / 2 - 10 + slide, y + 17, w / 2 + tw / 2 + 10 + slide, y + 18, Ui.argb(alpha * 0.9, colour));
		scaled(g, font, name, w / 2 + slide, y, 1.6F, Ui.mix(0xFFFFFF, colour, 0.35), alpha);
	}

	private static void ultimate(GuiGraphicsExtractor g, Font font, int w, int h, long now, boolean calm) {
		if (ultAt < 0) {
			return;
		}
		double in = Ui.ease(Math.min(1, (now - ultAt) / 600.0));
		double out = ultEnd < 0 ? 1 : 1 - Ui.ease(Math.min(1, (now - ultEnd) / 700.0));
		// A lost ending (left the fight) lifts the bars on its own a little after the ultimate's time
		if (ultEnd < 0 && now - ultAt > ultLength + 3000) {
			ultEnd = now;
		}
		if (out <= 0) {
			ultAt = -1;
			ultEnd = -1;
			return;
		}
		double k = in * out;
		int colour = COLOURS[ultBoss];
		double run = Math.min(1, (now - ultAt) / (double) ultLength);
		// The edges darken in its colour, pulsing faster as it builds
		if (!calm) {
			double pulse = 0.5 + 0.5 * Math.sin((now - ultAt) / 1000.0 * (2 + run * 8));
			Ui.vignette(g, w, h, Ui.mix(Ui.SHADOW, colour, 0.35), (int) (9 * k * (0.6 + 0.4 * pulse)), 26);
		}
		int bar = (int) (h * 0.12 * k);
		g.fill(0, 0, w, bar, Ui.argb(0.96 * k, 0x000000));
		g.fill(0, h - bar, w, h, Ui.argb(0.96 * k, 0x000000));
		g.fill(0, bar, w, bar + 1, Ui.argb(0.8 * k, colour));
		g.fill(0, h - bar - 1, w, h - bar, Ui.argb(0.8 * k, colour));
		// Its time runs out along the lower bar
		int line = (int) (w * (1 - run));
		g.fill(w / 2 - line / 2, h - bar / 2, w / 2 + line / 2, h - bar / 2 + 1, Ui.argb(0.9 * k, colour));
		String label = Ui.tr(ultLabel);
		String name = Ui.tr(ultKey);
		if (bar > 12) {
			Ui.centered(g, font, label + "  —  " + name, w / 2, bar / 2 - 4, Ui.mix(0xFFFFFF, colour, 0.3), k);
		}
		// Its name slashed across the middle as it begins
		double t = (now - ultAt) / 1000.0;
		if (t < 3.2 && ultEnd < 0) {
			double a = Math.min(Ui.ease(Math.min(1, t / 0.3)), Ui.ease(Math.min(1, (3.2 - t) / 0.8)));
			int cy = (int) (h * 0.42);
			int half = (int) (w * 0.45 * Ui.ease(Math.min(1, t / 0.4)));
			g.fill(w / 2 - half, cy - 3, w / 2 + half, cy - 2, Ui.argb(a * 0.9, colour));
			g.fill(w / 2 - half, cy + 30, w / 2 + half, cy + 31, Ui.argb(a * 0.9, colour));
			scaled(g, font, label, w / 2, cy + 2, 1.2F, Ui.mix(Ui.TEXT, colour, 0.5), a);
			float scale = (float) (3.2 + 0.6 * (1 - Ui.ease(Math.min(1, t / 0.5))));
			scaled(g, font, name, w / 2 + 2, cy + 14, scale, 0x000000, a * 0.6);
			scaled(g, font, name, w / 2, cy + 12, scale, Ui.mix(0xFFFFFF, colour, 0.2), a);
		}
	}
}
