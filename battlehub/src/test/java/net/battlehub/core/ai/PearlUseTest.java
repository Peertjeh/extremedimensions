package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.battlehub.core.BotMath;

/** When and where a bot throws an ender pearl (1.49). */
class PearlUseTest {
	static final V3 ME = new V3(0, 64, 0);

	@Test
	void afterSomeoneFarOffItSees() {
		assertTrue(PearlUse.chase(20, true, true, 1, Difficulty.EXPERT, 0));
		// close by it runs; out of sight, in the air, low or too far it does not
		assertFalse(PearlUse.chase(8, true, true, 1, Difficulty.EXPERT, 0));
		assertFalse(PearlUse.chase(20, false, true, 1, Difficulty.EXPERT, 0));
		assertFalse(PearlUse.chase(20, true, false, 1, Difficulty.EXPERT, 0));
		assertFalse(PearlUse.chase(20, true, true, 0.2, Difficulty.EXPERT, 0));
		assertFalse(PearlUse.chase(60, true, true, 1, Difficulty.EXPERT, 0));
	}

	@Test
	void awayToEatWhenLowAndCornered() {
		assertTrue(PearlUse.escape(2, 0.2, true, true, Difficulty.MASTER, 0));
		assertFalse(PearlUse.escape(2, 0.2, false, true, Difficulty.MASTER, 0), "nothing to eat: no point");
		assertFalse(PearlUse.escape(10, 0.2, true, true, Difficulty.MASTER, 0), "nobody on top of it");
		assertFalse(PearlUse.escape(2, 0.8, true, true, Difficulty.MASTER, 0), "not low");
	}

	@Test
	void aBeginnerNeverTheHigherTheLevelTheSooner() {
		assertEquals(0, PearlUse.chance(Difficulty.BEGINNER));
		assertFalse(PearlUse.chase(20, true, true, 1, Difficulty.BEGINNER, 0));
		double last = -1;
		for (Difficulty d : Difficulty.values()) {
			assertTrue(PearlUse.chance(d) >= last, d.name());
			last = PearlUse.chance(d);
		}
		assertTrue(PearlUse.chance(Difficulty.MASTER) > 0.8);
	}

	@Test
	void aChasingPearlComesDownShortOfThem() {
		V3 at = PearlUse.chaseTo(ME, new V3(20, 64, 0));
		assertEquals(17, at.x(), 1e-9);
		assertEquals(0, at.z(), 1e-9);
	}

	@Test
	void awayFromThemOntoGround() {
		// ground everywhere: straight away
		V3 at = PearlUse.escapeTo(ME, new V3(2, 64, 0), p -> true);
		assertNotNull(at);
		assertEquals(-PearlUse.AWAY, at.x(), 1e-9);
		// no ground behind it (x < -5): round to a side
		V3 side = PearlUse.escapeTo(ME, new V3(2, 64, 0), p -> p.x() > -5);
		assertNotNull(side);
		assertTrue(side.x() > -5 && Math.abs(side.z()) > 5, "to a side: " + side);
		assertTrue(side.sub(new V3(2, 64, 0)).flatLength() > 6, "still away from them: " + side);
		// no ground anywhere: no pearl
		assertNull(PearlUse.escapeTo(ME, new V3(2, 64, 0), p -> false));
	}

	@Test
	void thePearlsArcComesDownWhereItAims() {
		for (double h : new double[] {10, 17, 25, 35}) {
			for (double dy : new double[] {-3, 0, 4}) {
				float pitch = BotMath.pearlPitch(h, dy);
				// fly it as the game does: speed 1.5 along the look, drag 0.99, gravity 0.03
				double a = Math.toRadians(-pitch), vx = Math.cos(a) * BotMath.PEARL_SPEED, vy = Math.sin(a) * BotMath.PEARL_SPEED, x = 0, y = 0;
				double at = Double.NaN;
				for (int t = 0; t < 300 && Double.isNaN(at); t++) {
					double nx = x + vx, ny = y + vy;
					if (nx >= h) at = y + (ny - y) * (h - x) / (nx - x);
					x = nx;
					y = ny;
					vx *= 0.99;
					vy = vy * 0.99 - BotMath.PEARL_GRAVITY;
				}
				assertEquals(dy, at, 0.3, "h " + h + ", dy " + dy + ": pitch " + pitch);
			}
		}
	}
}
