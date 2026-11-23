package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.elder.ElderStir;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.WeaverService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * 3.38: the Elders after the Matriarch give warning too. Before one rises a bar counts down; it rises for whoever stays
 * near, and settles when everyone goes far enough. A village's Last Weaver that settled wakes on the next visit, and the
 * Ferryman's bell falls silent once the one he comes for is out of the Nether.
 */
public class StirTests {
	/** A wind-up runs once under its key, raises nothing before its time, and rises for the one who stayed. */
	@GameTest(maxTicks = 60)
	public void aWindUpRisesForWhoeverStays(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		String key = "test-rises@" + p.getUUID();
		ServerPlayer[] rose = new ServerPlayer[1];
		boolean[] settled = {false};
		ElderStir.Stir st = new ElderStir.Stir(key, "starved_one", helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p, 1, 16,
				BossEvent.BossBarColor.PURPLE, q -> q == p, q -> rose[0] = q).settled(() -> settled[0] = true);
		check(helper, ElderStir.begin(st), "the wind-up did not begin");
		check(helper, ElderStir.stirring(key), "it is not counted as stirring");
		check(helper, !ElderStir.begin(new ElderStir.Stir(key, "starved_one", helper.getLevel(), at.getX(), at.getY(), at.getZ(), p, 1, 16,
				BossEvent.BossBarColor.PURPLE, q -> true, q -> {})), "a second wind-up began under the same key");
		check(helper, rose[0] == null, "it rose at once, with no time to make ready");
		helper.runAfterDelay(10, () -> check(helper, ElderStir.stirring(key) && rose[0] == null, "it rose before its time was up"));
		helper.runAfterDelay(30, () -> {
			check(helper, !ElderStir.stirring(key), "it still stirs after its time was up");
			check(helper, rose[0] == p && !settled[0], "it did not rise for the one who stayed (rose for %s, settled %s)", rose[0], settled[0]);
			helper.succeed();
		});
	}

	/** With everyone gone far enough, a wind-up settles and nothing rises. */
	@GameTest(maxTicks = 60)
	public void aWindUpSettlesWhenEveryoneGoes(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		String key = "test-settles@" + p.getUUID();
		ServerPlayer[] rose = new ServerPlayer[1];
		boolean[] settled = {false};
		check(helper, ElderStir.begin(new ElderStir.Stir(key, "weaver", helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p, 3, 8,
				BossEvent.BossBarColor.BLUE, q -> q == p, q -> rose[0] = q).settled(() -> settled[0] = true)), "the wind-up did not begin");
		helper.runAfterDelay(10, () -> {
			check(helper, ElderStir.stirring(key) && !settled[0], "it settled while they stood by it");
			p.teleportTo(at.getX() + 30.5, at.getY(), at.getZ() + 0.5);
		});
		helper.runAfterDelay(14, () -> {
			check(helper, !ElderStir.stirring(key) && settled[0], "it still stirs after everyone went far from it");
			check(helper, rose[0] == null, "it rose although everyone had gone");
			helper.succeed();
		});
	}

	/**
	 * A village's Last Weaver stirs before it wakes; with everyone gone it sinks back into the weave, the village
	 * remembers, and walking into it again starts the wind-up again (at once, with no roll).
	 */
	@GameTest(environment = Alone.WEAVER_SETTLES, maxTicks = 80)
	public void aVillagesWeaverSinksBackAndWakesOnTheNextVisit(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper);
		// (high over the test's floor: the players of the tests round this one, some blocks off, never count as near it)
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2)).above(60);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		int vx = at.getX() + 1, vy = at.getY(), vz = at.getZ() + 1;
		String due = "wvd@" + vx + "," + vz, rolled = "wv@" + vx + "," + vz;
		int wake = Config.WEAVER_WAKE_SECONDS, calm = Config.WEAVER_CALM_RANGE, unlock = Config.WEAVER_UNLOCK;
		ServerLevel level = helper.getLevel();
		net.minecraft.world.Difficulty was = level.getDifficulty();
		// (on peaceful no Weaver wakes, nor stirs)
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		Config.WEAVER_WAKE_SECONDS = 3;
		Config.WEAVER_CALM_RANGE = 12;
		Config.WEAVER_UNLOCK = 0;
		Runnable restore = () -> {
			Config.WEAVER_WAKE_SECONDS = wake;
			Config.WEAVER_CALM_RANGE = calm;
			Config.WEAVER_UNLOCK = unlock;
			DataStore.seamCells().remove(due);
			DataStore.seamCells().remove(rolled);
			level.getServer().setDifficulty(was, true);
		};
		try {
			check(helper, WeaverService.stir(p, vx, vy, vz), "the village's threads did not tighten");
			check(helper, ElderStir.stirring(WeaverService.stirKey(vx, vz)), "the village is not counted as stirring");
			check(helper, !WeaverService.stir(p, vx, vy, vz), "the threads tightened twice over one village");
			check(helper, !WeaverService.hunting(p), "the Weaver woke at once, with no time to make ready");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		helper.runAfterDelay(20, () -> {
			try {
				check(helper, ElderStir.stirring(WeaverService.stirKey(vx, vz)), "it sank back while they stood in the village");
				p.teleportTo(at.getX() + 40.5, at.getY(), at.getZ() + 0.5);
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(24, () -> {
			try {
				check(helper, !ElderStir.stirring(WeaverService.stirKey(vx, vz)), "it still stirs after everyone left the village");
				check(helper, DataStore.seamCells().contains(due), "the village does not remember the Weaver that sank back");
				check(helper, !WeaverService.hunting(p), "the Weaver woke although everyone had gone");
				// Walked into again, the village wakes it again at once
				p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				WeaverService.nearVillage(p, vx, vy, vz);
				check(helper, ElderStir.stirring(WeaverService.stirKey(vx, vz)), "walking into the village again did not stir its Weaver");
				check(helper, !DataStore.seamCells().contains(due), "the village still holds its Weaver back once it stirs again");
				p.teleportTo(at.getX() + 40.5, at.getY(), at.getZ() + 0.5);
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(28, () -> {
			try {
				check(helper, !ElderStir.stirring(WeaverService.stirKey(vx, vz)), "it still stirs after everyone left again");
				check(helper, !WeaverService.hunting(p), "the Weaver woke although everyone had gone again");
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	/** The Ferryman's bell tolls before he comes, and falls silent at once for someone not in the Nether. */
	@GameTest(maxTicks = 20)
	public void theFerrymansBellFallsSilentOutsideTheNether(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper);
		int wake = Config.FERRYMAN_WAKE_SECONDS;
		try {
			Config.FERRYMAN_WAKE_SECONDS = 3;
			check(helper, FerrymanService.stir(p), "the bell did not toll");
			check(helper, ElderStir.stirring(FerrymanService.stirKey(p)), "the bell is not counted as tolling");
			check(helper, !FerrymanService.stir(p), "the bell tolled twice for one player");
			check(helper, !FerrymanService.hunting(p), "he came at once, with no time to make ready");
		} finally {
			Config.FERRYMAN_WAKE_SECONDS = wake;
		}
		helper.runAfterDelay(3, () -> {
			check(helper, !ElderStir.stirring(FerrymanService.stirKey(p)), "the bell still tolls for someone out of the Nether");
			check(helper, !FerrymanService.hunting(p), "he came for someone out of the Nether");
			helper.succeed();
		});
	}

	/** Every wind-up is on by default (as long as the Matriarch's), and every one can be switched off with 0. */
	@GameTest
	public void everyEldersWindUpIsOnByDefault(GameTestHelper helper) {
		check(helper, Config.FERRYMAN_WAKE_SECONDS == 15 && Config.WEAVER_WAKE_SECONDS == 15 && Config.KNOT_MOTHER_WAKE_SECONDS == 15
				&& Config.STARVED_WAKE_SECONDS == 15 && Config.MATRIARCH_WAKE_SECONDS == 15, "not every Elder gives fifteen seconds of warning");
		check(helper, Config.WEAVER_WARN_RANGE > 16 && Config.STARVED_WARN_RANGE > 3.5, "a warning range does not reach past where the Elder wakes");
		helper.succeed();
	}
}
