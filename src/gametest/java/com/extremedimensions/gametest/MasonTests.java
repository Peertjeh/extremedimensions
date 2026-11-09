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
	@GameTest(maxTicks = 240)
	public void heLiftsGreatBlocksAndHurlsThemAll(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-target");
		PaleMason m = mason(helper, p);
		check(helper, m != null, "the Pale Mason could not come");
		PaleMasonService.force(m, PaleMason.LIFT);
		int[] most = {0};
		for (int t = 5; t <= 60; t += 5) {
			helper.runAfterDelay(t, () -> most[0] = Math.max(most[0], PaleMasonService.boulders(m)));
		}
		helper.runAfterDelay(200, () -> {
			int left = PaleMasonService.boulders(m);
			int action = m.getAction();
			PaleMasonService.fade(m);
			check(helper, most[0] >= 2, "he lifted only %s great blocks", most[0]);
			check(helper, left == 0, "%s great blocks were left hanging (his action %s)", left, action);
			helper.succeed();
		});
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

	/** His ultimate opens the Last Door; gone, he takes it with him. */
	@GameTest(maxTicks = 60)
	public void hisLastDoorOpens(GameTestHelper helper) {
		ServerPlayer p = visitor(helper, "mason-door");
		PaleMason m = mason(helper, p);
		check(helper, m != null, "the Pale Mason could not come");
		PaleMasonService.force(m, ElderBoss.ULTIMATE);
		helper.runAfterDelay(20, () -> {
			boolean open = PaleMasonService.doorOpen(m);
			int action = m.getAction();
			PaleMasonService.fade(m);
			check(helper, open, "the Last Door did not open (his action %s)", action);
			check(helper, !PaleMasonService.doorOpen(m), "the Last Door stayed open after he went");
			helper.succeed();
		});
	}
}
