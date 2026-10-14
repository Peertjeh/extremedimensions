package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.mending.StarvedOne;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** The Elders: when the Matriarch comes, what the Ferryman takes and waives, the Weaver's theft, the Starved One's growth. */
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
		p.giveExperienceLevels(10);
		check(helper, FerrymanService.drainFactor(p) == 1.0, "the Tether drained faster before he came");
		check(helper, FerrymanService.summon(p), "the Ferryman could not come");
		check(helper, !FerrymanService.summon(p), "the Ferryman came twice for one passenger");
		check(helper, FerrymanService.drainFactor(p) == Math.max(0.1, Config.FERRYMAN_DRAIN), "while he walks the drain is %s", FerrymanService.drainFactor(p));
		int levels = p.experienceLevel;
		FerrymanService.toll(p);
		check(helper, p.experienceLevel == levels - Math.min(levels, Config.FERRYMAN_TOLL_LEVELS), "his blow took %s levels", levels - p.experienceLevel);
		List<Ferryman> found = helper.getLevel().getEntitiesOfClass(Ferryman.class, p.getBoundingBox().inflate(48));
		check(helper, !found.isEmpty(), "the Ferryman is nowhere near");
		Ferryman f = found.get(0);
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
}
