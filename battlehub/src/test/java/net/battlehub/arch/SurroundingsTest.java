package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.arena.ArenaPlan;
import net.battlehub.arch.arena.Cover;
import net.battlehub.arch.arena.Dress;
import net.battlehub.arch.arena.Surroundings;
import net.battlehub.arch.mod.Library;

/** What surrounds the other arenas: stands round walled ones, islands round the rest, cover pieces in the field. */
class SurroundingsTest {
	static final Library LIB = Library.defaults();

	static Surroundings.Settings walled(String theme, int r, int y) {
		Surroundings.Settings s = new Surroundings.Settings();
		s.theme = theme;
		s.seed = 4;
		s.rIn = r + 4;
		s.rOut = r + 12;
		s.groundY = y;
		s.reach = r + 1;
		s.reserved = (x, z) -> Math.hypot(x, z) <= r + 1;
		return s;
	}

	@Test
	void walledArenasGetStandsAndNothingOverTheField() {
		for (String t : new String[] {"castle", "desert", "nether", "forest", "deep_dark"}) {
			Surroundings.Settings s = walled(t, 20, 0);
			ArenaPlan p = Surroundings.generate(s, LIB);
			int[] seats = {0}, intruders = {0};
			p.voxels.forEach((x, y, z, id) -> {
				if (Math.hypot(x, z) <= 21 && y > s.groundY - 40) intruders[0]++;
				if (Props.base(id).endsWith("_stairs") && Math.hypot(x, z) < Surroundings.end(s)) seats[0]++;
			});
			assertEquals(0, intruders[0], t + ": blocks over the field");
			assertTrue(seats[0] > 300, t + " seats: " + seats[0]);
			// the gatehouses rise behind both spawn sides, higher than the stands
			int rw = Surroundings.end(s) - 1;
			assertTrue(p.voxels.filled(-rw, Surroundings.height() - 2, 3) && p.voxels.filled(rw, Surroundings.height() - 2, -3), t + " gatehouses");
		}
	}

	@Test
	void theStandsStartBeyondTheArenasFarthestCorner() {
		Surroundings.Settings s = walled("castle", 20, 0);
		s.reach = 29;
		s.reserved = (x, z) -> Math.max(Math.abs(x), Math.abs(z)) <= 20;
		ArenaPlan p = Surroundings.generate(s, LIB);
		assertTrue(Surroundings.start(s) >= 32);
		int[] bad = {0};
		p.voxels.forEach((x, y, z, id) -> {
			if (Math.max(Math.abs(x), Math.abs(z)) <= 20 && y > -40) bad[0]++;
		});
		assertEquals(0, bad[0]);
	}

	@Test
	void openThemesAndArenasOverTheVoidGetIslands() {
		for (String t : new String[] {"sky", "end", "cherry"}) {
			Surroundings.Settings s = walled(t, 14, t.equals("cherry") ? -8 : 0);
			ArenaPlan p = Surroundings.generate(s, LIB);
			assertTrue(p.log.get(p.log.size() - 1).contains("islands"), p.log.toString());
			assertTrue(p.backdrop.size() >= 5, t + ": " + p.backdrop.size() + " islands and landmarks");
			int[] near = {0};
			p.voxels.forEach((x, y, z, id) -> {
				if (Math.hypot(x, z) <= 16) near[0]++;
			});
			assertEquals(0, near[0], t + ": islands too close");
		}
	}

	@Test
	void coverPiecesFitTheirFootprintInEveryTheme() {
		for (String t : Dress.ids()) {
			for (Cover.Kind k : Cover.Kind.values()) {
				Voxels v = Cover.piece(t, 9, k, Xform.ID, LIB);
				assertTrue(v.size() >= 5, t + "/" + k + " is empty");
				v.forEach((x, y, z, id) -> {
					assertTrue(Math.abs(x) <= Cover.RADIUS && Math.abs(z) <= Cover.RADIUS && y >= 0 && y <= 7, t + "/" + k + " reaches " + x + "," + y + "," + z);
				});
			}
		}
	}

	@Test
	void theCopyOnTheOtherHalfIsTheSamePieceTurnedRound() {
		for (Cover.Kind k : Cover.Kind.values()) {
			Voxels a = Cover.piece("castle", 3, k, new Xform(1, false, 10, 1, 4), LIB);
			Voxels b = Cover.piece("castle", 3, k, new Xform(3, false, -10, 1, -4), LIB);
			assertEquals(a.size(), b.size(), k.toString());
			a.forEach((x, y, z, id) -> assertEquals(Props.base(id), Props.base(b.get(-x, y, -z)), k + " at " + x + "," + y + "," + z));
		}
	}
}
