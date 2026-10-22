package net.battlehub.core.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.battlehub.core.ai.Tactic;

/**
 * The model report: how the ticks of a fight split into kept, changed and replaced, which values were new to the model
 * (against the ranges ml/tools/make_ranges.py wrote), and what that says to practise (ml/spec/lessons.json, the same
 * file train/focus.py reads).
 */
class ModelReportTest {
	static CombatSpec v1, v2;
	static Lessons lessons;
	static ObsRanges ranges2;

	@BeforeAll
	static void load() throws IOException {
		v1 = CombatSpec.load(CombatObservationTest.resource("/combat_v1.json"));
		v2 = CombatSpec.load(CombatObservationTest.resource("/combat_v2.json"));
		lessons = Lessons.parse(text("/lessons.json"));
		ranges2 = ObsRanges.parse(text("/combat_v2_ranges.json"), v2);
	}

	static String text(String name) throws IOException {
		return new String(CombatObservationTest.resource(name).readAllBytes(), StandardCharsets.UTF_8);
	}

	@Test
	void everyObservationOfEverySpecBelongsToExactlyOneLesson() {
		for (CombatSpec spec : List.of(v1, v2)) {
			for (String name : spec.obsNames) {
				assertEquals(1, lessons.claims(name), name + " (spec " + spec.version + ") must be in exactly one feature group of lessons.json");
				assertNotNull(lessons.ofFeature(name));
			}
		}
	}

	@Test
	void everyReasonIsDeclaredAndEveryLessonNamedExists() {
		for (ModelReport.Why w : ModelReport.Why.values()) assertTrue(lessons.declares(w), w.id() + " is missing from lessons.json reasons");
		for (String id : lessons.used()) assertTrue(lessons.known(id), "lesson " + id + " is used but not described");
		assertEquals("edges", lessons.ofReason(ModelReport.Why.EDGE));
		assertNull(lessons.ofReason(ModelReport.Why.REACT), "reacting is meant that way: nothing to learn");
		assertTrue(lessons.items("eating"));
		assertFalse(lessons.items("edges"));
	}

	@Test
	void theRangesWereMadeForThisSpecAndAreOrdered() throws IOException {
		Map<String, Object> d = MiniJson.object(MiniJson.parse(text("/combat_v2_ranges.json")));
		assertEquals(v2.sha256, d.get("spec_sha256"), "run ml/tools/make_ranges.py");
		Map<String, Object> r = MiniJson.object(d.get("ranges"));
		assertEquals(v2.obsNames, List.copyOf(r.keySet()));
		for (Object v : r.values()) {
			double[] ab = MiniJson.doubles(v);
			assertTrue(ab[0] <= ab[1]);
		}
		ObsRanges.parse(text("/combat_v1_ranges.json"), v1);
		assertThrows(IllegalArgumentException.class, () -> ObsRanges.parse(text("/combat_v1_ranges.json"), v2));
	}

	@Test
	void ticksSplitIntoKeptChangedAndReplaced() {
		ModelReport r = new ModelReport();
		for (int i = 0; i < 70; i++) r.kept();
		for (int i = 0; i < 20; i++) r.changed(ModelReport.Why.EDGE);
		for (int i = 0; i < 6; i++) r.replaced(ModelReport.Why.of(Tactic.HEAL));
		for (int i = 0; i < 4; i++) r.replaced(ModelReport.Why.of(Tactic.IDLE));
		assertEquals(100, r.ticks());
		assertEquals(0.70, r.keptShare(), 1e-9);
		assertEquals(0.20, r.changedShare(), 1e-9);
		assertEquals(0.10, r.replacedShare(), 1e-9);
		List<ModelReport.Reason> why = r.reasons();
		assertEquals(ModelReport.Why.EDGE, why.get(0).why());
		assertEquals(ModelReport.Why.HEAL, why.get(1).why());
		assertEquals(ModelReport.Why.OTHER, why.get(2).why());
		assertEquals(0.20, why.get(0).share(), 1e-9);
	}

	@Test
	void valuesTheSimulationNeverProducedAreNewToTheModel() {
		CombatState st = new CombatState();
		st.oppX = 3;
		st.oppVisible = true;
		st.oppHealth = st.oppMaxHealth = st.selfHealth = st.selfMaxHealth = 20;
		st.selfFood = 20;
		st.selfWalkSpeed = 0.1;
		st.selfJumpPower = 0.42;
		st.selfGravity = 0.08;
		st.selfScale = st.oppScale = 1;
		st.selfReach = 3;
		st.selfCooldown = st.oppCooldown = 12.5;
		java.util.Arrays.fill(st.voids, 4);
		java.util.Arrays.fill(st.drops, 4);
		java.util.Arrays.fill(st.steps, 4);
		java.util.Arrays.fill(st.walls, 4);
		ModelReport r = new ModelReport();
		r.decision(v2, CombatObservation.build(v2, st), ranges2, lessons);
		assertEquals(List.of(), r.outsideTop(5), "an ordinary duel is nothing new");
		// creative reach, and slowness beyond any kit or Chaos event of the simulation
		st.selfReach = 5;
		r.decision(v2, CombatObservation.build(v2, st), ranges2, lessons);
		st.selfReach = 3;
		st.selfWalkSpeed = 0.1 * 0.5;
		r.decision(v2, CombatObservation.build(v2, st), ranges2, lessons);
		assertEquals(3, r.decisions());
		List<ModelReport.Feature> top = r.outsideTop(5);
		assertEquals(2, top.size());
		assertEquals(1 / 3.0, top.get(0).share(), 1e-9);
		List<ModelReport.Lesson> l = r.lessons(lessons, 0.03, 3);
		assertEquals("effects", l.get(0).id(), "reach and slowness are effects: practise with Chaos");
		assertEquals(2 / 3.0, l.get(0).weight(), 1e-9, "one count per decision for the group, not per value");
	}

	@Test
	void lessonsWeighReasonsAndNewValuesTogether() {
		ModelReport r = new ModelReport();
		for (int i = 0; i < 80; i++) r.kept();
		for (int i = 0; i < 10; i++) r.changed(ModelReport.Why.EDGE);
		for (int i = 0; i < 5; i++) r.changed(ModelReport.Why.JUMP);
		for (int i = 0; i < 3; i++) r.replaced(ModelReport.Why.HEAL);
		for (int i = 0; i < 2; i++) r.replaced(ModelReport.Why.REACT);
		List<ModelReport.Lesson> l = r.lessons(lessons, 0.03, 3);
		assertEquals(2, l.size(), "reacting teaches nothing; 2% is below the bar");
		assertEquals("edges", l.get(0).id());
		assertEquals(0.15, l.get(0).weight(), 1e-9);
		assertEquals("eating", l.get(1).id());
		assertTrue(l.get(1).items());
		assertEquals(1, r.lessons(lessons, 0.0, 1).size());
	}

	@Test
	void reportsAddUpAndSurviveTheFeedbackFile() {
		ModelReport a = new ModelReport(), b = new ModelReport();
		a.kept();
		a.changed(ModelReport.Why.AIR);
		a.match();
		b.replaced(ModelReport.Why.SEARCH);
		b.match();
		CombatState st = new CombatState();
		st.selfReach = 6;
		st.selfScale = st.oppScale = 1;
		b.decision(v2, CombatObservation.build(v2, st), ranges2, lessons);
		ModelReport all = new ModelReport();
		all.add(a);
		all.add(b);
		assertEquals(2, all.matches());
		assertEquals(3, all.ticks());
		assertEquals(1, all.count(ModelReport.Why.AIR));
		// as the feedback file stores it: numbers come back as doubles
		Map<String, Object> m = MiniJson.object(MiniJson.parse(json(all.toMap())));
		ModelReport back = ModelReport.fromMap(m);
		assertEquals(all.toMap(), back.toMap());
		assertEquals(all.outsideTop(9), back.outsideTop(9));
	}

	/** A tiny writer for the test (the mod writes the file with Gson). */
	private static String json(Object o) {
		if (o instanceof Map<?, ?> m) {
			StringBuilder sb = new StringBuilder("{");
			m.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":").append(json(v)));
			return sb.append('}').toString();
		}
		return String.valueOf(o);
	}
}
