package net.battlehub.core;

/** Where buttons go in a chest menu (9 columns): rows of at most {@code perRow}, each row centred. */
public final class MenuSlots {
	private MenuSlots() {}

	/** Slots for {@code n} buttons starting on row {@code firstRow}. Up to four in a row get a gap between them. */
	public static int[] centered(int n, int firstRow, int perRow) {
		int per = Math.max(1, Math.min(9, perRow));
		int[] out = new int[Math.max(0, n)];
		int i = 0;
		int row = firstRow;
		while (i < n) {
			int k = Math.min(per, n - i);
			int[] cols = columns(k);
			for (int c : cols) {
				out[i++] = row * 9 + c;
			}
			row++;
		}
		return out;
	}

	static int[] columns(int k) {
		int[] cols = new int[k];
		if (k <= 4) {
			int start = 4 - (k - 1);
			for (int j = 0; j < k; j++) cols[j] = start + 2 * j;
		} else if (k == 6) {
			int[] six = {1, 2, 3, 5, 6, 7};
			System.arraycopy(six, 0, cols, 0, 6);
		} else {
			int start = (9 - k) / 2;
			for (int j = 0; j < k; j++) cols[j] = start + j;
		}
		return cols;
	}
}
