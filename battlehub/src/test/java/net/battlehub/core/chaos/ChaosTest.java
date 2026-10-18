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
	void aHundredValidEvents() {
		assertEquals(List.of(), ChaosCatalog.problems());
		assertEquals(100, ChaosCatalog.all().size());
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
	void aLongMatchGoesOnWithNewEventsBeforeRepeating() {
		ChaosPlan plan = ChaosPlan.create(7, 20);
		List<String> seen = new ArrayList<>();
		for (int i = 0; i < 100; i++) seen.add(plan.next());
		assertEquals(100, new HashSet<>(seen).size(), "all hundred before anything comes back");
		assertEquals(plan.order(), seen.subList(0, 25));
		Set<String> next = new HashSet<>();
		for (int i = 0; i < 100; i++) next.add(plan.next());
		assertEquals(100, next.size());
		assertEquals(200, plan.count());
	}
}
