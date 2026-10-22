package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.anchor.SoulWards;
import com.extremedimensions.binding.Homebound;
import com.extremedimensions.binding.Veilsight;
import com.extremedimensions.binding.WoundLens;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Echo;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderArena;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.Matriarch;
import com.extremedimensions.elder.MatriarchService;
import com.extremedimensions.elder.Weaver;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.events.Echoes;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.ServerEvents;
import com.extremedimensions.server.TickProfile;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * What the mod costs a server. Each test logs a {@code PERF} line and fails only far above any sane cost, so a slow
 * runner never fails it but a real regression (a loop gone quadratic, a command in a hot path) does.
 */
public class PerfTests {
	private static void report(String what, String result) {
		ExtremeDimensions.LOGGER.info("PERF {}: {}", what, result);
	}

	/** Milliseconds the JVM has spent collecting garbage so far, over all its collectors. */
	private static long gcMillis() {
		long ms = 0;
		for (java.lang.management.GarbageCollectorMXBean gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
			ms += Math.max(0, gc.getCollectionTime());
		}
		return ms;
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

	/**
	 * Ten players carrying the bindings: the Homebound count, the Veilsight shimmer and the Wound Lens's reading, as
	 * often as the game asks for them (each once or twice a second per player), and a soul ward looked for round fifty
	 * anchors. Logged per second of play.
	 */
	@GameTest(maxTicks = 100)
	public void bindingsForTenPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> homebound = ModEnchantments.holder(level.registryAccess(),
				ModEnchantments.HOMEBOUND).orElseThrow(), veilsight = ModEnchantments.holder(level.registryAccess(), ModEnchantments.VEILSIGHT).orElseThrow();
		List<ServerPlayer> players = new ArrayList<>();
		net.minecraft.core.BlockPos o = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
		for (int i = 0; i < 10; i++) {
			ServerPlayer p = TestPlayers.survivor(helper);
			p.teleportTo(o.getX() + 0.5 + i % 3, o.getY(), o.getZ() + 0.5 + i / 3);
			ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE), helmet = new ItemStack(Items.IRON_HELMET);
			chest.enchant(homebound, 1);
			helmet.enchant(veilsight, 1);
			p.setItemSlot(EquipmentSlot.CHEST, chest);
			p.setItemSlot(EquipmentSlot.HEAD, helmet);
			for (int k = 0; k < 30; k++) {
				p.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
			}
			players.add(p);
		}
		for (int warm = 0; warm < 20; warm++) {
			bindings(players);
		}
		int rounds = 100;
		long t0 = System.nanoTime();
		for (int i = 0; i < rounds; i++) {
			bindings(players);
		}
		double perSecond = (System.nanoTime() - t0) / 1e6 / rounds;
		long t1 = System.nanoTime();
		for (int i = 0; i < rounds; i++) {
			for (int a = 0; a < 50; a++) {
				SoulWards.warded(level, o.getX() + a * 7, o.getY(), o.getZ() - 40);
			}
		}
		double wards = (System.nanoTime() - t1) / 1e6 / rounds;
		report("bindings, 10 players carrying them", String.format(java.util.Locale.ROOT,
				"%.3f ms per second of play (%.2f%% of a second's ticks), soul wards round 50 anchors %.3f ms", perSecond, perSecond / 1000 * 100, wards));
		for (ServerPlayer p : players) {
			p.getInventory().clearContent();
		}
		CurseTests.check(helper, perSecond < 20, "the bindings of 10 players take %s ms per second", perSecond);
		CurseTests.check(helper, wards < 20, "looking for soul wards round 50 anchors takes %s ms", wards);
		helper.succeed();
	}

	/** One second of the bindings for every player: the Homebound count, the shimmer, and two readings of the lens. */
	private static void bindings(List<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			Homebound.pieces(p);
			Veilsight.shimmer(p);
			WoundLens.reading(p);
			WoundLens.reading(p);
		}
	}

	/**
	 * Thirty-two echoes round a player who does not listen: shown once, then only their shapes are drawn. Twenty
	 * seconds of them must not run their chains up and down again (each show and hide runs commands).
	 */
	@GameTest(maxTicks = 200)
	public void echoesStayPut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper);
		net.minecraft.core.BlockPos o = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
		p.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
		List<Echo> made = new ArrayList<>();
		String ending = DataStore.ending();
		DataStore.setEnding(null);
		try {
			for (int i = 0; i < 32; i++) {
				double a = i * Math.PI * 2 / 32, r = 10 + (i % 3) * 4;
				Echo e = new Echo(Dims.of(level), o.getX() + (int) Math.round(Math.cos(a) * r), o.getY(), o.getZ() + (int) Math.round(Math.sin(a) * r),
						java.util.UUID.randomUUID().toString(), "perf" + i, 0L, 10);
				made.add(e);
				DataStore.echoes().add(e);
			}
			net.minecraft.server.MinecraftServer s = level.getServer();
			Echoes.step(s);
			int shown = (int) made.stream().filter(e -> e.shown).count();
			int before = WorldFx.commandsRun;
			long t0 = System.nanoTime();
			for (int i = 0; i < 40; i++) {
				Echoes.step(s);
			}
			double stepMs = (System.nanoTime() - t0) / 1e6 / 40;
			int commands = WorldFx.commandsRun - before;
			report("echoes, 32 round one player", String.format(java.util.Locale.ROOT, "%d shown, %.3f ms per half second, %d commands in twenty seconds",
					shown, stepMs, commands));
			CurseTests.check(helper, shown == 32, "only %s of 32 echoes showed to the player near them", shown);
			// (other tests run alongside and may run a few of their own)
			CurseTests.check(helper, commands < 40, "the echoes ran %s commands in twenty seconds: they are put up and taken down again", commands);
			CurseTests.check(helper, stepMs < 10, "a half second of 32 echoes takes %s ms", stepMs);
		} finally {
			for (Echo e : made) {
				Echoes.forget(e);
			}
			DataStore.setEnding(ending);
		}
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
		CurseTests.check(helper, kinds == 8 && queued > 1000, "the showcase designed %s structures with %s blocks", kinds, queued);
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
				long gc = gcMillis(), t = System.nanoTime();
				SeamArchitect.tick(level, budget);
				// (what the JVM's garbage collection took meanwhile is the heap's, shared with every test's world, not the
				// building's)
				double ms = Math.max(0, (System.nanoTime() - t) / 1e6 - (gcMillis() - gc));
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
		});
		// Then, with the queue to itself, the Seam building itself before a watcher's eyes
		FrayTests.seamBuildsBeforeAWatcher(helper, 82);
	}

	/**
	 * Server ticks timed while a test listens: for each, the whole tick (the worlds, their creatures, the mod), the mod's
	 * own share of it, what the JVM's garbage collection took meanwhile, and the CPU time of the mod's share (while the
	 * profile listens), all in milliseconds.
	 */
	private static final class TickClock {
		private TickClock() {}

		private static boolean hooked = false;
		private static long start = 0;
		private static long gcAtStart = 0;
		private static List<double[]> heard = null;

		/** Starts listening (hooking the server's tick the first time it is asked). */
		static void listen() {
			if (!hooked) {
				hooked = true;
				ServerTickEvents.START_SERVER_TICK.register(s -> {
					start = System.nanoTime();
					gcAtStart = gcMillis();
				});
				// (hooked after the mod's own handler: it hears each tick once the mod's work is done)
				ServerTickEvents.END_SERVER_TICK.register(s -> {
					if (heard != null && start != 0) {
						heard.add(new double[] {(System.nanoTime() - start) / 1e6, ServerEvents.lastTickNanos() / 1e6, gcMillis() - gcAtStart,
								ServerEvents.lastTickCpuNanos() / 1e6});
					}
				});
			}
			heard = new ArrayList<>();
		}

		/** Stops listening: the ticks heard. */
		static List<double[]> stop() {
			List<double[]> h = heard == null ? List.of() : heard;
			heard = null;
			return h;
		}
	}

	/** {average, 95th percentile, worst} of one column of the ticks heard, less each tick's garbage collection if asked. */
	private static double[] spread(List<double[]> ticks, int column, boolean lessGc) {
		if (ticks.isEmpty()) {
			return new double[] {0, 0, 0};
		}
		double[] v = new double[ticks.size()];
		double total = 0;
		for (int i = 0; i < v.length; i++) {
			double[] t = ticks.get(i);
			v[i] = Math.max(0, t[column] - (lessGc ? t[2] : 0));
			total += v[i];
		}
		java.util.Arrays.sort(v);
		return new double[] {total / v.length, v[(int) Math.ceil(v.length * 0.95) - 1], v[v.length - 1]};
	}

	/** An Elder for the fight test: how it is called for a player, how its ultimate is forced and how it is sent away. */
	private record Elder(String name, Class<? extends ElderBoss> kind, java.util.function.Predicate<ServerPlayer> call,
			java.util.function.Consumer<ElderBoss> ultimate, java.util.function.Consumer<ElderBoss> fade) {}

	/** Ticks the world round the fight is given to load, the quiet ticks measured before it, and the fight's own. */
	private static final int LOAD = 100, CALM = 40, FIGHT = 400;
	/** When in the fight its ultimate is forced (once it has formed); the ultimate lasts {@code elder_ultimate_seconds}. */
	private static final int ULTIMATE_AT = 160;

	@GameTest(environment = Alone.PERF_MATRIARCH, maxTicks = LOAD + CALM + FIGHT + 60)
	public void theMatriarchsFight(GameTestHelper helper) {
		int hoards = DataStore.hoards().size();
		elderFight(helper, new Elder("Matriarch", Matriarch.class, HollowService::summonMatriarch, m -> MatriarchService.force(m, ElderBoss.ULTIMATE),
				m -> MatriarchService.fade((Matriarch) m)), 6008, () -> {
					// her nest, called with her, is forgotten again
					while (DataStore.hoards().size() > hoards) {
						DataStore.hoards().remove(DataStore.hoards().size() - 1);
					}
				});
	}

	@GameTest(environment = Alone.PERF_FERRYMAN, maxTicks = LOAD + CALM + FIGHT + 60)
	public void theFerrymansFight(GameTestHelper helper) {
		elderFight(helper, new Elder("Ferryman", Ferryman.class, FerrymanService::summon, f -> FerrymanService.force(f, ElderBoss.ULTIMATE),
				f -> FerrymanService.fade((Ferryman) f)), 9008, () -> {});
	}

	@GameTest(environment = Alone.PERF_WEAVER, maxTicks = LOAD + CALM + FIGHT + 60)
	public void theWeaversFight(GameTestHelper helper) {
		elderFight(helper, new Elder("Weaver", Weaver.class, p -> WeaverService.summon(p, p.blockPosition().offset(12, 0, 0)),
				w -> WeaverService.force(w, ElderBoss.ULTIMATE), w -> WeaverService.fade((Weaver) w)), 12008, () -> {});
	}

	/**
	 * An Elder's whole fight as a player meets it: it forms while its arena rises round it at its full size (blocks
	 * flying in for the one watching), then it fights, its ultimate forced partway. Logged: what calling it cost at once,
	 * what its arena's design cost in all and in its longest step (it is drawn up over the first ticks), and every server
	 * tick of the fight against quiet ones before it, the whole tick and the mod's own share. Far out from every other
	 * test (and alone in its batch), in chunks forced round it as a player arriving there would load them; the arena is
	 * taken down and the ground put back afterwards.
	 */
	private static void elderFight(GameTestHelper helper, Elder elder, int east, Runnable tidy) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int cx = (o.getX() >> 4 << 4) + east, cz = (o.getZ() >> 4 << 4) + 8, r = 10;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				level.setChunkForced((cx >> 4) + x, (cz >> 4) + z, true);
			}
		}
		ServerPlayer p = TestPlayers.survivor(helper, "perf-" + elder.name().toLowerCase(java.util.Locale.ROOT));
		// It must live through the fight to be fought all through it
		p.getAbilities().invulnerable = true;
		BlockPos centre = new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);
		Runnable home = () -> {
			p.teleportTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
			p.setDeltaMovement(0, 0, 0);
		};
		home.run();
		ElderBoss[] boss = {null};
		double[] called = {0};
		long[][] design = {{0, 0}};
		String[] longest = {""};
		int[] flyingMost = {0}, loaded = {0};
		List<List<double[]>> heard = new ArrayList<>();
		Runnable restore = () -> {
			TickClock.stop();
			TickProfile.stop();
			if (boss[0] != null && boss[0].isAlive()) {
				elder.fade().accept(boss[0]);
			}
			ElderArena.clear();
			tidy.run();
			p.getAbilities().invulnerable = false;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					level.setChunkForced((cx >> 4) + x, (cz >> 4) + z, false);
				}
			}
		};
		// Every step is scheduled up front (a step scheduled from inside another upsets the test's list of steps)
		helper.runAfterDelay(LOAD, () -> {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					loaded[0] += level.hasChunk((cx >> 4) + x, (cz >> 4) + z) ? 1 : 0;
				}
			}
			home.run();
			TickClock.listen();
		});
		helper.runAfterDelay(LOAD + CALM, () -> {
			heard.add(TickClock.stop());
			AABB near = new AABB(centre).inflate(64);
			List<? extends ElderBoss> had = level.getEntitiesOfClass(elder.kind(), near);
			TickClock.listen();
			TickProfile.listen();
			long t = System.nanoTime();
			boolean came = elder.call().test(p);
			called[0] = (System.nanoTime() - t) / 1e6;
			boss[0] = level.getEntitiesOfClass(elder.kind(), near).stream().filter(b -> !had.contains(b)).findFirst().orElse(null);
			if (!came || boss[0] == null) {
				restore.run();
			}
			CurseTests.check(helper, came && boss[0] != null, "the %s did not come", elder.name());
		});
		for (int t = LOAD + CALM + 1; t <= LOAD + CALM + FIGHT; t++) {
			int now = t - LOAD - CALM;
			helper.runAfterDelay(t, () -> {
				flyingMost[0] = Math.max(flyingMost[0], ElderArena.flying());
				if (now % 20 == 0) {
					home.run();
				}
				if (now == ULTIMATE_AT && boss[0] != null && boss[0].isAlive()) {
					elder.ultimate().accept(boss[0]);
				}
			});
		}
		helper.runAfterDelay(LOAD + CALM + FIGHT + 1, () -> {
			List<double[]> fight = TickClock.stop();
			// The parts of the mod's tick with the worst single ticks, worst first
			StringBuilder worst = new StringBuilder();
			TickProfile.stop().entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0])).limit(6)
					.forEach(e -> worst.append(worst.length() == 0 ? "" : ", ").append(String.format(java.util.Locale.ROOT, "%s %.1f ms (CPU %.1f ms; %.2f ms a tick)",
							e.getKey(), e.getValue()[0], e.getValue()[3], e.getValue()[1] / Math.max(1, e.getValue()[2]))));
			// Where the server thread stood when a part ran long: by the clock but not in CPU time, it was waiting
			for (String stall : TickProfile.stalls()) {
				report(elder.name().toLowerCase(java.util.Locale.ROOT) + " fight, a part ran long", stall);
			}
			boolean fought = boss[0] != null && boss[0].isAlive();
			int standing = ElderArena.standing();
			// (its design is drawn up over the first ticks of the fight)
			design[0] = ElderArena.lastDesign();
			longest[0] = ElderArena.lastDesignStep();
			restore.run();
			double[] calm = spread(heard.get(0), 0, true), whole = spread(fight, 0, true), mod = spread(fight, 1, false);
			// The JVM's garbage collection in the fight's ticks (the heap is every world's), and the mod's worst tick
			// among those it did not run in
			double gcAll = 0, gcWorst = 0;
			List<double[]> clean = new ArrayList<>();
			for (double[] tick : fight) {
				gcAll += tick[2];
				gcWorst = Math.max(gcWorst, tick[2]);
				if (tick[2] == 0) {
					clean.add(tick);
				}
			}
			double[] modClean = spread(clean, 1, false);
			// The CPU time of the mod's work: what the mod costs, apart from its thread waiting its turn on a busy machine
			double[] cpu = spread(fight, 3, false);
			double slowestCpu = 0, slowest = -1;
			for (double[] tick : fight) {
				if (tick[1] > slowest) {
					slowest = tick[1];
					slowestCpu = tick[3];
				}
			}
			report(elder.name().toLowerCase(java.util.Locale.ROOT) + " fight with its arena", String.format(java.util.Locale.ROOT,
					"called in %.1f ms (its arena of %d blocks designed in %.1f ms over %d ticks, its longest step %.1f ms, the most CPU one took %.1f ms, %s); %d ticks: the mod's own work %.2f ms a tick on average, "
							+ "95%% within %.2f ms, worst %.2f ms; the whole server tick %.2f ms on average, 95%% within %.2f ms, worst %.2f ms "
							+ "(quiet before it: %.2f, %.2f, %.2f ms); at most %d blocks in the air, %d standing at the end; still fighting %s; "
							+ "%d of %d chunks loaded at the start; garbage collection %.0f ms in all, at most %.0f ms in a tick, and in the %d ticks "
							+ "without it the mod's worst was %.2f ms; the mod's CPU time %.2f ms a tick on average, 95%% within %.2f ms, worst %.2f ms, "
							+ "and %.2f ms in its slowest tick; the worst ticks of the mod's parts: %s",
					called[0], design[0][1], design[0][0] / 1e6, design[0][2], design[0][3] / 1e6, design[0][4] / 1e6, longest[0], fight.size(), mod[0], mod[1], mod[2], whole[0], whole[1], whole[2], calm[0], calm[1],
					calm[2], flyingMost[0], standing, fought, loaded[0], (2 * r + 1) * (2 * r + 1), gcAll, gcWorst, clean.size(), modClean[2], cpu[0], cpu[1], cpu[2], slowestCpu, worst));
			CurseTests.check(helper, design[0][1] > 1000 && flyingMost[0] > 0, "no arena rose round the %s (%s blocks, %s at most in the air)",
					elder.name(), design[0][1], flyingMost[0]);
			// Far above any sane cost, as the other tests here: a slow runner never fails these, a real regression does
			// Its arena is designed a little each tick, never all at once when it is called
			CurseTests.check(helper, called[0] < 150, "calling the %s stalled the server for %s ms", elder.name(), called[0]);
			// What the mod costs is judged by its CPU time: on a shared machine its thread can wait its turn for far longer
			CurseTests.check(helper, design[0][4] < 60_000_000L, "one step of the %s's arena design (%s) took %s ms of CPU time", elder.name(), longest[0],
					design[0][4] / 1e6);
			CurseTests.check(helper, cpu[1] < 25, "95%% of the %s's fight ticks took the mod up to %s ms of CPU time", elder.name(), cpu[1]);
			CurseTests.check(helper, cpu[2] < 250, "a tick of the %s's fight took the mod %s ms of CPU time", elder.name(), cpu[2]);
			helper.succeed();
		});
	}
}
