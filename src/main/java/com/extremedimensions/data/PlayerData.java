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
	public int expeditions = 0;
	/** Steps of the first-expedition guide done (see Guide). */
	public int guide = 0;
	/** The relic bound to the Tether (its id), or null. */
	public String relic = null;
	public int collapses = 0;
	public double longestRun = 0;
	/** Curse id -> stacks. Curses are permanent: only atonement, fragments or a Mending Cloth take stacks off. */
	public Map<String, Integer> curses = new HashMap<>();
	/** The sworn pact: extra stacks of curses taken on willingly, acting on expeditions (see PactService). */
	public Map<String, Integer> pact = new HashMap<>();
	/** The Elders: Keepers slain (the Matriarch comes after enough of them), and whether she has fallen to this player. */
	public int keepersSlain = 0;
	public boolean matriarchSlain = false;
	/** Whether the Ferryman has had his chance on this expedition. */
	public transient boolean ferrymanRolled = false;
	/** Rift Trials: taken up for the next visit to the Seam, the one under way, and the medals already paid this week. */
	public boolean trialArmed = false;
	public com.extremedimensions.trial.TrialRun trial = null;
	public int trialClaimWeek = Integer.MIN_VALUE;
	public int trialClaimMedal = 0;
	/** Curse id -> atonement points gathered towards taking its next stack off. */
	public Map<String, Integer> atonement = new HashMap<>();
	/** The curse the player chose to atone for (and to mend first); null = the heaviest one. */
	public String atoning = null;
	/** Before 2.3 curses wore off: id -> remaining seconds per stack. Read once and turned into permanent stacks. */
	public Map<String, List<Double>> penaltyTime = new HashMap<>();
	/** Curse ids currently on the choice screen (empty = nothing to choose), and each card's multiplier. */
	public List<String> pendingOffer = new ArrayList<>();
	public List<Integer> pendingMult = new ArrayList<>();
	/** Died since the last dawn (no atonement for that night). */
	public boolean diedToday = false;
	/** Every curse stack mended in the Overworld set is gone, but the Starved took the levels instead (all at max). */
	public boolean xpForfeit = false;
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
	/** In the Seam: time left, the rift home (in the Seam) and where the player came from. */
	public boolean inSeam = false;
	public double seamLeft = 0, seamMax = 0;
	public double riftX, riftY, riftZ;
	public String homeDim = "overworld";
	public double homeX, homeY, homeZ;
	public transient int seamPull = 0;
	/** Tier of the bound Tether: 0 iron chain, 1 soul-woven, 2 seam-woven. */
	public int tetherTier = 0;
	/** Scars: curse stacks taken off again. Each one makes the drain a little weaker (up to five). */
	public int scars = 0;
	/** A Hollowling follows this player. */
	public boolean companion = false;
	/** Online seconds until the companion brings its next find. */
	public int companionGiftIn = 0;
	/** Anchor key the physical tether is bound to; null = not bound. */
	public String tetherAnchor = null;
	/** Arrival point beyond the wound (distance to the portal). */
	public double arrivalX, arrivalZ;
	/** Furthest distance from the arrival portal ever reached (advancement). */
	public double furthest = 0;

	// --- runtime only ---
	public transient String dim = "overworld";
	/** In another mod's world: nothing of the mod acts on this player there (see {@code Dims.ours}). */
	public transient boolean away = false;
	/** Died in another mod's world: the death costs nothing of the mod's. */
	public transient boolean diedAway = false;
	public transient boolean inRun = false;
	public transient boolean collapsing = false;
	public transient double runTime = 0;
	public transient int grace = 0;
	public transient int tickCounter = 0;
	public transient int lastWarn = Integer.MAX_VALUE;
	public transient boolean hasLast = false;
	public transient double lastX, lastY, lastZ;
	/** Collapse timer: negative while the pull runs, then counts up to retry the death. */
	public transient int collapseRetry = 0;
	public transient int lastBeat = Integer.MAX_VALUE;
	/** Ticks until the penalty screen opens; -1 = nothing scheduled. */
	public transient int offerDelay = -1;
	/** Died beyond the wound (not from the timer): shows the "Expedition Ended" title on respawn. */
	public transient String endedIn = null;
	/** Atonement points the last death took away (told on respawn). */
	public transient int atonementLost = 0;

	// Idle detection: atonement only grows while the player looks around and moves
	public transient float lastYaw, lastPitch;
	public transient double anchorX, anchorZ;
	public transient int ticksSinceLook = Integer.MAX_VALUE / 2;
	public transient int ticksSinceMove = Integer.MAX_VALUE / 2;
	/** Active seconds on the current expedition not yet turned into atonement. */
	public transient double activeSecs = 0;
	/** Active seconds on the current expedition (a kept pact pays by them). */
	public transient int runActive = 0;
	// Curse upkeep: what the player had last tick (XP and food skims), sleep, spells and risings
	public transient int lastXp = -1;
	public transient double xpCarry = 0;
	public transient int lastFood = -1;
	public transient double foodCarry = 0;
	public transient boolean sleepRolled = false, sleepFails = false;
	public transient int vertigoIn = 60, huntIn = 120;
	/** Stormcalled: seconds under an open, thundering sky until the next bolt; -1 = not counting yet. */
	public transient int stormIn = -1;
	/** Tithe Bell: experience owed but not yet whole. */
	public transient double xpBonusCarry = 0;
	/** Recent kills that could earn atonement: x, z, game time (see AtonementGuard). */
	public transient java.util.ArrayDeque<long[]> recentKills = new java.util.ArrayDeque<>();
	/** Whether it was dark outside last time we looked (dawn = a night survived). */
	public transient int wasDark = -1;
	/** Short Breath and Frostbitten: last air and frozen ticks seen, and the fraction still owed. */
	public transient int lastAir = -1, lastFrozen = -1;
	public transient double airCarry, frozenCarry;
	/** Lead Pockets: the slowdown currently applied. */
	public transient double leadValue = Double.NaN;
	public transient boolean joinDone = false;
	public transient int ticksOnline = 0;
	/** Current drain including the distance factor (HUD). */
	public transient double currentDrain = 1.0;

	// Physical tether
	public transient boolean hasRopeEnd = false;
	public transient double ropeX, ropeY, ropeZ;
	public transient String ropeDim = null;
	public transient int ropeIndex = 0;

	/** Stacks of a curse the player carries (what owing, offers, atonement and mending count). */
	public int owned(Penalty p) {
		return curses.getOrDefault(p.id, 0);
	}

	/** Stacks sworn in the pact for a curse (0 without a pact). */
	public int sworn(Penalty p) {
		return pact == null ? 0 : pact.getOrDefault(p.id, 0);
	}

	/**
	 * Stacks of a curse as they act now: the ones carried, and on an expedition the pact's on top (never past the
	 * curse's maximum); none at all in another mod's world, where the curses sleep. Every effect reads this;
	 * bookkeeping reads {@link #owned}.
	 */
	public int stacks(Penalty p) {
		return away ? 0 : carried(p);
	}

	/** Stacks of a curse carried right now, pact included, whether or not they act in this world (for the HUD). */
	public int carried(Penalty p) {
		int own = owned(p);
		return inRun ? Math.min(Math.max(own, p.max), own + sworn(p)) : own;
	}

	/** Adds up to {@code n} stacks, never past the curse's maximum; returns how many were added. */
	public int addStacks(Penalty p, int n) {
		int have = owned(p);
		int add = Math.max(0, Math.min(n, p.max - have));
		if (add > 0) {
			curses.put(p.id, have + add);
		}
		return add;
	}

	/** Takes one stack off; the curse's atonement starts over. Returns false if it had none. */
	public boolean removeStack(Penalty p) {
		int have = owned(p);
		if (have <= 0) {
			return false;
		}
		if (have == 1) {
			curses.remove(p.id);
		} else {
			curses.put(p.id, have - 1);
		}
		atonement.remove(p.id);
		return true;
	}

	/** Stacks of a curse, but only where it acts (beyond curses beyond the wound, Overworld curses at home, the rest everywhere). */
	public int active(Penalty p, boolean beyond, boolean overworld) {
		return (p.everywhere || (p.overworld ? overworld : beyond)) ? stacks(p) : 0;
	}

	/** Turns the stacks of the old, timed penalties into permanent curses (capped at their maximum), once. */
	public void migrate() {
		if (penaltyTime == null || penaltyTime.isEmpty()) {
			return;
		}
		for (Map.Entry<String, List<Double>> e : penaltyTime.entrySet()) {
			Penalty p = Penalty.byId(e.getKey());
			if (p != null && e.getValue() != null && !e.getValue().isEmpty()) {
				curses.merge(p.id, Math.min(p.max, e.getValue().size()), Integer::max);
			}
		}
		penaltyTime.clear();
	}

	public double drainMultiplier() {
		double tier = tetherTier == 2 ? Config.SEAM_WOVEN_DRAIN : tetherTier == 1 ? Config.SOUL_WOVEN_DRAIN : 1.0;
		double scar = 1.0 - Config.SCAR_DRAIN_REDUCTION * Math.min(5, scars);
		return Config.BASE_DRAIN * tier * scar * (1.0 + Penalty.ACCELERATED_DRAIN.frac() * stacks(Penalty.ACCELERATED_DRAIN));
	}

	public double debtFactor() {
		return Math.pow(Math.max(0, 1.0 - Penalty.DEBT.frac()), stacks(Penalty.DEBT));
	}
}
