package net.battlehub.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small pixel icons for the optional resource pack (8 by 8). Each row is 8 characters: '.' is empty, a letter a
 * colour from {@link #COLORS}. The pack turns them into a font, one private-use character per icon.
 */
public final class PixelIcons {
	private PixelIcons() {}

	public static final int SIZE = 8;
	/** The first character of the icons; the rest follow in the order of {@link #ICONS}. */
	public static final char FIRST = '';

	public static final Map<Character, Integer> COLORS = Map.ofEntries(
			Map.entry('k', 0xFF1B1B1B), Map.entry('w', 0xFFFFFFFF), Map.entry('g', 0xFFAAAAAA), Map.entry('d', 0xFF555555),
			Map.entry('y', 0xFFFFD83D), Map.entry('o', 0xFFE8A317), Map.entry('r', 0xFFE02929), Map.entry('m', 0xFF8B1A1A),
			Map.entry('c', 0xFF5BE0FF), Map.entry('b', 0xFF2F6FE0), Map.entry('l', 0xFF6BE04A), Map.entry('n', 0xFF7A4E2A),
			Map.entry('p', 0xFFC264FF));

	public static final Map<String, String[]> ICONS = new LinkedHashMap<>();

	static {
		ICONS.put("coin", new String[] {
				"..oooo..", ".oyyyyo.", "oyywwyyo", "oyywyyyo", "oyywyyyo", "oyyyyyyo", ".oyyyyo.", "..oooo.."});
		ICONS.put("sword", new String[] {
				"......cw", ".....cwc", "....cwc.", "n..cwc..", ".ncwc...", "..nc....", ".n.nn...", "n......."});
		ICONS.put("bow", new String[] {
				"..nnn..w", ".n...ww.", "n...w.g.", "n..w..g.", "n.w...g.", "nw....g.", ".n...g..", "..nnn..."});
		ICONS.put("skull", new String[] {
				"..wwww..", ".wwwwww.", "wwwwwwww", "wkkwwkkw", "wkkwwkkw", "wwwkkwww", ".wwwwww.", ".w.ww.w."});
		ICONS.put("heart", new String[] {
				".rr..rr.", "rwrrrrrr", "rwrrrrrr", "rrrrrrrr", ".rrrrrr.", "..rrrr..", "...rr...", "........"});
		ICONS.put("star", new String[] {
				"...yy...", "...yy...", "yyyyyyyy", ".yyyyyy.", "..yyyy..", ".yyyyyy.", ".yy..yy.", "y......y"});
		ICONS.put("trophy", new String[] {
				"yyyyyyyy", "yywwyyyy", ".yyyyyy.", "..yyyy..", "...yy...", "...yy...", "..oooo..", ".oooooo."});
		ICONS.put("crown", new String[] {
				"........", "y..y..y.", "yy.yy.yy", "yyyyyyyy", "yryyyryy", "yyyyyyyy", "oooooooo", "........"});
		ICONS.put("fire", new String[] {
				"...r....", "..rr..r.", "..rorrr.", ".rooorr.", ".royyor.", "rroyyorr", ".rooyor.", "..rrrr.."});
		ICONS.put("clock", new String[] {
				"..gggg..", ".gwwwwg.", "gwwkwwwg", "gwwkwwwg", "gwwkkkwg", "gwwwwwwg", ".gwwwwg.", "..gggg.."});
		ICONS.put("shield", new String[] {
				"bbbbbbbb", "bwwbbwwb", "bwwbbwwb", "bbbbbbbb", "bwwbbwwb", ".bwbbwb.", "..bbbb..", "...bb..."});
		ICONS.put("bolt", new String[] {
				"....yy..", "...yy...", "..yy....", ".yyyyyy.", "....yy..", "...yy...", "..yy....", ".y......"});
	}

	/** The character of an icon, or 0 when there is no such icon. */
	public static char glyph(String name) {
		int i = 0;
		for (String n : ICONS.keySet()) {
			if (n.equals(name)) return (char) (FIRST + i);
			i++;
		}
		return 0;
	}

	/** The colour (ARGB) of one pixel; 0 is transparent. */
	public static int pixel(String[] rows, int x, int y) {
		char ch = rows[y].charAt(x);
		return ch == '.' ? 0 : COLORS.getOrDefault(ch, 0);
	}
}
