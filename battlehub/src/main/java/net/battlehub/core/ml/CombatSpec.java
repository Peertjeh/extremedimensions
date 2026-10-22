package net.battlehub.core.ml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The combat spec ({@code ml/spec/combat_v1.json}, shipped as {@code assets/battlehub/ml/combat_v1.json}): what the
 * melee model sees and does. The Python simulation reads the same file; its sha256 travels with every exported model
 * and with the golden test file, so a model or a test made for another spec is noticed.
 */
public final class CombatSpec {
	public final String name;
	public final int version;
	public final String sha256;
	public final int decisionTicks;
	public final double reach;
	public final double eyeHeight;
	public final double chestHeight;
	public final double hitboxWidth;
	public final double hitboxHeight;
	public final double viewHalfAngle;
	public final int rayDirections;
	public final double rayMax;
	public final List<String> obsNames = new ArrayList<>();
	public final double[] obsScale;
	public final double[] obsClip;
	public final List<String> actionNames = new ArrayList<>();
	public final double[][] actionValues;

	private CombatSpec(byte[] raw) {
		this.sha256 = sha256(raw);
		Map<String, Object> d = MiniJson.object(MiniJson.parse(new String(raw, StandardCharsets.UTF_8)));
		name = (String) d.get("name");
		version = ((Number) d.get("version")).intValue();
		decisionTicks = ((Number) d.get("decision_ticks")).intValue();
		reach = num(d, "reach");
		eyeHeight = num(d, "eye_height");
		chestHeight = num(d, "chest_height");
		Map<String, Object> box = MiniJson.object(d.get("hitbox"));
		hitboxWidth = num(box, "width");
		hitboxHeight = num(box, "height");
		viewHalfAngle = num(d, "view_half_angle");
		rayDirections = ((Number) d.get("ray_directions")).intValue();
		rayMax = num(d, "ray_max");
		List<Object> obs = MiniJson.array(d.get("observation"));
		obsScale = new double[obs.size()];
		obsClip = new double[obs.size()];
		for (int k = 0; k < obs.size(); k++) {
			Map<String, Object> o = MiniJson.object(obs.get(k));
			obsNames.add((String) o.get("name"));
			obsScale[k] = num(o, "scale");
			obsClip[k] = num(o, "clip");
		}
		List<Object> acts = MiniJson.array(d.get("actions"));
		actionValues = new double[acts.size()][];
		for (int k = 0; k < acts.size(); k++) {
			Map<String, Object> a = MiniJson.object(acts.get(k));
			actionNames.add((String) a.get("name"));
			actionValues[k] = MiniJson.doubles(a.get("values"));
		}
	}

	public static CombatSpec parse(byte[] raw) {
		return new CombatSpec(raw);
	}

	public static CombatSpec load(InputStream in) throws IOException {
		try (in) {
			return new CombatSpec(in.readAllBytes());
		}
	}

	/** The spec shipped with the mod. */
	public static CombatSpec shipped() throws IOException {
		InputStream in = CombatSpec.class.getResourceAsStream("/assets/battlehub/ml/combat_v1.json");
		if (in == null) throw new IOException("assets/battlehub/ml/combat_v1.json is missing");
		return load(in);
	}

	public int obsDim() {
		return obsNames.size();
	}

	public int index(String obsName) {
		int k = obsNames.indexOf(obsName);
		if (k < 0) throw new IllegalArgumentException("no observation " + obsName);
		return k;
	}

	/** How many choices each action head has (the model's logits are these, one head after the other). */
	public int[] actionHeads() {
		int[] n = new int[actionValues.length];
		for (int k = 0; k < n.length; k++) n[k] = actionValues[k].length;
		return n;
	}

	public int logitCount() {
		int n = 0;
		for (double[] v : actionValues) n += v.length;
		return n;
	}

	private static double num(Map<String, Object> m, String k) {
		return ((Number) m.get(k)).doubleValue();
	}

	static String sha256(byte[] raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
