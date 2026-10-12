package com.extremedimensions.lore;

import java.util.List;
import java.util.Map;

import com.extremedimensions.registry.ModItems;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * The Wanderer passed through the world before you: torn journal pages and Tether Fragments turn up in the chests of
 * ruined portals, fortresses, bastions, strongholds, ancient cities and end cities.
 */
public final class WorldLoot {
	private WorldLoot() {}

	private record Find(double page, double fragment) {}

	private static final Map<ResourceKey<LootTable>, Find> FINDS = Map.of(
			BuiltInLootTables.RUINED_PORTAL, new Find(0.30, 0.20),
			BuiltInLootTables.NETHER_BRIDGE, new Find(0.12, 0.10),
			BuiltInLootTables.BASTION_TREASURE, new Find(0.25, 0.25),
			BuiltInLootTables.STRONGHOLD_LIBRARY, new Find(0.35, 0.0),
			BuiltInLootTables.ANCIENT_CITY, new Find(0.15, 0.10),
			BuiltInLootTables.END_CITY_TREASURE, new Find(0.20, 0.15));

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey().ifPresent(key -> {
			Find f = FINDS.get(key);
			if (f == null) {
				return;
			}
			add(drops, ModItems.WANDERERS_PAGE, f.page(), context.getRandom().nextDouble());
			add(drops, ModItems.TETHER_FRAGMENT, f.fragment(), context.getRandom().nextDouble());
		}));
	}

	private static void add(List<ItemStack> drops, Item item, double chance, double roll) {
		if (roll < chance) {
			drops.add(new ItemStack(item));
		}
	}
}
