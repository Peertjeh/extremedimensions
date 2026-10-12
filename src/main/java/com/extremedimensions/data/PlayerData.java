package com.extremedimensions.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.penalty.Penalty;

/** Everything the mod knows about one player. Non-transient fields are saved to the world's JSON file. */
public class PlayerData {
	// --- saved ---
	/** Leaky Tank stacks waiting for the next expedition. */
	public int leakStacks = 0;
	/** Toll/Tithe: levels the Tether takes as soon as they are earned. */
	public int xpDebt = 0;
	/** Remaining Mourning seconds (display only; the effects are vanilla). */
	public double mourningSecs = 0;
	public int expeditions = 0;
	public int collapses = 0;
	public double longestRun = 0;
	/** Penalty id -> remaining active seconds per stack. */
	public Map<String, List<Double>> penaltyTime = new HashMap<>();
	/** Penalty ids currently on the choice screen (empty = nothing to choose). */
	public List<String> pendingOffer = new ArrayList<>();
	/** Penalties still to be chosen (after several deaths in a row). */
	public int pendingPenalties = 0;
	/** Per owed penalty its set: "beyond" or "overworld". The first one is on screen now. */
	public List<String> owed = new ArrayList<>();
	/** Anchor keys feeding the current expedition (saved in case the player logs out beyond). */
	public List<String> linked = new ArrayList<>();
	/** Charge at the start of the expedition (fills the timer bar). */
	public double runMax = 0;
	/** Unlocked Tether Journal pages. */
	public Set<String> journal = new LinkedHashSet<>();
	public boolean journalDelivered = false;
	/** Anchor key the physical tether is bound to; null = not bound. */
	public String tetherAnchor = null;
	/** Arrival point beyond the wound (distance to the portal). */
	public double arrivalX, arrivalZ;
	/** Furthest distance from the arrival portal ever reached (advancement). */
	public double furthest = 0;

	// --- runtime only ---
	public transient String dim = "overworld";
	public transient boolean inRun = false;
	public transient boolean collapsing = false;
	public transient double runTime = 0;
	public transient int grace = 0;
	public transient int tickCounter = 0;
	public transient int lastWarn = Integer.MAX_VALUE;
	public transient boolean hasLast = false;
	public transient double lastX, lastY, lastZ;
	public transient int collapseRetry = 0;
	public transient int lastBeat = Integer.MAX_VALUE;
	/** Ticks until the penalty screen opens; -1 = nothing scheduled. */
	public transient int offerDelay = -1;
	/** Died beyond the wound (not from the timer): shows the "Expedition Ended" title on respawn. */
	public transient String endedIn = null;

	// Idle detection: penalties only wear off while the player looks around and moves
	public transient float lastYaw, lastPitch;
	public transient double anchorX, anchorZ;
	public transient int ticksSinceLook = Integer.MAX_VALUE / 2;
	public transient int ticksSinceMove = Integer.MAX_VALUE / 2;
	/** 0 = no penalty clock here, 1 = wearing off, 2 = paused (idle). */
	public transient int penaltyClock = 0;
	public transient boolean joinDone = false;
	public transient int ticksOnline = 0;
	/** Current drain including the distance factor (HUD). */
	public transient double currentDrain = 1.0;

	// Physical tether
	public transient boolean hasRopeEnd = false;
	public transient double ropeX, ropeY, ropeZ;
	public transient String ropeDim = null;
	public transient int ropeIndex = 0;

	public int stacks(Penalty p) {
		List<Double> l = penaltyTime.get(p.id);
		return l == null ? 0 : l.size();
	}

	public void addStack(Penalty p) {
		penaltyTime.computeIfAbsent(p.id, k -> new ArrayList<>()).add((double) Config.PENALTY_SECONDS);
	}

	public double drainMultiplier() {
		return Config.BASE_DRAIN * (1.0 + Config.ACCELERATED_DRAIN_PER_STACK * stacks(Penalty.ACCELERATED_DRAIN));
	}

	public double debtFactor() {
		return Math.pow(Config.DEBT_FACTOR, stacks(Penalty.DEBT));
	}
}
