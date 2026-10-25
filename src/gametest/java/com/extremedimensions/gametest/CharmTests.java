package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.binding.Charms;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEffects;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;

import org.apache.commons.lang3.mutable.MutableFloat;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Seam's craft, grown (3.3): the fifteen enchantments, where they go, what they keep apart from, and what each does.
 */
public class CharmTests {
	private static final List<ResourceKey<Enchantment>> ALL = List.of(ModEnchantments.UNRAVELLING, ModEnchantments.REAPING,
			ModEnchantments.TETHERSHOT, ModEnchantments.VEINSEEKER, ModEnchantments.ANCHORED, ModEnchantments.WARDWEAVE,
			ModEnchantments.SATED, ModEnchantments.DRIFTFALL, ModEnchantments.HUSHED_STEPS, ModEnchantments.FORESIGHT,
			ModEnchantments.SOULWARD, ModEnchantments.KNITTING, ModEnchantments.STEADFAST, ModEnchantments.CURSE_OF_FRAYING,
			ModEnchantments.CURSE_OF_THE_STARVED);

	/**
	 * All fifteen are there; Knitting and the two curses only as treasure, the curses counted as curses; each goes on
	 * what it is for and not elsewhere, and keeps apart from what it would double.
	 */
	@GameTest
	public void theNewBindingsKeepTheirPlaces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderLookup.RegistryLookup<Enchantment> all = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (ResourceKey<Enchantment> k : ALL) {
			check(helper, all.get(k).isPresent(), "%s is not in the registry", k.identifier());
		}
		List<ResourceKey<Enchantment>> treasure = List.of(ModEnchantments.KNITTING, ModEnchantments.CURSE_OF_FRAYING, ModEnchantments.CURSE_OF_THE_STARVED);
		for (ResourceKey<Enchantment> k : ALL) {
			Holder<Enchantment> h = ench(level, k);
			boolean t = treasure.contains(k);
			check(helper, h.is(EnchantmentTags.TREASURE) == t && h.is(EnchantmentTags.NON_TREASURE) != t, "%s is %s treasure", k.identifier(), t ? "not" : "");
		}
		check(helper, ench(level, ModEnchantments.CURSE_OF_FRAYING).is(EnchantmentTags.CURSE)
				&& ench(level, ModEnchantments.CURSE_OF_THE_STARVED).is(EnchantmentTags.CURSE), "the two curses are not curses");
		check(helper, !ench(level, ModEnchantments.KNITTING).is(EnchantmentTags.CURSE), "Knitting is a curse");

		incompatible(helper, level, all, ModEnchantments.UNRAVELLING, Enchantments.FIRE_ASPECT);
		incompatible(helper, level, all, ModEnchantments.TETHERSHOT, Enchantments.PUNCH);
		incompatible(helper, level, all, ModEnchantments.KNITTING, Enchantments.MENDING);
		incompatible(helper, level, all, ModEnchantments.WARDWEAVE, Enchantments.PROTECTION);
		check(helper, !Enchantment.areCompatible(ench(level, ModEnchantments.FORESIGHT), ench(level, ModEnchantments.THRIFT)),
				"Foresight goes with Thrift on one Tether");

		goesOn(helper, level, ModEnchantments.UNRAVELLING, Items.IRON_SWORD, Items.IRON_PICKAXE);
		goesOn(helper, level, ModEnchantments.REAPING, Items.IRON_AXE, Items.BOW);
		goesOn(helper, level, ModEnchantments.TETHERSHOT, Items.BOW, Items.IRON_SWORD);
		goesOn(helper, level, ModEnchantments.TETHERSHOT, Items.CROSSBOW, Items.TRIDENT);
		goesOn(helper, level, ModEnchantments.VEINSEEKER, ModItems.TETHERITE_PICKAXE, Items.IRON_SHOVEL);
		goesOn(helper, level, ModEnchantments.ANCHORED, Items.IRON_CHESTPLATE, Items.IRON_HELMET);
		goesOn(helper, level, ModEnchantments.ANCHORED, ModItems.TETHERITE_LEGGINGS, Items.IRON_BOOTS);
		goesOn(helper, level, ModEnchantments.WARDWEAVE, Items.IRON_BOOTS, Items.IRON_SWORD);
		goesOn(helper, level, ModEnchantments.SATED, Items.IRON_HELMET, Items.IRON_CHESTPLATE);
		goesOn(helper, level, ModEnchantments.SOULWARD, ModItems.TETHERITE_HELMET, Items.IRON_LEGGINGS);
		goesOn(helper, level, ModEnchantments.DRIFTFALL, Items.IRON_BOOTS, Items.IRON_HELMET);
		goesOn(helper, level, ModEnchantments.HUSHED_STEPS, ModItems.TETHERITE_BOOTS, Items.IRON_LEGGINGS);
		goesOn(helper, level, ModEnchantments.STEADFAST, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		goesOn(helper, level, ModEnchantments.FORESIGHT, ModItems.TETHER, Items.IRON_SWORD);
		goesOn(helper, level, ModEnchantments.KNITTING, Items.IRON_SWORD, Items.STICK);
		goesOn(helper, level, ModEnchantments.CURSE_OF_FRAYING, Items.IRON_PICKAXE, Items.STICK);
		goesOn(helper, level, ModEnchantments.CURSE_OF_THE_STARVED, Items.IRON_CHESTPLATE, Items.IRON_SWORD);
		helper.succeed();
	}

	/** An Unravelling blade leaves what it struck unravelling, 2 seconds a level; the effect bites once a second. */
	@GameTest
	public void unravellingBleeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-unravelling");
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(ench(level, ModEnchantments.UNRAVELLING), 2);
		p.setItemSlot(EquipmentSlot.MAINHAND, sword);
		Mob struck = mob(helper, 1.5, 2, 1.5);
		Mob bleeding = mob(helper, 3.5, 2, 1.5);
		try {
			EnchantmentHelper.doPostAttackEffectsWithItemSource(level, struck, level.damageSources().playerAttack(p), sword);
			MobEffectInstance e = struck.getEffect(ModEffects.UNRAVELLING);
			check(helper, e != null && e.getDuration() > 60 && e.getDuration() <= 80, "Unravelling II left %s, not 4 seconds of it", e);

			check(helper, ModEffects.UNRAVELLING.value().shouldApplyEffectTickThisTick(40, 0)
					&& !ModEffects.UNRAVELLING.value().shouldApplyEffectTickThisTick(39, 0), "Unravelling does not bite once a second");
			float before = bleeding.getHealth();
			ModEffects.UNRAVELLING.value().applyEffectTick(level, bleeding, 0);
			float lost = before - bleeding.getHealth();
			check(helper, Math.abs(lost - Config.ENCH_UNRAVELLING_DAMAGE) < 0.01, "a bite of Unravelling took %s, not %s", lost, Config.ENCH_UNRAVELLING_DAMAGE);
		} finally {
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			struck.discard();
			bleeding.discard();
		}
		helper.succeed();
	}

	/** A monster slain with Reaping on an expedition gives charge back to the first anchor the Tether is tied to. */
	@GameTest
	public void reapingFeedsTheAnchor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-reaping");
		PlayerData d = DataStore.get(p.getUUID());
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		String key = Anchor.key(at.getX(), at.getY(), at.getZ());
		Anchor a = new Anchor(at.getX(), at.getY(), at.getZ(), Dims.NETHER);
		DataStore.anchors().put(key, a);
		List<String> linked = new ArrayList<>(d.linked);
		boolean inRun = d.inRun, collapsing = d.collapsing;
		Mob zombie = mob(helper, 2.5, 2, 2.5);
		try {
			d.inRun = true;
			d.collapsing = false;
			d.linked.clear();
			d.linked.add(key);
			check(helper, Charms.reap(p, zombie) == 0, "a bare hand reaped");
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.enchant(ench(level, ModEnchantments.REAPING), 2);
			p.setItemSlot(EquipmentSlot.MAINHAND, sword);
			double back = Charms.reap(p, zombie);
			check(helper, Math.abs(back - 2 * Config.ENCH_REAPING_SECONDS) < 1e-9 && Math.abs(a.own(p.getStringUUID()) - back) < 1e-9,
					"Reaping II gave back %s (the anchor holds %s), not %s", back, a.own(p.getStringUUID()), 2 * Config.ENCH_REAPING_SECONDS);
			d.inRun = false;
			check(helper, Charms.reap(p, zombie) == 0, "Reaping gave charge back at home");
		} finally {
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			d.inRun = inRun;
			d.collapsing = collapsing;
			d.linked.clear();
			d.linked.addAll(linked);
			DataStore.anchors().remove(key);
			zombie.discard();
		}
		helper.succeed();
	}

	/** An arrow from a Tethershot bow hauls what it hits toward the archer; from a plain bow it does not. */
	@GameTest
	public void tethershotHauls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-tethershot");
		Vec3 o = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		p.teleportTo(o.x, o.y, o.z);
		Mob target = mob(helper, 1.5, 2, 7.5);
		Holder<net.minecraft.world.damagesource.DamageType> arrow = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.ARROW);
		try {
			p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			target.setDeltaMovement(Vec3.ZERO);
			Charms.afterDamage(target, new DamageSource(arrow, p, p), 2, 2, false);
			check(helper, target.getDeltaMovement().lengthSqr() < 1e-6, "a plain bow's arrow hauled its target (%s)", target.getDeltaMovement());
			ItemStack bow = new ItemStack(Items.BOW);
			bow.enchant(ench(level, ModEnchantments.TETHERSHOT), 1);
			p.setItemSlot(EquipmentSlot.MAINHAND, bow);
			Charms.afterDamage(target, new DamageSource(arrow, p, p), 2, 2, false);
			Vec3 v = target.getDeltaMovement();
			check(helper, v.z < -0.5 && v.y > 0, "a Tethershot arrow moved its target by %s, not toward the archer", v);
		} finally {
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			target.discard();
		}
		helper.succeed();
	}

	/** Veinseeker answers only in the Seam; where it answers, the ore and the veins within reach ring out. */
	@GameTest
	public void veinseekerRings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-veinseeker");
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.enchant(ench(level, ModEnchantments.VEINSEEKER), 1);
		p.setItemSlot(EquipmentSlot.MAINHAND, pick);
		BlockPos o = helper.absolutePos(new BlockPos(3, 2, 3));
		check(helper, Charms.seek(p, o, Blocks.STONE.defaultBlockState()) == -1, "Veinseeker answered outside the Seam");
		level.setBlock(o.offset(2, 0, 0), ModBlocks.TETHERITE_ORE.defaultBlockState(), 2);
		level.setBlock(o.offset(0, 1, -2), ModBlocks.SEAM_VEIN.defaultBlockState(), 2);
		level.setBlock(o.offset(-1, 0, 1), ModBlocks.SEAM_VEIN.defaultBlockState(), 2);
		try {
			int found = Charms.ring(level, p, o);
			check(helper, found == 1002, "Veinseeker found %s, not one ore and two veins", found);
		} finally {
			level.setBlock(o.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(o.offset(0, 1, -2), Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(o.offset(-1, 0, 1), Blocks.AIR.defaultBlockState(), 2);
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		helper.succeed();
	}

	/**
	 * What the armour's charms read: Anchored eases Unmoored, Steadfast the Elders' spells, Sated and the Starved the
	 * hunger, Hushed Steps how far one is seen (through the game's own reckoning), Soulward the Lurker; Wardweave guards
	 * against the wound's creatures only.
	 */
	@GameTest
	public void theArmourCharmsAreRead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-armour-charms");
		double chance = Config.ENCH_SOULWARD_CHANCE;
		try {
			check(helper, Charms.unmoored(p) == 1 && Charms.steadfast(p) == 1 && Charms.exhaustion(p) == 1f && Charms.visibility(p) == 1,
					"bare, the charms still count");
			p.setItemSlot(EquipmentSlot.CHEST, enchanted(level, Items.IRON_CHESTPLATE, ModEnchantments.ANCHORED, 2));
			check(helper, Math.abs(Charms.unmoored(p) - (1 - 2 * Config.ENCH_ANCHORED_UNMOORED)) < 1e-9, "Anchored II leaves %s of Unmoored", Charms.unmoored(p));
			p.setItemSlot(EquipmentSlot.LEGS, enchanted(level, Items.IRON_LEGGINGS, ModEnchantments.STEADFAST, 2));
			check(helper, Math.abs(Charms.steadfast(p) - (1 - 2 * Config.ENCH_STEADFAST)) < 1e-9, "Steadfast II leaves %s of a spell", Charms.steadfast(p));

			p.setItemSlot(EquipmentSlot.HEAD, enchanted(level, Items.IRON_HELMET, ModEnchantments.SATED, 2));
			check(helper, Math.abs(Charms.exhaustion(p) - (1 - 2 * Config.ENCH_SATED)) < 1e-6, "Sated II leaves %s of the hunger", Charms.exhaustion(p));
			ItemStack starved = enchanted(level, Items.IRON_BOOTS, ModEnchantments.CURSE_OF_THE_STARVED, 1);
			starved.enchant(ench(level, ModEnchantments.HUSHED_STEPS), 2);
			p.setItemSlot(EquipmentSlot.FEET, starved);
			check(helper, Math.abs(Charms.exhaustion(p) - (1 - 2 * Config.ENCH_SATED + Config.ENCH_STARVED)) < 1e-6,
					"Sated II with one Starved piece leaves %s of the hunger", Charms.exhaustion(p));
			double seen = p.getVisibilityPercent(level, null);
			check(helper, Math.abs(seen - (1 - 2 * Config.ENCH_HUSHED_STEPS)) < 1e-6, "in Hushed Steps II boots, a player is seen at %s", seen);

			check(helper, !Charms.soulward(p), "a helmet without Soulward warded off the Lurker");
			p.setItemSlot(EquipmentSlot.HEAD, enchanted(level, Items.IRON_HELMET, ModEnchantments.SOULWARD, 1));
			Config.ENCH_SOULWARD_CHANCE = 1.0;
			check(helper, Charms.soulward(p), "Soulward at a certain chance did not ward");
			Config.ENCH_SOULWARD_CHANCE = 0.0;
			check(helper, !Charms.soulward(p), "Soulward at no chance warded");

			Holder<Enchantment> ward = ench(level, ModEnchantments.WARDWEAVE);
			ItemStack chest = enchanted(level, Items.IRON_CHESTPLATE, ModEnchantments.WARDWEAVE, 3);
			LivingEntity lurker = com.extremedimensions.registry.ModEntities.LURKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			Mob zombie = mob(helper, 1.5, 2, 4.5);
			MutableFloat fromLurker = new MutableFloat(0), fromZombie = new MutableFloat(0);
			ward.value().modifyDamageProtection(level, 3, chest, p, level.damageSources().mobAttack(lurker), fromLurker);
			ward.value().modifyDamageProtection(level, 3, chest, p, level.damageSources().mobAttack(zombie), fromZombie);
			check(helper, Math.abs(fromLurker.floatValue() - 6) < 0.01 && fromZombie.floatValue() == 0,
					"Wardweave III gave %s protection against a Lurker and %s against a zombie", fromLurker.floatValue(), fromZombie.floatValue());
			lurker.discard();
			zombie.discard();
		} finally {
			Config.ENCH_SOULWARD_CHANCE = chance;
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				p.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		helper.succeed();
	}

	/** Foresight on the bound Tether adds its cards to a Collapse's offer; unbound it adds none. */
	@GameTest
	public void foresightAddsACard(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-foresight");
		PlayerData d = DataStore.get(p.getUUID());
		try {
			check(helper, Charms.foresight(d) == 0, "with no Tether, Foresight adds cards");
			d.tetherAnchor = "test-foresight-anchor";
			d.tetherCharms = new java.util.HashMap<>(java.util.Map.of(ModEnchantments.id(ModEnchantments.FORESIGHT), 1));
			check(helper, Charms.foresight(d) == Config.ENCH_FORESIGHT_CARDS, "Foresight adds %s cards, not %s", Charms.foresight(d), Config.ENCH_FORESIGHT_CARDS);
		} finally {
			d.tetherAnchor = null;
			d.tetherCharms.clear();
		}
		helper.succeed();
	}

	/** Driftfall catches a long fall with slow falling, then rests; Knitting mends a point; Fraying wears in the Seam only. */
	@GameTest
	public void driftKnitAndFray(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-drift-knit");
		try {
			p.setItemSlot(EquipmentSlot.FEET, enchanted(level, Items.IRON_BOOTS, ModEnchantments.DRIFTFALL, 1));
			p.setOnGround(false);
			p.fallDistance = Config.ENCH_DRIFTFALL_FALL + 4;
			check(helper, Charms.drift(p) && p.hasEffect(MobEffects.SLOW_FALLING), "Driftfall did not catch a long fall");
			p.removeEffect(MobEffects.SLOW_FALLING);
			check(helper, !Charms.drift(p), "Driftfall caught again before it rested");

			ItemStack sword = enchanted(level, Items.IRON_SWORD, ModEnchantments.KNITTING, 1);
			sword.setDamageValue(10);
			p.setItemSlot(EquipmentSlot.MAINHAND, sword);
			check(helper, Charms.knit(p) == 1 && p.getMainHandItem().getDamageValue() == 9, "Knitting mended to %s, not 9", p.getMainHandItem().getDamageValue());

			ItemStack frayed = enchanted(level, Items.IRON_PICKAXE, ModEnchantments.CURSE_OF_FRAYING, 1);
			ServerLevel seam = level.getServer().getLevel(SeamService.SEAM);
			check(helper, Charms.fraying(level, frayed, 3) == 3, "the Curse of Fraying wore faster outside the Seam");
			if (seam != null) {
				int worn = EnchantmentHelper.processDurabilityChange(seam, frayed, 3);
				check(helper, worn == 3 + (int) Math.round(3 * Config.ENCH_FRAYING_WEAR), "in the Seam, Fraying wore %s for 3", worn);
				check(helper, EnchantmentHelper.processDurabilityChange(seam, new ItemStack(Items.IRON_PICKAXE), 3) == 3, "a plain pick wore faster in the Seam");
			}
		} finally {
			Charms.forget(p.getUUID());
			p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			p.fallDistance = 0;
		}
		helper.succeed();
	}

	/** The mixins behind the charms are applied (a missing target fails silently otherwise). */
	@GameTest
	public void everyCharmMixinIsApplied(GameTestHelper helper) {
		check(helper, has(Player.class, "extremedimensions$sated"), "the Sated mixin did not apply to Player");
		check(helper, has(LivingEntity.class, "extremedimensions$hushedSteps"), "the Hushed Steps mixin did not apply to LivingEntity");
		check(helper, has(EnchantmentHelper.class, "extremedimensions$fraying"), "the Fraying mixin did not apply to EnchantmentHelper");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static boolean has(Class<?> c, String name) {
		for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
			if (m.getName().contains(name)) {
				return true;
			}
		}
		return false;
	}

	private static Holder<Enchantment> ench(ServerLevel level, ResourceKey<Enchantment> k) {
		return ModEnchantments.holder(level.registryAccess(), k).orElseThrow();
	}

	private static ItemStack enchanted(ServerLevel level, net.minecraft.world.item.Item item, ResourceKey<Enchantment> k, int lvl) {
		ItemStack s = new ItemStack(item);
		s.enchant(ench(level, k), lvl);
		return s;
	}

	private static void incompatible(GameTestHelper helper, ServerLevel level, HolderLookup.RegistryLookup<Enchantment> all, ResourceKey<Enchantment> ours,
			ResourceKey<Enchantment> theirs) {
		check(helper, !Enchantment.areCompatible(ench(level, ours), all.getOrThrow(theirs)), "%s goes with %s", ours.identifier(), theirs.identifier());
	}

	private static void goesOn(GameTestHelper helper, ServerLevel level, ResourceKey<Enchantment> k, net.minecraft.world.item.Item yes,
			net.minecraft.world.item.Item no) {
		Enchantment e = ench(level, k).value();
		check(helper, e.canEnchant(new ItemStack(yes)), "%s does not go on %s", k.identifier(), yes);
		check(helper, !e.canEnchant(new ItemStack(no)), "%s goes on %s", k.identifier(), no);
	}

	/** A zombie at a position relative to the test, standing still. */
	private static Mob mob(GameTestHelper helper, double x, double y, double z) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "zombie"));
		Mob m = (Mob) helper.spawn(type, new Vec3(x, y, z));
		m.setNoAi(true);
		return m;
	}
}
