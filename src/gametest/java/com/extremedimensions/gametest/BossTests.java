package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderTerrain;
import com.extremedimensions.elder.ElderUltimate;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The Elders: when the Matriarch comes, what the Ferryman takes and waives, the Weaver's theft, the Starved One's growth,
 * what an ultimate lays on those it reaches, and how the Elders tear up the world (only when allowed, and the thorns
 * they raise sink back).
 */
public class BossTests {
	@GameTest
	public void theStarvedOneGrowsWithItsFoes(GameTestHelper helper) {
		double per = Config.STARVED_HEALTH_PER_PLAYER;
		try {
			Config.STARVED_HEALTH_PER_PLAYER = 0.5;
			check(helper, MendingService.healthFor(1) == StarvedOne.BASE_HEALTH, "alone, it has %s health", MendingService.healthFor(1));
			check(helper, MendingService.healthFor(3) == StarvedOne.BASE_HEALTH * 2, "against three, it has %s health", MendingService.healthFor(3));
			Config.STARVED_HEALTH_PER_PLAYER = 0;
			check(helper, MendingService.healthFor(4) == StarvedOne.BASE_HEALTH, "with no growth set, it still grew");
		} finally {
			Config.STARVED_HEALTH_PER_PLAYER = per;
		}
		helper.succeed();
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

	@GameTest
	public void theFerrymanTakesHisTollAndWaivesTheFee(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.FERRYMANS_FEE, 2);
		PlayerData d = DataStore.get(p.getUUID());
		// Test players join at the world's origin; he must find ground where the test's chunks are loaded
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.giveExperienceLevels(10);
		check(helper, FerrymanService.drainFactor(p) == 1.0, "the Tether drained faster before he came");
		Ferryman f = FerrymanService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
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
		ElderTerrain.column(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 3, Blocks.MANGROVE_ROOTS, 5);
		check(helper, level.getBlockState(ground.above()).is(Blocks.MANGROVE_ROOTS), "no thorns rose out of the ground");
		check(helper, level.getBlockState(ground.above(3)).is(Blocks.MANGROVE_ROOTS), "the thorns did not rise three blocks");
		helper.runAfterDelay(30, () -> {
			check(helper, level.getBlockState(ground.above()).isAir() && level.getBlockState(ground.above(3)).isAir(), "the thorns did not sink back");
			helper.succeed();
		});
	}
}
