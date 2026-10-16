package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A particle as a cosmetic's effect text describes it (no Minecraft classes, so it can be tested on its own).
 * Several can be joined with {@code +}.
 * <ul>
 * <li>{@code flame}, {@code minecraft:end_rod}: a plain particle</li>
 * <li>{@code dust:#FF0000} or {@code dust:#FF0000:1.5}: coloured dust (optional size)</li>
 * <li>{@code dust:#FF0000>#0000FF}: dust that fades from one colour to the other</li>
 * <li>{@code rainbow} or {@code rainbow:1.2}: dust that runs through every colour</li>
 * <li>{@code trail:#00FFFF}: a streak of light</li>
 * <li>{@code block:minecraft:gold_block}, {@code item:minecraft:diamond}: crumbs of a block or an item</li>
 * <li>{@code tint:entity_effect:#55FF55}: a tinted particle (entity_effect or tinted_leaves)</li>
 * </ul>
 */
public record ParticleSpec(Kind kind, String id, int color, int color2, float scale) {
	public enum Kind { SIMPLE, DUST, DUST_FADE, RAINBOW, TRAIL, BLOCK, ITEM, TINT }

	/** Particles that take a colour with {@code tint:}. */
	public static final List<String> TINTABLE = List.of("minecraft:entity_effect", "minecraft:tinted_leaves");

	/** All parts, or an empty list when any part is not understood. */
	public static List<ParticleSpec> parseAll(String text) {
		List<ParticleSpec> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		for (String part : text.split("\\+")) {
			ParticleSpec s = parse(part);
			if (s == null) {
				return new ArrayList<>();
			}
			out.add(s);
		}
		return out;
	}

	/** One part, or null when it is not understood. */
	public static ParticleSpec parse(String text) {
		if (text == null) {
			return null;
		}
		String t = text.trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty()) {
			return null;
		}
		if (t.equals("rainbow") || t.startsWith("rainbow:")) {
			Float scale = t.equals("rainbow") ? Float.valueOf(1f) : scale(t.substring(8));
			return scale == null ? null : new ParticleSpec(Kind.RAINBOW, "", 0, 0, scale);
		}
		if (t.startsWith("dust:")) {
			String[] parts = t.substring(5).split(":");
			if (parts.length > 2) {
				return null;
			}
			Float scale = parts.length == 2 ? scale(parts[1]) : Float.valueOf(1f);
			if (scale == null) {
				return null;
			}
			String[] colors = parts[0].split(">");
			int from = rgb(colors[0]);
			if (from < 0 || colors.length > 2) {
				return null;
			}
			if (colors.length == 2) {
				int to = rgb(colors[1]);
				return to < 0 ? null : new ParticleSpec(Kind.DUST_FADE, "", from, to, scale);
			}
			return new ParticleSpec(Kind.DUST, "", from, from, scale);
		}
		if (t.startsWith("trail:")) {
			int c = rgb(t.substring(6));
			return c < 0 ? null : new ParticleSpec(Kind.TRAIL, "", c, c, 1f);
		}
		if (t.startsWith("block:") || t.startsWith("item:")) {
			boolean block = t.startsWith("block:");
			String id = id(t.substring(block ? 6 : 5));
			return id == null ? null : new ParticleSpec(block ? Kind.BLOCK : Kind.ITEM, id, 0, 0, 1f);
		}
		if (t.startsWith("tint:")) {
			String rest = t.substring(5);
			int cut = rest.lastIndexOf(':');
			if (cut <= 0) {
				return null;
			}
			String id = id(rest.substring(0, cut));
			int c = rgb(rest.substring(cut + 1));
			return id == null || c < 0 || !TINTABLE.contains(id) ? null : new ParticleSpec(Kind.TINT, id, c, c, 1f);
		}
		String id = id(t);
		return id == null ? null : new ParticleSpec(Kind.SIMPLE, id, 0, 0, 1f);
	}

	/** A namespaced id ("flame" becomes "minecraft:flame"), or null when it has characters an id cannot have. */
	static String id(String s) {
		String t = s.trim();
		if (t.isEmpty() || !t.matches("[a-z0-9_.\\-]+(:[a-z0-9_./\\-]+)?")) {
			return null;
		}
		return t.contains(":") ? t : "minecraft:" + t;
	}

	private static Float scale(String s) {
		try {
			float f = Float.parseFloat(s.trim());
			return f > 0 && f <= 4 ? f : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** "#RRGGBB" or "RRGGBB" as a number, -1 when it is not one. */
	public static int rgb(String text) {
		return CosmeticRules.rgb(text);
	}

	/** The colour at this moment: fixed, or for a rainbow running through all hues every {@code period} ticks. */
	public int colorAt(long tick, int period) {
		if (kind != Kind.RAINBOW) {
			return color;
		}
		float hue = (float) Math.floorMod(tick, Math.max(1, period)) / Math.max(1, period);
		return hsb(hue, 0.85f, 1f);
	}

	/** Hue, saturation and brightness (0..1) as an RGB number. */
	public static int hsb(float hue, float saturation, float brightness) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		float c = brightness * saturation;
		float x = c * (1 - Math.abs(h % 2 - 1));
		float m = brightness - c;
		float r;
		float g;
		float b;
		switch ((int) h) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		int ri = Math.round((r + m) * 255);
		int gi = Math.round((g + m) * 255);
		int bi = Math.round((b + m) * 255);
		return (ri << 16) | (gi << 8) | bi;
	}
}
