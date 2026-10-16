package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.Loot;

/**
 * {@code config/battlehub/skywars.json}: what is in the chests. Each line is "item min-max xWeight", for example
 * "minecraft:cobblestone 16-32 x8" (a weight of 8 out of the total, 16 to 32 of them). Island chests and the
 * better chests in the middle have their own list; how many items a chest gets is in config.json (skywars).
 */
public final class SkyWarsConfig {
	public List<String> islandLoot = new ArrayList<>(List.of(
			"minecraft:stone 16-32 x8", "minecraft:oak_planks 16-32 x6", "minecraft:stone_sword x4", "minecraft:iron_sword x3",
			"minecraft:bow x2", "minecraft:arrow 4-12 x4", "minecraft:chainmail_chestplate x3", "minecraft:iron_chestplate x2",
			"minecraft:iron_leggings x2", "minecraft:iron_helmet x2", "minecraft:iron_boots x2", "minecraft:cooked_beef 3-6 x5",
			"minecraft:golden_apple x2", "minecraft:snowball 4-8 x3", "minecraft:egg 4-8 x2", "minecraft:water_bucket x2",
			"minecraft:lava_bucket x1", "minecraft:iron_pickaxe x2", "minecraft:iron_axe x2", "minecraft:ender_pearl x1"));
	public List<String> centerLoot = new ArrayList<>(List.of(
			"minecraft:diamond_sword x3", "minecraft:diamond_chestplate x2", "minecraft:diamond_helmet x2",
			"minecraft:diamond_leggings x2", "minecraft:diamond_boots x2", "minecraft:ender_pearl 1-2 x3",
			"minecraft:golden_apple 1-2 x4", "minecraft:bow x2", "minecraft:arrow 8-16 x3", "minecraft:obsidian 4-8 x2",
			"minecraft:tnt 2-4 x2", "minecraft:flint_and_steel x1", "minecraft:enchanted_golden_apple x1",
			"minecraft:cobweb 2-4 x2", "minecraft:oak_planks 32-64 x3"));

	public List<Loot.Entry> island() {
		return parse(islandLoot);
	}

	public List<Loot.Entry> center() {
		return parse(centerLoot);
	}

	private static List<Loot.Entry> parse(List<String> lines) {
		List<Loot.Entry> out = new ArrayList<>();
		for (String l : lines) {
			if (l != null && !l.isBlank()) out.add(Loot.Entry.parse(l));
		}
		return out;
	}

	public SkyWarsConfig normalize() {
		if (islandLoot == null || islandLoot.isEmpty()) islandLoot = new SkyWarsConfig().islandLoot;
		if (centerLoot == null || centerLoot.isEmpty()) centerLoot = new SkyWarsConfig().centerLoot;
		return this;
	}
}
