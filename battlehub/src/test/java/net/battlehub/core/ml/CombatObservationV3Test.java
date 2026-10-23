package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Spec v3: the mod computes exactly what the v3 simulation computes. Every state of the golden file (made by
 * ml/tools/make_golden3.py, with a team mate, a third enemy and memory) gives the same observation, its history taken
 * from the observations of the cases it names (or the values now at a fight's start); v2's observations stay in their
 * place; the masks of v3 are the simulation's; and the history keeps the last decisions as the simulation does.
 */
class CombatObservationV3Test {
	static CombatSpec spec;
	static CombatSpec v2;
	static Map<String, Object> golden;

	@BeforeAll
	static void load() throws IOException {
		spec = CombatSpec.load(CombatObservationTest.resource("/combat_v3.json"));
		v2 = CombatSpec.load(CombatObservationTest.resource("/combat_v2.json"));
		golden = MiniJson.object(MiniJson.parse(new String(CombatObservationTest.resource("/combat_v3_golden.json").readAllBytes(), StandardCharsets.UTF_8)));
	}

	@Test
	void theGoldenFileWasMadeFromThisSpecAndV2StaysInPlace() {
		assertEquals(golden.get("spec_sha256"), spec.sha256, "the golden file was made from another spec: run ml/tools/make_golden3.py");
		assertEquals(golden.get("observation_names"), spec.obsNames);
		assertEquals(3, spec.version);
		assertEquals(169, spec.obsDim());
		assertEquals(v2.obsNames, spec.obsNames.subList(0, v2.obsDim()));
		assertArrayEquals(v2.actionHeads(), spec.actionHeads());
		assertEquals(3, spec.maxAgo);
		assertEquals(2, spec.pause);
		assertEquals(6.0, spec.nearAllies);
		int k = spec.index("opp_fwd@3");
		assertEquals(spec.index("opp_fwd"), spec.historyOf[k]);
		assertEquals(3, spec.historyAgo[k]);
		assertTrue(!spec.isHistory(spec.index("ally_fwd")) && v2.maxAgo == 0);
	}

	@Test
	void everyObservationIsTheSimulationsToTheMillionthWithItsHistory() {
		double tol = ((Number) golden.get("tolerance")).doubleValue();
		List<Object> cases = MiniJson.array(golden.get("cases"));
		assertTrue(cases.size() >= 100);
		int withPast = 0;
		for (int c = 0; c < cases.size(); c++) {
			Map<String, Object> cs = MiniJson.object(cases.get(c));
			CombatState s = state(MiniJson.object(cs.get("state")));
			double[][] past = null;
			if (cs.get("past") != null) {
				List<Object> refs = MiniJson.array(cs.get("past"));
				past = new double[refs.size()][];
				for (int a = 0; a < past.length; a++) {
					past[a] = MiniJson.doubles(MiniJson.object(cases.get(((Number) refs.get(a)).intValue())).get("observation"));
				}
				withPast++;
			}
			double[] want = MiniJson.doubles(cs.get("observation"));
			double[] got = CombatObservation.scaled(spec, s, past);
			for (int k = 0; k < want.length; k++) assertEquals(want[k], got[k], tol, "case " + c + ", " + spec.obsNames.get(k));
			// as floats, the way the model takes it
			float[][] pf = null;
			if (past != null) {
				pf = new float[past.length][];
				for (int a = 0; a < past.length; a++) pf[a] = floats(past[a]);
			}
			float[] f = CombatObservation.build(spec, s, pf);
			for (int k = 0; k < want.length; k++) assertEquals(want[k], f[k], 1e-6, "case " + c + ", " + spec.obsNames.get(k) + " (float)");
		}
		assertTrue(withPast > 50 && withPast < cases.size());
	}

	@Test
	void v3sMasksAreTheSimulations() throws IOException {
		String rules = new String(CombatObservationTest.resource("/combat_v3_masks.json").readAllBytes(), StandardCharsets.UTF_8);
		CombatMasks masks = CombatMasks.parse(rules, spec);
		Map<String, Object> mg = MiniJson.object(MiniJson.parse(new String(CombatObservationTest.resource("/combat_v3_masks_golden.json").readAllBytes(),
				StandardCharsets.UTF_8)));
		assertEquals(CombatSpec.sha256(rules.getBytes(StandardCharsets.UTF_8)), mg.get("masks_sha256"), "run ml/tools/make_golden3.py");
		List<Object> cases = MiniJson.array(mg.get("cases"));
		assertTrue(cases.size() > 200);
		for (int c = 0; c < cases.size(); c++) {
			Map<String, Object> cs = MiniJson.object(cases.get(c));
			boolean[] got = masks.mask(floats(MiniJson.doubles(cs.get("observation"))));
			StringBuilder sb = new StringBuilder();
			for (boolean b : got) sb.append(b ? '1' : '0');
			assertEquals(cs.get("mask"), sb.toString(), "case " + c);
			int[] best = CombatAction.best(spec, floats(MiniJson.doubles(cs.get("logits"))), got);
			assertArrayEquals(MiniJson.array(cs.get("best")).stream().mapToInt(o -> ((Number) o).intValue()).toArray(), best, "case " + c);
		}
	}

	@Test
	void theHistoryKeepsTheLastDecisionsAndStartsAgainAfterAPause() {
		ObsHistory h = new ObsHistory(spec);
		assertNull(h.past(10));
		float[][] seen = new float[6][];
		for (int d = 0; d < 6; d++) {
			long tick = 10 + 2L * d;
			float[][] past = h.past(tick);
			if (d == 0) assertNull(past);
			else {
				assertEquals(3, past.length);
				assertSame(seen[d - 1], past[0]);
				// the first observation stands for the decisions before it, as in the simulation
				assertSame(seen[Math.max(0, d - 3)], past[2]);
			}
			seen[d] = new float[] {d};
			h.add(tick, seen[d]);
		}
		// one decision left to the scripted mind (4 ticks), two (6): the history goes on; three (8): a new fight
		assertSame(seen[5], h.past(20 + 6)[0]);
		assertNull(h.past(20 + 8));
		h.add(30, seen[0]);
		assertSame(seen[0], h.past(32)[2]);
		h.clear();
		assertNull(h.past(34));
		assertNull(new ObsHistory(v2).past(1));
	}

	static CombatState state(Map<String, Object> m) {
		CombatState s = CombatObservationV2Test.state(m);
		s.allyPresent = b(m, "ally_present");
		s.allyX = d(m, "ally_x");
		s.allyY = d(m, "ally_y");
		s.allyZ = d(m, "ally_z");
		s.allyVisible = b(m, "ally_visible");
		s.allyHealth = d(m, "ally_health");
		s.allyMaxHealth = d(m, "ally_max_health");
		s.alliesNear = d(m, "allies_near");
		s.e3Present = b(m, "e3_present");
		s.e3X = d(m, "e3_x");
		s.e3Y = d(m, "e3_y");
		s.e3Z = d(m, "e3_z");
		s.e3Yaw = d(m, "e3_yaw");
		s.e3Visible = b(m, "e3_visible");
		s.e3SinceSwing = d(m, "e3_since_swing");
		s.e3HoldsBow = b(m, "e3_holds_bow");
		s.oppUnseen = d(m, "opp_unseen");
		s.selfSinceHit = d(m, "self_since_hit");
		s.oppSinceHit = d(m, "opp_since_hit");
		return s;
	}

	private static double d(Map<String, Object> m, String k) {
		return ((Number) m.get(k)).doubleValue();
	}

	private static boolean b(Map<String, Object> m, String k) {
		return Boolean.TRUE.equals(m.get(k));
	}

	private static float[] floats(double[] d) {
		float[] f = new float[d.length];
		for (int k = 0; k < d.length; k++) f[k] = (float) d[k];
		return f;
	}
}
