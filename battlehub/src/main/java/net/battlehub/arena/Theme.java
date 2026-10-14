package net.battlehub.arena;

import java.util.Locale;

import net.battlehub.util.Ids;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Arena themes. Each has its own block palette; the generators ask the theme for "floor", "wall", "accent" and so
 * on, plus trees, plants and the floor that breaks in Bow Spleef and TNT Run. Open themes (Sky, End) have no walls:
 * their arenas are floating islands.
 */
public enum Theme {
	FOREST(new Palette()
			.floor("grass_block", "grass_block", "grass_block", "moss_block", "coarse_dirt", "rooted_dirt")
			.blocks("dirt", "oak_log", "oak_leaves", "oak_planks", "mossy_cobblestone", "lantern", "spruce_log", "moss_block", "green_wool")
			.tree("oak_log", "oak_leaves").plants("short_grass", "short_grass", "short_grass", "fern", "poppy", "dandelion", "cornflower", "azure_bluet")
			.breakable("moss_block", "moss_block", "moss_block", "oak_leaves")),
	CASTLE(new Palette()
			.floor("stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "polished_andesite", "andesite")
			.blocks("cobblestone", "stone_bricks", "chiseled_stone_bricks", "stone_brick_slab", "cobblestone", "lantern", "polished_andesite", "gravel", "red_wool")
			.breakable("smooth_stone", "smooth_stone", "andesite")),
	NETHER(new Palette()
			.floor("netherrack", "netherrack", "crimson_nylium", "netherrack", "soul_soil", "blackstone")
			.blocks("netherrack", "nether_bricks", "red_nether_bricks", "nether_brick_slab", "basalt", "shroomlight", "polished_blackstone_bricks", "magma_block", "red_nether_bricks")
			.tree("crimson_stem", "nether_wart_block").plants("crimson_roots", "crimson_fungus")
			.breakable("netherrack", "netherrack", "soul_soil")),
	ICE(new Palette()
			.floor("snow_block", "snow_block", "packed_ice", "snow_block", "packed_ice", "snow_block")
			.blocks("packed_ice", "packed_ice", "blue_ice", "snow_block", "ice", "sea_lantern", "blue_ice", "ice", "light_blue_wool")
			.tree("spruce_log", "spruce_leaves")
			.breakable("snow_block", "snow_block", "snow_block", "packed_ice")),
	SKY(new Palette()
			.floor("grass_block", "grass_block", "grass_block", "moss_block", "grass_block", "coarse_dirt")
			.blocks("dirt", "stone", "white_wool", "smooth_quartz", "calcite", "end_rod", "quartz_pillar", "moss_block", "white_concrete")
			.tree("oak_log", "oak_leaves").plants("short_grass", "short_grass", "poppy", "dandelion", "oxeye_daisy")
			.breakable("white_wool", "white_wool", "white_wool", "light_gray_wool").open()),
	DESERT(new Palette()
			.floor("sandstone", "smooth_sandstone", "sandstone", "cut_sandstone", "smooth_sandstone", "sandstone")
			.blocks("sandstone", "cut_sandstone", "chiseled_sandstone", "smooth_sandstone_slab", "sandstone", "lantern", "cut_sandstone", "smooth_sandstone", "orange_wool")
			.plants("dead_bush")
			.breakable("smooth_sandstone", "smooth_sandstone", "cut_sandstone")),
	JUNGLE(new Palette()
			.floor("grass_block", "grass_block", "moss_block", "podzol", "coarse_dirt", "grass_block")
			.blocks("dirt", "jungle_log", "jungle_leaves", "jungle_planks", "mossy_cobblestone", "lantern", "jungle_log", "moss_block", "lime_wool")
			.tree("jungle_log", "jungle_leaves").plants("fern", "fern", "short_grass", "short_grass", "blue_orchid")
			.breakable("moss_block", "moss_block", "jungle_leaves")),
	CHERRY(new Palette()
			.floor("grass_block", "grass_block", "grass_block", "moss_block", "grass_block", "coarse_dirt")
			.blocks("dirt", "cherry_log", "cherry_leaves", "cherry_planks", "mossy_stone_bricks", "lantern", "stripped_cherry_log", "moss_block", "pink_wool")
			.tree("cherry_log", "cherry_leaves").plants("pink_petals", "pink_petals", "pink_petals", "short_grass", "lily_of_the_valley")
			.breakable("pink_terracotta", "pink_terracotta", "cherry_planks")),
	END(new Palette()
			.floor("end_stone", "end_stone", "end_stone", "end_stone_bricks", "end_stone", "end_stone_bricks")
			.blocks("end_stone", "purpur_block", "purpur_pillar", "end_stone_brick_slab", "end_stone", "end_rod", "obsidian", "end_stone", "purple_wool")
			.breakable("end_stone", "end_stone", "purpur_block").open()),
	MUSHROOM(new Palette()
			.floor("mycelium", "mycelium", "mycelium", "podzol", "mycelium", "coarse_dirt")
			.blocks("dirt", "mushroom_stem", "red_mushroom_block", "brown_mushroom_block", "cobblestone", "shroomlight", "mushroom_stem", "mycelium", "red_wool")
			.tree("mushroom_stem", "red_mushroom_block").plants("red_mushroom", "brown_mushroom")
			.breakable("brown_mushroom_block", "red_mushroom_block", "brown_mushroom_block")),
	BADLANDS(new Palette()
			.floor("terracotta", "orange_terracotta", "red_terracotta", "yellow_terracotta", "brown_terracotta", "white_terracotta")
			.blocks("red_sandstone", "cut_red_sandstone", "chiseled_red_sandstone", "smooth_red_sandstone_slab", "terracotta", "lantern", "cut_red_sandstone", "red_terracotta", "orange_wool")
			.plants("dead_bush")
			.breakable("orange_terracotta", "red_terracotta", "yellow_terracotta")),
	DEEP_DARK(new Palette()
			.floor("sculk", "sculk", "deepslate_tiles", "cobbled_deepslate", "sculk", "polished_deepslate")
			.blocks("deepslate", "deepslate_bricks", "chiseled_deepslate", "polished_deepslate_slab", "cobbled_deepslate", "soul_lantern", "polished_deepslate", "sculk", "cyan_wool")
			.breakable("sculk", "sculk", "deepslate_tiles")),
	OCEAN(new Palette()
			.floor("prismarine_bricks", "prismarine_bricks", "prismarine", "dark_prismarine", "prismarine_bricks", "prismarine")
			.blocks("prismarine", "dark_prismarine", "sea_lantern", "prismarine_brick_slab", "prismarine", "sea_lantern", "prismarine_bricks", "prismarine", "light_blue_wool")
			.breakable("prismarine", "prismarine", "dark_prismarine"));

	/** The block names of a theme, filled in with a few chained calls. */
	static final class Palette {
		String[] floor = {"stone"};
		String under = "stone";
		String wall = "stone";
		String accent = "stone";
		String trim = "stone";
		String rough = "stone";
		String light = "lantern";
		String pillar = "stone";
		String soft = "stone";
		String marker = "white_wool";
		String log = "";
		String leaves = "";
		String[] plants = {};
		String[] breakable = {"stone"};
		boolean open;

		Palette floor(String... ids) {
			floor = ids;
			return this;
		}

		Palette blocks(String under, String wall, String accent, String trim, String rough, String light, String pillar, String soft, String marker) {
			this.under = under;
			this.wall = wall;
			this.accent = accent;
			this.trim = trim;
			this.rough = rough;
			this.light = light;
			this.pillar = pillar;
			this.soft = soft;
			this.marker = marker;
			return this;
		}

		Palette tree(String log, String leaves) {
			this.log = log;
			this.leaves = leaves;
			return this;
		}

		Palette plants(String... ids) {
			plants = ids;
			return this;
		}

		Palette breakable(String... ids) {
			breakable = ids;
			return this;
		}

		Palette open() {
			open = true;
			return this;
		}
	}

	private final Palette p;

	Theme(Palette p) {
		this.p = p;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Theme byId(String id) {
		for (Theme t : values()) {
			if (t.id().equalsIgnoreCase(id)) {
				return t;
			}
		}
		return null;
	}

	/** A random floor block from the theme mix. */
	public BlockState floor(RandomSource rnd) {
		return Ids.state(p.floor[rnd.nextInt(p.floor.length)]);
	}

	public BlockState under() { return Ids.state(p.under); }
	public BlockState wall() { return Ids.state(p.wall); }
	public BlockState accent() { return Ids.state(p.accent); }
	public BlockState trim() { return Ids.state(p.trim); }
	public BlockState rough() { return Ids.state(p.rough); }
	public BlockState light() { return Ids.state(p.light); }
	public BlockState pillar() { return Ids.state(p.pillar); }
	public BlockState soft() { return Ids.state(p.soft); }
	public BlockState marker() { return Ids.state(p.marker); }

	/** No walls; arenas are floating islands. */
	public boolean open() {
		return p.open;
	}

	public boolean hasTrees() {
		return !p.log.isEmpty();
	}

	public BlockState log() {
		return Ids.state(p.log.isEmpty() ? "oak_log" : p.log);
	}

	/** Leaves that never decay. */
	public BlockState leaves() {
		String id = p.leaves.isEmpty() ? "oak_leaves" : p.leaves;
		return id.endsWith("_leaves") ? Build.leaves(id) : Ids.state(id);
	}

	/** A random plant for the floor, or null when the theme has none. */
	public BlockState plant(RandomSource rnd) {
		return p.plants.length == 0 ? null : Ids.state(p.plants[rnd.nextInt(p.plants.length)]);
	}

	/** Block that disappears in Bow Spleef and TNT Run. */
	public BlockState breakable(RandomSource rnd) {
		return Ids.state(p.breakable[rnd.nextInt(p.breakable.length)]);
	}
}
