package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FloorPatternTest {
	@Test
	void noiseIsSmoothRepeatableAndInRange() {
		Noise a = new Noise(42);
		Noise b = new Noise(42);
		Noise c = new Noise(43);
		double maxStep = 0;
		boolean differs = false;
		for (int x = -40; x <= 40; x++) {
			for (int z = -40; z <= 40; z++) {
				double v = a.at(x, z, 8, 3);
				assertTrue(v >= 0 && v <= 1, "out of range: " + v);
				assertEquals(v, b.at(x, z, 8, 3), "same seed, same noise");
				differs |= Math.abs(v - c.at(x, z, 8, 3)) > 1e-9;
				maxStep = Math.max(maxStep, Math.abs(v - a.at(x + 1, z, 8, 3)));
			}
		}
		assertTrue(differs, "another seed gives another pattern");
		assertTrue(maxStep < 0.35, "neighbouring blocks are close: " + maxStep);
	}

	@Test
	void everyPatternUsesTheWholeGradient() {
		for (FloorPattern p : FloorPattern.values()) {
			Noise n = new Noise(7);
			Set<Integer> steps = new HashSet<>();
			for (int x = -24; x <= 24; x++) {
				for (int z = -24; z <= 24; z++) {
					double t = p.t(n, x, z, 24, 0.6, 8);
					assertTrue(t >= 0 && t <= 1, p + " out of range: " + t);
					steps.add(FloorPattern.step(t, 5, n.hash(x, z), 0.2));
				}
			}
			assertTrue(steps.size() >= 4, p + " uses only " + steps + " of 5 steps");
		}
	}

	@Test
	void steps() {
		assertEquals(0, FloorPattern.step(0, 5, 0.5, 0));
		assertEquals(4, FloorPattern.step(1, 5, 0.5, 0));
		assertEquals(2, FloorPattern.step(0.5, 5, 0.5, 0));
		assertEquals(0, FloorPattern.step(0.7, 1, 0.5, 0));
		assertEquals(4, FloorPattern.step(0.99, 5, 1.0, 1.0), "jitter never runs past the end");
		assertEquals(0.0, FloorPattern.tri(0));
		assertEquals(1.0, FloorPattern.tri(0.5));
		assertEquals(0.0, FloorPattern.tri(1));
		assertNotEquals(new Noise(1).hash(3, 4), new Noise(1).hash(4, 3));
	}
}
