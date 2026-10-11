package com.extremedimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * De geladen altaren: een Lodestone verandert in een van deze blokken zodra hij zijn eerste
 * Nether- of End-lading krijgt. Kapotmaken geeft een gewone Lodestone terug (de charge is dan weg).
 */
public final class ModBlocks {
	private ModBlocks() {}

	public static final Block EMBER_LODESTONE = register("ember_lodestone", 7);
	public static final Block VOID_LODESTONE = register("void_lodestone", 5);

	private static Block register(String id, int light) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ExtremeDimensions.id(id));
		BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE)
				.lightLevel(state -> light)
				.emissiveRendering(state -> true)
				.setId(key);
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(props));
	}

	/** Alleen aanroepen om de klasse (en dus de registratie) te laden. */
	public static void init() {}
}
