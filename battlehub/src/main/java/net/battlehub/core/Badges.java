package net.battlehub.core;

/**
 * Badges: a sign after your name (chat, tab list, over your head). The effect text is "sign colour", the colour
 * "#RRGGBB" or a fade "#RRGGBB>#RRGGBB" over the signs (for example "★ #FFD700" or "✦✦✦ #FF4F8B>#4F8BFF").
 */
public final class Badges {
	private Badges() {}

	public record Badge(String sign, int from, int to) {
		/** The colour of the {@code i}-th of {@code n} signs. */
		public int color(int i, int n) {
			return n <= 1 ? from : CosmeticRules.mix(from, to, i / (double) (n - 1));
		}
	}

	/** The badge of an effect text, or null when it is not one (one to three signs, a colour). */
	public static Badge parse(String effect) {
		if (effect == null) return null;
		String t = effect.trim();
		int cut = t.lastIndexOf(' ');
		if (cut <= 0) return null;
		String sign = t.substring(0, cut).trim(), color = t.substring(cut + 1).trim();
		int n = sign.codePointCount(0, sign.length());
		if (n < 1 || n > 3) return null;
		int from, to;
		if (color.contains(">")) {
			String[] parts = color.split(">");
			if (parts.length != 2) return null;
			from = CosmeticRules.rgb(parts[0]);
			to = CosmeticRules.rgb(parts[1]);
		} else {
			from = to = CosmeticRules.rgb(color);
		}
		return from < 0 || to < 0 ? null : new Badge(sign, from, to);
	}
}
