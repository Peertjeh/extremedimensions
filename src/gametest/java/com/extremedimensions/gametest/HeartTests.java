package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderArena;
import com.extremedimensions.elder.ElderFx;
import com.extremedimensions.elder.HeartArena;
import com.extremedimensions.mending.Binding;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.FightBlocks;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Starved One's fight at the Heart (3.48): who counts as at it (the whole of the Heart's island group, a pillar's
 * node high up its shaft too), how long it waits for its prey with nobody there, its rise (nothing hurts it until it is
 * free), the two changes of its fight, the Heart's arena (raised round it and gone again, every block of it, once the
 * fight is over), and its sounds and music where they are missing.
 *
 * <p>The fight is the Seam's own, at a fixed place, one for the whole world: each test of it runs alone, with a player
 * of its own sent into the Seam and the Heart's part of the world held loaded meanwhile, and puts everything back.
 */
public class HeartTests {
	/** A fight at the Heart for one test: its player, the Seam, and everything it changed, put back by {@link #end}. */
	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel seam;
		final ServerPlayer p;
		final List<long[]> forced = new ArrayList<>();
		final net.minecraft.world.Difficulty difficulty;
		final double rise, build, dissolve, drinkAt;
		final int leave, arena, rays, minions, flight, linger;
		boolean ended;

		Scene(GameTestHelper helper, String name, boolean creative, int chunks) {
			this.helper = helper;
			ServerLevel s = helper.getLevel().getServer().getLevel(SeamService.SEAM);
			check(helper, s != null, "there is no Seam to fight in");
			this.seam = s;
			difficulty = helper.getLevel().getDifficulty();
			rise = Config.STARVED_RISE_SECONDS;
			build = Config.ELDER_ARENA_BUILD_SECONDS;
			dissolve = Config.ELDER_ARENA_DISSOLVE_SECONDS;
			drinkAt = Config.STARVED_DRINK_AT;
			leave = Config.STARVED_LEAVE_SECONDS;
			arena = Config.STARVED_ARENA;
			rays = Config.STARVED_RAYS;
			minions = Config.STARVED_MINIONS;
			flight = Config.ELDER_ARENA_FLIGHT_TICKS;
			linger = Config.ELDER_ARENA_LINGER_SECONDS;
			// (on peaceful nothing would stay risen to fight; no rays or young to tidy away, and it never drinks time here)
			helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
			Config.STARVED_RISE_SECONDS = 2;
			Config.STARVED_RAYS = 0;
			Config.STARVED_MINIONS = 0;
			Config.STARVED_DRINK_AT = 0;
			Config.STARVED_ARENA = 0;
			// The Heart's part of the world, held loaded while the test runs (the Starved One rises over its middle)
			int cx = MendingService.HX >> 4, cz = MendingService.HZ >> 4;
			for (int dx = -chunks; dx <= chunks; dx++) {
				for (int dz = -chunks; dz <= chunks; dz++) {
					seam.getChunk(cx + dx, cz + dz);
					seam.setChunkForced(cx + dx, cz + dz, true);
					forced.add(new long[] {cx + dx, cz + dz});
				}
			}
			p = creative ? TestPlayers.creative(helper, name) : TestPlayers.survivor(helper, name);
		}

		/** The player goes to a point in the Seam (a test player hangs where it is put). */
		void to(double x, double y, double z) {
			p.teleportTo(seam, x, y, z, java.util.Set.of(), 0, 0, true);
		}

		/** Everything put back: the Starved One gone, the settings, the world's difficulty, the loaded ground, the player. */
		void end() {
			if (ended) {
				return;
			}
			ended = true;
			StarvedOne so = MendingService.driven(seam);
			if (so != null) {
				MendingService.fade(so);
			}
			Config.STARVED_RISE_SECONDS = rise;
			Config.ELDER_ARENA_BUILD_SECONDS = build;
			Config.ELDER_ARENA_DISSOLVE_SECONDS = dissolve;
			Config.STARVED_DRINK_AT = drinkAt;
			Config.STARVED_LEAVE_SECONDS = leave;
			Config.STARVED_ARENA = arena;
			Config.STARVED_RAYS = rays;
			Config.STARVED_MINIONS = minions;
			Config.ELDER_ARENA_FLIGHT_TICKS = flight;
			Config.ELDER_ARENA_LINGER_SECONDS = linger;
			for (long[] c : forced) {
				seam.setChunkForced((int) c[0], (int) c[1], false);
			}
			helper.getLevel().getServer().setDifficulty(difficulty, true);
			// (sent into the Seam for the test, the player leaves: nothing waits on the ground round them)
			helper.getLevel().getServer().getPlayerList().remove(p);
		}

		/** A step of the test after {@code delay} ticks; a failing check puts everything back at once. */
		void after(int delay, Runnable step) {
			helper.runAfterDelay(delay, () -> {
				try {
					step.run();
				} catch (RuntimeException e) {
					end();
					throw e;
				}
			});
		}
	}

	/** The old reach of its fight (before 3.48): a ball round the Heart, 84 blocks every way. */
	private static boolean inOldBall(double x, double y, double z) {
		double dx = x - (MendingService.HX + 0.5), dy = y - MendingService.HY, dz = z - (MendingService.HZ + 0.5);
		return dx * dx + dy * dy + dz * dz < 84.0 * 84.0;
	}

	/**
	 * Its fight reaches the whole of the Heart's island group: each pillar's island, its node high up its shaft and the
	 * top of the shaft over it, the strands between, the islands' undersides; not past them. The top of a node far out
	 * on its shaft lay outside the old ball, which is what left a lone player there to see it sink back.
	 */
	/**
	 * The Heart lies in the Seam, which the dedicated server's test world does not have (the client test, which has it,
	 * raises the Starved One there for its shots): a test that fights at the Heart passes over itself there, as the
	 * Seam's parts of the other tests do.
	 */
	private static boolean noSeam(GameTestHelper helper) {
		if (helper.getLevel().getServer().getLevel(SeamService.SEAM) != null) {
			return false;
		}
		helper.succeed();
		return true;
	}

	/** Its wait with nobody at the fight: it sinks back only once the grace is over, and anyone coming back starts it over. */
	@GameTest
	public void itWaitsOutTheGraceBeforeItSinksBack(GameTestHelper helper) {
		int leave = Config.STARVED_LEAVE_SECONDS;
		try {
			Config.STARVED_LEAVE_SECONDS = 1;
			MendingService.waitedOut(false);
			for (int i = 1; i < 20; i++) {
				check(helper, !MendingService.waitedOut(true), "it sank back after %s ticks of a 20-tick grace", i);
			}
			check(helper, !MendingService.waitedOut(false), "someone at the fight did not start the wait over");
			for (int i = 1; i < 20; i++) {
				check(helper, !MendingService.waitedOut(true), "after someone came back it sank back after %s ticks", i);
			}
			check(helper, MendingService.waitedOut(true), "it did not sink back once the grace was over");
			Config.STARVED_LEAVE_SECONDS = 0;
			MendingService.waitedOut(false);
			check(helper, MendingService.waitedOut(true), "with no grace it did not sink back at once");
		} finally {
			Config.STARVED_LEAVE_SECONDS = leave;
			MendingService.waitedOut(false);
		}
		helper.succeed();
	}

	@GameTest
	public void theHeartsFightReachesEveryPillarAndItsShaft(GameTestHelper helper) {
		for (int i = 0; i < Binding.PILLARS; i++) {
			int[] c = Binding.pillar(i);
			double ox = c[0] - MendingService.HX, oz = c[1] - MendingService.HZ, len = Math.sqrt(ox * ox + oz * oz);
			// on top of a node, on the far side of its shaft, and higher up the shaft still
			double nx = c[0] + 0.5 + ox / len * 3, nz = c[1] + 0.5 + oz / len * 3;
			check(helper, MendingService.within(nx, MendingService.HY + 56, nz), "the top of pillar %s's node is not at the fight", i);
			check(helper, !inOldBall(nx, MendingService.HY + 56, nz), "the top of pillar %s's node lay within the old reach after all", i);
			check(helper, MendingService.within(c[0] + 0.5, MendingService.HY + 120, c[1] + 0.5), "high up pillar %s's shaft is not at the fight", i);
			check(helper, MendingService.within(c[0] + 0.5 + ox / len * 14, MendingService.HY - 30, c[1] + 0.5 + oz / len * 14),
					"under the far edge of pillar %s's island is not at the fight", i);
			check(helper, MendingService.within(MendingService.HX + 0.5 + ox / 2, MendingService.HY - 2, MendingService.HZ + 0.5 + oz / 2),
					"the strand out to pillar %s is not at the fight", i);
		}
		check(helper, MendingService.within(MendingService.HX + 0.5, MendingService.HY + 1, MendingService.HZ + 0.5), "the altar is not at the fight");
		check(helper, !MendingService.within(MendingService.HX + 200, MendingService.HY, MendingService.HZ), "200 blocks out is still at the fight");
		check(helper, !MendingService.within(MendingService.HX, MendingService.HY + 400, MendingService.HZ), "400 blocks up is still at the fight");
		check(helper, !MendingService.within(MendingService.HX, MendingService.HY - 120, MendingService.HZ), "120 blocks down is still at the fight");
		helper.succeed();
	}

	/**
	 * A lone player who flew out to a pillar's node keeps the fight going (3.48); with nobody at the fight it waits for
	 * the grace ({@code starved_leave_seconds}) over the Heart, and only then sinks back.
	 */
	@GameTest(environment = Alone.HEART_LEAVE, maxTicks = 260)
	public void aLonePlayerAtAPillarKeepsTheFightAndItSinksBackOnlyAfterTheGrace(GameTestHelper helper) {
		if (noSeam(helper)) {
			return;
		}
		Scene sc = new Scene(helper, "test-heart-leave", false, 1);
		int[] c = Binding.pillar(0);
		double ox = c[0] - MendingService.HX, oz = c[1] - MendingService.HZ, len = Math.sqrt(ox * ox + oz * oz);
		double nx = c[0] + 0.5 + ox / len * 3, ny = MendingService.HY + 56, nz = c[1] + 0.5 + oz / len * 3;
		try {
			Config.STARVED_LEAVE_SECONDS = 2;
			sc.to(nx, ny, nz);
			check(helper, MendingService.inArena(sc.p), "the player on top of the node does not count as at the fight");
			MendingService.wakeNow(sc.seam, sc.p);
			check(helper, MendingService.active() && MendingService.driven(sc.seam) != null, "the Starved One did not rise for the player at the pillar");
		} catch (RuntimeException e) {
			sc.end();
			throw e;
		}
		// Risen (two seconds) and well past the grace (another two), it is still there for the one at the node
		sc.after(110, () -> {
			check(helper, MendingService.active() && MendingService.driven(sc.seam) != null && !MendingService.risingNow(),
					"the Starved One sank back with a player at a pillar's node");
			// ... who goes far away: nobody is at its fight now
			sc.to(MendingService.HX + 400.5, MendingService.HY, MendingService.HZ + 400.5);
		});
		sc.after(130, () -> check(helper, MendingService.active() && MendingService.driven(sc.seam) != null,
				"the Starved One sank back the moment nobody was at its fight, before its grace was up"));
		sc.after(190, () -> {
			check(helper, !MendingService.active(), "the Starved One did not sink back once its grace was up with nobody at its fight");
			sc.end();
			helper.succeed();
		});
	}

	/**
	 * Nothing hurts it while it rises out of the Heart, and blows land once it is free; its fight changes below
	 * {@code starved_phase_one} (66%) and again below {@code starved_phase_two} (33%), once each.
	 */
	@GameTest(environment = Alone.HEART_RISE, maxTicks = 200)
	public void itCannotBeHurtWhileRisingAndItsFightChangesAt66And33(GameTestHelper helper) {
		if (noSeam(helper)) {
			return;
		}
		Scene sc = new Scene(helper, "test-heart-rise", true, 1);
		StarvedOne[] so = new StarvedOne[1];
		try {
			check(helper, Config.STARVED_PHASE_ONE == 0.66 && Config.STARVED_PHASE_TWO == 0.33, "its fight does not change at 66%% and 33%% by default");
			sc.to(MendingService.HX + 0.5, MendingService.HY + 1, MendingService.HZ + 4.5);
			MendingService.wakeNow(sc.seam, sc.p);
			so[0] = MendingService.driven(sc.seam);
			check(helper, so[0] != null && so[0].isRising() && MendingService.risingNow(), "the Starved One is not rising as it comes");
			float full = so[0].getHealth();
			check(helper, !so[0].hurtServer(sc.seam, sc.seam.damageSources().generic(), 20f) && so[0].getHealth() == full,
					"a blow landed on it while it rose (%s of %s health left)", so[0].getHealth(), full);
		} catch (RuntimeException e) {
			sc.end();
			throw e;
		}
		sc.after(20, () -> {
			check(helper, so[0].isRising(), "it was free of the Heart before its rise was over");
			float full = so[0].getHealth();
			check(helper, !so[0].hurtServer(sc.seam, sc.seam.damageSources().generic(), 20f) && so[0].getHealth() == full, "a blow landed on it halfway up");
		});
		sc.after(70, () -> {
			check(helper, !so[0].isRising() && !MendingService.risingNow(), "it is still rising after its rise was over");
			float full = so[0].getHealth();
			so[0].hurtServer(sc.seam, sc.seam.damageSources().generic(), 20f);
			check(helper, so[0].getHealth() < full, "a blow did not land on it once it was free (%s of %s)", so[0].getHealth(), full);
			check(helper, MendingService.stage() == 0, "its fight changed at %s of its health", so[0].getHealth() / so[0].getMaxHealth());
			so[0].setHealth(so[0].getMaxHealth() * 0.67f);
		});
		sc.after(80, () -> {
			check(helper, MendingService.stage() == 0, "its fight changed above 66%%");
			so[0].setHealth(so[0].getMaxHealth() * 0.65f);
		});
		sc.after(90, () -> {
			check(helper, MendingService.stage() == 1, "its fight did not change below 66%% (stage %s)", MendingService.stage());
			so[0].setHealth(so[0].getMaxHealth() * 0.34f);
		});
		sc.after(100, () -> {
			check(helper, MendingService.stage() == 1, "its fight changed again above 33%% (stage %s)", MendingService.stage());
			so[0].setHealth(so[0].getMaxHealth() * 0.32f);
		});
		sc.after(110, () -> {
			check(helper, MendingService.stage() == 2, "its fight did not change below 33%% (stage %s)", MendingService.stage());
			// (healed again, it does not change back, nor again)
			so[0].setHealth(so[0].getMaxHealth());
		});
		sc.after(120, () -> {
			check(helper, MendingService.stage() == 2, "its fight changed once more after it healed (stage %s)", MendingService.stage());
			sc.end();
			helper.succeed();
		});
	}

	/**
	 * The Heart raises its arena round the fight: its blocks are built into the open air (held by FightBlocks) and leave
	 * the pillars, their strands and the altar free; once the fight is over (here: it sinks back) every block of it is
	 * gone again, none of them held any more.
	 */
	@GameTest(environment = Alone.HEART_ARENA, maxTicks = 500)
	public void theHeartsArenaRisesAndLeavesNothingBehind(GameTestHelper helper) {
		if (noSeam(helper)) {
			return;
		}
		Scene sc = new Scene(helper, "test-heart-arena", true, 4);
		StarvedOne[] so = new StarvedOne[1];
		List<BlockPos> placed = new ArrayList<>();
		try {
			Config.STARVED_ARENA = 1;
			Config.ELDER_ARENA_BUILD_SECONDS = 1;
			Config.ELDER_ARENA_FLIGHT_TICKS = 10;
			Config.ELDER_ARENA_LINGER_SECONDS = 0;
			Config.ELDER_ARENA_DISSOLVE_SECONDS = 1;
			sc.to(MendingService.HX + 0.5, MendingService.HY + 1, MendingService.HZ + 4.5);
			MendingService.wakeNow(sc.seam, sc.p);
			so[0] = MendingService.driven(sc.seam);
			check(helper, so[0] != null && HeartArena.has(so[0]), "the Heart raised no arena as the Starved One came");
		} catch (RuntimeException e) {
			sc.end();
			throw e;
		}
		sc.after(160, () -> {
			placed.addAll(ElderArena.placedBy(so[0]));
			check(helper, placed.size() > 500, "the Heart's arena stands with only %s blocks", placed.size());
			for (BlockPos q : placed) {
				check(helper, FightBlocks.holds(sc.seam, q), "a block of the Heart's arena at %s is not held for the fight", q);
				// The pillars, their strands and the altar are left free
				for (int i = 0; i < Binding.PILLARS; i++) {
					int[] c = Binding.pillar(i);
					double px = q.getX() + 0.5 - c[0], pz = q.getZ() + 0.5 - c[1];
					check(helper, px * px + pz * pz > 14 * 14, "the Heart's arena built at %s, on pillar %s's island", q, i);
					double ux = c[0] - MendingService.HX, uz = c[1] - MendingService.HZ, len = Math.sqrt(ux * ux + uz * uz);
					double dx = q.getX() + 0.5 - (MendingService.HX + 0.5), dz = q.getZ() + 0.5 - (MendingService.HZ + 0.5);
					double along = (dx * ux + dz * uz) / len, side = Math.abs(dx * uz - dz * ux) / len;
					check(helper, !(along > MendingService.HR - 1 && along < len - 8 && side < 3.5 && q.getY() > MendingService.HY - 12),
							"the Heart's arena built at %s, on the way out to pillar %s", q, i);
				}
				double ax = q.getX() + 0.5 - (MendingService.HX + 0.5), az = q.getZ() + 0.5 - (MendingService.HZ + 0.5);
				// (the room a player stands in over the altar, where the Thread and the Shears are laid)
				check(helper, ax * ax + az * az > 2.5 * 2.5 || q.getY() > MendingService.HY + 12 || q.getY() < MendingService.HY,
						"the Heart's arena built at %s, over the altar", q);
			}
			// The fight is over: it sinks back, and the arena comes apart at once
			MendingService.fade(so[0]);
		});
		sc.after(320, () -> {
			check(helper, !HeartArena.has(so[0]) && ElderArena.placedBy(so[0]).isEmpty(), "the Heart's arena still stands after its fight was over");
			for (BlockPos q : placed) {
				check(helper, !FightBlocks.holds(sc.seam, q) && !ElderArena.holds(sc.seam, q), "a block of the Heart's arena at %s was left behind", q);
			}
			sc.end();
			helper.succeed();
		});
	}

	/**
	 * Nothing the Heart's arena is built of changes of itself while it stands (grows, spreads, decays, burns or falls),
	 * so exactly what it put up comes down again. (Its design is only looked at, where the Seam is loaded: nothing is
	 * built.)
	 */
	@GameTest
	public void theHeartsArenaIsMadeOfNothingThatChangesOfItself(GameTestHelper helper) {
		if (noSeam(helper)) {
			return;
		}
		ServerLevel seam = helper.getLevel().getServer().getLevel(SeamService.SEAM);
		check(helper, seam != null, "there is no Seam");
		String bad = ElderArena.changesOfItself(seam, HeartArena.STYLE, new BlockPos(MendingService.HX, MendingService.HY + 1, MendingService.HZ), 4848L);
		check(helper, bad == null, "the Heart's arena is built of what changes of itself: %s", bad);
		helper.succeed();
	}

	/**
	 * Its sounds are its own where they are, and something in their place where they are not: none is ever missing,
	 * and its music and sounds sent where none of them is registered throw nothing.
	 */
	@GameTest
	public void theStarvedOnesSoundsFallBackWhenMissing(GameTestHelper helper) {
		check(helper, StarvedOne.sound("no_such_sound_for_the_test", "entity.warden.ambient") != null, "a missing sound of its own left nothing in its place");
		check(helper, StarvedOne.sound("no_such_sound_for_the_test", "no_such_vanilla_sound_either") != null,
				"a missing sound with a missing fallback left nothing in its place");
		for (String name : new String[] {"ambient", "hurt", "death", "voice", "roar", "wake", "swoop", "call", "ray", "drink", "heal", "quench", "bound",
				"break_free", "phase", "ultimate", "upheaval"}) {
			check(helper, StarvedOne.sound(name, "entity.warden.ambient") != null, "its %s sound is missing, with nothing in its place", name);
		}
		ServerPlayer p = TestPlayers.survivor(helper, "test-heart-sounds");
		ServerLevel level = helper.getLevel();
		ElderFx.music(level, q -> q == p, ElderFx.MUSIC_STARVED_ONE);
		ElderFx.silence(level, q -> q == p, ElderFx.MUSIC_STARVED_ONE);
		ElderFx.cinema(level, q -> q == p, ElderFx.CINEMA_STARVED_ONE, com.extremedimensions.network.ModPackets.BossFxPayload.FLASH, 0, 0.1f, "");
		WorldFx.soundIn(level.dimension().identifier().toString(), "extremedimensions:elder.starved_one.no_such_sound", "hostile", p.getX(), p.getY(), p.getZ(),
				1f, 1f);
		WorldFx.soundSelf(p, "extremedimensions:elder.starved_one.roar", 0.1f, 1f);
		helper.succeed();
	}
}
