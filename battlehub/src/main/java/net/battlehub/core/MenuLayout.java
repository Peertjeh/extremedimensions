package net.battlehub.core;

/**
 * Where things sit in every BattleHub menu, so they are always in the same place: the top row says where you
 * are (the menu's icon in the middle, your coins on the right), the bottom row goes places (back on the left, the
 * pages either side of the middle, close in the middle). Slots count from 0, nine per row.
 */
public final class MenuLayout {
	private MenuLayout() {}

	/** The menu's own icon (top middle) and your coins and level (top right). */
	public static final int TITLE = 4, WALLET = 8;

	public static int back(int rows) {
		return (rows - 1) * 9;
	}

	public static int previous(int rows) {
		return (rows - 1) * 9 + 3;
	}

	public static int close(int rows) {
		return (rows - 1) * 9 + 4;
	}

	public static int next(int rows) {
		return (rows - 1) * 9 + 5;
	}

	/** Something extra in the bottom row (a "clear" or "leave" button): bottom right. */
	public static int extra(int rows) {
		return (rows - 1) * 9 + 8;
	}

	/** Whether {@code slot} is in the top or bottom row (the frame). */
	public static boolean frame(int slot, int rows) {
		return slot < 9 || slot >= (rows - 1) * 9;
	}

	/** The slots between the top and bottom rows, left to right, top to bottom, leaving the outer columns free. */
	public static int[] body(int rows) {
		int n = Math.max(0, (rows - 2) * 7);
		int[] out = new int[n];
		int i = 0;
		for (int r = 1; r < rows - 1; r++) {
			for (int c = 1; c < 8; c++) out[i++] = r * 9 + c;
		}
		return out;
	}
}
