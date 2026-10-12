package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	private ModItems() {}

	/** Instantly charges a Nether anchor. */
	public static final Item EMBER_TETHER = register("ember_tether", 64, Rarity.UNCOMMON);
	/** Instantly charges an End anchor. */
	public static final Item VOID_TETHER = register("void_tether", 64, Rarity.UNCOMMON);
	/** The physical tether: bind it to an anchor before stepping through the wound. */
	public static final Item TETHER = register("tether", 16, Rarity.COMMON);
	/** A Wanderer's journal that fills as the player discovers the mod. */
	public static final Item TETHER_JOURNAL = register("tether_journal", 1, Rarity.UNCOMMON);
	/** A frayed piece of the Tether. Offered to an anchor it removes one penalty stack. */
	public static final Item TETHER_FRAGMENT = register("tether_fragment", 16, Rarity.RARE);

	/** A torn journal page found in ruins and strongholds; using it unlocks a page of the Tether Journal. */
	public static final Item WANDERERS_PAGE = register("wanderers_page", 16, Rarity.UNCOMMON);

	private static Item register(String id, int maxStack, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		Item.Properties props = new Item.Properties().stacksTo(maxStack).rarity(rarity).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
