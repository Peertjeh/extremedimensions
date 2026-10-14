package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.penalty.SealedPockets;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The curses do what their cards say. Runs on a headless dedicated server ({@code ./gradlew runGameTest}), so it also
 * proves the mod starts without any client class.
 */
public class CurseTests {
	/** A fresh survival player in the test's level, carrying the given curses. */
	static ServerPlayer cursed(GameTestHelper helper, Object... curseAndStacks) {
		ServerPlayer p = TestPlayers.survivor(helper);
		PlayerData d = DataStore.get(p.getUUID());
		for (int i = 0; i + 1 < curseAndStacks.length; i += 2) {
			d.addStacks((Penalty) curseAndStacks[i], (Integer) curseAndStacks[i + 1]);
		}
		PenaltyService.applyEffects(p, d);
		return p;
	}

	static void check(GameTestHelper helper, boolean ok, String message, Object... args) {
		if (!ok) {
			throw helper.assertionException(message, args);
		}
	}

	static void near(GameTestHelper helper, double actual, double expected, String what) {
		check(helper, Math.abs(actual - expected) < 0.01, "%s: expected %s, got %s", what, expected, actual);
	}

	@GameTest
	public void kindlingRaisesFireDamage(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.KINDLING, 3);
		TestPlayers.vulnerable(p);
		float before = p.getHealth();
		p.hurtServer(helper.getLevel(), helper.getLevel().damageSources().inFire(), 4.0f);
		near(helper, before - p.getHealth(), 4.0 * (1 + 3 * Penalty.KINDLING.frac()), "fire damage with Kindling 3");
		helper.succeed();
	}

	@GameTest
	public void brewsFadeAndIllsLinger(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.FADING_BREWS, 3, Penalty.LINGERING_ILLS, 2);
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 2000));
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2000));
		int speed = p.getEffect(MobEffects.SPEED).getDuration();
		int slowness = p.getEffect(MobEffects.SLOWNESS).getDuration();
		double fading = 2000 * Math.pow(1 - Penalty.FADING_BREWS.frac(), 3);
		double lingering = 2000 * (1 + 2 * Penalty.LINGERING_ILLS.frac());
		check(helper, Math.abs(speed - fading) <= 1, "Speed with Fading Brews 3: expected %s, got %s", (int) fading, speed);
		check(helper, Math.abs(slowness - lingering) <= 1, "Slowness with Lingering Ills 2: expected %s, got %s", (int) lingering, slowness);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void shrunkenStomachCapsFood(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.SHRUNKEN_STOMACH, 2);
		p.getFoodData().setFoodLevel(20);
		helper.runAfterDelay(5, () -> {
			check(helper, p.getFoodData().getFoodLevel() == 16, "food with Shrunken Stomach 2: expected 16, got %s", p.getFoodData().getFoodLevel());
			helper.succeed();
		});
	}

	@GameTest
	public void witheringLowersMaxHealth(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.WITHERING, 1);
		near(helper, p.getMaxHealth(), 18.0, "max health with Withering 1");
		helper.succeed();
	}

	@GameTest
	public void sealedPocketsRefusePickups(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.SEALED_POCKETS, 3);
		check(helper, SealedPockets.count(p) == 9, "sealed slots with Sealed Pockets 3: expected 9, got %s", SealedPockets.count(p));
		for (int i = 0; i < 27; i++) {
			p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		ItemEntity dirt = new ItemEntity(helper.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIRT, 5));
		dirt.setNoPickUpDelay();
		helper.getLevel().addFreshEntity(dirt);
		dirt.playerTouch(p);
		check(helper, dirt.isAlive() && dirt.getItem().getCount() == 5, "an item was picked up into a sealed slot");
		for (int i = 27; i < 36; i++) {
			check(helper, p.getInventory().getItem(i).isEmpty(), "sealed slot %s holds %s", i, p.getInventory().getItem(i));
		}
		check(helper, !p.inventoryMenu.getSlot(35).mayPlace(new ItemStack(Items.STONE)), "a menu lets an item into sealed slot 35");
		check(helper, p.inventoryMenu.getSlot(26).mayPlace(new ItemStack(Items.STONE)), "an unsealed slot refuses items");
		p.getInventory().setItem(13, ItemStack.EMPTY);
		dirt.playerTouch(p);
		check(helper, p.getInventory().getItem(13).is(Items.DIRT), "a freed unsealed slot did not take the item");
		helper.succeed();
	}

	@GameTest
	public void multipliedStacksSpillOver(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 2);
		PlayerData d = DataStore.get(p.getUUID());
		int before = total(d);
		d.pendingOffer = new ArrayList<>(List.of("glass_jaw"));
		d.pendingMult = new ArrayList<>(List.of(3));
		d.owed.clear();
		d.owed.add("beyond");
		d.pendingPenalties = 1;
		PenaltyService.choose(p, "glass_jaw");
		check(helper, d.stacks(Penalty.GLASS_JAW) == 3, "Glass Jaw should be at its maximum 3, is %s", d.stacks(Penalty.GLASS_JAW));
		check(helper, total(d) == before + 3, "3x should add 3 stacks in all (1 + 2 spilled), added %s", total(d) - before);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void deathHalvesAtonement(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		d.atonement.put("kindling", 121);
		d.atonement.put("glass_jaw", 7);
		d.atonement.put("scar", 1);
		ExpeditionService.onDeath(p);
		check(helper, d.atonement.getOrDefault("kindling", -1) == 60, "kindling atonement 121 -> expected 60, got %s", d.atonement.get("kindling"));
		check(helper, d.atonement.getOrDefault("glass_jaw", -1) == 3, "glass_jaw atonement 7 -> expected 3, got %s", d.atonement.get("glass_jaw"));
		check(helper, !d.atonement.containsKey("scar"), "scar atonement 1 should be gone, is %s", d.atonement.get("scar"));
		helper.succeed();
	}

	private static int total(PlayerData d) {
		int n = 0;
		for (int v : d.curses.values()) {
			n += v;
		}
		return n;
	}
}
