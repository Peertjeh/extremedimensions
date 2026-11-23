package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Seam pass: thrown out, the Seam keeps its share of what was taken; a run of empty villages makes the Last Weaver
 * likelier; the world's ending lies safe on the altar.
 */
public class SeamPassTests {
	/** Thrown out, the Seam keeps its share (rounded down) of the silk and fragments carried, and only of those. */
	@GameTest
	public void theSeamKeepsItsShareWhenItThrowsYouOut(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "seam-toll");
		p.getInventory().add(new ItemStack(ModItems.SEAM_SILK, 9));
		p.getInventory().add(new ItemStack(ModItems.TETHER_FRAGMENT, 4));
		p.getInventory().add(new ItemStack(ModItems.VEIL_WING, 3));
		int silk = SeamService.keep(p, ModItems.SEAM_SILK, 0.5);
		int fragments = SeamService.keep(p, ModItems.TETHER_FRAGMENT, 0.5);
		check(helper, silk == 4 && p.getInventory().countItem(ModItems.SEAM_SILK) == 5, "half of 9 silk took %s and left %s", silk,
				p.getInventory().countItem(ModItems.SEAM_SILK));
		check(helper, fragments == 2 && p.getInventory().countItem(ModItems.TETHER_FRAGMENT) == 2, "half of 4 fragments took %s", fragments);
		check(helper, p.getInventory().countItem(ModItems.VEIL_WING) == 3, "the toll took what it should not");
		check(helper, SeamService.keep(p, ModItems.SEAM_SILK, 0) == 0, "a toll of 0 took something");
		p.getInventory().clearContent();
		helper.succeed();
	}

	/** Every village in a row that woke no Weaver makes the next likelier, until it is certain. */
	@GameTest
	public void emptyVillagesMakeTheWeaverLikelier(GameTestHelper helper) {
		double chance = Config.WEAVER_CHANCE, pity = Config.WEAVER_PITY;
		try {
			Config.WEAVER_CHANCE = 0.4;
			Config.WEAVER_PITY = 0.2;
			check(helper, Math.abs(WeaverService.villageChance(0) - 0.4) < 1e-9, "a first village's chance is %s", WeaverService.villageChance(0));
			check(helper, Math.abs(WeaverService.villageChance(1) - 0.6) < 1e-9, "after one empty village the chance is %s", WeaverService.villageChance(1));
			check(helper, WeaverService.villageChance(3) >= 1.0, "after three empty villages it is not certain (%s)", WeaverService.villageChance(3));
			Config.WEAVER_PITY = 0;
			check(helper, Math.abs(WeaverService.villageChance(5) - 0.4) < 1e-9, "with no pity the chance still grew");
		} finally {
			Config.WEAVER_CHANCE = chance;
			Config.WEAVER_PITY = pity;
		}
		helper.succeed();
	}

	/** The Thread and the Shears lie on the altar for as long as it takes, and fire cannot burn them. */
	/**
	 * While the Heart waits, everyone who steps through one rift lands at one spot on the ring round it (a party arrives
	 * together, at one gate), and rifts torn elsewhere land elsewhere on it.
	 */
	@GameTest
	public void aPartyLandsTogetherAtTheHeart(GameTestHelper helper) {
		int[] a = MendingService.arrival(120.5, -340.5), b = MendingService.arrival(120.5, -340.5);
		check(helper, a[0] == b[0] && a[1] == b[1], "one rift landed at %s,%s and at %s,%s", a[0], a[1], b[0], b[1]);
		double r = Math.sqrt((double) (a[0] - MendingService.HX) * (a[0] - MendingService.HX) + (double) (a[1] - MendingService.HZ) * (a[1] - MendingService.HZ));
		check(helper, Math.abs(r - 34) <= 1.5, "a rift landed %s blocks from the Heart, not on the ring at 34", r);
		java.util.Set<Long> spots = new java.util.HashSet<>();
		for (int i = 0; i < 12; i++) {
			int[] c = MendingService.arrival(i * 97.5, i * -53.5);
			spots.add(((long) c[0] << 32) | (c[1] & 0xFFFFFFFFL));
		}
		check(helper, spots.size() >= 6, "twelve rifts torn in different places landed on only %s spots round the Heart", spots.size());
		helper.succeed();
	}

	@GameTest
	public void theEndingLiesSafe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.2, 4.5));
		MendingService.offerEnding(level, at.x, at.y, at.z);
		AABB box = new AABB(at, at).inflate(3);
		List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, box,
				e -> e.getItem().is(ModItems.MENDING_THREAD) || e.getItem().is(ModItems.SEVERING_SHEARS));
		try {
			check(helper, lying.size() == 2, "the altar laid down %s things, not the Thread and the Shears", lying.size());
			for (ItemEntity e : lying) {
				check(helper, !e.getItem().canBeHurtBy(level.damageSources().inFire()), "%s can be burnt", e.getItem());
				check(helper, e.getAge() < 0, "%s will despawn (age %s)", e.getItem(), e.getAge());
			}
		} finally {
			lying.forEach(ItemEntity::discard);
		}
		helper.succeed();
	}
}
