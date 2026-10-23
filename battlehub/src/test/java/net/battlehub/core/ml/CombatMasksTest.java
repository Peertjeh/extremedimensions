package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The action masks are the simulation's: every observation of ml/spec/combat_v2_masks_golden.json (written by
 * ml/tools/make_golden2.py from the same rules) gives the same mask, and the same best allowed choice for its logits.
 */
class CombatMasksTest {
	static CombatSpec spec;
	static CombatMasks masks;
	static String rules;
	static Map<String, Object> golden;

	@BeforeAll
	static void load() throws IOException {
		spec = CombatSpec.load(CombatObservationTest.resource("/combat_v2.json"));
		rules = new String(CombatObservationTest.resource("/combat_v2_masks.json").readAllBytes(), StandardCharsets.UTF_8);
		masks = CombatMasks.parse(rules, spec);
		golden = MiniJson.object(MiniJson.parse(new String(CombatObservationTest.resource("/combat_v2_masks_golden.json").readAllBytes(),
				StandardCharsets.UTF_8)));
	}

	@Test
	void theGoldenFileWasMadeFromTheseRules() throws IOException {
		assertEquals(spec.sha256, golden.get("spec_sha256"));
		assertEquals(CombatSpec.sha256(rules.getBytes(StandardCharsets.UTF_8)), golden.get("masks_sha256"),
				"the golden masks were made from other rules: run ml/tools/make_golden2.py");
		assertEquals(38, masks.size());
		// the rules shipped in the mod are the same file; v1 has none
		CombatMasks shipped = CombatMasks.shipped("combat_v2.json", spec);
		if (CombatMasksTest.class.getResource("/assets/battlehub/ml/combat_v2_masks.json") != null) assertNotNull(shipped);
		assertNull(CombatMasks.shipped("combat_v1.json", CombatSpec.load(CombatObservationTest.resource("/combat_v1.json"))));
	}

	@Test
	void everyMaskAndBestChoiceIsTheSimulations() {
		List<Object> cases = MiniJson.array(golden.get("cases"));
		assertTrue(cases.size() > 200);
		int differs = 0;
		for (int c = 0; c < cases.size(); c++) {
			Map<String, Object> cs = MiniJson.object(cases.get(c));
			float[] obs = floats(MiniJson.doubles(cs.get("observation")));
			String want = (String) cs.get("mask");
			boolean[] got = masks.mask(obs);
			StringBuilder sb = new StringBuilder();
			for (boolean b : got) sb.append(b ? '1' : '0');
			assertEquals(want, sb.toString(), "case " + c);
			float[] logits = floats(MiniJson.doubles(cs.get("logits")));
			int[] best = CombatAction.best(spec, logits, got);
			int[] expect = MiniJson.array(cs.get("best")).stream().mapToInt(o -> ((Number) o).intValue()).toArray();
			assertArrayEquals(expect, best, "case " + c);
			if (!java.util.Arrays.equals(best, CombatAction.best(spec, logits))) differs++;
		}
		// the masks matter in these cases: without them the model would often pick something it does not carry
		assertTrue(differs > 20, "masked and unmasked choices differ in only " + differs + " cases");
	}

	@Test
	void sampledChoicesAreAllowedOnesAndTheWeaponAlwaysIs() {
		Random rng = new Random(3);
		int hand = spec.actionNames.indexOf("hand");
		int use = spec.actionNames.indexOf("use");
		int[] heads = spec.actionHeads();
		int handAt = 0;
		for (int h = 0; h < hand; h++) handAt += heads[h];
		float[] obs = new float[spec.obsDim()];
		obs[spec.index("self_food")] = 1f;
		// nothing carried: only the weapon and not using
		boolean[] m = masks.mask(obs);
		for (int k = 1; k < 6; k++) assertTrue(!m[handAt + k], spec.hands.get(k));
		assertTrue(m[handAt] && m[handAt + 6] && !m[handAt + 7]);
		for (int i = 0; i < 2000; i++) {
			float[] logits = new float[spec.logitCount()];
			for (int k = 0; k < logits.length; k++) logits[k] = (float) (rng.nextGaussian() * 3);
			int[] pick = CombatAction.sample(spec, logits, 1.0, rng, m);
			assertEquals(0, pick[hand]);
			assertEquals(0, pick[use]);
		}
		// food at full hunger stays off, a golden apple is fine
		obs[spec.index("self_food_items")] = 0.5f;
		obs[spec.index("self_gapples")] = 0.25f;
		m = masks.mask(obs);
		assertTrue(!m[handAt + 2] && m[handAt + 3] && m[handAt + 7]);
		obs[spec.index("self_food")] = 0.5f;
		assertTrue(masks.mask(obs)[handAt + 2]);
	}

	@Test
	void rulesForAnotherSpecAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> CombatMasks.parse(rules.replace(spec.sha256, "0".repeat(64)), spec));
	}

	private static float[] floats(double[] d) {
		float[] f = new float[d.length];
		for (int k = 0; k < d.length; k++) f[k] = (float) d[k];
		return f;
	}
}
