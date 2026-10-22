package net.battlehub.core.ml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * What the model saw in training ({@code ml/spec/combat_v*_ranges.json}, written by ml/tools/make_ranges.py): per
 * observation value the range the simulation produced, scaled and clipped as the model takes it. A value outside it by
 * more than the file's tolerance is new to the model: there it guesses.
 */
public final class ObsRanges {
	private final double[] lo, hi;
	private final boolean[] known;
	private final double tolerance;

	private ObsRanges(int n, double tolerance) {
		lo = new double[n];
		hi = new double[n];
		known = new boolean[n];
		this.tolerance = tolerance;
	}

	/** The ranges for this spec, in its order; the file must have been made for it (its sha256). */
	public static ObsRanges parse(String json, CombatSpec spec) {
		Map<String, Object> d = MiniJson.object(MiniJson.parse(json));
		if (!spec.sha256.equals(d.get("spec_sha256"))) {
			throw new IllegalArgumentException("the ranges were made for another spec (" + d.get("spec_sha256") + ", not " + spec.sha256 + ")");
		}
		ObsRanges out = new ObsRanges(spec.obsDim(), ((Number) d.get("tolerance")).doubleValue());
		Map<String, Object> r = MiniJson.object(d.get("ranges"));
		for (int k = 0; k < spec.obsDim(); k++) {
			Object v = r.get(spec.obsNames.get(k));
			if (v == null) continue;
			double[] ab = MiniJson.doubles(v);
			out.lo[k] = ab[0];
			out.hi[k] = ab[1];
			out.known[k] = true;
		}
		return out;
	}

	/** The ranges shipped with the mod for a spec file ("combat_v2.json" -> "combat_v2_ranges.json"). */
	public static ObsRanges shipped(String specFile, CombatSpec spec) throws IOException {
		String file = specFile.replace(".json", "_ranges.json");
		InputStream in = ObsRanges.class.getResourceAsStream("/assets/battlehub/ml/" + file);
		if (in == null) throw new IOException("assets/battlehub/ml/" + file + " is missing");
		try (in) {
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), spec);
		}
	}

	/** Whether observation value k (as the model takes it) lies outside what it saw in training. */
	public boolean outside(int k, double v) {
		return known[k] && (v < lo[k] - tolerance || v > hi[k] + tolerance);
	}

	public int size() {
		return known.length;
	}
}
