package net.battlehub.core.ml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lines of a fight log (without Minecraft, so it can be tested on its own; ml/train/fights.py reads them): one
 * JSON object per line, in a gzip file per fight. The first line says what follows (the format, the spec, the names of
 * the state's values and of the action, the match and its fighters); then, every decision (two ticks), one line per
 * fighter with a target: the state it was in (core.ml.CombatState, the simulation's values) and what it did over the
 * decision, in the spec's action terms; for a bot also what its model chose and how the tick went against it; lines
 * for hits; and a last line with the result.
 */
public final class FightLog {
	private FightLog() {}

	public static final String FORMAT = "battlehub-fights";
	public static final int VERSION = 1;
	/** What a fighter did over one decision: keys (-1, 0, 1), jump, sprint, a swing (0, 1), the turn left and down in degrees, the hand, the right button. */
	public static final List<String> ACTIONS = List.of("forward", "strafe", "jump", "sprint", "attack", "yaw", "pitch", "hand", "use");

	/** One fighter of the match: "human" or "bot", a bot's level and model, its team, and (only when names are kept) its name. */
	public record Fighter(String kind, String level, String model, int team, String name) {}

	/** The first line. */
	public static String header(CombatSpec spec, String mod, int match, String mode, String map, String kit, String started, List<Fighter> fighters) {
		Map<String, Object> h = new LinkedHashMap<>();
		h.put("format", FORMAT);
		h.put("version", VERSION);
		h.put("mod", mod);
		h.put("spec", spec.name);
		h.put("spec_version", spec.version);
		h.put("spec_sha256", spec.sha256);
		h.put("decision_ticks", spec.decisionTicks);
		h.put("state_keys", CombatState.KEYS);
		h.put("rays", CombatState.RAYS);
		h.put("ray_length", 8);
		h.put("action_names", ACTIONS);
		h.put("hands", spec.hands);
		h.put("match", match);
		h.put("mode", mode);
		h.put("map", map);
		h.put("kit", kit);
		h.put("started", started);
		List<Object> fs = new ArrayList<>();
		for (int i = 0; i < fighters.size(); i++) {
			Fighter f = fighters.get(i);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("i", i);
			m.put("kind", f.kind());
			if (f.level() != null) m.put("level", f.level());
			if (f.model() != null) m.put("model", f.model());
			m.put("team", f.team());
			if (f.name() != null) m.put("name", f.name());
			fs.add(m);
		}
		h.put("fighters", fs);
		return MiniJson.write(h);
	}

	/**
	 * One fighter's decision: its state, what it did, and for a bot what its model chose (its action heads; null for
	 * none) and how the tick went against it ("kept", a reason, or null). {@code obs}, when given, is written too (the
	 * sample file the tests compare).
	 */
	public static String row(int tick, int fighter, int target, CombatState s, double[] action, int[] model, String reason, float[] obs) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("t", tick);
		r.put("f", fighter);
		r.put("o", target);
		double[] v = s.values();
		for (int k = 0; k < v.length; k++) v[k] = round(v[k]);
		r.put("s", v);
		double[] a = action.clone();
		for (int k = 0; k < a.length; k++) a[k] = round(a[k]);
		r.put("a", a);
		if (model != null) r.put("m", model);
		if (reason != null && !reason.isEmpty()) r.put("r", reason);
		if (obs != null) {
			double[] o = new double[obs.length];
			for (int k = 0; k < o.length; k++) o[k] = Math.round(obs[k] * 1e6) / 1e6;
			r.put("obs", o);
		}
		return MiniJson.write(r);
	}

	/** A hit: who (-1 when not a fighter of the match) hit whom, for how much health. */
	public static String hit(int tick, int attacker, int victim, double damage) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("t", tick);
		r.put("hit", new int[] {attacker, victim});
		r.put("dmg", round(damage));
		return MiniJson.write(r);
	}

	/** The last line: how long it went, who won, and why it ended ("done", or "stopped" without a result). */
	public static String end(int ticks, List<Integer> winners, String reason) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("end", true);
		r.put("t", ticks);
		r.put("winners", winners);
		r.put("reason", reason);
		return MiniJson.write(r);
	}

	/** An action in {@link #ACTIONS} order. */
	public static double[] action(int forward, int strafe, boolean jump, boolean sprint, boolean attack, double yawLeft, double pitchDown, int hand,
			boolean use) {
		return new double[] {forward, strafe, jump ? 1 : 0, sprint ? 1 : 0, attack ? 1 : 0, yawLeft, pitchDown, hand, use ? 1 : 0};
	}

	/** The turn to the left from one Minecraft yaw to another (turning left lowers the yaw), within -180..180. */
	public static double turnLeft(double fromYaw, double toYaw) {
		double d = (fromYaw - toYaw) % 360.0;
		if (d > 180) d -= 360;
		if (d < -180) d += 360;
		return d;
	}

	/** Three decimals: enough for every value of the state, a third of the size. */
	static double round(double v) {
		return Math.round(v * 1000.0) / 1000.0;
	}
}
