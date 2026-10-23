package net.battlehub.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.battlehub.core.ml.CombatAction;
import net.battlehub.core.ml.CombatLevels;
import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.MiniJson;

/**
 * The shipped models run in ONNX Runtime for Java and give the logits PyTorch gave when they were exported (the
 * .check.json files the export writes); every level of difficulties.json has its model, made for the shipped spec.
 */
class CombatModelsTest {
	static byte[] bytes(String name) throws Exception {
		try (InputStream in = CombatModelsTest.class.getResourceAsStream(name)) {
			assertNotNull(in, name + " is missing");
			return in.readAllBytes();
		}
	}

	@Test
	void everyLevelHasAModelThatGivesWhatPyTorchGave() throws Exception {
		check(CombatSpec.shipped(), "difficulties.json");
	}

	@Test
	void everyLevelOfModelV2HasAModelThatGivesWhatPyTorchGave() throws Exception {
		check(CombatSpec.shipped("combat_v2.json"), "difficulties_v2.json");
	}

	static void check(CombatSpec spec, String levelsFile) throws Exception {
		CombatLevels levels = CombatLevels.parse(new String(bytes("/assets/battlehub/ml/" + levelsFile), StandardCharsets.UTF_8));
		assertEquals(6, levels.all().size());
		for (CombatLevels.Level lv : levels.all().values()) {
			CombatModels.Model m = CombatModels.load(lv.model(), bytes("/assets/battlehub/ml/" + lv.model()), spec);
			assertEquals(spec.sha256, m.meta.get("spec_sha256"));
			Map<String, Object> check = MiniJson.object(MiniJson.parse(new String(bytes("/net/battlehub/ml/" + lv.model() + ".check.json"), StandardCharsets.UTF_8)));
			double tol = ((Number) check.get("tolerance")).doubleValue();
			List<Object> obs = MiniJson.array(check.get("obs"));
			List<Object> want = MiniJson.array(check.get("logits"));
			for (int i = 0; i < obs.size(); i++) {
				double[] o = MiniJson.doubles(obs.get(i));
				float[] in = new float[o.length];
				for (int k = 0; k < in.length; k++) in[k] = (float) o[k];
				float[] got = m.logits(in);
				double[] w = MiniJson.doubles(want.get(i));
				assertEquals(spec.logitCount(), got.length);
				for (int k = 0; k < w.length; k++) assertEquals(w[k], got[k], tol, lv.name() + " case " + i + " logit " + k);
				// a decision comes out of it
				assertNotNull(CombatAction.decode(spec, CombatAction.best(spec, got)));
			}
			// the hands a model learned (its "items" mark) are hands of the spec, never the weapon
			for (String h : m.items(spec)) assertTrue(spec.hands.contains(h) && !h.equals("weapon"), lv.name() + " learned " + h);
			m.session.close();
		}
	}

	@Test
	void aModelForAnotherSpecIsRefused() throws Exception {
		CombatSpec spec = CombatSpec.shipped();
		byte[] raw = bytes("/assets/battlehub/ml/combat_v1.json");
		byte[] other = new String(raw, StandardCharsets.UTF_8).replace("\"version\": 1", "\"version\": 2").getBytes(StandardCharsets.UTF_8);
		CombatSpec changed = CombatSpec.parse(other);
		byte[] model = bytes("/assets/battlehub/ml/combat_v1_master.onnx");
		assertNotNull(CombatModels.load("combat_v1_master.onnx", model, spec));
		assertThrows(IllegalStateException.class, () -> CombatModels.load("combat_v1_master.onnx", model, changed));
	}
}
