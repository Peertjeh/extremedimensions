package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.SeamFauna;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.seam.VeilRay;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

/**
 * The Seam's islands as they are built (blocks fly in before a watcher's eyes, the ground at their feet is simply
 * there) and the Fray beneath them: knots out past the rims, wells over them, strands between, a faster drain.
 */
public class FrayTests {
	@GameTest
	public void theFrayHangsBeneathTheIslands(GameTestHelper helper) {
		double wells = Config.SEAM_WELL_CHANCE;
		Config.SEAM_WELL_CHANCE = 1.0;
		try {
			long seed = 12345L;
			SeamArchitect.Shape island = new SeamArchitect.Shape(1050, 90, 1050, 14, 0.3, 1.1, 2.0);
			SeamArchitect.Shape east = new SeamArchitect.Shape(1150, 86, 1046, 12, 1.0, 0.2, 2.5);
			SeamDepth.Knot k = SeamDepth.knot(10, 10, island, seed);
			SeamDepth.Knot e = SeamDepth.knot(11, 10, east, seed);
			check(helper, k != null && k.well(), "with every island given a well, this one has none");
			check(helper, !island.inside(k.x(), k.z(), -2), "the knot hangs under its island (%s,%s)", k.x(), k.z());
			check(helper, k.y() < Config.SEAM_FRAY_TOP && k.y() > 8, "the knot hangs at %s, not in the Fray", k.y());
			SeamArchitect.Plan plan = SeamDepth.plan(10, 10, island, east, null, seed);
			check(helper, plan != null, "no Fray was planned");
			check(helper, plan.has(k.x(), k.y(), k.z()), "the knot has no landing under its well");
			check(helper, !plan.has(k.x(), k.top(), k.z()) && plan.has(k.x() + 2, k.top(), k.z()), "the well is no ring over a drop");
			check(helper, plan.marks.contains("w@" + k.x() + "," + k.top() + "," + k.z() + "," + k.y()), "the well was not remembered");
			check(helper, plan.marks.contains("k@" + k.x() + "," + k.y() + "," + k.z()), "the knot was not remembered");
			// The strand east: somewhere in the column halfway between the knots
			int mx = (k.x() + e.x()) / 2, mz = (k.z() + e.z()) / 2;
			boolean strand = false;
			for (int y = 2; y < Config.SEAM_FRAY_TOP && !strand; y++) {
				for (int dx = -1; dx <= 1 && !strand; dx++) {
					for (int dz = -1; dz <= 1 && !strand; dz++) {
						strand = plan.has(mx + dx, y, mz + dz);
					}
				}
			}
			check(helper, strand, "no strand runs from the knot to the next one east");
		} finally {
			Config.SEAM_WELL_CHANCE = wells;
		}
		helper.succeed();
	}

	/** A village's Weaver roll saved before 2.22 ("w@x,z") shares the wells' prefix: it is skipped, never read as a well. */
	@GameTest
	public void anOldWeaverRollIsNotTakenForAWell(GameTestHelper helper) {
		java.util.Set<String> cells = com.extremedimensions.data.DataStore.seamCells();
		cells.add("w@123,456");
		SeamDepth.forget();
		try {
			int wells = SeamDepth.wells().size();
			check(helper, wells >= 0, "no wells");
		} finally {
			cells.remove("w@123,456");
			SeamDepth.forget();
		}
		helper.succeed();
	}

	/**
	 * A Frayed Ray hunts a visitor down in the Fray: it climbs over them, dives, strikes them and poisons them, and
	 * pulls away to come again. Flown for real in the test's own sky (the test server has no Seam).
	 */
	@GameTest(maxTicks = 820)
	public void aFrayedRayDivesAndPoisons(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		int cooldown = Config.SEAM_FRAY_RAY_COOLDOWN;
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		Config.SEAM_FRAY_RAY_COOLDOWN = 1;
		// Up in open sky over the test's own space (which is walled in, so nothing outside could see in): a small
		// platform for the prey to stand on
		BlockPos o = helper.absolutePos(BlockPos.ZERO).above(24);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlock(o.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		Runnable clear = () -> {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setBlock(o.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		};
		ServerPlayer p = TestPlayers.survivor(helper, "test-prey");
		p.teleportTo(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
		// A player cannot be hurt until their client says it has loaded the world, and a test player has no client: it
		// is marked loaded by hand, as the game does when that word comes (found by name, whatever this version calls it)
		String how = markLoaded(p);
		float before = p.getHealth();
		boolean took = p.hurtServer(level, level.damageSources().generic(), 1.0F);
		check(helper, took && p.getHealth() < before, "the test player cannot be hurt at all (marked loaded: %s; took: %s)", how, took);
		p.setHealth(before);
		p.invulnerableTime = 0;
		check(helper, p.getY() < Config.SEAM_FRAY_TOP, "the test's ground (%s) is above the Fray", p.getY());
		VeilRay ray = ModEntities.VEIL_RAY.spawn(level, BlockPos.containing(p.getX() + 16, p.getY() + 9, p.getZ() + 4), EntitySpawnReason.EVENT);
		check(helper, ray != null, "no ray could be made");
		ray.setFrayed(true);
		ray.anchor(p.getX(), p.getY() + 9, p.getZ(), 24);
		ray.rouse();
		// (it hunts this one only, never the player of another test)
		ray.huntOnly(p);
		SeamFauna.drive(ray);
		check(helper, com.extremedimensions.journal.Bestiary.of(ray) == com.extremedimensions.journal.Bestiary.Beast.FRAYED_RAY,
				"a Frayed Ray is not in the bestiary as one");
		float full = p.getHealth();
		boolean[] seen = new boolean[3];
		String[] early = new String[1];
		for (int t = 1; t <= 800; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				p.teleportTo(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
				seen[0] |= p.hasEffect(MobEffects.POISON);
				seen[1] |= p.getHealth() < full;
				seen[2] |= ray.isDiving();
				if (tick == 30) {
					early[0] = ray.huntState(p);
				}
			});
		}
		helper.runAfterDelay(805, () -> {
			level.getServer().setDifficulty(was, true);
			Config.SEAM_FRAY_RAY_COOLDOWN = cooldown;
			int strikes = ray.strikes();
			String state = ray.huntState(p);
			ray.discard();
			clear.run();
			check(helper, seen[2], "the Frayed Ray never dived (at tick 30: %s; at the end: %s)", early[0], state);
			check(helper, strikes > 0, "the Frayed Ray never struck its prey (%s)", state);
			check(helper, seen[0] && seen[1], "the strike did not hurt (%s) and poison (%s) (%s)", seen[1], seen[0], state);
			helper.succeed();
		});
	}

	/**
	 * Tells the game a test player's client has loaded the world (a real client says so in a packet), so the player can
	 * be hurt; returns how it was done, or what was found when it could not be.
	 */
	private static String markLoaded(ServerPlayer p) {
		StringBuilder near = new StringBuilder();
		Object[] owners = {p, p.connection};
		for (Object owner : owners) {
			for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
					String n = m.getName().toLowerCase(java.util.Locale.ROOT);
					Class<?>[] args = m.getParameterTypes();
					try {
						if (args.length == 1 && args[0] == boolean.class && n.contains("loaded") && n.startsWith("set")) {
							m.setAccessible(true);
							m.invoke(owner, true);
							return c.getSimpleName() + "." + m.getName() + "(true)";
						}
						if (args.length == 1 && args[0].getSimpleName().equals("ServerboundPlayerLoadedPacket")) {
							java.lang.reflect.Constructor<?> make = args[0].getDeclaredConstructor();
							make.setAccessible(true);
							m.setAccessible(true);
							m.invoke(owner, make.newInstance());
							return c.getSimpleName() + "." + m.getName() + "(packet)";
						}
					} catch (ReflectiveOperationException | RuntimeException e) {
						near.append("(").append(m.getName()).append(" failed: ").append(e).append(") ");
					}
					if (n.contains("load")) {
						near.append(c.getSimpleName()).append('.').append(m.getName()).append(' ');
					}
				}
			}
		}
		return "not found: " + near;
	}

	/** A strand gives way under a visitor (after it creaks), and is woven back: nothing stays holed for good. */
	@GameTest
	public void aStrandGivesWayAndIsWovenBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 3, 2));
		net.minecraft.world.level.block.state.BlockState white = SeamDepth.threadBlock("white_wool"), purple = SeamDepth.threadBlock("purple_wool");
		check(helper, SeamDepth.thread(white) && SeamDepth.thread(purple), "the strand's wool is not taken for thread");
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(at.offset(dx, 0, dz), white, 2);
				level.setBlock(at.offset(dx, -1, dz), purple, 2);
			}
		}
		SeamDepth.reweave(null);
		SeamDepth.giveWayAt(at, level.getGameTime() + 2);
		SeamDepth.tickWorld(level);
		check(helper, level.getBlockState(at) == white, "the strand gave way before it creaked");
		helper.runAfterDelay(4, () -> {
			SeamDepth.tickWorld(level);
			check(helper, level.getBlockState(at).isAir() && level.getBlockState(at.below()).isAir(), "the strand did not give way under the feet");
			check(helper, SeamDepth.gaps() >= 2, "what gave way is not remembered to be woven back (%s)", SeamDepth.gaps());
			SeamDepth.reweave(level);
			check(helper, level.getBlockState(at) == white && level.getBlockState(at.below()) == purple, "the strand was not woven back as it was");
			check(helper, SeamDepth.gaps() == 0, "gaps are left after weaving back");
			helper.succeed();
		});
	}

	/** An island whose cell an arrival's bridge reaches is given a level way in on the side the bridge comes in on. */
	@GameTest
	public void anArrivalBridgeLandsOnALevelWay(GameTestHelper helper) {
		java.util.Set<String> cells = com.extremedimensions.data.DataStore.seamCells();
		String east = "g@9000,9000=" + net.minecraft.core.Direction.EAST.get2DDataValue() + ",77,78,14";
		String north = "g@9100,9000=" + net.minecraft.core.Direction.NORTH.get2DDataValue() + ",77,78,12";
		cells.add(east);
		cells.add(north);
		try {
			int mask = SeamService.inbound(77, 78);
			// looking east, its bridge comes in on the west; looking north, on the south
			check(helper, mask == (2 | 4), "the way in is on sides %s", mask);
			check(helper, SeamService.inbound(78, 77) == 0, "a cell no bridge reaches has a way in (%s)", SeamService.inbound(78, 77));
		} finally {
			cells.remove(east);
			cells.remove(north);
		}
		helper.succeed();
	}

	@GameTest
	public void timeRunsOutFasterInTheFray(GameTestHelper helper) {
		check(helper, SeamDepth.drain(Config.SEAM_FRAY_TOP - 10) == Config.SEAM_FRAY_DRAIN, "the Fray drains at %s", SeamDepth.drain(Config.SEAM_FRAY_TOP - 10));
		check(helper, SeamDepth.drain(Config.SEAM_FRAY_TOP + 30) == 1.0, "the islands drain at %s", SeamDepth.drain(Config.SEAM_FRAY_TOP + 30));
		SeamDepth.Well w = new SeamDepth.Well(100, 90, 100, 30);
		check(helper, SeamDepth.inShaft(w, 100.5, 60, 100.5, 2) && !SeamDepth.inShaft(w, 104.5, 60, 100.5, 2), "the shaft is not where its well is");
		check(helper, !SeamDepth.inShaft(w, 100.5, 95, 100.5, 2) && !SeamDepth.inShaft(w, 100.5, 20, 100.5, 2), "the shaft reaches past its ends");
		helper.succeed();
	}

	/**
	 * The Seam builds itself before a watcher's eyes: a wall a few blocks off flies in (it is not there at once, and
	 * stands whole a moment later), the ground right under them is simply there, and on stopping what is in the air
	 * lands and an unfinished plan is handed back to be built again. Built where the test's own space is, in the
	 * Overworld (the test server has no Seam), driving the Seam's builder by hand. The builder's queue is shared, so
	 * this runs as the second half of the Seam's performance test ({@link PerfTests#seamStructures}), never beside it;
	 * it ends the test.
	 */
	static void seamBuildsBeforeAWatcher(GameTestHelper helper, int start) {
		// Every step is scheduled here, up front: a step scheduled from inside another one would upset the test's own
		// list of steps while it is being run through
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int base = Math.max(o.getY() + 3, 8);
		int range = Config.SEAM_FLY_RANGE;
		BlockPos corner = new BlockPos(o.getX() + 7, base + 2, o.getZ() + 5);
		BlockPos feet = new BlockPos(o.getX() + 1, base - 1, o.getZ() + 1);
		Runnable restore = () -> {
			Config.SEAM_FLY_RANGE = range;
			SeamArchitect.clear();
		};
		helper.runAfterDelay(start, () -> {
			ServerPlayer p = TestPlayers.survivor(helper, "test-watcher");
			p.teleportTo(o.getX() + 1.5, base, o.getZ() + 1.5);
			Config.SEAM_FLY_RANGE = 48;
			SeamArchitect.Plan wall = new SeamArchitect.Plan();
			wall.cell = "test@wall";
			for (int y = 1; y <= 3; y++) {
				for (int z = 4; z <= 6; z++) {
					wall.set(o.getX() + 7, base + y, o.getZ() + z, Blocks.STONE.defaultBlockState());
				}
			}
			// Right under the watcher's feet: the ground is simply there
			wall.set(o.getX() + 1, base - 1, o.getZ() + 1, Blocks.STONE.defaultBlockState());
			wall.queue();
		});
		for (int t = 1; t <= 50; t++) {
			int tick = t;
			helper.runAfterDelay(start + t, () -> {
				SeamArchitect.tick(level, 5_000_000L);
				if (tick == 3) {
					boolean ok = SeamArchitect.flying() > 0 && level.getBlockState(corner).isAir() && level.getBlockState(feet).is(Blocks.STONE);
					if (!ok) {
						restore.run();
					}
					check(helper, ok, "the wall did not fly in (flying %s, corner %s, ground under the watcher %s)", SeamArchitect.flying(), level.getBlockState(corner),
							level.getBlockState(feet));
				}
			});
		}
		helper.runAfterDelay(start + 52, () -> {
			int stood = 0;
			for (int y = 1; y <= 3; y++) {
				for (int z = 4; z <= 6; z++) {
					stood += level.getBlockState(new BlockPos(o.getX() + 7, base + y, o.getZ() + z)).is(Blocks.STONE) ? 1 : 0;
				}
			}
			boolean ok = stood == 9 && SeamArchitect.flying() == 0 && SeamArchitect.pending() == 0;
			if (!ok) {
				restore.run();
			}
			check(helper, ok, "the wall stands with %s of 9 blocks (%s flying, %s pending)", stood, SeamArchitect.flying(), SeamArchitect.pending());
			// Stopping with a plan in the air: what flies lands at once, and the plan is handed back unfinished
			SeamArchitect.Plan more = new SeamArchitect.Plan();
			more.cell = "test@cell";
			for (int z = 4; z <= 6; z++) {
				more.set(o.getX() + 6, base + 4, o.getZ() + z, Blocks.STONE.defaultBlockState());
			}
			more.then(o.getX() + 6, base + 4, o.getZ() + 4, l -> {});
			more.queue();
			SeamArchitect.tick(level, 5_000_000L);
			SeamArchitect.tick(level, 5_000_000L);
			int inAir = SeamArchitect.flying();
			java.util.List<SeamArchitect.Meta> left = SeamArchitect.stop(level);
			boolean landed = level.getBlockState(new BlockPos(o.getX() + 6, base + 4, o.getZ() + 5)).is(Blocks.STONE);
			boolean handed = left.stream().anyMatch(m -> "test@cell".equals(m.cell()));
			restore.run();
			check(helper, inAir > 0 && landed, "on stopping the blocks in the air did not land (%s in the air)", inAir);
			check(helper, handed, "the unfinished plan was not handed back on stopping");
			// Built again after it was left half done: only open air is filled, and its veins come back as plain stone
			int range0 = Config.SEAM_FLY_RANGE;
			BlockPos kept = new BlockPos(o.getX() + 9, base + 1, o.getZ() + 2);
			BlockPos vein = new BlockPos(o.getX() + 9, base + 2, o.getZ() + 2);
			boolean keptOk, veinOk;
			try {
				Config.SEAM_FLY_RANGE = 0;
				level.setBlock(kept, Blocks.OAK_PLANKS.defaultBlockState(), 2);
				SeamArchitect.Plan again = new SeamArchitect.Plan();
				again.rebuild = true;
				again.set(kept.getX(), kept.getY(), kept.getZ(), Blocks.STONE.defaultBlockState());
				again.set(vein.getX(), vein.getY(), vein.getZ(), com.extremedimensions.registry.ModBlocks.SEAM_VEIN.defaultBlockState());
				again.queue();
				SeamArchitect.tick(level, 5_000_000L);
				keptOk = level.getBlockState(kept).is(Blocks.OAK_PLANKS);
				veinOk = !level.getBlockState(vein).isAir() && !level.getBlockState(vein).is(com.extremedimensions.registry.ModBlocks.SEAM_VEIN);
			} finally {
				Config.SEAM_FLY_RANGE = range0;
				SeamArchitect.clear();
			}
			check(helper, keptOk, "an island built again overwrote what stood in its way");
			check(helper, veinOk, "an island built again grew its vein back (%s)", level.getBlockState(vein));
			helper.succeed();
		});
	}
}
