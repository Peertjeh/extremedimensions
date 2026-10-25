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

	// --- 3.3: the Seam's craft, grown (what each does is in Charms, or wholly in its data file) ---------------------

	/** A blade that unravels what it cuts: the struck bleed for a while (data, with the Unravelling effect). */
	public static final ResourceKey<Enchantment> UNRAVELLING = key("unravelling");
	/** Every monster slain on an expedition gives a few seconds back to the Tether. */
	public static final ResourceKey<Enchantment> REAPING = key("reaping");
	/** Arrows that hit pull what they hit toward the archer. */
	public static final ResourceKey<Enchantment> TETHERSHOT = key("tethershot");
	/** Stone broken in the Seam makes the Tetherite ore and Seam Veins near it ring out. */
	public static final ResourceKey<Enchantment> VEINSEEKER = key("veinseeker");
	/** Steadier on the feet (data: knockback resistance), and the End's void throws its wearer less. */
	public static final ResourceKey<Enchantment> ANCHORED = key("anchored");
	/** Armour against the creatures of the wound and the Elders (data). */
	public static final ResourceKey<Enchantment> WARDWEAVE = key("wardweave");
	/** A helmet that keeps hunger at bay. */
	public static final ResourceKey<Enchantment> SATED = key("sated");
	/** Boots that catch a long fall in the Seam with a moment of slow falling. */
	public static final ResourceKey<Enchantment> DRIFTFALL = key("driftfall");
	/** Boots that make their wearer harder to notice. */
	public static final ResourceKey<Enchantment> HUSHED_STEPS = key("hushed_steps");
	/** A Tether whose Collapse offers one card more. */
	public static final ResourceKey<Enchantment> FORESIGHT = key("foresight");
	/** A helmet the Lurker flinches from before it can take anything. */
	public static final ResourceKey<Enchantment> SOULWARD = key("soulward");
	/** Gear that knits itself back together in the Seam. */
	public static final ResourceKey<Enchantment> KNITTING = key("knitting");
	/** Leggings that hold against the Elders' spells. */
	public static final ResourceKey<Enchantment> STEADFAST = key("steadfast");
	/** A curse: in the Seam and the Fray the item wears twice as fast. */
	public static final ResourceKey<Enchantment> CURSE_OF_FRAYING = key("fraying_curse");
	/** A curse: armour that makes its wearer hungry. */
	public static final ResourceKey<Enchantment> CURSE_OF_THE_STARVED = key("starved_curse");

	private static ResourceKey<Enchantment> key(String id) {
		return ResourceKey.create(Registries.ENCHANTMENT, ExtremeDimensions.id(id));
	}

	/** One of the mod's enchantments in this world (empty if a datapack took it out). */
	public static Optional<? extends Holder<Enchantment>> holder(RegistryAccess access, ResourceKey<Enchantment> k) {
		return access.lookupOrThrow(Registries.ENCHANTMENT).get(k);
	}

	/** Its level on one item stack (read off the stack's own enchantments: nothing is looked up for a plain one). */
	public static int level(RegistryAccess access, ItemStack stack, ResourceKey<Enchantment> k) {
		if (stack.isEmpty() || stack.getEnchantments().isEmpty()) {
			return 0;
		}
		for (var e : stack.getEnchantments().entrySet()) {
			if (e.getKey().is(k)) {
				return e.getIntValue();
			}
		}
		return 0;
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
