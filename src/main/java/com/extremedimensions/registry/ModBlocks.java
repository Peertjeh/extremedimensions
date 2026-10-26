package com.extremedimensions.registry;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

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

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
