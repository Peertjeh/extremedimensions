package com.extremedimensions.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

/**
 * The mod's visual language. Every HUD element, screen and panel draws through these helpers, so colours,
 * borders and spacing stay identical everywhere.
 *
 * <p>Style: dark soot-black panels with a hairline accent border and small corner rivets, parchment text, and
 * one accent colour per context (soul teal for the Tether, ember for the Nether, violet for the End).
 */
public final class Ui {
	private Ui() {}

	// --- Palette ---------------------------------------------------------------------------------
	public static final int INK = 0x0C0A10;
	public static final int INK_RAISED = 0x16121C;
	public static final int SHADOW = 0x000000;
	public static final int TRACK = 0x231D2A;

	public static final int SOUL = 0x6FE3D0;
	public static final int SOUL_DIM = 0x2E7F78;
	public static final int EMBER = 0xFF7A1A;
	public static final int EMBER_DEEP = 0xC0462E;
	public static final int VOID = 0xB66BFF;
	/** The Extreme curses. */
	public static final int BLOOD = 0xD0203A;

	public static final int PARCHMENT = 0xEADFC6;
	public static final int TEXT = 0xCFC3AE;
	public static final int MUTED = 0x8A7F70;
	public static final int DANGER = 0xFF5A4E;
	public static final int WARN = 0xFFB347;
	public static final int OK = 0x8FDB8A;

	// --- Accessibility ---------------------------------------------------------------------------

	/** True when the player asked for fewer flashes: strobes, shakes and glitches are left out, pulses hold still. */
	public static boolean calm() {
		return com.extremedimensions.client.options.ClientOptions.reduceFlashing();
	}

	/** A pulse between 0 and 1 with the given period in milliseconds; holds at its middle when {@link #calm()}. */
	public static double pulse(long now, double periodMs, double phase) {
		return calm() ? 0.5 : 0.5 + 0.5 * Math.sin(now / periodMs * Math.PI * 2 + phase);
	}

	/**
	 * The colour of a curse set. With the colour-blind option the four sets take colours that stay apart for every
	 * kind of colour blindness (Okabe-Ito: orange, sky blue, reddish purple, vermillion); they also always differ in
	 * their sigils.
	 */
	public static int setColour(com.extremedimensions.penalty.Penalty.Group group) {
		boolean cb = com.extremedimensions.client.options.ClientOptions.colourBlind();
		return switch (group) {
			case OVERWORLD -> cb ? 0x56B4E9 : SOUL;
			case EVERYWHERE -> cb ? 0xCC79A7 : VOID;
			case EXTREME -> cb ? 0xD55E00 : BLOOD;
			default -> cb ? 0xE69F00 : EMBER_DEEP;
		};
	}

	// --- Colour math -----------------------------------------------------------------------------

	public static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	public static int argb(double alpha01, int rgb) {
		return argb((int) Math.round(255 * alpha01), rgb);
	}

	public static int mix(int a, int b, double t) {
		t = clamp01(t);
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/** Cubic ease-out over 0..1. */
	public static double ease(double t) {
		double c = 1 - clamp01(t);
		return 1 - c * c * c;
	}

	public static double hash(long n) {
		long x = n * 0x9E3779B97F4A7C15L;
		x ^= x >>> 32;
		x *= 0xD6E8FEB86659FD93L;
		x ^= x >>> 32;
		return (x & 0xFFFFFF) / (double) 0x1000000;
	}

	// --- Text ------------------------------------------------------------------------------------

	public static String tr(String key, Object... args) {
		return I18n.get(key, args);
	}

	public static String time(double seconds) {
		int s = (int) Math.ceil(Math.max(0, seconds));
		return String.format("%d:%02d", s / 60, s % 60);
	}

	public static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	/** Word wrap on pixel width. */
	public static List<String> wrap(Font font, String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (font.width(candidate) > width && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	/** Cuts text to a pixel width, ending in an ellipsis when it had to cut. */
	public static String ellipsize(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && font.width(cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.stripTrailing() + "...";
	}

	/** Text with the vanilla drop shadow and an alpha. */
	public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int rgb, double alpha) {
		g.text(font, s, x, y, argb(alpha, rgb));
	}

	/** Text without drop shadow, for dark ink on parchment. */
	public static void ink(GuiGraphicsExtractor g, Font font, String s, int x, int y, int rgb, double alpha) {
		g.text(font, s, x, y, argb(alpha, rgb), false);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int rgb, double alpha) {
		text(g, font, s, cx - font.width(s) / 2, y, rgb, alpha);
	}

	/** A spaced, upper-case heading flanked by thin accent rules: "—— TITLE ——". */
	public static void heading(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int accent, double alpha) {
		String t = s.toUpperCase();
		int w = font.width(t);
		centered(g, font, t, cx, y, PARCHMENT, alpha);
		int ry = y + font.lineHeight / 2 - 1;
		int len = 18;
		g.fill(cx - w / 2 - 6 - len, ry, cx - w / 2 - 6, ry + 1, argb(alpha * 0.8, accent));
		g.fill(cx + w / 2 + 6, ry, cx + w / 2 + 6 + len, ry + 1, argb(alpha * 0.8, accent));
		g.fill(cx - w / 2 - 6 - len - 3, ry - 1, cx - w / 2 - 6 - len, ry + 2, argb(alpha, accent));
		g.fill(cx + w / 2 + 6 + len, ry - 1, cx + w / 2 + 6 + len + 3, ry + 2, argb(alpha, accent));
	}

	// --- Shapes ----------------------------------------------------------------------------------

	/**
	 * The standard panel: soft shadow, black outline, accent hairline, raised inner face and corner rivets.
	 */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, double alpha) {
		g.fill(x + 2, y + 2, x + w + 2, y + h + 2, argb(alpha * 0.35, SHADOW));
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(alpha, SHADOW));
		g.fill(x, y, x + w, y + h, argb(alpha * 0.85, accent));
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(alpha * 0.96, INK));
		g.fill(x + 2, y + 2, x + w - 2, y + h / 2, argb(alpha * 0.5, INK_RAISED));
		g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(alpha * 0.25, 0xFFFFFF));
		rivet(g, x, y, accent, alpha);
		rivet(g, x + w - 3, y, accent, alpha);
		rivet(g, x, y + h - 3, accent, alpha);
		rivet(g, x + w - 3, y + h - 3, accent, alpha);
	}

	private static void rivet(GuiGraphicsExtractor g, int x, int y, int accent, double alpha) {
		g.fill(x, y, x + 3, y + 3, argb(alpha, mix(accent, 0xFFFFFF, 0.35)));
		g.fill(x + 1, y + 1, x + 2, y + 2, argb(alpha, SHADOW));
	}

	/** A small panel for HUD chips: no shadow block, lighter border. */
	public static void chip(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, double alpha) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(alpha * 0.8, SHADOW));
		g.fill(x, y, x + w, y + h, argb(alpha * 0.75, INK));
		g.fill(x, y, x + 1, y + h, argb(alpha, accent));
	}

	public static void divider(GuiGraphicsExtractor g, int x, int y, int w, int accent, double alpha) {
		g.fill(x, y, x + w, y + 1, argb(alpha * 0.35, accent));
		int cx = x + w / 2;
		g.fill(cx - 2, y - 1, cx + 2, y + 2, argb(alpha * 0.9, accent));
	}

	/**
	 * A meter with a dark track, tick marks every 10 %, a fill with a top highlight, and an optional sweep shine.
	 */
	public static void meter(GuiGraphicsExtractor g, int x, int y, int w, int h, double frac, int color, double alpha, boolean shine) {
		frac = clamp01(frac);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(alpha, SHADOW));
		g.fill(x, y, x + w, y + h, argb(alpha, TRACK));
		int fw = (int) Math.round(w * frac);
		if (fw > 0) {
			g.fill(x, y, x + fw, y + h, argb(alpha, color));
			g.fill(x, y, x + fw, y + 1, argb(alpha * 0.6, mix(color, 0xFFFFFF, 0.6)));
			g.fill(x, y + h - 1, x + fw, y + h, argb(alpha * 0.5, mix(color, SHADOW, 0.5)));
			if (shine) {
				int sweep = (int) ((System.currentTimeMillis() / 12) % (w + 40)) - 20;
				int s0 = Math.max(x, x + sweep);
				int s1 = Math.min(x + fw, x + sweep + 12);
				if (s1 > s0) {
					g.fill(s0, y, s1, y + h, argb(alpha * 0.28, 0xFFFFFF));
				}
			}
		}
		for (int i = 1; i < 10; i++) {
			int tx = x + w * i / 10;
			g.fill(tx, y + h - 2, tx + 1, y + h, argb(alpha * 0.45, SHADOW));
		}
	}

	/** Full-screen vignette tinted with {@code rgb}. */
	public static void vignette(GuiGraphicsExtractor g, int w, int h, int rgb, int strength, int steps) {
		for (int k = 1; k <= steps; k++) {
			int tx = w * k / (steps * 5);
			int ty = h * k / (steps * 3 + 2);
			int col = argb(strength, rgb);
			g.fill(0, 0, w, ty, col);
			g.fill(0, h - ty, w, h, col);
			g.fill(0, ty, tx, h - ty, col);
			g.fill(w - tx, ty, w, h - ty, col);
		}
	}

	/**
	 * A 7x7 rune (left half mirrored) derived from a string, two pixels per cell: every id gets its own sigil.
	 */
	public static void rune(GuiGraphicsExtractor g, String seed, int x, int y, int rgb, double alpha) {
		long h = seed.hashCode() * 0x9E3779B97F4A7C15L;
		for (int row = 0; row < 7; row++) {
			for (int col = 0; col < 4; col++) {
				boolean on = ((h >>> (row * 4 + col)) & 1) == 1 || (col == 3 && (row == 0 || row == 6));
				if (on) {
					int px = x + col * 2;
					int mx = x + (6 - col) * 2;
					int py = y + row * 2;
					g.fill(px, py, px + 2, py + 2, argb(alpha, rgb));
					g.fill(mx, py, mx + 2, py + 2, argb(alpha, rgb));
				}
			}
		}
	}

	/** A rune inside its framed socket (18x18). */
	public static void runeSocket(GuiGraphicsExtractor g, String seed, int x, int y, int accent, double alpha, double glow) {
		g.fill(x, y, x + 18, y + 18, argb(alpha, SHADOW));
		g.fill(x + 1, y + 1, x + 17, y + 17, argb(alpha, mix(INK, accent, 0.18 + 0.12 * glow)));
		rune(g, seed, x + 2, y + 2, mix(accent, 0xFFFFFF, 0.25 + 0.25 * glow), alpha);
	}

	/** A tooltip box in the house style. */
	public static void tooltip(GuiGraphicsExtractor g, Font font, List<String> lines, List<Integer> colors, int mx, int my, int sw, int sh, int accent) {
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, font.width(l));
		}
		int h = lines.size() * 10 + 4;
		int x = Math.min(mx + 12, sw - w - 8);
		int y = Math.max(4, Math.min(my - 4, sh - h - 6));
		panel(g, x - 5, y - 5, w + 10, h + 6, accent, 1.0);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x, y + i * 10, argb(255, colors.get(i)));
		}
	}
}
