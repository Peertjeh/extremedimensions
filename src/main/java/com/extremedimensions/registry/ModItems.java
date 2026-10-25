package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	private ModItems() {}

	/** Instantly charges a Nether anchor. */
	public static final Item EMBER_TETHER = register("ember_tether", 64, Rarity.UNCOMMON);
	/** Instantly charges an End anchor. */
	public static final Item VOID_TETHER = register("void_tether", 64, Rarity.UNCOMMON);
	/** The physical tether: bind it to an anchor before stepping through the wound. */
	public static final Item TETHER = tether("tether", Rarity.COMMON);
	/** A Wanderer's journal that fills as the player discovers the mod. */
	public static final Item TETHER_JOURNAL = register("tether_journal", 1, Rarity.UNCOMMON);
	/** A frayed piece of the Tether. Offered to an anchor it takes a curse stack off (one fragment per stack it has). */
	public static final Item TETHER_FRAGMENT = register("tether_fragment", 16, Rarity.RARE);

	/** A torn journal page found in ruins and strongholds; using it unlocks a page of the Tether Journal. */
	public static final Item WANDERERS_PAGE = register("wanderers_page", 16, Rarity.UNCOMMON);
	/** A page in a hand that is not the Wanderer's: the Held's letter. Read, not used up (friends can read the same one). */
	public static final Item LOOSE_LEAF = register("loose_leaf", 1, Rarity.RARE);

	/** Thread spun from the Seam itself, mined from seam veins. */
	public static final Item SEAM_SILK = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ExtremeDimensions.id("seam_silk")),
			new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON).trimMaterial(com.extremedimensions.tetherite.TetheriteTrims.SEAM_SILK)
					.setId(ResourceKey.create(Registries.ITEM, ExtremeDimensions.id("seam_silk")))));
	/** Used on crying obsidian, it tears a rift into the Seam. */
	public static final Item SEAM_NEEDLE = register("seam_needle", 16, Rarity.RARE);

	/** A Tether woven with Seam Silk and soul light: slower drain, a longer reach. */
	public static final Item SOUL_WOVEN_TETHER = tether("soul_woven_tether", Rarity.UNCOMMON);
	/** The Weavers' own work: slowest drain, and once per expedition it unravels you home instead of collapsing. */
	public static final Item SEAM_WOVEN_TETHER = tether("seam_woven_tether", Rarity.EPIC);

	/** A wing of a Veil Ray, still glowing along its seams. */
	public static final Item VEIL_WING = register("veil_wing", 16, Rarity.UNCOMMON);
	/** Silk woven around a Veil Wing and a fragment: pressed to the skin, it takes one curse stack off. */
	public static final Item MENDING_CLOTH = register("mending_cloth", 16, Rarity.RARE);

	/** Dropped by the Starved One: use it to mend the first wound. */
	public static final Item MENDING_THREAD = register("mending_thread", 1, Rarity.EPIC);
	/** Dropped by the Starved One: use them to sever the Tether from the world for good. */
	public static final Item SEVERING_SHEARS = register("severing_shears", 1, Rarity.EPIC);

	/** The First Weaver's trail: a thread left in the frame of an Ancient City once it is strung with silk. */
	public static final Item ECHO_THREAD = register("echo_thread", 1, Rarity.RARE);
	/** ... tied round a ledger in a bastion's chest. */
	public static final Item EMBER_THREAD = register("ember_thread", 1, Rarity.RARE);
	/** ... in an empty socket in an End city's chest. */
	public static final Item PALE_THREAD = register("pale_thread", 1, Rarity.RARE);
	/** ... pulled from a half-woven tapestry on a loom in the Seam. */
	public static final Item SILK_THREAD = register("silk_thread", 1, Rarity.RARE);
	/** The four threads twisted round a weight: held in the Seam it points the way down to the First Knot. */
	public static final Item PLUMB_LINE = register("plumb_line", 1, Rarity.EPIC);

	/**
	 * The Tapestry's threads, each earned by a great deed and woven into its panel on the Weaver's Loom once the world
	 * has its ending (see Tapestry).
	 */
	public static final Item THREAD_OF_ROOTS = register("thread_of_roots", 1, Rarity.EPIC);
	public static final Item THREAD_OF_TOLL = register("thread_of_toll", 1, Rarity.EPIC);
	public static final Item THREAD_OF_UNRAVELLING = register("thread_of_unravelling", 1, Rarity.EPIC);
	public static final Item THREAD_OF_THE_CHAIN = register("thread_of_the_chain", 1, Rarity.EPIC);
	public static final Item THREAD_OF_THE_KNOT = register("thread_of_the_knot", 1, Rarity.EPIC);
	public static final Item THREAD_OF_THE_HEART = register("thread_of_the_heart", 1, Rarity.EPIC);

	/** The Seam's song (its jukebox_song data file: the sound, length and comparator signal) and the disc that plays it. */
	public static final ResourceKey<JukeboxSong> TICK_OF_THE_BOUNDARY = ResourceKey.create(Registries.JUKEBOX_SONG, ExtremeDimensions.id("tick_of_the_boundary"));
	/** A music disc like Minecraft's own, found now and then in the barrels of the Thinned. */
	public static final Item MUSIC_DISC_TICK_OF_THE_BOUNDARY = disc("music_disc_tick_of_the_boundary", TICK_OF_THE_BOUNDARY);

	/** The relics: bound to the Tether one at a time, each with a gift and a price (see Relic). */
	public static final java.util.Map<com.extremedimensions.relic.Relic, Item> RELICS = relics();

	/** Every block of the hush masonry family as an item, in creative-tab order (the vein last). */
	public static final java.util.List<Item> HUSH = ModBlocks.HUSH.stream().map(ModItems::block).toList();
	/** The Veil Lantern's item: placed like any lantern, standing or hanging. */
	public static final Item VEIL_LANTERN = block(ModBlocks.VEIL_LANTERN);
	/** The Weaver's Loom as an item. */
	public static final Item WEAVERS_LOOM = block(ModBlocks.WEAVERS_LOOM);

	private static Item block(Block block) {
		return block(block, false);
	}

	private static Item block(Block block, boolean fireproof) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().setId(key);
		if (fireproof) {
			props = props.fireResistant().rarity(Rarity.RARE);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, props));
	}

	/** A Tether: stacks to 16 and takes the Tether enchantments (one at a time at the table, see the ItemStack mixin). */
	private static Item tether(String id, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		Item.Properties props = new Item.Properties().stacksTo(16).rarity(rarity).enchantable(10).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
	}

	/** An echo shard set in a spyglass and bound with silk: it listens to wounds. Scopes like a spyglass. */
	public static final Item WOUND_LENS = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ExtremeDimensions.id("wound_lens")),
			new net.minecraft.world.item.SpyglassItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
					.setId(ResourceKey.create(Registries.ITEM, ExtremeDimensions.id("wound_lens")))));

	// --- Tetherite: the ore of the Seam's hearts, spun cord, the ingot, the upgrade and the gear (see Tetherite) ------

	public static final Item TETHERITE_ORE = block(ModBlocks.TETHERITE_ORE, true);
	public static final Item SPINDLE = block(ModBlocks.SPINDLE);
	/** Smelted from Tetherite ore. */
	public static final Item TETHERITE_SCRAP = Registry.register(BuiltInRegistries.ITEM, key("tetherite_scrap"),
			new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant().setId(key("tetherite_scrap"))));
	/** Tether Fragments spun with Seam Silk on a Spindle: what binds scrap into Tetherite. */
	public static final Item TETHER_CORD = register("tether_cord", 64, Rarity.RARE);
	/** Four scrap and four cord. */
	public static final Item TETHERITE_INGOT = Registry.register(BuiltInRegistries.ITEM, key("tetherite_ingot"),
			new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()
					.trimMaterial(com.extremedimensions.tetherite.TetheriteTrims.TETHERITE).setId(key("tetherite_ingot"))));
	/** Takes netherite gear and a Tetherite ingot to Tetherite at a smithing table. */
	public static final Item TETHERITE_UPGRADE_SMITHING_TEMPLATE = Registry.register(BuiltInRegistries.ITEM, key("tetherite_upgrade_smithing_template"),
			upgradeTemplate(new Item.Properties().rarity(Rarity.RARE).setId(key("tetherite_upgrade_smithing_template"))));

	public static final Item TETHERITE_SWORD = gear("tetherite_sword", p -> p.sword(com.extremedimensions.tetherite.Tetherite.TOOLS, 3.0F, -2.4F));
	public static final Item TETHERITE_SHOVEL = gear("tetherite_shovel", p -> p.shovel(com.extremedimensions.tetherite.Tetherite.TOOLS, 1.5F, -3.0F));
	public static final Item TETHERITE_PICKAXE = gear("tetherite_pickaxe", p -> p.pickaxe(com.extremedimensions.tetherite.Tetherite.TOOLS, 1.0F, -2.8F));
	public static final Item TETHERITE_AXE = gear("tetherite_axe", p -> p.axe(com.extremedimensions.tetherite.Tetherite.TOOLS, 5.0F, -3.0F));
	public static final Item TETHERITE_HOE = gear("tetherite_hoe", p -> p.hoe(com.extremedimensions.tetherite.Tetherite.TOOLS, -4.0F, 0.0F));
	/** As netherite's spear, on Tetherite. */
	public static final Item TETHERITE_SPEAR = gear("tetherite_spear",
			p -> p.spear(com.extremedimensions.tetherite.Tetherite.TOOLS, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F));
	public static final Item TETHERITE_HELMET = gear("tetherite_helmet",
			p -> p.humanoidArmor(com.extremedimensions.tetherite.Tetherite.ARMOR, net.minecraft.world.item.equipment.ArmorType.HELMET));
	public static final Item TETHERITE_CHESTPLATE = gear("tetherite_chestplate",
			p -> p.humanoidArmor(com.extremedimensions.tetherite.Tetherite.ARMOR, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item TETHERITE_LEGGINGS = gear("tetherite_leggings",
			p -> p.humanoidArmor(com.extremedimensions.tetherite.Tetherite.ARMOR, net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item TETHERITE_BOOTS = gear("tetherite_boots",
			p -> p.humanoidArmor(com.extremedimensions.tetherite.Tetherite.ARMOR, net.minecraft.world.item.equipment.ArmorType.BOOTS));

	/** The Seam's armor trims: the Weaver's, from its reliquaries, and the Fray's, from its snarls (see TetheriteTrims). */
	public static final Item WEAVER_ARMOR_TRIM_SMITHING_TEMPLATE = Registry.register(BuiltInRegistries.ITEM, key("weaver_armor_trim_smithing_template"),
			net.minecraft.world.item.SmithingTemplateItem.createArmorTrimTemplate(new Item.Properties().rarity(Rarity.RARE)
					.setId(key("weaver_armor_trim_smithing_template"))));
	public static final Item FRAY_ARMOR_TRIM_SMITHING_TEMPLATE = Registry.register(BuiltInRegistries.ITEM, key("fray_armor_trim_smithing_template"),
			net.minecraft.world.item.SmithingTemplateItem.createArmorTrimTemplate(new Item.Properties().rarity(Rarity.RARE)
					.setId(key("fray_armor_trim_smithing_template"))));

	/** The Tetherite gear, in creative-tab order. */
	public static final java.util.List<Item> TETHERITE_GEAR = java.util.List.of(TETHERITE_SWORD, TETHERITE_SHOVEL, TETHERITE_PICKAXE, TETHERITE_AXE,
			TETHERITE_HOE, TETHERITE_SPEAR, TETHERITE_HELMET, TETHERITE_CHESTPLATE, TETHERITE_LEGGINGS, TETHERITE_BOOTS);

	private static ResourceKey<Item> key(String id) {
		return ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
	}

	/** A Tetherite tool or piece of armor: fire-resistant, rare, one to a stack. */
	private static Item gear(String id, java.util.function.UnaryOperator<Item.Properties> kind) {
		return Registry.register(BuiltInRegistries.ITEM, key(id), new Item(kind.apply(new Item.Properties()).fireResistant().rarity(Rarity.RARE).setId(key(id))));
	}

	/** The upgrade template, worded and drawn like netherite's own. */
	private static Item upgradeTemplate(Item.Properties props) {
		java.util.List<net.minecraft.resources.Identifier> base = java.util.List.of("helmet", "sword", "chestplate", "pickaxe", "leggings", "axe", "boots",
				"hoe", "shovel", "spear").stream().map(s -> net.minecraft.resources.Identifier.withDefaultNamespace("container/slot/" + s)).toList();
		java.util.List<net.minecraft.resources.Identifier> additions = java.util.List.of(net.minecraft.resources.Identifier.withDefaultNamespace("container/slot/ingot"));
		String k = "item.extremedimensions.smithing_template.tetherite_upgrade.";
		return new net.minecraft.world.item.SmithingTemplateItem(
				net.minecraft.network.chat.Component.translatable(k + "applies_to").withStyle(net.minecraft.ChatFormatting.BLUE),
				net.minecraft.network.chat.Component.translatable(k + "ingredients").withStyle(net.minecraft.ChatFormatting.BLUE),
				net.minecraft.network.chat.Component.translatable(k + "base_slot_description"),
				net.minecraft.network.chat.Component.translatable(k + "additions_slot_description"),
				base, additions, props);
	}

	/** A music disc: one to a stack and rare, as vanilla's are, and played in a jukebox. */
	private static Item disc(String id, ResourceKey<JukeboxSong> song) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		Item.Properties props = new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
	}

	private static Item register(String id, int maxStack, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		Item.Properties props = new Item.Properties().stacksTo(maxStack).rarity(rarity).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
	}

	private static java.util.Map<com.extremedimensions.relic.Relic, Item> relics() {
		java.util.Map<com.extremedimensions.relic.Relic, Item> out = new java.util.EnumMap<>(com.extremedimensions.relic.Relic.class);
		for (com.extremedimensions.relic.Relic r : com.extremedimensions.relic.Relic.values()) {
			out.put(r, register(r.id, 1, Rarity.EPIC));
		}
		return java.util.Collections.unmodifiableMap(out);
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
