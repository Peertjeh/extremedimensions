package com.extremedimensions.network;

/** Client animations triggered by the server (see {@link ModPackets.FxPayload}). */
public final class Fx {
	private Fx() {}

	public static final int DEPOSIT_NETHER = 1;
	public static final int DEPOSIT_END = 2;
	public static final int START_NETHER = 3;
	public static final int START_END = 4;
	public static final int SAFE = 5;
	public static final int COLLAPSE = 6;
	public static final int BOUNCE = 7;
	public static final int PENALTY = 8;
	public static final int ENDED = 9;
	public static final int TIE = 10;
	public static final int TAKEN = 11;
	/** The seal broke (the Ender Dragon died): a white flash and the world cracking. */
	public static final int SEAL = 12;
	/** A Wound Storm begins nearby. */
	public static final int STORM = 13;
	/** Arrived in the Seam (the first few visits): what to do there, on the screen. Value: seconds the visit lasts. */
	public static final int SEAM_BRIEF = 14;
	/** Down in the Fray (the first few descents): what it is. Value: ten times how much faster time runs out there. */
	public static final int FRAY_BRIEF = 15;
	/** A page of the Tether Journal was written: a toast, as the game shows such news. Value: the page's ordinal. */
	public static final int JOURNAL_PAGE = 16;
	/** A creature was written into the bestiary: a toast. Value: the creature's ordinal. */
	public static final int BESTIARY = 17;
	/** A note in another hand turned up in the journal's margin: a toast. Value: the note's ordinal. */
	public static final int JOURNAL_NOTE = 18;
	/** The island the player stands on frays (value: seconds it lasts). */
	public static final int FRAYED_SEAM = 19;
}
