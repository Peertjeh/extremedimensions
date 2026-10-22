package net.battlehub.core.ml;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which model plays at which difficulty, and what is added on top ({@code difficulties.json}, written by
 * ml/train/league.py from the Elo ratings): a perception delay of the opponent, aim noise per tick, the most it turns
 * in a decision, and whether it samples its actions (lower levels) or takes the best.
 */
public final class CombatLevels {
	public record Level(String name, String model, int extraDelay, double aimNoise, double turnCap, boolean sample, int elo) {}

	private final Map<String, Level> levels = new LinkedHashMap<>();

	public static CombatLevels parse(String json) {
		CombatLevels out = new CombatLevels();
		for (Map.Entry<String, Object> e : MiniJson.object(MiniJson.parse(json)).entrySet()) {
			Map<String, Object> m = MiniJson.object(e.getValue());
			out.levels.put(e.getKey(), new Level(e.getKey(), (String) m.get("model"), ((Number) m.get("extra_delay")).intValue(),
					((Number) m.get("aim_noise")).doubleValue(), ((Number) m.get("turn_cap")).doubleValue(), Boolean.TRUE.equals(m.get("sample")),
					m.get("elo") instanceof Number n ? n.intValue() : 0));
		}
		return out;
	}

	/** The level for a difficulty id ("beginner" .. "master"), or the nearest one there is. */
	public Level of(String difficulty) {
		Level l = levels.get(difficulty.toLowerCase(Locale.ROOT));
		if (l != null) return l;
		return levels.isEmpty() ? null : levels.values().iterator().next();
	}

	public Map<String, Level> all() {
		return levels;
	}
}
