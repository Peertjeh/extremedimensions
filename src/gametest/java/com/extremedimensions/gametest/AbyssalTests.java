package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import java.util.List;
import java.util.Random;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.pact.PactService;
import com.extremedimensions.penalty.AbyssalCurses;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Abyssal curses: dealt only where they are switched on and after enough Collapses, sometimes as the only card;
 * never rolled, spilled onto or sworn; four times the atonement, all of it lost on a death, no Mending Cloth; and each
 * one does what it hides and breaks at once when its deed is done.
 */
public class AbyssalTests {
	private static EntityType<?> type(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", id));
	}

	/** A creature at a position relative to the test, standing still. */
	private static Mob mob(GameTestHelper helper, String id, double x, double y, double z) {
		Mob m = (Mob) helper.spawn(type(id), new Vec3(x, y, z));
		m.setNoAi(true);
		return m;
	}

	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	private static long day(GameTestHelper helper) {
		return helper.getLevel().getServer().overworld().getGameTime() / 24000L;
	}

	// ------------------------------------------------------------------ the rules

	@GameTest
	public void dealtOnlyWhereSwitchedOnAndSometimesAlone(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int on = Config.ABYSSAL_CURSES, after = Config.ABYSSAL_AFTER_COLLAPSES, most = Config.ABYSSAL_MAX_CARRIED;
		double chance = Config.ABYSSAL_CARD_CHANCE, forced = Config.ABYSSAL_FORCED_CHANCE;
		List<String> cards = List.of("glass_jaw", "short_breath", "unhealing");
		try {
			Config.ABYSSAL_CARD_CHANCE = 1.0;
			Config.ABYSSAL_FORCED_CHANCE = 0;
			Config.ABYSSAL_AFTER_COLLAPSES = 5;
			Config.ABYSSAL_MAX_CARRIED = 1;
			Config.ABYSSAL_CURSES = 0;
			d.collapses = 10;
			check(helper, AbyssalCurses.deal(d, cards, new Random(1)).equals(cards), "an Abyssal card was dealt while they are switched off");
			Config.ABYSSAL_CURSES = 1;
			d.collapses = 4;
			check(helper, AbyssalCurses.deal(d, cards, new Random(2)).equals(cards), "an Abyssal card was dealt before the fifth Collapse");
			d.collapses = 5;
			List<String> dealt = AbyssalCurses.deal(d, cards, new Random(3));
			long deep = dealt.stream().filter(id -> Penalty.byId(id).abyssal).count();
			check(helper, dealt.size() == 3 && deep == 1, "a certain deal should put 1 Abyssal card among 3, got %s", dealt);
			check(helper, dealt.contains("unhealing"), "the Abyssal card took the Extreme card's place: %s", dealt);
			Config.ABYSSAL_FORCED_CHANCE = 1.0;
			List<String> alone = AbyssalCurses.deal(d, cards, new Random(4));
			check(helper, alone.size() == 1 && Penalty.byId(alone.get(0)).abyssal, "a forced deal should be one Abyssal card, got %s", alone);
			d.addStacks(Penalty.SUNSCORN, 1);
			check(helper, AbyssalCurses.deal(d, cards, new Random(5)).equals(cards), "a second Abyssal card was dealt with one carried (most 1)");
		} finally {
			Config.ABYSSAL_CURSES = on;
			Config.ABYSSAL_AFTER_COLLAPSES = after;
			Config.ABYSSAL_MAX_CARRIED = most;
			Config.ABYSSAL_CARD_CHANCE = chance;
			Config.ABYSSAL_FORCED_CHANCE = forced;
			d.collapses = 0;
		}
		helper.succeed();
	}

	@GameTest
	public void neverRolledSpilledOrSworn(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		for (int i = 0; i < 200; i++) {
			List<String> offer = Penalty.rollOffer(d, false, 3, 1.0);
			check(helper, offer.stream().noneMatch(id -> Penalty.byId(id).abyssal), "an ordinary roll gave an Abyssal card: %s", offer);
		}
		check(helper, Penalty.withRoom(d, true).stream().noneMatch(pen -> pen.abyssal), "stacks could spill onto an Abyssal curse");
		check(helper, Penalty.available(d, Penalty.Group.EXTREME).stream().noneMatch(pen -> pen.abyssal), "an Abyssal curse counts as an Extreme one on offer");
		for (Penalty pen : Penalty.values()) {
			if (pen.abyssal) {
				check(helper, !PactService.allowed(pen), "%s can be sworn in a pact", pen.id);
				check(helper, pen.max == 1, "%s has a maximum of %s, not 1", pen.id, pen.max);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void hardToLiftAnyOtherWay(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.SUNSCORN, 1, Penalty.GLASS_JAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		int plain = PenaltyService.cost(d, Penalty.GLASS_JAW);
		check(helper, PenaltyService.cost(d, Penalty.SUNSCORN) == (int) Math.ceil(plain * Config.ABYSSAL_ATONE_FACTOR),
				"an Abyssal curse should cost %sx to atone: %s vs %s", Config.ABYSSAL_ATONE_FACTOR, PenaltyService.cost(d, Penalty.SUNSCORN), plain);
		// A death takes all of an Abyssal curse's atonement, half of another's
		d.atonement.put("sunscorn", 40);
		d.atonement.put("glass_jaw", 40);
		PenaltyService.deathToll(d);
		check(helper, !d.atonement.containsKey("sunscorn"), "an Abyssal curse kept %s atonement through a death", d.atonement.get("sunscorn"));
		check(helper, d.atonement.getOrDefault("glass_jaw", 0) == (int) Math.floor(40 * (1 - Config.ATONE_DEATH_LOSS)),
				"an ordinary curse lost the wrong share on a death: %s left", d.atonement.get("glass_jaw"));
		// No Mending Cloth takes it
		d.atoning = "sunscorn";
		ItemStack cloth = new ItemStack(ModItems.MENDING_CLOTH);
		PenaltyService.cloth(p, cloth);
		check(helper, d.owned(Penalty.SUNSCORN) == 1 && cloth.getCount() == 1, "a Mending Cloth took an Abyssal curse");
		// Fragments: four per stack, not one
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		ItemStack three = new ItemStack(ModItems.TETHER_FRAGMENT, Config.ABYSSAL_FRAGMENTS - 1);
		p.getInventory().setItem(0, three);
		PenaltyService.mend(p, d, at, three);
		check(helper, d.owned(Penalty.SUNSCORN) == 1, "%s fragments mended an Abyssal curse", Config.ABYSSAL_FRAGMENTS - 1);
		ItemStack four = new ItemStack(ModItems.TETHER_FRAGMENT, Config.ABYSSAL_FRAGMENTS);
		p.getInventory().setItem(0, four);
		PenaltyService.mend(p, d, at, four);
		check(helper, d.owned(Penalty.SUNSCORN) == 0, "%s fragments did not mend an Abyssal curse", Config.ABYSSAL_FRAGMENTS);
		helper.succeed();
	}

	@GameTest
	public void theDeedBreaksItAtOnce(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.LOCKJAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		int target = Config.ABYSSAL_BREAK_LOCKJAW, scars = d.scars;
		try {
			Config.ABYSSAL_BREAK_LOCKJAW = 3;
			AbyssalCurses.advance(p, d, Penalty.LOCKJAW, 2);
			check(helper, AbyssalCurses.progress(d, Penalty.LOCKJAW) == 2 && d.owned(Penalty.LOCKJAW) == 1, "two of three did not count, or broke it early");
			AbyssalCurses.advance(p, d, Penalty.LOCKJAW, 1);
			check(helper, d.owned(Penalty.LOCKJAW) == 0, "the deed done did not break the curse");
			check(helper, AbyssalCurses.progress(d, Penalty.LOCKJAW) == 0, "the deed's count stayed after the curse broke");
			check(helper, d.scars == Math.min(5, scars + 1), "breaking the curse left no scar (%s -> %s)", scars, d.scars);
			AbyssalCurses.advance(p, d, Penalty.LOCKJAW, 1);
			check(helper, AbyssalCurses.progress(d, Penalty.LOCKJAW) == 0, "a deed counted for a curse not carried");
		} finally {
			Config.ABYSSAL_BREAK_LOCKJAW = target;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the eight

	@GameTest
	public void sunscornBurnsTheBareHeaded(GameTestHelper helper) {
		ServerPlayer bare = cursed(helper, Penalty.SUNSCORN, 1);
		AbyssalCurses.scorch(bare);
		check(helper, bare.getRemainingFireTicks() >= 20 * Penalty.SUNSCORN.value - 1, "a bare head in the sun did not catch fire (%s ticks)",
				bare.getRemainingFireTicks());
		ServerPlayer helmed = cursed(helper, Penalty.SUNSCORN, 1);
		helmed.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		AbyssalCurses.scorch(helmed);
		check(helper, helmed.getRemainingFireTicks() <= 0, "a helmet did not shield the player from the sun");
		bare.clearFire();
		// The deed: undead slain in daylight (a tick for the sky to brighten)
		WorldFx.run("time set noon");
		WorldFx.run("weather clear");
		helper.runAfterDelay(2, () -> {
			Mob zombie = mob(helper, "zombie", 2.5, 2, 2.5);
			ServerLevel level = helper.getLevel();
			check(helper, AbyssalCurses.inSun(level, zombie.getX(), zombie.getEyeY(), zombie.getZ()), "the test spot is not in the sun at noon");
			AbyssalCurses.onKill(bare, zombie, bare.damageSources().playerAttack(bare));
			check(helper, AbyssalCurses.progress(DataStore.get(bare.getUUID()), Penalty.SUNSCORN) == 1, "an undead slain in the sun did not count");
			zombie.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void graveboundCountsDaysAndSendsHome(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GRAVEBOUND, 1);
		PlayerData d = DataStore.get(p.getUUID());
		long now = helper.getLevel().getServer().overworld().getGameTime();
		d.graveSince = now - 2 * 24000L - 5;
		AbyssalCurses.tick(p, d);
		check(helper, AbyssalCurses.progress(d, Penalty.GRAVEBOUND) == 2 && d.owned(Penalty.GRAVEBOUND) == 1, "two days lived counted %s",
				AbyssalCurses.progress(d, Penalty.GRAVEBOUND));
		AbyssalCurses.onDeath(p);
		check(helper, AbyssalCurses.progress(d, Penalty.GRAVEBOUND) == 0, "a death did not start the days over");
		// Rising far from the world's spawn: sent back to it
		ServerLevel overworld = helper.getLevel().getServer().overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		p.teleportTo(spawn.getX() + 300.5, spawn.getY() + 10, spawn.getZ() + 0.5);
		AbyssalCurses.onRespawn(p);
		double dx = p.getX() - (spawn.getX() + 0.5), dz = p.getZ() - (spawn.getZ() + 0.5);
		check(helper, p.level() == overworld && dx * dx + dz * dz <= 4, "Gravebound did not send the player to the world spawn (%s blocks off)",
				Math.sqrt(dx * dx + dz * dz));
		d.graveSince = now - AbyssalCurses.target(Penalty.GRAVEBOUND) * 24000L;
		AbyssalCurses.tick(p, d);
		check(helper, d.owned(Penalty.GRAVEBOUND) == 0, "enough days without dying did not break Gravebound");
		helper.succeed();
	}

	@GameTest
	public void marrowRotWithersAndTheWitherBreaksIt(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.MARROW_ROT, 1);
		place(helper, p, 1.5, 2, 1.5);
		Mob zombie = mob(helper, "zombie", 3.5, 2, 1.5);
		double value = Penalty.MARROW_ROT.value;
		try {
			Penalty.MARROW_ROT.value = 100;
			AbyssalCurses.afterDamage(p, helper.getLevel().damageSources().mobAttack(zombie), 2f, 2f, false);
			check(helper, p.hasEffect(MobEffects.WITHER), "a creature's blow did not wither the player (at 100%%)");
		} finally {
			Penalty.MARROW_ROT.value = value;
			zombie.discard();
		}
		Mob wither = mob(helper, "wither", 4.5, 2, 4.5);
		AbyssalCurses.onKill(p, wither, p.damageSources().playerAttack(p));
		wither.discard();
		check(helper, DataStore.get(p.getUUID()).owned(Penalty.MARROW_ROT) == 0, "slaying the Wither did not break Marrow Rot");
		helper.succeed();
	}

	@GameTest
	public void weightOfIronSlowsAndLightArmourBreaksIt(GameTestHelper helper) {
		ServerPlayer clean = cursed(helper);
		ServerPlayer p = cursed(helper, Penalty.WEIGHT_OF_IRON, 1);
		PlayerData d = DataStore.get(p.getUUID());
		Item[] iron = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(slots[i], new ItemStack(iron[i]));
		}
		AbyssalCurses.tick(p, d);
		double normal = clean.getAttributeValue(Attributes.MOVEMENT_SPEED);
		near(helper, p.getAttributeValue(Attributes.MOVEMENT_SPEED), normal * (1 - 2 * Penalty.WEIGHT_OF_IRON.frac()), "speed in four pieces of iron");
		AbyssalCurses.elderDeeds(p, false);
		check(helper, d.owned(Penalty.WEIGHT_OF_IRON) == 1, "an Elder slain in full iron broke Weight of Iron");
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		AbyssalCurses.elderDeeds(p, false);
		check(helper, d.owned(Penalty.WEIGHT_OF_IRON) == 0, "an Elder slain in two pieces of iron did not break Weight of Iron");
		AbyssalCurses.tick(p, d);
		near(helper, p.getAttributeValue(Attributes.MOVEMENT_SPEED), normal, "speed once Weight of Iron broke");
		helper.succeed();
	}

	@GameTest
	public void lockjawRefusesMealsNearMonsters(GameTestHelper helper) {
		ServerPlayer clean = cursed(helper);
		ServerPlayer p = cursed(helper, Penalty.LOCKJAW, 1);
		place(helper, p, 1.5, 2, 1.5);
		place(helper, clean, 1.5, 2, 2.5);
		PlayerData d = DataStore.get(p.getUUID());
		ItemStack bread = new ItemStack(Items.BREAD);
		check(helper, AbyssalCurses.refuseFood(p, bread) == InteractionResult.PASS, "Lockjaw refused a meal with no monster near");
		Mob zombie = mob(helper, "zombie", 4.5, 2, 1.5);
		int target = Config.ABYSSAL_BREAK_LOCKJAW;
		try {
			check(helper, AbyssalCurses.refuseFood(p, bread) != InteractionResult.PASS, "Lockjaw let the player eat with a zombie 3 blocks away");
			check(helper, AbyssalCurses.refuseFood(p, new ItemStack(Items.COBBLESTONE)) == InteractionResult.PASS, "Lockjaw refused something not food");
			check(helper, AbyssalCurses.refuseFood(clean, bread) == InteractionResult.PASS, "an uncursed player was refused a meal");
			// The deed: monsters slain while hungry
			Config.ABYSSAL_BREAK_LOCKJAW = 2;
			p.getFoodData().setFoodLevel(20);
			AbyssalCurses.onKill(p, zombie, p.damageSources().playerAttack(p));
			check(helper, AbyssalCurses.progress(d, Penalty.LOCKJAW) == 0, "a monster slain on a full stomach counted");
			p.getFoodData().setFoodLevel(6);
			AbyssalCurses.onKill(p, zombie, p.damageSources().playerAttack(p));
			AbyssalCurses.onKill(p, zombie, p.damageSources().playerAttack(p));
			check(helper, d.owned(Penalty.LOCKJAW) == 0, "two monsters slain while hungry did not break Lockjaw");
		} finally {
			Config.ABYSSAL_BREAK_LOCKJAW = target;
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void theHungeringEatsFromTheBagAndAStarBreaksIt(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.THE_HUNGERING, 1);
		PlayerData d = DataStore.get(p.getUUID());
		p.getInventory().setItem(0, new ItemStack(Items.BREAD, 4));
		p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
		d.hungeringDay = day(helper) - 1;
		AbyssalCurses.tick(p, d);
		check(helper, p.getInventory().getItem(9).isEmpty(), "a new day passed and the bag kept its stack");
		check(helper, p.getInventory().getItem(0).getCount() == 4, "The Hungering took from the hotbar");
		p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
		AbyssalCurses.tick(p, d);
		check(helper, p.getInventory().getItem(9).getCount() == 32, "The Hungering ate twice in one day");
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		ItemStack stars = new ItemStack(Items.NETHER_STAR, 2);
		check(helper, AbyssalCurses.offerStar(p, at, stars) == InteractionResult.SUCCESS, "the anchor did not take the Nether Star");
		check(helper, stars.getCount() == 1 && d.owned(Penalty.THE_HUNGERING) == 0, "a Nether Star offered did not break The Hungering");
		check(helper, AbyssalCurses.offerStar(p, at, stars) == InteractionResult.PASS && stars.getCount() == 1,
				"a Nether Star was taken with The Hungering already broken");
		helper.succeed();
	}

	@GameTest
	public void cinderLungsBreakWithTheFerryman(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.CINDER_LUNGS, 1);
		PlayerData d = DataStore.get(p.getUUID());
		float health = p.getHealth();
		AbyssalCurses.tick(p, d);
		check(helper, p.getHealth() == health, "Cinder Lungs burned in the Overworld");
		AbyssalCurses.elderDeeds(p, false);
		check(helper, d.owned(Penalty.CINDER_LUNGS) == 1, "another Elder broke Cinder Lungs");
		AbyssalCurses.elderDeeds(p, true);
		check(helper, d.owned(Penalty.CINDER_LUNGS) == 0, "the Ferryman slain did not break Cinder Lungs");
		helper.succeed();
	}

	@GameTest
	public void mirrorWoundReflectsAndArrowsBreakIt(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.MIRROR_WOUND, 1);
		PlayerData d = DataStore.get(p.getUUID());
		place(helper, p, 1.5, 2, 1.5);
		// past the grace a player has on joining
		for (int i = 0; i < 61; i++) {
			p.doTick();
		}
		p.setHealth(20f);
		Mob zombie = mob(helper, "zombie", 3.5, 2, 1.5);
		int target = Config.ABYSSAL_BREAK_MIRROR_WOUND;
		try {
			AbyssalCurses.afterDamage(zombie, p.damageSources().playerAttack(p), 10f, 10f, false);
			near(helper, p.getHealth(), 20f - 10f * Penalty.MIRROR_WOUND.frac(), "health after striking a zombie for 10");
			Config.ABYSSAL_BREAK_MIRROR_WOUND = 2;
			AbyssalCurses.onKill(p, zombie, p.damageSources().playerAttack(p));
			check(helper, AbyssalCurses.progress(d, Penalty.MIRROR_WOUND) == 0, "a monster slain by hand counted");
			AbyssalCurses.onKill(p, zombie, p.damageSources().trident(p, p));
			AbyssalCurses.onKill(p, zombie, p.damageSources().trident(p, p));
			check(helper, d.owned(Penalty.MIRROR_WOUND) == 0, "two monsters slain with a trident did not break Mirror Wound");
		} finally {
			Config.ABYSSAL_BREAK_MIRROR_WOUND = target;
			zombie.discard();
		}
		helper.succeed();
	}
}
