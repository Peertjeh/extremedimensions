package net.battlehub.arch.town;

import net.battlehub.arch.Slot;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mat.Ramp;

/**
 * The palette hierarchy of the lobby: one dimension palette (ground, paving, lights, glass, the usual roof) and a
 * palette per district that changes what gives each district its character (roofs, paving, house stone). Town
 * houses between two districts pick between their palettes by how close they stand to each, so the city shifts
 * gradually from one quarter to the next.
 */
public final class Regions {
	private Regions() {}

	public static Palette dimension() {
		Palette p = new Palette("lobby", null);
		p.set(Slot.GROUND, Ramp.of(0.3, "grass_block*6", "moss_block", "grass_block*3", "coarse_dirt"));
		p.set(Slot.SOIL, Ramp.of(0.2, "dirt*3", "coarse_dirt", "rooted_dirt"));
		p.set(Slot.ROCK, Ramp.of(0.18, "deepslate", "cobbled_deepslate", "tuff*2", "andesite", "stone*2", "dripstone_block"));
		p.set(Slot.PATH, Ramp.of(0.25, "stone_bricks*3", "polished_andesite*2", "andesite"));
		p.set(Slot.PAVING, Ramp.of(0.25, "polished_andesite*2", "stone_bricks*2", "smooth_stone"));
		p.set(Slot.ROOF, Ramp.of(0.2, "dark_oak_planks*2", "spruce_planks"));
		p.set(Slot.LEAVES, Ramp.of(0.3, "oak_leaves*3", "azalea_leaves", "flowering_azalea_leaves"));
		p.set(Slot.RUBBLE, Ramp.of("cobblestone", "gravel", "mossy_cobblestone"));
		p.set(Slot.METAL, Ramp.of("iron_bars"));
		p.set(Slot.LIGHT, Ramp.of("glowstone"));
		p.set(Slot.MAGIC, Ramp.of("amethyst_block"));
		p.set(Slot.ACCENT, Ramp.of("gold_block"));
		p.set(Slot.GLASS, Ramp.of("glass"));
		p.set(Slot.WOOD, Ramp.of("spruce_planks"));
		p.set(Slot.TIMBER, Ramp.of("spruce_log"));
		p.set(Slot.TRIM, Ramp.of("polished_andesite"));
		p.set(Slot.WALL, Ramp.of("stone_bricks"));
		p.set(Slot.FOUNDATION, Ramp.of("cobblestone", "stone_bricks"));
		p.set(Slot.FLOOR, Ramp.of("spruce_planks"));
		p.set(Slot.STAINED, Ramp.of(0, "blue_stained_glass", "light_blue_stained_glass", "purple_stained_glass", "cyan_stained_glass", "red_stained_glass",
				"yellow_stained_glass"));
		return p;
	}

	/** The four districts, in the order north, east, south, west. */
	public static Palette[] districts(Palette dim) {
		Palette north = dim.child("north");
		north.set(Slot.ROOF, Ramp.of(0.2, "deepslate_tiles*3", "polished_deepslate"));
		north.set(Slot.ROOF_EDGE, Ramp.of("polished_blackstone"));
		north.set(Slot.PATH, Ramp.of(0.25, "polished_andesite*2", "stone_bricks*2", "polished_tuff"));
		north.set(Slot.TIMBER, Ramp.of("dark_oak_log"));
		Palette east = dim.child("east");
		east.set(Slot.ROOF, Ramp.of(0.2, "dark_oak_planks*2", "spruce_planks"));
		east.set(Slot.PATH, Ramp.of(0.25, "cobblestone*2", "stone_bricks*2", "andesite"));
		east.set(Slot.TIMBER, Ramp.of("spruce_log"));
		Palette south = dim.child("south");
		south.set(Slot.ROOF, Ramp.of(0.2, "bricks*3", "mud_bricks"));
		south.set(Slot.ROOF_EDGE, Ramp.of("granite"));
		south.set(Slot.PATH, Ramp.of(0.25, "smooth_sandstone*2", "cut_sandstone", "sandstone"));
		south.set(Slot.PAVING, Ramp.of(0.25, "smooth_sandstone*2", "sandstone"));
		south.set(Slot.TIMBER, Ramp.of("stripped_oak_log"));
		Palette west = dim.child("west");
		west.set(Slot.ROOF, Ramp.of(0.2, "waxed_oxidized_cut_copper*3", "waxed_weathered_cut_copper"));
		west.set(Slot.ROOF_EDGE, Ramp.of("polished_deepslate"));
		west.set(Slot.PATH, Ramp.of(0.25, "polished_tuff*2", "tuff_bricks*2", "polished_deepslate"));
		west.set(Slot.TIMBER, Ramp.of("dark_oak_log"));
		return new Palette[] {north, east, south, west};
	}
}
