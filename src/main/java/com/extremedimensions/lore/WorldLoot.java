package com.extremedimensions.lore;

import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * The Wanderer passed through the world before you: torn journal pages and Tether Fragments turn up in the chests of
 * ruined portals, fortresses, bastions, strongholds, ancient cities and end cities. Each of those chests also draws
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
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey().ifPresent(key -> {
			String leavings = LEAVINGS.get(key);
			if (leavings != null) {
				context.getLevel().getServer().reloadableRegistries().getLootTable(Loot.key(leavings)).getRandomItemsRaw(context, drops::add);
			}
			// A player on the First Weaver's trail finds its thread here (whoever opened the chest, if anyone did)
			FirstWeaver.chest(key, opener(context), drops::add);
		}));
	}

	/** How the loot context is asked for one of its values, found once by its shape (the name differs between versions). */
	private static final java.lang.reflect.Method PARAMETER = parameterGetter();

	private static java.lang.reflect.Method parameterGetter() {
		java.lang.reflect.Method found = null;
		for (java.lang.reflect.Method m : LootContext.class.getMethods()) {
			Class<?>[] args = m.getParameterTypes();
			if (args.length == 1 && args[0] == ContextKey.class && m.getReturnType() != boolean.class
					&& (found == null || m.getName().toLowerCase(java.util.Locale.ROOT).contains("optional"))) {
				// (one that answers nothing when the value is missing, before one that insists)
				found = m;
			}
		}
		return found;
	}

	/** Whoever opened the chest, or null (nobody did, or the context does not say). */
	private static Object opener(LootContext context) {
		if (PARAMETER == null) {
			return null;
		}
		try {
			Object v = PARAMETER.invoke(context, LootContextParams.THIS_ENTITY);
			return v instanceof java.util.Optional<?> o ? o.orElse(null) : v;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
