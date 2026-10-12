package com.extremedimensions.journal;

/**
 * Pages of the Tether Journal, written by a nameless Wanderer long ago. Each page unlocks through something the
 * player does. Titles and text live in the language files under {@code journal.extremedimensions.<id>.*}.
 */
public enum JournalEntry {
	TETHERED("tethered"),
	ANCHOR("anchor"),
	CORD("cord"),
	WOUND("wound"),
	EMBER_DEEP("ember_deep"),
	PALE_VOID("pale_void"),
	STARVED("starved"),
	ENDED("ended"),
	SCARS("scars"),
	FRAGMENT("fragment"),
	MENDING("mending"),
	WEAVERS("weavers"),
	UNANCHORED("unanchored"),
	HABIT("habit"),
	SILENCE("silence"),
	TRIBE("tribe"),
	SOUL_LIGHT("soul_light"),
	/** Only found on Wanderer's Pages in the world. */
	RUINED_GATE("ruined_gate"),
	FIRST_WOUND("first_wound"),
	HOLLOWS("hollows"),
	LITTLE_THIEVES("little_thieves"),
	KEEPER("keeper"),
	SEAM("seam"),
	LOOM("loom"),
	THINNED("thinned"),
	WOVEN("woven"),
	SEAL("seal"),
	STORMS("storms"),
	HEART("heart"),
	WANDERER("wanderer"),
	ENDING("ending");

	public final String id;

	JournalEntry(String id) {
		this.id = id;
	}

	public String titleKey() {
		return "journal.extremedimensions." + id + ".title";
	}

	public String textKey() {
		return "journal.extremedimensions." + id + ".text";
	}

	public static JournalEntry byId(String id) {
		for (JournalEntry e : values()) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}
}
