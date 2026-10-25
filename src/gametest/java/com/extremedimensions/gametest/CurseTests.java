package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.CarriedContainers;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.CurseRunes;
import com.extremedimensions.penalty.Dwindling;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.penalty.SealedPockets;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

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
		CurseEffects.applyEffects(p, d);
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

	/**
	 * On Hard and Extreme a curse the player has never carried reads in runes, on its card and in the Curses screen, until
	 * they take it; after that it stays readable, even once its last stack is lifted. On Easy and Normal every curse reads.
	 */
	@GameTest
	public void unknownCursesReadInRunesUntilTaken(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int before = Config.CURSE_RUNES;
		try {
			Config.CURSE_RUNES = 0;
			check(helper, CurseRunes.readable(d, Penalty.GLASS_JAW), "on Normal a curse never carried does not read");
			Config.CURSE_RUNES = 1;
			check(helper, !CurseRunes.readable(d, Penalty.GLASS_JAW), "on Hard a curse never carried reads");
			check(helper, readableFlag(com.extremedimensions.network.Sync.cursesData(p, d), "glass_jaw").equals("0"),
					"the Curses screen is told it can read Glass Jaw");
			d.pendingOffer = new ArrayList<>(List.of("glass_jaw"));
			PenaltyService.choose(p, "glass_jaw");
			check(helper, d.owned(Penalty.GLASS_JAW) == 1, "choosing the card added %s stacks", d.owned(Penalty.GLASS_JAW));
			check(helper, CurseRunes.readable(d, Penalty.GLASS_JAW), "a curse taken still reads in runes");
			check(helper, readableFlag(com.extremedimensions.network.Sync.cursesData(p, d), "glass_jaw").equals("1"),
					"the Curses screen is not told it can read Glass Jaw now");
			d.removeStack(Penalty.GLASS_JAW);
			check(helper, CurseRunes.readable(d, Penalty.GLASS_JAW), "a curse lifted went back to runes");
			check(helper, !CurseRunes.readable(d, Penalty.SHORT_BREATH), "taking one curse taught another");
			// a sworn pact counts as taking the curse
			d.learn(Penalty.SHORT_BREATH);
			check(helper, CurseRunes.readable(d, Penalty.SHORT_BREATH), "a curse learnt does not read");
		} finally {
			Config.CURSE_RUNES = before;
		}
		String runes = CurseRunes.runes("Glass Jaw: 25% harder, Één");
		check(helper, runes.chars().noneMatch(c -> c < 128 && Character.isLetterOrDigit(c)), "letters show through the runes: %s", runes);
		check(helper, runes.equals(CurseRunes.runes("Glass Jaw: 25% harder, Één")) && runes.contains(" ") && runes.contains("%"),
				"the runes are not a fixed cipher keeping spaces and marks: %s", runes);
		// The Seam script: every letter and digit its own rune, all of them the mod's (none from a real alphabet)
		String all = CurseRunes.runes("abcdefghijklmnopqrstuvwxyz0123456789");
		check(helper, all.chars().distinct().count() == CurseRunes.COUNT
				&& all.chars().allMatch(c -> c >= CurseRunes.FIRST && c < CurseRunes.FIRST + CurseRunes.COUNT),
				"the runes are not the Seam script's own 36: %s", all.chars().mapToObj(Integer::toHexString).toList());
		helper.succeed();
	}

	/** The seventh field of a curse's entry in the Curses screen's data: 1 if the player can read it. */
	private static String readableFlag(String data, String id) {
		for (String part : data.split("\\|", -1)[3].split(";")) {
			String[] kv = part.split(":");
			if (kv[0].equals(id)) {
				return kv[6];
			}
		}
		return "?";
	}

	/** The damage a hit is raised to (the hook on every hit is proven by {@link WorldTests#everyMixinIsApplied}). */
	@GameTest
	public void cursesRaiseDamageTaken(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.KINDLING, 3, Penalty.VOLATILE, 2);
		var sources = helper.getLevel().damageSources();
		near(helper, CurseEffects.damageTaken(p, sources.inFire(), 4.0f), 4.0 * (1 + 3 * Penalty.KINDLING.frac()), "fire damage with Kindling 3");
		near(helper, CurseEffects.damageTaken(p, sources.explosion(null, null), 4.0f), 4.0 * (1 + 2 * Penalty.VOLATILE.frac()), "explosion damage with Volatile 2");
		near(helper, CurseEffects.damageTaken(p, sources.fall(), 4.0f), 4.0, "fall damage, untouched by both");
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

	/** A shulker box or bundle carried in the inventory is no way round the carrying curses. */
	@GameTest
	public void carriedBoxesAreNoWayRoundThePocketCurses(GameTestHelper helper) {
		List<ItemStack> full = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			full.add(new ItemStack(Items.STONE, 64));
		}
		// Sealed Pockets 3 seals 9 of 36 slots, so 7 of a box's 27 (rounded up); what lies there comes out
		ServerPlayer sealed = cursed(helper, Penalty.SEALED_POCKETS, 3);
		ItemStack box = new ItemStack(Items.SHULKER_BOX);
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(full));
		sealed.getInventory().setItem(0, box);
		List<ItemStack> out = new ArrayList<>();
		CarriedContainers.seal(sealed, SealedPockets.count(sealed), out::add);
		int kept = CarriedContainers.inside(sealed.getInventory().getItem(0));
		check(helper, kept == 27 - CarriedContainers.sealedInBox(9) && kept == 20, "a carried box with 9 pocket slots sealed kept %s stacks, expected 20", kept);
		check(helper, out.size() == 7 && out.stream().allMatch(s -> s.is(Items.STONE) && s.getCount() == 64), "the sealed slots of the box gave out %s", out);
		// Lead Pockets weighs each stack inside as one of its own: the box and its 20 stacks are 21
		check(helper, 1 + CarriedContainers.inside(box) > 18, "a full box does not weigh down the pockets");

		// Dwindling shrinks the stacks inside a carried box, and nothing is lost or made
		ServerPlayer small = cursed(helper, Penalty.DWINDLING, 2);
		int limit = Dwindling.limit(small, 64);
		ItemStack box2 = new ItemStack(Items.SHULKER_BOX);
		box2.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 64))));
		small.getInventory().setItem(0, box2);
		List<ItemStack> rest = new ArrayList<>();
		CarriedContainers.dwindle(small, rest::add);
		int inside = 0, biggest = 0;
		for (ItemStack s : small.getInventory().getItem(0).get(DataComponents.CONTAINER).nonEmptyItemCopyStream().toList()) {
			inside += s.getCount();
			biggest = Math.max(biggest, s.getCount());
		}
		int given = rest.stream().mapToInt(ItemStack::getCount).sum();
		check(helper, biggest <= limit, "a stack of %s inside a carried box is above the Dwindling limit %s", biggest, limit);
		check(helper, inside + given == 128, "shrinking the box's stacks lost or made items: 128 became %s inside and %s out", inside, given);
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

	/**
	 * A death at home with the Overworld set and the Everywhere set full still costs a curse: from the set beyond the
	 * wound. With {@code curse_overflow} 0 it costs none (no ordinary card is dealt) and the Starved take levels instead.
	 */
	@GameTest
	public void fullSetsSpillOver(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-full-sets");
		PlayerData d = DataStore.get(p.getUUID());
		java.util.Map<String, Integer> before = new java.util.HashMap<>(d.curses);
		int overflow = Config.CURSE_OVERFLOW;
		try {
			for (Penalty pen : Penalty.values()) {
				if (!pen.abyssal && (pen.group == Penalty.Group.OVERWORLD || pen.group == Penalty.Group.EVERYWHERE)) {
					d.addStacks(pen, pen.max);
				}
			}
			List<Penalty> pool = Penalty.pool(d, true);
			check(helper, !pool.isEmpty(), "with the Overworld and Everywhere sets full, a death at home draws from nothing");
			for (Penalty pen : pool) {
				check(helper, pen.group == Penalty.Group.BEYOND, "a full home spilled onto %s, not the set beyond", pen.id);
			}
			unowe(d);
			PenaltyService.owe(d, true);
			check(helper, !d.pendingOffer.isEmpty(), "a death at home with full sets dealt no cards");
			for (String id : d.pendingOffer) {
				Penalty pen = Penalty.byId(id);
				check(helper, pen != null && (pen.group == Penalty.Group.BEYOND || pen.extreme), "a full home dealt %s", id);
			}
			check(helper, !d.xpForfeit, "a death that dealt cards took the levels too");

			unowe(d);
			Config.CURSE_OVERFLOW = 0;
			check(helper, Penalty.pool(d, true).isEmpty(), "with curse_overflow 0, full sets still drew from the set beyond");
			PenaltyService.owe(d, true);
			for (String id : d.pendingOffer) {
				check(helper, Penalty.byId(id) != null && Penalty.byId(id).extreme, "with curse_overflow 0, a full home dealt %s", id);
			}
			check(helper, !d.pendingOffer.isEmpty() || d.xpForfeit, "a death that could lay no curse neither dealt a card nor took the levels");
		} finally {
			Config.CURSE_OVERFLOW = overflow;
			d.curses.clear();
			d.curses.putAll(before);
			unowe(d);
			d.xpForfeit = false;
		}
		helper.succeed();
	}

	/** Clears what a player owes and the offer in front of them. */
	private static void unowe(PlayerData d) {
		d.owed.clear();
		d.pendingOffer.clear();
		d.pendingMult.clear();
		d.pendingPenalties = 0;
	}
}
