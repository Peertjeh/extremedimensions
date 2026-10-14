package com.extremedimensions.trial;

import java.util.HashSet;
import java.util.Set;

/** A trial under way in the Seam (saved with the player, so a relog does not lose it). */
public class TrialRun {
	public int week;
	public String kind;
	public String twist;
	public int score;
	/** Where the visit arrived (the far-walking trial measures from here). */
	public double fromX, fromZ;
	/** Island cells set foot on ("cx,cz"). */
	public Set<String> cells = new HashSet<>();
}
