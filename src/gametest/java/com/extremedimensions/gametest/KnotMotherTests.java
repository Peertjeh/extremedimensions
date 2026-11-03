package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderSpells;
import com.extremedimensions.elder.ElderStrikes;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.WeaverService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

/**
 * The Knot Mother's own kit (3.23, after her design document): the Threadline sweeps low across the floor (it catches
 * a player standing, not one in the air over it); the Knot Snare holds only after its pattern has shown, and holds fast
 * (no walking, no jumping) and takes Seam time; her anchors shield her, and breaking one exposes her to heavier blows;
 * and her cords keep her near where she woke.
 */
public class KnotMotherTests {
	private static void floor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static void forceAround(GameTestHelper helper, int r) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				level.setChunkForced((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, true);
			}
		}
	}

	private static KnotMother wake(GameTestHelper helper, ServerPlayer p, BlockPos heart) {
		check(helper, WeaverService.summonKnotMother(p, heart), "the Knot Mother did not come");
		KnotMother m = helper.getLevel().getEntitiesOfClass(KnotMother.class, p.getBoundingBox().inflate(64)).stream().findFirst().orElse(null);
		check(helper, m != null, "the Knot Mother did not come");
		return m;
	}

	private static void rest(KnotMother m) {
		if (m != null && m.isAlive()) {
			WeaverService.fade(m);
			m.discard();
		}
		DataStore.seamCells().removeIf(k -> k.startsWith("km@"));
	}

	/**
	 * The Threadline: forced from sixteen blocks off, it catches the player standing on the floor; forced again with the
	 * player in the air over the floor (as at the top of a jump), it passes under them. Then, carried past her leash with
	 * the player further off still, her cords draw her back rather than after them.
	 */
	@GameTest(environment = Alone.KNOT_DRAW, maxTicks = 420)
	public void theKnotMothersThreadlineSweepsLowAndHerCordsHoldHer(GameTestHelper helper) {
		forceAround(helper, 4);
		floor(helper, -2, 24, -3, 9);
		ServerPlayer p = TestPlayers.survivor(helper, "knot-threadline");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 3));
		BlockPos heart = at.offset(16, 0, 0);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		KnotMother[] m = {null};
		float[] full = {0}, lowest = {Float.MAX_VALUE};
		int[] seen = {0};
		double[] strayed = {0};
		double attacks = Config.ELDER_ATTACK_SPEED;
		int anchors = Config.KNOT_MOTHER_ANCHORS;
		Runnable restore = () -> {
			Config.ELDER_ATTACK_SPEED = attacks;
			Config.KNOT_MOTHER_ANCHORS = anchors;
			rest(m[0]);
		};
		helper.runAfterDelay(5, () -> {
			// No blow but the ones forced here, and no anchors in the way
			Config.ELDER_ATTACK_SPEED = 0.1;
			Config.KNOT_MOTHER_ANCHORS = 0;
			m[0] = wake(helper, p, heart);
		});
		// (a player who has just joined cannot be hurt for three seconds: the blow comes after that)
		helper.runAfterDelay(70, () -> {
			p.setHealth(p.getMaxHealth());
			full[0] = p.getHealth();
			WeaverService.force(m[0], KnotMother.THREADLINE);
		});
		for (int t = 71; t <= 125; t++) {
			helper.runAfterDelay(t, () -> lowest[0] = Math.min(lowest[0], p.getHealth()));
		}
		helper.runAfterDelay(125, () -> {
			float h = lowest[0];
			if (!(h < full[0])) {
				restore.run();
			}
			check(helper, h < full[0], "her Threadline did not catch the player standing in its lane (health %s)", h);
			// In the air over the floor, as at the top of a jump: it should pass under them
			p.setHealth(p.getMaxHealth());
			full[0] = p.getHealth();
			lowest[0] = Float.MAX_VALUE;
			p.teleportTo(at.getX() + 0.5, at.getY() + 1.7, at.getZ() + 0.5);
			WeaverService.force(m[0], KnotMother.THREADLINE);
		});
		for (int t = 126; t <= 185; t++) {
			helper.runAfterDelay(t, () -> {
				// (kept up there, whatever moves a player)
				if (Math.abs(p.getY() - (at.getY() + 1.7)) > 0.05) {
					p.teleportTo(at.getX() + 0.5, at.getY() + 1.7, at.getZ() + 0.5);
				}
				seen[0] = Math.max(seen[0], m[0] == null ? 0 : ElderStrikes.threadsOf(m[0]));
				lowest[0] = Math.min(lowest[0], p.getHealth());
			});
		}
		helper.runAfterDelay(187, () -> {
			float h = lowest[0];
			if (seen[0] == 0 || h < full[0]) {
				restore.run();
			}
			check(helper, seen[0] > 0, "her second Threadline was never swept");
			check(helper, h >= full[0], "her Threadline caught a player in the air over it (health %s)", h);
			// Carried off past her leash, the player further off still: her cords draw her back, not after them
			m[0].teleportTo(heart.getX() + 0.5 + 26, m[0].getY(), heart.getZ() + 0.5);
			p.teleportTo(heart.getX() + 0.5 + 40, at.getY(), heart.getZ() + 0.5);
			strayed[0] = WeaverService.strayed(m[0]);
		});
		helper.runAfterDelay(385, () -> {
			double now = WeaverService.strayed(m[0]);
			restore.run();
			check(helper, strayed[0] > Config.KNOT_MOTHER_LEASH, "she was not carried past her leash (%s blocks)", strayed[0]);
			check(helper, now < strayed[0] - 2, "her cords did not draw her back (from %s to %s blocks off)", strayed[0], now);
			helper.succeed();
		});
	}

	/**
	 * The Knot Snare: a snare holds a player fast, no step and no jump, and picks its Seam time off their thread; it gives
	 * after its time. In a fight, her Snare's pattern shows first and catches a player who stands still in it only after
	 * its warning.
	 */
	@GameTest(environment = Alone.KNOT_SNARL, maxTicks = 220)
	public void theKnotMothersSnareHoldsFastAfterItsWarning(GameTestHelper helper) {
		forceAround(helper, 3);
		floor(helper, -2, 16, -3, 9);
		ServerPlayer p = TestPlayers.survivor(helper, "knot-snared");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 3));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		PlayerData d = DataStore.get(p.getUUID());
		double seconds = Config.KNOT_MOTHER_SNARE_SECONDS, warning = Config.KNOT_MOTHER_SNARE_WARNING;
		int time = Config.KNOT_MOTHER_SNARE_TIME;
		double attacks = Config.ELDER_ATTACK_SPEED;
		KnotMother[] m = {null};
		int[] caughtAt = {-1};
		Runnable restore = () -> {
			Config.KNOT_MOTHER_SNARE_SECONDS = seconds;
			Config.KNOT_MOTHER_SNARE_WARNING = warning;
			Config.KNOT_MOTHER_SNARE_TIME = time;
			Config.ELDER_ATTACK_SPEED = attacks;
			p.getAbilities().invulnerable = false;
			d.inSeam = false;
			d.seamLeft = 0;
			d.seamMax = 0;
			rest(m[0]);
		};
		helper.runAfterDelay(5, () -> {
			Config.KNOT_MOTHER_SNARE_SECONDS = 1.0;
			Config.KNOT_MOTHER_SNARE_TIME = 10;
			d.inSeam = true;
			d.seamMax = 100;
			d.seamLeft = 100;
			WeaverService.snared(p);
			check(helper, ElderSpells.snared(p), "the snare did not hold the player");
			check(helper, Math.abs(d.seamLeft - 90) < 1e-6, "the snare left %s seconds of thread, not 90", d.seamLeft);
			d.inSeam = false;
		});
		helper.runAfterDelay(8, () -> {
			double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED), jump = p.getAttributeValue(Attributes.JUMP_STRENGTH);
			if (speed > 1e-6 || jump > 1e-6) {
				restore.run();
			}
			check(helper, speed <= 1e-6, "held fast, the player can still walk (speed %s)", speed);
			check(helper, jump <= 1e-6, "held fast, the player can still jump (jump %s)", jump);
		});
		helper.runAfterDelay(40, () -> {
			double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
			if (ElderSpells.snared(p) || speed <= 1e-6) {
				restore.run();
			}
			check(helper, !ElderSpells.snared(p), "the snare never gave");
			check(helper, speed > 1e-6, "the snare gave, but the player still cannot walk");
			// In a fight: her Snare's pattern falls under the player, who stands still in it (kept from harm, and from any
			// blow of hers but this one)
			Config.ELDER_ATTACK_SPEED = 0.1;
			Config.KNOT_MOTHER_SNARE_WARNING = 1.5;
			p.getAbilities().invulnerable = true;
			m[0] = wake(helper, p, at.offset(10, 0, 0));
			WeaverService.force(m[0], KnotMother.SNARE);
		});
		for (int t = 41; t <= 200; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				if (caughtAt[0] < 0 && ElderSpells.snared(p)) {
					caughtAt[0] = tick;
				}
			});
		}
		helper.runAfterDelay(202, () -> {
			int when = caughtAt[0];
			restore.run();
			check(helper, when > 0, "her Snare never caught the player who stood in it");
			// forced at 40, its pattern drawn some beats in, then at least 1.5 seconds of warning
			check(helper, when - 40 >= 30, "her Snare held the player only %s ticks after it was cast (no fair warning)", when - 40);
			helper.succeed();
		});
	}

	/**
	 * Her anchors: three stand round her chamber once she is formed and shield her from much of a blow; three hits on one
	 * break it and expose her, and an exposed Knot Mother takes far more of a blow; the exposure passes; and when she
	 * goes, her anchors go with her.
	 */
	@GameTest(environment = Alone.KNOT_ANCHOR, maxTicks = 240)
	public void theKnotMothersAnchorsShieldHerAndBreakingOneExposesHer(GameTestHelper helper) {
		forceAround(helper, 4);
		floor(helper, -2, 26, -8, 16);
		ServerPlayer p = TestPlayers.survivor(helper, "knot-anchors");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 4));
		BlockPos heart = at.offset(12, 0, 0);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.getAbilities().invulnerable = true;
		ServerLevel level = helper.getLevel();
		KnotMother[] m = {null};
		float[] dealt = {0, 0};
		List<BlockPos> nodes = new ArrayList<>();
		double attacks = Config.ELDER_ATTACK_SPEED;
		Runnable restore = () -> {
			Config.ELDER_ATTACK_SPEED = attacks;
			p.getAbilities().invulnerable = false;
			rest(m[0]);
		};
		helper.runAfterDelay(5, () -> {
			Config.ELDER_ATTACK_SPEED = 0.1;
			m[0] = wake(helper, p, heart);
			// Past her forming at once (a harmless "blow": she ties her anchors on her first tick formed)
			WeaverService.force(m[0], KnotMother.EXPOSED);
		});
		helper.runAfterDelay(12, () -> {
			nodes.addAll(WeaverService.anchors(m[0]));
			if (nodes.size() != 3) {
				restore.run();
			}
			check(helper, nodes.size() == 3, "she tied %s anchors, not 3", nodes.size());
			for (BlockPos n : nodes) {
				check(helper, level.getBlockState(n).is(Blocks.OCHRE_FROGLIGHT) && level.getBlockState(n.below()).is(Blocks.CRYING_OBSIDIAN),
						"an anchor at %s is not a pillar of crying obsidian under a node of light", n);
			}
			float h = m[0].getHealth();
			m[0].hurtServer(level, level.damageSources().playerAttack(p), 40f);
			dealt[0] = h - m[0].getHealth();
		});
		for (int k = 0; k < 3; k++) {
			helper.runAfterDelay(20 + 10 * k, () -> check(helper, WeaverService.anchorHit(p, level, nodes.get(0)), "a strike on an anchor's node did not count"));
		}
		helper.runAfterDelay(44, () -> {
			int left = WeaverService.anchors(m[0]).size();
			boolean exposed = WeaverService.exposed(m[0]) > 0 && m[0].getAction() == KnotMother.EXPOSED;
			boolean gone = level.getBlockState(nodes.get(0)).isAir();
			if (left != 2 || !exposed || !gone) {
				restore.run();
			}
			check(helper, left == 2, "three hits left %s anchors standing, not 2", left);
			check(helper, gone, "the broken anchor's node is still there");
			check(helper, exposed, "breaking an anchor did not expose her");
			float h = m[0].getHealth();
			m[0].hurtServer(level, level.damageSources().playerAttack(p), 40f);
			dealt[1] = h - m[0].getHealth();
			if (!(dealt[0] > 0 && dealt[1] > dealt[0] * 2.5f)) {
				restore.run();
			}
			check(helper, dealt[0] > 0 && dealt[1] > dealt[0] * 2.5f,
					"shielded she lost %s, exposed %s: the anchors do not shield her or exposure does not open her", dealt[0], dealt[1]);
		});
		helper.runAfterDelay(160, () -> {
			boolean over = WeaverService.exposed(m[0]) == 0 && m[0].getAction() != KnotMother.EXPOSED;
			List<BlockPos> standing = WeaverService.anchors(m[0]);
			restore.run();
			check(helper, over, "her exposure never passed");
			for (BlockPos n : standing) {
				check(helper, level.getBlockState(n).isAir() && level.getBlockState(n.below()).isAir(), "an anchor at %s outlived her", n);
			}
			check(helper, DataStore.seamCells().stream().noneMatch(k -> k.startsWith("ka@")), "her anchors' marks outlived her");
			helper.succeed();
		});
	}
}
