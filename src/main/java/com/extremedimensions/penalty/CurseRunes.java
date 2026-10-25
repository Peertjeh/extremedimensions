package com.extremedimensions.penalty;

import java.text.Normalizer;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.PlayerData;

/**
 * Curses unknown to a player, on the harder difficulties ({@link Config#CURSE_RUNES}): until they have carried or sworn
 * a curse, its card and its place in the Curses screen show its name and its words in runes. The runes are a fixed
 * cipher of the words they hide, so a curse always wears the same ones. They are the Seam script, letters of the mod's
 * own drawn into the game's font at Private Use Area code points (tools/font/seam_script.py), so no real alphabet shows.
 */
public final class CurseRunes {
	private CurseRunes() {}

	/** The rune for a; b to z follow it, then 0 to 9 (36 in all). Must match FIRST in tools/font/seam_script.py. */
	public static final char FIRST = '\uEA00';
	public static final int COUNT = 36;

	/** Whether this player reads what the curse does: always, unless the world hides the curses they have never had. */
	public static boolean readable(PlayerData d, Penalty p) {
		return Config.CURSE_RUNES == 0 || d.knows(p);
	}

	/** The words in runes: every letter and digit becomes its rune; spaces and marks stay where they stood. */
	public static String runes(String text) {
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD);
		StringBuilder out = new StringBuilder(plain.length());
		for (int i = 0; i < plain.length(); i++) {
			char c = plain.charAt(i);
			char low = Character.toLowerCase(c);
			if (low >= 'a' && low <= 'z') {
				out.append((char) (FIRST + low - 'a'));
			} else if (c >= '0' && c <= '9') {
				out.append((char) (FIRST + 26 + c - '0'));
			} else if (Character.getType(c) != Character.NON_SPACING_MARK) {
				out.append(c);
			}
		}
		return out.toString();
	}
}
