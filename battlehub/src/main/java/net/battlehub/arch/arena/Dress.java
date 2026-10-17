package net.battlehub.arch.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.bldg.Style;
import net.battlehub.arch.mat.Catalog;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mat.Ramp;

/**
 * How an arena theme dresses: one palette for everything in it (ground, island rock, paving, and the masonry,
 * roofs, timber, glass and lights of its buildings), the style its buildings are drawn in, its trees, plants and
 * scenery, its lamps and the landmarks that may stand in the distance. A theme has a few variants (other roofs,
 * other stone), so two forest arenas need not look the same. Ids are the arena theme ids ("forest", "nether"...).
 */
public final class Dress {
	public final String id;
	public final String variant;
	public final Style style;
	public final Palette palette;
	/** Tree kind: round, conifer, jungle, cherry, fungus, mushroom, palm or none. */
	public final String trees;
	/** Ground cover players walk through, and rarer flowers. */
	public final List<String> plants;
	public final List<String> flowers;
	/** Scenery: boulder, basalt, spike, cactus, mesa, obelisk, spire, coral, cloud. */
	public final String feature;
	/** The lamp on posts and hanging from beams. */
	public final String lamp;
	/** Archetypes that may stand on islands in the distance. */
	public final List<String> landmarks;
	/** How weathered the stone of its buildings looks. */
	public final net.battlehub.arch.bldg.Condition condition;
	/** A particle drifting round the players (the game maps the id; unknown ids are skipped). */
	public final String ambient;
	/** Open themes have no ground round their arenas: everything floats. */
	public final boolean open;
	/** The colour of awnings and cloth in the theme. */
	public final String cloth;

	private Dress(Def d, String variant, Palette palette) {
		this.id = d.id;
		this.variant = variant;
		this.style = d.style;
		this.palette = palette;
		this.trees = d.trees;
		this.plants = List.copyOf(d.plants);
		this.flowers = List.copyOf(d.flowers);
		this.feature = d.feature;
		this.lamp = d.lamp;
		this.landmarks = List.copyOf(d.landmarks);
		this.condition = d.condition;
		this.ambient = d.ambient;
		this.open = d.open;
		this.cloth = d.cloth;
	}

	/** Names a theme used that the catalog does not know (should stay empty; checked by the tests). */
	private static final Set<String> UNKNOWN = new java.util.TreeSet<>();

	public static Set<String> unknown() {
		synchronized (UNKNOWN) {
			return Set.copyOf(UNKNOWN);
		}
	}

	static Ramp ramp(double fray, String... names) {
		for (String n : names) {
			String base = n.contains("*") ? n.substring(0, n.indexOf('*')) : n;
			if (Catalog.get(base) == null) {
				synchronized (UNKNOWN) {
					UNKNOWN.add(base);
				}
			}
		}
		return Ramp.of(fray, names);
	}

	static Ramp ramp(String... names) {
		return ramp(0.14, names);
	}

	/** A theme's fixed traits and its palette recipes. */
	static final class Def {
		final String id;
		Style style = Style.TOWN;
		String trees = "none";
		List<String> plants = List.of();
		List<String> flowers = List.of();
		String feature = "boulder";
		String lamp = "lantern";
		List<String> landmarks = List.of("watchtower");
		net.battlehub.arch.bldg.Condition condition = net.battlehub.arch.bldg.Condition.MAINTAINED;
		String ambient = "";
		boolean open;
		String cloth = "red";
		BiConsumer<Palette, Rng> base;
		final Map<String, BiConsumer<Palette, Rng>> variants = new LinkedHashMap<>();

		Def(String id) {
			this.id = id;
		}

		Def style(Style s) {
			style = s;
			return this;
		}

		Def trees(String t) {
			trees = t;
			return this;
		}

		Def plants(String... p) {
			plants = List.of(p);
			return this;
		}

		Def flowers(String... f) {
			flowers = List.of(f);
			return this;
		}

		Def feature(String f) {
			feature = f;
			return this;
		}

		Def lamp(String l) {
			lamp = l;
			return this;
		}

		Def landmarks(String... l) {
			landmarks = List.of(l);
			return this;
		}

		Def condition(net.battlehub.arch.bldg.Condition c) {
			condition = c;
			return this;
		}

		Def ambient(String a) {
			ambient = a;
			return this;
		}

		Def open() {
			open = true;
			return this;
		}

		Def cloth(String c) {
			cloth = c;
			return this;
		}

		Def base(BiConsumer<Palette, Rng> b) {
			base = b;
			return this;
		}

		Def variant(String name, BiConsumer<Palette, Rng> v) {
			variants.put(name, v);
			return this;
		}
	}

	private static final Map<String, Def> DEFS = new LinkedHashMap<>();

	private static Def def(String id) {
		Def d = new Def(id);
		DEFS.put(id, d);
		return d;
	}

	/** The theme ids, in the order of the game's themes. */
	public static List<String> ids() {
		return new ArrayList<>(DEFS.keySet());
	}

	/** The dress of a theme (an unknown id gets the castle's), in a variant picked by {@code seed}. */
	public static Dress of(String theme, long seed) {
		Def d = DEFS.getOrDefault(theme, DEFS.get("castle"));
		Rng r = new Rng(seed).stream("dress:" + d.id);
		List<String> names = new ArrayList<>(d.variants.keySet());
		String variant = names.isEmpty() ? "base" : names.get(r.nextInt(names.size()));
		return of(d, variant, r);
	}

	/** A named variant (tests, previews); the first variant when the name is unknown. */
	public static Dress of(String theme, String variant, long seed) {
		Def d = DEFS.getOrDefault(theme, DEFS.get("castle"));
		if (!d.variants.containsKey(variant)) variant = d.variants.isEmpty() ? "base" : d.variants.keySet().iterator().next();
		return of(d, variant, new Rng(seed).stream("dress:" + d.id));
	}

	private static Dress of(Def d, String variant, Rng r) {
		Palette p = new Palette("arena:" + d.id + ":" + variant, null);
		common(p);
		d.base.accept(p, r);
		BiConsumer<Palette, Rng> v = d.variants.get(variant);
		if (v != null) v.accept(p, r);
		return new Dress(d, variant, p);
	}

	public List<String> variants() {
		return new ArrayList<>(DEFS.get(id).variants.keySet());
	}

	/** What every theme shares unless it says otherwise. */
	private static void common(Palette p) {
		p.set(Slot.METAL, ramp("iron_bars"));
		p.set(Slot.ACCENT, ramp("gold_block"));
		p.set(Slot.GLASS, ramp("glass"));
		p.set(Slot.WATER, ramp("water"));
		p.set(Slot.MAGIC, ramp("amethyst_block"));
		p.set(Slot.LIGHT, ramp("glowstone"));
		stained(p, "white", "light_gray");
	}

	private static void stained(Palette p, String... colours) {
		String[] ids = new String[colours.length];
		for (int i = 0; i < colours.length; i++) ids[i] = colours[i] + "_stained_glass";
		p.set(Slot.STAINED, ramp(0, ids));
	}

	// ------------------------------------------------------------------ the themes

	static {
		def("forest").cloth("green").style(Style.TOWN).trees("round").plants("short_grass", "short_grass", "short_grass", "fern", "bush")
				.flowers("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy").feature("boulder")
				.landmarks("watchtower", "chapel", "market_hall").ambient("firefly")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "grass_block*6", "moss_block", "grass_block*3", "coarse_dirt"));
					p.set(Slot.SOIL, ramp(0.2, "dirt*3", "coarse_dirt", "rooted_dirt"));
					p.set(Slot.ROCK, ramp(0.18, "tuff", "cobblestone", "andesite*2", "stone*3"));
					p.set(Slot.PATH, ramp(0.25, "mossy_cobblestone", "cobblestone*2", "stone_bricks"));
					p.set(Slot.PAVING, ramp(0.25, "mossy_stone_bricks", "stone_bricks*3", "cobblestone", "andesite"));
					p.set(Slot.LEAVES, ramp(0.3, "oak_leaves*4", "birch_leaves", "azalea_leaves", "flowering_azalea_leaves"));
					p.set(Slot.FOUNDATION, ramp("mossy_cobblestone", "cobblestone*2"));
					p.set(Slot.WALL, ramp(0.16, "mossy_cobblestone", "cobblestone", "stone_bricks*3", "andesite"));
					p.set(Slot.BAND, ramp("polished_andesite"));
					p.set(Slot.QUOIN, ramp("stone_bricks", "polished_andesite"));
					p.set(Slot.TRIM, ramp("spruce_planks"));
					p.set(Slot.ARCH, ramp("stone_bricks"));
					p.set(Slot.WOOD, ramp("spruce_planks"));
					p.set(Slot.FLOOR, ramp("spruce_planks"));
					p.set(Slot.FLOOR_ALT, ramp("oak_planks"));
					p.set(Slot.RUBBLE, ramp("cobblestone", "mossy_cobblestone", "gravel"));
					p.set(Slot.LIGHT, ramp("shroomlight"));
				})
				.variant("timber", (p, r) -> {
					p.set(Slot.WALL_ALT, ramp(r.pick("white_terracotta", "calcite", "pale_oak_planks")));
					p.set(Slot.TIMBER, ramp(r.pick("dark_oak_log", "spruce_log")));
					p.set(Slot.PILLAR, ramp("spruce_log"));
					p.set(Slot.ROOF, ramp(0.2, "dark_oak_planks*2", "spruce_planks"));
					p.set(Slot.ROOF_EDGE, ramp("spruce_planks"));
				})
				.variant("mossy", (p, r) -> {
					p.set(Slot.WALL_ALT, ramp("oak_planks"));
					p.set(Slot.TIMBER, ramp("oak_log"));
					p.set(Slot.PILLAR, ramp("oak_log"));
					p.set(Slot.ROOF, ramp(0.2, "mossy_cobblestone*2", "cobblestone"));
					p.set(Slot.ROOF_EDGE, ramp("mossy_cobblestone"));
				});

		def("castle").style(Style.FORTRESS).trees("round").plants("short_grass", "short_grass", "fern").flowers("poppy", "red_tulip", "dandelion")
				.feature("boulder").landmarks("castle", "watchtower", "chapel").ambient("")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "grass_block*5", "moss_block", "grass_block*2", "coarse_dirt"));
					p.set(Slot.SOIL, ramp(0.2, "dirt*3", "coarse_dirt"));
					p.set(Slot.ROCK, ramp(0.18, "tuff", "cobblestone", "andesite*2", "stone*3"));
					p.set(Slot.PATH, ramp(0.25, "cracked_stone_bricks", "stone_bricks*3", "polished_andesite"));
					p.set(Slot.PAVING, ramp(0.25, "andesite", "cracked_stone_bricks", "stone_bricks*3", "polished_andesite"));
					p.set(Slot.LEAVES, ramp(0.3, "oak_leaves*3", "dark_oak_leaves"));
					p.set(Slot.FOUNDATION, ramp("cobblestone", "andesite", "stone_bricks"));
					p.set(Slot.WALL, ramp(0.16, "cobblestone", "stone_bricks*4", "andesite", "polished_andesite"));
					p.set(Slot.WALL_ALT, ramp("andesite", "stone_bricks"));
					p.set(Slot.BAND, ramp("polished_andesite"));
					p.set(Slot.QUOIN, ramp("polished_andesite", "stone_bricks"));
					p.set(Slot.PILLAR, ramp("polished_andesite"));
					p.set(Slot.TRIM, ramp("polished_andesite", "smooth_stone"));
					p.set(Slot.ARCH, ramp("stone_bricks", "polished_andesite"));
					p.set(Slot.WOOD, ramp("spruce_planks"));
					p.set(Slot.TIMBER, ramp("dark_oak_log"));
					p.set(Slot.FLOOR, ramp("spruce_planks"));
					p.set(Slot.FLOOR_ALT, ramp("stone_bricks"));
					p.set(Slot.RUBBLE, ramp("cobblestone", "gravel", "andesite"));
				})
				.variant("granite", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "dark_oak_planks*2", "spruce_planks"));
					p.set(Slot.ROOF_EDGE, ramp("spruce_planks"));
				})
				.variant("slate", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "deepslate_tiles*3", "polished_deepslate"));
					p.set(Slot.ROOF_EDGE, ramp("polished_blackstone"));
				});

		def("nether").style(Style.FORTRESS).trees("fungus").plants("crimson_roots", "crimson_roots", "nether_sprouts", "crimson_fungus")
				.flowers("crimson_fungus", "warped_roots").feature("basalt").lamp("soul_lantern").landmarks("castle", "watchtower", "mage_tower")
				.ambient("crimson_spore")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "netherrack*4", "crimson_nylium*3", "soul_soil", "netherrack*2"));
					p.set(Slot.SOIL, ramp("netherrack"));
					p.set(Slot.ROCK, ramp(0.18, "blackstone*2", "basalt", "netherrack*4"));
					p.set(Slot.PATH, ramp(0.25, "blackstone", "polished_blackstone_bricks*2", "nether_bricks"));
					p.set(Slot.PAVING, ramp(0.25, "polished_blackstone_bricks*2", "nether_bricks*2", "polished_blackstone"));
					p.set(Slot.FOUNDATION, ramp("blackstone", "polished_blackstone_bricks"));
					p.set(Slot.WALL, ramp(0.16, "polished_blackstone_bricks", "nether_bricks*3", "red_nether_bricks"));
					p.set(Slot.WALL_ALT, ramp("red_nether_bricks"));
					p.set(Slot.BAND, ramp("polished_blackstone"));
					p.set(Slot.QUOIN, ramp("polished_blackstone"));
					p.set(Slot.PILLAR, ramp("polished_basalt"));
					p.set(Slot.TRIM, ramp("polished_blackstone", "nether_bricks"));
					p.set(Slot.ARCH, ramp("nether_bricks", "polished_blackstone"));
					p.set(Slot.ROOF, ramp(0.2, "nether_bricks*2", "red_nether_bricks"));
					p.set(Slot.ROOF_EDGE, ramp("polished_blackstone"));
					p.set(Slot.LIGHT, ramp("shroomlight"));
					p.set(Slot.MAGIC, ramp("crying_obsidian"));
					p.set(Slot.ACCENT, ramp("gilded_blackstone"));
					p.set(Slot.RUBBLE, ramp("netherrack", "blackstone", "soul_soil"));
					stained(p, "red", "orange", "black", "magenta");
				})
				.variant("crimson", (p, r) -> {
					p.set(Slot.LEAVES, ramp(0.3, "nether_wart_block*6", "shroomlight"));
					p.set(Slot.TIMBER, ramp("crimson_stem"));
					p.set(Slot.WOOD, ramp("crimson_planks"));
					p.set(Slot.FLOOR, ramp("crimson_planks"));
					p.set(Slot.FLOOR_ALT, ramp("nether_bricks"));
				})
				.variant("warped", (p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "netherrack*3", "warped_nylium*3", "soul_soil", "netherrack*2"));
					p.set(Slot.LEAVES, ramp(0.3, "warped_wart_block*6", "shroomlight"));
					p.set(Slot.TIMBER, ramp("warped_stem"));
					p.set(Slot.WOOD, ramp("warped_planks"));
					p.set(Slot.FLOOR, ramp("warped_planks"));
					p.set(Slot.FLOOR_ALT, ramp("nether_bricks"));
				});

		def("ice").cloth("light_blue").style(Style.TOWN).trees("conifer").plants("snow", "snow", "short_grass").flowers("snow").feature("spike")
				.landmarks("watchtower", "chapel", "townhouse").ambient("snowflake")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "snow_block*6", "packed_ice", "snow_block*2"));
					p.set(Slot.SOIL, ramp(0.2, "snow_block*2", "packed_ice"));
					p.set(Slot.ROCK, ramp(0.18, "stone", "andesite", "packed_ice*3", "blue_ice"));
					p.set(Slot.PATH, ramp(0.25, "polished_andesite", "packed_ice*2", "polished_diorite"));
					p.set(Slot.PAVING, ramp(0.25, "packed_ice*2", "polished_diorite*2", "snow_block"));
					p.set(Slot.LEAVES, ramp(0.3, "spruce_leaves"));
					p.set(Slot.FOUNDATION, ramp("cobblestone", "stone_bricks"));
					p.set(Slot.WALL, ramp(0.16, "stone_bricks*2", "polished_andesite", "polished_diorite*2"));
					p.set(Slot.BAND, ramp("polished_andesite"));
					p.set(Slot.QUOIN, ramp("packed_ice", "blue_ice"));
					p.set(Slot.PILLAR, ramp("packed_ice"));
					p.set(Slot.TRIM, ramp("polished_diorite"));
					p.set(Slot.ARCH, ramp("polished_diorite"));
					p.set(Slot.TIMBER, ramp("spruce_log"));
					p.set(Slot.WOOD, ramp("spruce_planks"));
					p.set(Slot.FLOOR, ramp("spruce_planks"));
					p.set(Slot.FLOOR_ALT, ramp("packed_ice"));
					p.set(Slot.LIGHT, ramp("sea_lantern"));
					p.set(Slot.ACCENT, ramp("blue_ice"));
					p.set(Slot.MAGIC, ramp("blue_ice"));
					p.set(Slot.RUBBLE, ramp("snow_block", "packed_ice", "cobblestone"));
					stained(p, "light_blue", "cyan", "white", "blue");
				})
				.variant("nordic", (p, r) -> {
					p.set(Slot.WALL_ALT, ramp("spruce_planks"));
					p.set(Slot.ROOF, ramp(0.2, "spruce_planks*2", "dark_oak_planks"));
					p.set(Slot.ROOF_EDGE, ramp("dark_oak_planks"));
				})
				.variant("frost", (p, r) -> {
					p.set(Slot.WALL_ALT, ramp("calcite"));
					p.set(Slot.ROOF, ramp(0.2, "deepslate_tiles*2", "polished_deepslate"));
					p.set(Slot.ROOF_EDGE, ramp("polished_diorite"));
				});

		def("sky").cloth("light_blue").style(Style.ANCIENT).trees("round").plants("short_grass", "short_grass", "short_grass").flowers("oxeye_daisy", "azure_bluet",
				"lily_of_the_valley", "dandelion").feature("cloud").landmarks("temple_ruin", "mage_tower", "chapel").ambient("white_ash").open()
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "grass_block*6", "moss_block", "grass_block*2"));
					p.set(Slot.SOIL, ramp(0.2, "dirt*3", "rooted_dirt"));
					p.set(Slot.ROCK, ramp(0.18, "stone", "diorite*2", "calcite*3"));
					p.set(Slot.PATH, ramp(0.25, "polished_diorite", "calcite", "smooth_quartz*2"));
					p.set(Slot.PAVING, ramp(0.25, "polished_diorite", "quartz_bricks*2", "smooth_quartz*2", "calcite"));
					p.set(Slot.LEAVES, ramp(0.3, "oak_leaves*2", "birch_leaves", "azalea_leaves", "flowering_azalea_leaves*2"));
					p.set(Slot.TIMBER, ramp("birch_log"));
					p.set(Slot.FOUNDATION, ramp("polished_andesite", "stone_bricks"));
					p.set(Slot.WALL, ramp(0.16, "polished_diorite*2", "quartz_bricks*3", "calcite*2"));
					p.set(Slot.WALL_ALT, ramp("calcite"));
					p.set(Slot.BAND, ramp("polished_andesite"));
					p.set(Slot.QUOIN, ramp("smooth_quartz"));
					p.set(Slot.PILLAR, ramp("quartz_pillar"));
					p.set(Slot.TRIM, ramp("smooth_quartz"));
					p.set(Slot.ARCH, ramp("smooth_quartz"));
					p.set(Slot.WOOD, ramp("birch_planks"));
					p.set(Slot.FLOOR, ramp("polished_diorite"));
					p.set(Slot.FLOOR_ALT, ramp("polished_andesite"));
					p.set(Slot.LIGHT, ramp("pearlescent_froglight"));
					p.set(Slot.RUBBLE, ramp("diorite", "calcite", "gravel"));
					stained(p, "white", "light_blue", "yellow");
				})
				.variant("verdigris", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "waxed_oxidized_cut_copper*2", "waxed_weathered_cut_copper"));
					p.set(Slot.ROOF_EDGE, ramp("smooth_quartz"));
				})
				.variant("terracotta", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "bricks*2", "polished_granite"));
					p.set(Slot.ROOF_EDGE, ramp("polished_granite"));
				});

		def("desert").cloth("orange").style(Style.ANCIENT).trees("palm").plants("dead_bush", "short_dry_grass", "short_dry_grass", "tall_dry_grass")
				.flowers("dead_bush").feature("cactus").landmarks("temple_ruin", "watchtower").ambient("")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "sand*5", "sandstone", "sand*2"));
					p.set(Slot.SOIL, ramp(0.2, "sand", "sandstone*2"));
					p.set(Slot.ROCK, ramp(0.18, "terracotta", "sandstone*3", "smooth_sandstone"));
					p.set(Slot.PATH, ramp(0.25, "sandstone", "cut_sandstone*2", "smooth_sandstone"));
					p.set(Slot.PAVING, ramp(0.25, "sandstone", "cut_sandstone*2", "smooth_sandstone*2"));
					p.set(Slot.LEAVES, ramp(0.3, "jungle_leaves*3", "azalea_leaves"));
					p.set(Slot.TIMBER, ramp("stripped_jungle_log"));
					p.set(Slot.FOUNDATION, ramp("sandstone", "cut_sandstone"));
					p.set(Slot.WALL, ramp(0.16, "sandstone*2", "cut_sandstone*2", "smooth_sandstone*2"));
					p.set(Slot.WALL_ALT, ramp("smooth_sandstone"));
					p.set(Slot.QUOIN, ramp("smooth_sandstone"));
					p.set(Slot.PILLAR, ramp("smooth_sandstone", "cut_sandstone"));
					p.set(Slot.TRIM, ramp("smooth_sandstone"));
					p.set(Slot.ARCH, ramp("cut_sandstone"));
					p.set(Slot.WOOD, ramp("jungle_planks"));
					p.set(Slot.FLOOR, ramp("smooth_sandstone"));
					p.set(Slot.FLOOR_ALT, ramp("cut_red_sandstone"));
					p.set(Slot.LIGHT, ramp("ochre_froglight"));
					p.set(Slot.RUBBLE, ramp("sandstone", "sand"));
					stained(p, "orange", "yellow", "white", "blue");
				})
				.variant("ochre", (p, r) -> {
					p.set(Slot.BAND, ramp("cut_red_sandstone"));
					p.set(Slot.ROOF, ramp(0.2, "smooth_red_sandstone*2", "red_sandstone"));
					p.set(Slot.ROOF_EDGE, ramp("cut_red_sandstone"));
				})
				.variant("lapis", (p, r) -> {
					p.set(Slot.BAND, ramp("lapis_block"));
					p.set(Slot.ACCENT, ramp("lapis_block"));
					p.set(Slot.ROOF, ramp(0.2, "smooth_sandstone*2", "cut_sandstone"));
					p.set(Slot.ROOF_EDGE, ramp("smooth_sandstone"));
				});

		def("jungle").cloth("lime").style(Style.ANCIENT).trees("jungle").plants("fern", "fern", "short_grass", "short_grass", "bush").flowers("blue_orchid", "fern")
				.feature("boulder").landmarks("temple_ruin", "watchtower").condition(net.battlehub.arch.bldg.Condition.WEATHERED).ambient("spore_blossom_air")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "grass_block*4", "moss_block*2", "podzol", "grass_block*2", "coarse_dirt"));
					p.set(Slot.SOIL, ramp(0.2, "dirt*3", "coarse_dirt", "rooted_dirt"));
					p.set(Slot.ROCK, ramp(0.18, "tuff", "mossy_cobblestone", "cobblestone", "stone*2", "andesite"));
					p.set(Slot.PATH, ramp(0.25, "mossy_cobblestone", "mossy_stone_bricks*2", "stone_bricks"));
					p.set(Slot.PAVING, ramp(0.25, "mossy_cobblestone", "mossy_stone_bricks*2", "stone_bricks*2", "cracked_stone_bricks"));
					p.set(Slot.LEAVES, ramp(0.3, "jungle_leaves*4", "oak_leaves", "azalea_leaves"));
					p.set(Slot.TIMBER, ramp("jungle_log"));
					p.set(Slot.FOUNDATION, ramp("mossy_cobblestone", "cobblestone"));
					p.set(Slot.WALL, ramp(0.16, "mossy_stone_bricks", "stone_bricks*3", "andesite"));
					p.set(Slot.WALL_ALT, ramp("andesite"));
					p.set(Slot.BAND, ramp("polished_andesite"));
					p.set(Slot.QUOIN, ramp("polished_andesite"));
					p.set(Slot.PILLAR, ramp("polished_andesite", "stone_bricks"));
					p.set(Slot.TRIM, ramp("polished_andesite", "chiseled_stone_bricks"));
					p.set(Slot.ARCH, ramp("stone_bricks"));
					p.set(Slot.WOOD, ramp("jungle_planks"));
					p.set(Slot.FLOOR, ramp("stone_bricks"));
					p.set(Slot.FLOOR_ALT, ramp("mossy_stone_bricks"));
					p.set(Slot.LIGHT, ramp("ochre_froglight"));
					p.set(Slot.ACCENT, ramp("emerald_block"));
					p.set(Slot.RUBBLE, ramp("cobblestone", "mossy_cobblestone", "gravel"));
					stained(p, "lime", "green", "yellow");
				})
				.variant("temple", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "mossy_cobblestone*2", "cobblestone"));
					p.set(Slot.ROOF_EDGE, ramp("mossy_cobblestone"));
				})
				.variant("hut", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "jungle_planks*2", "spruce_planks"));
					p.set(Slot.ROOF_EDGE, ramp("jungle_planks"));
				});

		def("cherry").cloth("pink").style(Style.TOWN).trees("cherry").plants("pink_petals", "pink_petals", "short_grass", "short_grass")
				.flowers("lily_of_the_valley", "pink_tulip", "white_tulip").feature("boulder").landmarks("watchtower", "chapel", "market_hall")
				.ambient("cherry_leaves")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "grass_block*6", "moss_block", "grass_block*2"));
					p.set(Slot.SOIL, ramp(0.2, "dirt*3", "rooted_dirt"));
					p.set(Slot.ROCK, ramp(0.18, "tuff", "stone*2", "andesite", "calcite"));
					p.set(Slot.PATH, ramp(0.25, "andesite", "polished_andesite*2", "smooth_stone"));
					p.set(Slot.PAVING, ramp(0.25, "polished_andesite*2", "stone_bricks", "smooth_stone"));
					p.set(Slot.LEAVES, ramp(0.3, "cherry_leaves"));
					p.set(Slot.TIMBER, ramp("cherry_log"));
					p.set(Slot.FOUNDATION, ramp("cobblestone", "stone_bricks"));
					p.set(Slot.WALL, ramp(0.16, "stone_bricks*2", "polished_andesite"));
					p.set(Slot.WALL_ALT, ramp(r.pick("white_terracotta", "calcite")));
					p.set(Slot.QUOIN, ramp("stripped_cherry_log"));
					p.set(Slot.PILLAR, ramp("stripped_cherry_log"));
					p.set(Slot.TRIM, ramp("cherry_planks"));
					p.set(Slot.ARCH, ramp("cherry_planks"));
					p.set(Slot.BAND, ramp("dark_oak_planks"));
					p.set(Slot.WOOD, ramp("cherry_planks"));
					p.set(Slot.FLOOR, ramp("cherry_planks"));
					p.set(Slot.FLOOR_ALT, ramp("dark_oak_planks"));
					p.set(Slot.LIGHT, ramp("ochre_froglight"));
					p.set(Slot.RUBBLE, ramp("cobblestone", "gravel"));
					stained(p, "pink", "white", "magenta");
				})
				.variant("tiled", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "deepslate_tiles*3", "polished_deepslate"));
					p.set(Slot.ROOF_EDGE, ramp("polished_blackstone"));
				})
				.variant("thatch", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "dark_oak_planks*2", "spruce_planks"));
					p.set(Slot.ROOF_EDGE, ramp("dark_oak_planks"));
				});

		def("end").cloth("purple").style(Style.ARCANE).trees("none").feature("obelisk").lamp("end_rod").landmarks("mage_tower", "watchtower").ambient("portal").open()
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "end_stone*7", "end_stone_bricks"));
					p.set(Slot.SOIL, ramp("end_stone"));
					p.set(Slot.ROCK, ramp(0.18, "end_stone*4", "end_stone_bricks"));
					p.set(Slot.PATH, ramp(0.25, "purpur_block", "end_stone_bricks*2"));
					p.set(Slot.PAVING, ramp(0.25, "end_stone_bricks*3", "purpur_block"));
					p.set(Slot.LEAVES, ramp("purpur_block"));
					p.set(Slot.TIMBER, ramp("purpur_pillar"));
					p.set(Slot.FOUNDATION, ramp("obsidian", "polished_blackstone_bricks"));
					p.set(Slot.WALL, ramp(0.14, "end_stone_bricks*4", "end_stone"));
					p.set(Slot.WALL_ALT, ramp("purpur_block"));
					p.set(Slot.BAND, ramp("purpur_block"));
					p.set(Slot.QUOIN, ramp("purpur_block"));
					p.set(Slot.PILLAR, ramp("purpur_pillar"));
					p.set(Slot.TRIM, ramp("purpur_block"));
					p.set(Slot.ARCH, ramp("purpur_block"));
					p.set(Slot.ROOF, ramp(0.18, "purpur_block"));
					p.set(Slot.ROOF_EDGE, ramp("polished_blackstone"));
					p.set(Slot.WOOD, ramp("purpur_block"));
					p.set(Slot.FLOOR, ramp("purpur_block"));
					p.set(Slot.FLOOR_ALT, ramp("end_stone_bricks"));
					p.set(Slot.LIGHT, ramp("pearlescent_froglight"));
					p.set(Slot.RUBBLE, ramp("end_stone", "obsidian"));
					stained(p, "magenta", "purple", "pink", "white");
				})
				.variant("amethyst", (p, r) -> p.set(Slot.MAGIC, ramp("amethyst_block")))
				.variant("void", (p, r) -> p.set(Slot.MAGIC, ramp("crying_obsidian", "obsidian")));

		def("mushroom").style(Style.TOWN).trees("mushroom").plants("red_mushroom", "brown_mushroom", "short_grass").flowers("red_mushroom", "brown_mushroom")
				.feature("mushroom").landmarks("townhouse", "chapel", "watchtower").ambient("mycelium")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "mycelium*6", "podzol", "mycelium*2", "coarse_dirt"));
					p.set(Slot.SOIL, ramp(0.2, "dirt*3", "coarse_dirt"));
					p.set(Slot.ROCK, ramp(0.18, "tuff", "cobblestone", "stone*3", "andesite"));
					p.set(Slot.PATH, ramp(0.25, "mossy_cobblestone", "cobblestone*2", "packed_mud"));
					p.set(Slot.PAVING, ramp(0.25, "mud_bricks", "packed_mud*2", "cobblestone", "mossy_cobblestone"));
					p.set(Slot.LEAVES, ramp(0.2, "red_mushroom_block*3", "brown_mushroom_block"));
					p.set(Slot.TIMBER, ramp("dark_oak_log"));
					p.set(Slot.FOUNDATION, ramp("cobblestone", "mossy_cobblestone"));
					p.set(Slot.WALL, ramp(0.16, "mud_bricks*3", "bricks"));
					p.set(Slot.WALL_ALT, ramp("mushroom_stem"));
					p.set(Slot.QUOIN, ramp("mud_bricks"));
					p.set(Slot.PILLAR, ramp("dark_oak_log"));
					p.set(Slot.TRIM, ramp("dark_oak_planks"));
					p.set(Slot.ARCH, ramp("mud_bricks"));
					p.set(Slot.WOOD, ramp("dark_oak_planks"));
					p.set(Slot.FLOOR, ramp("spruce_planks"));
					p.set(Slot.FLOOR_ALT, ramp("dark_oak_planks"));
					p.set(Slot.LIGHT, ramp("shroomlight"));
					p.set(Slot.RUBBLE, ramp("cobblestone", "coarse_dirt"));
					stained(p, "red", "brown", "white");
				})
				.variant("red", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "mangrove_planks*2", "crimson_planks"));
					p.set(Slot.ROOF_EDGE, ramp("dark_oak_planks"));
				})
				.variant("brown", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "dark_oak_planks*2", "spruce_planks"));
					p.set(Slot.ROOF_EDGE, ramp("spruce_planks"));
				});

		def("badlands").cloth("orange").style(Style.FORTRESS).trees("none").plants("dead_bush", "short_dry_grass", "short_dry_grass").flowers("dead_bush")
				.feature("mesa").landmarks("watchtower", "temple_ruin").ambient("")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "red_sand*5", "orange_terracotta", "red_sand*2", "terracotta"));
					p.set(Slot.SOIL, ramp(0.2, "red_sand", "terracotta*2"));
					p.set(Slot.ROCK, ramp(0.04, "brown_terracotta", "red_terracotta*2", "orange_terracotta", "terracotta*2", "yellow_terracotta",
							"white_terracotta", "light_gray_terracotta", "orange_terracotta", "red_terracotta"));
					p.set(Slot.PATH, ramp(0.25, "red_sandstone", "cut_red_sandstone*2", "smooth_red_sandstone"));
					p.set(Slot.PAVING, ramp(0.25, "red_sandstone", "cut_red_sandstone*2", "smooth_red_sandstone*2"));
					p.set(Slot.FOUNDATION, ramp("red_sandstone", "terracotta"));
					p.set(Slot.WALL, ramp(0.16, "red_sandstone*2", "cut_red_sandstone*2", "smooth_red_sandstone"));
					p.set(Slot.WALL_ALT, ramp("white_terracotta"));
					p.set(Slot.BAND, ramp("terracotta"));
					p.set(Slot.QUOIN, ramp("cut_red_sandstone"));
					p.set(Slot.PILLAR, ramp("cut_red_sandstone"));
					p.set(Slot.TRIM, ramp("smooth_red_sandstone"));
					p.set(Slot.ARCH, ramp("cut_red_sandstone"));
					p.set(Slot.WOOD, ramp("acacia_planks"));
					p.set(Slot.TIMBER, ramp("acacia_log"));
					p.set(Slot.FLOOR, ramp("acacia_planks"));
					p.set(Slot.FLOOR_ALT, ramp("terracotta"));
					p.set(Slot.LEAVES, ramp("oak_leaves"));
					p.set(Slot.LIGHT, ramp("shroomlight"));
					p.set(Slot.RUBBLE, ramp("red_sand", "terracotta"));
					stained(p, "orange", "red", "yellow", "brown");
				})
				.variant("adobe", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "acacia_planks*2", "dark_oak_planks"));
					p.set(Slot.ROOF_EDGE, ramp("dark_oak_planks"));
				})
				.variant("mesa", (p, r) -> {
					p.set(Slot.ROOF, ramp(0.2, "smooth_red_sandstone*2", "red_sandstone"));
					p.set(Slot.ROOF_EDGE, ramp("cut_red_sandstone"));
				});

		def("deep_dark").cloth("cyan").style(Style.GOTHIC).trees("none").plants().flowers().feature("spire").lamp("soul_lantern").landmarks("chapel", "watchtower")
				.ambient("sculk_soul")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "sculk*4", "cobbled_deepslate", "sculk*2", "deepslate_tiles"));
					p.set(Slot.SOIL, ramp("cobbled_deepslate"));
					p.set(Slot.ROCK, ramp(0.18, "blackstone", "deepslate*2", "cobbled_deepslate*2", "tuff"));
					p.set(Slot.PATH, ramp(0.25, "polished_deepslate", "deepslate_tiles*2", "cracked_deepslate_tiles"));
					p.set(Slot.PAVING, ramp(0.25, "cracked_deepslate_tiles", "deepslate_tiles*2", "polished_deepslate", "deepslate_bricks"));
					p.set(Slot.FOUNDATION, ramp("cobbled_deepslate"));
					p.set(Slot.WALL, ramp(0.16, "deepslate_tiles", "deepslate_bricks*3", "cracked_deepslate_bricks"));
					p.set(Slot.WALL_ALT, ramp("polished_deepslate"));
					p.set(Slot.BAND, ramp("polished_deepslate"));
					p.set(Slot.QUOIN, ramp("deepslate_tiles"));
					p.set(Slot.PILLAR, ramp("polished_basalt"));
					p.set(Slot.TRIM, ramp("polished_deepslate"));
					p.set(Slot.ARCH, ramp("deepslate_tiles"));
					p.set(Slot.ROOF, ramp(0.2, "deepslate_tiles*3"));
					p.set(Slot.ROOF_EDGE, ramp("polished_blackstone"));
					p.set(Slot.WOOD, ramp("dark_oak_planks"));
					p.set(Slot.TIMBER, ramp("dark_oak_log"));
					p.set(Slot.FLOOR, ramp("polished_deepslate"));
					p.set(Slot.FLOOR_ALT, ramp("deepslate_tiles"));
					p.set(Slot.LIGHT, ramp("sea_lantern"));
					p.set(Slot.MAGIC, ramp("sculk"));
					p.set(Slot.ACCENT, ramp("waxed_oxidized_copper"));
					p.set(Slot.RUBBLE, ramp("cobbled_deepslate", "tuff"));
					stained(p, "cyan", "light_blue", "gray", "black");
				})
				.variant("ancient", (p, r) -> p.set(Slot.LEAVES, ramp("sculk")))
				.variant("lit", (p, r) -> {
					p.set(Slot.LEAVES, ramp("sculk"));
					p.set(Slot.LIGHT, ramp("verdant_froglight"));
				});

		def("ocean").cloth("cyan").style(Style.ARCANE).trees("palm").plants("short_grass", "dead_bush").flowers("sea_pickle").feature("coral").lamp("sea_lantern")
				.landmarks("temple_ruin", "mage_tower", "watchtower").ambient("underwater")
				.base((p, r) -> {
					p.set(Slot.GROUND, ramp(0.3, "sand*5", "gravel", "sand*2", "clay"));
					p.set(Slot.SOIL, ramp(0.2, "sand*2", "sandstone"));
					p.set(Slot.ROCK, ramp(0.18, "stone", "dark_prismarine*2", "prismarine*3"));
					p.set(Slot.PATH, ramp(0.25, "dark_prismarine", "prismarine_bricks*2", "prismarine"));
					p.set(Slot.PAVING, ramp(0.25, "dark_prismarine", "prismarine_bricks*3", "prismarine"));
					p.set(Slot.LEAVES, ramp(0.3, "jungle_leaves*3", "oak_leaves"));
					p.set(Slot.TIMBER, ramp("stripped_jungle_log"));
					p.set(Slot.FOUNDATION, ramp("dark_prismarine"));
					p.set(Slot.WALL, ramp(0.14, "dark_prismarine*2", "prismarine*2", "prismarine_bricks*2"));
					p.set(Slot.WALL_ALT, ramp("prismarine_bricks"));
					p.set(Slot.BAND, ramp("dark_prismarine"));
					p.set(Slot.QUOIN, ramp("prismarine_bricks"));
					p.set(Slot.PILLAR, ramp("prismarine_bricks", "smooth_quartz"));
					p.set(Slot.TRIM, ramp("smooth_quartz"));
					p.set(Slot.ARCH, ramp("prismarine_bricks", "smooth_quartz"));
					p.set(Slot.ROOF, ramp(0.18, "dark_prismarine*2", "prismarine_bricks"));
					p.set(Slot.ROOF_EDGE, ramp("dark_prismarine"));
					p.set(Slot.WOOD, ramp("warped_planks"));
					p.set(Slot.FLOOR, ramp("dark_prismarine"));
					p.set(Slot.FLOOR_ALT, ramp("prismarine_bricks"));
					p.set(Slot.LIGHT, ramp("sea_lantern"));
					p.set(Slot.MAGIC, ramp("sea_lantern"));
					p.set(Slot.RUBBLE, ramp("prismarine", "sand", "gravel"));
					stained(p, "cyan", "light_blue", "blue", "lime", "white");
				})
				.variant("reef", (p, r) -> p.set(Slot.ACCENT, ramp("gold_block")))
				.variant("deep", (p, r) -> p.set(Slot.ACCENT, ramp("waxed_oxidized_copper")));
	}

	@Override
	public String toString() {
		return id + ":" + variant;
	}
}
