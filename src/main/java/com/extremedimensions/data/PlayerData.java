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
	/** The name the player last played under (an ally's team is looked up by it while they are away). */
	/** The journal's bestiary: creatures seen, and slain by id; and a few more things counted for its ledger. */
	public Set<String> seen = new LinkedHashSet<>();
	public Map<String, Integer> kills = new HashMap<>();
	public int deaths = 0, seamVisits = 0, frayDescents = 0, lifted = 0;
	/** Times the Seam has said what a well is for, as the player came near one. */
	public int wellHints = 0;
	/** Standing with the tribe (-100 to 100), gifts given today and wares bought today (offer index:times). */
	public int tribeRep = 0;
	public long tribeDay = -1;
	public int tribeGifts = 0;
	public Map<Integer, Integer> barterUsed = new HashMap<>();
	/** Online seconds until the tribe forgets a point of a grudge, and until it next hunts this player. */
	public int tribeForgetIn = 0, tribeHuntIn = 0;
	public int raidsWon = 0, bartered = 0;
	/** The day (Overworld game days) the tribe last took note of a bond with one of its young; -1 = never. */
	public long bondRepDay = -1;
	public String name = null;
	/** Whether the player's charge in their anchors is open to their allies. */
	public boolean shareAnchors = false;
	/** Co-op: allies lifted up, times lifted up by an ally, and curse stacks carried for allies. */
	public int liftsGiven = 0, liftsTaken = 0, burdensCarried = 0;
	/** Co-op: when (Overworld game time) the player last fell; -1 = never. They fall again only after a while. */
	public long fallenAt = -1;
	/** Whether the Kindred Knot was bound at that fall (it shortens the wait only then). */
	public boolean fallenKnot = false;
	/** Steps of the first-expedition guide done (see Guide). */
	public int guide = 0;
	/** First-meeting hints already given (see guide.Hints), and whether what they knew before hints existed was looked at. */
	public Set<String> hints = new LinkedHashSet<>();
	public boolean hintsChecked = false;
	/** The relic bound to the Tether (its id), or null. */
	public String relic = null;
	public int collapses = 0;
	public double longestRun = 0;
	/** Curse id -> stacks. Curses are permanent: only atonement, fragments or a Mending Cloth take stacks off. */
	public Map<String, Integer> curses = new HashMap<>();
	/**
	 * Curses the player has carried or sworn at some time: where unknown curses show as runes (Config.CURSE_RUNES),
	 * these read in plain words. Kept after the last stack is lifted.
	 */
	public Set<String> knownCurses = new LinkedHashSet<>();
	/** How far the player is with each Abyssal curse's deed (curse id -> count), see AbyssalCurses. */
	public Map<String, Integer> abyssalDeeds = new HashMap<>();
	/** Gravebound: the Overworld game time its days are counted from (-1 = not carried); The Hungering: the day it last ate. */
	public long graveSince = -1, hungeringDay = -1;
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
	/** New Thread+: the threads beyond gold already paid for that week. */
	public int trialClaimThreads = 0;
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
	/** Notes in another hand found in the journal's margins (see {@code MarginNote}). */
	public Set<String> notes = new LinkedHashSet<>();
	/** Has found a Held's chamber of their own (the first one they come to holds the Held's letter and chain). */
	public boolean heldFound = false;
	/** The First Weaver's trail: the frame of an Ancient City strung, the First Knot reached. */
	public boolean frameStrung = false, knotReached = false;
	/** When (system time) the Heart panel of a severed world last spared what this player carried in a fall beyond the wound. */
	public long heartSparedAt = 0;
	/** This expedition, the Held's Chain already held the player once (kept, so leaving the game during it is no way out). */
	public boolean heldSpent = false;
	/** Ticks the Held's Chain still holds the player after the charge ran out. */
	public transient int heldTicks = 0;
	/** The Homebound pieces the player carries, as last counted, and when (game time): counted once a second. */
	public transient int homeboundPieces = 0;
	public transient long homeboundCounted = Long.MIN_VALUE / 2;
	public boolean journalDelivered = false;
	/** In the Seam: time left, the rift home (in the Seam) and where the player came from. */
	public boolean inSeam = false;
	public double seamLeft = 0, seamMax = 0;
	public double riftX, riftY, riftZ;
	/** The way the rift home's tear faces (a yaw: it stands across its gate). */
	public float riftYaw;
	public String homeDim = "overworld";
	public double homeX, homeY, homeZ;
	/** Was in the Seam when it was made anew: brought home (no curse) when next seen. */
	public boolean seamLost = false;
	public transient int seamPull = 0;
	/** Tier of the bound Tether: 0 iron chain, 1 soul-woven, 2 seam-woven. */
	public int tetherTier = 0;
	/** The bound Tether's enchantments (id to level): the item is tied into the anchor, its charms are kept here. */
	public Map<String, Integer> tetherCharms = new HashMap<>();
	/** Homebound things taken up at a death beyond the wound, waiting for the respawn (each one an item stack as JSON). */
	public List<String> homebound = new ArrayList<>();
	/** Atonement an echo of this player's Collapse gave back while they were away (granted when they are next in). */
	public int echoOwed = 0;
	/** When the Endermen last built for this player (ms since the epoch): the cooldown holds across restarts. */
	public long lastUnanchored = 0;
	/** When another's echo last paid this player for hearing it out (ms since the epoch): it pays once in a while. */
	public long echoPaidAt = 0;
	/** When a world event last began for this player (ms since the epoch): the spacing holds across restarts. */
	public long lastEventAt = 0;
	/** The visit to the Seam (by its count) in which this player's island last frayed: once a visit, across restarts. */
	public int frayedVisit = -1;
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
	/** The charge ran out and the death that follows has not come yet (it comes on joining, if they left meanwhile). */
	public boolean collapseOwed = false;
	/** Levels the Ferryman is owed by someone who left the game while he sailed for them. */
	public int fareOwed = 0;
	/** Ultimates whose curse is owed (their bosses' keys): the player slipped away while one held them. */
	public List<String> ultimatesOwed = new ArrayList<>();
	/** Allies this player carried a curse for, and when (game time): the wait before carrying for them again. */
	public Map<String, Long> carriedFor = new HashMap<>();
	/** The Overworld day of the last curse carried for this player, and how many stacks were carried that day. */
	public long reliefDay = -1;
	public int reliefs = 0;
	/** Ticks the current curse offer has stood unanswered (the Tether picks for the player after a while). */
	public transient int offerAge = 0;
	public double runTime = 0;
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
	public int runActive = 0;
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
	/** Active seconds in the current Overworld night (a dawn earns atonement only after enough of them). */
	public transient int nightActive = 0;
	/** Short Breath and Frostbitten: last air and frozen ticks seen, and the fraction still owed. */
	public transient int lastAir = -1, lastFrozen = -1;
	public transient double airCarry, frozenCarry;
	/** Lead Pockets: the slowdown currently applied. */
	public transient double leadValue = Double.NaN;
	/** Weight of Iron's slowdown as last set (NaN: set it afresh). */
	public transient double ironValue = Double.NaN;
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
			learn(p);
		}
		return add;
	}

	/** Whether the player has ever carried or sworn this curse (one carried now always counts). */
	public boolean knows(Penalty p) {
		return owned(p) > 0 || (knownCurses != null && knownCurses.contains(p.id));
	}

	/** From now on the player knows what this curse does. */
	public void learn(Penalty p) {
		if (knownCurses == null) {
			knownCurses = new LinkedHashSet<>();
		}
		knownCurses.add(p.id);
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
		return drainMultiplier(1.0);
	}

	/**
	 * The drain, with whatever else eases it here ({@code ease}, at most 1: a relic, a mended world). The woven Tether,
	 * scars, Thrift and that together never bring it below the drain floor.
	 */
	public double drainMultiplier(double ease) {
		double tier = tetherTier == 2 ? Config.SEAM_WOVEN_DRAIN : tetherTier == 1 ? Config.SOUL_WOVEN_DRAIN : 1.0;
		double scar = 1.0 - Config.SCAR_DRAIN_REDUCTION * Math.min(5, scars);
		double thrift = 1.0 - Config.ENCH_THRIFT_DRAIN * charm(com.extremedimensions.registry.ModEnchantments.THRIFT);
		double eased = Math.max(Config.ENCH_DRAIN_FLOOR, tier * scar * thrift * Math.min(1.0, ease));
		return Config.BASE_DRAIN * eased * (1.0 + Penalty.ACCELERATED_DRAIN.frac() * stacks(Penalty.ACCELERATED_DRAIN));
	}

	/** The level of an enchantment on the bound Tether (0 when unbound or without it). */
	public int charm(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> k) {
		if (tetherAnchor == null || tetherCharms == null) {
			return 0;
		}
		return tetherCharms.getOrDefault(com.extremedimensions.registry.ModEnchantments.id(k), 0);
	}

	public double debtFactor() {
		return Math.pow(Math.max(0.05, 1.0 - Penalty.DEBT.frac()), stacks(Penalty.DEBT));
	}
}
