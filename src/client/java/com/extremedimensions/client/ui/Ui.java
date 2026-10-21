package com.extremedimensions.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

/**
 * The mod's visual language: Minecraft's own. Every HUD element, screen and panel draws through these helpers, so
 * they all look like the game around them.
 *
 * <p>Panels are the game's tooltip box (near-black violet with its fading violet frame), text is the game's white
 * and grey with its drop shadow, colours are the sixteen of Minecraft's chat (aqua for the Tether, gold for the
 * Nether, light purple for the End and the Seam, red for danger), titles are plain like a vanilla screen's, and
 * meters are flat like a boss bar.
 */
public final class Ui {
	private Ui() {}

	// --- Palette (Minecraft's chat colours) ------------------------------------------------------
	/** The tooltip box's ground. */
	public static final int INK = 0x100010;
	public static final int INK_RAISED = 0x1C0C1C;
	public static final int SHADOW = 0x000000;
	public static final int TRACK = 0x2C2C2C;

	/** Aqua and dark aqua. */
	public static final int SOUL = 0x55FFFF;
	public static final int SOUL_DIM = 0x00AAAA;
	/** Gold and red. */
	public static final int EMBER = 0xFFAA00;
	public static final int EMBER_DEEP = 0xFF5555;
	/** Light purple. */
	public static final int VOID = 0xFF55FF;
	/** Dark red: the Extreme curses. */
	public static final int BLOOD = 0xAA0000;

	/** White for names and what must be read, grey for what is said beside it. */
	public static final int PARCHMENT = 0xFFFFFF;
	public static final int TEXT = 0xFFFFFF;
	public static final int MUTED = 0xAAAAAA;
	public static final int DANGER = 0xFF5555;
	public static final int WARN = 0xFFFF55;
	public static final int OK = 0x55FF55;

	/** The tooltip frame's violet, at its top and its foot (as argb, with the game's own alpha). */
	private static final int FRAME_TOP = 0x505000FF, FRAME_FOOT = 0x5028007F;

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

	/** A title as a vanilla screen has one: plain white words, centred, with the game's drop shadow. */
	public static void heading(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int accent, double alpha) {
		centered(g, font, s, cx, y, PARCHMENT, alpha);
	}

	// --- Shapes ----------------------------------------------------------------------------------

	/**
	 * The standard panel: the game's tooltip box, its ground reaching a pixel past its frame except at the corners,
	 * and its violet frame fading from top to foot. (Minecraft draws every box this way, whatever it holds, so the
	 * accent is not used for the frame.)
	 */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, double alpha) {
		int ground = argb(alpha * 0.94, INK);
		g.fill(x + 1, y, x + w - 1, y + h, ground);
		g.fill(x, y + 1, x + 1, y + h - 1, ground);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, ground);
		frame(g, x + 1, y + 1, w - 2, h - 2, alpha);
	}

	/** The tooltip box's frame: a pixel wide, violet at the top fading darker to the foot. */
	private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, double alpha) {
		g.fill(x, y, x + w, y + 1, fade(FRAME_TOP, alpha));
		g.fill(x, y + h - 1, x + w, y + h, fade(FRAME_FOOT, alpha));
		int steps = Math.max(1, Math.min(8, (h - 2) / 3));
		for (int i = 0; i < steps; i++) {
			int y0 = y + 1 + (h - 2) * i / steps, y1 = y + 1 + (h - 2) * (i + 1) / steps;
			int c = blend(FRAME_TOP, FRAME_FOOT, (i + 0.5) / steps);
			g.fill(x, y0, x + 1, y1, fade(c, alpha));
			g.fill(x + w - 1, y0, x + w, y1, fade(c, alpha));
		}
	}

	/** An argb colour with its alpha scaled. */
	private static int fade(int argb, double alpha) {
		return argb((int) Math.round(((argb >>> 24) & 255) * clamp01(alpha)), argb & 0xFFFFFF);
	}

	/** Two argb colours mixed, alpha too. */
	private static int blend(int a, int b, double t) {
		int alpha = (int) Math.round(((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
		return argb(alpha, mix(a & 0xFFFFFF, b & 0xFFFFFF, t));
	}

	/** A small panel for the HUD: the same tooltip box. */
	public static void chip(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, double alpha) {
		panel(g, x, y, w, h, accent, alpha);
	}

	/** A rule between parts of a panel, as the game draws one under a screen's header: light over dark. */
	public static void divider(GuiGraphicsExtractor g, int x, int y, int w, int accent, double alpha) {
		g.fill(x, y, x + w, y + 1, argb(alpha * 0.22, 0xFFFFFF));
		g.fill(x, y + 1, x + w, y + 2, argb(alpha * 0.5, SHADOW));
	}

	/**
	 * A meter as flat as a boss bar: the bar's colour darkened for what is empty, its colour for what is full, a
	 * lighter pixel along its top.
	 */
	public static void meter(GuiGraphicsExtractor g, int x, int y, int w, int h, double frac, int color, double alpha, boolean shine) {
		frac = clamp01(frac);
		g.fill(x, y, x + w, y + h, argb(alpha, mix(color, SHADOW, 0.7)));
		int fw = (int) Math.round(w * frac);
		if (fw > 0) {
			g.fill(x, y, x + fw, y + h, argb(alpha, color));
			g.fill(x, y, x + fw, y + 1, argb(alpha * 0.7, mix(color, 0xFFFFFF, 0.45)));
		}
		if (h > 2) {
			g.fill(x, y + h - 1, x + w, y + h, argb(alpha * 0.4, SHADOW));
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
