package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.function.ToIntFunction;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.events.PaleSquall;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Ferryman's blows of 3.43: his Undertow draws his passenger round and in; his Oars of the Drowned rise out of the
 * ground and none is left standing once the blow is over; his Bells of the Drowned rise out of the floor and none is left
 * once they have sunk back. No arena here: it would reach into the tests round these.
 */
public class FerrymanBlowTests {
	private static Ferryman ferryman(GameTestHelper helper, ServerPlayer p) {
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		try {
			return FerrymanService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		} finally {
			Config.ELDER_ARENA = arena;
		}
	}

	/**
	 * His new blows reach far (a bell rises beside anyone he can reach, its toll runs out over the ground), so these tests
	 * make them harmless: the players of the tests round these are not his. (Only his own settings change, which no other
	 * test reads.) Run the returned task when he is gone.
	 */
	private static Runnable harmless() {
		double geyser = Config.FERRYMAN_GEYSER_DAMAGE, oar = Config.FERRYMAN_OAR_DAMAGE, bell = Config.FERRYMAN_BELL_DAMAGE;
		Config.FERRYMAN_GEYSER_DAMAGE = 0;
		Config.FERRYMAN_OAR_DAMAGE = 0;
		Config.FERRYMAN_BELL_DAMAGE = 0;
		return () -> {
			Config.FERRYMAN_GEYSER_DAMAGE = geyser;
			Config.FERRYMAN_OAR_DAMAGE = oar;
			Config.FERRYMAN_BELL_DAMAGE = bell;
		};
	}

	private static ServerPlayer passenger(GameTestHelper helper, String name, int x, int z) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	/** What a watched blow came to: the most its count reached while it lasted, what it read once it was over, and when. */
	private interface Verdict {
		void judge(int most, int after, int ended);
	}

	/**
	 * Calls the Ferryman for his passenger, forces one of his blows and watches it every tick: the most {@code count}
	 * reached while the blow lasted, and what it reads the moment the blow is over (at once, before his rest is over and
	 * another blow can begin). Hands both to {@code verdict}.
	 */
	private static void watch(GameTestHelper helper, ServerPlayer p, String name, int blow, int limit, ToIntFunction<Ferryman> count, Verdict verdict) {
		Runnable restore = harmless();
		Ferryman f = ferryman(helper, p);
		if (f == null) {
			restore.run();
			throw helper.assertionException("the Ferryman could not come");
		}
		if (!FerrymanService.force(f, blow)) {
			FerrymanService.fade(f);
			restore.run();
			throw helper.assertionException("the Ferryman would not begin his %s", name);
		}
		int[] most = {0}, ended = {-1};
		for (int t = 1; t < limit; t++) {
			int now = t;
			helper.runAfterDelay(t, () -> {
				if (ended[0] >= 0) {
					return;
				}
				if (f.isAlive() && f.getAction() == blow) {
					most[0] = Math.max(most[0], count.applyAsInt(f));
					return;
				}
				ended[0] = now;
				int after = count.applyAsInt(f);
				if (f.isAlive()) {
					FerrymanService.fade(f);
				}
				restore.run();
				verdict.judge(most[0], after, now);
				helper.succeed();
			});
		}
		helper.runAfterDelay(limit, () -> {
			if (ended[0] >= 0) {
				return;
			}
			int action = f.getAction();
			if (f.isAlive()) {
				FerrymanService.fade(f);
			}
			restore.run();
			throw helper.assertionException("his %s never ended (his action %s after %s ticks)", name, action, limit);
		});
	}

	/** The Undertow: its whirlpool draws his passenger round and in (the squall's gust reaches them). */
	@GameTest(environment = Alone.FERRYMAN_UNDERTOW, maxTicks = 140)
	public void hisUndertowDrawsHisPassengerIn(GameTestHelper helper) {
		// (off the boat's side, inside the whirl, clear of the test's walls)
		ServerPlayer p = passenger(helper, "ferryman-undertow", 2, 2);
		int felt = PaleSquall.felt(p.getUUID());
		watch(helper, p, "undertow", Ferryman.UNDERTOW, 130, f -> PaleSquall.felt(p.getUUID()) - felt,
				(most, after, ended) -> check(helper, Math.max(most, after) > 0, "his undertow never drew his passenger in (over %s ticks)", ended));
	}

	/** Oars of the Drowned: two oars rise out of the ground, and none is left standing once the blow is over. */
	@GameTest(environment = Alone.FERRYMAN_OARS, maxTicks = 150)
	public void hisOarsRiseAndSinkAgain(GameTestHelper helper) {
		watch(helper, passenger(helper, "ferryman-oars", 1, 1), "oars", Ferryman.OARS, 140, FerrymanService::oars, (most, after, ended) -> {
			check(helper, most >= 2, "only %s displays of his oars rose", most);
			check(helper, after == 0, "%s displays of his oars were left standing (at %s)", after, ended);
		});
	}

	/** Bells of the Drowned: bells rise out of the floor, and none is left once they have sunk back. */
	@GameTest(environment = Alone.FERRYMAN_BELLS, maxTicks = 140)
	public void hisBellsRiseAndSinkAgain(GameTestHelper helper) {
		watch(helper, passenger(helper, "ferryman-bells", 1, 1), "bells", Ferryman.BELLS, 130, FerrymanService::bells, (most, after, ended) -> {
			check(helper, most > 0, "no bell rose out of the floor");
			check(helper, after == 0, "%s bells were left standing (at %s)", after, ended);
		});
	}
}
