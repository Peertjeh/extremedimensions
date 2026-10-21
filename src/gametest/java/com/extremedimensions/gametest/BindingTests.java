package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.binding.Homebound;
import com.extremedimensions.binding.Veilsight;
import com.extremedimensions.binding.WoundLens;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import org.apache.commons.lang3.mutable.MutableFloat;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * The Art of Binding (2.35): the five enchantments, Tethers that take them, and the Wound Lens.
 */
public class BindingTests {
	/** Woundbane adds its damage to the creatures of the wound only, and less to the Elders (whose tag it reads). */
	@GameTest
	public void woundbaneBitesTheWound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-woundbane");
		Holder<Enchantment> woundbane = ench(level, ModEnchantments.WOUNDBANE);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(woundbane, 5);
		Lurker lurker = ModEntities.LURKER.create(level, EntitySpawnReason.COMMAND);
		Entity zombie = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "zombie")).create(level, EntitySpawnReason.COMMAND);
		DamageSource hit = level.damageSources().playerAttack(p);
		MutableFloat onLurker = new MutableFloat(6.0f), onZombie = new MutableFloat(6.0f);
		woundbane.value().modifyDamage(level, 5, sword, lurker, hit, onLurker);
		woundbane.value().modifyDamage(level, 5, sword, zombie, hit, onZombie);
		float vsLurker = onLurker.floatValue(), vsZombie = onZombie.floatValue();
		check(helper, Math.abs(vsLurker - 18.5f) < 0.01f, "Woundbane V against a Lurker: expected 18.5, got %s", vsLurker);
		check(helper, Math.abs(vsZombie - 6.0f) < 0.01f, "Woundbane V against a zombie: expected 6, got %s", vsZombie);
		// Against an Elder only a little more than Sharpness V gives (+3.6 against +3): within a tenth of its time to kill
		Entity elder = ModEntities.MATRIARCH.create(level, EntitySpawnReason.COMMAND);
		MutableFloat onElder = new MutableFloat(6.0f);
		woundbane.value().modifyDamage(level, 5, sword, elder, hit, onElder);
		check(helper, Math.abs(onElder.floatValue() - 9.6f) < 0.01f, "Woundbane V against an Elder: expected 9.6, got %s", onElder.floatValue());
		elder.discard();
		TagKey<EntityType<?>> elders = TagKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("wound_elders"));
		TagKey<EntityType<?>> born = TagKey.create(Registries.ENTITY_TYPE, ExtremeDimensions.id("wound_born"));
		for (EntityType<?> t : List.of(ModEntities.MATRIARCH, ModEntities.FERRYMAN, ModEntities.WEAVER, ModEntities.STARVED_ONE)) {
			check(helper, tagged(t, elders) && !tagged(t, born), "%s is not counted among the Elders", t);
		}
		for (EntityType<?> t : List.of(ModEntities.LURKER, ModEntities.HOLLOWLING, ModEntities.KEEPER, ModEntities.THINNED, ModEntities.VEIL_RAY)) {
			check(helper, tagged(t, born) && !tagged(t, elders), "%s is not counted among the wound-born", t);
		}
		lurker.discard();
		zombie.discard();
		helper.succeed();
	}

	/**
	 * Which enchantments go together and where they come from: Woundbane is a damage enchantment (not with Sharpness),
	 * Thrift and Silkbound exclude each other, Homebound and the Curse of Vanishing too; the first three come from the
	 * table, Veilsight and Homebound only as treasure. A Tether takes Thrift and Silkbound, one at a time, never a stack.
	 */
	@GameTest
	public void theBindingsKeepTheirPlaces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> woundbane = ench(level, ModEnchantments.WOUNDBANE), thrift = ench(level, ModEnchantments.THRIFT),
				silkbound = ench(level, ModEnchantments.SILKBOUND), veilsight = ench(level, ModEnchantments.VEILSIGHT),
				homebound = ench(level, ModEnchantments.HOMEBOUND);
		HolderLookup.RegistryLookup<Enchantment> all = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		check(helper, !Enchantment.areCompatible(woundbane, all.getOrThrow(Enchantments.SHARPNESS)), "Woundbane goes with Sharpness");
		check(helper, !Enchantment.areCompatible(woundbane, all.getOrThrow(Enchantments.SMITE)), "Woundbane goes with Smite");
		check(helper, !Enchantment.areCompatible(thrift, silkbound), "Thrift goes with Silkbound");
		check(helper, !Enchantment.areCompatible(homebound, all.getOrThrow(Enchantments.VANISHING_CURSE)), "Homebound goes with the Curse of Vanishing");
		check(helper, Enchantment.areCompatible(veilsight, all.getOrThrow(Enchantments.PROTECTION)), "Veilsight does not go with Protection");
		for (Holder<Enchantment> h : List.of(woundbane, thrift, silkbound)) {
			check(helper, h.is(EnchantmentTags.NON_TREASURE) && !h.is(EnchantmentTags.TREASURE), "%s does not come from the table", h.getRegisteredName());
		}
		for (Holder<Enchantment> h : List.of(veilsight, homebound)) {
			check(helper, h.is(EnchantmentTags.TREASURE) && !h.is(EnchantmentTags.NON_TREASURE), "%s is not treasure", h.getRegisteredName());
		}
		for (ItemStack tether : List.of(new ItemStack(ModItems.TETHER), new ItemStack(ModItems.SOUL_WOVEN_TETHER), new ItemStack(ModItems.SEAM_WOVEN_TETHER))) {
			check(helper, tether.isEnchantable(), "a single %s cannot be enchanted", tether.getItem());
			check(helper, thrift.value().canEnchant(tether) && silkbound.value().canEnchant(tether), "%s takes neither Thrift nor Silkbound", tether.getItem());
		}
		check(helper, !new ItemStack(ModItems.TETHER, 2).isEnchantable(), "a stack of Tethers can be enchanted");
		check(helper, !thrift.value().canEnchant(new ItemStack(Items.IRON_SWORD)), "a sword takes Thrift");
		check(helper, woundbane.value().canEnchant(new ItemStack(Items.MACE)) && woundbane.value().canEnchant(new ItemStack(Items.IRON_AXE)),
				"Woundbane does not go on a mace or an axe");
		check(helper, !woundbane.value().canEnchant(new ItemStack(Items.IRON_PICKAXE)), "Woundbane goes on a pickaxe");
		check(helper, veilsight.value().canEnchant(new ItemStack(Items.IRON_HELMET)) && !veilsight.value().canEnchant(new ItemStack(Items.IRON_BOOTS)),
				"Veilsight is not a helmet's");
		check(helper, homebound.value().canEnchant(new ItemStack(Items.DIAMOND_CHESTPLATE)), "Homebound does not go on a chestplate");
		helper.succeed();
	}

	/**
	 * A Tether keeps its enchantments while it is tied into an anchor and comes back with them; Thrift eases the drain
	 * by its share per level, and nothing that eases it goes below the floor.
	 */
	@GameTest
	public void aTetherKeepsItsCharms(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-charms");
		PlayerData d = DataStore.get(p.getUUID());
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		String key = Anchor.key(at.getX(), at.getY(), at.getZ());
		DataStore.anchors().put(key, new Anchor(at.getX(), at.getY(), at.getZ(), Dims.NETHER));
		double drain = Config.ENCH_THRIFT_DRAIN;
		int tier = d.tetherTier, scars = d.scars;
		try {
			ItemStack tether = new ItemStack(ModItems.TETHER);
			tether.enchant(ench(level, ModEnchantments.THRIFT), 3);
			TetherService.tie(p, d, at, tether);
			check(helper, key.equals(d.tetherAnchor), "the Tether was not tied");
			check(helper, d.charm(ModEnchantments.THRIFT) == 3, "the tied Tether forgot its Thrift (%s)", d.tetherCharms);

			d.tetherTier = 0;
			d.scars = 0;
			double thrifty = d.drainMultiplier();
			java.util.Map<String, Integer> kept = new java.util.HashMap<>(d.tetherCharms);
			d.tetherCharms.clear();
			double plain = d.drainMultiplier();
			check(helper, Math.abs(thrifty - plain * (1 - 3 * Config.ENCH_THRIFT_DRAIN)) < 1e-6, "Thrift III drains %s, plain %s", thrifty, plain);
			d.tetherCharms.putAll(kept);
			// Everything that eases the drain together stops at the floor
			Config.ENCH_THRIFT_DRAIN = 0.2;
			d.tetherTier = 2;
			d.scars = 5;
			double floored = d.drainMultiplier();
			check(helper, Math.abs(floored - Config.BASE_DRAIN * Config.ENCH_DRAIN_FLOOR) < 1e-6, "the eased drain went to %s, below the floor", floored);
			Config.ENCH_THRIFT_DRAIN = drain;
			// ... and so do a relic and a mended world on top: the woven Tether, five scars and Thrift III with the
			// Ember Heart in a mended world is still only the floor
			d.tetherTier = 2;
			d.scars = 5;
			double withAll = d.drainMultiplier(Config.RELIC_EMBER_HEART_NETHER * Config.MENDED_DRAIN);
			check(helper, withAll >= Config.BASE_DRAIN * Config.ENCH_DRAIN_FLOOR - 1e-9, "a relic and a mended world took the drain to %s, below the floor",
					withAll);
			d.tetherTier = 0;
			d.scars = 0;
			double eased = d.drainMultiplier(0.8);
			check(helper, Math.abs(eased - thrifty * 0.8) < 1e-6, "above the floor, an easing of 0.8 drains %s, not %s", eased, thrifty * 0.8);

			TetherService.untie(p, d);
			check(helper, d.tetherAnchor == null && d.charm(ModEnchantments.THRIFT) == 0, "untied, the player still has Thrift");
			check(helper, count(p, ModItems.TETHER, ModEnchantments.THRIFT, 3) == 1, "the Tether came back without its Thrift");
		} finally {
			Config.ENCH_THRIFT_DRAIN = drain;
			d.tetherTier = tier;
			d.scars = scars;
			TetherService.clear(p, d, false);
			DataStore.anchors().remove(key);
		}
		helper.succeed();
	}

	/** A Collapse while bound to a Tether with Thrift costs a curse more than one without. */
	@GameTest
	public void thriftTakesItsDueAtACollapse(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-thrift-collapse");
		PlayerData d = DataStore.get(p.getUUID());
		String relic = d.relic;
		int pending = d.pendingPenalties, collapses = d.collapses;
		List<String> owed = new ArrayList<>(d.owed);
		try {
			d.relic = null;
			bound(d, 0);
			ExpeditionService.tick(p, d, Dims.NETHER);
			int plain = d.pendingPenalties - pending;
			check(helper, d.collapsing && plain == 1, "a plain Collapse owed %s curses (collapsing %s)", plain, d.collapsing);
			reset(d, pending, owed);

			bound(d, 1);
			ExpeditionService.tick(p, d, Dims.NETHER);
			int thrifty = d.pendingPenalties - pending;
			check(helper, d.collapsing && thrifty == 1 + Config.ENCH_THRIFT_COLLAPSE, "a Collapse with Thrift owed %s curses, not %s", thrifty,
					1 + Config.ENCH_THRIFT_COLLAPSE);
			check(helper, d.tetherAnchor == null && d.tetherCharms.isEmpty(), "the Tether outlived the Collapse");
		} finally {
			reset(d, pending, owed);
			d.inRun = false;
			d.relic = relic;
			d.collapses = collapses;
			d.tetherAnchor = null;
			d.tetherCharms.clear();
		}
		helper.succeed();
	}

	/** The anvil enchants one Tether with a book, never a stack of them at once (it still renames a stack). */
	@GameTest
	public void anAnvilTakesOneTether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-anvil");
		net.minecraft.world.inventory.AnvilMenu menu = new net.minecraft.world.inventory.AnvilMenu(0, p.getInventory(),
				net.minecraft.world.inventory.ContainerLevelAccess.NULL);
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.enchant(ench(level, ModEnchantments.THRIFT), 3);
		menu.getSlot(0).set(new ItemStack(ModItems.TETHER));
		menu.getSlot(1).set(book.copy());
		menu.createResult();
		check(helper, ModEnchantments.level(level.registryAccess(), menu.getSlot(2).getItem(), ModEnchantments.THRIFT) == 3,
				"the anvil did not put Thrift III on one Tether (it gave %s)", menu.getSlot(2).getItem());
		menu.getSlot(0).set(new ItemStack(ModItems.TETHER, 16));
		menu.getSlot(1).set(book.copy());
		menu.createResult();
		check(helper, menu.getSlot(2).getItem().isEmpty(), "the anvil enchanted a stack of Tethers at once: %s", menu.getSlot(2).getItem());
		menu.getSlot(0).set(ItemStack.EMPTY);
		menu.getSlot(1).set(ItemStack.EMPTY);
		helper.succeed();
	}

	/** Silkbound counts on the bound Tether and on one carried; Veilsight is read from the helmet. */
	@GameTest
	public void silkAndSightAreRead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-silk-sight");
		PlayerData d = DataStore.get(p.getUUID());
		check(helper, TetherService.charm(p, d, ModEnchantments.SILKBOUND) == 0, "a player with no Tether has Silkbound");
		ItemStack carried = new ItemStack(ModItems.SOUL_WOVEN_TETHER);
		carried.enchant(ench(level, ModEnchantments.SILKBOUND), 2);
		p.getInventory().add(carried);
		check(helper, TetherService.charm(p, d, ModEnchantments.SILKBOUND) == 2, "a carried Tether's Silkbound II reads %s",
				TetherService.charm(p, d, ModEnchantments.SILKBOUND));
		// Bound to a Thrift Tether, the Silkbound one in the bag counts for nothing: the two are never had at once
		d.tetherAnchor = "0,0,0";
		d.tetherCharms = new java.util.HashMap<>(java.util.Map.of(ModEnchantments.id(ModEnchantments.THRIFT), 3));
		try {
			check(helper, TetherService.charm(p, d, ModEnchantments.SILKBOUND) == 0, "bound to a Thrift Tether, a carried one still gave Silkbound");
		} finally {
			d.tetherAnchor = null;
			d.tetherCharms.clear();
		}

		check(helper, !Veilsight.has(p) && Veilsight.noticed(p) == 1.0, "a bare head has Veilsight");
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		helmet.enchant(ench(level, ModEnchantments.VEILSIGHT), 1);
		p.setItemSlot(EquipmentSlot.HEAD, helmet);
		check(helper, Veilsight.has(p) && Veilsight.noticed(p) == Config.ENCH_VEILSIGHT_NOTICE, "the helmet's Veilsight was not read");
		// Veins within reach shimmer for the one who sees them
		BlockPos o = helper.absolutePos(new BlockPos(2, 1, 2));
		p.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
		level.setBlock(o.offset(2, 0, 0), ModBlocks.SEAM_VEIN.defaultBlockState(), 2);
		level.setBlock(o.offset(0, 1, -2), ModBlocks.SEAM_VEIN.defaultBlockState(), 2);
		int seen = Veilsight.shimmer(p);
		check(helper, seen >= 2, "only %s of the two veins near shimmered", seen);
		level.setBlock(o.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(o.offset(0, 1, -2), Blocks.AIR.defaultBlockState(), 2);
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		helper.succeed();
	}

	/**
	 * Homebound gear dropped at a death beyond the wound is taken up, waits in the player's data (through a restart) and
	 * comes back at the respawn; at home it stays where it fell, as does gear without it. Worn, it drains a little.
	 */
	@GameTest
	public void homeboundComesHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-homebound");
		PlayerData d = DataStore.get(p.getUUID());
		BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(ench(level, ModEnchantments.HOMEBOUND), 1);
		ItemEntity kept = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), sword.copy());
		ItemEntity plain = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.COBBLESTONE, 3));
		level.addFreshEntity(kept);
		level.addFreshEntity(plain);
		try {
			check(helper, Homebound.gather(p, Dims.OVERWORLD) == 0 && kept.isAlive(), "Homebound gear was taken up at home");
			check(helper, Homebound.gather(p, Dims.NETHER) == 1, "Homebound gear was not taken up beyond the wound");
			check(helper, !kept.isAlive() && plain.isAlive(), "the wrong things were taken up (Homebound left %s, plain left %s)", kept.isAlive(), plain.isAlive());
			check(helper, Homebound.waitingCount(p.getUUID()) == 1, "%s stacks wait, not one", Homebound.waitingCount(p.getUUID()));
			check(helper, DataStore.reread(d).homebound.size() == 1, "what waits did not survive a restart");
			Homebound.restore(p);
			check(helper, Homebound.waitingCount(p.getUUID()) == 0, "something still waits after the respawn");
			check(helper, count(p, Items.IRON_SWORD, ModEnchantments.HOMEBOUND, 1) == 1, "the sword did not come home with its Homebound");

			// what came home lands in the hand, and held it counts: put it away first
			p.getInventory().clearContent();
			check(helper, Homebound.pieces(p) == 0, "no Homebound carried, yet %s pieces count", Homebound.pieces(p));
			ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
			chest.enchant(ench(level, ModEnchantments.HOMEBOUND), 1);
			p.setItemSlot(EquipmentSlot.CHEST, chest);
			check(helper, Homebound.pieces(p) == 1, "one Homebound piece worn counts as %s", Homebound.pieces(p));
			// one in the bag would come home too, so it drains too
			p.getInventory().add(sword.copy());
			check(helper, Homebound.pieces(p) == 2, "a Homebound sword in the bag did not count (%s pieces)", Homebound.pieces(p));
			d.homeboundCounted = Long.MIN_VALUE / 2;
			double carried = Homebound.drainFactor(p);
			check(helper, Math.abs(carried - (1 + 2 * Config.ENCH_HOMEBOUND_DRAIN)) < 1e-9, "two Homebound pieces carried drain %s", carried);
		} finally {
			plain.discard();
			kept.discard();
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			p.getInventory().clearContent();
			d.homebound.clear();
		}
		helper.succeed();
	}

	/** The Wound Lens reads what a Lurker is about, and whose charge an anchor keeps. */
	@GameTest
	public void theLensReads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-lens");
		Lurker lurker = ModEntities.LURKER.create(level, EntitySpawnReason.COMMAND);
		String[] doing = {"watching", "stalking", "taking", "drinking", "fleeing"};
		int[] poses = {Lurker.POSE_WATCH, Lurker.POSE_STALK, Lurker.POSE_TAKE, Lurker.POSE_DRAIN, Lurker.POSE_RUN};
		for (int i = 0; i < poses.length; i++) {
			lurker.setLurkerPose(poses[i]);
			String k = key(WoundLens.lurker(lurker));
			check(helper, ("gui.extremedimensions.lens.lurker." + doing[i]).equals(k), "a Lurker %s read as %s", doing[i], k);
		}
		lurker.discard();

		Anchor a = new Anchor(0, 0, 0, Dims.NETHER);
		check(helper, WoundLens.anchor(p, a).getString().contains("gui.extremedimensions.lens.anchor.empty")
				|| WoundLens.anchor(p, a).getSiblings().size() >= 2, "an empty anchor read as something");
		a.add(p.getStringUUID(), 125);
		String read = WoundLens.anchor(p, a).getString();
		check(helper, read.contains(p.getName().getString()) && read.contains("2:05"), "the anchor read \"%s\", not the player's 2:05", read);
		check(helper, "gui.extremedimensions.lens.anchor.nether".equals(key(WoundLens.anchor(p, a))), "an ember anchor read as %s", key(WoundLens.anchor(p, a)));
		check(helper, "gui.extremedimensions.lens.anchor.end".equals(key(WoundLens.anchor(p, new Anchor(0, 0, 0, Dims.END)))), "a void anchor read as an ember one");
		// A cracked anchor's time, as its charge is: minutes and seconds
		a.unstable = 287;
		String breaks = null;
		for (net.minecraft.network.chat.Component c : WoundLens.anchor(p, a).getSiblings()) {
			if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith(".unstable") && t.getArgs().length == 1) {
				breaks = String.valueOf(t.getArgs()[0]);
			}
		}
		check(helper, "4:47".equals(breaks), "a cracked anchor read as breaking in %s, not 4:47", breaks);
		// Told something else on the action bar, the lens holds its readout a moment, so that it can be read
		check(helper, !WoundLens.hushed(p), "the lens holds its readout with nothing said");
		p.sendSystemMessage(net.minecraft.network.chat.Component.literal("test"), true);
		check(helper, WoundLens.hushed(p), "told something on the action bar, the lens did not hold its readout");
		WoundLens.forget(p.getUUID());
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static boolean tagged(EntityType<?> t, TagKey<EntityType<?>> tag) {
		return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(t).is(tag);
	}

	private static Holder<Enchantment> ench(ServerLevel level, net.minecraft.resources.ResourceKey<Enchantment> k) {
		return ModEnchantments.holder(level.registryAccess(), k).orElseThrow();
	}

	/** How many stacks of an item with an enchantment at a level the player carries. */
	private static int count(ServerPlayer p, net.minecraft.world.item.Item item, net.minecraft.resources.ResourceKey<Enchantment> k, int lvl) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item) && ModEnchantments.level(p.level().registryAccess(), s, k) == lvl) {
				n++;
			}
		}
		return n;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	/** On an expedition in the Nether with no charge left, bound to an iron Tether with Thrift at a level (0 for none). */
	private static void bound(PlayerData d, int thrift) {
		d.inRun = true;
		d.collapsing = false;
		d.dim = Dims.NETHER;
		d.linked.clear();
		d.tetherAnchor = "test-thrift-anchor";
		d.tetherTier = 0;
		d.tetherCharms.clear();
		if (thrift > 0) {
			d.tetherCharms.put(ModEnchantments.id(ModEnchantments.THRIFT), thrift);
		}
		d.heldSpent = false;
		d.heldTicks = 0;
		d.runMax = 100;
	}

	private static void reset(PlayerData d, int pending, List<String> owed) {
		d.collapsing = false;
		d.collapseOwed = false;
		d.heldTicks = 0;
		d.pendingPenalties = pending;
		d.owed.clear();
		d.owed.addAll(owed);
	}
}
