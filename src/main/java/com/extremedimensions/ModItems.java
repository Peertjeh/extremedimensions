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
	public static final Item VOID_TETHER = register("void_tether", 64);
	/** De fysieke tether: maak hem vast aan een anker voor je door een portaal gaat. */
	public static final Item TETHER = register("tether", 16);
	/** Het dagboek van een Wanderer; vult zich naarmate je verder komt. */
	public static final Item TETHER_JOURNAL = register("tether_journal", 1);
	/** Een losgerafeld stukje van de Tether zelf. Aan een altaar geofferd haalt het een penalty-stack weg. */
	public static final Item TETHER_FRAGMENT = register("tether_fragment", 16);

	private static Item register(String id) {
		return register(id, 64);
	}

	private static Item register(String id, int maxStack) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ExtremeDimensions.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(maxStack).setId(key)));
	}

	/** Alleen aanroepen om de klasse (en dus de registratie) te laden. */
	public static void init() {}
}
