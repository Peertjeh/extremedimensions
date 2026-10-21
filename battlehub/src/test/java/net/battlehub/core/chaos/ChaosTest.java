package net.battlehub.core.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ChaosTest {
	@Test
	void atLeastAHundredAndFiftyValidEvents() {
		assertEquals(List.of(), ChaosCatalog.problems());
		assertTrue(ChaosCatalog.all().size() >= 150, ChaosCatalog.all().size() + " events");
		for (String f : ChaosCatalog.FIXED) assertTrue(ChaosCatalog.has(f), f);
		assertTrue(ChaosCatalog.attributes().contains("minecraft:scale"));
	}

	@Test
	void everyEventHasATitleAndSubtitleInBothLanguages() throws Exception {
		for (String lang : List.of("en_us", "nl_nl")) {
			String text = Files.readString(Path.of("src/main/resources/assets/battlehub/lang/" + lang + ".json"));
			for (ChaosEvent e : ChaosCatalog.all()) {
				assertTrue(text.contains("\"" + e.langKey() + "\":"), lang + " " + e.langKey());
				assertTrue(text.contains("\"" + e.langKey() + ".sub\":"), lang + " " + e.langKey() + ".sub");
			}
		}
	}

	@Test
	void aPlanHasTwentyRandomEventsAndTheFiveFixedOnesEarly() {
		for (long seed = 0; seed < 300; seed++) {
			ChaosPlan plan = ChaosPlan.create(seed, 20);
			List<String> order = plan.order();
			assertEquals(25, order.size());
			assertEquals(25, new HashSet<>(order).size(), "no event twice");
			for (String f : ChaosCatalog.FIXED) {
				int at = order.indexOf(f);
				assertTrue(at >= 0 && at < ChaosPlan.FIXED_WITHIN, f + " at " + at + " (seed " + seed + ")");
			}
		}
		assertEquals(ChaosPlan.create(42, 20).order(), ChaosPlan.create(42, 20).order(), "the same seed, the same plan");
		assertEquals(35, ChaosPlan.create(1, 30).order().size());
		assertEquals(25, ChaosPlan.create(1, 3).order().size() + 17, "fewer than twenty when asked for fewer");
	}

	@Test
	void kindsAreMixed() {
		int same = 0, pairs = 0;
		for (long seed = 0; seed < 300; seed++) {
			List<String> order = ChaosPlan.create(seed, 20).order();
			for (int i = 1; i < order.size(); i++) {
				pairs++;
				if (ChaosCatalog.get(order.get(i)).category() == ChaosCatalog.get(order.get(i - 1)).category()) same++;
			}
		}
		assertTrue(same * 20 < pairs, same + " of " + pairs + " neighbours of the same kind");
	}

	@Test
	void rareEventsComeLessOften() {
		Map<Integer, Integer> drawn = new HashMap<>();
		Map<Integer, Integer> events = new HashMap<>();
		for (ChaosEvent e : ChaosCatalog.all()) {
			if (!ChaosCatalog.FIXED.contains(e.id())) events.merge(e.weight(), 1, Integer::sum);
		}
		for (long seed = 0; seed < 2000; seed++) {
			for (String id : ChaosPlan.create(seed, 20).order()) {
				if (!ChaosCatalog.FIXED.contains(id)) drawn.merge(ChaosCatalog.get(id).weight(), 1, Integer::sum);
			}
		}
		double rare = drawn.get(1) / (double) events.get(1);
		double common = drawn.get(3) / (double) events.get(3);
		assertTrue(rare * 1.8 < common, "weight 1: " + rare + " per event, weight 3: " + common);
	}

	@Test
	void afterThePlanTheSameEventsComeAgainTwiceAsBadThenFourTimes() {
		ChaosPlan plan = ChaosPlan.create(7, 20);
		List<String> order = plan.order();
		for (int round = 0; round < 4; round++) {
			for (int i = 0; i < order.size(); i++) {
				ChaosPlan.Turn t = plan.nextTurn();
				assertEquals(order.get(i), t.id(), "round " + round + ", turn " + i);
				assertEquals(round, t.level());
				assertEquals(1 << round, plan.factor());
			}
		}
		assertEquals(100, plan.count());
		assertEquals(new HashSet<>(order), plan.played(), "only the planned events, again and again");
	}

	@Test
	void twiceAsBadMeansLongerStrongerMoreAndBigger() {
		assertEquals(List.of(1, 2, 4, 8), List.of(ChaosIntensity.factor(0), ChaosIntensity.factor(1), ChaosIntensity.factor(2), ChaosIntensity.factor(3)));
		// longer, but never past the limit, and instant stays instant
		assertEquals(10, ChaosIntensity.seconds(10, 0));
		assertEquals(20, ChaosIntensity.seconds(10, 1));
		assertEquals(30, ChaosIntensity.seconds(10, 5));
		assertEquals(ChaosIntensity.MAX_SECONDS, ChaosIntensity.seconds(20, 3));
		assertEquals(0, ChaosIntensity.seconds(0, 4));
		// stronger effects, more items (up to a stack), better enchantments (up to five)
		assertEquals(1, ChaosIntensity.amplifier(1, 0));
		assertEquals(3, ChaosIntensity.amplifier(1, 2));
		assertEquals(ChaosIntensity.MAX_AMPLIFIER, ChaosIntensity.amplifier(2, 9));
		assertEquals(32, ChaosIntensity.count(16, 1, 64));
		assertEquals(64, ChaosIntensity.count(16, 4, 64));
		assertEquals(2, ChaosIntensity.count(1, 1, 64));
		assertEquals(5, ChaosIntensity.enchantment(2, 7));
		// a body change twice as big, but a body that can still play
		assertEquals(-0.9, ChaosIntensity.attribute("minecraft:scale", "total", -0.5, 1), 1e-9);
		assertEquals(1.2, ChaosIntensity.attribute("minecraft:scale", "total", 0.6, 1), 1e-9);
		assertEquals(-0.95, ChaosIntensity.attribute("minecraft:gravity", "total", -0.7, 1), 1e-9);
		assertEquals(-0.8, ChaosIntensity.attribute("minecraft:max_health", "total", -0.5, 2), 1e-9);
		assertEquals(4, ChaosIntensity.attribute("minecraft:attack_knockback", "add", 2, 1), 1e-9);
		assertEquals(-1, ChaosIntensity.attribute("minecraft:movement_speed", "total", -1, 3), 1e-9);
		// the match's own doings: more of them, harder, more often
		assertEquals(6, ChaosIntensity.times(3, 1, 24));
		assertEquals(24, ChaosIntensity.times(3, 5, 24));
		assertEquals(1.875, ChaosIntensity.push(1.25, 1, 3), 1e-9);
		assertEquals(3, ChaosIntensity.push(1.25, 9, 3), 1e-9);
		assertEquals(12, ChaosIntensity.every(25, 1, 5));
		assertEquals(5, ChaosIntensity.every(25, 6, 5));
		assertEquals(5.0 / 9, ChaosIntensity.chance(1 / 3.0, 1), 1e-9);
	}

	@Test
	void everyEventCanBeMadeWorseWithinTheLimits() {
		for (ChaosEvent e : ChaosCatalog.all()) {
			for (int level = 0; level <= 6; level++) {
				int secs = ChaosIntensity.seconds(e.seconds(), level);
				assertTrue(secs >= e.seconds() && secs <= Math.max(e.seconds(), ChaosIntensity.MAX_SECONDS), e.id());
				for (ChaosEvent.Action a : e.actions()) {
					if (a.verb().equals("attr")) {
						double v = ChaosIntensity.attribute(a.arg(0), a.arg(1), Double.parseDouble(a.arg(2)), level);
						if (!a.arg(1).equals("add")) assertTrue(v >= -1 && v <= 4, e.id() + " level " + level + ": " + v);
						if (a.arg(0).equals("minecraft:scale")) assertTrue(1 + v >= 0.1 - 1e-9, e.id() + " shrinks to nothing");
						if (a.arg(0).equals("minecraft:gravity")) assertTrue(1 + v > 0, e.id() + " turns gravity round");
					}
				}
			}
		}
	}
}
