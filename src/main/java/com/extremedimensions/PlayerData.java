package com.extremedimensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alle gegevens van een speler. Velden zonder "transient" worden opgeslagen in JSON.
 */
public class PlayerData {
	// --- opgeslagen ---
	/** Lekkende Tank: aantal stacks dat op de volgende expeditie wacht. */
	public int leakStacks = 0;
	public int expeditions = 0;
	public int collapses = 0;
	public double longestRun = 0;
	/** penalty-id -> per stack de resterende geslaagde runs */
	public Map<String, List<Integer>> penaltyStacks = new HashMap<>();
	/** penalty-id's die nu op het keuzescherm liggen (leeg = niets te kiezen) */
	public List<String> pendingOffer = new ArrayList<>();
	/** Hoeveel penalty's de speler nog moet kiezen (na meerdere doden achter elkaar). */
	public int pendingPenalties = 0;
	/** De altaren (sleutels) die de lopende expeditie van brandstof voorzien. Opgeslagen voor als je uitlogt. */
	public List<String> linked = new ArrayList<>();
	/** Charge bij de start van de expeditie (voor de vulling van de timerbalk). */
	public double runMax = 0;

	// --- alleen tijdens het spelen ---
	public transient String dim = "overworld";
	public transient boolean inRun = false;
	public transient boolean collapsing = false;
	public transient double runTime = 0;
	public transient int grace = 0;
	public transient int tickCounter = 0;
	public transient int lastWarn = Integer.MAX_VALUE;
	public transient boolean hasLast = false;
	public transient double lastX, lastY, lastZ;
	public transient String respawnNotice = null;
	public transient int collapseRetry = 0;
	public transient int lastBeat = Integer.MAX_VALUE;
	/** Ticks tot het penalty-scherm opent; -1 = niets gepland. */
	public transient int offerDelay = -1;
	/** Gewoon doodgegaan in Nether/End (niet door de tijd): grote melding bij respawn. */
	public transient String endedIn = null;

	public int stacks(Penalty p) {
		List<Integer> l = penaltyStacks.get(p.id);
		return l == null ? 0 : l.size();
	}

	public void addStack(Penalty p) {
		penaltyStacks.computeIfAbsent(p.id, k -> new ArrayList<>()).add(Config.PENALTY_RUNS);
	}

	public double drainMultiplier() {
		return Config.BASE_DRAIN * (1.0 + Config.ACCELERATED_DRAIN_PER_STACK * stacks(Penalty.ACCELERATED_DRAIN));
	}

	public double debtFactor() {
		return Math.pow(Config.DEBT_FACTOR, stacks(Penalty.DEBT));
	}
}
