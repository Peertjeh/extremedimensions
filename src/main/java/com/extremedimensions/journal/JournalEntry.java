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
	ENDING("ending"),
	MATRIARCH("matriarch"),
	FERRYMAN("ferryman"),
	LAST_WEAVER("last_weaver"),
	BARTER("barter"),
	RAID("raid"),
	FRAY("fray"),
	FRAYED_RAY("frayed_ray"),
	/** Not the Wanderer's: the Held's letter, read from a Loose Leaf (a Wanderer's Page never turns out to be it). */
	HELD("held", false),
	/** Hearing an echo of a Collapse out. */
	ECHOES("echoes"),
	/** In the other hand: what the frame of an Ancient City shows once strung with silk. */
	FRAME("frame", false),
	/** In the other hand: the First Knot, deep in the Fray. */
	FIRST_KNOT("first_knot", false),
	/** In the player's own hand, after the ending, at the loom: a mended world's page... */
	CONTINUED_MENDED("continued_mended", false, true),
	/** ...and a severed world's (a world has one ending, so a journal holds one of the two). */
	CONTINUED_SEVERED("continued_severed", false, true);

	public final String id;
	/** Written by the Wanderer (and so to be found on a Wanderer's Page). */
	public final boolean wanderer;
	/** Written in the player's own hand (the journal shows it in an ink of its own). */
	public final boolean own;

	JournalEntry(String id) {
		this(id, true, false);
	}

	JournalEntry(String id, boolean wanderer) {
		this(id, wanderer, false);
	}

	JournalEntry(String id, boolean wanderer, boolean own) {
		this.id = id;
		this.wanderer = wanderer;
		this.own = own;
	}

	/** Whether a journal can hold this page at all, in a world of this ending ("" before it: both are still to come). */
	public boolean possible(String ending) {
		return this == CONTINUED_MENDED ? !"severed".equals(ending) : this != CONTINUED_SEVERED || !"mended".equals(ending);
	}

	/** How many pages one journal can hold: all but one of the two in the player's own hand (a world has one ending). */
	public static int total() {
		return values().length - 1;
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
