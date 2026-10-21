package com.extremedimensions.journal;

/**
 * Notes in another hand in the margins of the Tether Journal. Each belongs to one of the Wanderer's pages and shows
 * under it once found (the Wanderer's text itself is never changed). Texts live in the language files under
 * {@code journal.extremedimensions.note.<id>}.
 */
public enum MarginNote {
	/** Beside XVIII, The Ruined Gates: found at a warm chain. */
	RUINED_GATE("ruined_gate", JournalEntry.RUINED_GATE),
	/** Beside XXX, The Wanderer: found on the Loose Leaf. */
	WANDERER("wanderer", JournalEntry.WANDERER),
	/** Beside XIX, The First Wound: found on the Loose Leaf. */
	FIRST_WOUND("first_wound", JournalEntry.FIRST_WOUND),
	/** Beside XIII, The Unanchored: found where the Endermen finished a ring unwatched. */
	UNANCHORED("unanchored", JournalEntry.UNANCHORED);

	public final String id;
	public final JournalEntry page;

	MarginNote(String id, JournalEntry page) {
		this.id = id;
		this.page = page;
	}

	public String textKey() {
		return "journal.extremedimensions.note." + id;
	}

	public static MarginNote byId(String id) {
		for (MarginNote n : values()) {
			if (n.id.equals(id)) {
				return n;
			}
		}
		return null;
	}
}
