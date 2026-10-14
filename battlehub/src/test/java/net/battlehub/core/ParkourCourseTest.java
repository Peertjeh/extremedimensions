package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.battlehub.core.ParkourCourse.Difficulty;
import net.battlehub.core.ParkourCourse.Kind;
import net.battlehub.core.ParkourCourse.Platform;
import net.battlehub.core.ParkourCourse.Style;

class ParkourCourseTest {
	private static Platform solid(int x, int y, int z, int size) {
		return new Platform(x, y, z, size, Kind.JUMP, 0, Style.SOLID, ParkourCourse.EAST, 0);
	}

	@Test
	void jumpRules() {
		Platform a = solid(0, 0, 0, 1);
		// Flat: 3 air blocks is a normal sprint jump from medium up, 4 is too far
		assertTrue(ParkourCourse.jumpable(a, solid(4, 0, 0, 1), Difficulty.HARD));
		assertFalse(ParkourCourse.jumpable(a, solid(5, 0, 0, 1), Difficulty.HARD));
		assertFalse(ParkourCourse.jumpable(a, solid(4, 0, 0, 1), Difficulty.EASY));
		// One up: at most 2 air blocks (hard), never 3
		assertTrue(ParkourCourse.jumpable(a, solid(3, 1, 0, 1), Difficulty.HARD));
		assertFalse(ParkourCourse.jumpable(a, solid(4, 1, 0, 1), Difficulty.EXTREME));
		// Two up is impossible
		assertFalse(ParkourCourse.jumpable(a, solid(2, 2, 0, 1), Difficulty.EXTREME));
		// Going down allows a little more on extreme
		assertTrue(ParkourCourse.jumpable(a, solid(5, -2, 0, 1), Difficulty.EXTREME));
		// A fence post stands half a block higher: one block up onto a fence is 1.5 up, too high
		Platform fence = new Platform(2, 1, 0, 1, Kind.JUMP, 0, Style.FENCE, ParkourCourse.EAST, 0);
		assertFalse(ParkourCourse.jumpable(a, fence, Difficulty.EXTREME));
		// Ladder: starts at the feet, right next to the platform
		Platform ladder = new Platform(2, 3, 0, 1, Kind.JUMP, 0, Style.LADDER, ParkourCourse.EAST, 3);
		assertTrue(ParkourCourse.jumpable(a, ladder, Difficulty.EASY));
	}

	@Test
	void everyCourseIsJumpableAndHasNoShortcuts() {
		for (Difficulty d : Difficulty.values()) {
			for (long seed = 0; seed < 400; seed++) {
				List<Platform> c = ParkourCourse.generate(seed * 31 + d.ordinal(), d, d.defaultJumps, d.checkpointEvery, 14);
				String where = d + " seed " + seed;
				assertEquals(d.defaultJumps + 1, c.size(), where);
				assertEquals(Kind.START, c.get(0).kind(), where);
				assertEquals(Kind.FINISH, c.get(c.size() - 1).kind(), where);
				int checkpoints = 0;
				for (int i = 1; i < c.size(); i++) {
					Platform a = c.get(i - 1);
					Platform b = c.get(i);
					assertTrue(ParkourCourse.jumpable(a, b, d), where + " jump " + i + ": " + a + " -> " + b);
					assertTrue(b.x() >= a.x(), where + ": the course never heads west");
					assertTrue(Math.abs(b.z()) <= 40, where + ": stays in its corridor");
					if (b.kind() == Kind.CHECKPOINT) {
						checkpoints++;
						assertEquals(checkpoints, b.checkpoint(), where);
					}
				}
				assertEquals(checkpoints + 1, c.get(c.size() - 1).checkpoint(), where);
				assertTrue(checkpoints >= 3, where);
				// Nothing far along the route is within jumping distance of an earlier part
				for (int i = 0; i < c.size(); i++) {
					for (int j = i + ParkourCourse.NEIGHBOURS + 1; j < c.size(); j++) {
						assertTrue(ParkourCourse.separated(c.get(i), c.get(j)),
								where + ": " + i + " and " + j + " are too close: " + c.get(i) + " / " + c.get(j));
					}
				}
			}
		}
	}

	@Test
	void harderMeansSmallerAndTrickier() {
		Map<Difficulty, int[]> stats = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			int[] s = new int[Style.values().length + 1];
			for (long seed = 0; seed < 200; seed++) {
				for (Platform p : ParkourCourse.generate(seed, d, d.defaultJumps, d.checkpointEvery, 14)) {
					s[p.style().ordinal()]++;
					if (p.size() == 1 && p.style() == Style.SOLID) s[Style.values().length]++;
				}
			}
			stats.put(d, s);
		}
		int tiny = Style.values().length;
		assertEquals(0, stats.get(Difficulty.EASY)[Style.FENCE.ordinal()]);
		assertEquals(0, stats.get(Difficulty.EASY)[Style.ICE.ordinal()]);
		assertEquals(0, stats.get(Difficulty.EASY)[tiny]);
		assertTrue(stats.get(Difficulty.MEDIUM)[Style.SLAB.ordinal()] > 0);
		assertTrue(stats.get(Difficulty.HARD)[Style.FENCE.ordinal()] > 0);
		assertTrue(stats.get(Difficulty.HARD)[Style.ICE.ordinal()] > 0);
		assertTrue(stats.get(Difficulty.EXTREME)[Style.LADDER.ordinal()] > 0);
		assertTrue(stats.get(Difficulty.EXTREME)[tiny] > stats.get(Difficulty.HARD)[tiny]);
		assertTrue(stats.get(Difficulty.HARD)[tiny] > stats.get(Difficulty.MEDIUM)[tiny]);
	}

	@Test
	void everyCourseIsDifferentAndLonger() {
		Set<String> seen = new HashSet<>();
		Random r = new Random(5);
		for (int i = 0; i < 300; i++) {
			seen.add(ParkourCourse.generate(r.nextLong(), Difficulty.MEDIUM, 50, 8, 14).toString());
		}
		assertEquals(300, seen.size());
		assertTrue(Difficulty.MEDIUM.defaultJumps > 36 && Difficulty.EXTREME.defaultJumps > Difficulty.HARD.defaultJumps);
	}

	@Test
	void difficultyNames() {
		Random r = new Random(1);
		assertEquals(Difficulty.HARD, Difficulty.parse("hard", r));
		assertEquals(Difficulty.EXTREME, Difficulty.parse(" Extreme ", r));
		Set<Difficulty> random = new HashSet<>();
		for (int i = 0; i < 100; i++) random.add(Difficulty.parse("random", r));
		assertEquals(4, random.size());
	}
}
