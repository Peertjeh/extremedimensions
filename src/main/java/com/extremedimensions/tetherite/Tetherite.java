package com.extremedimensions.tetherite;

import java.util.Map;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Tetherite: the metal the Fray keeps of every Tether it swallowed. Its ore lies only at the hearts of the Seam's
 * islands and in the stone of the Fray's knots; smelted it gives scrap, and scrap goes to ingots only with Tether Cord,
 * Tether Fragments spun on a Spindle with Seam Silk. An ingot and an upgrade template take netherite gear one step
 * further: a little harder, a little sharper, longer-lived, and tethered (see {@link Tethered}).
 *
 * <p>Wearing all four pieces binds its wearer to the world: the Tether drains more slowly (held to the drain floor with
 * everything else that eases it).
 */
public final class Tetherite {
	private Tetherite() {}

	/** What repairs Tetherite tools and armor, and what the upgrade recipes take. */
	public static final TagKey<Item> TOOL_MATERIALS = TagKey.create(Registries.ITEM, ExtremeDimensions.id("tetherite_tool_materials"));
	public static final TagKey<Item> REPAIRS_ARMOR = TagKey.create(Registries.ITEM, ExtremeDimensions.id("repairs_tetherite_armor"));
	/** Every Tetherite item: tethered, and what the full set counts. */
	public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, ExtremeDimensions.id("tetherite_gear"));

	/** Netherite's numbers, a step on: longer-lived, faster, a point more damage, a little more enchantable. */
	public static final ToolMaterial TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2531, 10.0F, 5.0F, 18, TOOL_MATERIALS);

	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, ExtremeDimensions.id("tetherite"));

	/** Netherite's protection, with more toughness, a little more steadiness and a longer life. */
	public static final ArmorMaterial ARMOR = new ArmorMaterial(42,
			Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3, ArmorType.BODY, 19),
			18, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F, REPAIRS_ARMOR, ASSET);

	private static final EquipmentSlot[] PIECES = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static boolean isGear(ItemStack stack) {
		return !stack.isEmpty() && stack.is(GEAR);
	}

	/** True when all four armor slots hold Tetherite. */
	public static boolean fullSet(Player p) {
		for (EquipmentSlot slot : PIECES) {
			if (!isGear(p.getItemBySlot(slot))) {
				return false;
			}
		}
		return true;
	}

	/** What the full set takes off the drain (1 when the set is not worn), held to the drain floor by the caller. */
	public static double drainEase(ServerPlayer p) {
		return fullSet(p) ? Math.max(0.0, 1.0 - Config.TETHERITE_SET_DRAIN) : 1.0;
	}
}
