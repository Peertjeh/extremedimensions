package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

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

	/** Thread spun from the Seam itself, mined from seam veins. */
	public static final Item SEAM_SILK = register("seam_silk", 64, Rarity.UNCOMMON);
	/** Used on crying obsidian, it tears a rift into the Seam. */
	public static final Item SEAM_NEEDLE = register("seam_needle", 16, Rarity.RARE);

	/** A Tether woven with Seam Silk and soul light: slower drain, a longer reach. */
	public static final Item SOUL_WOVEN_TETHER = register("soul_woven_tether", 16, Rarity.UNCOMMON);
	/** The Weavers' own work: slowest drain, and once per expedition it unravels you home instead of collapsing. */
	public static final Item SEAM_WOVEN_TETHER = register("seam_woven_tether", 16, Rarity.EPIC);

	/** Dropped by the Starved One: use it to mend the first wound. */
	public static final Item MENDING_THREAD = register("mending_thread", 1, Rarity.EPIC);
	/** Dropped by the Starved One: use them to sever the Tether from the world for good. */
	public static final Item SEVERING_SHEARS = register("severing_shears", 1, Rarity.EPIC);

	public static final Item HUSH_STONE = block(ModBlocks.HUSH_STONE);
	public static final Item HUSH_BRICKS = block(ModBlocks.HUSH_BRICKS);
	public static final Item SEAM_VEIN = block(ModBlocks.SEAM_VEIN);

	private static Item block(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, props));
	}

	private static Item register(String id, int maxStack, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		Item.Properties props = new Item.Properties().stacksTo(maxStack).rarity(rarity).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
