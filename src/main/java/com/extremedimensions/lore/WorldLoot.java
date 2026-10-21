package com.extremedimensions.lore;

import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * The Wanderer passed through the world before you: torn journal pages and Tether Fragments turn up in the chests of
 * ruined portals, fortresses, bastions, strongholds, ancient cities and end cities. Each of those chests draws once
 * from one of the mod's tables (`loot_table/inject/`), so a datapack can change what the Wanderer left.
 */
public final class WorldLoot {
	private WorldLoot() {}

	private static final Map<ResourceKey<LootTable>, String> LEAVINGS = Map.of(
			BuiltInLootTables.RUINED_PORTAL, "inject/ruined_portal",
			BuiltInLootTables.NETHER_BRIDGE, "inject/nether_bridge",
			BuiltInLootTables.BASTION_TREASURE, "inject/bastion_treasure",
			BuiltInLootTables.STRONGHOLD_LIBRARY, "inject/stronghold_library",
			BuiltInLootTables.ANCIENT_CITY, "inject/ancient_city",
			BuiltInLootTables.END_CITY_TREASURE, "inject/end_city_treasure");

	public static void register() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			String leavings = LEAVINGS.get(key);
			if (leavings != null) {
				table.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(Loot.key(leavings))));
			}
		});
	}
}
