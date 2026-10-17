package com.extremedimensions.client.title;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The animated title screen: slow zoom, mouse parallax, a pulsing portal glow and drifting sparks.
 * The logo itself is replaced through resources (assets/minecraft/textures/gui/title).
 */
public final class TitleArt {
	private TitleArt() {}

	private static final Identifier TEX = ExtremeDimensions.id("textures/gui/title.png");
	private static final int IMG_W = 1672;
	private static final int IMG_H = 941;
	/** Where the portal sits in the image (fraction of width and height). */
	private static final double PORTAL_X = 0.72;
	private static final double PORTAL_Y = 0.43;

	public static void draw(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
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
		double z = 1.07 + 0.015 * Math.sin(t * 0.22);
		double tw = baseW * z;
		double th = baseH * z;
		double u = (tw - sw) / 2 + (0.5 - (double) mx / sw) * 26;
		double v = (th - sh) / 2 + (0.5 - (double) my / sh) * 14;
		u = Math.max(0, Math.min(tw - sw, u));
		v = Math.max(0, Math.min(th - sh, v));
		g.blit(RenderPipelines.GUI_TEXTURED, TEX, 0, 0, (float) u, (float) v, sw, sh, (int) Math.ceil(tw), (int) Math.ceil(th));

		// Pulsing glow around the portal
		int pcx = (int) (PORTAL_X * tw - u);
		int pcy = (int) (PORTAL_Y * th - v);
		double pulse = 0.55 + 0.45 * Math.sin(t * 1.5);
		int radius = (int) (sh * 0.20);
		for (int k = 0; k < 6; k++) {
			int rr = radius * (6 - k) / 6;
			int alpha = (int) (14 * pulse);
			for (int dy = -rr; dy < rr; dy += 3) {
				int half = (int) Math.sqrt((double) rr * rr - (double) dy * dy);
				g.fill(pcx - half, pcy + dy, pcx + half, pcy + dy + 3, Ui.argb(alpha, 0xB060FF));
			}
		}

		// Drifting sparks (ember and violet)
		for (int i = 0; i < 70; i++) {
			double seed = Ui.hash(i * 7L + 3);
			double period = 7 + seed * 7;
			double phase = ((t / period) + seed * 13.0) % 1.0;
			int x = (int) (sw * Ui.hash(i * 13L + 5) + Math.sin(t * 0.7 + i) * 14);
			int y = (int) (sh * (1.05 - phase * 1.15));
			int size = (i % 5 == 0) ? 3 : 2;
			double tw2 = 0.6 + 0.4 * Math.sin(t * 4 + i * 1.7);
			int alpha = (int) (Math.sin(phase * Math.PI) * 230 * tw2);
			g.fill(x, y, x + size, y + size, Ui.argb(alpha, i % 3 == 0 ? 0xC080FF : 0xFFA030));
		}

		// Light darkening so the buttons stay legible
		g.fill(0, 0, sw, sh, Ui.argb(56, 0x000000));
		g.fill(0, sh - sh / 3, sw, sh, Ui.argb(48, 0x000000));

		drawQuote(g, sw, sh);
	}

	/** Rotating cryptic quotes below the logo ({@code menu.extremedimensions.quote.N}). Nobody knows who wrote them. */
	private static final int QUOTES = 14;
	private static final double QUOTE_SECONDS = 9.0;

	private static void drawQuote(GuiGraphicsExtractor g, int sw, int sh) {
		net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
		double t = System.currentTimeMillis() / 1000.0;
		int idx = (int) (t / QUOTE_SECONDS) % QUOTES;
		double phase = (t % QUOTE_SECONDS) / QUOTE_SECONDS;
		// fade in and out, with a short flicker on appearing
		double alpha = Math.min(1.0, Math.min(phase / 0.12, (1 - phase) / 0.12));
		if (phase < 0.15 && Ui.hash((long) (t * 20)) < 0.25) {
			alpha *= 0.4;
		}
		String q = "\u201c" + Ui.tr("menu.extremedimensions.quote." + idx) + "\u201d";
		int y = Math.min(sh / 4 + 34, 96);
		g.centeredText(font, q, sw / 2 + 1, y + 1, Ui.argb((int) (160 * alpha), 0x000000));
		g.centeredText(font, q, sw / 2, y, Ui.argb((int) (230 * alpha), 0x9FE8F2));
	}
}
