package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses chat text with {@code &} colour codes ({@code &a}, {@code &l}, {@code &r}, {@code &#ff8800}) and
 * {@code {0}}-style placeholders into styled segments. No Minecraft types: the lang layer turns segments into
 * components.
 */
public final class LegacyText {
	private LegacyText() {}

	/** One run of text with one style. {@code color} is RGB or -1 for "inherit"; {@code arg} >= 0 marks a placeholder. */
	public record Segment(String text, int color, boolean bold, boolean italic, boolean underlined,
			boolean strikethrough, boolean obfuscated, int arg) {
		public boolean isArg() {
			return arg >= 0;
		}
	}

	/** RGB values of the 16 legacy colours, indexed by hex digit. */
	public static final int[] COLORS = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	public static List<Segment> parse(String input) {
		List<Segment> out = new ArrayList<>();
		if (input == null || input.isEmpty()) {
			return out;
		}
		StringBuilder buf = new StringBuilder();
		int color = -1;
		boolean bold = false, italic = false, underlined = false, strike = false, obf = false;
		int i = 0;
		while (i < input.length()) {
			char c = input.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < input.length()) {
				char code = Character.toLowerCase(input.charAt(i + 1));
				// &#rrggbb
				if (code == '#' && i + 8 <= input.length() && isHex(input, i + 2, 6)) {
					flush(out, buf, color, bold, italic, underlined, strike, obf);
					color = Integer.parseInt(input.substring(i + 2, i + 8), 16);
					bold = italic = underlined = strike = obf = false;
					i += 8;
					continue;
				}
				int hex = Character.digit(code, 16);
				if (hex >= 0) {
					flush(out, buf, color, bold, italic, underlined, strike, obf);
					color = COLORS[hex];
					bold = italic = underlined = strike = obf = false;
					i += 2;
					continue;
				}
				boolean known = true;
				switch (code) {
					case 'l' -> { flush(out, buf, color, bold, italic, underlined, strike, obf); bold = true; }
					case 'o' -> { flush(out, buf, color, bold, italic, underlined, strike, obf); italic = true; }
					case 'n' -> { flush(out, buf, color, bold, italic, underlined, strike, obf); underlined = true; }
					case 'm' -> { flush(out, buf, color, bold, italic, underlined, strike, obf); strike = true; }
					case 'k' -> { flush(out, buf, color, bold, italic, underlined, strike, obf); obf = true; }
					case 'r' -> {
						flush(out, buf, color, bold, italic, underlined, strike, obf);
						color = -1;
						bold = italic = underlined = strike = obf = false;
					}
					default -> known = false;
				}
				if (known) {
					i += 2;
					continue;
				}
			}
			if (c == '{') {
				int close = input.indexOf('}', i);
				if (close > i + 1 && close - i <= 4 && isDigits(input, i + 1, close)) {
					flush(out, buf, color, bold, italic, underlined, strike, obf);
					int arg = Integer.parseInt(input.substring(i + 1, close));
					out.add(new Segment("", color, bold, italic, underlined, strike, obf, arg));
					i = close + 1;
					continue;
				}
			}
			buf.append(c);
			i++;
		}
		flush(out, buf, color, bold, italic, underlined, strike, obf);
		return out;
	}

	/** Removes colour codes, keeping placeholders as written. */
	public static String strip(String input) {
		StringBuilder sb = new StringBuilder();
		for (Segment s : parse(input)) {
			sb.append(s.isArg() ? "{" + s.arg() + "}" : s.text());
		}
		return sb.toString();
	}

	private static void flush(List<Segment> out, StringBuilder buf, int color, boolean bold, boolean italic,
			boolean underlined, boolean strike, boolean obf) {
		if (buf.length() > 0) {
			out.add(new Segment(buf.toString(), color, bold, italic, underlined, strike, obf, -1));
			buf.setLength(0);
		}
	}

	private static boolean isHex(String s, int from, int len) {
		for (int i = from; i < from + len; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean isDigits(String s, int from, int to) {
		for (int i = from; i < to; i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}
}
