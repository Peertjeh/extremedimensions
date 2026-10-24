package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ToppersTest {
	@Test
	void everyModelStaysRoundTheHead() {
		assertTrue(Toppers.MODELS.size() >= 12, "at least twelve toppers");
		for (var e : Toppers.MODELS.entrySet()) {
			assertFalse(e.getValue().parts().isEmpty(), e.getKey());
			for (long tick : new long[] {0, 7, 40, 133, 999}) {
				for (float yaw : new float[] {0, 90, 217, -45}) {
					for (Toppers.Placed p : Toppers.frame(e.getKey(), tick, yaw)) {
						assertTrue(Math.hypot(p.x(), p.z()) <= Toppers.REACH, e.getKey() + " too wide: " + p);
						assertTrue(p.y() >= Toppers.LOW && p.y() <= Toppers.HIGH, e.getKey() + " too high or low: " + p);
						assertTrue(p.id().startsWith("minecraft:"), e.getKey() + ": " + p.id());
						assertTrue(p.sx() > 0 && p.sy() > 0 && p.sz() > 0 && p.sx() <= 1 && p.sz() <= 1, e.getKey() + " size: " + p);
					}
				}
			}
		}
	}

	@Test
	void theModelTurnsWithTheBody() {
		// the crown's front gem sits forwards: south when facing south (yaw 0), west when facing west (yaw 90)
		List<Toppers.Placed> south = Toppers.frame("crown", 0, 0);
		List<Toppers.Placed> west = Toppers.frame("crown", 0, 90);
		Toppers.Placed gemSouth = south.stream().filter(p -> p.id().equals("minecraft:diamond_block")).findFirst().orElseThrow();
		Toppers.Placed gemWest = west.stream().filter(p -> p.id().equals("minecraft:diamond_block")).findFirst().orElseThrow();
		assertTrue(gemSouth.z() > 0.25 && Math.abs(gemSouth.x()) < 1e-9, "front is south: " + gemSouth);
		assertTrue(gemWest.x() < -0.25 && Math.abs(gemWest.z()) < 1e-9, "front is west: " + gemWest);
		assertEquals(90f, gemWest.yaw() - gemSouth.yaw(), 1e-4);
	}

	@Test
	void animationsMove() {
		Toppers.Placed a = Toppers.frame("halo", 0, 0).get(0);
		Toppers.Placed b = Toppers.frame("halo", 20, 0).get(0);
		assertEquals(Math.hypot(a.x(), a.z()), Math.hypot(b.x(), b.z()), 1e-9, "a spinning part keeps its distance");
		assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) > 0.05, "and moves round");
		double y0 = Toppers.frame("raincloud", 0, 0).get(0).y(), y1 = Toppers.frame("raincloud", 10, 0).get(0).y();
		assertTrue(Math.abs(y0 - y1) > 0.01, "the cloud bobs");
		assertEquals(Toppers.frame("crown", 0, 0), Toppers.frame("crown", 500, 0), "a still model stays still");
		assertTrue(Toppers.frame("nothing", 0, 0).isEmpty());
	}
}
