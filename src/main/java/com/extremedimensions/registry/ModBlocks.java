package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Charged anchors. A lodestone turns into one of these on its first Nether or End charge and turns back
 * into a plain lodestone when it runs dry. Breaking one drops a lodestone; the stored charge is lost.
 */
public final class ModBlocks {
	private ModBlocks() {}

	public static final Block EMBER_ANCHOR = register("ember_lodestone", 7);
	public static final Block VOID_ANCHOR = register("void_lodestone", 5);

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
