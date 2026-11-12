package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.PaleMason;
import com.extremedimensions.elder.PaleMasonService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Pale Mason (3.40): he comes with his own health and leaves whole; he lifts great blocks and hurls them, and none
 * is left hanging; his Gaze strikes only one who looks him in the face (a carved pumpkin keeps it off); his Last Door
 * opens. No arena here: it would reach into the tests round these.
 */
public class MasonTests {
	private static PaleMason mason(GameTestHelper helper, ServerPlayer p) {
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		try {
			return PaleMasonService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		} finally {
			Config.ELDER_ARENA = arena;
		}
	}

	/**
	 * His blows reach far (a hurled block flies at anyone within 48 blocks), so the tests that let him fight make them
	 * harmless: the players of the tests round these are not his. (Only his own settings change, which no other test
	 * reads.) Run the returned task when he is gone.
	 */
	private static Runnable harmless() {
		double strike = Config.PALE_MASON_STRIKE_DAMAGE, block = Config.PALE_MASON_BLOCK_DAMAGE, gaze = Config.PALE_MASON_GAZE_DAMAGE,
				door = Config.PALE_MASON_DOOR_DAMAGE, chain = Config.PALE_MASON_CHAIN_DAMAGE, pillar = Config.PALE_MASON_PILLAR_DAMAGE,
				ring = Config.PALE_MASON_RING_DAMAGE, burst = Config.PALE_MASON_BURST_DAMAGE;
		Config.PALE_MASON_STRIKE_DAMAGE = 0;
		Config.PALE_MASON_BLOCK_DAMAGE = 0;
		Config.PALE_MASON_GAZE_DAMAGE = 0;
		Config.PALE_MASON_DOOR_DAMAGE = 0;
		Config.PALE_MASON_CHAIN_DAMAGE = 0;
		Config.PALE_MASON_PILLAR_DAMAGE = 0;
		Config.PALE_MASON_RING_DAMAGE = 0;
		Config.PALE_MASON_BURST_DAMAGE = 0;
		return () -> {
			Config.PALE_MASON_STRIKE_DAMAGE = strike;
			Config.PALE_MASON_BLOCK_DAMAGE = block;
			Config.PALE_MASON_GAZE_DAMAGE = gaze;
			Config.PALE_MASON_DOOR_DAMAGE = door;
			Config.PALE_MASON_CHAIN_DAMAGE = chain;
			Config.PALE_MASON_PILLAR_DAMAGE = pillar;
			Config.PALE_MASON_RING_DAMAGE = ring;
			Config.PALE_MASON_BURST_DAMAGE = burst;
		};
	}

	private static ServerPlayer visitor(GameTestHelper helper, String name) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	/** He comes for his visitor with his own health, and goes back into the void whole. */
	@GameTest(maxTicks = 40)
	public void thePaleMasonComesAndGoes(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-visitor");
		PaleMason m = mason(helper, p);
		check(helper, m != null, "the Pale Mason could not come");
		boolean driven = PaleMasonService.isDriven(m);
		boolean hunting = PaleMasonService.hunting(p);
		double max = m.getMaxHealth();
		PaleMasonService.fade(m);
		check(helper, driven && hunting, "the Pale Mason is not after his visitor (driven %s, hunting %s)", driven, hunting);
		check(helper, Math.abs(max - Math.min(1024, Config.PALE_MASON_HEALTH)) < 0.5, "his health is %s, not his setting %s", max, Config.PALE_MASON_HEALTH);
		check(helper, !PaleMasonService.isDriven(m) && m.isRemoved(), "he did not go");
		helper.succeed();
	}

	/** Falling Blocks: great blocks rise round him, then he hurls them, and none is left hanging afterwards. */
	@GameTest(environment = Alone.MASON_LIFT, maxTicks = 240)
	public void heLiftsGreatBlocksAndHurlsThemAll(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-target");
		// He lifts a block more for every player he counts within elder_fighters_range (200 blocks), which reaches the
		// players of every test round this one: only his own target counts here, so he lifts as many as for one
		int range = Config.ELDER_FIGHTERS_RANGE;
		Config.ELDER_FIGHTERS_RANGE = 8;
		Runnable harmless = harmless();
		Runnable restore = () -> {
			harmless.run();
			Config.ELDER_FIGHTERS_RANGE = range;
		};
		PaleMason m = mason(helper, p);
		if (m == null) {
			restore.run();
			throw helper.assertionException("the Pale Mason could not come");
		}
		PaleMasonService.force(m, PaleMason.LIFT);
		// (he fights on after it, and may lift again of his own accord: what counts is that the blocks of this lift are
		// all gone once its throw is over, the first time he turns to anything else)
		int[] most = {0}, after = {-1};
		boolean[] threw = {false};
		for (int t = 1; t <= 200; t++) {
			int now = t;
			helper.runAfterDelay(t, () -> {
				if (after[0] >= 0) {
					return;
				}
				int action = m.getAction();
				most[0] = Math.max(most[0], PaleMasonService.boulders(m));
				threw[0] |= action == PaleMason.THROW;
				if (threw[0] && action != PaleMason.THROW && action != PaleMason.LIFT) {
					after[0] = now;
					int left = PaleMasonService.boulders(m);
					String where = PaleMasonService.describe(m);
					PaleMasonService.fade(m);
					restore.run();
					check(helper, most[0] >= 2, "he lifted only %s great blocks", most[0]);
					check(helper, left == 0, "%s great blocks were left hanging once he had thrown (%s)", left, where);
					helper.succeed();
				} else if (now == 200) {
					String where = PaleMasonService.describe(m);
					PaleMasonService.fade(m);
					restore.run();
					throw helper.assertionException(threw[0] ? "his throw never ended (" + where + ")" : "he never threw what he lifted (" + where + ")");
				}
			});
		}
	}

	/** The Gaze strikes one who looks him in the face; not one who looks away, nor one in a carved pumpkin. */
	@GameTest(maxTicks = 40)
	public void hisGazeNeedsAFaceAndAPumpkinKeepsItOff(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-gazer");
		PaleMason m = mason(helper, p);
		check(helper, m != null, "the Pale Mason could not come");
		// look up at his eyes
		double dx = m.getX() - p.getX(), dz = m.getZ() - p.getZ(), dy = m.getY() + m.getEyeHeight() - p.getEyeY();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		p.setYRot(yaw);
		p.setXRot(pitch);
		boolean sees = p.hasLineOfSight(m);
		boolean face = PaleMasonService.looksAt(p, m);
		p.setYRot(yaw + 180);
		boolean away = PaleMasonService.looksAt(p, m);
		p.setYRot(yaw);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		boolean pumpkin = PaleMasonService.looksAt(p, m);
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		PaleMasonService.fade(m);
		check(helper, !sees || face, "looking him in the face does not count as looking at him");
		check(helper, !away, "looking away counts as looking at him");
		check(helper, !pumpkin, "a carved pumpkin does not keep his gaze off");
		helper.succeed();
	}

	/** Bound Stones (3.41): he swings chained stones round himself and lets them go; none is left in the air afterwards. */
	@GameTest(maxTicks = 240)
	public void heSwingsChainedStonesAndLetsThemGo(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-chained");
		Runnable restore = harmless();
		PaleMason m = mason(helper, p);
		if (m == null) {
			restore.run();
			throw helper.assertionException("the Pale Mason could not come");
		}
		PaleMasonService.force(m, PaleMason.CHAINS);
		int[] most = {0};
		for (int t = 5; t <= 90; t += 5) {
			helper.runAfterDelay(t, () -> most[0] = Math.max(most[0], PaleMasonService.chained(m)));
		}
		// (just after the blow, before his rest is over and another can begin)
		helper.runAfterDelay(136, () -> {
			int left = PaleMasonService.chained(m);
			int action = m.getAction();
			PaleMasonService.fade(m);
			restore.run();
			check(helper, most[0] >= 2, "he swung only %s chained stones", most[0]);
			check(helper, left == 0, "%s stones were left in the air (his action %s)", left, action);
			helper.succeed();
		});
	}

	/** Pillars of the Anchor (3.41): sigils are lit where his pillars will tear up, under his visitor too, and all of them go. */
	@GameTest(maxTicks = 140)
	public void hisPillarsRiseUnderHisVisitor(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-pillared");
		Runnable restore = harmless();
		PaleMason m = mason(helper, p);
		if (m == null) {
			restore.run();
			throw helper.assertionException("the Pale Mason could not come");
		}
		PaleMasonService.force(m, PaleMason.PILLARS);
		int[] most = {0};
		for (int t = 4; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> most[0] = Math.max(most[0], PaleMasonService.pillarsWaiting(m)));
		}
		helper.runAfterDelay(110, () -> {
			int left = PaleMasonService.pillarsWaiting(m);
			PaleMasonService.fade(m);
			restore.run();
			check(helper, most[0] >= 5, "only %s pillars were waiting to tear up", most[0]);
			check(helper, left == 0, "%s pillars never tore up", left);
			helper.succeed();
		});
	}

	/** The Unfinished Spiral (3.41): its stones wind round him, it draws his visitor in, and once it bursts none is left. */
	@GameTest(maxTicks = 200)
	public void hisSpiralDrawsInAndBursts(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-spiralled");
		Runnable restore = harmless();
		PaleMason m = mason(helper, p);
		if (m == null) {
			restore.run();
			throw helper.assertionException("the Pale Mason could not come");
		}
		int felt = com.extremedimensions.events.PaleSquall.felt(p.getUUID());
		PaleMasonService.force(m, PaleMason.SPIRAL);
		int[] most = {0};
		for (int t = 10; t <= 80; t += 10) {
			helper.runAfterDelay(t, () -> most[0] = Math.max(most[0], PaleMasonService.spiralling(m)));
		}
		// (just after the burst and the blow, before his rest is over and another can begin)
		helper.runAfterDelay(120, () -> {
			int left = PaleMasonService.spiralling(m);
			int drawn = com.extremedimensions.events.PaleSquall.felt(p.getUUID()) - felt;
			PaleMasonService.fade(m);
			restore.run();
			check(helper, most[0] >= 6, "only %s stones wound round him", most[0]);
			check(helper, drawn > 0, "his spiral never drew his visitor in");
			check(helper, left == 0, "%s stones of his spiral were left in the air", left);
			helper.succeed();
		});
	}

	/** His ultimate opens the Last Door; gone, he takes it with him. */
	@GameTest(maxTicks = 60)
	public void hisLastDoorOpens(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-door");
		Runnable restore = harmless();
		PaleMason m = mason(helper, p);
		if (m == null) {
			restore.run();
			throw helper.assertionException("the Pale Mason could not come");
		}
		PaleMasonService.force(m, ElderBoss.ULTIMATE);
		helper.runAfterDelay(20, () -> {
			boolean open = PaleMasonService.doorOpen(m);
			int action = m.getAction();
			PaleMasonService.fade(m);
			restore.run();
			check(helper, open, "the Last Door did not open (his action %s)", action);
			check(helper, !PaleMasonService.doorOpen(m), "the Last Door stayed open after he went");
			helper.succeed();
		});
	}
}
