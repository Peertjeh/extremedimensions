package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The fighting mind of one NPC, without Minecraft: it keeps a track and an opponent model per player it has seen,
 * decides on a tactic every few ticks (sooner when something urgent happens), steers every tick, learns which
 * tactics work, and can explain itself ({@link #report()}). The world layer feeds it {@link Situation}s and
 * carries out its {@link Intent}s.
 */
public final class CombatMind {
	/** Ticks after which an unseen player is half forgotten. */
	public static final int HALF_LIFE = 60;
	private static final int MAX_TRACKED = 8;

	private final Archetype archetype;
	private final Personality personality;
	private final Difficulty difficulty;
	private final Random rng;
	private final Decider decider = new Decider();
	private final CombatSteering steering = new CombatSteering();
	private final OutcomeMemory outcomes = new OutcomeMemory();
	private final Map<UUID, Track> tracks = new LinkedHashMap<>(16, 0.75f, true);
	private final Map<UUID, OpponentModel> models = new LinkedHashMap<>(16, 0.75f, true);

	private Decider.Decision last;
	private int nextThink = Integer.MIN_VALUE;
	private UUID targetId;
	private int reactUntil = Integer.MIN_VALUE;
	private int lastVisibleTick = Integer.MIN_VALUE;
	private Tactic outcomeTactic = Tactic.IDLE;
	private double dealt;
	private double taken;
	/** The last tick anyone in this fight was hit (or the fight began). */
	private int lastExchange = Integer.MIN_VALUE;
	private double totalDealt;
	private double totalTaken;
	private final java.util.EnumMap<Tactic, Integer> tacticTicks = new java.util.EnumMap<>(Tactic.class);
	private long thinkNanos;
	private long thinkMax;
	private int thinks;
	private Consumer<String> debug;
	/** Asked before each planned think (the server's budget); null: always yes. Dangers do not ask. */
	private java.util.function.BooleanSupplier gate;
	/** The time between thinks is multiplied by this while the server is busy. */
	private int stretch = 1;
	private long deferred;

	public CombatMind(Archetype archetype, Personality personality, Difficulty difficulty, long seed) {
		this.archetype = archetype;
		this.personality = personality.clamped();
		this.difficulty = difficulty;
		this.rng = new Random(seed);
		// spread the thinking of many NPCs over different ticks
		this.nextThink = Math.floorMod(seed, Math.max(1, difficulty.thinkTicks));
	}

	public Personality personality() {
		return personality;
	}

	public Difficulty difficulty() {
		return difficulty;
	}

	public Archetype archetype() {
		return archetype;
	}

	public Tactic tactic() {
		return decider.current();
	}

	/** The server's think budget: asked before every planned think. */
	public void gate(java.util.function.BooleanSupplier gate) {
		this.gate = gate;
	}

	/** While the server is busy: think this many times less often (1 = normal). */
	public void stretch(int stretch) {
		this.stretch = Math.max(1, stretch);
	}

	/** How often it thought so far, and how long that took (cheap, for the server's metrics). */
	public int thinks() {
		return thinks;
	}

	public long thinkNanos() {
		return thinkNanos;
	}

	public double maxMicros() {
		return thinkMax / 1000.0;
	}

	/** Planned thinks put off because the server had none to spare. */
	public long deferred() {
		return deferred;
	}

	/** Log every switch (one NPC at a time, from the debug command); null stops it. */
	public void debug(Consumer<String> log) {
		this.debug = log;
	}

	/** A sighting of a player (only for players located by sight or name tag); {@code rising}: going up (a jump). */
	public void see(int tick, UUID id, V3 pos, double dist, boolean rising) {
		track(id).observe(tick, pos);
		if (rising) risingNow.add(id);
	}

	private final java.util.Set<UUID> risingNow = new java.util.HashSet<>();

	/** Once per think: what the opponent's own movement says about them (seen from the NPC's position). */
	private void study(UUID id, V3 me, int tick) {
		Track t = tracks.get(id);
		if (t == null || t.empty() || tick - t.lastSeen() > 2) return;
		V3 last = t.last();
		V3 toMe = me.sub(last).flat().unit();
		// their own speed towards the NPC: the NPC walking up to them changes the distance, not this
		double approach = t.velocity().flat().dot(toMe);
		model(id).observe(last.distance(me), approach, t.strafeChanges(me, 20), risingNow.remove(id));
	}

	public Track track(UUID id) {
		Track t = tracks.get(id);
		if (t == null) {
			t = new Track();
			tracks.put(id, t);
			while (tracks.size() > MAX_TRACKED) tracks.remove(tracks.keySet().iterator().next());
		}
		return t;
	}

	public OpponentModel model(UUID id) {
		OpponentModel m = models.get(id);
		if (m == null) {
			m = new OpponentModel();
			models.put(id, m);
			while (models.size() > MAX_TRACKED) models.remove(models.keySet().iterator().next());
		}
		return m;
	}

	/** A target was seen swinging. */
	public void opponentSwing(UUID id, int tick) {
		model(id).swing(tick);
	}

	/** A player's hit landed on this NPC. */
	public void hitBy(UUID id) {
		model(id).hit();
	}

	private int swingsMade;
	private int swingsLanded;

	/** This NPC swung: did the crosshair find the target? */
	public void swung(boolean landed) {
		swingsMade++;
		if (landed) swingsLanded++;
	}

	public int swingsMade() {
		return swingsMade;
	}

	public int swingsLanded() {
		return swingsLanded;
	}

	/** Damage this NPC did (health points); counts for the tactic that was active. */
	public void dealt(double hp) {
		dealt += Math.max(0, hp);
		totalDealt += Math.max(0, hp);
	}

	/** Damage this NPC took. */
	public void taken(double hp) {
		taken += Math.max(0, hp);
		totalTaken += Math.max(0, hp);
	}

	public double totalDealt() {
		return totalDealt;
	}

	public double totalTaken() {
		return totalTaken;
	}

	/** Share of the fight spent in each tactic (0..1). */
	public java.util.Map<Tactic, Double> tacticShares() {
		int all = 0;
		for (int n : tacticTicks.values()) all += n;
		java.util.Map<Tactic, Double> out = new java.util.EnumMap<>(Tactic.class);
		for (var e : tacticTicks.entrySet()) out.put(e.getKey(), all == 0 ? 0 : e.getValue() / (double) all);
		return out;
	}

	/** What this NPC tells {@code player} after the fight. */
	public List<Debrief.Line> debrief(String botName, UUID player) {
		return Debrief.lines(botName, archetype.id(), difficulty.id(), models.get(player), totalDealt, totalTaken, tacticShares(), swingsMade,
				swingsLanded);
	}

	/** Decide (when it is time, or at once when something urgent happens) and steer for this tick. */
	public Intent tick(Situation s) {
		Situation.Target t = s.target();
		UUID id = t == null ? null : t.id();
		boolean newTarget = id != null && !id.equals(targetId);
		boolean reappeared = t != null && t.visible() && s.tick() - lastVisibleTick > 20;
		if (newTarget || reappeared) {
			// a human needs a moment to react to something new
			reactUntil = s.tick() + difficulty.reactionTicks;
		}
		if (newTarget) {
			targetId = id;
			lastExchange = s.tick();
		}
		if (t != null && t.visible()) lastVisibleTick = s.tick();
		boolean urgent = newTarget || (s.threat() != null && s.threat().any() && decider.current() != Tactic.EVADE)
				|| (t != null && t.visible() && s.distance() < s.self().reach() + 2 && s.ground(s.self().pos().sub(t.pos()).flat().unit()) < 1.0
						&& decider.current() != Tactic.RECOVER);
		boolean due = s.tick() >= nextThink;
		if (due && !urgent && last != null && gate != null && !gate.getAsBoolean()) {
			// the server has no think to spare this tick: keep the current plan a tick longer
			due = false;
			deferred++;
		}
		if (last == null || due || urgent) {
			think(s);
			nextThink = s.tick() + difficulty.thinkTicks * stretch;
		}
		tacticTicks.merge(decider.current(), 1, Integer::sum);
		learnedNow = false;
		reactingNow = s.tick() < reactUntil && t != null;
		if (reactingNow) {
			// still taking it in: look, do not act yet
			return Intent.NOTHING.withLook(t.pos().add(new V3(0, 1.4, 0)));
		}
		Tactic tactic = decider.current();
		if (melee != null && (FIGHTING.contains(tactic) || tactic == Tactic.SHOOT)) {
			Intent learned = melee.act(tactic, s, id == null ? null : tracks.get(id));
			if (learned != null) {
				learnedTicks++;
				learnedNow = true;
				return learned;
			}
		}
		return steering.steer(tactic, s, id == null ? null : tracks.get(id), personality, difficulty, rng);
	}

	/** The tactics of the fight itself, which a {@link MeleeController} may steer (healing, edges, objectives stay scripted). */
	private static final java.util.Set<Tactic> FIGHTING = java.util.EnumSet.of(Tactic.APPROACH, Tactic.STRIKE, Tactic.SPACE, Tactic.RETREAT);
	private MeleeController melee;
	private int learnedTicks;

	/** Lets a controller (the learned model) fight for this mind; null: only the scripted steering. */
	public void melee(MeleeController controller) {
		this.melee = controller;
	}

	/** The controller that may fight for this mind, or null. */
	public MeleeController melee() {
		return melee;
	}

	/** Ticks the learned controller steered (for the self-test and /bots inspect). */
	public int learnedTicks() {
		return learnedTicks;
	}

	private boolean learnedNow, reactingNow;

	/** Whether this tick's intent came from the learned controller. */
	public boolean learnedNow() {
		return learnedNow;
	}

	/** Whether this tick it was still taking in a new or returning target (looking, not acting). */
	public boolean reactingNow() {
		return reactingNow;
	}

	private void think(Situation s) {
		long t0 = System.nanoTime();
		// credit what happened since the last think to the tactic that was active
		if (dealt > 0 || taken > 0) {
			outcomes.record(outcomeTactic, dealt, taken);
			dealt = 0;
			taken = 0;
			lastExchange = s.tick();
		}
		int quiet = lastExchange == Integer.MIN_VALUE ? 0 : s.tick() - lastExchange;
		Situation.Target t = s.target();
		OpponentModel om = t == null ? new OpponentModel() : model(t.id());
		if (t != null && t.visible()) study(t.id(), s.self().pos(), s.tick());
		List<Score> scores = CombatPolicy.score(s, om, outcomes, personality, difficulty, quiet);
		Tactic before = decider.current();
		last = decider.decide(s.tick(), scores, personality, difficulty, rng);
		outcomeTactic = last.tactic();
		if (last.changed() && debug != null) {
			debug.accept(String.format(Locale.ROOT, "%s -> %s: %s", name(before), name(last.tactic()), last.reason()));
		}
		long dt = System.nanoTime() - t0;
		thinkNanos += dt;
		thinkMax = Math.max(thinkMax, dt);
		thinks++;
	}

	/** Forget the fight (a new round or match): tactic, tracks and what worked; the personality stays. */
	/**
	 * Another style for the same NPC in the middle of a fight (an admin command): what it saw, what it learned and
	 * how the fight went so far carry over; only the personality changes.
	 */
	public void inherit(CombatMind old) {
		tracks.putAll(old.tracks);
		models.putAll(old.models);
		outcomes.copyFrom(old.outcomes);
		totalDealt = old.totalDealt;
		totalTaken = old.totalTaken;
		swingsMade = old.swingsMade;
		swingsLanded = old.swingsLanded;
		tacticTicks.putAll(old.tacticTicks);
		lastExchange = old.lastExchange;
	}

	public void reset(int tick) {
		decider.reset(tick);
		last = null;
		targetId = null;
		tracks.clear();
		models.clear();
		risingNow.clear();
		outcomes.reset();
		dealt = 0;
		taken = 0;
		totalDealt = 0;
		totalTaken = 0;
		swingsMade = 0;
		swingsLanded = 0;
		tacticTicks.clear();
		lastExchange = Integer.MIN_VALUE;
	}

	public OutcomeMemory outcomes() {
		return outcomes;
	}

	/** Everything worth knowing about this mind right now, for the inspect command and the self-test. */
	public Report report() {
		List<Score> top = last == null ? List.of() : last.ranked().subList(0, Math.min(4, last.ranked().size()));
		OpponentModel om = targetId == null ? null : models.get(targetId);
		return new Report(archetype, difficulty, personality, decider.current(), last == null ? "" : last.reason(), top,
				decider.transitions(), thinks, thinks == 0 ? 0 : thinkNanos / thinks / 1000.0, thinkMax / 1000.0,
				om == null ? null : String.format(Locale.ROOT, "aggression %.2f, likes %.1f blocks, strafe changes %.1f/s, jumps %.1f/s", om.aggression(),
						om.preferredDistance(), om.strafeRate(), om.jumpRate()) + (om.swings() < 3 ? ", swings not seen yet"
								: String.format(Locale.ROOT, ", swings every %.0f ticks, lands %.0f%%", om.swingGap(), om.accuracy() * 100)),
				swingsMade, swingsLanded);
	}

	public record Report(Archetype archetype, Difficulty difficulty, Personality personality, Tactic tactic, String reason, List<Score> top,
			List<Decider.Transition> transitions, int thinks, double avgMicros, double maxMicros, String opponent, int swings, int landed) {
		/** Readable lines for chat or a log. */
		public List<String> lines() {
			List<String> out = new ArrayList<>();
			out.add(String.format(Locale.ROOT, "%s, %s: %s (%s)", archetype.id(), difficulty.id(), name(tactic), reason));
			Personality p = personality;
			out.add(String.format(Locale.ROOT, "aggression %.2f risk %.2f patience %.2f range %.1f adapt %.2f vary %.2f defence %.2f objective %.2f",
					p.aggression(), p.risk(), p.patience(), p.range(), p.adaptability(), p.variability(), p.defensive(), p.objective()));
			for (Score s : top) out.add("  " + s);
			if (opponent != null) out.add("opponent: " + opponent);
			int from = Math.max(0, transitions.size() - 4);
			for (Decider.Transition tr : transitions.subList(from, transitions.size())) {
				out.add(String.format(Locale.ROOT, "  @%d %s -> %s: %s", tr.tick(), name(tr.from()), name(tr.to()), tr.why()));
			}
			out.add(String.format(Locale.ROOT, "%d thinks, %.1f us average, %.1f us max", thinks, avgMicros, maxMicros));
			if (swings > 0) out.add(String.format(Locale.ROOT, "swings %d, landed %d (%.0f%%)", swings, landed, 100.0 * landed / swings));
			return out;
		}
	}

	private static String name(Tactic t) {
		return t.name().toLowerCase(Locale.ROOT);
	}
}
