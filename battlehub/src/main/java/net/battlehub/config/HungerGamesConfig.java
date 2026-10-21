package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.Loot;

/**
 * {@code config/battlehub/hunger_games.json}: what is in the chests of the Hunger Games city. Each line is
 * "item min-max xWeight" as in skywars.json. The chests and barrels in the houses have the plain list, the chests
 * inside the landmarks and watchtowers a better one, the cornucopia on the plaza the best; how many items a chest
 * gets is in config.json (hunger_games: houseChestRolls, landmarkChestRolls, cornucopiaChestRolls).
 */
public final class HungerGamesConfig {
	public List<String> houseLoot = new ArrayList<>(List.of(
			"minecraft:wooden_sword x5", "minecraft:stone_sword x4", "minecraft:stone_axe x3", "minecraft:bow x2", "minecraft:arrow 3-8 x4",
			"minecraft:leather_helmet x3", "minecraft:leather_chestplate x3", "minecraft:leather_leggings x3", "minecraft:leather_boots x3",
			"minecraft:golden_helmet x2", "minecraft:chainmail_boots x2", "minecraft:bread 1-3 x6", "minecraft:apple 1-3 x5",
			"minecraft:cooked_porkchop 1-2 x3", "minecraft:carrot 2-4 x3", "minecraft:baked_potato 1-3 x3", "minecraft:fishing_rod x2",
			"minecraft:stick 1-2 x3", "minecraft:feather 1-3 x2", "minecraft:flint 1-2 x2", "minecraft:iron_ingot x2", "minecraft:snowball 2-6 x2",
			"minecraft:cobweb x1"));
	public List<String> landmarkLoot = new ArrayList<>(List.of(
			"minecraft:stone_sword x3", "minecraft:iron_sword x4", "minecraft:iron_axe x2", "minecraft:bow x3", "minecraft:arrow 6-12 x4",
			"minecraft:chainmail_helmet x3", "minecraft:chainmail_chestplate x3", "minecraft:chainmail_leggings x3", "minecraft:iron_helmet x2",
			"minecraft:iron_boots x2", "minecraft:iron_chestplate x1", "minecraft:cooked_beef 2-4 x4", "minecraft:golden_carrot 1-3 x3",
			"minecraft:golden_apple x2", "minecraft:flint_and_steel x2", "minecraft:fishing_rod x2", "minecraft:crossbow x1",
			"minecraft:shield x2", "minecraft:ender_pearl x1", "minecraft:cobweb 1-2 x2"));
	public List<String> cornucopiaLoot = new ArrayList<>(List.of(
			"minecraft:iron_sword x5", "minecraft:diamond_sword x1", "minecraft:iron_axe x2", "minecraft:bow x3", "minecraft:arrow 8-16 x4",
			"minecraft:iron_helmet x3", "minecraft:iron_chestplate x3", "minecraft:iron_leggings x3", "minecraft:iron_boots x3",
			"minecraft:diamond_helmet x1", "minecraft:diamond_boots x1", "minecraft:golden_apple 1-2 x3", "minecraft:cooked_beef 3-6 x4",
			"minecraft:bread 2-4 x3", "minecraft:ender_pearl x2", "minecraft:shield x2", "minecraft:crossbow x2", "minecraft:tnt 1-2 x1",
			"minecraft:flint_and_steel x1", "minecraft:water_bucket x1", "minecraft:lava_bucket x1"));

	public List<Loot.Entry> house() {
		return parse(houseLoot);
	}

	public List<Loot.Entry> landmark() {
		return parse(landmarkLoot);
	}

	public List<Loot.Entry> cornucopia() {
		return parse(cornucopiaLoot);
	}

	private static List<Loot.Entry> parse(List<String> lines) {
		List<Loot.Entry> out = new ArrayList<>();
		for (String l : lines) {
			if (l != null && !l.isBlank()) out.add(Loot.Entry.parse(l));
		}
		return out;
	}

	public HungerGamesConfig normalize() {
		HungerGamesConfig d = new HungerGamesConfig();
		if (houseLoot == null || houseLoot.isEmpty()) houseLoot = d.houseLoot;
		if (landmarkLoot == null || landmarkLoot.isEmpty()) landmarkLoot = d.landmarkLoot;
		if (cornucopiaLoot == null || cornucopiaLoot.isEmpty()) cornucopiaLoot = d.cornucopiaLoot;
		return this;
	}
}
