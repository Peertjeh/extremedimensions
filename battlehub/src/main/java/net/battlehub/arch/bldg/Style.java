package net.battlehub.arch.bldg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.geom.ArchCurve;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mat.Ramp;

/**
 * An architectural style: its tags (which modules suit it), its proportions (storey height, bay rhythm, wall
 * thickness, roof pitch, arch form, how much ornament) and the palettes it builds in. A style describes a
 * tradition; a {@link Brief} picks one building out of it.
 */
public final class Style {
	public final String name;
	private final Set<String> tags;
	public final int storey;
	public final int bay;
	public final int wall;
	public final ArchCurve arch;
	/** Roof rise per block of run: 1 is 45 degrees, 2 steep gothic. */
	public final int pitch;
	/** 0..1: how much ornament the style carries. */
	public final double ornament;
	private final Map<String, Double> prefs = new HashMap<>();
	private final List<PaletteRecipe> palettes;

	/** A named palette for this style, filled in on top of the district's palette. */
	public record PaletteRecipe(String name, BiConsumer<Palette, Rng> fill) {}

	public Style(String name, Set<String> tags, int storey, int bay, int wall, ArchCurve arch, int pitch, double ornament,
			List<PaletteRecipe> palettes) {
		this.name = name;
		this.tags = Set.copyOf(tags);
		this.storey = storey;
		this.bay = bay;
		this.wall = wall;
		this.arch = arch;
		this.pitch = pitch;
		this.ornament = ornament;
		this.palettes = List.copyOf(palettes);
	}

	public boolean has(String tag) {
		return tags.contains(tag);
	}

	public Set<String> tags() {
		return tags;
	}

	/** How much more (or less) this style likes a module than others of its category. */
	public double preference(String moduleId) {
		return prefs.getOrDefault(moduleId, 1.0);
	}

	public Style prefer(String moduleId, double factor) {
		prefs.put(moduleId, factor);
		return this;
	}

	public List<PaletteRecipe> palettes() {
		return palettes;
	}

	/** A building palette: one of the style's recipes, as a child of {@code district}. */
	public Palette palette(Palette district, Rng rng) {
		PaletteRecipe r = palettes.get(rng.nextInt(palettes.size()));
		Palette p = district.child(name + ":" + r.name());
		r.fill().accept(p, rng);
		return p;
	}

	public Palette palette(Palette district, String recipe, Rng rng) {
		for (PaletteRecipe r : palettes) {
			if (r.name().equals(recipe)) {
				Palette p = district.child(name + ":" + r.name());
				r.fill().accept(p, rng);
				return p;
			}
		}
		return palette(district, rng);
	}

	private static PaletteRecipe recipe(String name, BiConsumer<Palette, Rng> fill) {
		return new PaletteRecipe(name, fill);
	}

	/** Stained glass in a fixed order, so modules can compose with "colour 1, colour 2...". */
	private static void glass(Palette p, String... colours) {
		String[] ids = new String[colours.length];
		for (int i = 0; i < colours.length; i++) ids[i] = colours[i] + "_stained_glass";
		p.set(Slot.STAINED, Ramp.of(0, ids));
	}

	// ------------------------------------------------------------------ the built-in styles

	public static final Style GOTHIC = new Style("gothic", Set.of("gothic", "sacred", "tall"), 6, 6, 2, ArchCurve.POINTED, 2, 0.8, List.of(
			recipe("verdigris", (p, r) -> {
				p.set(Slot.FOUNDATION, "cobbled_deepslate", "deepslate_bricks*2", "polished_deepslate");
				p.set(Slot.WALL, Ramp.of(0.14, "deepslate_bricks", "tuff_bricks*2", "stone_bricks*3", "polished_andesite*2"));
				p.set(Slot.WALL_ALT, "polished_tuff", "tuff_bricks");
				p.set(Slot.BAND, "polished_deepslate");
				p.set(Slot.QUOIN, "tuff_bricks", "polished_andesite", "polished_diorite");
				p.set(Slot.PILLAR, "polished_andesite", "polished_diorite", "calcite");
				p.set(Slot.TRIM, "polished_andesite", "polished_diorite");
				p.set(Slot.ARCH, "polished_andesite", "polished_diorite");
				p.set(Slot.ROOF, Ramp.of(0.2, "waxed_oxidized_cut_copper*3", "waxed_weathered_cut_copper"));
				p.set(Slot.ROOF_EDGE, "polished_deepslate");
				glass(p, "blue", "light_blue", "purple", "cyan", "red", "yellow", "orange", "magenta", "white");
				p.set(Slot.FLOOR, "polished_andesite");
				p.set(Slot.FLOOR_ALT, "polished_diorite");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.WOOD, "dark_oak_planks");
				p.set(Slot.TIMBER, "dark_oak_log");
				p.set(Slot.LIGHT, "ochre_froglight");
			}),
			recipe("limestone", (p, r) -> {
				p.set(Slot.FOUNDATION, "andesite", "stone_bricks*2");
				p.set(Slot.WALL, Ramp.of(0.14, "stone_bricks*2", "polished_andesite*2", "polished_diorite*2", "calcite"));
				p.set(Slot.WALL_ALT, "polished_andesite", "polished_diorite");
				p.set(Slot.BAND, "stone_bricks");
				p.set(Slot.QUOIN, "polished_diorite", "calcite");
				p.set(Slot.PILLAR, "polished_diorite", "smooth_quartz");
				p.set(Slot.TRIM, "polished_diorite", "calcite");
				p.set(Slot.ARCH, "polished_diorite", "calcite");
				p.set(Slot.ROOF, Ramp.of(0.2, "deepslate_tiles*3", "polished_deepslate"));
				p.set(Slot.ROOF_EDGE, "polished_blackstone");
				glass(p, "red", "orange", "yellow", "blue", "purple", "light_blue", "magenta", "lime", "white");
				p.set(Slot.FLOOR, "polished_diorite");
				p.set(Slot.FLOOR_ALT, "polished_andesite");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.WOOD, "spruce_planks");
				p.set(Slot.TIMBER, "spruce_log");
				p.set(Slot.LIGHT, "shroomlight");
			}),
			recipe("obsidian", (p, r) -> {
				p.set(Slot.FOUNDATION, "blackstone", "polished_blackstone_bricks");
				p.set(Slot.WALL, Ramp.of(0.14, "polished_blackstone_bricks*3", "deepslate_bricks*2", "deepslate_tiles"));
				p.set(Slot.WALL_ALT, "polished_blackstone", "deepslate_tiles");
				p.set(Slot.BAND, "deepslate_tiles");
				p.set(Slot.QUOIN, "polished_deepslate", "polished_tuff");
				p.set(Slot.PILLAR, "polished_andesite", "polished_diorite");
				p.set(Slot.TRIM, "polished_deepslate", "polished_andesite");
				p.set(Slot.ARCH, "polished_andesite", "polished_diorite");
				p.set(Slot.ROOF, Ramp.of(0.2, "waxed_oxidized_cut_copper*3", "waxed_weathered_cut_copper"));
				p.set(Slot.ROOF_EDGE, "polished_blackstone");
				glass(p, "purple", "magenta", "red", "blue", "pink", "light_blue", "orange", "yellow", "white");
				p.set(Slot.FLOOR, "polished_blackstone");
				p.set(Slot.FLOOR_ALT, "polished_diorite");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.WOOD, "dark_oak_planks");
				p.set(Slot.TIMBER, "dark_oak_log");
				p.set(Slot.LIGHT, "pearlescent_froglight");
			})));

	public static final Style FORTRESS = new Style("fortress", Set.of("castle", "military", "heavy"), 5, 6, 2, ArchCurve.ROUND, 1, 0.45, List.of(
			recipe("granite", (p, r) -> {
				p.set(Slot.FOUNDATION, "cobblestone", "andesite", "stone_bricks");
				p.set(Slot.WALL, Ramp.of(0.16, "cobblestone", "stone_bricks*4", "andesite", "polished_andesite"));
				p.set(Slot.WALL_ALT, "andesite", "stone_bricks");
				p.set(Slot.BAND, "polished_andesite");
				p.set(Slot.QUOIN, "polished_andesite", "stone_bricks");
				p.set(Slot.PILLAR, "polished_andesite");
				p.set(Slot.TRIM, "polished_andesite", "smooth_stone");
				p.set(Slot.ARCH, "stone_bricks", "polished_andesite");
				p.set(Slot.ROOF, Ramp.of(0.2, "dark_oak_planks*2", "spruce_planks"));
				p.set(Slot.ROOF_EDGE, "spruce_planks");
				p.set(Slot.WOOD, "spruce_planks");
				p.set(Slot.TIMBER, "dark_oak_log");
				p.set(Slot.FLOOR, "spruce_planks");
				p.set(Slot.FLOOR_ALT, "stone_bricks");
				glass(p, "light_gray", "gray", "white");
			}),
			recipe("sandstone", (p, r) -> {
				p.set(Slot.FOUNDATION, "sandstone", "cut_sandstone");
				p.set(Slot.WALL, Ramp.of(0.16, "sandstone*2", "cut_sandstone*2", "smooth_sandstone*2"));
				p.set(Slot.WALL_ALT, "smooth_sandstone");
				p.set(Slot.BAND, "cut_red_sandstone");
				p.set(Slot.QUOIN, "smooth_sandstone");
				p.set(Slot.PILLAR, "smooth_sandstone");
				p.set(Slot.TRIM, "smooth_sandstone", "chiseled_sandstone");
				p.set(Slot.ARCH, "cut_sandstone", "smooth_sandstone");
				p.set(Slot.ROOF, Ramp.of(0.2, "smooth_red_sandstone*2", "red_sandstone"));
				p.set(Slot.ROOF_EDGE, "red_sandstone");
				p.set(Slot.WOOD, "jungle_planks");
				p.set(Slot.TIMBER, "stripped_oak_log");
				p.set(Slot.FLOOR, "smooth_sandstone");
				p.set(Slot.FLOOR_ALT, "cut_red_sandstone");
				glass(p, "orange", "yellow", "white");
			}),
			recipe("darkkeep", (p, r) -> {
				p.set(Slot.FOUNDATION, "cobbled_deepslate", "deepslate_bricks");
				p.set(Slot.WALL, Ramp.of(0.16, "cobbled_deepslate", "deepslate_bricks*3", "polished_deepslate", "tuff_bricks"));
				p.set(Slot.WALL_ALT, "polished_deepslate");
				p.set(Slot.BAND, "polished_tuff");
				p.set(Slot.QUOIN, "polished_tuff", "tuff_bricks");
				p.set(Slot.PILLAR, "polished_tuff");
				p.set(Slot.TRIM, "polished_tuff", "tuff_bricks");
				p.set(Slot.ARCH, "tuff_bricks", "polished_tuff");
				p.set(Slot.ROOF, Ramp.of(0.2, "deepslate_tiles*3", "polished_deepslate"));
				p.set(Slot.ROOF_EDGE, "polished_blackstone");
				p.set(Slot.WOOD, "dark_oak_planks");
				p.set(Slot.TIMBER, "dark_oak_log");
				p.set(Slot.FLOOR, "dark_oak_planks");
				p.set(Slot.FLOOR_ALT, "polished_deepslate");
				glass(p, "red", "gray", "black");
			}),
			recipe("brick", (p, r) -> {
				p.set(Slot.FOUNDATION, "cobblestone", "stone_bricks");
				p.set(Slot.WALL, Ramp.of(0.16, "stone_bricks", "bricks*4", "polished_granite"));
				p.set(Slot.WALL_ALT, "bricks");
				p.set(Slot.BAND, "stone_bricks");
				p.set(Slot.QUOIN, "stone_bricks", "polished_andesite");
				p.set(Slot.PILLAR, "polished_andesite");
				p.set(Slot.TRIM, "polished_andesite", "stone_bricks");
				p.set(Slot.ARCH, "stone_bricks");
				p.set(Slot.ROOF, Ramp.of(0.2, "deepslate_tiles*2", "polished_deepslate"));
				p.set(Slot.ROOF_EDGE, "polished_blackstone");
				p.set(Slot.WOOD, "spruce_planks");
				p.set(Slot.TIMBER, "spruce_log");
				p.set(Slot.FLOOR, "spruce_planks");
				p.set(Slot.FLOOR_ALT, "bricks");
				glass(p, "white", "light_gray");
			})));

	public static final Style ARCANE = new Style("arcane", Set.of("arcane", "magic", "tall"), 6, 5, 2, ArchCurve.POINTED, 2, 0.75, List.of(
			recipe("amethyst", (p, r) -> {
				p.set(Slot.FOUNDATION, "polished_blackstone_bricks", "deepslate_bricks");
				p.set(Slot.WALL, Ramp.of(0.14, "deepslate_bricks*2", "polished_deepslate", "tuff_bricks*2", "polished_tuff", "polished_andesite", "polished_diorite", "calcite"));
				p.set(Slot.WALL_ALT, "polished_tuff", "polished_andesite", "polished_diorite");
				p.set(Slot.BAND, "polished_deepslate");
				p.set(Slot.QUOIN, "polished_tuff", "polished_andesite", "polished_diorite");
				p.set(Slot.PILLAR, "polished_tuff", "polished_andesite", "polished_diorite");
				p.set(Slot.TRIM, "calcite", "polished_diorite");
				p.set(Slot.ARCH, "polished_tuff", "polished_andesite", "polished_diorite");
				p.set(Slot.ROOF, Ramp.of(0.18, "waxed_oxidized_cut_copper*2", "waxed_weathered_cut_copper*2", "waxed_exposed_cut_copper", "waxed_cut_copper"));
				p.set(Slot.ROOF_EDGE, "polished_deepslate");
				p.set(Slot.MAGIC, "amethyst_block");
				p.set(Slot.LIGHT, "pearlescent_froglight");
				glass(p, "purple", "magenta", "light_blue", "blue", "pink", "cyan", "white");
				p.set(Slot.FLOOR, "polished_deepslate");
				p.set(Slot.FLOOR_ALT, "calcite");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.WOOD, "dark_oak_planks");
				p.set(Slot.TIMBER, "dark_oak_log");
			}),
			recipe("endstone", (p, r) -> {
				p.set(Slot.FOUNDATION, "obsidian", "polished_blackstone_bricks");
				p.set(Slot.WALL, Ramp.of(0.14, "end_stone_bricks*4", "end_stone"));
				p.set(Slot.WALL_ALT, "purpur_block");
				p.set(Slot.BAND, "purpur_block");
				p.set(Slot.QUOIN, "purpur_block");
				p.set(Slot.PILLAR, "purpur_pillar");
				p.set(Slot.TRIM, "purpur_block");
				p.set(Slot.ARCH, "purpur_block");
				p.set(Slot.ROOF, Ramp.of(0.18, "purpur_block"));
				p.set(Slot.ROOF_EDGE, "obsidian");
				p.set(Slot.MAGIC, "amethyst_block", "crying_obsidian");
				p.set(Slot.LIGHT, "pearlescent_froglight");
				glass(p, "magenta", "purple", "pink", "white");
				p.set(Slot.FLOOR, "purpur_block");
				p.set(Slot.FLOOR_ALT, "end_stone_bricks");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.WOOD, "crimson_planks");
				p.set(Slot.TIMBER, "dark_oak_log");
			}),
			recipe("tidal", (p, r) -> {
				p.set(Slot.FOUNDATION, "dark_prismarine");
				p.set(Slot.WALL, Ramp.of(0.14, "dark_prismarine*2", "prismarine*2", "prismarine_bricks*2"));
				p.set(Slot.WALL_ALT, "prismarine_bricks");
				p.set(Slot.BAND, "dark_prismarine");
				p.set(Slot.QUOIN, "prismarine_bricks");
				p.set(Slot.PILLAR, "prismarine_bricks", "smooth_quartz");
				p.set(Slot.TRIM, "smooth_quartz");
				p.set(Slot.ARCH, "prismarine_bricks", "smooth_quartz");
				p.set(Slot.ROOF, Ramp.of(0.18, "dark_prismarine*2", "prismarine_bricks"));
				p.set(Slot.ROOF_EDGE, "dark_prismarine");
				p.set(Slot.MAGIC, "sea_lantern");
				p.set(Slot.LIGHT, "sea_lantern");
				glass(p, "cyan", "light_blue", "blue", "lime", "white");
				p.set(Slot.FLOOR, "dark_prismarine");
				p.set(Slot.FLOOR_ALT, "prismarine_bricks");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.WOOD, "warped_planks");
				p.set(Slot.TIMBER, "stripped_dark_oak_log");
			})));

	public static final Style ANCIENT = new Style("ancient", Set.of("ancient", "classical", "sacred"), 6, 4, 1, ArchCurve.ROUND, 1, 0.55, List.of(
			recipe("desert", (p, r) -> {
				p.set(Slot.FOUNDATION, "sandstone", "cut_sandstone");
				p.set(Slot.WALL, Ramp.of(0.16, "sandstone*2", "cut_sandstone*2", "smooth_sandstone*2"));
				p.set(Slot.WALL_ALT, "smooth_sandstone");
				p.set(Slot.BAND, "cut_red_sandstone");
				p.set(Slot.QUOIN, "smooth_sandstone");
				p.set(Slot.PILLAR, "smooth_sandstone", "cut_sandstone");
				p.set(Slot.TRIM, "smooth_sandstone", "chiseled_sandstone");
				p.set(Slot.ARCH, "cut_sandstone");
				p.set(Slot.ROOF, Ramp.of(0.2, "smooth_red_sandstone*2", "red_sandstone"));
				p.set(Slot.ROOF_EDGE, "cut_red_sandstone");
				p.set(Slot.FLOOR, "smooth_sandstone");
				p.set(Slot.FLOOR_ALT, "cut_red_sandstone");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.RUBBLE, "sandstone", "sand");
				glass(p, "orange", "yellow", "white");
			}),
			recipe("marble", (p, r) -> {
				p.set(Slot.FOUNDATION, "polished_andesite", "stone_bricks");
				p.set(Slot.WALL, Ramp.of(0.16, "polished_diorite*2", "quartz_bricks*3", "calcite*2"));
				p.set(Slot.WALL_ALT, "calcite");
				p.set(Slot.BAND, "polished_andesite");
				p.set(Slot.QUOIN, "smooth_quartz");
				p.set(Slot.PILLAR, "quartz_pillar");
				p.set(Slot.TRIM, "smooth_quartz", "chiseled_quartz_block");
				p.set(Slot.ARCH, "smooth_quartz");
				p.set(Slot.ROOF, Ramp.of(0.2, "bricks*2", "polished_granite"));
				p.set(Slot.ROOF_EDGE, "polished_granite");
				p.set(Slot.FLOOR, "polished_diorite");
				p.set(Slot.FLOOR_ALT, "polished_andesite");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.RUBBLE, "diorite", "calcite", "gravel");
				glass(p, "white", "light_blue", "yellow");
			}),
			recipe("overgrown", (p, r) -> {
				p.set(Slot.FOUNDATION, "mossy_cobblestone", "cobblestone");
				p.set(Slot.WALL, Ramp.of(0.16, "mossy_stone_bricks", "stone_bricks*3", "andesite"));
				p.set(Slot.WALL_ALT, "andesite");
				p.set(Slot.BAND, "polished_andesite");
				p.set(Slot.QUOIN, "polished_andesite");
				p.set(Slot.PILLAR, "polished_andesite", "stone_bricks");
				p.set(Slot.TRIM, "polished_andesite", "chiseled_stone_bricks");
				p.set(Slot.ARCH, "stone_bricks");
				p.set(Slot.ROOF, Ramp.of(0.2, "mossy_cobblestone", "cobblestone"));
				p.set(Slot.ROOF_EDGE, "mossy_cobblestone");
				p.set(Slot.FLOOR, "stone_bricks");
				p.set(Slot.FLOOR_ALT, "mossy_stone_bricks");
				p.set(Slot.ACCENT, "gold_block");
				p.set(Slot.RUBBLE, "cobblestone", "mossy_cobblestone", "gravel");
				glass(p, "lime", "green", "white");
			})));

	public static final Style TOWN = new Style("town", Set.of("town", "rustic", "timber"), 4, 4, 1, ArchCurve.ROUND, 1, 0.5, List.of(
			recipe("timber", (p, r) -> {
				p.set(Slot.FOUNDATION, "cobblestone", "stone_bricks");
				p.set(Slot.WALL, Ramp.of(0.16, "stone_bricks*2", "andesite"));
				p.set(Slot.WALL_ALT, r.pick("white_terracotta", "calcite", "mushroom_stem", "pale_oak_planks"));
				p.set(Slot.TIMBER, r.pick("dark_oak_log", "spruce_log", "stripped_dark_oak_log"));
				p.set(Slot.WOOD, r.pick("spruce_planks", "dark_oak_planks", "oak_planks"));
				p.set(Slot.TRIM, "spruce_planks");
				p.set(Slot.FLOOR, "spruce_planks");
				p.set(Slot.FLOOR_ALT, "oak_planks");
				glass(p, "white", "light_gray");
			}),
			recipe("brick", (p, r) -> {
				p.set(Slot.FOUNDATION, "stone_bricks", "cobblestone");
				p.set(Slot.WALL, Ramp.of(0.16, "bricks*3", "mud_bricks"));
				p.set(Slot.WALL_ALT, r.pick("white_terracotta", "bricks"));
				p.set(Slot.TIMBER, r.pick("spruce_log", "dark_oak_log"));
				p.set(Slot.WOOD, "spruce_planks");
				p.set(Slot.TRIM, "stone_bricks");
				p.set(Slot.FLOOR, "spruce_planks");
				p.set(Slot.FLOOR_ALT, "bricks");
				glass(p, "white", "light_gray");
			}),
			recipe("stone", (p, r) -> {
				p.set(Slot.FOUNDATION, "cobblestone", "mossy_cobblestone");
				p.set(Slot.WALL, Ramp.of(0.16, "cobblestone", "stone_bricks*3", "andesite"));
				p.set(Slot.WALL_ALT, r.pick("calcite", "white_terracotta", "stone_bricks"));
				p.set(Slot.TIMBER, r.pick("oak_log", "spruce_log"));
				p.set(Slot.WOOD, r.pick("oak_planks", "spruce_planks"));
				p.set(Slot.TRIM, "polished_andesite");
				p.set(Slot.FLOOR, "oak_planks");
				p.set(Slot.FLOOR_ALT, "spruce_planks");
				glass(p, "white", "light_gray");
			})));

	public static List<Style> all() {
		return List.of(GOTHIC, FORTRESS, ARCANE, ANCIENT, TOWN);
	}

	public static Style byName(String name) {
		for (Style s : all()) {
			if (s.name.equals(name)) return s;
		}
		return null;
	}
}
