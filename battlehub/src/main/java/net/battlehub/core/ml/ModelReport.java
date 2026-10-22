package net.battlehub.core.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.battlehub.core.ai.Tactic;

/**
 * How closely a bot's fights kept to its learned model, and what the model met that it never saw in training. Every
 * tick of a fight is one of three:
 * <ul>
 * <li>kept: the model chose, and that is what the bot did;</li>
 * <li>changed: the model chose, but the bot's own rules changed it (it would walk off an edge or jump into the void,
 * it was steered back in the air, it was stuck, it ate, it broke the objective instead of swinging);</li>
 * <li>replaced: the scripted mind fought instead (away from an edge, dodging, healing, the bow, the objective,
 * searching, still reacting).</li>
 * </ul>
 * Every decision of the model also checks which of its values lie outside what the simulation produced
 * ({@link ObsRanges}). {@link #lessons} turns both into what to practise in the simulation ({@link Lessons}).
 */
public final class ModelReport {
	/** Why a tick did not go as the model chose. */
	public enum Why {
		EDGE, JUMP, AIR, STUCK, RECOVER, EVADE, HEAL, SHOOT, OBJECTIVE, SEARCH, REACT, OTHER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The reason when the scripted mind fights with this tactic instead of the model. */
		public static Why of(Tactic t) {
			return switch (t) {
				case RECOVER -> RECOVER;
				case EVADE -> EVADE;
				case HEAL -> HEAL;
				case SHOOT -> SHOOT;
				case OBJECTIVE -> OBJECTIVE;
				case SEARCH -> SEARCH;
				default -> OTHER;
			};
		}

		public static Why parse(String id) {
			for (Why w : values()) {
				if (w.id().equals(id)) return w;
			}
			return null;
		}
	}

	public record Reason(Why why, double share) {}

	public record Feature(String name, double share) {}

	public record Lesson(String id, double weight, boolean items) {}

	private int matches, ticks, kept, changed, replaced, decisions;
	private final int[] why = new int[Why.values().length];
	/** Decisions with this value outside the training range. */
	private final Map<String, Integer> outside = new LinkedHashMap<>();
	/** Decisions with any value of this lesson's group outside the training range. */
	private final Map<String, Integer> outsideLessons = new LinkedHashMap<>();

	/** A tick on which the model chose and the bot did that. */
	public void kept() {
		ticks++;
		kept++;
	}

	/** A tick on which the model chose but the bot's own rules changed it. */
	public void changed(Why w) {
		ticks++;
		changed++;
		why[w.ordinal()]++;
	}

	/** A tick on which the scripted mind fought instead of the model. */
	public void replaced(Why w) {
		ticks++;
		replaced++;
		why[w.ordinal()]++;
	}

	/** One decision of the model on this observation (as it takes it): which values were new to it. */
	public void decision(CombatSpec spec, float[] obs, ObsRanges ranges, Lessons lessons) {
		decisions++;
		if (ranges == null) return;
		Set<String> groups = new LinkedHashSet<>();
		for (int k = 0; k < obs.length && k < ranges.size(); k++) {
			if (!ranges.outside(k, obs[k])) continue;
			String name = spec.obsNames.get(k);
			outside.merge(name, 1, Integer::sum);
			String lesson = lessons == null ? null : lessons.ofFeature(name);
			if (lesson != null) groups.add(lesson);
		}
		for (String g : groups) outsideLessons.merge(g, 1, Integer::sum);
	}

	/** A finished match counted into a total. */
	public void match() {
		matches++;
	}

	/** Adds another report (a total over bots and matches). */
	public void add(ModelReport o) {
		matches += o.matches;
		ticks += o.ticks;
		kept += o.kept;
		changed += o.changed;
		replaced += o.replaced;
		decisions += o.decisions;
		for (int k = 0; k < why.length; k++) why[k] += o.why[k];
		o.outside.forEach((k, v) -> outside.merge(k, v, Integer::sum));
		o.outsideLessons.forEach((k, v) -> outsideLessons.merge(k, v, Integer::sum));
	}

	public int matches() {
		return matches;
	}

	public int ticks() {
		return ticks;
	}

	public int decisions() {
		return decisions;
	}

	public int count(Why w) {
		return why[w.ordinal()];
	}

	public double keptShare() {
		return share(kept, ticks);
	}

	public double changedShare() {
		return share(changed, ticks);
	}

	public double replacedShare() {
		return share(replaced, ticks);
	}

	/** Why the fight left the model, as shares of all its ticks, biggest first. */
	public List<Reason> reasons() {
		List<Reason> out = new ArrayList<>();
		for (Why w : Why.values()) {
			if (why[w.ordinal()] > 0) out.add(new Reason(w, share(why[w.ordinal()], ticks)));
		}
		out.sort(Comparator.comparingDouble(Reason::share).reversed());
		return out;
	}

	/** The values most often new to the model, as shares of its decisions, biggest first. */
	public List<Feature> outsideTop(int n) {
		List<Feature> out = new ArrayList<>();
		outside.forEach((k, v) -> out.add(new Feature(k, share(v, decisions))));
		out.sort(Comparator.comparingDouble(Feature::share).reversed());
		return out.subList(0, Math.min(n, out.size()));
	}

	/**
	 * What to practise in the simulation, biggest first: each lesson weighs the share of the fight ticks its reasons
	 * explain plus the share of the decisions on which values of its group were new to the model; only lessons of at
	 * least {@code min}, at most {@code n}.
	 */
	public List<Lesson> lessons(Lessons l, double min, int n) {
		Map<String, Double> w = new LinkedHashMap<>();
		for (Reason r : reasons()) {
			String id = l.ofReason(r.why());
			if (id != null) w.merge(id, r.share(), Double::sum);
		}
		outsideLessons.forEach((id, v) -> w.merge(id, share(v, decisions), Double::sum));
		List<Lesson> out = new ArrayList<>();
		w.forEach((id, v) -> {
			if (v >= min) out.add(new Lesson(id, v, l.items(id)));
		});
		out.sort(Comparator.comparingDouble(Lesson::weight).reversed());
		return out.subList(0, Math.min(n, out.size()));
	}

	private static double share(int part, int all) {
		return all == 0 ? 0 : part / (double) all;
	}

	/** The counts as plain maps and numbers (for the feedback file). */
	public Map<String, Object> toMap() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("matches", matches);
		m.put("ticks", ticks);
		m.put("kept", kept);
		m.put("changed", changed);
		m.put("replaced", replaced);
		m.put("decisions", decisions);
		Map<String, Object> ws = new LinkedHashMap<>();
		for (Why w : Why.values()) {
			if (why[w.ordinal()] > 0) ws.put(w.id(), why[w.ordinal()]);
		}
		m.put("why", ws);
		m.put("outside", new LinkedHashMap<>(outside));
		m.put("outside_lessons", new LinkedHashMap<>(outsideLessons));
		return m;
	}

	/** The counts back from {@link #toMap()} (numbers may come back as doubles). */
	public static ModelReport fromMap(Map<String, Object> m) {
		ModelReport r = new ModelReport();
		r.matches = num(m.get("matches"));
		r.ticks = num(m.get("ticks"));
		r.kept = num(m.get("kept"));
		r.changed = num(m.get("changed"));
		r.replaced = num(m.get("replaced"));
		r.decisions = num(m.get("decisions"));
		if (m.get("why") instanceof Map<?, ?> ws) {
			ws.forEach((k, v) -> {
				Why w = Why.parse(String.valueOf(k));
				if (w != null) r.why[w.ordinal()] = num(v);
			});
		}
		if (m.get("outside") instanceof Map<?, ?> o) o.forEach((k, v) -> r.outside.put(String.valueOf(k), num(v)));
		if (m.get("outside_lessons") instanceof Map<?, ?> o) o.forEach((k, v) -> r.outsideLessons.put(String.valueOf(k), num(v)));
		return r;
	}

	private static int num(Object o) {
		return o instanceof Number n ? n.intValue() : 0;
	}
}
