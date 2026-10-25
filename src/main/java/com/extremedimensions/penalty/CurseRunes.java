package com.extremedimensions.penalty;

import java.text.Normalizer;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.PlayerData;

/**
 * Curses unknown to a player, on the harder difficulties ({@link Config#CURSE_RUNES}): until they have carried or sworn
 * a curse, its card and its place in the Curses screen show its name and its words in runes. The runes are a fixed
 * cipher of the words they hide, so a curse always wears the same ones.
 */
public final class CurseRunes {
	private CurseRunes() {}

	/** One rune for each letter a to z, and for each digit. */
	private static final String LETTERS = "ᚨᛒᚲᛞᛖᚠᚷᚺᛁᛃᚴᛚᛗᚾᛟᛈᛩᚱᛋᛏᚢᚡᚹᛪᛇᛉ";
	private static final String DIGITS = "ᛥᚬᚭᚮᚯᚰᚵᚶᚸᚻ";

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
				out.append(LETTERS.charAt(low - 'a'));
			} else if (c >= '0' && c <= '9') {
				out.append(DIGITS.charAt(c - '0'));
			} else if (Character.getType(c) != Character.NON_SPACING_MARK) {
				out.append(c);
			}
		}
		return out.toString();
	}
}
