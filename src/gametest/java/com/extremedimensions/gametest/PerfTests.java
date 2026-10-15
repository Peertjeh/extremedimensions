package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the mod costs a server. Each test logs a {@code PERF} line and fails only far above any sane cost, so a slow
 * runner never fails it but a real regression (a loop gone quadratic, a command in a hot path) does.
 */
public class PerfTests {
	private static void report(String what, String result) {
		ExtremeDimensions.LOGGER.info("PERF {}: {}", what, result);
	}

	/** Twenty players, each carrying every curse at its maximum: the cost of one server tick of curse upkeep. */
	@GameTest(maxTicks = 100)
	public void cursesForTwentyPlayers(GameTestHelper helper) {
		List<ServerPlayer> players = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ServerPlayer p = TestPlayers.survivor(helper);
			PlayerData d = DataStore.get(p.getUUID());
			for (Penalty pen : Penalty.values()) {
				d.addStacks(pen, pen.max);
			}
			players.add(p);
		}
		// Attribute curses: set on joining, respawning and every change of stacks
		long t0 = System.nanoTime();
		for (ServerPlayer p : players) {
			CurseEffects.applyEffects(p, DataStore.get(p.getUUID()));
		}
		double applyMs = (System.nanoTime() - t0) / 1e6 / players.size();
		// Upkeep: every tick, for every player (warm up first, then measure 200 ticks)
		for (int warm = 0; warm < 50; warm++) {
			tickAll(players);
		}
		int ticks = 200;
		long t1 = System.nanoTime();
		for (int i = 0; i < ticks; i++) {
			tickAll(players);
		}
		double tickMs = (System.nanoTime() - t1) / 1e6 / ticks;
		report("curses, 20 players with every curse at max", String.format(java.util.Locale.ROOT,
				"upkeep %.3f ms per server tick (%.1f%% of a 50 ms tick), attribute curses %.3f ms per player", tickMs, tickMs / 50 * 100, applyMs));
		CurseTests.check(helper, tickMs < 10, "curse upkeep for 20 players takes %s ms per tick", tickMs);
		CurseTests.check(helper, applyMs < 20, "setting the attribute curses takes %s ms per player", applyMs);
		helper.succeed();
	}

	private static void tickAll(List<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			PlayerData d = DataStore.get(p.getUUID());
			PenaltyService.tick(p, d, false, true);
			d.tickCounter++;
		}
	}

	/**
	 * The Seam: designing one of every structure, then building at the game's pace. Blocks in chunks nobody has loaded
	 * must wait without costing anything; once the chunks are loaded the architect builds within its time budget.
	 * The test server only loads the vanilla worlds, so the blocks are laid in the test level (same cost per block).
	 */
	@GameTest(maxTicks = 600)
	public void seamStructures(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SeamArchitect.clear();
		long t0 = System.nanoTime();
		int kinds = SeamService.designShowcase();
		double designMs = (System.nanoTime() - t0) / 1e6;
		int queued = SeamArchitect.pending();
		CurseTests.check(helper, kinds == 6 && queued > 1000, "the showcase designed %s structures with %s blocks", kinds, queued);
		long budget = com.extremedimensions.config.Config.SEAM_BUILD_BUDGET_MS * 1_000_000L;
		// Nothing is loaded out there yet: a tick of building must cost about nothing and load no chunk
		long t1 = System.nanoTime();
		SeamArchitect.tick(level, budget);
		double idleMs = (System.nanoTime() - t1) / 1e6;
		// Load the first two islands' chunks, as players walking there would
		int x0 = 3940 >> 4, x1 = 4160 >> 4, z0 = 3940 >> 4, z1 = 4160 >> 4;
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		helper.runAfterDelay(80, () -> {
			int before = SeamArchitect.pending();
			double worst = 0, total = 0;
			int ticks = 60, worstAt = 0;
			double[] each = new double[ticks];
			for (int i = 0; i < ticks; i++) {
				long t = System.nanoTime();
				SeamArchitect.tick(level, budget);
				double ms = (System.nanoTime() - t) / 1e6;
				each[i] = ms;
				if (ms > worst) {
					worst = ms;
					worstAt = i;
				}
				total += ms;
			}
			java.util.Arrays.sort(each);
			double p95 = each[(int) Math.ceil(ticks * 0.95) - 1];
			double second = each[ticks - 2];
			int built = before - SeamArchitect.pending();
			for (int cx = x0; cx <= x1; cx++) {
				for (int cz = z0; cz <= z1; cz++) {
					level.setChunkForced(cx, cz, false);
				}
			}
			SeamArchitect.clear();
			report("seam", String.format(java.util.Locale.ROOT,
					"designed %d structures (%d blocks) in %.1f ms (in the game on its own thread); a tick with nothing loaded %.2f ms; "
							+ "with chunks loaded %d blocks in %d ticks, %.2f ms a tick on average, 95%% within %.2f ms, worst %.2f ms (tick %d), "
							+ "next worst %.2f ms (budget %d ms)",
					kinds, queued, designMs, idleMs, built, ticks, total / ticks, p95, worst, worstAt, second, budget / 1_000_000));
			CurseTests.check(helper, idleMs < 50, "building with nothing loaded took %s ms", idleMs);
			CurseTests.check(helper, built > 0, "nothing was built in loaded chunks");
			// The budget holds for nearly every tick; one tick may meet a garbage collection or a busy machine (the other
			// tests' worlds share the heap), so all but the worst stay near the budget and the worst only far from a stall
			CurseTests.check(helper, p95 < budget / 1e6 + 5, "95%% of building ticks took up to %s ms (budget %s ms)", p95, budget / 1e6);
			CurseTests.check(helper, second < budget / 1e6 + 60, "two ticks of building took %s ms or more (budget %s ms)", second, budget / 1e6);
			CurseTests.check(helper, worst < 250, "a tick of building stalled for %s ms (budget %s ms)", worst, budget / 1e6);
			// Then, with the queue to itself, the Seam building itself before a watcher's eyes
			FrayTests.seamBuildsBeforeAWatcher(helper);
		});
	}
}
