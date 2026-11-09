package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.List;
import java.util.function.ToIntFunction;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderStrikes;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.Weaver;
import com.extremedimensions.elder.WeaverService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Last Weaver's blows of 3.43: its Warp and Weft lays a grid of threads and lets it fall; its Spindle Storm raises
 * tornadoes of thread with shreds whirling round them, and none is left once they burst; its Loom brings down rows of
 * great needles, each with its sigil, and none is left falling. No arena here: it would reach into the tests round these.
 */
public class WeaverBlowTests {
	/** Its own Weaver, the one this wakes (a test beside it may wake one in the same tick); null if none came. */
	private static Weaver weaver(GameTestHelper helper, ServerPlayer p) {
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		try {
			List<Weaver> had = helper.getLevel().getEntitiesOfClass(Weaver.class, p.getBoundingBox().inflate(64));
			if (!WeaverService.summon(p, helper.absolutePos(new BlockPos(1, 2, 1)).offset(12, 0, 0))) {
				return null;
			}
			return helper.getLevel().getEntitiesOfClass(Weaver.class, p.getBoundingBox().inflate(64)).stream()
					.filter(w -> !had.contains(w) && !(w instanceof KnotMother)).findFirst().orElse(null);
		} finally {
			Config.ELDER_ARENA = arena;
		}
	}

	/**
	 * Its blows reach far (its needles fall towards anyone within 44 blocks, its tornadoes wander after whoever is nearest),
	 * so these tests make its new blows harmless: the players of the tests round these are not its. (Only its own settings
	 * change, which no other test reads.) Run the returned task when it is gone.
	 */
	private static Runnable harmless() {
		double warp = Config.WEAVER_WARP_DAMAGE, spindle = Config.WEAVER_SPINDLE_DAMAGE, needle = Config.WEAVER_NEEDLE_DAMAGE;
		Config.WEAVER_WARP_DAMAGE = 0;
		Config.WEAVER_SPINDLE_DAMAGE = 0;
		Config.WEAVER_NEEDLE_DAMAGE = 0;
		return () -> {
			Config.WEAVER_WARP_DAMAGE = warp;
			Config.WEAVER_SPINDLE_DAMAGE = spindle;
			Config.WEAVER_NEEDLE_DAMAGE = needle;
		};
	}

	private static ServerPlayer visitor(GameTestHelper helper, String name) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	/** What a watched blow came to: the most its count reached while it lasted, what it read once it was over, and when. */
	private interface Verdict {
		void judge(int most, int after, int ended, int sigils);
	}

	/**
	 * Wakes a Weaver for a player, forces one of its blows and watches it every tick: the most {@code count} reached while
	 * the blow lasted, and what it reads the moment the blow is over (at once, before its rest is over and another blow can
	 * begin, whose own would be counted too). Hands both, and the most of the Elders' strikes under way at once (any
	 * Elder's: only whether some burned is asked of it), to {@code verdict}.
	 */
	private static void watch(GameTestHelper helper, String name, int blow, int limit, ToIntFunction<Weaver> count, Verdict verdict) {
		ServerPlayer p = visitor(helper, "weaver-" + name);
		Runnable restore = harmless();
		Weaver w = weaver(helper, p);
		if (w == null) {
			restore.run();
			throw helper.assertionException("the Last Weaver could not come");
		}
		if (!WeaverService.force(w, blow)) {
			WeaverService.fade(w);
			restore.run();
			throw helper.assertionException("the Last Weaver would not begin its %s", name);
		}
		int[] most = {0}, sigils = {0}, ended = {-1};
		for (int t = 1; t < limit; t++) {
			int now = t;
			helper.runAfterDelay(t, () -> {
				if (ended[0] >= 0) {
					return;
				}
				if (w.isAlive() && w.getAction() == blow) {
					most[0] = Math.max(most[0], count.applyAsInt(w));
					sigils[0] = Math.max(sigils[0], ElderStrikes.active());
					return;
				}
				ended[0] = now;
				int after = count.applyAsInt(w);
				if (w.isAlive()) {
					WeaverService.fade(w);
				}
				restore.run();
				verdict.judge(most[0], after, now, sigils[0]);
				helper.succeed();
			});
		}
		helper.runAfterDelay(limit, () -> {
			if (ended[0] >= 0) {
				return;
			}
			int action = w.getAction();
			if (w.isAlive()) {
				WeaverService.fade(w);
			}
			restore.run();
			throw helper.assertionException("its %s never ended (its action %s after %s ticks)", name, action, limit);
		});
	}

	/** Warp and Weft: its grid is laid over the ground round its prey, and it has fallen once the blow is over. */
	@GameTest(environment = Alone.WEAVER_WARP, maxTicks = 140)
	public void itsWarpAndWeftIsLaidAndFalls(GameTestHelper helper) {
		watch(helper, "warp", Weaver.WARP, 130, w -> WeaverService.warpLaid(w) ? 1 : 0, (most, after, ended, sigils) -> {
			check(helper, most > 0, "its grid of threads was never laid");
			check(helper, after == 0, "its grid of threads still hung there after the blow (at %s)", ended);
		});
	}

	/** Spindle Storm: its tornadoes rise with shreds whirling round them, and once they burst none is left. */
	@GameTest(environment = Alone.WEAVER_SPINDLE, maxTicks = 220)
	public void itsSpindleStormRisesAndBursts(GameTestHelper helper) {
		watch(helper, "spindle", Weaver.SPINDLE, 210, WeaverService::spindleShreds, (most, after, ended, sigils) -> {
			check(helper, most >= 3, "only %s shreds whirled round its tornadoes", most);
			check(helper, after == 0, "%s shreds of its tornadoes were left in the air (at %s)", after, ended);
		});
	}

	/** The Loom Comes Down: rows of great needles fall, each with its sigil, and none is left falling once it is over. */
	@GameTest(environment = Alone.WEAVER_NEEDLEFALL, maxTicks = 160)
	public void itsLoomBringsDownRowsOfNeedles(GameTestHelper helper) {
		watch(helper, "needlefall", Weaver.NEEDLEFALL, 150, WeaverService::needlesFalling, (most, after, ended, sigils) -> {
			check(helper, most >= 4, "only %s great needles fell at once", most);
			check(helper, sigils > 0, "no sigil burned under its %s needles", most);
			check(helper, after == 0, "%s great needles were left falling (at %s)", after, ended);
		});
	}
}
