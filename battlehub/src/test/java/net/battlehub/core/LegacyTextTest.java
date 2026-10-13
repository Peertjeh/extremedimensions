package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LegacyTextTest {
	@Test
	void coloursAndFormats() {
		List<LegacyText.Segment> s = LegacyText.parse("&aGreen &l&cRed bold&r plain");
		assertEquals(3, s.size());
		assertEquals("Green ", s.get(0).text());
		assertEquals(0x55FF55, s.get(0).color());
		assertEquals("Red bold", s.get(1).text());
		assertEquals(0xFF5555, s.get(1).color());
		assertFalse(s.get(1).bold(), "a colour code resets formatting, like vanilla");
		assertEquals(-1, s.get(2).color());
	}

	@Test
	void boldAfterColour() {
		List<LegacyText.Segment> s = LegacyText.parse("&6&lBattleHub");
		assertEquals(1, s.size());
		assertTrue(s.get(0).bold());
		assertEquals(0xFFAA00, s.get(0).color());
	}

	@Test
	void hexAndPlaceholders() {
		List<LegacyText.Segment> s = LegacyText.parse("&#ff8800{0} vs {1}!");
		assertEquals(4, s.size());
		assertEquals(0, s.get(0).arg());
		assertEquals(0xFF8800, s.get(0).color());
		assertEquals(" vs ", s.get(1).text());
		assertEquals(1, s.get(2).arg());
		assertEquals("!", s.get(3).text());
	}

	@Test
	void literalBracesAndAmpersands() {
		assertEquals("a & b {x} {", LegacyText.strip("a & b {x} {"));
		assertEquals("Tom & Jerry", LegacyText.strip("&eTom & Jerry"));
	}
}
