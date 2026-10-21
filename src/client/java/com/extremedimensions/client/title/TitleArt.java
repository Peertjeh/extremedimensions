package com.extremedimensions.client.title;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * "The Tether" on the title screen and behind the loading screens: a slow zoom, mouse parallax on the title screen, a
 * pulsing glow in the portal the chain runs into, drifting sparks, and a rotating quote. The picture carries its own
 * title, so Minecraft's logo and splash text stand aside while it shows (see TitleLogoMixin).
 */
public final class TitleArt {
	private TitleArt() {}

	private static final Identifier TEX = ExtremeDimensions.id("textures/gui/title.png");
	private static final int IMG_W = 1672;
	private static final int IMG_H = 941;
	/** Where the portal the chain runs into sits in the picture (fraction of width and height). */
	private static final double PORTAL_X = 0.10;
	private static final double PORTAL_Y = 0.58;
	/** Just below the picture's own title (fraction of its height). */
	private static final double TITLE_BOTTOM = 0.345;

	/** For tests: how many frames have drawn the art behind a loading screen. */
	private static int loadingFrames = 0;

	public static boolean enabled() {
		return ClientOptions.menu().art();
	}

	public static int loadingFrames() {
		return loadingFrames;
	}

	/** The title screen: the picture following the mouse a little, the quote just under the picture's title. */
	public static void drawTitle(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
		if (!enabled()) {
			return;
		}
		double[] at = picture(g, sw, sh, (0.5 - (double) mx / sw) * 26, (0.5 - (double) my / sh) * 14, 1.07);
		glowAndSparks(g, sw, sh, at);
		// light darkening so the buttons stay legible
		g.fill(0, 0, sw, sh, Ui.argb(48, 0x000000));
		g.fill(0, sh - sh / 3, sw, sh, Ui.argb(48, 0x000000));
		// the quote sits under the picture's title if there is room above the buttons, else near the foot
		int below = (int) (TITLE_BOTTOM * at[1] - at[3]) + 3;
		int buttons = sh / 4 + 48;
		quote(g, sw, below + 11 <= buttons ? below : sh - 24);
	}

	/** A loading screen: the picture held still and darker, so the progress over it reads; the quote near the foot. */
	public static void drawLoading(GuiGraphicsExtractor g, int sw, int sh) {
		if (!enabled() || !Menus.artBehindLoading()) {
			return;
		}
		loadingFrames++;
		double[] at = picture(g, sw, sh, 0, 0, 1.04);
		glowAndSparks(g, sw, sh, at);
		g.fill(0, 0, sw, sh, Ui.argb(96, 0x000000));
		g.fill(0, sh * 2 / 5, sw, sh * 2 / 3, Ui.argb(56, 0x000000));
		quote(g, sw, sh - 24);
	}

	/**
	 * The picture, scaled to cover the screen and zoomed in a little (breathing slowly), shifted by the given offset.
	 * Returns its drawn size and the part cut off at the left and top: {width, height, left, top}.
	 */
	private static double[] picture(GuiGraphicsExtractor g, int sw, int sh, double dx, double dy, double zoom) {
		double t = System.currentTimeMillis() / 1000.0;
		double imgAspect = (double) IMG_W / IMG_H;
		double baseW;
		double baseH;
		if ((double) sw / sh >= imgAspect) {
			baseW = sw;
			baseH = sw / imgAspect;
		} else {
			baseH = sh;
			baseW = sh * imgAspect;
		}
		double z = zoom + 0.015 * Math.sin(t * 0.22);
		double tw = baseW * z;
		double th = baseH * z;
		double u = Math.max(0, Math.min(tw - sw, (tw - sw) / 2 + dx));
		double v = Math.max(0, Math.min(th - sh, (th - sh) / 2 + dy));
		g.blit(RenderPipelines.GUI_TEXTURED, TEX, 0, 0, (float) u, (float) v, sw, sh, (int) Math.ceil(tw), (int) Math.ceil(th));
		return new double[] {tw, th, u, v};
	}

	private static void glowAndSparks(GuiGraphicsExtractor g, int sw, int sh, double[] at) {
		double t = System.currentTimeMillis() / 1000.0;
		boolean calm = ClientOptions.reduceFlashing();
		// a slow pulse in the portal the chain runs into
		int pcx = (int) (PORTAL_X * at[0] - at[2]);
		int pcy = (int) (PORTAL_Y * at[1] - at[3]);
		double pulse = calm ? 0.7 : 0.55 + 0.45 * Math.sin(t * 1.5);
		int radius = (int) (sh * 0.15);
		for (int k = 0; k < 6; k++) {
			int rr = radius * (6 - k) / 6;
			int alpha = (int) (13 * pulse);
			for (int dy = -rr; dy < rr; dy += 3) {
				int half = (int) Math.sqrt((double) rr * rr - (double) dy * dy);
				g.fill(pcx - half, pcy + dy, pcx + half, pcy + dy + 3, Ui.argb(alpha, 0xB060FF));
			}
		}
		// drifting sparks, ember and violet
		for (int i = 0; i < 70; i++) {
			double seed = Ui.hash(i * 7L + 3);
			double period = 7 + seed * 7;
			double phase = ((t / period) + seed * 13.0) % 1.0;
			int x = (int) (sw * Ui.hash(i * 13L + 5) + Math.sin(t * 0.7 + i) * 14);
			int y = (int) (sh * (1.05 - phase * 1.15));
			int size = (i % 5 == 0) ? 3 : 2;
			double twinkle = calm ? 0.8 : 0.6 + 0.4 * Math.sin(t * 4 + i * 1.7);
			int alpha = (int) (Math.sin(phase * Math.PI) * 230 * twinkle);
			g.fill(x, y, x + size, y + size, Ui.argb(alpha, i % 3 == 0 ? 0xC080FF : 0xFFA030));
		}
	}

	/** Rotating cryptic quotes ({@code menu.extremedimensions.quote.N}). Nobody knows who wrote them. */
	private static final int QUOTES = 14;
	private static final double QUOTE_SECONDS = 9.0;

	private static void quote(GuiGraphicsExtractor g, int sw, int y) {
		Font font = Minecraft.getInstance().font;
		double t = System.currentTimeMillis() / 1000.0;
		int idx = (int) (t / QUOTE_SECONDS) % QUOTES;
		double phase = (t % QUOTE_SECONDS) / QUOTE_SECONDS;
		// fade in and out, with a short flicker on appearing
		double alpha = Math.min(1.0, Math.min(phase / 0.12, (1 - phase) / 0.12));
		if (phase < 0.15 && !ClientOptions.reduceFlashing() && Ui.hash((long) (t * 20)) < 0.25) {
			alpha *= 0.4;
		}
		String q = "“" + Ui.tr("menu.extremedimensions.quote." + idx) + "”";
		g.centeredText(font, q, sw / 2 + 1, y + 1, Ui.argb((int) (160 * alpha), 0x000000));
		g.centeredText(font, q, sw / 2, y, Ui.argb((int) (230 * alpha), 0x9FE8F2));
	}
}
