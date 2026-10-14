package com.extremedimensions.progression;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the mod's advancements. The advancement files use the {@code minecraft:impossible} trigger, so they are
 * only ever granted from code, at the exact moment the milestone happens.
 */
public final class Advancements {
	private Advancements() {}

	public static final String ROOT = "root";
	public static final String ANCHOR_CHARGED = "anchor_charged";
	public static final String ANCHOR_FULL = "anchor_full";
	public static final String BOUND = "bound";
	public static final String FIRST_EXPEDITION = "first_expedition";
	public static final String VETERAN = "veteran";
	public static final String LONG_TETHER = "long_tether";
	public static final String COLLAPSE = "collapse";
	public static final String ENDED = "ended";
	public static final String MARKED = "marked";
	public static final String MENDED = "mended";
	public static final String JOURNAL_COMPLETE = "journal_complete";
	public static final String WATCHED = "watched";
	public static final String ROBBED = "robbed";
	public static final String HUNTED = "hunted";
	public static final String HOARD_FOUND = "hoard_found";
	public static final String KEEPER_SLAIN = "keeper_slain";
	public static final String HOLLOW_BOUND = "hollow_bound";
	public static final String SEAM_ENTERED = "seam_entered";
	public static final String SEAM_RETURNED = "seam_returned";
	public static final String THINNED_FOUND = "thinned_found";
	public static final String SEAL_BROKEN = "seal_broken";
	public static final String STARVED_SLAIN = "starved_slain";
	public static final String ENDING_MENDED = "ending_mended";
	public static final String ENDING_SEVERED = "ending_severed";

	/** Expeditions needed for {@link #VETERAN}. */
	public static final int VETERAN_EXPEDITIONS = 10;
	/** Blocks from the arrival portal needed for {@link #LONG_TETHER}. */
	public static final double LONG_TETHER_BLOCKS = 300;

	public static void grant(ServerPlayer p, String id) {
		WorldFx.grantAdvancement(p, id);
	}
}
