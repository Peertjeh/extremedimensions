package net.battlehub.core.ml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a learned bot should practise in the simulation ({@code ml/spec/lessons.json}, shipped in
 * {@code assets/battlehub/ml}; train/focus.py reads the same file): the lesson behind each reason a fight left the
 * model's choice ({@link ModelReport.Why}), the lesson behind each observation value (by name or prefix; an
 * observation of an earlier decision, v3's {@code name@ago}, has the lesson of the observation it was), and per lesson
 * the scenario weights it raises in the simulation and whether it needs the training with items.
 */
public final class Lessons {
	private final Map<String, String> reasons = new LinkedHashMap<>();
	private final java.util.Set<String> declared = new java.util.LinkedHashSet<>();
	private final Map<String, String> names = new LinkedHashMap<>();
	private final Map<String, String> prefixes = new LinkedHashMap<>();
	private final Map<String, Map<String, Object>> focus = new LinkedHashMap<>();
	private final Map<String, Boolean> items = new LinkedHashMap<>();

	public static Lessons parse(String json) {
		Lessons out = new Lessons();
		Map<String, Object> d = MiniJson.object(MiniJson.parse(json));
		for (Map.Entry<String, Object> e : MiniJson.object(d.get("reasons")).entrySet()) {
			out.declared.add(e.getKey());
			if (e.getValue() != null) out.reasons.put(e.getKey(), (String) e.getValue());
		}
		for (Object g : MiniJson.array(d.get("features"))) {
			Map<String, Object> m = MiniJson.object(g);
			String lesson = (String) m.get("lesson");
			for (Object n : list(m.get("names"))) out.names.put((String) n, lesson);
			for (Object p : list(m.get("prefixes"))) out.prefixes.put((String) p, lesson);
		}
		for (Map.Entry<String, Object> e : MiniJson.object(d.get("lessons")).entrySet()) {
			Map<String, Object> m = MiniJson.object(e.getValue());
			out.focus.put(e.getKey(), m.get("focus") == null ? Map.of() : MiniJson.object(m.get("focus")));
			out.items.put(e.getKey(), Boolean.TRUE.equals(m.get("items")));
		}
		return out;
	}

	/** The lessons shipped with the mod. */
	public static Lessons shipped() throws IOException {
		InputStream in = Lessons.class.getResourceAsStream("/assets/battlehub/ml/lessons.json");
		if (in == null) throw new IOException("assets/battlehub/ml/lessons.json is missing");
		try (in) {
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private static List<Object> list(Object o) {
		return o == null ? List.of() : MiniJson.array(o);
	}

	/** The lesson behind a reason, or null when there is nothing to learn (it is meant that way). */
	public String ofReason(ModelReport.Why why) {
		return reasons.get(why.id());
	}

	/** Whether the file names this reason (with a lesson, or null for "nothing to learn"). */
	public boolean declares(ModelReport.Why why) {
		return declared.contains(why.id());
	}

	/** The lesson behind an observation value, or null when the file does not know it. */
	public String ofFeature(String name) {
		name = now(name);
		String l = names.get(name);
		if (l != null) return l;
		for (Map.Entry<String, String> e : prefixes.entrySet()) {
			if (name.startsWith(e.getKey())) return e.getValue();
		}
		return null;
	}

	/** How many feature groups claim this name (by name and by prefix): exactly one for every observation. */
	public int claims(String name) {
		name = now(name);
		int n = names.containsKey(name) ? 1 : 0;
		for (String p : prefixes.keySet()) {
			if (name.startsWith(p)) n++;
		}
		return n;
	}

	/** The observation an observation of an earlier decision was ({@code opp_fwd@3}: {@code opp_fwd}). */
	private static String now(String name) {
		int at = name.indexOf('@');
		return at < 0 ? name : name.substring(0, at);
	}

	/** The scenario weights this lesson raises (terrains, families, modes3, modes4 as maps; three, four, chaos as numbers). */
	public Map<String, Object> focus(String lesson) {
		return focus.getOrDefault(lesson, Map.of());
	}

	/** Whether the lesson needs the training with items. */
	public boolean items(String lesson) {
		return items.getOrDefault(lesson, false);
	}

	public boolean known(String lesson) {
		return focus.containsKey(lesson);
	}

	/** Every lesson named by a reason or a feature group. */
	public java.util.Set<String> used() {
		java.util.Set<String> out = new java.util.LinkedHashSet<>(reasons.values());
		out.addAll(names.values());
		out.addAll(prefixes.values());
		return out;
	}
}
