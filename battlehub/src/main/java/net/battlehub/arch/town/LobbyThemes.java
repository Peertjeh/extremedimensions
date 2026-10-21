package net.battlehub.arch.town;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The same city in another dress: a lobby made by {@link Lobby} keeps every block where it is, only the materials
 * change. Each theme says what the ground, the plants, the trees, the stone families (block, stairs, slab, wall) and
 * the woods become, so a desert city has sand and sandstone, a winter city snow and spruce, a nether city nylium,
 * nether bricks and crimson wood. Shapes and properties stay (stairs stay stairs facing the same way), which is why
 * the layout is exactly the same in every theme. Only themes that suit a city are offered.
 */
public final class LobbyThemes {
	private LobbyThemes() {}

	/** The themes a city can wear; "castle" is the city as generated. */
	public static final List<String> THEMES = List.of("castle", "desert", "ice", "cherry", "nether", "badlands", "jungle", "deep_dark", "end");

	public static boolean supports(String theme) {
		return THEMES.contains(theme);
	}

	/** A stone family: the full block and, where the game has them, its stairs, slab and wall. */
	record Fam(String block, String stairs, String slab, String wall) {
		String variant(int i) {
			return switch (i) {
				case 1 -> stairs;
				case 2 -> slab;
				case 3 -> wall;
				default -> block;
			};
		}
	}

	private static final Map<String, Fam> FAMS = new LinkedHashMap<>();
	/** Every variant name of a family: its family and which variant (0 block, 1 stairs, 2 slab, 3 wall). */
	private static final Map<String, Object[]> VARIANT = new HashMap<>();

	private static void fam(String key, String block, String stairs, String slab, String wall) {
		Fam f = new Fam(block, stairs, slab, wall);
		FAMS.put(key, f);
		for (int i = 0; i < 4; i++) {
			String n = f.variant(i);
			if (n != null) VARIANT.putIfAbsent(n, new Object[] {key, i});
		}
	}

	/** A family that only exists as a target (variants of different stones that go together). */
	private static void target(String key, String block, String stairs, String slab, String wall) {
		FAMS.put(key, new Fam(block, stairs, slab, wall));
	}

	static {
		fam("stone_brick", "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall");
		fam("mossy_stone_brick", "mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall");
		fam("cracked_stone_brick", "cracked_stone_bricks", null, null, null);
		fam("cobblestone", "cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall");
		fam("mossy_cobblestone", "mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall");
		fam("stone", "stone", "stone_stairs", "stone_slab", null);
		fam("smooth_stone", "smooth_stone", null, "smooth_stone_slab", null);
		fam("andesite", "andesite", "andesite_stairs", "andesite_slab", "andesite_wall");
		fam("polished_andesite", "polished_andesite", "polished_andesite_stairs", "polished_andesite_slab", null);
		fam("diorite", "diorite", "diorite_stairs", "diorite_slab", "diorite_wall");
		fam("polished_diorite", "polished_diorite", "polished_diorite_stairs", "polished_diorite_slab", null);
		fam("granite", "granite", "granite_stairs", "granite_slab", "granite_wall");
		fam("polished_granite", "polished_granite", "polished_granite_stairs", "polished_granite_slab", null);
		fam("brick", "bricks", "brick_stairs", "brick_slab", "brick_wall");
		fam("mud_brick", "mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall");
		fam("deepslate_tile", "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall");
		fam("cracked_deepslate_tile", "cracked_deepslate_tiles", null, null, null);
		fam("deepslate_brick", "deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall");
		fam("polished_deepslate", "polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall");
		fam("cobbled_deepslate", "cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall");
		fam("blackstone", "blackstone", "blackstone_stairs", "blackstone_slab", "blackstone_wall");
		fam("polished_blackstone", "polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall");
		fam("polished_blackstone_brick", "polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
				"polished_blackstone_brick_wall");
		fam("tuff", "tuff", "tuff_stairs", "tuff_slab", "tuff_wall");
		fam("polished_tuff", "polished_tuff", "polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall");
		fam("tuff_brick", "tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall");
		fam("sandstone", "sandstone", "sandstone_stairs", "sandstone_slab", "sandstone_wall");
		fam("cut_sandstone", "cut_sandstone", null, "cut_sandstone_slab", null);
		fam("smooth_sandstone", "smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", null);
		fam("red_sandstone", "red_sandstone", "red_sandstone_stairs", "red_sandstone_slab", "red_sandstone_wall");
		fam("cut_red_sandstone", "cut_red_sandstone", null, "cut_red_sandstone_slab", null);
		fam("smooth_red_sandstone", "smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab", null);
		fam("waxed_cut_copper", "waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab", null);
		fam("waxed_exposed_cut_copper", "waxed_exposed_cut_copper", "waxed_exposed_cut_copper_stairs", "waxed_exposed_cut_copper_slab", null);
		fam("waxed_weathered_cut_copper", "waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab", null);
		fam("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", null);
		fam("end_stone_brick", "end_stone_bricks", "end_stone_brick_stairs", "end_stone_brick_slab", "end_stone_brick_wall");
		fam("purpur", "purpur_block", "purpur_stairs", "purpur_slab", null);
		fam("nether_brick", "nether_bricks", "nether_brick_stairs", "nether_brick_slab", "nether_brick_wall");
		fam("red_nether_brick", "red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab", "red_nether_brick_wall");
		fam("resin_brick", "resin_bricks", "resin_brick_stairs", "resin_brick_slab", "resin_brick_wall");
		// targets: stones of one look whose variants come from different families
		target("desert_brick", "cut_sandstone", "sandstone_stairs", "cut_sandstone_slab", "sandstone_wall");
		target("desert_smooth", "smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall");
		target("mesa_brick", "cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab", "red_sandstone_wall");
		target("mesa_smooth", "smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab", "red_sandstone_wall");
		target("purpur_walled", "purpur_block", "purpur_stairs", "purpur_slab", "end_stone_brick_wall");
	}

	/** One theme: exact block names, stone families and woods. */
	private static final class Dress {
		final Map<String, String> exact = new HashMap<>();
		final Map<String, String> fams = new HashMap<>();
		final Map<String, String> woods = new HashMap<>();

		Dress exact(String target, String... from) {
			for (String f : from) exact.put(f, target);
			return this;
		}

		Dress fam(String target, String... from) {
			for (String f : from) fams.put(f, target);
			return this;
		}

		Dress wood(String target, String... from) {
			for (String f : from) woods.put(f, target);
			return this;
		}
	}

	private static final String[] FLOWERS = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "pink_petals", "red_tulip", "blue_orchid"};
	private static final String[] POTTED = {"potted_poppy", "potted_dandelion", "potted_azure_bluet", "potted_oxeye_daisy", "potted_blue_orchid",
			"potted_red_tulip", "potted_cornflower"};
	private static final String[] LEAVES = {"oak_leaves", "azalea_leaves", "flowering_azalea_leaves"};
	private static final String[] GROUND = {"grass_block", "moss_block"};
	private static final Map<String, Dress> DRESSES = new HashMap<>();

	static {
		DRESSES.put("castle", new Dress());
		DRESSES.put("desert", new Dress()
				.exact("sand", GROUND).exact("sandstone", "coarse_dirt", "dirt", "rooted_dirt", "dripstone_block")
				.exact("smooth_sandstone", "tuff", "calcite").exact("orange_terracotta", "andesite", "diorite").exact("terracotta", "deepslate")
				.exact("air", "moss_carpet", "vine").exact("air", FLOWERS).exact("dead_bush!", "short_grass", "fern", "azalea", "flowering_azalea")
				.exact("potted_dead_bush!", POTTED).exact("jungle_leaves", LEAVES)
				.fam("desert_brick", "stone_brick", "mossy_stone_brick").fam("sandstone", "cobblestone", "mossy_cobblestone", "stone", "andesite", "diorite")
				.fam("desert_smooth", "polished_andesite", "smooth_stone", "polished_diorite", "tuff", "polished_tuff", "tuff_brick")
				.fam("mesa_brick", "deepslate_tile", "polished_blackstone_brick").fam("mesa_smooth", "polished_deepslate", "polished_blackstone")
				.fam("red_sandstone", "deepslate_brick", "cobbled_deepslate", "blackstone")
				.fam("mud_brick", "waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper", "waxed_oxidized_cut_copper")
				.exact("chiseled_sandstone", "cracked_stone_bricks", "cracked_deepslate_tiles", "cracked_polished_blackstone_bricks")
				.wood("jungle", "oak").wood("acacia", "spruce").wood("birch", "pale_oak"));
		DRESSES.put("ice", new Dress()
				.exact("snow_block", GROUND).exact("snow_block", "coarse_dirt").exact("snow", "moss_carpet")
				.exact("air", "short_grass", "fern", "azalea", "flowering_azalea", "vine").exact("air", FLOWERS)
				.exact("potted_spruce_sapling!", POTTED).exact("spruce_leaves", LEAVES).exact("ice", "water")
				.wood("spruce", "oak", "pale_oak"));
		DRESSES.put("cherry", new Dress()
				.exact("cherry_leaves", LEAVES).exact("pink_petals!", "azalea", "flowering_azalea", "poppy", "moss_carpet")
				.exact("pink_tulip", "dandelion", "cornflower").exact("potted_cherry_sapling!", POTTED)
				.fam("deepslate_tile", "waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper", "waxed_oxidized_cut_copper")
				.wood("cherry", "oak", "spruce", "pale_oak"));
		DRESSES.put("nether", new Dress()
				.exact("crimson_nylium", "grass_block").exact("warped_nylium", "moss_block").exact("netherrack", "coarse_dirt", "dirt", "stone")
				.exact("soul_soil", "rooted_dirt").exact("air", "moss_carpet", "vine", "dandelion", "azure_bluet", "oxeye_daisy", "pink_petals", "red_tulip")
				.exact("crimson_roots!", "short_grass").exact("warped_roots!", "fern").exact("crimson_fungus!", "poppy", "azalea")
				.exact("warped_fungus!", "cornflower", "blue_orchid", "flowering_azalea").exact("potted_crimson_fungus!", POTTED)
				.exact("nether_wart_block!", "oak_leaves").exact("warped_wart_block!", "azalea_leaves").exact("shroomlight!", "flowering_azalea_leaves")
				.exact("basalt", "tuff").exact("nether_quartz_ore", "diorite").exact("bone_block", "calcite").exact("smooth_basalt", "dripstone_block")
				.exact("blackstone!", "deepslate").exact("soul_lantern", "lantern").exact("soul_campfire", "campfire")
				.exact("cracked_nether_bricks", "cracked_stone_bricks", "cracked_deepslate_tiles")
				.fam("nether_brick", "stone_brick", "mossy_stone_brick", "mud_brick").fam("blackstone", "cobblestone", "mossy_cobblestone", "andesite", "cobbled_deepslate", "sandstone")
				.fam("polished_blackstone", "polished_andesite", "smooth_stone", "smooth_sandstone").fam("red_nether_brick", "deepslate_tile", "brick")
				.fam("polished_blackstone_brick", "cut_sandstone")
				.wood("crimson", "oak", "spruce", "birch").wood("warped", "dark_oak", "pale_oak", "jungle"));
		DRESSES.put("badlands", new Dress()
				.exact("red_sand", GROUND).exact("terracotta", "dirt", "rooted_dirt", "stone").exact("orange_terracotta", "tuff", "dripstone_block")
				.exact("brown_terracotta!", "deepslate").exact("white_terracotta", "diorite").exact("yellow_terracotta", "calcite")
				.exact("air", "moss_carpet", "vine").exact("air", FLOWERS).exact("dead_bush!", "short_grass", "fern", "azalea", "flowering_azalea")
				.exact("potted_dead_bush!", POTTED).exact("acacia_leaves", LEAVES).exact("chiseled_red_sandstone", "cracked_stone_bricks")
				.fam("mesa_brick", "stone_brick", "mossy_stone_brick").fam("red_sandstone", "cobblestone", "mossy_cobblestone", "andesite", "cobbled_deepslate")
				.fam("mesa_smooth", "polished_andesite", "smooth_stone", "polished_diorite")
				.wood("acacia", "oak", "spruce").wood("birch", "pale_oak"));
		DRESSES.put("jungle", new Dress()
				.exact("jungle_leaves", LEAVES).exact("podzol", "coarse_dirt").exact("blue_orchid", "poppy", "cornflower").exact("fern", "dandelion")
				.exact("potted_fern!", POTTED)
				.fam("mossy_stone_brick", "stone_brick", "polished_andesite").fam("mossy_cobblestone", "cobblestone")
				.exact("mossy_stone_bricks", "cracked_stone_bricks")
				.wood("jungle", "oak", "spruce", "pale_oak").wood("mangrove", "dark_oak"));
		DRESSES.put("deep_dark", new Dress()
				.exact("sculk", GROUND).exact("cobbled_deepslate", "coarse_dirt").exact("deepslate!", "dirt", "rooted_dirt", "stone")
				.exact("air", "moss_carpet", "short_grass", "fern", "azalea", "flowering_azalea", "vine").exact("air", FLOWERS)
				.exact("potted_wither_rose!", POTTED).exact("pale_oak_leaves", LEAVES).exact("smooth_basalt", "calcite")
				.exact("soul_lantern", "lantern").exact("soul_campfire", "campfire").exact("cracked_deepslate_bricks", "cracked_stone_bricks")
				.fam("deepslate_brick", "stone_brick", "mossy_stone_brick").fam("cobbled_deepslate", "cobblestone", "mossy_cobblestone")
				.fam("polished_deepslate", "polished_andesite", "smooth_stone").fam("tuff", "andesite", "diorite", "sandstone")
				.fam("tuff_brick", "brick", "mud_brick", "cut_sandstone").fam("polished_tuff", "smooth_sandstone", "polished_diorite")
				.wood("pale_oak", "oak", "birch").wood("dark_oak", "spruce", "jungle"));
		DRESSES.put("end", new Dress()
				.exact("end_stone", GROUND).exact("end_stone!", "coarse_dirt", "dirt", "rooted_dirt", "stone", "tuff", "diorite", "calcite", "dripstone_block",
						"deepslate", "andesite")
				.exact("air", "moss_carpet", "short_grass", "fern", "azalea", "flowering_azalea", "vine").exact("air", FLOWERS)
				.exact("potted_dead_bush!", POTTED).exact("chorus_flower[age=5]", LEAVES).exact("end_stone_bricks", "cracked_stone_bricks", "cracked_deepslate_tiles")
				.fam("end_stone_brick", "stone_brick", "mossy_stone_brick", "cobblestone", "mossy_cobblestone", "deepslate_brick", "cobbled_deepslate", "mud_brick",
						"andesite", "tuff", "sandstone")
				.fam("purpur_walled", "polished_andesite", "smooth_stone", "deepslate_tile", "polished_deepslate", "brick", "smooth_sandstone", "polished_tuff")
				.wood("birch", "oak", "spruce", "pale_oak"));
	}

	private static final Pattern WOOD = Pattern.compile(
			"^(stripped_)?(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak|crimson|warped)_(planks|log|wood|stem|hyphae|stairs|slab|fence|fence_gate|door|trapdoor|leaves|sapling|button|pressure_plate|sign|wall_sign|hanging_sign|wall_hanging_sign)$");

	/**
	 * The block {@code id} ("stone_brick_stairs[facing=north,half=bottom]") in {@code theme}: same shape, same
	 * properties, the theme's material. Anything the theme does not change stays as it is.
	 */
	public static String block(String id, String theme) {
		Dress d = DRESSES.get(theme);
		if (d == null || (d.exact.isEmpty() && d.fams.isEmpty() && d.woods.isEmpty())) return id;
		int open = id.indexOf('[');
		String name = open < 0 ? id : id.substring(0, open);
		String props = open < 0 ? "" : id.substring(open);
		String to = d.exact.get(name);
		if (to != null) return withProps(to, props);
		Object[] v = VARIANT.get(name);
		if (v != null) {
			String fam = d.fams.get((String) v[0]);
			if (fam != null) {
				Fam f = FAMS.get(fam);
				String n = f.variant((int) v[1]);
				// a family without that shape gives its full block (a wall's or stair's properties do not fit it)
				return n != null ? n + props : f.block();
			}
			return id;
		}
		Matcher m = WOOD.matcher(name);
		if (m.matches()) {
			String target = d.woods.get(m.group(2));
			if (target == null) return id;
			return wood(m.group(1) != null, target, m.group(3)) + props;
		}
		return id;
	}

	/** "!" drops the old properties (another kind of block), properties written in the target replace them. */
	private static String withProps(String to, String props) {
		if (to.endsWith("!")) return to.substring(0, to.length() - 1);
		if (to.contains("[") || to.equals("air")) return to;
		return to + props;
	}

	private static boolean nether(String wood) {
		return wood.equals("crimson") || wood.equals("warped");
	}

	/** A wood's part in another wood: the nether woods have stems and hyphae where trees have logs and wood. */
	private static String wood(boolean stripped, String wood, String part) {
		if (nether(wood)) {
			if (part.equals("log")) part = "stem";
			else if (part.equals("wood")) part = "hyphae";
			else if (part.equals("leaves")) return wood.equals("crimson") ? "nether_wart_block" : "warped_wart_block";
			else if (part.equals("sapling")) return wood + "_fungus";
		} else {
			if (part.equals("stem")) part = "log";
			else if (part.equals("hyphae")) part = "wood";
		}
		return (stripped ? "stripped_" : "") + wood + "_" + part;
	}

	/** Every theme this city can wear, for the tests. */
	static Map<String, Fam> families() {
		return FAMS;
	}
}
