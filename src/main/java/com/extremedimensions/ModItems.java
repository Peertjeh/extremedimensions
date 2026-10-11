package com.extremedimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * De twee Tether-items. Ze worden gemaakt met een Lodestone en laden een dimensie-tank
 * in een keer op als je ze op het altaar (Lodestone) gebruikt.
 */
public final class ModItems {
	private ModItems() {}

	public static final Item EMBER_TETHER = register("ember_tether");
	public static final Item VOID_TETHER = register("void_tether");

	private static Item register(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** Alleen aanroepen om de klasse (en dus de registratie) te laden. */
	public static void init() {}
}
