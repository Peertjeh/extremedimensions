package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.lurker.Cragback;
import com.extremedimensions.lurker.CragbackService;
import com.extremedimensions.lurker.Edgecrawler;
import com.extremedimensions.lurker.EdgecrawlerService;
import com.extremedimensions.lurker.Threadbiter;
import com.extremedimensions.lurker.ThreadbiterService;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Seam's own of 3.22: a Threadbiter gnaws seconds off a visitor's thread and gives every one of them back when it
 * is slain; an Edgecrawler drags its prey to an island's edge and throws them over, unless they crouch; a Cragback
 * asleep wakes when a block is broken near it, slams whoever is close, and gives up Tetherite when it dies.
 */
public class SeamKinTests {
	private static void floor(GameTestHelper helper, int y, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
			}
		}
	}

	@GameTest(environment = Alone.THREAD_BITE, maxTicks = 300)
	public void aThreadbiterGnawsTheThreadAndGivesItBackWhenSlain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 0, 0, 7, 0, 7);
		ServerPlayer p = TestPlayers.survivor(helper, "thread-visitor");
		Vec3 start = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
		p.snapTo(start.x, start.y, start.z, 0f, 0f);
		PlayerData d = DataStore.get(p.getUUID());
		d.seamMax = 100;
		d.seamLeft = 100;
		double bite = Config.THREADBITER_BITE;
		int bites = Config.THREADBITER_BITES;
		Config.THREADBITER_BITE = 4;
		Config.THREADBITER_BITES = 5;
		Runnable restore = () -> {
			Config.THREADBITER_BITE = bite;
			Config.THREADBITER_BITES = bites;
			d.seamLeft = 0;
			d.seamMax = 0;
		};
		Threadbiter[] in = {null};
		helper.runAfterDelay(5, () -> {
			in[0] = ModEntities.THREADBITER.spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Threadbiter came");
			ThreadbiterService.adopt(level, in[0], p);
		});
		boolean[] done = {false};
		for (int t = 6; t <= 260; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive() || ThreadbiterService.eaten(in[0]) < 8) {
					return;
				}
				done[0] = true;
				try {
					double ate = ThreadbiterService.eaten(in[0]);
					check(helper, Math.abs(d.seamLeft - (100 - ate)) < 1e-6, "the thread holds %s seconds after %s were eaten", d.seamLeft, ate);
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 100.0f);
					check(helper, !in[0].isAlive(), "the Threadbiter did not die");
					check(helper, Math.abs(d.seamLeft - 100) < 1e-6, "slain, the Threadbiter gave back the thread only to %s seconds", d.seamLeft);
				} finally {
					restore.run();
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(261, () -> {
			if (done[0]) {
				return;
			}
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + ThreadbiterService.mode(in[0]) + ", ate " + ThreadbiterService.eaten(in[0]) + ")";
			restore.run();
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Threadbiter never gnawed at the thread (it is at %s)", where);
		});
	}

	/**
	 * A ledge of stone with open air beyond it: the player stands near its edge looking out, the Edgecrawler comes up
	 * behind them. {@code crouch}: they plant their feet.
	 */
	private static void edge(GameTestHelper helper, String name, boolean crouch) {
		ServerLevel level = helper.getLevel();
		floor(helper, 4, 0, 7, 0, 3);
		ServerPlayer p = TestPlayers.survivor(helper, name);
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 5, 2.5));
		p.snapTo(start.x, start.y, start.z, 0f, 0f);
		p.setShiftKeyDown(crouch);
		int drop = Config.EDGECRAWLER_DROP;
		Config.EDGECRAWLER_DROP = 3;
		Runnable restore = () -> Config.EDGECRAWLER_DROP = drop;
		Edgecrawler[] in = {null};
		helper.runAfterDelay(5, () -> {
			in[0] = ModEntities.EDGECRAWLER.spawn(level, helper.absolutePos(new BlockPos(3, 5, 0)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Edgecrawler came");
			EdgecrawlerService.adopt(level, in[0], p);
		});
		boolean[] dragged = {false};
		boolean[] done = {false};
		for (int t = 6; t <= 200; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0]) {
					return;
				}
				String mode = EdgecrawlerService.mode(in[0]);
				dragged[0] |= "drag".equals(mode);
				boolean over = EdgecrawlerService.hurls(in[0]) > 0 || (dragged[0] && ("retreat".equals(mode) || mode.isEmpty()));
				if (!over) {
					return;
				}
				done[0] = true;
				try {
					check(helper, dragged[0], "the Edgecrawler never took hold of its prey");
					if (crouch) {
						check(helper, EdgecrawlerService.hurls(in[0]) == 0, "it threw a crouching player over the edge");
					} else {
						check(helper, EdgecrawlerService.hurls(in[0]) == 1, "it did not throw its prey (hurls: %s)", EdgecrawlerService.hurls(in[0]));
						check(helper, p.getDeltaMovement().z > 0.2, "its prey was not thrown toward the edge (moving %s)", p.getDeltaMovement());
					}
				} finally {
					restore.run();
					p.setShiftKeyDown(false);
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(201, () -> {
			if (done[0]) {
				return;
			}
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + EdgecrawlerService.mode(in[0]) + ")";
			restore.run();
			p.setShiftKeyDown(false);
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Edgecrawler never dragged its prey to the edge (it is at %s)", where);
		});
	}

	@GameTest(environment = Alone.EDGE_THROW, maxTicks = 260)
	public void anEdgecrawlerThrowsItsPreyOverTheEdge(GameTestHelper helper) {
		edge(helper, "edge-visitor", false);
	}

	@GameTest(environment = Alone.EDGE_THROW, maxTicks = 260)
	public void anEdgecrawlerCannotThrowOneWhoCrouches(GameTestHelper helper) {
		edge(helper, "edge-croucher", true);
	}

	@GameTest(environment = Alone.CRAG_WAKE, maxTicks = 300)
	public void aCragbackWakesWhenDugNearAndSlams(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 0, -2, 9, -2, 9);
		ServerPlayer p = TestPlayers.survivor(helper, "crag-digger");
		Vec3 start = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
		p.snapTo(start.x, start.y, start.z, 0f, 0f);
		p.setShiftKeyDown(true);
		Cragback[] in = {null};
		float[] full = {0};
		helper.runAfterDelay(5, () -> {
			in[0] = ModEntities.CRAGBACK.spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Cragback came");
			CragbackService.adopt(level, in[0], p);
			check(helper, CragbackService.stones(in[0]) >= 5, "its back has %s stones on it, not 5", CragbackService.stones(in[0]));
			check(helper, "dormant".equals(CragbackService.mode(in[0])), "it is not asleep but %s", CragbackService.mode(in[0]));
		});
		helper.runAfterDelay(30, () -> {
			check(helper, "dormant".equals(CragbackService.mode(in[0])), "it woke with no one near (%s)", CragbackService.mode(in[0]));
			// A block broken near it: it wakes
			BlockPos dug = helper.absolutePos(new BlockPos(6, 0, 6));
			CragbackService.noise(p, dug, ModBlocks.TETHERITE_ORE.defaultBlockState());
			check(helper, "wake".equals(CragbackService.mode(in[0])), "it did not wake when dug near (%s)", CragbackService.mode(in[0]));
			// Its prey stands close
			Vec3 close = in[0].position().add(2.6, 0, 0);
			p.snapTo(close.x, close.y, close.z, 90f, 0f);
			p.setShiftKeyDown(false);
			full[0] = p.getHealth();
		});
		boolean[] done = {false};
		for (int t = 31; t <= 260; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || done[0] || !in[0].isAlive()) {
					return;
				}
				// (it walks after its prey: keep them close)
				Vec3 close = in[0].position().add(2.6, 0, 0);
				if (p.position().distanceTo(close) > 0.5) {
					p.snapTo(close.x, close.y, close.z, 90f, 0f);
				}
				if (CragbackService.slams(in[0]) < 1) {
					return;
				}
				done[0] = true;
				try {
					check(helper, p.getHealth() < full[0], "its slam did no harm (health %s)", p.getHealth());
					Vec3 at = in[0].position();
					in[0].hurtServer(level, level.damageSources().playerAttack(p), 5000.0f);
					check(helper, !in[0].isAlive(), "the Cragback did not die");
					int scrap = 0;
					for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(5))) {
						if (e.getItem().is(ModItems.TETHERITE_SCRAP)) {
							scrap += e.getItem().getCount();
						}
					}
					check(helper, scrap >= 1, "the slain Cragback gave up no Tetherite scrap");
				} finally {
					if (in[0].isAlive()) {
						in[0].discard();
					}
				}
				helper.succeed();
			});
		}
		helper.runAfterDelay(261, () -> {
			if (done[0]) {
				return;
			}
			String where = in[0] == null ? "gone" : in[0].blockPosition() + " (" + CragbackService.mode(in[0]) + ")";
			if (in[0] != null && in[0].isAlive()) {
				in[0].discard();
			}
			check(helper, false, "the Cragback never slammed (it is at %s)", where);
		});
	}
}
