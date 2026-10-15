package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EffectsTest {
	@Test
	void orbitLooksAtTheCentre() {
		for (double t = 0; t <= 1.0; t += 0.25) {
			Orbit.Pose p = Orbit.at(10, 64, -5, 12, 6, 30, 120, 0, t);
			assertEquals(12, Math.hypot(p.x() - 10, p.z() + 5), 1e-9);
			assertEquals(70, p.y(), 1e-9);
			// Looking along the yaw from the camera reaches the centre
			double yaw = Math.toRadians(p.yaw());
			double dx = -Math.sin(yaw);
			double dz = Math.cos(yaw);
			assertEquals(0, dx * (-5 - p.z()) - dz * (10 - p.x()), 1e-6);
			assertTrue(dx * (10 - p.x()) + dz * (-5 - p.z()) > 0);
			assertTrue(p.pitch() > 0, "looks down at the centre");
		}
	}

	@Test
	void orbitEasesInAndOut() {
		assertEquals(0, Orbit.ease(-1), 1e-9);
		assertEquals(0.5, Orbit.ease(0.5), 1e-9);
		assertEquals(1, Orbit.ease(2), 1e-9);
		assertTrue(Orbit.ease(0.1) < 0.1 && Orbit.ease(0.9) > 0.9);
	}

	@Test
	void introComesCloser() {
		Orbit.Pose start = Orbit.intro(0, 64, 0, 20, 0);
		Orbit.Pose end = Orbit.intro(0, 64, 0, 20, 1);
		assertTrue(Math.hypot(start.x(), start.z()) > Math.hypot(end.x(), end.z()));
		assertTrue(start.y() > end.y() && end.y() > 64);
	}

	@Test
	void dummyDamageFollowsTheCooldown() {
		assertEquals(7.0, DummyHits.damage(DummyHits.SWORD, 1f, false), 1e-9);
		assertEquals(10.5, DummyHits.damage(DummyHits.SWORD, 1f, true), 1e-9);
		assertEquals(1.4, DummyHits.damage(DummyHits.SWORD, 0f, false), 1e-9);
		assertTrue(DummyHits.damage(DummyHits.SWORD, 0.5f, false) < 4);
	}

	@Test
	void critsNeedAFallingFullHit() {
		assertTrue(DummyHits.crit(1f, 0.5, false, false, false, false, false, false));
		assertFalse(DummyHits.crit(0.8f, 0.5, false, false, false, false, false, false));
		assertFalse(DummyHits.crit(1f, 0, false, false, false, false, false, false));
		assertFalse(DummyHits.crit(1f, 0.5, true, false, false, false, false, false));
		assertFalse(DummyHits.crit(1f, 0.5, false, false, false, false, false, true));
	}

	@Test
	void dummyCountsCombosAndClicks() {
		DummyHits d = new DummyHits();
		for (long t : new long[] {100, 110, 120}) d.click(t);
		d.hit(100, 1f, false, 2.9);
		d.hit(110, 1f, true, 3.0);
		d.hit(120, 1f, false, 2.5);
		assertEquals(3, d.combo);
		assertEquals(1, d.crits);
		assertEquals(3, d.hits);
		assertEquals((7 + 10.5 + 7) / 3.0, d.average(), 1e-9);
		assertEquals(2.0, d.cps(120), 1e-9);
		assertEquals(2.0, d.bestCps, 1e-9);
		d.hit(200, 1f, false, 3.0);
		assertEquals(1, d.combo);
		assertEquals(3, d.bestCombo);
		assertTrue(d.comboOver(260));
		assertFalse(d.comboOver(220));
	}

	@Test
	void pixelIconsAreWellFormed() {
		Set<Character> glyphs = new HashSet<>();
		for (var e : PixelIcons.ICONS.entrySet()) {
			String[] rows = e.getValue();
			assertEquals(PixelIcons.SIZE, rows.length, e.getKey());
			boolean any = false;
			for (String r : rows) {
				assertEquals(PixelIcons.SIZE, r.length(), e.getKey() + ": " + r);
				for (char ch : r.toCharArray()) {
					assertTrue(ch == '.' || PixelIcons.COLORS.containsKey(ch), e.getKey() + " uses an unknown colour " + ch);
					any |= ch != '.';
				}
			}
			assertTrue(any);
			char g = PixelIcons.glyph(e.getKey());
			assertNotEquals(0, g);
			assertTrue(glyphs.add(g));
		}
		assertEquals(0, PixelIcons.glyph("nope"));
		assertEquals(0, PixelIcons.pixel(PixelIcons.ICONS.get("coin"), 0, 0));
		assertNotEquals(0, PixelIcons.pixel(PixelIcons.ICONS.get("coin"), 3, 3));
	}

	@Test
	void outfitColours() {
		assertEquals(0xFF0000, CosmeticRules.rgb("#FF0000"));
		assertEquals(0x00AAFF, CosmeticRules.rgb("00aaff"));
		assertEquals(-1, CosmeticRules.rgb("red"));
		assertEquals(-1, CosmeticRules.rgb("#12345"));
		assertEquals(-1, CosmeticRules.rgb("-12345"));
		assertEquals(-1, CosmeticRules.rgb(null));
		assertTrue(CosmeticRules.CATEGORIES.containsAll(CosmeticRules.WARDROBE));
	}
}
