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
 * The mod's lanterns in a shader pack's list of blocks (3.51.1, with Iris). A shader pack gives glow and coloured light
 * to the blocks its list names, by the number it gives each; it names none of the mod's, so under it they shone as
 * plain blocks. Each of the mod's lanterns is given the number of a vanilla light the pack does name: the Kindled Seam
 * Lantern that of the block chosen in the settings (crying obsidian, the most violet of them, unless changed), the Veil
 * Lantern a soul lantern's. Where the pack names one of them itself, its own number is kept.
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

	/** Adds the lanterns to a list of blocks for a shader pack (one that cannot be added to is left to the caller). */
	public static void addLanterns(Object2IntMap<BlockState> ids) {
		if (ids == null) {
			return;
		}
		try {
			ClientOptions.ShaderLook look = ClientOptions.shaderLook();
			if (look == null || look.block.isEmpty()) {
				return;
			}
			BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", look.block))
					.ifPresent(source -> copy(ids, source.value(), ModBlocks.KINDLED_SEAM_LANTERN));
			copy(ids, Blocks.SOUL_LANTERN, ModBlocks.VEIL_LANTERN);
		} catch (UnsupportedOperationException fixed) {
			throw fixed;
		} catch (RuntimeException | LinkageError e) {
			if (!warned) {
				warned = true;
				ExtremeDimensions.LOGGER.warn("Could not show the mod's lanterns to the shader pack", e);
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
