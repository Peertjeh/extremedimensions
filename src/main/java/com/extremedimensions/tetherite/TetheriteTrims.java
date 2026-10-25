package com.extremedimensions.tetherite;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/**
 * The Seam's armor trims, all data (data/extremedimensions/trim_material, trim_pattern): two materials, Tetherite
 * (dark violet with the Tether's cyan in its light) and Seam Silk (a glowing violet), and two patterns, the Weaver
 * (found in the Seam's reliquaries) and the Fray (found in its snarls).
 */
public final class TetheriteTrims {
	private TetheriteTrims() {}

	public static final ResourceKey<TrimMaterial> TETHERITE = ResourceKey.create(Registries.TRIM_MATERIAL, ExtremeDimensions.id("tetherite"));
	public static final ResourceKey<TrimMaterial> SEAM_SILK = ResourceKey.create(Registries.TRIM_MATERIAL, ExtremeDimensions.id("seam_silk"));
	public static final ResourceKey<TrimPattern> WEAVER = ResourceKey.create(Registries.TRIM_PATTERN, ExtremeDimensions.id("weaver"));
	public static final ResourceKey<TrimPattern> FRAY = ResourceKey.create(Registries.TRIM_PATTERN, ExtremeDimensions.id("fray"));
}
