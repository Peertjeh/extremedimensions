package net.battlehub.core.ml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which choices of each action head a fighter may make ({@code ml/spec/combat_v2_masks.json}, the rules
 * ml/pvp_sim2/masks.py applies in training), from its observation alone: a choice listed in the file is allowed when
 * any of its alternatives holds (all conditions of it), a choice not listed always is. The model's choice skips what
 * is not allowed (CombatAction.best and sample with a mask), so a bot never picks something it does not carry.
 */
public final class CombatMasks {
	private record Cond(int obs, int op, double value) {
		boolean holds(float[] o) {
			double v = o[obs];
			return switch (op) {
				case 0 -> v > value;
				case 1 -> v < value;
				case 2 -> v >= value;
				default -> v <= value;
			};
		}
	}

	private record Rule(int flat, Cond[][] any) {
		boolean allows(float[] o) {
			for (Cond[] alt : any) {
				boolean all = true;
				for (Cond c : alt) {
					if (!c.holds(o)) {
						all = false;
						break;
					}
				}
				if (all) return true;
			}
			return false;
		}
	}

	private static final List<String> OPS = List.of(">", "<", ">=", "<=");

	private final int size;
	private final Rule[] rules;

	private CombatMasks(int size, Rule[] rules) {
		this.size = size;
		this.rules = rules;
	}

	/** The masks of a file made for this spec (its sha256). */
	public static CombatMasks parse(String json, CombatSpec spec) {
		Map<String, Object> d = MiniJson.object(MiniJson.parse(json));
		if (!spec.sha256.equals(d.get("spec_sha256"))) {
			throw new IllegalArgumentException("the masks were made for another spec (" + d.get("spec_sha256") + ", not " + spec.sha256 + ")");
		}
		int[] heads = spec.actionHeads();
		int[] offset = new int[heads.length];
		for (int h = 1; h < heads.length; h++) offset[h] = offset[h - 1] + heads[h - 1];
		List<Rule> rules = new ArrayList<>();
		for (Object o : MiniJson.array(d.get("rules"))) {
			Map<String, Object> r = MiniJson.object(o);
			int head = spec.actionNames.indexOf((String) r.get("head"));
			int choice = ((Number) r.get("choice")).intValue();
			if (head < 0 || choice < 0 || choice >= heads[head]) throw new IllegalArgumentException("no choice " + choice + " of " + r.get("head"));
			List<Object> alts = MiniJson.array(r.get("any"));
			Cond[][] any = new Cond[alts.size()][];
			for (int a = 0; a < any.length; a++) {
				List<Object> conds = MiniJson.array(alts.get(a));
				any[a] = new Cond[conds.size()];
				for (int c = 0; c < conds.size(); c++) {
					List<Object> t = MiniJson.array(conds.get(c));
					int op = OPS.indexOf((String) t.get(1));
					if (op < 0) throw new IllegalArgumentException("unknown comparison " + t.get(1));
					any[a][c] = new Cond(spec.index((String) t.get(0)), op, ((Number) t.get(2)).doubleValue());
				}
			}
			rules.add(new Rule(offset[head] + choice, any));
		}
		return new CombatMasks(spec.logitCount(), rules.toArray(new Rule[0]));
	}

	/** The masks shipped with the mod for a spec file ("combat_v2.json" -> "combat_v2_masks.json"), or null when it has none (v1). */
	public static CombatMasks shipped(String specFile, CombatSpec spec) throws IOException {
		String file = specFile.replace(".json", "_masks.json");
		InputStream in = CombatMasks.class.getResourceAsStream("/assets/battlehub/ml/" + file);
		if (in == null) return null;
		try (in) {
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), spec);
		}
	}

	/** Per logit (the heads one after the other) whether that choice is allowed for this observation. */
	public boolean[] mask(float[] obs) {
		boolean[] out = new boolean[size];
		java.util.Arrays.fill(out, true);
		for (Rule r : rules) out[r.flat] = r.allows(obs);
		return out;
	}

	public int size() {
		return size;
	}
}
