package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The balloon's string, badges, gadget cooldowns and kill messages (1.44). */
class CosmeticExtrasTest {
	@Test
	void aBalloonStaysOnItsStringAndTrailsBehind() {
		double[] at = {0, 3, 0};
		double px = 0;
		for (int t = 0; t < 400; t++) {
			px += t < 200 ? 0.28 : 0; // running east, then standing
			double[] hand = Balloons.hand(px, 0, 0, -90);
			at = Balloons.step(at, hand, t);
			double d = Math.sqrt(Math.pow(at[0] - hand[0], 2) + Math.pow(at[1] - hand[1], 2) + Math.pow(at[2] - hand[2], 2));
			assertTrue(d <= Balloons.STRING + 1e-9, "on its string at tick " + t + ": " + d);
			if (t == 199) assertTrue(at[0] < hand[0] - 0.3, "it trails behind while running");
			if (t > 300) assertTrue(at[1] > hand[1] + 1.5, "and floats up when standing");
		}
	}

	@Test
	void badges() {
		Badges.Badge b = Badges.parse("★ #FFD700");
		assertNotNull(b);
		assertEquals("★", b.sign());
		assertEquals(0xFFD700, b.color(0, 1));
		Badges.Badge fade = Badges.parse("✦✦✦ #FF0000>#0000FF");
		assertNotNull(fade);
		assertEquals(0xFF0000, fade.color(0, 3));
		assertEquals(0x0000FF, fade.color(2, 3));
		assertNull(Badges.parse("★"));
		assertNull(Badges.parse("★★★★ #FFFFFF"));
		assertNull(Badges.parse("★ gold"));
		assertNull(Badges.parse(null));
	}

	@Test
	void gadgetCooldowns() {
		assertTrue(Gadgets.COOLDOWN.size() >= 12);
		assertEquals(0, Gadgets.ticksLeft("confetti", -1, 100));
		assertEquals(6 * 20, Gadgets.ticksLeft("confetti", 100, 100));
		assertEquals(20, Gadgets.ticksLeft("confetti", 100, 200));
		assertEquals(0, Gadgets.ticksLeft("confetti", 100, 220));
		for (int cd : Gadgets.COOLDOWN.values()) assertTrue(cd >= 3 && cd <= 30);
	}

	@Test
	void killMessages() {
		assertTrue(KillMessages.IDS.size() >= 15);
		assertEquals(KillMessages.IDS.size(), KillMessages.IDS.stream().distinct().count());
		assertTrue(KillMessages.known("bonk"));
	}
}
