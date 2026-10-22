package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Spec v2: the mod computes exactly what the v2 simulation computes. Every state of the golden file (made by
 * ml/tools/make_golden2.py) gives the same observation, every action index the same values (with the hand and the right
 * button), and on every small map of the file the terrain rays come out the same.
 */
class CombatObservationV2Test {
	static CombatSpec spec;
	static Map<String, Object> golden;

	@BeforeAll
	static void load() throws IOException {
		spec = CombatSpec.load(CombatObservationTest.resource("/combat_v2.json"));
		golden = MiniJson.object(MiniJson.parse(new String(CombatObservationTest.resource("/combat_v2_golden.json").readAllBytes(), StandardCharsets.UTF_8)));
	}

	@Test
	void theGoldenFileWasMadeFromThisSpec() {
		assertEquals(golden.get("spec_sha256"), spec.sha256, "the golden file was made from another spec: run ml/tools/make_golden2.py");
		assertEquals(golden.get("observation_names"), spec.obsNames);
		assertEquals(2, spec.version);
		assertArrayEquals(new int[] {3, 3, 2, 2, 2, 11, 7, 6, 2}, spec.actionHeads());
		assertEquals(List.of("weapon", "bow", "food", "gapple", "pearl", "wind"), spec.hands);
		assertEquals(0.25, spec.raySample);
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
			for (int k = 0; k < want.length; k++) assertEquals(want[k], got[k], tol, "case " + c + ", " + spec.obsNames.get(k));
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
			assertEquals(d(d, "yaw"), act.yawLeft());
			assertEquals(d(d, "pitch"), act.pitchDown());
			assertEquals(num(d, "hand"), act.hand());
			assertEquals(num(d, "use") > 0.5, act.use());
		}
	}

	@Test
	void theTerrainRaysAreTheSimulations() {
		List<Object> maps = MiniJson.array(golden.get("terrain"));
		assertTrue(maps.size() >= 3);
		for (int m = 0; m < maps.size(); m++) {
			Map<String, Object> map = MiniJson.object(maps.get(m));
			List<Object> rows = MiniJson.array(map.get("ground"));
			double[][] ground = new double[rows.size()][];
			for (int i = 0; i < ground.length; i++) {
				List<Object> row = MiniJson.array(rows.get(i));
				ground[i] = new double[row.size()];
				for (int j = 0; j < row.size(); j++) ground[i][j] = row.get(j) == null ? Double.NaN : ((Number) row.get(j)).doubleValue();
			}
			TerrainRays.Columns cols = TerrainRays.heightmap(ground);
			List<Object> qs = MiniJson.array(map.get("queries"));
			for (int q = 0; q < qs.size(); q++) {
				Map<String, Object> e = MiniJson.object(qs.get(q));
				double x = d(e, "x"), y = d(e, "y"), z = d(e, "z");
				double[][] rays = TerrainRays.rays(spec, cols, x, y, z, d(e, "yaw"));
				String where = "map " + m + ", query " + q;
				assertArrayEquals(MiniJson.doubles(e.get("voids")), rays[0], 1e-9, where + " voids");
				assertArrayEquals(MiniJson.doubles(e.get("drops")), rays[1], 1e-9, where + " drops");
				assertArrayEquals(MiniJson.doubles(e.get("steps")), rays[2], 1e-9, where + " steps");
				assertArrayEquals(MiniJson.doubles(e.get("walls")), rays[3], 1e-9, where + " walls");
				assertEquals(d(e, "below"), TerrainRays.below(spec, cols, x, y, z), 1e-9, where + " below");
				double[] behind = TerrainRays.behind(spec, cols, x, y, z, d(e, "dx"), d(e, "dz"));
				assertEquals(d(e, "behind_drop"), behind[0], 1e-9, where + " behind drop");
				assertEquals(d(e, "behind_void"), behind[1], 1e-9, where + " behind void");
			}
		}
	}

	private static int num(Map<String, Object> m, String k) {
		return (int) Math.round(((Number) m.get(k)).doubleValue());
	}

	private static double d(Map<String, Object> m, String k) {
		return ((Number) m.get(k)).doubleValue();
	}

	private static boolean b(Map<String, Object> m, String k) {
		return Boolean.TRUE.equals(m.get(k));
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
		s.selfOnGround = b(m, "self_on_ground");
		s.selfSprinting = b(m, "self_sprinting");
		s.selfCharge = d(m, "self_charge");
		s.selfHurtTicks = d(m, "self_hurt_ticks");
		s.selfHealth = d(m, "self_health");
		s.selfMaxHealth = d(m, "self_max_health");
		s.selfAbsorption = d(m, "self_absorption");
		s.selfFood = d(m, "self_food");
		s.selfSaturation = d(m, "self_saturation");
		s.selfRegen = d(m, "self_regen");
		s.selfFall = d(m, "self_fall");
		s.selfScale = d(m, "self_scale");
		s.selfReach = d(m, "self_reach");
		s.selfMeleeDamage = d(m, "self_melee_damage");
		s.selfMeleeBonus = d(m, "self_melee_bonus");
		s.selfCooldown = d(m, "self_cooldown");
		s.selfKb = d(m, "self_kb");
		s.selfArmor = d(m, "self_armor");
		s.selfToughness = d(m, "self_toughness");
		s.selfResistance = d(m, "self_resistance");
		s.selfWalkSpeed = d(m, "self_walk_speed");
		s.selfJumpPower = d(m, "self_jump_power");
		s.selfGravity = d(m, "self_gravity");
		s.selfArrows = d(m, "self_arrows");
		s.selfFoodItems = d(m, "self_food_items");
		s.selfGapples = d(m, "self_gapples");
		s.selfPearls = d(m, "self_pearls");
		s.selfWinds = d(m, "self_winds");
		s.selfShield = b(m, "self_shield");
		s.selfPearlCd = d(m, "self_pearl_cd");
		s.selfWindCd = d(m, "self_wind_cd");
		s.selfShieldCd = d(m, "self_shield_cd");
		s.selfHand = num(m, "self_hand");
		s.selfUseTicks = d(m, "self_use_ticks");
		s.selfBlocking = b(m, "self_blocking");
		s.oppX = d(m, "opp_x");
		s.oppY = d(m, "opp_y");
		s.oppZ = d(m, "opp_z");
		s.oppVx = d(m, "opp_vx");
		s.oppVy = d(m, "opp_vy");
		s.oppVz = d(m, "opp_vz");
		s.oppYaw = d(m, "opp_yaw");
		s.oppOnGround = b(m, "opp_on_ground");
		s.oppSprinting = b(m, "opp_sprinting");
		s.oppSinceSwing = d(m, "opp_since_swing");
		s.oppHurtTicks = d(m, "opp_hurt_ticks");
		s.oppHealth = d(m, "opp_health");
		s.oppMaxHealth = d(m, "opp_max_health");
		s.oppAbsorption = d(m, "opp_absorption");
		s.oppVisible = b(m, "opp_visible");
		s.oppBlocking = b(m, "opp_blocking");
		s.oppDraw = d(m, "opp_draw");
		s.oppEating = b(m, "opp_eating");
		s.oppHoldsBow = b(m, "opp_holds_bow");
		s.oppHoldsFood = b(m, "opp_holds_food");
		s.oppHoldsThrowable = b(m, "opp_holds_throwable");
		s.oppMeleeDamage = d(m, "opp_melee_damage");
		s.oppMeleeBonus = d(m, "opp_melee_bonus");
		s.oppCooldown = d(m, "opp_cooldown");
		s.oppKb = d(m, "opp_kb");
		s.oppArmor = d(m, "opp_armor");
		s.oppToughness = d(m, "opp_toughness");
		s.oppResistance = d(m, "opp_resistance");
		s.oppScale = d(m, "opp_scale");
		s.e2Present = b(m, "e2_present");
		s.e2X = d(m, "e2_x");
		s.e2Y = d(m, "e2_y");
		s.e2Z = d(m, "e2_z");
		s.e2Yaw = d(m, "e2_yaw");
		s.e2Visible = b(m, "e2_visible");
		s.e2SinceSwing = d(m, "e2_since_swing");
		s.e2HoldsBow = b(m, "e2_holds_bow");
		s.threatPresent = b(m, "threat_present");
		s.threatX = d(m, "threat_x");
		s.threatY = d(m, "threat_y");
		s.threatZ = d(m, "threat_z");
		s.threatVx = d(m, "threat_vx");
		s.threatVy = d(m, "threat_vy");
		s.threatVz = d(m, "threat_vz");
		s.damageOn = b(m, "damage_on");
		s.enemiesNear = d(m, "enemies_near");
		s.selfBelow = d(m, "self_below");
		s.oppBelow = d(m, "opp_below");
		s.oppDropBehind = d(m, "opp_drop_behind");
		s.oppVoidBehind = d(m, "opp_void_behind");
		System.arraycopy(MiniJson.doubles(m.get("voids")), 0, s.voids, 0, 8);
		System.arraycopy(MiniJson.doubles(m.get("drops")), 0, s.drops, 0, 8);
		System.arraycopy(MiniJson.doubles(m.get("steps")), 0, s.steps, 0, 8);
		System.arraycopy(MiniJson.doubles(m.get("walls")), 0, s.walls, 0, 8);
		return s;
	}
}
