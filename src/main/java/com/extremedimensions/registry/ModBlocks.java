package com.extremedimensions.registry;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ExtremeDimensions;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistryEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The mod's blocks. Charged anchors: a lodestone turns into one of these on its first Nether or End charge and turns
 * back into a plain lodestone when it runs dry (breaking one drops a lodestone; the stored charge is lost). And the
 * Seam's hush masonry.
 */
public final class ModBlocks {
	private ModBlocks() {}

	public static final Block EMBER_ANCHOR = register("ember_lodestone", 7);
	public static final Block VOID_ANCHOR = register("void_lodestone", 5);

	/**
	 * The hush masonry family (see tools/hush), in creative-tab order: the Seam's stone, dark, violet-grey and strangely
	 * quiet underfoot, and everything its builders cut from it, from the darkest cobbles to the pale stone of their
	 * crowns. Every stone with shapes has a slab, stairs and a wall.
	 */
	public static final List<Block> HUSH = new ArrayList<>();

	public static final Block HUSH_STONE = stone("hush_stone", 0);
	public static final Block HUSH_STONE_SLAB = slab("hush_stone_slab", HUSH_STONE);
	public static final Block HUSH_STONE_STAIRS = stairs("hush_stone_stairs", HUSH_STONE);
	public static final Block HUSH_STONE_WALL = wall("hush_stone_wall", HUSH_STONE);
	public static final Block COBBLED_HUSH_STONE = stone("cobbled_hush_stone", 0);
	public static final Block COBBLED_HUSH_STONE_SLAB = slab("cobbled_hush_stone_slab", COBBLED_HUSH_STONE);
	public static final Block COBBLED_HUSH_STONE_STAIRS = stairs("cobbled_hush_stone_stairs", COBBLED_HUSH_STONE);
	public static final Block COBBLED_HUSH_STONE_WALL = wall("cobbled_hush_stone_wall", COBBLED_HUSH_STONE);
	public static final Block POLISHED_HUSH_STONE = stone("polished_hush_stone", 0);
	public static final Block POLISHED_HUSH_STONE_SLAB = slab("polished_hush_stone_slab", POLISHED_HUSH_STONE);
	public static final Block POLISHED_HUSH_STONE_STAIRS = stairs("polished_hush_stone_stairs", POLISHED_HUSH_STONE);
	public static final Block POLISHED_HUSH_STONE_WALL = wall("polished_hush_stone_wall", POLISHED_HUSH_STONE);
	public static final Block HUSH_PILLAR = pillar("hush_pillar");
	public static final Block HUSH_BRICKS = stone("hush_bricks", 0);
	public static final Block HUSH_BRICK_SLAB = slab("hush_brick_slab", HUSH_BRICKS);
	public static final Block HUSH_BRICK_STAIRS = stairs("hush_brick_stairs", HUSH_BRICKS);
	public static final Block HUSH_BRICK_WALL = wall("hush_brick_wall", HUSH_BRICKS);
	public static final Block CRACKED_HUSH_BRICKS = stone("cracked_hush_bricks", 0);
	public static final Block MOSSY_HUSH_BRICKS = stone("mossy_hush_bricks", 0);
	public static final Block MOSSY_HUSH_BRICK_SLAB = slab("mossy_hush_brick_slab", MOSSY_HUSH_BRICKS);
	public static final Block MOSSY_HUSH_BRICK_STAIRS = stairs("mossy_hush_brick_stairs", MOSSY_HUSH_BRICKS);
	public static final Block MOSSY_HUSH_BRICK_WALL = wall("mossy_hush_brick_wall", MOSSY_HUSH_BRICKS);
	public static final Block CHISELED_HUSH_BRICKS = stone("chiseled_hush_bricks", 0);
	/** Hush bricks with seam silk threaded through their mortar: the Weavers' halls, warm with its light. */
	public static final Block SILKED_HUSH_BRICKS = stone("silked_hush_bricks", 6);
	public static final Block HUSH_TILES = stone("hush_tiles", 0);
	public static final Block HUSH_TILE_SLAB = slab("hush_tile_slab", HUSH_TILES);
	public static final Block HUSH_TILE_STAIRS = stairs("hush_tile_stairs", HUSH_TILES);
	public static final Block HUSH_TILE_WALL = wall("hush_tile_wall", HUSH_TILES);
	/** Hush stone bleached by the Seam's light, for the crowns of its buildings. */
	public static final Block PALE_HUSH_BRICKS = stone("pale_hush_bricks", 0);
	public static final Block PALE_HUSH_BRICK_SLAB = slab("pale_hush_brick_slab", PALE_HUSH_BRICKS);
	public static final Block PALE_HUSH_BRICK_STAIRS = stairs("pale_hush_brick_stairs", PALE_HUSH_BRICKS);
	public static final Block PALE_HUSH_BRICK_WALL = wall("pale_hush_brick_wall", PALE_HUSH_BRICKS);
	public static final Block POLISHED_PALE_HUSH_STONE = stone("polished_pale_hush_stone", 0);
	public static final Block POLISHED_PALE_HUSH_STONE_SLAB = slab("polished_pale_hush_stone_slab", POLISHED_PALE_HUSH_STONE);
	public static final Block POLISHED_PALE_HUSH_STONE_STAIRS = stairs("polished_pale_hush_stone_stairs", POLISHED_PALE_HUSH_STONE);
	public static final Block POLISHED_PALE_HUSH_STONE_WALL = wall("polished_pale_hush_stone_wall", POLISHED_PALE_HUSH_STONE);
	/** Hush stone threaded with glowing silk; mined for Seam Silk. */
	public static final Block SEAM_VEIN = simple("seam_vein", 6);
	/** A soul lantern with a Veil Wing in its glass: a soul ward that also keeps a Wound Storm from breaking through near it. */
	public static final Block VEIL_LANTERN = Registry.register(BuiltInRegistries.BLOCK, key("veil_lantern"),
			new LanternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_LANTERN).setId(key("veil_lantern"))));

	/**
	 * The Weaver's Loom, built after the looms in the Seam: a frame of hush pillars strung with silk. The first one used
	 * holds the world's tapestry (see Tapestry); any other is only a loom.
	 */
	public static final Block WEAVERS_LOOM = Registry.register(BuiltInRegistries.BLOCK, key("weavers_loom"),
			new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).sound(SoundType.WOOL).lightLevel(state -> 4).setId(key("weavers_loom"))));

	/**
	 * Tetherite ore: a knot of lost Tether hardened round the stone, found only deep in the hearts of the Seam's islands
	 * and in the stone of the Fray's knots. As hard as ancient debris and as unmoved by blasts; a faint cyan glow.
	 */
	public static final Block TETHERITE_ORE = Registry.register(BuiltInRegistries.BLOCK, key("tetherite_ore"),
			new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.ANCIENT_DEBRIS).sound(SoundType.ANCIENT_DEBRIS).lightLevel(state -> 3)
					.setId(key("tetherite_ore"))));

	/** The Spindle, where Tether Fragments are spun with Seam Silk into Tether Cord (see SpindleBlock). */
	public static final Block SPINDLE = Registry.register(BuiltInRegistries.BLOCK, key("spindle"),
			// Not a copy of deepslate: that would need a pickaxe for it (and all it holds) to drop
			new com.extremedimensions.tetherite.SpindleBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
					.sound(SoundType.WOOL).strength(2.5F, 6.0F).noOcclusion().lightLevel(state -> state.getValue(com.extremedimensions.tetherite.SpindleBlock.FRAGMENTS) > 0 ? 5 : 0)
					.setId(key("spindle"))));

	/**
	 * The Kindled Seam Lantern: the Seam Lantern lit from a lantern's and a soul lantern's flames; it stands or hangs as
	 * they do, burns blue-violet, and its light reaches twice as far (see relic.KindledLanternBlock).
	 */
	public static final Block KINDLED_SEAM_LANTERN = Registry.register(BuiltInRegistries.BLOCK, key("kindled_seam_lantern"),
			// (drawn at full brightness, as its flame glows: under a shader pack that does not know it, it still shines)
			new com.extremedimensions.relic.KindledLanternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_LANTERN).lightLevel(state -> 15)
					.emissiveRendering(state -> true).randomTicks().setId(key("kindled_seam_lantern"))));

	/**
	 * The unseen light a Kindled Seam Lantern carries out round it (3.51.1; before, vanilla's own light block): the same
	 * block as vanilla's, under a name of its own, so a shader pack can be shown it apart from the light blocks of a map
	 * and give it the lantern's violet (see client.compat.ShaderLight).
	 */
	public static final Block KINDLED_LIGHT = Registry.register(BuiltInRegistries.BLOCK, key("kindled_light"),
			new net.minecraft.world.level.block.LightBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHT).noLootTable().setId(key("kindled_light"))));

	/** A knot of curse thread the Unweaving ties round the Weaver's Loom (see tapestry.Unweaving): cut by blows, never mined. */
	public static final Block CURSE_KNOT = Registry.register(BuiltInRegistries.BLOCK, key("curse_knot"),
			new com.extremedimensions.tapestry.CurseKnotBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)
					.strength(-1.0F, 3600000.0F).sound(SoundType.WOOL).lightLevel(state -> 9).randomTicks().noLootTable().setId(key("curse_knot"))));

	// --- The Seam's furnishings for builders: its silk as cloth, its sky as glass, and a lamp. Each is the vanilla block
	// it stands in for in all but its colours, so it changes nothing in play; none is part of the hush family.

	/**
	 * Seam Silk woven into a block, as string is into wool: it sounds, muffles and burns as wool does. It is kept out of
	 * the wool tags, so vanilla's recipes for wool (beds, banners) cannot take it; shears are no quicker on it for the
	 * same reason, as they know wool by that tag. Nothing turns it back into silk.
	 */
	public static final Block SEAM_SILK_BLOCK = Registry.register(BuiltInRegistries.BLOCK, key("seam_silk_block"),
			new Block(BlockBehaviour.Properties.ofFullCopy(vanilla("purple_wool")).setId(key("seam_silk_block"))));
	/** The silk as a carpet, cut from the block as vanilla's carpets are from wool. */
	public static final Block SEAM_SILK_CARPET = Registry.register(BuiltInRegistries.BLOCK, key("seam_silk_carpet"),
			new CarpetBlock(BlockBehaviour.Properties.ofFullCopy(vanilla("purple_carpet")).setId(key("seam_silk_carpet"))));
	/**
	 * Nebula Glass: glass stained with the Seam's sky, violet with a few faint stars. Glass in all it does: light passes,
	 * neighbours of its own kind hide the faces between them, and it drops only to Silk Touch. A beacon's beam goes
	 * through it as through plain glass, untinted.
	 */
	public static final Block NEBULA_GLASS = Registry.register(BuiltInRegistries.BLOCK, key("nebula_glass"),
			new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).mapColor(MapColor.COLOR_PURPLE).setId(key("nebula_glass"))));
	/** Its pane, which joins its neighbours as any glass pane does (other panes, iron bars, walls and solid faces). */
	public static final Block NEBULA_GLASS_PANE = Registry.register(BuiltInRegistries.BLOCK, key("nebula_glass_pane"),
			new IronBarsBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS_PANE).mapColor(MapColor.COLOR_PURPLE).setId(key("nebula_glass_pane"))));
	/**
	 * The Veil Lamp: a redstone lamp in the silk's violet that is always lit, at light 15. Its light is its own block's
	 * and spreads from there as any light does; unlike the Kindled Seam Lantern it carries none out round it, so it is a
	 * lamp to build with and no rival to the lantern's reach.
	 */
	public static final Block VEIL_LAMP = Registry.register(BuiltInRegistries.BLOCK, key("veil_lamp"),
			// (the lamp's own light follows its lit state, which this has none of: always on)
			new Block(BlockBehaviour.Properties.ofFullCopy(vanilla("redstone_lamp")).mapColor(MapColor.COLOR_PURPLE).lightLevel(state -> 15)
					.setId(key("veil_lamp"))));

	/**
	 * A vanilla block by id, to copy its properties: the dyed blocks have no constants in 26.3 (see ArenaDesign.mc), and
	 * the lamp is found the same way. One not found stops the game here, rather than build a block on the wrong one.
	 */
	private static Block vanilla(String id) {
		return BuiltInRegistries.BLOCK.get(Identifier.withDefaultNamespace(id)).map(h -> h.value())
				.orElseThrow(() -> new IllegalStateException("No vanilla block " + id + " to copy"));
	}

	private static ResourceKey<Block> key(String id) {
		return ResourceKey.create(Registries.BLOCK, ExtremeDimensions.id(id));
	}

	private static BlockBehaviour.Properties hush(ResourceKey<Block> key, int light) {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
				.sound(SoundType.WOOL)
				.lightLevel(state -> light)
				.setId(key);
	}

	private static <T extends Block> T add(ResourceKey<Block> key, T block) {
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		HUSH.add(block);
		return block;
	}

	private static Block stone(String id, int light) {
		ResourceKey<Block> key = key(id);
		return add(key, new Block(hush(key, light)));
	}

	private static Block pillar(String id) {
		ResourceKey<Block> key = key(id);
		return add(key, new RotatedPillarBlock(hush(key, 0)));
	}

	private static Block slab(String id, Block base) {
		ResourceKey<Block> key = key(id);
		return add(key, new SlabBlock(hush(key, 0)));
	}

	private static Block stairs(String id, Block base) {
		ResourceKey<Block> key = key(id);
		return add(key, new StairBlock(base.defaultBlockState(), hush(key, 0)));
	}

	private static Block wall(String id, Block base) {
		ResourceKey<Block> key = key(id);
		return add(key, new WallBlock(hush(key, 0).forceSolidOn()));
	}

	private static Block simple(String id, int light) {
		ResourceKey<Block> key = key(id);
		BlockBehaviour.Properties props = hush(key, light);
		if (light > 0) {
			props = props.emissiveRendering(state -> true);
		}
		return add(key, new Block(props));
	}

	private static Block register(String id, int light) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ExtremeDimensions.id(id));
		BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE)
				.lightLevel(state -> light)
				.emissiveRendering(state -> true)
				.setId(key);
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(props));
	}

	/**
	 * Forces class loading, and with it registration. And lets fire and furnaces take the silk cloth as they take wool
	 * and carpet: vanilla gives its own their odds in FireBlock and their burn time by the wool tags, which the silk is
	 * kept out of.
	 */
	public static void init() {
		// vanilla's odds for wool and for carpet: how readily each catches, and how quickly it burns away
		FlammableBlockRegistry.getDefaultInstance().add(SEAM_SILK_BLOCK, 30, 60);
		FlammableBlockRegistry.getDefaultInstance().add(SEAM_SILK_CARPET, 60, 20);
		// and the burn time of wool (half a smelt) and of a carpet (a third and a tick)
		FuelRegistryEvents.BUILD.register((fuels, context) -> {
			fuels.add(SEAM_SILK_BLOCK, 100);
			fuels.add(SEAM_SILK_CARPET, 67);
		});
	}
}
