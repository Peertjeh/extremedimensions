package net.battlehub.arena.gen;

import java.util.List;
import java.util.Map;

import net.battlehub.arena.Theme;

/**
 * Colour gradients per theme: blocks that fade into each other, from one end to the other. Floors use them with a
 * pattern (clouds, bands, rings, patches, ...); walls fade from their foot to their top. Waxed copper does not
 * weather, so its gradient stays as it is.
 */
final class Palettes {
	private Palettes() {}

	private static String[] g(String... ids) {
		return ids;
	}

	static final Map<Theme, List<String[]>> FLOORS = Map.ofEntries(
			Map.entry(Theme.FOREST, List.of(
					g("podzol", "coarse_dirt", "rooted_dirt", "grass_block", "moss_block"),
					g("packed_mud", "coarse_dirt", "grass_block", "grass_block", "moss_block"),
					g("moss_block", "grass_block", "grass_block", "mossy_cobblestone", "cobblestone"))),
			Map.entry(Theme.CASTLE, List.of(
					g("cobblestone", "andesite", "stone", "stone_bricks", "polished_andesite", "smooth_stone"),
					g("mossy_cobblestone", "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "chiseled_stone_bricks"),
					g("tuff", "tuff_bricks", "polished_tuff", "chiseled_tuff", "chiseled_tuff_bricks"),
					g("deepslate_bricks", "cobbled_deepslate", "cobblestone", "stone_bricks", "polished_andesite"))),
			Map.entry(Theme.NETHER, List.of(
					g("netherrack", "crimson_nylium", "nether_wart_block", "red_nether_bricks"),
					g("soul_soil", "blackstone", "polished_blackstone", "polished_blackstone_bricks", "gilded_blackstone"),
					g("warped_nylium", "warped_wart_block", "nether_bricks", "netherrack"),
					g("nether_bricks", "red_nether_bricks", "crimson_planks", "crimson_nylium"))),
			Map.entry(Theme.ICE, List.of(
					g("blue_ice", "packed_ice", "snow_block", "snow_block", "calcite"),
					g("light_blue_concrete", "blue_ice", "packed_ice", "snow_block"),
					g("snow_block", "white_concrete", "calcite", "diorite", "polished_diorite"))),
			Map.entry(Theme.SKY, List.of(
					g("coarse_dirt", "grass_block", "grass_block", "moss_block"),
					g("calcite", "white_concrete", "smooth_quartz", "quartz_bricks"),
					g("pale_moss_block", "moss_block", "grass_block", "grass_block"))),
			Map.entry(Theme.DESERT, List.of(
					g("smooth_sandstone", "sandstone", "cut_sandstone", "chiseled_sandstone"),
					g("sand", "sandstone", "smooth_sandstone", "cut_sandstone"),
					g("terracotta", "orange_terracotta", "yellow_terracotta", "smooth_sandstone", "sandstone"))),
			Map.entry(Theme.JUNGLE, List.of(
					g("podzol", "coarse_dirt", "grass_block", "moss_block"),
					g("mossy_cobblestone", "mossy_stone_bricks", "moss_block", "grass_block"),
					g("packed_mud", "mud_bricks", "jungle_planks", "stripped_jungle_wood"))),
			Map.entry(Theme.CHERRY, List.of(
					g("pink_terracotta", "cherry_planks", "stripped_cherry_wood", "pink_wool"),
					g("moss_block", "grass_block", "grass_block", "pale_moss_block"),
					g("calcite", "pink_concrete", "pink_terracotta", "cherry_planks"))),
			Map.entry(Theme.END, List.of(
					g("end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar"),
					g("obsidian", "crying_obsidian", "purpur_block", "end_stone_bricks"),
					g("end_stone", "end_stone", "end_stone_bricks", "calcite"))),
			Map.entry(Theme.MUSHROOM, List.of(
					g("mycelium", "podzol", "coarse_dirt", "packed_mud"),
					g("brown_mushroom_block", "mushroom_stem", "red_mushroom_block"))),
			Map.entry(Theme.BADLANDS, List.of(
					g("white_terracotta", "yellow_terracotta", "orange_terracotta", "red_terracotta", "brown_terracotta", "terracotta"),
					g("red_sandstone", "smooth_red_sandstone", "cut_red_sandstone", "orange_terracotta"),
					g("terracotta", "light_gray_terracotta", "white_terracotta", "yellow_terracotta"))),
			Map.entry(Theme.DEEP_DARK, List.of(
					g("sculk", "cobbled_deepslate", "deepslate_tiles", "polished_deepslate", "deepslate_bricks"),
					g("deepslate_tiles", "cracked_deepslate_tiles", "deepslate_bricks", "chiseled_deepslate"),
					g("tuff", "polished_tuff", "deepslate", "smooth_basalt"))),
			Map.entry(Theme.OCEAN, List.of(
					g("dark_prismarine", "prismarine", "prismarine_bricks", "warped_wart_block"),
					g("waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper"),
					g("cyan_terracotta", "prismarine", "prismarine_bricks", "light_blue_concrete"))));

	static final Map<Theme, List<String[]>> WALLS = Map.ofEntries(
			Map.entry(Theme.CASTLE, List.of(
					g("cobblestone", "stone_bricks", "stone_bricks", "polished_andesite"),
					g("mossy_cobblestone", "mossy_stone_bricks", "stone_bricks", "chiseled_stone_bricks"),
					g("tuff", "tuff_bricks", "tuff_bricks", "polished_tuff"))),
			Map.entry(Theme.NETHER, List.of(
					g("nether_bricks", "nether_bricks", "red_nether_bricks", "nether_wart_block"),
					g("blackstone", "polished_blackstone_bricks", "polished_blackstone_bricks", "gilded_blackstone"))),
			Map.entry(Theme.ICE, List.of(
					g("blue_ice", "packed_ice", "packed_ice", "snow_block"),
					g("packed_ice", "ice", "ice", "white_concrete"))),
			Map.entry(Theme.DESERT, List.of(
					g("sandstone", "cut_sandstone", "cut_sandstone", "chiseled_sandstone"),
					g("smooth_sandstone", "sandstone", "cut_sandstone", "smooth_sandstone"))),
			Map.entry(Theme.END, List.of(
					g("end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar"),
					g("obsidian", "end_stone_bricks", "end_stone_bricks", "purpur_block"))),
			Map.entry(Theme.DEEP_DARK, List.of(
					g("cobbled_deepslate", "deepslate_bricks", "deepslate_tiles", "polished_deepslate"),
					g("tuff", "deepslate_bricks", "deepslate_bricks", "chiseled_deepslate"))),
			Map.entry(Theme.OCEAN, List.of(
					g("dark_prismarine", "prismarine_bricks", "prismarine_bricks", "prismarine"),
					g("waxed_oxidized_copper", "waxed_weathered_copper", "waxed_exposed_copper", "waxed_copper_block"))),
			Map.entry(Theme.SKY, List.of(
					g("calcite", "smooth_quartz", "quartz_bricks", "chiseled_quartz_block"),
					g("stone", "diorite", "calcite", "white_concrete"))));

	static List<String[]> floors(Theme t) {
		return FLOORS.getOrDefault(t, List.of());
	}

	static List<String[]> walls(Theme t) {
		return WALLS.getOrDefault(t, List.of());
	}
}
