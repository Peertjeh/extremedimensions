package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.elder.WeaverService;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The Knot Mother is one of a kind, and the batches of tests run side by side: a test that wakes her waits until no
 * other test has her, then runs its steps, each at its own tick after that. A claim lapses after 600 ticks (a test that
 * failed before it let go).
 */
final class KnotMotherSlot {
	private KnotMotherSlot() {}

	private static String holder;
	private static int since;

	private static boolean claim(GameTestHelper helper, String who) {
		int now = helper.getLevel().getServer().getTickCount();
		if (who.equals(holder)) {
			return true;
		}
		if ((holder == null || now - since > 600) && !WeaverService.knotMotherAwake()) {
			holder = who;
			since = now;
			return true;
		}
		return false;
	}

	static void release(String who) {
		if (who.equals(holder)) {
			holder = null;
		}
	}

	/** A test's steps, by the tick after it has the Knot Mother to itself. */
	static final class Steps {
		private final Map<Integer, List<Runnable>> at = new HashMap<>();

		void at(int tick, Runnable step) {
			at.computeIfAbsent(tick, k -> new ArrayList<>()).add(step);
		}

		/** Waits at most {@code wait} ticks for her, then runs {@code length} ticks of steps (all scheduled now, at the top). */
		void run(GameTestHelper helper, String who, int wait, int length) {
			int[] start = {-1};
			for (int t = 1; t <= wait + length; t++) {
				int tick = t;
				helper.runAfterDelay(t, () -> {
					if (start[0] < 0) {
						if (tick > wait) {
							check(helper, false, "the Knot Mother was never free for this test");
						}
						if (!claim(helper, who)) {
							return;
						}
						start[0] = tick;
					}
					List<Runnable> steps = at.get(tick - start[0]);
					if (steps != null) {
						steps.forEach(Runnable::run);
					}
				});
			}
		}
	}
}
