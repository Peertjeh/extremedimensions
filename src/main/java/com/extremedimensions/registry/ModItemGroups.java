package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The "Extreme Dimensions" creative tab: everything the mod adds, plus the vanilla items it builds on. */
public final class ModItemGroups {
	private ModItemGroups() {}

	public static final ResourceKey<CreativeModeTab> MAIN = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ExtremeDimensions.id("main"));

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN, CreativeModeTab.builder(CreativeModeTab.Row.TOP, 7)
				.title(Component.translatable("itemGroup.extremedimensions.main"))
				.icon(() -> new ItemStack(ModItems.TETHER))
				.displayItems((params, out) -> {
					out.accept(ModItems.TETHER_JOURNAL);
					out.accept(ModItems.TETHER);
					out.accept(ModItems.SOUL_WOVEN_TETHER);
					out.accept(ModItems.SEAM_WOVEN_TETHER);
					out.accept(ModItems.EMBER_TETHER);
					out.accept(ModItems.VOID_TETHER);
					out.accept(ModItems.TETHER_FRAGMENT);
					out.accept(ModItems.WANDERERS_PAGE);
					out.accept(ModItems.LOOSE_LEAF);
					out.accept(ModItems.SEAM_NEEDLE);
					out.accept(ModItems.SEAM_SILK);
					out.accept(ModItems.VEIL_WING);
					out.accept(ModItems.MENDING_CLOTH);
					out.accept(ModItems.MENDING_THREAD);
					out.accept(ModItems.SEVERING_SHEARS);
					ModItems.RELICS.values().forEach(out::accept);
					ModItems.HUSH.forEach(out::accept);
					out.accept(Items.LODESTONE);
				})
				.build());
	}
}
