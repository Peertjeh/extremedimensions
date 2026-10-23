package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The fight logs: a state stored as the simulation's values comes back the same, the lines are what ml/train/fights.py
 * reads, and the sample file both sides check (ml/spec/fightlog_sample.jsonl: states of the golden file with the
 * observation the mod computes; the Python test computes it again from the stored state) is what this code writes. To
 * write it again: run this test with -Dbattlehub.writeFightSample=ml/spec/fightlog_sample.jsonl.
 */
class FightLogTest {
	static CombatSpec spec;
	static List<Object> cases;

	@BeforeAll
	static void load() throws IOException {
		spec = CombatSpec.load(CombatObservationTest.resource("/combat_v2.json"));
		Map<String, Object> golden = MiniJson.object(MiniJson.parse(new String(CombatObservationTest.resource("/combat_v2_golden.json").readAllBytes(),
				StandardCharsets.UTF_8)));
		cases = MiniJson.array(golden.get("cases"));
	}

	@Test
	void theKeysAreTheSimulationsAndAStateComesBackTheSame() {
		Map<String, Object> st = MiniJson.object(MiniJson.object(cases.get(0)).get("state"));
		assertEquals(st.keySet(), new java.util.HashSet<>(CombatState.KEYS), "the golden file's state keys");
		assertEquals(CombatState.KEYS.size() - 4 + 4 * 8, CombatState.SIZE);
		for (Object c : cases) {
			CombatState s = CombatObservationV2Test.state(MiniJson.object(MiniJson.object(c).get("state")));
			double[] v = s.values();
			assertEquals(CombatState.SIZE, v.length);
			assertArrayEquals(v, CombatState.of(v).values());
			assertArrayEquals(CombatObservation.scaled(spec, s), CombatObservation.scaled(spec, CombatState.of(v)), 1e-12);
		}
	}

	@Test
	void linesAreOneJsonObjectEach() {
		CombatState s = CombatObservationV2Test.state(MiniJson.object(MiniJson.object(cases.get(3)).get("state")));
		String row = FightLog.row(40, 1, 0, s, FightLog.action(1, -1, true, false, true, 12.5, -3.25, 2, false), new int[] {2, 0, 1, 1, 1, 5, 3, 0, 0},
				"edge", null);
		Map<String, Object> r = MiniJson.object(MiniJson.parse(row));
		assertEquals(40.0, r.get("t"));
		assertEquals(CombatState.SIZE, MiniJson.doubles(r.get("s")).length);
		assertArrayEquals(new double[] {1, -1, 1, 0, 1, 12.5, -3.25, 2, 0}, MiniJson.doubles(r.get("a")));
		assertEquals("edge", r.get("r"));
		assertEquals(9, MiniJson.doubles(r.get("m")).length);
		Map<String, Object> e = MiniJson.object(MiniJson.parse(FightLog.end(1200, List.of(1), "done")));
		assertEquals(Boolean.TRUE, e.get("end"));
		Map<String, Object> h = MiniJson.object(MiniJson.parse(FightLog.hit(41, 1, 0, 4.123456)));
		assertEquals(4.123, h.get("dmg"));
		assertEquals("\"a\\\"b\\\\c\"", MiniJson.write("a\"b\\c"));
	}

	@Test
	void turningLeftLowersTheYawAndWrapsRound() {
		assertEquals(10, FightLog.turnLeft(0, -10), 1e-9);
		assertEquals(-20, FightLog.turnLeft(170, -170), 1e-9);
		assertEquals(20, FightLog.turnLeft(-170, 170), 1e-9);
	}

	@Test
	void theSampleFileIsWhatThisCodeWrites() throws IOException {
		List<String> lines = sample();
		String out = System.getProperty("battlehub.writeFightSample");
		if (out != null) Files.write(Path.of(out), lines, StandardCharsets.UTF_8);
		InputStream in = FightLogTest.class.getResourceAsStream("/fightlog_sample.jsonl");
		assertTrue(in != null, "ml/spec/fightlog_sample.jsonl is missing: write it with -Dbattlehub.writeFightSample");
		List<String> have = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		assertEquals(lines, have, "the sample file is out of date: write it again (see the class comment)");
	}

	/** A short fight of a person and a master bot over twenty states of the golden file. */
	static List<String> sample() {
		List<String> out = new ArrayList<>();
		out.add(FightLog.header(spec, "test", 1, "axe", "castle", "axe", "2026-10-23T00:00:00Z",
				List.of(new FightLog.Fighter("human", null, null, 0, null), new FightLog.Fighter("bot", "master", "v2", 1, null))));
		for (int i = 0; i < 20; i++) {
			CombatState s = CombatObservationV2Test.state(MiniJson.object(MiniJson.object(cases.get(i)).get("state")));
			double[] v = s.values();
			for (int k = 0; k < v.length; k++) v[k] = FightLog.round(v[k]);
			CombatState stored = CombatState.of(v);
			boolean bot = i % 2 == 1;
			double[] a = FightLog.action(i % 3 - 1, (i / 3) % 3 - 1, i % 2 == 0, i % 3 == 0, i % 4 == 0, (i * 7 % 60) - 30, (i * 5 % 30) - 15.5, i % 6,
					i % 5 == 0);
			out.add(FightLog.row(2 * i, bot ? 1 : 0, bot ? 0 : 1, stored, a, bot ? new int[] {i % 3, 1, 0, 1, i % 2, i % 11, i % 7, 0, 0} : null,
					bot ? (i % 4 == 1 ? "kept" : "edge") : null, CombatObservation.build(spec, stored)));
			if (i == 10) out.add(FightLog.hit(2 * i, 1, 0, 6.5));
		}
		out.add(FightLog.end(40, List.of(1), "done"));
		return out;
	}
}
