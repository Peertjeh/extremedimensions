package com.extremedimensions.client.compat;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.registry.ModBlocks;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The mod's glowing blocks in a shader pack's list of blocks (3.51.1, with Iris; every glowing block since 3.52.0). A shader pack gives glow and coloured light
 * to the blocks its list names, by the number it gives each; it names none of the mod's, so under it they shone as
 * plain blocks. Each of the mod's lanterns is given the number of a vanilla light the pack does name: the Kindled Seam
 * Lantern that of the block chosen in the settings (crying obsidian, the most violet of them, unless changed), and the
 * unseen lights it carries out round it the same (a pack that lights the world from its light blocks, as Solas does,
 * then lights its whole reach violet), the Veil Lantern a soul lantern's. With the setting off, the unseen lights are
 * given vanilla light blocks' numbers, as they were. Since 3.52.0 the mod's other glowing blocks too, each as the
 * vanilla light nearest it in colour and shape: the Ember Anchor as a magma block, the Void Anchor, the Seam Vein and
 * silked hush bricks as crying obsidian, Tetherite Ore as the ancient debris it is drawn after, and the Curse Knots each
 * in their set's colour (a torch, a soul torch, an amethyst cluster, a redstone torch); since 3.53.0 the Veil Lamp as
 * crying obsidian, and Nebula Glass, its panes and the silk block and carpet as their purple vanilla kin (glass and
 * cloth, for how a pack draws them rather than for light). The Weaver's Loom and the
 * Spindle, workplaces with a faint glow, are left as they are. With the setting off, none of them is shown to the pack.
 * Where the pack names one of them itself, its own number is kept.
 */
public final class ShaderLight {
	private static boolean warned;

	private ShaderLight() {
	}

	/**
	 * The list Iris is about to hand the shader pack, with the lanterns in it: the same list where it can be added to,
	 * else a copy of it. Never throws.
	 */
	public static Object2IntMap<BlockState> withLanterns(Object2IntMap<BlockState> ids) {
		if (ids == null) {
			return null;
		}
		try {
			addLanterns(ids);
			return ids;
		} catch (UnsupportedOperationException fixed) {
			Object2IntOpenHashMap<BlockState> copy = new Object2IntOpenHashMap<>(ids);
			copy.defaultReturnValue(ids.defaultReturnValue());
			addLanterns(copy);
			return copy;
		}
	}

	/** The vanilla light each set of Curse Knots is shown as, in the order of Unweaving.SETS (beyond, Overworld, everywhere, Extreme). */
	private static final Block[] KNOT_LIGHTS = {Blocks.TORCH, Blocks.SOUL_TORCH, Blocks.AMETHYST_CLUSTER, Blocks.REDSTONE_TORCH};

	/** Adds the mod's glowing blocks to a list of blocks for a shader pack (one that cannot be added to is left to the caller). */
	public static void addLanterns(Object2IntMap<BlockState> ids) {
		if (ids == null) {
			return;
		}
		try {
			ClientOptions.ShaderLook look = ClientOptions.shaderLook();
			if (look != null && !look.block.isEmpty()) {
				BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", look.block)).ifPresent(source -> {
					copy(ids, source.value(), ModBlocks.KINDLED_SEAM_LANTERN);
					copy(ids, source.value(), ModBlocks.KINDLED_LIGHT);
				});
				copy(ids, Blocks.SOUL_LANTERN, ModBlocks.VEIL_LANTERN);
				// (3.52.0) the anchors, the glowing stone and the ore
				copy(ids, Blocks.MAGMA_BLOCK, ModBlocks.EMBER_ANCHOR);
				copy(ids, Blocks.CRYING_OBSIDIAN, ModBlocks.VOID_ANCHOR);
				copy(ids, Blocks.CRYING_OBSIDIAN, ModBlocks.SEAM_VEIN);
				copy(ids, Blocks.CRYING_OBSIDIAN, ModBlocks.SILKED_HUSH_BRICKS);
				copy(ids, Blocks.ANCIENT_DEBRIS, ModBlocks.TETHERITE_ORE);
				// (3.53.0) the builders' blocks: the lamp as violet light, the glass and the silk as their purple vanilla
				// kin, so a pack gives the glass its gleam and the cloth its softness
				copy(ids, Blocks.CRYING_OBSIDIAN, ModBlocks.VEIL_LAMP);
				copy(ids, Blocks.PURPLE_STAINED_GLASS, ModBlocks.NEBULA_GLASS);
				copy(ids, Blocks.PURPLE_STAINED_GLASS_PANE, ModBlocks.NEBULA_GLASS_PANE);
				copy(ids, Blocks.PURPLE_WOOL, ModBlocks.SEAM_SILK_BLOCK);
				copy(ids, Blocks.PURPLE_CARPET, ModBlocks.SEAM_SILK_CARPET);
				// the Curse Knots, each set in its own colour
				for (BlockState state : ModBlocks.CURSE_KNOT.getStateDefinition().getPossibleStates()) {
					int set = state.getValue(com.extremedimensions.tapestry.CurseKnotBlock.SET);
					BlockState like = KNOT_LIGHTS[Math.floorMod(set, KNOT_LIGHTS.length)].defaultBlockState();
					if (!ids.containsKey(state) && ids.containsKey(like)) {
						ids.put(state, ids.getInt(like));
					}
				}
			}
			// (any unseen light not given the lantern's: as vanilla's light block of its level)
			copy(ids, Blocks.LIGHT, ModBlocks.KINDLED_LIGHT);
		} catch (UnsupportedOperationException fixed) {
			throw fixed;
		} catch (RuntimeException | LinkageError e) {
			if (!warned) {
				warned = true;
				ExtremeDimensions.LOGGER.warn("Could not show the mod's glowing blocks to the shader pack", e);
			}
		}
	}

	/** Every state of {@code to} given the number of the state of {@code from} most like it (public for tests). */
	public static void copy(Object2IntMap<BlockState> ids, Block from, Block to) {
		for (BlockState state : to.getStateDefinition().getPossibleStates()) {
			if (ids.containsKey(state)) {
				continue;
			}
			BlockState like = like(from, state);
			if (ids.containsKey(like)) {
				ids.put(state, ids.getInt(like));
			}
		}
	}

	/** {@code from}'s state that shares {@code state}'s settings where they have the same ones (hanging, in water). */
	public static BlockState like(Block from, BlockState state) {
		BlockState like = from.defaultBlockState();
		for (Property<?> p : state.getProperties()) {
			if (like.hasProperty(p)) {
				like = with(like, p, state);
			}
		}
		return like;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState like, Property<T> p, BlockState state) {
		return like.setValue(p, state.getValue(p));
	}
}
