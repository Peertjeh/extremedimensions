package com.extremedimensions.registry;

import java.util.Optional;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The mod's enchantments. They are data (`data/extremedimensions/enchantment/`), as the game's own are: Woundbane does
 * its work there entirely; the others are read here by their level, since what they change (the drain, the Seam's time,
 * the fog, what comes home) is the mod's own.
 */
public final class ModEnchantments {
	private ModEnchantments() {}

	/** More damage against the creatures of the wound, a little more against the Elders. */
	public static final ResourceKey<Enchantment> WOUNDBANE = key("woundbane");
	/** A Tether that drinks less; a Collapse with it costs a curse more. */
	public static final ResourceKey<Enchantment> THRIFT = key("thrift");
	/** A Tether that holds longer in the Seam. */
	public static final ResourceKey<Enchantment> SILKBOUND = key("silkbound");
	/** A helmet that sees farther through the Seam's fog, and is seen farther by the Thinned. */
	public static final ResourceKey<Enchantment> VEILSIGHT = key("veilsight");
	/** Gear that comes home with you from a death beyond the wound; worn beyond, it drains a little. */
	public static final ResourceKey<Enchantment> HOMEBOUND = key("homebound");

	private static ResourceKey<Enchantment> key(String id) {
		return ResourceKey.create(Registries.ENCHANTMENT, ExtremeDimensions.id(id));
	}

	/** One of the mod's enchantments in this world (empty if a datapack took it out). */
	public static Optional<? extends Holder<Enchantment>> holder(RegistryAccess access, ResourceKey<Enchantment> k) {
		return access.lookupOrThrow(Registries.ENCHANTMENT).get(k);
	}

	/** Its level on one item stack. */
	public static int level(RegistryAccess access, ItemStack stack, ResourceKey<Enchantment> k) {
		return stack.isEmpty() ? 0 : holder(access, k).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
	}

	/** The highest level of it on what a creature wears and holds. */
	public static int worn(LivingEntity e, ResourceKey<Enchantment> k) {
		return holder(e.level().registryAccess(), k).map(h -> EnchantmentHelper.getEnchantmentLevel(h, e)).orElse(0);
	}

	/** An enchantment's id as text (what a Tether's charms are kept under while it is tied). */
	public static String id(ResourceKey<Enchantment> k) {
		return k.identifier().toString();
	}
}
