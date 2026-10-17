package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.check.Report;
import net.battlehub.arch.check.Validator;
import net.battlehub.arch.mod.Library;
import net.battlehub.arch.town.Lobby;

/** The lobby settlement: complete, inside its bounds, walkable, and different for every seed. */
class LobbyTest {
	static Lobby.Result lobby;

	@BeforeAll
	static void generate() {
		lobby = Lobby.generate(new Lobby.Settings(), Library.defaults());
	}

	@Test
	void hasItsLandmarksAndATown() {
		assertEquals(Set.of("cathedral", "castle", "temple_ruin", "mage_tower"), lobby.landmarks.keySet());
		assertTrue(lobby.houses >= 30, "houses: " + lobby.houses);
		assertTrue(lobby.voxels.size() > 100_000);
	}

	@Test
	void passesValidationAndIsWalkableFromTheSpawn() {
		Report r = Validator.lobby(lobby, new int[] {0, 1, 9});
		assertTrue(r.ok(), r.errors().toString());
		for (String l : lobby.landmarks.keySet()) assertTrue(r.metrics.get("path:" + l) > 0, "no way to " + l);
	}

	@Test
	void staysInsideItsBounds() {
		int limit = new Lobby.Settings().radius + 40;
		assertTrue(lobby.voxels.minX() >= -limit && lobby.voxels.maxX() <= limit);
		assertTrue(lobby.voxels.minZ() >= -limit && lobby.voxels.maxZ() <= limit);
		assertTrue(lobby.voxels.maxY() < 200 && lobby.voxels.minY() > -100);
		for (Building b : lobby.buildings) {
			int[] box = b.worldBox();
			assertTrue(Math.hypot(Math.max(Math.abs(box[0]), Math.abs(box[3])), Math.max(Math.abs(box[2]), Math.abs(box[5]))) < limit * 1.5);
		}
	}

	@Test
	void thePlazaIsWhereTheGameExpectsIt() {
		// beacon over the fountain, the four gate beacons, the floor under the spawn, the balustrade
		assertEquals("beacon", lobby.voxels.get(0, 15, 0));
		assertEquals("beacon", lobby.voxels.get(0, 13, -27));
		assertEquals("beacon", lobby.voxels.get(27, 13, 0));
		assertTrue(lobby.voxels.filled(0, 0, 9));
		assertTrue(!lobby.voxels.filled(0, 1, 9) && !lobby.voxels.filled(0, 2, 9));
	}

	@Test
	void anotherSeedIsAnotherCity() {
		Lobby.Settings s = new Lobby.Settings();
		s.seed = 7;
		Lobby.Result other = Lobby.generate(s, Library.defaults());
		assertTrue(other.houses >= 30);
		int same = 0, total = 0;
		for (int x = -100; x <= 100; x += 3) {
			for (int z = -100; z <= 100; z += 3) {
				for (int y = 0; y < 30; y += 3) {
					String a = lobby.voxels.get(x, y, z), b = other.voxels.get(x, y, z);
					if (a == null && b == null) continue;
					total++;
					if (a != null && a.equals(b)) same++;
				}
			}
		}
		assertTrue(same < total * 0.7, "too similar: " + same + "/" + total);
	}
}
