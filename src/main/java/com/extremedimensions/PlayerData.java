package com.extremedimensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alle gegevens van een speler. Velden zonder "transient" worden opgeslagen in JSON.
 */
public class PlayerData {
	// --- opgeslagen ---
	/** Lekkende Tank: aantal stacks dat op de volgende expeditie wacht. */
	public int leakStacks = 0;
	/** Toll/Tithe: zoveel levels neemt de Tether van je af zodra je ze verdient. */
	public int xpDebt = 0;
	public int expeditions = 0;
	public int collapses = 0;
	public double longestRun = 0;
	/** penalty-id -> per stack de resterende actieve seconden */
	public Map<String, List<Double>> penaltyTime = new HashMap<>();
	/** penalty-id's die nu op het keuzescherm liggen (leeg = niets te kiezen) */
	public List<String> pendingOffer = new ArrayList<>();
	/** Hoeveel penalty's de speler nog moet kiezen (na meerdere doden achter elkaar). */
	public int pendingPenalties = 0;
	/** Per tegoed-penalty uit welke set: "beyond" (Nether/End) of "overworld". Eerste = nu op het scherm. */
	public List<String> owed = new ArrayList<>();
	/** De altaren (sleutels) die de lopende expeditie van brandstof voorzien. Opgeslagen voor als je uitlogt. */
	public List<String> linked = new ArrayList<>();
	/** Charge bij de start van de expeditie (voor de vulling van de timerbalk). */
	public double runMax = 0;
	/** Ontgrendelde pagina's van het Tether Journal. */
	public Set<String> journal = new LinkedHashSet<>();
	/** Of de speler zijn eerste Tether Journal al kreeg. */
	public boolean journalDelivered = false;
	/** Het anker (sleutel) waar de speler zijn fysieke tether aan vastmaakte; null = niet vastgemaakt. */
	public String tetherAnchor = null;
	/** Waar de speler aankwam in Nether/End (voor de afstand tot het portaal). */
	public double arrivalX, arrivalZ;

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
	public transient int collapseRetry = 0;
	public transient int lastBeat = Integer.MAX_VALUE;
	/** Ticks tot het penalty-scherm opent; -1 = niets gepland. */
	public transient int offerDelay = -1;
	/** Gewoon doodgegaan in Nether/End (niet door de tijd): grote melding bij respawn. */
	public transient String endedIn = null;

	// AFK-detectie: penalty's lopen alleen af als je rondkijkt en rondloopt
	public transient float lastYaw, lastPitch;
	public transient double anchorX, anchorZ;
	public transient int ticksSinceLook = Integer.MAX_VALUE / 2;
	public transient int ticksSinceMove = Integer.MAX_VALUE / 2;
	/** 0 = geen penalty-klok, 1 = penalty's lopen af, 2 = gepauzeerd (AFK) */
	public transient int penaltyClock = 0;
	/** Join-acties (journal, recepten, effecten) gedaan sinds deze speler online kwam. */
	public transient boolean joinDone = false;
	public transient int ticksOnline = 0;
	/** De huidige drain inclusief afstandstoeslag (voor de HUD). */
	public transient double currentDrain = 1.0;
	/** Waar het laatste kettingstuk eindigde. */
	public transient boolean hasRopeEnd = false;
	public transient double ropeX, ropeY, ropeZ;
	public transient String ropeDim = null;
	/** Volgnummer van het volgende kettingstuk (voor het opruimen van de oudste stukken). */
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
