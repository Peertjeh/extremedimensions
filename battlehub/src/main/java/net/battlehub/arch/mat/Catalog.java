package net.battlehub.arch.mat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.battlehub.arch.Shape;

/**
 * Everything the generator knows about blocks: the material families (with their forms as they exist in
 * Minecraft 26.3, average texture colours and tags), and the colour and kind of single decoration blocks (for the
 * previews and for connections). The game can add to it at start-up ({@link #discover}), or remove families it
 * does not have ({@link #forget}); a server owner can add or correct entries in the config.
 */
public final class Catalog {
	private Catalog() {}

	/** How a non-family block behaves: whether it fills its block, lets light through, glows. */
	public record Kind(int rgb, boolean full, boolean clear, boolean glow, boolean small) {}

	private static final Map<String, Material> FAMILIES = new LinkedHashMap<>();
	private static final Map<String, Material> BY_BLOCK = new HashMap<>();
	private static final Map<String, Kind> KINDS = new HashMap<>();
	private static final Map<String, Material> NEAREST = new HashMap<>();

	/*
	 * name rgb forms tags [key=value ...]. Forms: c cube, s stairs, h slab, w wall, f fence, p pane, l pillar
	 * (with axis), t trapdoor, d door; "-" for no cube of its own. Keys: alt, aged (comma list), and s/h/w/f/p/l/t/d
	 * to name a form's block when it does not follow the usual naming.
	 */
	private static final String FAMILY_TABLE = """
			stone 7d7d7d csh stone smooth
			cobblestone 7a7a7a cshw stone rough aged=mossy_cobblestone
			mossy_cobblestone 6e765e cshw stone rough mossy
			stone_bricks 7a797a cshw stone brick aged=mossy_stone_bricks,cracked_stone_bricks
			mossy_stone_bricks 737969 cshw stone brick mossy
			cracked_stone_bricks 767576 c stone brick cracked alt=stone_bricks
			chiseled_stone_bricks 777677 c stone carved alt=stone_bricks
			smooth_stone 9f9f9f ch stone smooth alt=stone
			andesite 888889 cshw stone rough
			polished_andesite 848786 csh stone polished aged=andesite
			diorite bdbcbd cshw stone light rough
			polished_diorite c1c1c3 csh stone polished light aged=diorite
			granite 956755 cshw stone warm rough
			polished_granite 9a6a59 csh stone polished warm aged=granite
			calcite dfe0dc c stone light marble smooth alt=polished_diorite
			tuff 6c6d66 cshw stone rough
			polished_tuff 62675f cshw stone polished aged=tuff
			tuff_bricks 62665f cshw stone brick aged=tuff
			chiseled_tuff 5b5e58 c stone carved alt=polished_tuff
			chiseled_tuff_bricks 696b64 c stone carved alt=tuff_bricks
			deepslate 505052 cl stone dark rough l=deepslate alt=cobbled_deepslate
			cobbled_deepslate 4d4d50 cshw stone dark rough
			polished_deepslate 484849 cshw stone dark polished aged=cobbled_deepslate
			deepslate_bricks 464647 cshw stone dark brick aged=cracked_deepslate_bricks
			cracked_deepslate_bricks 424243 c stone dark brick cracked alt=deepslate_bricks
			deepslate_tiles 363637 cshw stone dark tile aged=cracked_deepslate_tiles
			cracked_deepslate_tiles 343435 c stone dark tile cracked alt=deepslate_tiles
			chiseled_deepslate 363637 c stone dark carved alt=deepslate_tiles
			blackstone 2a2429 cshw stone dark rough
			polished_blackstone 353038 cshw stone dark polished
			polished_blackstone_bricks 302a31 cshw stone dark brick aged=cracked_polished_blackstone_bricks
			cracked_polished_blackstone_bricks 2c272c c stone dark brick cracked alt=polished_blackstone_bricks
			chiseled_polished_blackstone 353038 c stone dark carved alt=polished_blackstone
			gilded_blackstone 382b26 c stone dark accent alt=polished_blackstone
			basalt 505156 -l stone dark l=basalt
			polished_basalt 626164 -l stone dark l=polished_basalt
			smooth_basalt 48484e c stone dark smooth alt=polished_deepslate
			sandstone d8cb9b cshw sand warm rough
			cut_sandstone d9ce9f ch sand warm cut aged=sandstone alt=sandstone
			smooth_sandstone dfd6aa csh sand warm smooth aged=sandstone
			chiseled_sandstone d8ca9b c sand warm carved alt=sandstone
			red_sandstone ba631d cshw sand red rough
			cut_red_sandstone b5611f ch sand red cut alt=red_sandstone
			smooth_red_sandstone b5621f csh sand red smooth
			chiseled_red_sandstone b7601c c sand red carved alt=red_sandstone
			quartz_block ebe5de csh marble light s=quartz_stairs h=quartz_slab
			smooth_quartz ebe5de csh marble light smooth
			quartz_bricks eae5dd c marble light brick alt=smooth_quartz
			chiseled_quartz_block e7e2da c marble light carved alt=quartz_block
			quartz_pillar ebe6e0 -l marble light l=quartz_pillar alt=quartz_block
			bricks 966153 cshw brick warm
			mud_bricks 89684f cshw brick warm earth
			packed_mud 8e6a4f c earth warm alt=mud_bricks
			nether_bricks 2c151a cshwf brick dark red f=nether_brick_fence aged=cracked_nether_bricks
			cracked_nether_bricks 281417 c brick dark red cracked alt=nether_bricks
			chiseled_nether_bricks 2f171c c brick dark red carved alt=nether_bricks
			red_nether_bricks 450709 cshw brick dark red
			end_stone dbde9e c pale rough alt=end_stone_bricks
			end_stone_bricks dae0a2 cshw pale brick
			purpur_block a97da9 csh magic purple s=purpur_stairs h=purpur_slab
			purpur_pillar ab81ab -l magic purple l=purpur_pillar alt=purpur_block
			prismarine 639c97 cshw sea rough
			prismarine_bricks 63ab9e csh sea brick
			dark_prismarine 335b4b csh sea dark
			resin_bricks ce6527 cshw warm orange brick
			chiseled_resin_bricks c86224 c warm orange carved alt=resin_bricks
			dripstone_block 866b5c c earth rough
			obsidian 0f0b19 c dark magic
			crying_obsidian 200a3c c dark magic luminous
			amethyst_block 8561bf c magic crystal purple
			waxed_copper_block c06b4f c metal copper aged=waxed_exposed_copper alt=waxed_cut_copper
			waxed_exposed_copper a17d67 c metal copper aged=waxed_weathered_copper alt=waxed_exposed_cut_copper
			waxed_weathered_copper 6c996e c metal copper aged=waxed_oxidized_copper alt=waxed_weathered_cut_copper
			waxed_oxidized_copper 52a284 c metal copper alt=waxed_oxidized_cut_copper
			waxed_cut_copper bf6a50 csh metal copper aged=waxed_exposed_cut_copper
			waxed_exposed_cut_copper 9b7a65 csh metal copper aged=waxed_weathered_cut_copper
			waxed_weathered_cut_copper 6d916b csh metal copper aged=waxed_oxidized_cut_copper
			waxed_oxidized_cut_copper 4f9a7e csh metal copper
			waxed_chiseled_copper b8654b c metal copper carved alt=waxed_cut_copper
			waxed_oxidized_chiseled_copper 4e9a7c c metal copper carved alt=waxed_oxidized_cut_copper
			iron_block dcdcdc c metal
			gold_block f6d03d c metal accent precious
			raw_gold_block dda92e c accent precious
			lapis_block 1e438c c accent blue
			emerald_block 2acb57 c accent green
			netherite_block 423d3f c metal dark
			terracotta 985e43 c earth warm
			white_terracotta d1b2a1 c earth warm light plaster
			light_gray_terracotta 876b62 c earth
			gray_terracotta 392a23 c earth dark
			brown_terracotta 4d3323 c earth dark
			red_terracotta 8f3d2e c earth red
			orange_terracotta a15325 c earth warm orange
			yellow_terracotta ba8523 c earth warm
			cyan_terracotta 565b5b c earth cool
			blue_terracotta 4a3b5b c earth cool
			light_blue_terracotta 716c89 c earth cool
			black_terracotta 251610 c earth dark
			green_terracotta 4c532a c earth
			lime_terracotta 677534 c earth
			purple_terracotta 764656 c earth
			white_concrete cfd5d6 c plaster light smooth
			light_gray_concrete 7d7d73 c plaster smooth
			oak_planks a2824e cshftd wood
			spruce_planks 735531 cshftd wood dark
			birch_planks c0af79 cshftd wood light
			jungle_planks a07351 cshftd wood warm
			acacia_planks a85a32 cshftd wood warm orange
			dark_oak_planks 432b14 cshftd wood dark
			mangrove_planks 753630 cshftd wood red
			cherry_planks e2b2ac cshftd wood pink light
			pale_oak_planks e3d9d6 cshftd wood light
			bamboo_planks c1ad50 cshftd wood light
			crimson_planks 653046 cshftd wood red magic
			warped_planks 2b6863 cshftd wood cool magic
			oak_log 6d5532 -l wood timber l=oak_log
			spruce_log 3a2510 -l wood timber dark l=spruce_log
			birch_log d8d7d2 -l wood timber light l=birch_log
			dark_oak_log 3c2e1a -l wood timber dark l=dark_oak_log
			jungle_log 554419 -l wood timber l=jungle_log
			cherry_log 36212c -l wood timber dark l=cherry_log
			mangrove_log 544324 -l wood timber l=mangrove_log
			stripped_oak_log b19056 -l wood timber light l=stripped_oak_log
			stripped_spruce_log 735934 -l wood timber l=stripped_spruce_log
			stripped_dark_oak_log 604c31 -l wood timber dark l=stripped_dark_oak_log
			stripped_birch_log c4b076 -l wood timber light l=stripped_birch_log
			stripped_cherry_log d79b93 -l wood timber pink l=stripped_cherry_log
			glass dbeef0 cp glass clear
			tinted_glass 2b262e c glass dark
			white_stained_glass ffffff cp glass stained
			orange_stained_glass d87f33 cp glass stained warm
			magenta_stained_glass b24cd8 cp glass stained
			light_blue_stained_glass 6699d8 cp glass stained cool
			yellow_stained_glass e5e533 cp glass stained warm
			lime_stained_glass 7fcc19 cp glass stained
			pink_stained_glass f27fa5 cp glass stained
			gray_stained_glass 4c4c4c cp glass stained dark
			light_gray_stained_glass 999999 cp glass stained
			cyan_stained_glass 4c7f99 cp glass stained cool
			purple_stained_glass 7f3fb2 cp glass stained
			blue_stained_glass 334cb2 cp glass stained cool
			brown_stained_glass 664c33 cp glass stained
			green_stained_glass 667f33 cp glass stained
			red_stained_glass 993333 cp glass stained warm
			black_stained_glass 191919 cp glass stained dark
			iron_bars a0a0a0 -p metal p=iron_bars alt=iron_block
			glowstone ab8354 c light
			sea_lantern acc7be c light cool
			shroomlight f0924a c light warm
			ochre_froglight f2e2a6 -l light warm l=ochre_froglight
			verdant_froglight d9eed2 -l light cool l=verdant_froglight
			pearlescent_froglight f2e6ea -l light l=pearlescent_froglight
			grass_block 6a9a3e c ground organic
			moss_block 596d2d c ground organic mossy
			pale_moss_block a5ad9d c ground organic
			dirt 866043 c ground earth
			coarse_dirt 77553b c ground earth rough
			podzol 5b3f18 c ground earth
			rooted_dirt 90674c c ground earth
			mud 3c393d c ground earth dark
			gravel 837f7e c ground rough
			sand dbcfa3 c ground sand
			dirt_path 947a41 c ground path
			clay a0a6b3 c ground
			snow_block f9fefe c ground light
			hay_block a68826 -l organic warm l=hay_block
			bone_block e5e1cf -l organic light l=bone_block
			mushroom_stem cbc4b9 c organic light plaster
			bookshelf 755e3b c wood interior
			oak_leaves 4a7a29 c leaves organic
			spruce_leaves 3d5e3d c leaves organic dark
			birch_leaves 6a8f43 c leaves organic
			dark_oak_leaves 3b6b1f c leaves organic dark
			azalea_leaves 5f7a2c c leaves organic
			flowering_azalea_leaves 6e7a3c c leaves organic
			cherry_leaves e6aac8 c leaves organic pink
			jungle_leaves 4f8a20 c leaves organic
			pale_oak_leaves a0a597 c leaves organic light
			netherrack 6f3634 c ground dark red nether
			crimson_nylium 852020 c ground red nether
			warped_nylium 2b7265 c ground cool nether
			soul_soil 4b3a2f c ground dark
			soul_sand 51402f c ground dark
			magma_block 8e3f20 c ground warm
			nether_wart_block 732a1e c organic red
			warped_wart_block 167e86 c organic cool
			crimson_stem 5c1d2a -l wood timber dark red l=crimson_stem
			warped_stem 3a3b4e -l wood timber cool l=warped_stem
			packed_ice 8db4fe c ice light cool
			blue_ice 74a8fd c ice cool
			mycelium 6f6265 c ground organic
			red_mushroom_block c42e2c c organic red
			brown_mushroom_block 957051 c organic warm
			sculk 0d1e24 c ground dark
			red_sand be6621 c ground sand red
			acacia_log 676157 -l wood timber l=acacia_log
			stripped_jungle_log ab8456 -l wood timber light l=stripped_jungle_log
			diamond_block 62ede4 c accent precious
			water 3f76e4 c water
			""";

	/** Single blocks: name rgb flags (f full, t lets light through, g glows, m small). */
	private static final String KIND_TABLE = """
			air 000000 t
			lantern 6a5b53 gm
			soul_lantern 4a6a6a gm
			torch ffd060 gm
			wall_torch ffd060 gm
			soul_torch 70d0ff gm
			end_rod e8e6dc gm
			iron_chain 3c3f48 m
			lightning_rod c06b4f m
			bell e0b030 m
			campfire 9a6b3a gm
			soul_campfire 6a8a8a gm
			candle e6d6b0 gm
			white_candle e6e6e6 gm
			flower_pot 7c4536 m
			poppy c02020 m
			dandelion f0d020 m
			cornflower 4060f0 m
			allium b060f0 m
			azure_bluet e0e8f0 m
			oxeye_daisy f0f0e0 m
			lily_of_the_valley f0f0f0 m
			red_tulip d03020 m
			white_tulip e8e8e8 m
			pink_tulip f0a0c0 m
			orange_tulip f08020 m
			short_grass 5f8f35 m
			fern 4f7f30 m
			tall_grass 5f8f35 m
			large_fern 4f7f30 m
			rose_bush b02030 m
			peony e0a0d0 m
			lilac c0a0e0 m
			sweet_berry_bush 3f6f30 m
			vine 3d6b1f m
			glow_lichen 7f9f8f gm
			hanging_roots a07b5b m
			moss_carpet 596d2d m
			pale_moss_carpet a5ad9d m
			pink_petals f0b0d0 m
			wildflowers e8e070 m
			leaf_litter 9a7a40 m
			azalea 5f7a2c m
			flowering_azalea 7f7a5c m
			bush 4f7f30 m
			cobweb e8e8e8 m
			chest 9a6b2a m
			barrel 866439 f
			lectern 9a7a4a m
			enchanting_table 3a2a4a m
			anvil 4a4a4a m
			cauldron 3a3a3a m
			water_cauldron 3f76e4 m
			beacon 75d8d2 g
			conduit a08060 gm
			decorated_pot 9a5a3a m
			ladder 9a7a4a m
			amethyst_cluster b080f0 gm
			large_amethyst_bud a070e0 m
			pointed_dripstone 866b5c m
			skeleton_skull d0d0c0 m
			wither_skeleton_skull 303030 m
			sea_pickle 607020 gm
			brewing_stand 5a5a5a m
			grindstone 7a7a7a m
			smithing_table 3a3030 f
			cartography_table 6a5030 f
			fletching_table c0a060 f
			loom a08060 f
			stonecutter 7a7a7a m
			crafting_table 9a6a3a f
			furnace 6a6a6a f
			blast_furnace 5a5a5a f
			smoker 6a5a4a f
			composter 7a5a30 m
			target e0c0b0 f
			respawn_anchor 2a1a4a g
			lodestone 9a9a9a f
			jack_o_lantern d08020 fg
			carved_pumpkin c07020 f
			pumpkin c07020 f
			melon 70a030 f
			scaffolding c0a060 m
			light_weighted_pressure_plate f0d040 m
			heavy_weighted_pressure_plate d0d0d0 m
			waxed_copper_grate b06a4f ft
			waxed_oxidized_copper_grate 4f9a7e ft
			waxed_copper_bulb c06b4f fg
			waxed_oxidized_copper_bulb 52a284 fg
			redstone_lamp e0a060 fg
			cherry_sapling e0a0c0 m
			potted_red_tulip 7c4536 m
			potted_poppy 7c4536 m
			potted_azure_bluet 7c4536 m
			potted_dandelion 7c4536 m
			potted_oxeye_daisy 7c4536 m
			potted_blue_orchid 7c4536 m
			red_carpet a12722 m
			oak_sapling 4a7a29 m
			crimson_roots 7b0a20 m
			warped_roots 147a6f m
			crimson_fungus a02020 m
			warped_fungus 147a6f m
			nether_sprouts 147a6f m
			red_mushroom c42e2c m
			brown_mushroom 957051 m
			dead_bush 6b4f2a m
			cactus 5b8a2f m
			blue_orchid 2aa0d0 m
			short_dry_grass b8a060 m
			tall_dry_grass b8a060 m
			firefly_bush 4f6f30 gm
			spore_blossom d070a0 m
			sculk_sensor 0d4f5a gm
			snow f9fefe m
			lava d05010 g
			weeping_vines 7b0a20 m
			""";

	static {
		load();
	}

	private static void load() {
		for (String line : FAMILY_TABLE.split("\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;
			parseFamily(line);
		}
		for (String line : KIND_TABLE.split("\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;
			String[] p = line.split("\\s+");
			String fl = p.length > 2 ? p[2] : "";
			KINDS.put(p[0], new Kind(Integer.parseInt(p[1], 16), fl.contains("f"), fl.contains("t"), fl.contains("g"), fl.contains("m")));
		}
	}

	/** The stem a form's name is built on: "stone_bricks" gives "stone_brick_stairs", "oak_planks" "oak_stairs". */
	static String stem(String name) {
		if (name.endsWith("_planks")) return name.substring(0, name.length() - 7);
		if (name.endsWith("_block")) return name.substring(0, name.length() - 6);
		if (name.endsWith("bricks")) return name.substring(0, name.length() - 1);
		if (name.endsWith("tiles")) return name.substring(0, name.length() - 1);
		return name;
	}

	private static void parseFamily(String line) {
		String[] p = line.split("\\s+");
		String name = p[0];
		int rgb = Integer.parseInt(p[1], 16);
		String forms = p[2];
		Set<String> tags = new java.util.HashSet<>();
		Map<String, String> keys = new HashMap<>();
		for (int i = 3; i < p.length; i++) {
			int eq = p[i].indexOf('=');
			if (eq > 0) {
				keys.put(p[i].substring(0, eq), p[i].substring(eq + 1));
			} else {
				tags.add(p[i]);
			}
		}
		String stem = stem(name);
		boolean ownCube = !forms.startsWith("-");
		String cube = ownCube ? name : null;
		String stairs = forms.contains("s") ? keys.getOrDefault("s", stem + "_stairs") : null;
		String slab = forms.contains("h") ? keys.getOrDefault("h", stem + "_slab") : null;
		String wall = forms.contains("w") ? keys.getOrDefault("w", stem + "_wall") : null;
		String fence = forms.contains("f") ? keys.getOrDefault("f", stem + "_fence") : null;
		String pane = forms.contains("p") ? keys.getOrDefault("p", stem + "_pane") : null;
		String pillar = forms.contains("l") ? keys.getOrDefault("l", name) : null;
		String trap = forms.contains("t") ? keys.getOrDefault("t", stem + "_trapdoor") : null;
		String door = forms.contains("d") ? keys.getOrDefault("d", stem + "_door") : null;
		if (cube == null && pillar != null) cube = pillar;
		List<String> aged = keys.containsKey("aged") ? List.of(keys.get("aged").split(",")) : List.of();
		add(new Material(name, rgb, Set.copyOf(tags), cube, stairs, slab, wall, fence, pane, pillar, trap, door, aged, keys.get("alt")));
	}

	public static synchronized void add(Material m) {
		NEAREST.clear();
		FAMILIES.put(m.name(), m);
		for (Shape s : Shape.values()) {
			String id = m.id(s);
			if (id != null) BY_BLOCK.putIfAbsent(id, m);
		}
	}

	/** Families the game turned out not to have are dropped; the generator then uses their stand-ins. */
	public static synchronized void forget(String name) {
		Material m = FAMILIES.remove(name);
		if (m == null) return;
		NEAREST.clear();
		BY_BLOCK.values().removeIf(x -> x == m);
	}

	/**
	 * A block family found in the game that the catalog does not know (blocks from other mods): its colour comes
	 * from the game's map colour and its tags from its name, so palettes that ask by tag can use it.
	 */
	public static synchronized Material discover(String name, int rgb, String stairs, String slab, String wall) {
		if (FAMILIES.containsKey(name)) return FAMILIES.get(name);
		Set<String> tags = new java.util.HashSet<>();
		tags.add("discovered");
		for (String w : new String[] {"brick", "tile", "stone", "wood", "plank", "glass", "copper", "marble", "crystal", "metal"}) {
			if (name.contains(w)) tags.add(w.equals("plank") ? "wood" : w);
		}
		if (Material.luma(rgb) < 0.3) tags.add("dark");
		if (Material.luma(rgb) > 0.7) tags.add("light");
		Material m = new Material(name, rgb, Set.copyOf(tags), name, stairs, slab, wall, null, null, null, null, null, List.of(), null);
		add(m);
		return m;
	}

	public static synchronized Material get(String name) {
		return FAMILIES.get(name);
	}

	/** The family a block belongs to (any of its forms), or null. */
	public static synchronized Material ofBlock(String id) {
		String base = id;
		int open = base.indexOf('[');
		if (open >= 0) base = base.substring(0, open);
		if (base.startsWith("minecraft:")) base = base.substring(10);
		return BY_BLOCK.get(base);
	}

	public static synchronized Collection<Material> families() {
		return List.copyOf(FAMILIES.values());
	}

	/** Families with all of these tags. */
	public static synchronized List<Material> tagged(String... tags) {
		List<Material> out = new ArrayList<>();
		for (Material m : FAMILIES.values()) {
			boolean ok = true;
			for (String t : tags) ok &= m.is(t);
			if (ok) out.add(m);
		}
		return out;
	}

	/**
	 * The family that has a form and looks most like {@code m} (by colour, preferring shared tags): the stand-in
	 * when neither a ramp nor the family's own stand-in has the form (polished diorite has no wall; diorite does).
	 */
	public static synchronized Material nearestWith(Material m, Shape shape) {
		String key = m.name() + "/" + shape;
		if (NEAREST.containsKey(key)) return NEAREST.get(key);
		Material best = null;
		double bestScore = Double.MAX_VALUE;
		for (Material f : FAMILIES.values()) {
			if (!f.has(shape) || f.is("discovered")) continue;
			double score = Material.distance(m.rgb(), f.rgb());
			int shared = 0;
			for (String t : m.tags()) {
				if (f.is(t)) shared++;
			}
			score += shared == 0 ? 0.25 : 0.04 / shared;
			if (score < bestScore) {
				bestScore = score;
				best = f;
			}
		}
		NEAREST.put(key, best);
		return best;
	}

	/** Every block name the catalog can produce (for checking them against the game). */
	public static synchronized Set<String> blockNames() {
		Set<String> out = new java.util.TreeSet<>(BY_BLOCK.keySet());
		out.addAll(KINDS.keySet());
		return out;
	}

	public static synchronized Kind kind(String id) {
		String base = id;
		int open = base.indexOf('[');
		if (open >= 0) base = base.substring(0, open);
		if (base.startsWith("minecraft:")) base = base.substring(10);
		Kind k = KINDS.get(base);
		if (k != null) return k;
		Material m = BY_BLOCK.get(base);
		if (m != null) {
			boolean glass = m.is("glass");
			boolean light = m.is("light") || m.is("luminous");
			boolean full = base.equals(m.cube()) || base.equals(m.pillar());
			return new Kind(m.rgb(), full && !glass, glass || m.is("leaves") || m.is("water"), light, false);
		}
		// Colour families named by colour: banners, carpets, wool, candles
		int c = colourOf(base);
		if (c >= 0) {
			boolean small = base.contains("banner") || base.contains("carpet") || base.contains("candle") || base.contains("bed");
			return new Kind(c, !small, false, base.contains("candle"), small);
		}
		if (base.endsWith("_door") || base.endsWith("_trapdoor") || base.endsWith("_sign") || base.endsWith("_button")) {
			return new Kind(0x8a6a40, false, false, false, true);
		}
		return null;
	}

	public static void putKind(String id, Kind kind) {
		synchronized (Catalog.class) {
			KINDS.put(id, kind);
		}
	}

	private static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
			"cyan", "purple", "blue", "brown", "green", "red", "black"};
	private static final int[] COLOUR_RGB = {0xe9ecec, 0xf07613, 0xbd44b3, 0x3aafd9, 0xf8c627, 0x70b919, 0xed8dac, 0x3e4447, 0x8e8e86,
			0x158991, 0x792aac, 0x35399d, 0x724728, 0x546d1b, 0xa12722, 0x141519};

	private static int colourOf(String base) {
		for (int i = COLOURS.length - 1; i >= 0; i--) {
			// light_blue before blue and light_gray before gray: test the longer names first
			String c = COLOURS[i];
			if (base.startsWith(c + "_")) {
				for (int j = 0; j < COLOURS.length; j++) {
					if (COLOURS[j].length() > c.length() && COLOURS[j].endsWith(c) && base.startsWith(COLOURS[j] + "_")) return COLOUR_RGB[j];
				}
				return COLOUR_RGB[i];
			}
		}
		return -1;
	}

	/** The colour to draw a block in (previews), or grey when unknown. */
	public static int colour(String id) {
		Kind k = kind(id);
		return k == null ? 0x8a8a8a : k.rgb();
	}
}
