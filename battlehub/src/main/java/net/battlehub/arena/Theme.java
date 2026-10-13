package net.battlehub.arena;

import java.util.Locale;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.battlehub.util.Ids;

/**
 * The five arena themes. Each has its own block palette; layouts ask the theme for "floor", "wall", "accent" and
 * so on, and themes add their own decoration (trees, towers, lava, ice spikes, floating islands).
 */
public enum Theme {
	FOREST(
			new String[] {"grass_block", "grass_block", "grass_block", "moss_block", "coarse_dirt", "rooted_dirt"},
			"dirt", "oak_log", "oak_leaves", "oak_planks", "mossy_cobblestone", "lantern", "spruce_log",
			"moss_block", "green_wool"),
	CASTLE(
			new String[] {"stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "polished_andesite", "andesite"},
			"cobblestone", "stone_bricks", "chiseled_stone_bricks", "stone_brick_slab", "cobblestone", "lantern", "polished_andesite",
			"gravel", "red_wool"),
	NETHER(
			new String[] {"netherrack", "netherrack", "crimson_nylium", "netherrack", "soul_soil", "blackstone"},
			"netherrack", "nether_bricks", "red_nether_bricks", "nether_brick_slab", "basalt", "shroomlight", "polished_blackstone_bricks",
			"magma_block", "red_nether_bricks"),
	ICE(
			new String[] {"snow_block", "snow_block", "packed_ice", "snow_block", "packed_ice", "snow_block"},
			"packed_ice", "packed_ice", "blue_ice", "snow_block", "ice", "sea_lantern", "blue_ice",
			"ice", "light_blue_wool"),
	SKY(
			new String[] {"grass_block", "grass_block", "grass_block", "moss_block", "grass_block", "coarse_dirt"},
			"dirt", "stone", "white_wool", "smooth_quartz", "calcite", "end_rod", "quartz_pillar",
			"moss_block", "white_concrete");

	private final String[] floor;
	private final String under;
	private final String wall;
	private final String accent;
	private final String trim;
	private final String rough;
	private final String light;
	private final String pillar;
	private final String soft;
	private final String marker;

	Theme(String[] floor, String under, String wall, String accent, String trim, String rough, String light, String pillar,
			String soft, String marker) {
		this.floor = floor;
		this.under = under;
		this.wall = wall;
		this.accent = accent;
		this.trim = trim;
		this.rough = rough;
		this.light = light;
		this.pillar = pillar;
		this.soft = soft;
		this.marker = marker;
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
		return Ids.state(floor[rnd.nextInt(floor.length)]);
	}

	public BlockState under() { return Ids.state(under); }
	public BlockState wall() { return Ids.state(wall); }
	public BlockState accent() { return Ids.state(accent); }
	public BlockState trim() { return Ids.state(trim); }
	public BlockState rough() { return Ids.state(rough); }
	public BlockState light() { return Ids.state(light); }
	public BlockState pillar() { return Ids.state(pillar); }
	public BlockState soft() { return Ids.state(soft); }
	public BlockState marker() { return Ids.state(marker); }
}
