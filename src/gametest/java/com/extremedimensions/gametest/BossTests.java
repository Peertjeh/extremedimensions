package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderArena;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.ElderTerrain;
import com.extremedimensions.elder.ElderUltimate;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.mending.Binding;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Elders: when the Matriarch comes, what the Ferryman takes and waives, the Weaver's theft, the Starved One's growth,
 * what an ultimate lays on those it reaches, and how the Elders tear up the world (only when allowed, and the thorns
 * they raise sink back).
 */
public class BossTests {
	/** 3.32: every boss takes its own health from its setting as it is made, half again its old by default. */
	@GameTest
	public void eachBossTakesItsHealthFromItsSetting(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double was = Config.FERRYMAN_HEALTH;
		try {
			check(helper, Config.MATRIARCH_HEALTH == 960 && Config.FERRYMAN_HEALTH == 780 && Config.WEAVER_HEALTH == 720 && Config.KNOT_MOTHER_HEALTH == 840
					&& Config.STARVED_HEALTH == 750, "the bosses' defaults are not half again their old health");
			var m = ModEntities.MATRIARCH.create(level, EntitySpawnReason.EVENT);
			var w = ModEntities.WEAVER.create(level, EntitySpawnReason.EVENT);
			var k = ModEntities.KNOT_MOTHER.create(level, EntitySpawnReason.EVENT);
			var so = ModEntities.STARVED_ONE.create(level, EntitySpawnReason.EVENT);
			check(helper, m != null && m.getMaxHealth() == 960 && m.getHealth() == 960, "the Matriarch is made with %s health", m == null ? 0 : m.getMaxHealth());
			check(helper, w != null && w.getMaxHealth() == 720, "the Last Weaver is made with %s health", w == null ? 0 : w.getMaxHealth());
			check(helper, k != null && k.getMaxHealth() == 840, "the Knot Mother is made with %s health", k == null ? 0 : k.getMaxHealth());
			check(helper, so != null && so.getMaxHealth() == 750, "the Starved One is made with %s health", so == null ? 0 : so.getMaxHealth());
			Config.FERRYMAN_HEALTH = 900;
			var f = ModEntities.FERRYMAN.create(level, EntitySpawnReason.EVENT);
			check(helper, f != null && f.getMaxHealth() == 900, "a Ferryman made after his setting changed has %s health", f == null ? 0 : f.getMaxHealth());
		} finally {
			Config.FERRYMAN_HEALTH = was;
		}
		helper.succeed();
	}

	@GameTest
	public void theStarvedOneGrowsWithItsFoes(GameTestHelper helper) {
		double per = Config.STARVED_HEALTH_PER_PLAYER;
		try {
			Config.STARVED_HEALTH_PER_PLAYER = 0.5;
			check(helper, MendingService.healthFor(1) == StarvedOne.baseHealth(), "alone, it has %s health", MendingService.healthFor(1));
			check(helper, MendingService.healthFor(3) == StarvedOne.baseHealth() * 2, "against three, it has %s health", MendingService.healthFor(3));
			// 3.31: past the game's 1024 too, as every blow is divided rather than its health raised
			check(helper, MendingService.healthFor(5) == StarvedOne.baseHealth() * 3 && MendingService.toughness(5) == 3,
					"against five, it has %s health (each blow divided by %s)", MendingService.healthFor(5), MendingService.toughness(5));
			Config.STARVED_HEALTH_PER_PLAYER = 0;
			check(helper, MendingService.healthFor(4) == StarvedOne.baseHealth() && MendingService.toughness(4) == 1, "with no growth set, it still grew");
		} finally {
			Config.STARVED_HEALTH_PER_PLAYER = per;
		}
		helper.succeed();
	}

	/** 3.33: three pillars quenched stop its healing and all four stun it, for any party; the settings are held to the four pillars. */
	@GameTest
	public void thePillarsAskThreeToStopItsHealingAndFourToStunIt(GameTestHelper helper) {
		int stun = Config.STARVED_SOCKETS, stop = Config.STARVED_HEAL_QUENCH;
		try {
			check(helper, Binding.healStopAt() == 3 && Binding.stunAt() == 4, "the pillars ask %s to stop its healing and %s to stun it, not 3 and 4",
					Binding.healStopAt(), Binding.stunAt());
			Config.STARVED_SOCKETS = 9;
			Config.STARVED_HEAL_QUENCH = 0;
			check(helper, Binding.stunAt() == 4 && Binding.healStopAt() == 1, "the settings were not held to the four pillars (%s, %s)", Binding.stunAt(),
					Binding.healStopAt());
		} finally {
			Config.STARVED_SOCKETS = stun;
			Config.STARVED_HEAL_QUENCH = stop;
		}
		// The four pillars stand where the crown builds its obelisks: on the diagonals, sixty-four out
		for (int i = 0; i < Binding.PILLARS; i++) {
			int[] c = Binding.pillar(i);
			double r = Math.sqrt((double) (c[0] - MendingService.HX) * (c[0] - MendingService.HX) + (double) (c[1] - MendingService.HZ) * (c[1] - MendingService.HZ));
			check(helper, Math.abs(r - Binding.RING) < 1.0 && Math.abs(Math.abs(c[0]) - Math.abs(c[1])) <= 1, "pillar %s stands at %s, %s", i, c[0], c[1]);
			check(helper, r + 14 < MendingService.ARENA, "pillar %s's island reaches past the fight (%s)", i, r + 14);
		}
		helper.succeed();
	}

	/**
	 * 3.33: a pillar's node is found atop the lowest piece of its shaft: up the shaft's heart from the plinth (found by
	 * its top step at the corner facing the Heart) to where the shaft first breaks. A replica in the test's own space.
	 */
	@GameTest
	public void aPillarsNodeIsFoundAtopItsLowestPiece(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// In world space (the test's own space may be turned): the obelisk's centre in one corner of the test, its top
		// step five out on both axes towards the test's middle, and the shaft's heart over the centre
		BlockPos centre = helper.absolutePos(new BlockPos(1, 1, 1)), far = helper.absolutePos(new BlockPos(6, 1, 6));
		int sx = Integer.signum(far.getX() - centre.getX()), sz = Integer.signum(far.getZ() - centre.getZ());
		for (int y = 0; y <= 8; y++) {
			level.setBlock(centre.offset(5 * sx, y, 5 * sz), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(centre.offset(0, y, 0), Blocks.AIR.defaultBlockState(), 3);
		}
		level.setBlock(centre.offset(5 * sx, 1, 5 * sz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
		for (int y = 2; y <= 5; y++) {
			level.setBlock(centre.offset(0, y, 0), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
		}
		BlockPos node = Binding.nodeOf(level, centre.getX(), centre.getZ(), sx, sz, centre.getY() + 5, centre.getY() - 1);
		BlockPos want = centre.offset(0, 6, 0);
		check(helper, want.equals(node), "the node was found at %s, not atop the shaft's lowest piece at %s", node, want);
		// A node standing there already is found where it stands (not above it)
		level.setBlock(want, Binding.FEEDING.defaultBlockState(), 3);
		check(helper, want.equals(Binding.nodeOf(level, centre.getX(), centre.getZ(), sx, sz, centre.getY() + 5, centre.getY() - 1)),
				"a node already standing was not found where it stands");
		// No shaft over the plinth: no node
		for (int y = 2; y <= 6; y++) {
			level.setBlock(centre.offset(0, y, 0), Blocks.AIR.defaultBlockState(), 3);
		}
		check(helper, Binding.nodeOf(level, centre.getX(), centre.getZ(), sx, sz, centre.getY() + 5, centre.getY() - 1) == null,
				"a node was found with no shaft to hold it");
		helper.succeed();
	}

	/**
	 * A strand out to a pillar is walkable all the way: three wide, rising or falling at most half a block from one step
	 * to the next (slabs between), with a wall along each side, from the Heart's floor to the island's.
	 */
	@GameTest
	public void aStrandToAPillarIsWalkable(GameTestHelper helper) {
		double ax = 0.5, az = 0.5, bx = 25.5, bz = 25.5;
		SeamArchitect.Plan plan = Binding.strand(null, ax, az, 100, bx, bz, 93);
		check(helper, plan.size() > 100, "the strand lays only %s blocks", plan.size());
		double len = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az)), ux = (bx - ax) / len, uz = (bz - az) / len;
		double last = Double.NaN, first = Double.NaN;
		for (double t = 0; t <= len; t += 0.5) {
			double h = surface(plan, (int) Math.floor(ax + ux * t), (int) Math.floor(az + uz * t));
			check(helper, !Double.isNaN(h), "no floor under the strand %s along", t);
			if (!Double.isNaN(last)) {
				check(helper, Math.abs(h - last) <= 0.5 + 1e-6, "the strand steps %s at %s along", h - last, t);
			} else {
				first = h;
			}
			last = h;
		}
		check(helper, Math.abs(first - 101) <= 0.5 && Math.abs(last - 94) <= 0.5, "the strand runs from %s to %s", first, last);
		// three wide, walled both sides, halfway along
		double mx = ax + ux * len / 2, mz = az + uz * len / 2;
		for (int side = -1; side <= 1; side += 2) {
			double fx = mx - uz * side * 1.0, fz = mz + ux * side * 1.0;
			check(helper, !Double.isNaN(surface(plan, (int) Math.floor(fx), (int) Math.floor(fz))), "the strand is not three wide (side %s)", side);
			boolean wall = false;
			for (double off = 1.6; off <= 2.6; off += 0.2) {
				int wx = (int) Math.floor(mx - uz * side * off), wz = (int) Math.floor(mz + ux * side * off);
				for (int y = 90; y <= 104; y++) {
					BlockState s = plan.at(wx, y, wz);
					wall |= s != null && s.is(ModBlocks.HUSH_BRICK_WALL);
				}
			}
			check(helper, wall, "no wall along side %s of the strand", side);
		}
		helper.succeed();
	}

	/** The height a player stands at on a strand's column (a slab counts half), or NaN with nothing there. */
	private static double surface(SeamArchitect.Plan plan, int x, int z) {
		for (int y = 106; y >= 86; y--) {
			BlockState s = plan.at(x, y, z);
			if (s == null || s.is(ModBlocks.HUSH_BRICK_WALL)) {
				continue;
			}
			return y + (s.is(ModBlocks.HUSH_TILE_SLAB) ? 0.5 : 1.0);
		}
		return Double.NaN;
	}

	/**
	 * The Starved One hangs in the air (it never falls), tells the client what it is doing and whether it is bound, and
	 * outside the Heart's fight takes harm in full.
	 */
	@GameTest(maxTicks = 60)
	public void theStarvedOneHangsInTheAir(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(4, 6, 4));
		StarvedOne so = ModEntities.STARVED_ONE.create(level, EntitySpawnReason.EVENT);
		check(helper, so != null, "no Starved One could be made");
		so.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.addFreshEntity(so);
		double y0 = so.getY();
		check(helper, so.isNoGravity() && so.getAction() == StarvedOne.IDLE && !so.isBound(), "it began falling, acting or bound");
		int take = so.getTake();
		so.act(StarvedOne.SWOOP);
		check(helper, so.getAction() == StarvedOne.SWOOP && so.getTake() == take + 1, "its swoop was not told (%s, %s)", so.getAction(), so.getTake());
		so.setBound(true);
		check(helper, so.isBound(), "its binding was not told");
		check(helper, MendingService.damageFactor(so) == 1.0F, "away from the Heart's fight it takes %s of the harm", MendingService.damageFactor(so));
		helper.runAfterDelay(25, () -> {
			check(helper, so.isAlive() && Math.abs(so.getY() - y0) < 0.05, "it fell from %s to %s", y0, so.getY());
			so.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void theMatriarchComesAfterTheKeepers(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int after = Config.MATRIARCH_AFTER_KEEPERS;
		try {
			Config.MATRIARCH_AFTER_KEEPERS = 3;
			d.keepersSlain = 2;
			check(helper, !HollowService.matriarchFor(d), "the Matriarch came after two Keepers");
			d.keepersSlain = 3;
			check(helper, HollowService.matriarchFor(d), "the Matriarch did not come after three Keepers");
			d.matriarchSlain = true;
			check(helper, !HollowService.matriarchFor(d), "the Matriarch came again after she fell");
			d.matriarchSlain = false;
			Config.MATRIARCH_AFTER_KEEPERS = 0;
			check(helper, !HollowService.matriarchFor(d), "the Matriarch came although she is switched off");
		} finally {
			Config.MATRIARCH_AFTER_KEEPERS = after;
			d.keepersSlain = 0;
			d.matriarchSlain = false;
		}
		helper.succeed();
	}

	/**
	 * 3.28: woken, the Matriarch first stirs beneath her nest (a bar counts down, nothing has risen yet, and one nest
	 * stirs only once); with everyone who earned her gone far enough she sinks back to sleep, and does not come.
	 */
	@GameTest(environment = Alone.MATRIARCH_SETTLES, maxTicks = 140)
	public void theMatriarchStirsAndSleepsAgainWhenTheyGo(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Hoard h = new Hoard(Dims.of(helper.getLevel()), at.getX() + 1, at.getY(), at.getZ() + 1, "");
		Runnable restore = stirred(p, 3);
		try {
			check(helper, HollowService.stir(h, p), "the Matriarch did not stir beneath her nest");
			check(helper, HollowService.stirring(h.key()), "she is not stirring");
			check(helper, !HollowService.stir(h, p), "she stirred twice beneath one nest");
			check(helper, !com.extremedimensions.elder.MatriarchService.hunting(p), "she rose at once, with no time to make ready");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		// Both later steps are scheduled up front (a step scheduled from inside another upsets the test's list of steps)
		helper.runAfterDelay(20, () -> {
			try {
				check(helper, HollowService.stirring(h.key()), "she sank back to sleep while they stood by her nest");
				p.teleportTo(at.getX() + 40.5, at.getY(), at.getZ() + 0.5);
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(24, () -> {
			try {
				check(helper, !HollowService.stirring(h.key()), "she still stirs after everyone went far from her nest");
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(90, () -> {
			try {
				check(helper, !com.extremedimensions.elder.MatriarchService.hunting(p), "she rose although everyone had gone");
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	/** 3.28: whoever earned her and stays near her nest while she stirs is the one she rises for once the time is up. */
	@GameTest(environment = Alone.MATRIARCH_STIRS, maxTicks = 120)
	public void theMatriarchRisesForWhoeverStays(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		forceAround(helper, 3);
		Hoard h = new Hoard(Dims.of(helper.getLevel()), at.getX() + 1, at.getY(), at.getZ() + 1, "");
		int arena = Config.ELDER_ARENA;
		ServerLevel level = helper.getLevel();
		net.minecraft.world.Difficulty was = level.getDifficulty();
		// (on peaceful nothing would stay risen to fight)
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		Config.ELDER_ARENA = 0;
		Runnable undo = stirred(p, 2);
		Runnable restore = () -> {
			undo.run();
			Config.ELDER_ARENA = arena;
			level.getServer().setDifficulty(was, true);
		};
		try {
			check(helper, HollowService.stir(h, p), "the Matriarch did not stir beneath her nest");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		helper.runAfterDelay(20, () -> {
			try {
				check(helper, HollowService.stirring(h.key()) && !com.extremedimensions.elder.MatriarchService.hunting(p),
						"she rose before her time was up");
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(55, () -> {
			java.util.List<com.extremedimensions.elder.Matriarch> found = helper.getLevel().getEntitiesOfClass(com.extremedimensions.elder.Matriarch.class,
					p.getBoundingBox().inflate(48));
			boolean hunting = com.extremedimensions.elder.MatriarchService.hunting(p), stirring = HollowService.stirring(h.key());
			found.forEach(com.extremedimensions.elder.MatriarchService::fade);
			restore.run();
			check(helper, !stirring, "she still stirs after her time was up");
			check(helper, hunting && !found.isEmpty(), "she did not rise for the one who stayed (hunting %s, found %s)", hunting, found.size());
			helper.succeed();
		});
	}

	/** Gives the player the Matriarch (three Keepers slain) and a short wind-up; returns what puts it all back. */
	private static Runnable stirred(ServerPlayer p, int seconds) {
		PlayerData d = DataStore.get(p.getUUID());
		int after = Config.MATRIARCH_AFTER_KEEPERS, wake = Config.MATRIARCH_WAKE_SECONDS, calm = Config.MATRIARCH_CALM_RANGE;
		Config.MATRIARCH_AFTER_KEEPERS = 3;
		Config.MATRIARCH_WAKE_SECONDS = seconds;
		Config.MATRIARCH_CALM_RANGE = 16;
		d.keepersSlain = 3;
		d.matriarchSlain = false;
		return () -> {
			Config.MATRIARCH_AFTER_KEEPERS = after;
			Config.MATRIARCH_WAKE_SECONDS = wake;
			Config.MATRIARCH_CALM_RANGE = calm;
			d.keepersSlain = 0;
		};
	}

	/** {@code /tether boss matriarch} brings her, even to an operator in creative mode (not only her nest). */
	@GameTest(environment = Alone.MATRIARCH_CALLED, maxTicks = 60)
	public void anOperatorCallsTheMatriarchInCreative(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.creative(helper, "test-operator");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Runnable unforce = forceAround(helper, 3);
		// Both steps are scheduled up front (a step scheduled from inside another upsets the test's list of steps)
		boolean[] ok = {false};
		int[] hoards = {0};
		helper.runAfterDelay(5, () -> {
			int arena = Config.ELDER_ARENA;
			hoards[0] = DataStore.hoards().size();
			Config.ELDER_ARENA = 0;
			try {
				ok[0] = HollowService.summonMatriarch(p);
			} finally {
				Config.ELDER_ARENA = arena;
			}
		});
		helper.runAfterDelay(7, () -> {
			java.util.List<com.extremedimensions.elder.Matriarch> found = helper.getLevel().getEntitiesOfClass(com.extremedimensions.elder.Matriarch.class,
					p.getBoundingBox().inflate(48));
			found.forEach(com.extremedimensions.elder.MatriarchService::fade);
			while (DataStore.hoards().size() > hoards[0]) {
				DataStore.hoards().remove(DataStore.hoards().size() - 1);
			}
			unforce.run();
			check(helper, ok[0], "calling the Matriarch in creative failed");
			check(helper, !found.isEmpty(), "only her nest came, not the Matriarch");
			helper.succeed();
		});
	}

	/**
	 * Forces the chunks round the test's space (an Elder appears a little way off, and must be about to be found and
	 * driven wherever the test happens to stand). They are not let go again: the game keeps the chunks of every test's
	 * structure forced the same way, and letting go of one a neighbouring test stands in stops that test. The test
	 * server forgets them when it stops. Returns what the tests call when done (nothing to do).
	 */
	private static Runnable forceAround(GameTestHelper helper, int r) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int cx = o.getX() >> 4, cz = o.getZ() >> 4;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				level.setChunkForced(cx + dx, cz + dz, true);
			}
		}
		return () -> {};
	}

	@GameTest(environment = Alone.FERRYMAN_TOLL)
	public void theFerrymanTakesHisTollAndWaivesTheFee(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.FERRYMANS_FEE, 2);
		PlayerData d = DataStore.get(p.getUUID());
		// Test players join at the world's origin; he must find ground where the test's chunks are loaded
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.giveExperienceLevels(10);
		check(helper, FerrymanService.drainFactor(p) == 1.0, "the Tether drained faster before he came");
		// No arena here: it would reach into the other tests round this one
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		Ferryman f = FerrymanService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		Config.ELDER_ARENA = arena;
		check(helper, f != null, "the Ferryman could not come");
		check(helper, !FerrymanService.summon(p), "the Ferryman came twice for one passenger");
		check(helper, FerrymanService.drainFactor(p) == Math.max(0.1, Config.FERRYMAN_DRAIN), "while he walks the drain is %s", FerrymanService.drainFactor(p));
		int levels = p.experienceLevel;
		FerrymanService.toll(p);
		check(helper, p.experienceLevel == levels - Math.min(levels, Config.FERRYMAN_TOLL_LEVELS), "his blow took %s levels", levels - p.experienceLevel);
		FerrymanService.onDefeat(f, p);
		f.discard();
		check(helper, d.owned(Penalty.FERRYMANS_FEE) == 1, "slaying him left %s stacks of the Fee", d.owned(Penalty.FERRYMANS_FEE));
		check(helper, FerrymanService.drainFactor(p) == 1.0, "the Tether still drains faster after he fell");
		helper.succeed();
	}

	/** The Elders' attack effects reach the client as they were sent (every field of every effect, in order). */
	@GameTest
	public void attackEffectsSurviveTheWire(GameTestHelper helper) {
		java.util.List<com.extremedimensions.network.ModPackets.AttackFxPayload.Fx> fx = java.util.List.of(
				new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(1, 0, 2, 1.5F, 64F, -3.25F, 0, 0, 0, 2.8F, 28),
				new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 2, 1, -10F, 70F, 4F, 6F, 64F, 9.5F, 0, 12),
				new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(5, 1, 0, 3F, 66.5F, 3F, 0.04F, -0.01F, 0.02F, 0, 123456));
		com.extremedimensions.network.ModPackets.AttackFxPayload sent = new com.extremedimensions.network.ModPackets.AttackFxPayload(1.5F, fx);
		net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		com.extremedimensions.network.ModPackets.AttackFxPayload.CODEC.encode(buf, sent);
		com.extremedimensions.network.ModPackets.AttackFxPayload back = com.extremedimensions.network.ModPackets.AttackFxPayload.CODEC.decode(buf);
		check(helper, back.equals(sent), "the attack effects came back changed: %s", back);
		check(helper, buf.readableBytes() == 0, "%s bytes were left over", buf.readableBytes());
		helper.succeed();
	}

	@GameTest(environment = Alone.ELDER_FORMING)
	public void anElderCannotBeHurtWhileItForms(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		Ferryman f = FerrymanService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		Config.ELDER_ARENA = arena;
		check(helper, f != null, "the Ferryman could not come");
		check(helper, f.getAction() == ElderBoss.SPAWN, "the Ferryman did not form first (action %s)", f.getAction());
		float health = f.getHealth();
		f.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(p), 40f);
		check(helper, f.getHealth() == health, "he was hurt while he formed");
		FerrymanService.force(f, Ferryman.KNELL);
		check(helper, f.getAction() == Ferryman.KNELL, "forcing a blow did not end his forming (action %s)", f.getAction());
		// 3.37: formed, a mace's blow glances off him, a bare hand's of the same weight lands whole
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
		f.invulnerableTime = 0;
		float before = f.getHealth();
		f.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(p), 40f);
		float byMace = before - f.getHealth();
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		f.invulnerableTime = 0;
		before = f.getHealth();
		f.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(p), 40f);
		float byHand = before - f.getHealth();
		check(helper, byHand > 0 && byMace > 0, "a blow did not land at all (mace %s, hand %s)", byMace, byHand);
		check(helper, byMace <= byHand * (Config.BOSS_MACE_DAMAGE + 0.02), "a mace's blow took %s of his health, a hand's of the same weight %s", byMace,
				byHand);
		FerrymanService.fade(f);
		helper.succeed();
	}

	/** 3.37: the same guard holds for the Starved One, and a blow struck without a mace (or with a sword) is let through whole. */
	@GameTest
	public void aMaceGlancesOffTheBosses(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		ServerLevel level = helper.getLevel();
		StarvedOne so = ModEntities.STARVED_ONE.create(level, EntitySpawnReason.EVENT);
		check(helper, so != null, "no Starved One could be made");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		so.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
		float mace = com.extremedimensions.elder.ElderPower.maceGuard(so, level, level.damageSources().playerAttack(p), 100f);
		check(helper, Math.abs(mace - 100f * (float) Config.BOSS_MACE_DAMAGE) < 0.01f, "a mace's 100 came to %s on the Starved One", mace);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
		float sword = com.extremedimensions.elder.ElderPower.maceGuard(so, level, level.damageSources().playerAttack(p), 100f);
		check(helper, sword == 100f, "a sword's 100 came to %s", sword);
		check(helper, com.extremedimensions.elder.ElderPower.maceGuard(so, level, level.damageSources().generic(), 100f) == 100f,
				"a blow from nowhere was held back");
		so.discard();
		helper.succeed();
	}

	/**
	 * A whole arena, far from the other tests so it touches nothing of theirs: it builds (blocks flying in as block
	 * displays), its Elder leaves, it comes apart, and the ground is as it was (plants it was built over aside).
	 */
	@GameTest(environment = Alone.ARENA_RAISED, maxTicks = 400)
	public void anElderRaisesItsArenaAndPutsTheGroundBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int cx = (origin.getX() >> 4 << 4) + 6008, cz = (origin.getZ() >> 4 << 4) + 8;
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				level.getChunk((cx >> 4) + x, (cz >> 4) + z);
				level.setChunkForced((cx >> 4) + x, (cz >> 4) + z, true);
			}
		}
		// What an earlier test's Elder raised here sinks back now, not during this test: the performance tests fight at
		// this same spot, and the root columns of a Matriarch's coming, still standing, sank back mid-test and read as
		// ground the arena had not put back
		com.extremedimensions.elder.ElderTerrain.clear();
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		BlockPos centre = new BlockPos(cx, top, cz);
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
		for (BlockPos q : BlockPos.betweenClosed(centre.offset(-28, -10, -28), centre.offset(28, 32, 28))) {
			before.put(q.immutable(), level.getBlockState(q));
		}
		double build = Config.ELDER_ARENA_BUILD_SECONDS, dissolve = Config.ELDER_ARENA_DISSOLVE_SECONDS, size = Config.ELDER_ARENA_SIZE;
		int flight = Config.ELDER_ARENA_FLIGHT_TICKS, linger = Config.ELDER_ARENA_LINGER_SECONDS, on = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 1;
		// At its own size, so all of it stands within the forced chunks and the ground looked at
		Config.ELDER_ARENA_SIZE = 1.0;
		Config.ELDER_ARENA_BUILD_SECONDS = 1;
		Config.ELDER_ARENA_FLIGHT_TICKS = 10;
		Config.ELDER_ARENA_LINGER_SECONDS = 1;
		Config.ELDER_ARENA_DISSOLVE_SECONDS = 1;
		com.extremedimensions.elder.Weaver w = com.extremedimensions.registry.ModEntities.WEAVER.spawn(level, centre.above(2),
				net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		check(helper, w != null, "no Weaver to raise the arena");
		int arenas = ElderArena.count();
		ElderArena.raise(w, centre);
		check(helper, ElderArena.count() == arenas + 1, "the Weaver raised no arena");
		Runnable restore = () -> {
			Config.ELDER_ARENA = on;
			Config.ELDER_ARENA_BUILD_SECONDS = build;
			Config.ELDER_ARENA_FLIGHT_TICKS = flight;
			Config.ELDER_ARENA_LINGER_SECONDS = linger;
			Config.ELDER_ARENA_DISSOLVE_SECONDS = dissolve;
			Config.ELDER_ARENA_SIZE = size;
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					level.setChunkForced((cx >> 4) + x, (cz >> 4) + z, false);
				}
			}
		};
		// Blocks fly in within the first second or so; looked for every other tick, as a busy server can be slow to let
		// the far, freshly forced chunks hold entities
		boolean[] seen = {false};
		for (int t = 2; t <= 30; t += 2) {
			helper.runAfterDelay(t, () -> {
				if (!seen[0]) {
					seen[0] = ElderArena.pending() > 0
							|| !level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, new AABB(centre).inflate(48)).isEmpty();
				}
			});
		}
		helper.runAfterDelay(31, () -> {
			if (!seen[0]) {
				restore.run();
			}
			check(helper, seen[0], "no blocks were flying in within 30 ticks");
		});
		helper.runAfterDelay(36, () -> {
			int changed = 0;
			for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> e : before.entrySet()) {
				changed += level.getBlockState(e.getKey()) != e.getValue() ? 1 : 0;
			}
			if (changed < 40 || ElderArena.standing() < 40) {
				restore.run();
			}
			check(helper, changed >= 40 && ElderArena.standing() >= 40, "the arena put down only %s blocks (%s changed)", ElderArena.standing(), changed);
			// What it built drops nothing however it comes down (a blast, a lost support): no loot from the arena
			BlockPos held = ElderArena.anyStanding(level);
			if (held != null) {
				int items = level.getEntitiesOfClass(ItemEntity.class, new AABB(held).inflate(3)).size();
				net.minecraft.world.level.block.Block.popResource(level, held, new ItemStack(Items.STONE));
				int after = level.getEntitiesOfClass(ItemEntity.class, new AABB(held).inflate(3)).size();
				if (after != items) {
					restore.run();
				}
				check(helper, after == items, "a block of the arena dropped something");
			}
			w.discard();
			ElderArena.release(w);
		});
		helper.runAfterDelay(150, () -> {
			restore.run();
			check(helper, ElderArena.pending() == 0, "%s blocks of the arena are still on their way", ElderArena.pending());
			check(helper, ElderArena.standing() == 0, "%s blocks of the arena still stand", ElderArena.standing());
			int wrong = 0;
			// (the first few told in full, from the arena's middle: what stood there, and what stands there now)
			java.util.List<String> told = new java.util.ArrayList<>();
			for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> e : before.entrySet()) {
				net.minecraft.world.level.block.state.BlockState now = level.getBlockState(e.getKey());
				// Plants built over are gone, and grass covered for a while may have died back to dirt
				boolean plant = !e.getValue().isAir() && e.getValue().canBeReplaced() && !e.getValue().liquid() && now.isAir();
				boolean grass = (e.getValue().is(Blocks.GRASS_BLOCK) || e.getValue().is(Blocks.MYCELIUM) || e.getValue().is(Blocks.PODZOL))
						&& (now.is(Blocks.DIRT) || now.is(e.getValue().getBlock()));
				if (now != e.getValue() && !plant && !grass) {
					wrong++;
					if (told.size() < 4) {
						BlockPos d = e.getKey().subtract(centre);
						told.add(String.format(java.util.Locale.ROOT, "(%d, %d, %d): %s, now %s", d.getX(), d.getY(), d.getZ(),
								e.getValue().getBlock().getDescriptionId(), now.getBlock().getDescriptionId()));
					}
				}
			}
			check(helper, wrong == 0, "%s blocks were not put back, from the middle: %s", wrong, String.join("; ", told));
			int left = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, new AABB(centre).inflate(48)).size();
			check(helper, left == 0, "%s block displays of the arena were left behind", left);
			helper.succeed();
		});
	}

	/** The Weaver's ultimate sweeps its Lance round in two full rings, each its own beam. */
	@GameTest(environment = Alone.WEAVER_RINGS, maxTicks = 200)
	public void theWeaversUltimateSweepsTwoRingsOfLight(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		// It must not die to the needles while the rings are counted
		p.getAbilities().invulnerable = true;
		Runnable unforce = forceAround(helper, 3);
		// Every step is scheduled up front (a step scheduled from inside another upsets the test's list of steps)
		com.extremedimensions.elder.Weaver[] weaver = {null};
		int[] most = {0};
		int rings = Config.WEAVER_ULTIMATE_RINGS, seconds = Config.ELDER_ULTIMATE_SECONDS;
		Runnable restore = () -> {
			Config.WEAVER_ULTIMATE_RINGS = rings;
			Config.ELDER_ULTIMATE_SECONDS = seconds;
			p.getAbilities().invulnerable = false;
			if (weaver[0] != null && weaver[0].isAlive()) {
				WeaverService.fade(weaver[0]);
			}
			unforce.run();
		};
		helper.runAfterDelay(5, () -> {
			int arena = Config.ELDER_ARENA;
			Config.ELDER_ARENA = 0;
			Config.WEAVER_ULTIMATE_RINGS = 2;
			Config.ELDER_ULTIMATE_SECONDS = 6;
			// Its own Weaver, the one this summons: a test beside it may raise one in the same tick
			java.util.List<com.extremedimensions.elder.Weaver> had = helper.getLevel().getEntitiesOfClass(com.extremedimensions.elder.Weaver.class,
					p.getBoundingBox().inflate(64));
			boolean came = WeaverService.summon(p, at.offset(12, 0, 0));
			Config.ELDER_ARENA = arena;
			weaver[0] = helper.getLevel().getEntitiesOfClass(com.extremedimensions.elder.Weaver.class, p.getBoundingBox().inflate(64))
					.stream().filter(w -> !had.contains(w)).findFirst().orElse(null);
			if (!came || weaver[0] == null) {
				restore.run();
			}
			check(helper, came && weaver[0] != null, "the Weaver did not come");
			WeaverService.force(weaver[0], ElderBoss.ULTIMATE);
		});
		// Six seconds long, its rings fire a quarter of the way in (30 ticks) and sweep for half of it (60 ticks): the
		// beams are counted all through it, so a slow tick on a busy machine cannot make the count miss them
		for (int t = 15; t <= 110; t += 2) {
			helper.runAfterDelay(t, () -> {
				if (weaver[0] != null) {
					most[0] = Math.max(most[0], com.extremedimensions.elder.ElderStrikes.beamsOf(weaver[0]));
				}
			});
		}
		helper.runAfterDelay(115, () -> {
			restore.run();
			check(helper, most[0] == 2, "its ultimate swept at most %s beams at once, not two rings", most[0]);
			helper.succeed();
		});
	}

	/**
	 * The Knot Mother, pulled loose from the First Knot's heart, fights on the Last Weaver's lines: her own name on the bar,
	 * no arena raised (the Knot is hers), one awake at a time, her ultimate's rings of light; slain, she rests, and those
	 * near her are told the First Knot's page and earn her advancement.
	 */
	@GameTest(environment = Alone.KNOT_MOTHER, maxTicks = 1100)
	public void theKnotMotherFightsOnTheWeaversLines(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.getAbilities().invulnerable = true;
		Runnable unforce = forceAround(helper, 3);
		com.extremedimensions.elder.KnotMother[] mother = {null};
		int[] most = {0};
		int rings = Config.WEAVER_ULTIMATE_RINGS, seconds = Config.ELDER_ULTIMATE_SECONDS;
		Runnable restore = () -> {
			Config.WEAVER_ULTIMATE_RINGS = rings;
			Config.ELDER_ULTIMATE_SECONDS = seconds;
			p.getAbilities().invulnerable = false;
			if (mother[0] != null && mother[0].isAlive()) {
				WeaverService.fade(mother[0]);
			}
			com.extremedimensions.data.DataStore.seamCells().removeIf(k -> k.startsWith("km@"));
			unforce.run();
			KnotMotherSlot.release("weavers-lines");
		};
		KnotMotherSlot.Steps steps = new KnotMotherSlot.Steps();
		steps.at(5, () -> {
			Config.WEAVER_ULTIMATE_RINGS = 2;
			Config.ELDER_ULTIMATE_SECONDS = 6;
			int arenas = com.extremedimensions.elder.ElderArena.count();
			boolean came = WeaverService.summonKnotMother(p, at.offset(12, 0, 0));
			mother[0] = helper.getLevel().getEntitiesOfClass(com.extremedimensions.elder.KnotMother.class, p.getBoundingBox().inflate(64))
					.stream().findFirst().orElse(null);
			if (!came || mother[0] == null) {
				restore.run();
			}
			check(helper, came && mother[0] != null, "the Knot Mother did not come");
			check(helper, WeaverService.knotMotherAwake(), "the Knot Mother is not counted awake");
			check(helper, !WeaverService.summonKnotMother(p, at.offset(-12, 0, 0)), "a second Knot Mother came while one was awake");
			check(helper, com.extremedimensions.elder.ElderArena.count() == arenas, "an arena was raised for the Knot Mother");
			WeaverService.force(mother[0], ElderBoss.ULTIMATE);
		});
		for (int t = 15; t <= 110; t += 2) {
			steps.at(t, () -> {
				if (mother[0] != null) {
					most[0] = Math.max(most[0], com.extremedimensions.elder.ElderStrikes.beamsOf(mother[0]));
				}
			});
		}
		steps.at(115, () -> {
			check(helper, most[0] == 2, "her ultimate swept at most %s beams at once, not two rings", most[0]);
			// Slain: she rests, and those near her earn her advancement and the Knot's page
			WeaverService.onDefeat(mother[0], p);
			boolean earned = com.extremedimensions.server.WorldFx.hasAdvancement(p, com.extremedimensions.progression.Advancements.KNOT_MOTHER);
			boolean page = com.extremedimensions.data.DataStore.get(p.getUUID()).journal.contains(com.extremedimensions.journal.JournalEntry.FIRST_KNOT.id);
			boolean resting = com.extremedimensions.data.DataStore.seamCells().stream().anyMatch(k -> k.startsWith("km@"));
			mother[0].discard();
			restore.run();
			check(helper, earned && page, "slaying the Knot Mother gave no advancement (%s) or page (%s)", earned, page);
			check(helper, resting, "the Knot Mother does not rest once slain");
			check(helper, !WeaverService.knotMotherAwake(), "the Knot Mother is still counted awake once slain");
			helper.succeed();
		});
		steps.run(helper, "weavers-lines", 900, 120);
	}

	@GameTest
	public void theWeaverUnpicksSeamTime(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int theft = Config.WEAVER_TIME_THEFT;
		try {
			Config.WEAVER_TIME_THEFT = 10;
			d.seamLeft = 100;
			WeaverService.unweave(p);
			check(helper, d.seamLeft == 100, "the Weaver unpicked time outside the Seam");
			d.inSeam = true;
			WeaverService.unweave(p);
			check(helper, d.seamLeft == 90, "a blow left %s seconds, expected 90", d.seamLeft);
			d.seamLeft = 5;
			WeaverService.unweave(p);
			check(helper, d.seamLeft == 1, "the Weaver cut the thread through (%s left)", d.seamLeft);
		} finally {
			Config.WEAVER_TIME_THEFT = theft;
			d.inSeam = false;
		}
		helper.succeed();
	}

	private static int extremeStacks(PlayerData d) {
		int n = 0;
		for (Penalty pen : Penalty.values()) {
			if (pen.extreme) {
				n += d.owned(pen);
			}
		}
		return n;
	}

	@GameTest
	public void anUltimateLaysExtremeCurses(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int curses = Config.ELDER_ULTIMATE_CURSES;
		try {
			Config.ELDER_ULTIMATE_CURSES = 2;
			int before = extremeStacks(d);
			ElderUltimate.strike(p, "boss.extremedimensions.matriarch");
			check(helper, extremeStacks(d) == before + 2, "the ultimate laid %s extreme curses, expected 2", extremeStacks(d) - before);
		} finally {
			Config.ELDER_ULTIMATE_CURSES = curses;
		}
		helper.succeed();
	}

	@GameTest
	public void anUltimateFlingsAwayWhatAFullyCursedPlayerCarries(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		for (Penalty pen : Penalty.values()) {
			if (pen.extreme && pen.max > 0) {
				d.addStacks(pen, pen.max);
			}
		}
		int full = extremeStacks(d);
		p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
		p.getInventory().setItem(7, new ItemStack(Items.IRON_INGOT, 9));
		ElderUltimate.strike(p, "boss.extremedimensions.ferryman");
		check(helper, extremeStacks(d) == full, "a fully cursed player was cursed further");
		check(helper, p.getInventory().getItem(0).isEmpty() && p.getInventory().getItem(7).isEmpty(), "the player kept what they carried");
		int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6)).size();
		check(helper, dropped >= 2, "%s stacks were flung around the player, expected 2", dropped);
		helper.succeed();
	}

	@GameTest
	public void theEldersTearUpTheGroundOnlyWhenAllowed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
		int grief = Config.ELDER_GRIEFING;
		try {
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
			Config.ELDER_GRIEFING = 0;
			ElderTerrain.furrow(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.5, 0, 0, 0);
			check(helper, level.getBlockState(pos).is(Blocks.DIRT), "with elder_griefing off the ground was torn up");
			Config.ELDER_GRIEFING = 1;
			ElderTerrain.furrow(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.5, 0, 0, 0);
			check(helper, level.getBlockState(pos).isAir(), "the ground was not torn up");
		} finally {
			Config.ELDER_GRIEFING = grief;
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void theThornsTheEldersRaiseSinkBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
		for (int i = 1; i <= 4; i++) {
			level.setBlock(ground.above(i), Blocks.AIR.defaultBlockState(), 3);
		}
		// A block no Elder ever raises or strews, so another test's Elder fighting nearby cannot muddle what is seen
		net.minecraft.world.level.block.Block thorn = Blocks.OAK_PLANKS;
		ElderTerrain.column(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 3, thorn, 5);
		check(helper, level.getBlockState(ground.above()).is(thorn), "no thorns rose out of the ground");
		check(helper, level.getBlockState(ground.above(3)).is(thorn), "the thorns did not rise three blocks");
		check(helper, ElderTerrain.temporary(level, ground.above(2)), "raised thorns are not known as temporary");
		// They sink back after 7 to 11 ticks. Raised blocks wait while their ground is unloaded, and a test's own ground
		// counts as loaded only while the test runs (never when the server ticks them, as a player's ground does), so
		// their time runs out here: a block forgotten while it waited would stay
		String atStart = ElderTerrain.describe(level, ground.above(3));
		helper.runAfterDelay(30, () -> {
			for (int i = 1; i <= 3; i++) {
				ElderTerrain.tickAt(level, ground.above(i), 30);
			}
			check(helper, !level.getBlockState(ground.above()).is(thorn) && !level.getBlockState(ground.above(3)).is(thorn)
					&& !ElderTerrain.temporary(level, ground.above(2)), "the thorns did not sink back (%s, %s); at the start: %s; now: %s",
					level.getBlockState(ground.above()), level.getBlockState(ground.above(3)), atStart, ElderTerrain.describe(level, ground.above(3)));
			helper.succeed();
		});
	}

	@GameTest
	public void aKilledHollowlingComesBackOnlyAfterAWhile(GameTestHelper helper) {
		int per = Config.HOLLOWLINGS_PER_HOARD, secs = Config.HOLLOWLING_RESPAWN_SECONDS;
		try {
			Config.HOLLOWLINGS_PER_HOARD = 3;
			Config.HOLLOWLING_RESPAWN_SECONDS = 120;
			Hoard h = new Hoard(Dims.OVERWORLD, 0, 64, 0, "");
			long now = 1_000_000L;
			check(helper, HollowService.youngAllowed(h, now) == 3, "a whole hoard has %s young", HollowService.youngAllowed(h, now));
			HollowService.youngLost(h, now);
			HollowService.youngLost(h, now);
			check(helper, HollowService.youngAllowed(h, now + 1000) == 1, "two killed, yet %s may play", HollowService.youngAllowed(h, now + 1000));
			check(helper, HollowService.youngAllowed(h, now + 119_000) == 1, "one came back before two minutes");
			check(helper, HollowService.youngAllowed(h, now + 121_000) == 2, "none came back after two minutes");
			check(helper, HollowService.youngAllowed(h, now + 200_000) == 2, "the second came back without its own wait");
			check(helper, HollowService.youngAllowed(h, now + 241_000) == 3, "the second did not come back after another two minutes");
		} finally {
			Config.HOLLOWLINGS_PER_HOARD = per;
			Config.HOLLOWLING_RESPAWN_SECONDS = secs;
		}
		helper.succeed();
	}

	@GameTest
	public void aNestWithoutItsBarrelIsNoMore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos core = helper.absolutePos(new BlockPos(3, 1, 3));
		Hoard h = new Hoard(Dims.of(level), core.getX(), core.getY(), core.getZ(), "");
		level.setBlock(core, Blocks.BARREL.defaultBlockState(), 3);
		check(helper, HollowService.intact(h), "a hoard with its barrel counts as wrecked");
		level.setBlock(core, Blocks.AIR.defaultBlockState(), 3);
		check(helper, !HollowService.intact(h), "a hoard without its barrel still counts as a nest");
		helper.succeed();
	}
}
