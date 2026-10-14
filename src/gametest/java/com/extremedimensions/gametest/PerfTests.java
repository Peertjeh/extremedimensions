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

	/** The Seam: designing one of every structure, and building the queued blocks at the rate the game does. */
	@GameTest(maxTicks = 400)
	public void seamStructures(GameTestHelper helper) {
		ServerLevel seam = helper.getLevel().getServer().getLevel(SeamService.SEAM);
		CurseTests.check(helper, seam != null, "the Seam dimension is not loaded");
		SeamArchitect.clear();
		long t0 = System.nanoTime();
		int kinds = SeamService.showcase(helper.getLevel().getServer());
		double designMs = (System.nanoTime() - t0) / 1e6;
		int queued = SeamArchitect.pending();
		// Build at the game's own rate (4000 blocks a tick) for a few ticks; the first chunks load on the way
		int budget = 4000, rounds = 5;
		long t1 = System.nanoTime();
		for (int i = 0; i < rounds && SeamArchitect.pending() > 0; i++) {
			SeamArchitect.tick(seam, budget);
		}
		double buildMs = (System.nanoTime() - t1) / 1e6 / rounds;
		SeamArchitect.clear();
		report("seam", String.format(java.util.Locale.ROOT, "designed %d structures (%d blocks) in %.1f ms; building %d blocks a tick costs %.1f ms",
				kinds, queued, designMs, budget, buildMs));
		CurseTests.check(helper, kinds == 6 && queued > 1000, "the showcase designed %s structures with %s blocks", kinds, queued);
		CurseTests.check(helper, designMs < 5000, "designing the six structures took %s ms", designMs);
		helper.succeed();
	}
}
