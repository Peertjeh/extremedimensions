package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.AtonementGuard;
import com.extremedimensions.penalty.Dwindling;
import com.extremedimensions.penalty.ExtremeCurses;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The Extreme curses do what their (cryptic) cards hide, and atonement from kills only comes from the wild. */
public class ExtremeTests {
	private static EntityType<?> type(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", id));
	}

	/** A creature at a position relative to the test, standing still (no AI yet: it has not ticked). */
	private static Mob mob(GameTestHelper helper, String id, double x, double y, double z) {
		Mob m = (Mob) helper.spawn(type(id), new Vec3(x, y, z));
		m.setNoAi(true);
		return m;
	}

	/** Puts a player at a position relative to the test, looking along +z. */
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
	}

	@GameTest
	public void dwindlingShrinksStacks(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.DWINDLING, 2);
		ItemStack cobble = new ItemStack(Items.COBBLESTONE);
		int limit = 64 / (int) Math.round(Math.pow(Penalty.DWINDLING.value, 2));
		check(helper, p.getInventory().getMaxStackSize(cobble) == limit, "inventory stack size with Dwindling 2: expected %s, got %s",
				limit, p.getInventory().getMaxStackSize(cobble));
		check(helper, p.inventoryMenu.getSlot(9).getMaxStackSize(cobble) == limit, "menu slot stack size with Dwindling 2: expected %s, got %s",
				limit, p.inventoryMenu.getSlot(9).getMaxStackSize(cobble));
		check(helper, p.getInventory().getMaxStackSize(new ItemStack(Items.DIAMOND_SWORD)) == 1, "a sword should still stack to 1");
		p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		Dwindling.enforce(p);
		int total = 0, biggest = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(Items.COBBLESTONE)) {
				total += s.getCount();
				biggest = Math.max(biggest, s.getCount());
			}
		}
		check(helper, total == 64, "splitting lost or made items: 64 cobblestone became %s", total);
		check(helper, biggest <= limit, "a stack of %s is above the Dwindling limit %s", biggest, limit);
		check(helper, Dwindling.limit(TestPlayers.survivor(helper), 64) == 64, "an uncursed player's stacks shrank");
		helper.succeed();
	}

	@GameTest
	public void witheredGraspShortensReach(GameTestHelper helper) {
		ServerPlayer clean = cursed(helper);
		ServerPlayer p = cursed(helper, Penalty.WITHERED_GRASP, 3);
		double normal = clean.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		near(helper, p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), normal - 3 * Penalty.WITHERED_GRASP.value, "block reach with Withered Grasp 3");
		helper.succeed();
	}

	@GameTest
	public void restlessGazeWidensWithStacks(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		place(helper, p, 1.5, 2, 1.5);
		// One Enderman straight ahead, one 38 degrees to the side: 15 degrees per stack catches the first with one stack,
		// the second only with three (45 degrees)
		double side = Math.toRadians(38);
		Mob ahead = mob(helper, "enderman", 1.5, 2, 7.5);
		Mob aside = mob(helper, "enderman", 1.5 + 6 * Math.sin(side), 2, 1.5 + 6 * Math.cos(side));
		ExtremeCurses.gaze(p, 1);
		check(helper, ahead.getTarget() == p, "an Enderman straight ahead did not take the glance (1 stack)");
		check(helper, aside.getTarget() == null, "an Enderman 38 degrees aside took the glance with 1 stack (15 degrees)");
		ExtremeCurses.gaze(p, 3);
		check(helper, aside.getTarget() == p, "an Enderman 38 degrees aside did not take the glance with 3 stacks (45 degrees)");
		ahead.discard();
		aside.discard();
		helper.succeed();
	}

	@GameTest
	public void theGrudgeAngersZombifiedPiglins(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		place(helper, p, 1.5, 2, 1.5);
		Mob piglin = mob(helper, "zombified_piglin", 4.5, 2, 4.5);
		ExtremeCurses.grudge(p);
		check(helper, piglin.getTarget() == p, "a Zombified Piglin did not turn on the player");
		piglin.discard();
		helper.succeed();
	}

	@GameTest
	public void echoingStepsCarryFurther(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		place(helper, p, 1.5, 2, 0.5);
		Mob near = mob(helper, "zombie", 1.5, 2, 0.5 + 5);
		Mob far = mob(helper, "zombie", 1.5, 2, 0.5 + 7);
		for (Mob m : List.of(near, far)) {
			m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4);
		}
		// Range 4, half again as far: 6. The zombie at 5 notices, the one at 7 does not
		ExtremeCurses.echo(p, 0.5);
		check(helper, near.getTarget() == p, "a zombie at 1.25x its follow range did not notice the echo");
		check(helper, far.getTarget() == null, "a zombie at 1.75x its follow range noticed the echo");
		near.discard();
		far.discard();
		helper.succeed();
	}

	@GameTest
	public void stormcalledDrawsLightning(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.STORMCALLED, 1);
		place(helper, p, 1.5, 2, 1.5);
		PlayerData d = DataStore.get(p.getUUID());
		ServerLevel level = helper.getLevel();
		if (!level.isThundering()) {
			ExtremeCurses.storm(p, d);
			check(helper, d.stormIn == -1, "lightning was counted down without a thunderstorm");
		}
		ExtremeCurses.strike(p);
		List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, p.getBoundingBox().inflate(2));
		check(helper, !bolts.isEmpty(), "no lightning bolt came down on the player");
		// Gone before it ticks: the test area is not set alight
		bolts.forEach(Entity::discard);
		helper.succeed();
	}

	@GameTest
	public void feralScentRefusesTaming(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.FERAL_SCENT, 1);
		ServerPlayer clean = cursed(helper);
		Mob wolf = mob(helper, "wolf", 2.5, 2, 2.5);
		Mob horse = mob(helper, "horse", 4.5, 2, 4.5);
		check(helper, ExtremeCurses.refuseTaming(p, wolf) == InteractionResult.FAIL, "a wolf could be tamed with Feral Scent");
		check(helper, ExtremeCurses.refuseTaming(p, horse) == InteractionResult.FAIL, "a horse could be tamed with Feral Scent");
		check(helper, ExtremeCurses.refuseTaming(clean, wolf) == InteractionResult.PASS, "an uncursed player was refused a wolf");
		wolf.discard();
		horse.discard();
		helper.succeed();
	}

	@GameTest
	public void brokenOathCrumblesTotems(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.BROKEN_OATH, 1);
		ServerPlayer clean = cursed(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		clean.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		double chance = Penalty.BROKEN_OATH.value;
		try {
			// Forced to fail every time, so the test is not left to luck
			Penalty.BROKEN_OATH.value = 100;
			var fall = helper.getLevel().damageSources().fall();
			check(helper, ExtremeCurses.oath(p, fall, 100f), "the oath must never cancel death itself");
			ExtremeCurses.oath(clean, fall, 100f);
		} finally {
			Penalty.BROKEN_OATH.value = chance;
		}
		check(helper, p.getMainHandItem().isEmpty(), "the totem did not crumble with Broken Oath forced to certain");
		check(helper, clean.getMainHandItem().is(Items.TOTEM_OF_UNDYING), "an uncursed player's totem crumbled");
		helper.succeed();
	}

	@GameTest
	public void unhealingStopsNaturalRegeneration(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.UNHEALING, 1);
		ServerPlayer clean = cursed(helper);
		for (ServerPlayer s : List.of(p, clean)) {
			s.setHealth(10f);
			s.getFoodData().setFoodLevel(20);
			s.getFoodData().setSaturation(5f);
		}
		for (int i = 0; i < 40; i++) {
			p.getFoodData().tick(p);
			clean.getFoodData().tick(clean);
		}
		check(helper, clean.getHealth() > 10f, "the uncursed control did not regenerate (%s)", clean.getHealth());
		near(helper, p.getHealth(), 10f, "health with Unhealing after a well-fed while");
		helper.succeed();
	}

	@GameTest
	public void extremeCardsAreRareSingleAndCostDouble(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		List<String> offer = Penalty.rollOffer(d, false, 3, 1.0);
		long extreme = offer.stream().filter(id -> Penalty.byId(id).extreme).count();
		check(helper, offer.size() == 3 && extreme == 1, "a certain Extreme roll should give 3 cards with 1 Extreme, got %s", offer);
		List<String> plain = Penalty.rollOffer(d, false, 3, 0.0);
		check(helper, plain.stream().noneMatch(id -> Penalty.byId(id).extreme), "an Extreme card came without its chance: %s", plain);
		d.addStacks(Penalty.UNHEALING, 1);
		d.addStacks(Penalty.GLASS_JAW, 1);
		check(helper, PenaltyService.cost(d, Penalty.UNHEALING) == (int) Math.ceil(PenaltyService.cost(d, Penalty.GLASS_JAW) * Config.EXTREME_ATONE_FACTOR),
				"an Extreme curse should cost %sx to atone: %s vs %s", Config.EXTREME_ATONE_FACTOR, PenaltyService.cost(d, Penalty.UNHEALING),
				PenaltyService.cost(d, Penalty.GLASS_JAW));
		helper.succeed();
	}

	// ------------------------------------------------------------------ atonement from kills

	/** A zombie as the wild makes them (natural spawn, through the game's own spawn preparation). */
	private static Mob wild(GameTestHelper helper, double x, double z) {
		Mob m = mob(helper, "zombie", x, 2, z);
		ServerLevel level = helper.getLevel();
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.NATURAL, null);
		return m;
	}

	/** The player deals this share of the creature's health. */
	private static void hit(ServerPlayer p, Mob m, double share) {
		float amount = (float) (m.getMaxHealth() * share);
		AtonementGuard.damaged(m, p.damageSources().playerAttack(p), amount, amount, false);
	}

	@GameTest
	public void onlyWildKillsAtone(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		// A spawner's zombie, as in a farm
		Mob farmed = (Mob) type("zombie").spawn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), EntitySpawnReason.SPAWNER);
		hit(p, farmed, 1.0);
		check(helper, !AtonementGuard.counts(p, farmed), "a spawner's zombie earned atonement");
		Mob wild = wild(helper, 3.5, 3.5);
		check(helper, AtonementGuard.natural(wild), "a natural spawn was not remembered (the spawn mixin did not apply)");
		hit(p, wild, 0.1);
		check(helper, !AtonementGuard.counts(p, wild), "a kill with only a tenth of the damage by the player earned atonement");
		Mob fought = wild(helper, 4.5, 4.5);
		hit(p, fought, 0.6);
		check(helper, AtonementGuard.counts(p, fought), "a wild zombie fought by hand earned nothing");
		for (Mob m : List.of(farmed, wild, fought)) {
			m.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void oneSpotSaturates(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		int limit = Config.ATONE_KILL_AREA_LIMIT;
		for (int i = 0; i < limit; i++) {
			Mob m = wild(helper, 2.5, 2.5);
			hit(p, m, 1.0);
			check(helper, AtonementGuard.counts(p, m), "kill %s of %s at one spot earned nothing", i + 1, limit);
			m.discard();
		}
		Mob more = wild(helper, 3.5, 2.5);
		hit(p, more, 1.0);
		check(helper, !AtonementGuard.counts(p, more), "kill %s at the same spot still earned atonement", limit + 1);
		more.discard();
		// Elsewhere it counts again (moved there after spawning, so no far chunk has to be loaded)
		Mob away = wild(helper, 2.5, 2.5);
		away.setPos(away.getX() + Config.ATONE_KILL_AREA_RADIUS * 2, away.getY(), away.getZ());
		hit(p, away, 1.0);
		check(helper, AtonementGuard.counts(p, away), "a kill far from the saturated spot earned nothing");
		away.discard();
		helper.succeed();
	}
}
