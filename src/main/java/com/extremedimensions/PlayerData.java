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
	public double nether = 0;
	public double end = 0;
	public int expeditions = 0;
	public int collapses = 0;
	public double longestRun = 0;
	/** penalty-id -> resterende geslaagde runs */
	public Map<String, Integer> penalties = new HashMap<>();
	/** penalty-id's die de speler nog moet kiezen (leeg = niets te kiezen) */
	public List<String> pendingOffer = new ArrayList<>();

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
	/** In een wereld uit Config.PAUSED_NAMESPACES (BattleHub): alles staat stil. */
	public transient boolean paused = false;

	public boolean has(Penalty p) {
		return penalties.containsKey(p.id);
	}

	public double drainMultiplier() {
		double m = Config.BASE_DRAIN;
		if (has(Penalty.ACCELERATED_DRAIN)) {
			m *= Config.ACCELERATED_DRAIN_FACTOR;
		}
		return m;
	}
}
