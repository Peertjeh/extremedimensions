package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackTest {
	@Test
	void predictsAPlayerWalkingStraight() {
		Track t = new Track();
		assertNull(t.predict(0, 5));
		for (int i = 0; i <= 10; i++) t.observe(i, new V3(i * 0.2, 64, 0));
		assertEquals(0.2, t.velocity().x(), 1e-9);
		V3 p = t.predict(10, 5);
		assertEquals(3.0, p.x(), 1e-9);
		assertEquals(64, p.y(), 1e-9);
	}

	@Test
	void rememberedPositionsFadeAndPredictionStaysBounded() {
		Track t = new Track();
		for (int i = 0; i < 5; i++) t.observe(i, new V3(i, 64, 0));
		assertEquals(1.0, t.confidence(4, 60), 1e-9);
		assertEquals(0.5, t.confidence(64, 60), 1e-9);
		assertTrue(t.confidence(400, 60) < 0.02);
		// long unseen: the prediction does not run off to infinity
		assertTrue(t.predict(1000, 5).x() <= 4 + 20 + 1e-9);
	}

	@Test
	void countsStrafeChangesSeenFromTheObserver() {
		Track zigzag = new Track(), straight = new Track();
		V3 me = new V3(0, 64, -5);
		for (int i = 0; i < 20; i++) {
			double side = (i / 3) % 2 == 0 ? i % 3 * 0.25 : (3 - i % 3) * 0.25;
			zigzag.observe(i, new V3(((i / 3) % 2 == 0 ? 1 : -1) * 0.1 + side, 64, 0));
			straight.observe(i, new V3(i * 0.2, 64, 0));
		}
		assertTrue(zigzag.strafeChanges(me, 40) >= 3, "zigzag " + zigzag.strafeChanges(me, 40));
		assertEquals(0, straight.strafeChanges(me, 40));
	}

	@Test
	void theBufferIsBounded() {
		Track t = new Track();
		for (int i = 0; i < 1000; i++) t.observe(i, new V3(i, 0, 0));
		assertEquals(40, t.size());
		assertEquals(999, t.last().x(), 1e-9);
		t.observe(999, new V3(5, 0, 0));
		assertEquals(999, t.last().x(), 1e-9, "one sighting per tick");
	}
}
