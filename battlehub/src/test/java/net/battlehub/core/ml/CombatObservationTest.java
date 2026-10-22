package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mod computes exactly what the simulation computes: every state of the golden file (made by
 * ml/tools/make_golden.py from the same spec) gives the same observation, every action index the same values.
 */
class CombatObservationTest {
	static CombatSpec spec;
	static Map<String, Object> golden;

	@BeforeAll
	static void load() throws IOException {
		spec = CombatSpec.load(resource("/combat_v1.json"));
		golden = MiniJson.object(MiniJson.parse(new String(resource("/combat_v1_golden.json").readAllBytes(), StandardCharsets.UTF_8)));
	}

	static InputStream resource(String name) throws IOException {
		InputStream in = CombatObservationTest.class.getResourceAsStream(name);
		if (in == null) throw new IOException(name + " is not on the test classpath (it comes from ml/spec)");
		return in;
	}

	@Test
	void theGoldenFileWasMadeFromThisSpec() throws IOException {
		assertEquals(golden.get("spec_sha256"), spec.sha256, "the golden file was made from another spec: run ml/tools/make_golden.py");
		assertEquals(golden.get("observation_names"), spec.obsNames);
		assertEquals(43, spec.obsDim());
		assertArrayEquals(new int[] {3, 3, 2, 2, 2, 9, 5}, spec.actionHeads());
		// the spec shipped in the mod is the same file
		InputStream shipped = CombatObservationTest.class.getResourceAsStream("/assets/battlehub/ml/combat_v1.json");
		if (shipped != null) assertEquals(spec.sha256, CombatSpec.load(shipped).sha256);
	}

	@Test
	void everyObservationIsTheSimulationsToTheMillionth() {
		double tol = ((Number) golden.get("tolerance")).doubleValue();
		List<Object> cases = MiniJson.array(golden.get("cases"));
		assertTrue(cases.size() >= 100);
		for (int c = 0; c < cases.size(); c++) {
			Map<String, Object> cs = MiniJson.object(cases.get(c));
			CombatState s = state(MiniJson.object(cs.get("state")));
			double[] want = MiniJson.doubles(cs.get("observation"));
			double[] got = CombatObservation.scaled(spec, s);
			for (int k = 0; k < want.length; k++) {
				assertEquals(want[k], got[k], tol, "case " + c + ", " + spec.obsNames.get(k));
			}
			// and as floats for the model
			float[] f = CombatObservation.build(spec, s);
			for (int k = 0; k < want.length; k++) assertEquals(want[k], f[k], 1e-6, "case " + c + " (float), " + spec.obsNames.get(k));
		}
	}

	@Test
	void everyActionDecodesToTheSimulationsValues() {
		for (Object o : MiniJson.array(golden.get("actions"))) {
			Map<String, Object> a = MiniJson.object(o);
			double[] idx = MiniJson.doubles(a.get("index"));
			int[] ix = new int[idx.length];
			for (int k = 0; k < ix.length; k++) ix[k] = (int) idx[k];
			Map<String, Object> d = MiniJson.object(a.get("decoded"));
			CombatAction act = CombatAction.decode(spec, ix);
			assertEquals(num(d, "forward"), act.forward());
			assertEquals(num(d, "strafe"), act.strafe());
			assertEquals(num(d, "jump") > 0.5, act.jump());
			assertEquals(num(d, "sprint") > 0.5, act.sprint());
			assertEquals(num(d, "attack") > 0.5, act.attack());
			assertEquals(num(d, "yaw"), act.yawLeft());
			assertEquals(num(d, "pitch"), act.pitchDown());
		}
	}

	@Test
	void theBestAndSampledChoicesComeFromTheirOwnHead() {
		float[] logits = new float[spec.logitCount()];
		// forward: index 2, strafe: 0, jump: 1, sprint: 0, attack: 1, yaw: 7, pitch: 2
		int[] want = {2, 0, 1, 0, 1, 7, 2};
		int o = 0;
		int[] heads = spec.actionHeads();
		for (int h = 0; h < heads.length; h++) {
			logits[o + want[h]] = 5f;
			o += heads[h];
		}
		assertArrayEquals(want, CombatAction.best(spec, logits));
		// a sharp sample takes the same; a hot one does not always
		assertArrayEquals(want, CombatAction.sample(spec, logits, 0.05, new Random(1)));
		boolean differs = false;
		Random rng = new Random(2);
		for (int i = 0; i < 50 && !differs; i++) differs = !java.util.Arrays.equals(want, CombatAction.sample(spec, logits, 5.0, rng));
		assertTrue(differs);
	}

	@Test
	void difficultiesAreReadPerLevel() {
		CombatLevels levels = CombatLevels.parse("{\"beginner\": {\"model\": \"a.onnx\", \"extra_delay\": 4, \"aim_noise\": 2.5, \"turn_cap\": 24, \"sample\": true, \"elo\": 1010},"
				+ " \"master\": {\"model\": \"b.onnx\", \"extra_delay\": 0, \"aim_noise\": 0.2, \"turn_cap\": 60, \"sample\": false}}");
		assertEquals("a.onnx", levels.of("BEGINNER").model());
		assertEquals(4, levels.of("beginner").extraDelay());
		assertTrue(levels.of("beginner").sample());
		assertEquals(60.0, levels.of("master").turnCap());
		assertEquals("a.onnx", levels.of("unknown").model());
	}

	private static int num(Map<String, Object> m, String k) {
		return (int) Math.round(((Number) m.get(k)).doubleValue());
	}

	private static double d(Map<String, Object> m, String k) {
		return ((Number) m.get(k)).doubleValue();
	}

	static CombatState state(Map<String, Object> m) {
		CombatState s = new CombatState();
		s.selfX = d(m, "self_x");
		s.selfY = d(m, "self_y");
		s.selfZ = d(m, "self_z");
		s.selfVx = d(m, "self_vx");
		s.selfVy = d(m, "self_vy");
		s.selfVz = d(m, "self_vz");
		s.selfYaw = d(m, "self_yaw");
		s.selfPitch = d(m, "self_pitch");
		s.selfOnGround = Boolean.TRUE.equals(m.get("self_on_ground"));
		s.selfSprinting = Boolean.TRUE.equals(m.get("self_sprinting"));
		s.selfCharge = d(m, "self_charge");
		s.selfHurtTicks = d(m, "self_hurt_ticks");
		s.selfHealth = d(m, "self_health");
		s.selfMaxHealth = d(m, "self_max_health");
		s.oppX = d(m, "opp_x");
		s.oppY = d(m, "opp_y");
		s.oppZ = d(m, "opp_z");
		s.oppVx = d(m, "opp_vx");
		s.oppVy = d(m, "opp_vy");
		s.oppVz = d(m, "opp_vz");
		s.oppYaw = d(m, "opp_yaw");
		s.oppOnGround = Boolean.TRUE.equals(m.get("opp_on_ground"));
		s.oppSprinting = Boolean.TRUE.equals(m.get("opp_sprinting"));
		s.oppSinceSwing = d(m, "opp_since_swing");
		s.oppHurtTicks = d(m, "opp_hurt_ticks");
		s.oppHealth = d(m, "opp_health");
		s.oppMaxHealth = d(m, "opp_max_health");
		s.oppVisible = Boolean.TRUE.equals(m.get("opp_visible"));
		double[] edges = MiniJson.doubles(m.get("edges")), walls = MiniJson.doubles(m.get("walls"));
		System.arraycopy(edges, 0, s.edges, 0, 8);
		System.arraycopy(walls, 0, s.walls, 0, 8);
		return s;
	}
}
