package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.function.DoubleSupplier;

import com.extremedimensions.binding.Charms;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.CurseLog;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.tetherite.Tetherite;
import com.extremedimensions.tetherite.TetheriteBinding;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * 3.26: Tetherite asks more. The ore needs a netherite pick and a blast furnace; the upgrade at the smithing table is a
 * binding that may fail (its chance made of the piece's enchantments, luck, the Seam, a cord in hand and old scars); the
 * gear is heavy, hungry and calls the Lurkers. And every curse gained is written down with where it came from.
 */
public class TetheriteBindingTests {
	private static Holder<Enchantment> ench(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** Only a netherite pick (or better) brings the ore out, and only a blast furnace gives up its scrap. */
	@GameTest
	public void theOreWantsNetheriteAndABlastFurnace(GameTestHelper helper) {
		var ore = com.extremedimensions.registry.ModBlocks.TETHERITE_ORE.defaultBlockState();
		check(helper, !new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(ore), "a diamond pick brings the ore out");
		check(helper, new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(ore), "a netherite pick does not bring the ore out");
		check(helper, new ItemStack(ModItems.TETHERITE_PICKAXE).isCorrectToolForDrops(ore), "a Tetherite pick does not bring the ore out");
		boolean smelting = false, blasting = false;
		for (var r : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			String id = r.id().identifier().toString();
			smelting |= id.equals("extremedimensions:tetherite_scrap");
			blasting |= id.equals("extremedimensions:tetherite_scrap_from_blasting");
		}
		check(helper, !smelting, "a plain furnace still smelts the ore");
		check(helper, blasting, "a blast furnace no longer gives up the scrap");
		helper.succeed();
	}

	/** The chance: its base, each enchantment's share, a curse's cost, scars, a cord in hand, and the cap. */
	@GameTest
	public void theBindingsChanceAddsUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "bind-odds");
		double max = Config.TETHERITE_BIND_MAX;
		try {
			ItemStack piece = new ItemStack(Items.NETHERITE_CHESTPLATE);
			near(helper, TetheriteBinding.odds(p, piece).chance(), Config.TETHERITE_BIND_CHANCE, "a plain piece");
			piece.enchant(ench(level, Enchantments.UNBREAKING), 3);
			piece.enchant(ench(level, Enchantments.MENDING), 1);
			double enchanted = Config.TETHERITE_BIND_CHANCE + 3 * Config.TETHERITE_BIND_UNBREAKING + Config.TETHERITE_BIND_MENDING;
			near(helper, TetheriteBinding.odds(p, piece).chance(), enchanted, "Unbreaking III and Mending");
			piece.enchant(ench(level, Enchantments.VANISHING_CURSE), 1);
			near(helper, TetheriteBinding.odds(p, piece).chance(), enchanted - Config.TETHERITE_BIND_CURSE, "and a curse");
			TetheriteBinding.setScars(piece, 2);
			check(helper, TetheriteBinding.scars(piece) == 2, "the piece keeps %s scars, not 2", TetheriteBinding.scars(piece));
			double scarred = enchanted - Config.TETHERITE_BIND_CURSE + 2 * Config.TETHERITE_BIND_SCAR;
			near(helper, TetheriteBinding.odds(p, piece).chance(), scarred, "and two scars");
			p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.TETHER_CORD));
			TetheriteBinding.Odds odds = TetheriteBinding.odds(p, piece);
			near(helper, odds.chance(), Math.min(Config.TETHERITE_BIND_MAX, scarred + Config.TETHERITE_BIND_CORD), "and a cord in hand");
			check(helper, odds.parts().stream().anyMatch(x -> x.kind().equals("cord")), "the cord is not among the shares");
			Config.TETHERITE_BIND_MAX = 0.6;
			odds = TetheriteBinding.odds(p, piece);
			near(helper, odds.chance(), 0.6, "held to the cap");
			check(helper, odds.capped(), "the chance held to its cap does not say so");
			TetheriteBinding.setScars(piece, 0);
			check(helper, TetheriteBinding.scars(piece) == 0, "the scars did not come off");
		} finally {
			Config.TETHERITE_BIND_MAX = max;
			p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		helper.succeed();
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		check(helper, Math.abs(actual - expected) < 1e-6, "%s: the chance is %s, not %s", what, actual, expected);
	}

	/** A smithing table's menu with a Tetherite upgrade laid in: the template, a netherite chestplate and two ingots. */
	private static SmithingMenu table(GameTestHelper helper, ServerPlayer p) {
		helper.setBlock(new net.minecraft.core.BlockPos(1, 0, 1), Blocks.SMITHING_TABLE);
		SmithingMenu menu = new SmithingMenu(7, p.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1))));
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(ModItems.TETHERITE_UPGRADE_SMITHING_TEMPLATE));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.NETHERITE_CHESTPLATE));
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(ModItems.TETHERITE_INGOT, 2));
		menu.createResult();
		return menu;
	}

	/** Takes the result as a click would: off the slot, then the take. */
	private static ItemStack take(SmithingMenu menu, ServerPlayer p) {
		Slot result = menu.getSlot(SmithingMenu.RESULT_SLOT);
		ItemStack taken = result.tryRemove(1, Integer.MAX_VALUE, p).orElse(ItemStack.EMPTY);
		result.onTake(p, taken);
		return taken;
	}

	/**
	 * A failed binding takes nothing from the table but an ingot: the template stays, the piece stays, worn and scarred,
	 * and nothing comes out. The next try holds: the Tetherite piece comes out without the scar, and the rest is spent.
	 */
	@GameTest
	public void aFailedBindingShattersTheIngotAndScarsThePiece(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "bind-fail");
		DoubleSupplier roll = TetheriteBinding.roll;
		int where = Config.TETHERITE_BIND_WHERE;
		try {
			Config.TETHERITE_BIND_WHERE = 0;
			SmithingMenu menu = table(helper, p);
			check(helper, TetheriteBinding.pending(menu), "the table offers no binding (result: %s)", menu.getSlot(SmithingMenu.RESULT_SLOT).getItem());
			TetheriteBinding.roll = () -> 0.999;
			ItemStack taken = take(menu, p);
			check(helper, taken.isEmpty(), "a failed binding still gave %s", taken);
			ItemStack piece = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
			check(helper, piece.is(Items.NETHERITE_CHESTPLATE), "the piece did not stay on the table (%s)", piece);
			check(helper, piece.getDamageValue() >= (int) (piece.getMaxDamage() * Config.TETHERITE_BIND_WEAR) - 1, "the piece was not worn (%s of %s)",
					piece.getDamageValue(), piece.getMaxDamage());
			check(helper, TetheriteBinding.scars(piece) == 1, "the piece has %s scars, not one", TetheriteBinding.scars(piece));
			check(helper, menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == 1, "the failure took %s ingots, not one",
					2 - menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount());
			check(helper, !menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty(), "the template was lost to a failure");
			// The scar makes the next try likelier, and that one holds
			check(helper, TetheriteBinding.pending(menu), "after a failure the table offers no binding again");
			TetheriteBinding.roll = () -> 0.0;
			taken = take(menu, p);
			check(helper, Tetherite.isGear(taken), "the binding that held gave %s", taken);
			check(helper, TetheriteBinding.scars(taken) == 0, "the scars went on into the Tetherite");
			check(helper, menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty(), "the netherite stayed on the table");
			check(helper, menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "the second ingot was not spent");
		} finally {
			TetheriteBinding.roll = roll;
			Config.TETHERITE_BIND_WHERE = where;
			p.getInventory().clearContent();
		}
		helper.succeed();
	}

	/** Shift-clicking a binding rolls it first: a failure moves nothing into the pack; a success moves the piece. */
	@GameTest
	public void aShiftClickedBindingRollsBeforeItMoves(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "bind-shift");
		DoubleSupplier roll = TetheriteBinding.roll;
		int where = Config.TETHERITE_BIND_WHERE;
		try {
			Config.TETHERITE_BIND_WHERE = 0;
			p.getInventory().clearContent();
			SmithingMenu menu = table(helper, p);
			TetheriteBinding.roll = () -> 0.999;
			ItemStack moved = menu.quickMoveStack(p, SmithingMenu.RESULT_SLOT);
			check(helper, moved.isEmpty(), "a failed binding moved %s", moved);
			check(helper, !p.getInventory().hasAnyMatching(Tetherite::isGear), "a failed binding still put Tetherite in the pack");
			check(helper, TetheriteBinding.scars(menu.getSlot(SmithingMenu.BASE_SLOT).getItem()) == 1, "a failed shift-click left no scar");
			TetheriteBinding.roll = () -> 0.0;
			menu.quickMoveStack(p, SmithingMenu.RESULT_SLOT);
			check(helper, p.getInventory().hasAnyMatching(Tetherite::isGear), "a binding that held moved nothing into the pack");
		} finally {
			TetheriteBinding.roll = roll;
			Config.TETHERITE_BIND_WHERE = where;
			p.getInventory().clearContent();
		}
		helper.succeed();
	}

	/**
	 * Where the metal binds (3.26.1): set to the Seam, a table in the Overworld shows the result but will not let it be
	 * taken, by hand or by shift-click, and nothing on the table is spent; set to anywhere, it is taken.
	 */
	@GameTest
	public void theMetalBindsOnlyWhereItIsSetTo(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "bind-where");
		DoubleSupplier roll = TetheriteBinding.roll;
		int where = Config.TETHERITE_BIND_WHERE;
		try {
			p.getInventory().clearContent();
			TetheriteBinding.roll = () -> 0.0;
			SmithingMenu menu = table(helper, p);
			for (int setting : new int[] {1, 2}) {
				Config.TETHERITE_BIND_WHERE = setting;
				check(helper, TetheriteBinding.needed(p) == setting, "in the Overworld a binding set to %s needs %s", setting, TetheriteBinding.needed(p));
				check(helper, !menu.getSlot(SmithingMenu.RESULT_SLOT).mayPickup(p), "set to %s, the result could be taken in the Overworld", setting);
				ItemStack moved = menu.quickMoveStack(p, SmithingMenu.RESULT_SLOT);
				check(helper, moved.isEmpty() && !p.getInventory().hasAnyMatching(Tetherite::isGear), "set to %s, a shift-click took the result", setting);
				check(helper, menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == 2 && !menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty(),
						"set to %s, a refused binding spent something", setting);
			}
			Config.TETHERITE_BIND_WHERE = 0;
			check(helper, TetheriteBinding.needed(p) == 0 && menu.getSlot(SmithingMenu.RESULT_SLOT).mayPickup(p), "set to anywhere, the result cannot be taken");
			ItemStack taken = take(menu, p);
			check(helper, Tetherite.isGear(taken), "set to anywhere, the binding gave %s", taken);
		} finally {
			TetheriteBinding.roll = roll;
			Config.TETHERITE_BIND_WHERE = where;
			p.getInventory().clearContent();
		}
		helper.succeed();
	}

	/** Tetherite's price: slower for each piece worn, hungrier for each piece worn or held, and the Lurkers come more often. */
	@GameTest
	public void tetheriteIsHeavyHungryAndCallsTheTribe(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "tetherite-price");
		PlayerData d = DataStore.get(p.getUUID());
		try {
			float plain = Charms.exhaustion(p);
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.TETHERITE_CHESTPLATE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.TETHERITE_LEGGINGS));
			p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.TETHERITE_SWORD));
			check(helper, Tetherite.worn(p) == 2 && Tetherite.borne(p) == 3, "worn %s, borne %s (not 2 and 3)", Tetherite.worn(p), Tetherite.borne(p));
			near(helper, Tetherite.weight(p), -2 * Config.TETHERITE_WEIGHT, "the weight");
			near(helper, Tetherite.lure(p), 1 + 3 * Config.TETHERITE_LURE, "the call to the Lurkers");
			near(helper, Charms.exhaustion(p) - plain, (float) (3 * Config.TETHERITE_HUNGER), "the hunger");
			d.tetheriteWeight = Double.NaN;
			Tetherite.weigh(p, d);
			AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
			var mod = speed.getModifier(Identifier.fromNamespaceAndPath("extremedimensions", "tetherite_weight"));
			check(helper, mod != null && Math.abs(mod.amount() + 2 * Config.TETHERITE_WEIGHT) < 1e-6, "the speed carries no Tetherite weight (%s)", mod);
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			Tetherite.weigh(p, d);
			check(helper, speed.getModifier(Identifier.fromNamespaceAndPath("extremedimensions", "tetherite_weight")) == null,
					"the weight stayed when the armor came off");
		} finally {
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			d.tetheriteWeight = Double.NaN;
			Tetherite.weigh(p, d);
		}
		helper.succeed();
	}

	/** 3.27.4: with its drawbacks switched off, Tetherite weighs nothing, feeds on no one and calls no Lurker; on again, it does. */
	@GameTest
	public void tetheritesDrawbacksCanBeSwitchedOff(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "tetherite-free");
		PlayerData d = DataStore.get(p.getUUID());
		int was = Config.TETHERITE_DRAWBACKS;
		Identifier weight = Identifier.fromNamespaceAndPath("extremedimensions", "tetherite_weight");
		try {
			float plain = Charms.exhaustion(p);
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.TETHERITE_CHESTPLATE));
			p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.TETHERITE_SWORD));
			Config.TETHERITE_DRAWBACKS = 0;
			near(helper, Tetherite.weight(p), 0, "the weight with the drawbacks off");
			near(helper, Tetherite.lure(p), 1, "the call to the Lurkers with the drawbacks off");
			near(helper, Charms.exhaustion(p) - plain, 0, "the hunger with the drawbacks off");
			d.tetheriteWeight = Double.NaN;
			Tetherite.weigh(p, d);
			check(helper, p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(weight) == null, "the speed carries a weight with the drawbacks off");
			Config.TETHERITE_DRAWBACKS = 1;
			near(helper, Tetherite.weight(p), -Config.TETHERITE_WEIGHT, "the weight with the drawbacks on again");
			near(helper, Tetherite.lure(p), 1 + 2 * Config.TETHERITE_LURE, "the call with the drawbacks on again");
		} finally {
			Config.TETHERITE_DRAWBACKS = was;
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			d.tetheriteWeight = Double.NaN;
			Tetherite.weigh(p, d);
		}
		helper.succeed();
	}

	/**
	 * Every curse gained is written down newest first, with where it came from: one laid without a choice (a Lurker's)
	 * waits to be told as a toast, one chosen on a card does not; and each is new on the Curses screen until it is opened.
	 */
	@GameTest
	public void aCurseIsWrittenDownWithWhereItCameFrom(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "curse-log");
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.curses.clear();
			d.curseLog.clear();
			d.curseToasts.clear();
			d.cursesSeenAt = 0;
			Penalty pen = PenaltyService.curse(p, true, 0, CurseLog.Source.LURKER);
			check(helper, pen != null, "no curse was laid");
			var log = CurseLog.entries(d);
			check(helper, !log.isEmpty() && log.get(0).id().equals(pen.id) && log.get(0).source() == CurseLog.Source.LURKER,
					"the Lurker's curse was not written down first, as the Lurker's (%s)", d.curseLog);
			check(helper, d.curseToasts.size() == 1, "%s toasts wait for a Lurker's curse, not one", d.curseToasts.size());
			CurseLog.flush(p, d);
			check(helper, d.curseToasts.isEmpty(), "the toast was not sent");
			Penalty other = pen == Penalty.values()[0] ? Penalty.values()[1] : Penalty.values()[0];
			CurseLog.add(d, other, 1, CurseLog.Source.CARD);
			check(helper, d.curseToasts.isEmpty(), "a curse chosen on a card is told as a toast too");
			check(helper, CurseLog.entries(d).get(0).source() == CurseLog.Source.CARD, "the card's curse was not written down first");
			check(helper, CurseLog.data(d).endsWith(",1"), "a fresh curse is not new on the Curses screen (%s)", CurseLog.data(d));
			CurseLog.seen(d);
			check(helper, !CurseLog.data(d).contains(",1;") && !CurseLog.data(d).endsWith(",1"), "after the screen opened a curse is still new (%s)",
					CurseLog.data(d));
			for (int i = 0; i < CurseLog.KEEP + 4; i++) {
				CurseLog.add(d, Penalty.values()[i % 6], 1, i % 2 == 0 ? CurseLog.Source.SWARM : CurseLog.Source.BARTER);
			}
			check(helper, d.curseLog.size() <= CurseLog.KEEP, "the log keeps %s entries, past %s", d.curseLog.size(), CurseLog.KEEP);
		} finally {
			d.curses.clear();
			d.curseLog.clear();
			d.curseToasts.clear();
		}
		helper.succeed();
	}
}
