package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.extremedimensions.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The three arenas, after their concepts. Each is a round sigil inlaid into the ground round the Elder, edged with a
 * low rim and lit pillars, with eight paths (bridges where the ground falls away) running out to eight towers, and
 * broken islands hanging over it trailing spikes and lanterns; chains or threads are strung between the towers.
 *
 * <ul>
 * <li>The Matriarch's is obsidian and blackstone: a star of obsidian with a ring of crying obsidian and an amethyst
 * heart, roots and moss creeping in at its edge, lantern towers whose cores burn violet, islands of blackstone with
 * moss on top and crystals beneath, and iron chains.</li>
 * <li>The Ferryman's is blackstone and nether brick round a whirlpool of red nether brick and wart lit with shroomlight,
 * ringed by a deck of crimson planks; his towers are gallows posts with lanterns on chains, the great ones with a boat
 * hung between two posts; his islands are basalt and blackstone glowing with magma beneath.</li>
 * <li>The Weaver's is deepslate inlaid with a web: spokes of calcite, rings of amethyst and sea lanterns where they
 * cross; its towers are thin spires with lit cages and cobwebs, its islands deepslate veined with amethyst trailing end
 * rods, and threads of light run from spire to spire and down to the heart of the web.</li>
 * </ul>
 *
 * Sizes scale with {@code elder_arena_size}: the sigil's rings and spokes spread out with it (and grow broader, more
 * slowly), the towers grow taller, a second ring of towers stands beyond the first, and the islands grow in number, in
 * size, in height and in how far out they hang. Towers, rim pillars and islands are all or nothing: one that would not
 * fit in the air there is left out, and so is a tower or pillar that would rise right beside a player.
 */
final class ArenaDesign {
	private ArenaDesign() {}

	/** A chain or thread from one point to another, sagging by {@code sag} at its middle. */
	record Line(double x0, double y0, double z0, double x1, double y1, double z1, double sag) {}

	/** An island hung over an arena: its middle, the top of it, its breadth and its group. */
	record Isle(int x, int top, int z, double r, int group) {}

	/** What a group of an arena's blocks is: the floor (it never comes down), a building, an island, a core, grown later. */
	static final int FLOOR = 0, BUILT = 1, ISLE = 2, CORE = 3, GROWN = 4;

	/**
	 * An arena's blocks in the order they come, and its chains; what each group of them is; its islands and cores; and
	 * the lie of its floor (its Elder, middle, height, radius, turn, size and breadth), for what happens on it in a fight.
	 */
	static final class Blueprint {
		final List<ElderArena.Piece> pieces = new ArrayList<>();
		final List<Line> lines = new ArrayList<>();
		final List<Integer> kinds = new ArrayList<>();
		final List<Isle> isles = new ArrayList<>();
		final List<List<BlockPos>> cores = new ArrayList<>();
		final java.util.Set<Integer> arches = new java.util.HashSet<>();
		int style, cx, gy, cz, floorR;
		double turn, size = 1, wide = 1;
	}

	private static final double TAU = Math.PI * 2;
	private static final Map<String, BlockState> LOOKUP = new java.util.concurrent.ConcurrentHashMap<>();

	// ------------------------------------------------------------------ blocks

	/** A vanilla block by id, looked up at runtime (block constants come and go between versions). */
	private static BlockState mc(String id, BlockState fallback) {
		BlockState s = LOOKUP.computeIfAbsent(id, k -> BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", k))
				.map(h -> h.value().defaultBlockState()).orElse(Blocks.AIR.defaultBlockState()));
		return s.isAir() ? fallback : s;
	}

	private static BlockState mc(String id) {
		return mc(id, Blocks.STONE.defaultBlockState());
	}

	private static BlockState facing(BlockState s, Direction d) {
		return s.hasProperty(BlockStateProperties.FACING) ? s.setValue(BlockStateProperties.FACING, d) : s;
	}

	private static BlockState axis(BlockState s, Direction.Axis a) {
		return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}

	private static BlockState hanging(BlockState s) {
		return s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, true) : s;
	}

	private static BlockState slab(BlockState s, boolean top) {
		return s.hasProperty(BlockStateProperties.SLAB_TYPE) ? s.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : s;
	}

	/** Stairs facing a direction (the tall side), the right way up or upside down. */
	private static BlockState stairs(BlockState s, Direction d, boolean upsideDown) {
		if (!s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return s;
		}
		BlockState r = s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		return r.hasProperty(BlockStateProperties.HALF) ? r.setValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM) : r;
	}

	private static BlockState chain() {
		return mc("iron_chain", mc("chain", Blocks.COBWEB.defaultBlockState()));
	}

	private static BlockState lantern() {
		return hanging(mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState()));
	}

	/** What an arena's blocks trail as they fly: violet, fire, light. */
	static String spark(int style) {
		return switch (style) {
			case 0 -> "minecraft:reverse_portal";
			case 1 -> "minecraft:flame";
			default -> "minecraft:end_rod";
		};
	}

	/** The sound of blocks setting off. */
	static String whoosh(int style) {
		return switch (style) {
			case 0 -> "minecraft:block.rooted_dirt.break";
			case 1 -> "minecraft:item.firecharge.use";
			default -> "minecraft:block.amethyst_block.chime";
		};
	}

	/** What the chains between the towers are made of: iron, or the Weaver's threads of light. */
	static BlockState strand(int style) {
		return style == 2 ? mc("amethyst_block") : chain();
	}

	/** An Elder's stone and light. */
	private static final class Palette {
		BlockState[] floor;
		BlockState line, glow, gem, border, rim, rimSlab, pillar, pillarTop, deck, deckLine;
		BlockState base, corner, shaft, wall, core, cap, tip;
		BlockState isleTop, isle, isleVein, isleTip, under;
		/** What else tops an island here and there, and what grows on it. */
		BlockState isleTop2, isleTop3, decor;
	}

	private static Palette palette(int style) {
		Palette p = new Palette();
		switch (style) {
			case 0 -> {
				p.floor = new BlockState[] {mc("polished_blackstone_bricks"), mc("cracked_polished_blackstone_bricks"), mc("polished_blackstone")};
				p.line = mc("obsidian");
				p.glow = mc("crying_obsidian");
				p.gem = mc("amethyst_block");
				p.border = mc("chiseled_polished_blackstone");
				p.rim = mc("polished_blackstone_bricks");
				p.rimSlab = slab(mc("polished_blackstone_brick_slab"), false);
				p.pillar = mc("polished_blackstone_bricks");
				p.pillarTop = mc("crying_obsidian");
				p.deck = mc("polished_blackstone_bricks");
				p.deckLine = mc("obsidian");
				p.base = mc("polished_blackstone_bricks");
				p.corner = mc("chiseled_polished_blackstone");
				p.shaft = mc("obsidian");
				p.wall = mc("polished_blackstone_brick_wall");
				p.core = mc("crying_obsidian");
				p.cap = mc("polished_blackstone");
				p.tip = facing(mc("amethyst_cluster"), Direction.UP);
				p.isleTop = mc("moss_block");
				p.isle = mc("blackstone");
				p.isleVein = mc("crying_obsidian");
				p.isleTip = facing(mc("amethyst_cluster"), Direction.DOWN);
				p.under = mc("hanging_roots", mc("blackstone_wall"));
				p.isleTop2 = mc("mossy_cobblestone");
				p.isleTop3 = mc("rooted_dirt");
				p.decor = mc("moss_carpet");
			}
			case 1 -> {
				p.floor = new BlockState[] {mc("polished_blackstone_bricks"), mc("blackstone"), mc("nether_bricks")};
				p.line = mc("red_nether_bricks");
				p.glow = mc("shroomlight");
				p.gem = mc("nether_wart_block");
				p.border = mc("nether_bricks");
				p.rim = mc("nether_bricks");
				p.rimSlab = slab(mc("nether_brick_slab"), false);
				p.pillar = mc("nether_bricks");
				p.pillarTop = mc("shroomlight");
				p.deck = mc("crimson_planks");
				p.deckLine = axis(mc("stripped_crimson_stem"), Direction.Axis.Y);
				p.base = mc("polished_blackstone_bricks");
				p.corner = mc("chiseled_polished_blackstone");
				p.shaft = axis(mc("crimson_stem"), Direction.Axis.Y);
				p.wall = mc("nether_brick_fence");
				p.core = mc("shroomlight");
				p.cap = mc("crimson_planks");
				p.tip = mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState());
				p.isleTop = mc("crimson_nylium");
				p.isle = axis(mc("basalt"), Direction.Axis.Y);
				p.isleVein = mc("magma_block");
				p.isleTip = mc("shroomlight");
				p.under = mc("blackstone_wall");
				p.isleTop2 = mc("netherrack");
				p.isleTop3 = mc("nether_wart_block");
				p.decor = mc("crimson_roots");
			}
			default -> {
				p.floor = new BlockState[] {mc("deepslate_tiles"), mc("polished_deepslate"), mc("cracked_deepslate_tiles")};
				p.line = mc("calcite");
				p.glow = mc("sea_lantern");
				p.gem = mc("amethyst_block");
				p.border = mc("deepslate_bricks");
				p.rim = mc("deepslate_bricks");
				p.rimSlab = slab(mc("deepslate_tile_slab"), false);
				p.pillar = mc("deepslate_bricks");
				p.pillarTop = facing(mc("end_rod"), Direction.UP);
				p.deck = mc("deepslate_tiles");
				p.deckLine = mc("calcite");
				p.base = mc("deepslate_bricks");
				p.corner = mc("chiseled_deepslate");
				p.shaft = mc("deepslate_tiles");
				p.wall = mc("deepslate_tile_wall");
				p.core = mc("sea_lantern");
				p.cap = mc("polished_deepslate");
				p.tip = facing(mc("end_rod"), Direction.UP);
				p.isleTop = mc("polished_deepslate");
				p.isle = mc("cobbled_deepslate");
				p.isleVein = mc("amethyst_block");
				p.isleTip = facing(mc("end_rod"), Direction.DOWN);
				p.under = mc("cobweb");
				p.isleTop2 = mc("deepslate_tiles");
				p.isleTop3 = mc("calcite");
				p.decor = facing(mc("small_amethyst_bud"), Direction.UP);
			}
		}
		return p;
	}

	// ------------------------------------------------------------------ the plan

	/** A design in progress: pieces in the order they will come, built up a structure at a time. */
	private static final class Plan {
		final ServerLevel level;
		final boolean grief;
		final Random rnd;
		final LinkedHashMap<Long, ElderArena.Piece> pieces = new LinkedHashMap<>();
		/** Where the players near stand (x, z): no tower or pillar rises right beside one of them. */
		final List<double[]> people = new ArrayList<>();
		LinkedHashMap<Long, ElderArena.Piece> group;
		/** What the structure in progress is (see {@link #FLOOR}), and what each kept one was, by group. */
		int kind = BUILT;
		final List<Integer> kinds = new ArrayList<>();
		/** The group the last structure kept became, or -1 if it was left out. */
		int lastGroup = -1;
		final List<Isle> isles = new ArrayList<>();
		final List<List<BlockPos>> cores = new ArrayList<>();
		final java.util.Set<Integer> arches = new java.util.HashSet<>();

		Plan(ServerLevel level, boolean grief, long seed) {
			this.level = level;
			this.grief = grief;
			this.rnd = new Random(seed);
		}

		void begin() {
			group = new LinkedHashMap<>();
			kind = BUILT;
		}

		/** Whether the chunk of (x, z) is loaded (a design never loads one to look at it). */
		boolean loaded(int x, int y, int z) {
			return level.isLoaded(new BlockPos(x, y, z));
		}

		/** Whether someone stands within {@code r} blocks (across the ground) of the middle of block column (x, z). */
		boolean crowded(int x, int z, double r) {
			for (double[] q : people) {
				double dx = q[0] - (x + 0.5), dz = q[1] - (z + 0.5);
				if (dx * dx + dz * dz < r * r) {
					return true;
				}
			}
			return false;
		}

		/** A block into the air (or plants) there. */
		void air(int x, int y, int z, BlockState s) {
			if (s == null || s.isAir()) {
				return;
			}
			BlockPos pos = new BlockPos(x, y, z);
			long k = pos.asLong();
			if (!pieces.containsKey(k)) {
				group.putIfAbsent(k, new ElderArena.Piece(pos, s, ElderArena.AIR, null));
			}
		}

		/** The lowest block of the structure in progress in column (x, z), or {@code fallback} if it has none there. */
		int lowest(int x, int z, int fallback) {
			int y = Integer.MAX_VALUE;
			for (ElderArena.Piece p : group.values()) {
				if (p.pos.getX() == x && p.pos.getZ() == z) {
					y = Math.min(y, p.pos.getY());
				}
			}
			return y == Integer.MAX_VALUE ? fallback : y;
		}

		/** A block inlaid in place of the ground block at {@code pos} (only where the world may be torn up). */
		void inlay(BlockPos pos, BlockState s) {
			if (!grief || s == null || pieces.containsKey(pos.asLong())) {
				return;
			}
			BlockState was = level.getBlockState(pos);
			if (!inlayable(pos, was) || was == s) {
				return;
			}
			group.put(pos.asLong(), new ElderArena.Piece(pos, s, ElderArena.GROUND, was));
		}

		private boolean inlayable(BlockPos pos, BlockState s) {
			if (s.isAir() || s.liquid() || s.hasBlockEntity() || s.is(BlockTags.WITHER_IMMUNE) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)
					|| !s.isCollisionShapeFullBlock(level, pos)) {
				return false;
			}
			float hard = s.getDestroySpeed(level, pos);
			return hard >= 0 && hard < 30;
		}

		/** Whether a piece has room now (beyond the world loaded now, it is kept: it waits for its part of the world, and is looked at then). */
		boolean room(ElderArena.Piece p) {
			if (level.isOutsideBuildHeight(p.pos) || !ElderArena.free(level, p.pos)) {
				return false;
			}
			if (!level.isLoaded(p.pos)) {
				return true;
			}
			if (p.kind == ElderArena.GROUND) {
				return true;
			}
			BlockState s = level.getBlockState(p.pos);
			return s.isAir() || s.canBeReplaced() && !s.liquid() && !s.hasBlockEntity();
		}

		/** Keeps the structure if at least {@code share} of it has room (the rest is left out); false if dropped. */
		boolean commit(double share) {
			LinkedHashMap<Long, ElderArena.Piece> g = group;
			group = null;
			if (g == null || g.isEmpty()) {
				lastGroup = -1;
				return false;
			}
			int ok = 0;
			for (ElderArena.Piece p : g.values()) {
				ok += room(p) ? 1 : 0;
			}
			if (ok < share * g.size()) {
				lastGroup = -1;
				return false;
			}
			lastGroup = kinds.size();
			kinds.add(kind);
			for (Map.Entry<Long, ElderArena.Piece> e : g.entrySet()) {
				if (room(e.getValue())) {
					e.getValue().group = lastGroup;
					pieces.put(e.getKey(), e.getValue());
				}
			}
			return true;
		}

		/** The ground under (x, z) near {@code y}: the top solid block, skipping plants and leaves; null over the void, water or lava, or a tree. */
		BlockPos ground(int x, int y, int z) {
			if (!loaded(x, y, z)) {
				return null;
			}
			for (int by = y + 6; by >= y - 10; by--) {
				BlockPos pos = new BlockPos(x, by, z);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.is(BlockTags.LEAVES) || s.canBeReplaced() && !s.liquid()) {
					continue;
				}
				if (s.liquid() || s.is(BlockTags.LOGS)) {
					return null;
				}
				return pos;
			}
			return null;
		}
	}

	// ------------------------------------------------------------------ the design

	static Blueprint design(ServerLevel level, int style, BlockPos centre, boolean grief, long seed) {
		Plan plan = new Plan(level, grief, seed);
		double size = Math.max(0.5, Math.min(12.0, Config.ELDER_ARENA_SIZE));
		// Where things are spreads with the size; how broad they are grows only with its root
		double wide = Math.sqrt(Math.max(1, size));
		int floorR = (int) Math.round(12 * size);
		int rimR = floorR + 1;
		int towerR = (int) Math.round(18 * size);
		int outerR = (int) Math.round(23.5 * size);
		for (net.minecraft.server.level.ServerPlayer q : level.players()) {
			double near = Math.max(64, outerR + 16);
			if (!q.isSpectator() && q.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5) < near * near) {
				plan.people.add(new double[] {q.getX(), q.getZ()});
			}
		}
		Palette pal = palette(style);
		int cx = centre.getX(), cz = centre.getZ();
		BlockPos g0 = plan.ground(cx, centre.getY(), cz);
		int gy = g0 != null ? g0.getY() : centre.getY() - 1;
		// Taller towers in a larger arena (the lesser ones half as much), up to 30 blocks taller
		int lift = (int) Math.round(5 * Math.min(6, Math.max(0, size - 1)));
		double turn = plan.rnd.nextDouble() * TAU / 8;
		Blueprint bp = new Blueprint();

		floor(plan, pal, style, cx, gy, cz, floorR, turn, size, wide);
		rim(plan, pal, cx, gy, cz, rimR, turn, wide);
		if (size >= 3 && Config.ELDER_ARENA_WALL > 0) {
			wall(plan, pal, cx, gy, cz, floorR + 4, turn, size, wide);
		}
		List<double[]> tops = new ArrayList<>();
		for (int k = 0; k < 8; k++) {
			double a = turn + TAU * k / 8;
			path(plan, pal, cx, gy, cz, a, rimR + 1, towerR - 2, (int) Math.round(wide));
			double[] top = tower(plan, pal, style, cx + Math.cos(a) * towerR, gy, cz + Math.sin(a) * towerR, a, k % 2 == 0, k % 2 == 0 ? lift : lift / 2);
			tops.add(top);
		}
		if (size >= 2) {
			// A second ring beyond the first, between them, the great ones where the lesser stand within
			for (int k = 0; k < 8; k++) {
				double a = turn + TAU * (k + 0.5) / 8;
				tower(plan, pal, style, cx + Math.cos(a) * outerR, gy, cz + Math.sin(a) * outerR, a, k % 2 != 0, k % 2 != 0 ? lift : lift / 2);
			}
		}
		if (size >= 3 && Config.ELDER_ARENA_LANDMARK > 0) {
			List<int[]> origins = switch (style) {
				case 0 -> rootArches(plan, cx, gy, cz, floorR, turn, wide);
				case 1 -> wreck(plan, bp, cx, gy, cz, floorR, turn);
				default -> loom(plan, bp, pal, cx, gy, cz, floorR, turn, wide);
			};
			if (Config.ELDER_CORES > 0) {
				for (int[] o : origins) {
					core(plan, coreBlock(style), o[0], o[1], o[2]);
				}
			}
		}
		// More islands in a larger arena, larger, higher and further out, hung in three layers: many small ones low
		// over the ground, larger ones above them, and the greatest highest of all
		int isles = (int) Math.round(size <= 4 ? Math.max(7 * size, 3.75 * size * size) : 60 + 16 * (size - 4));
		int span = (int) Math.round(9 + 34 * Math.min(11, Math.max(0, size - 1)) / 3);
		double big = Math.min(1, Math.max(0, size - 1) / 11);
		for (int i = 0; i < isles; i++) {
			double a = turn + TAU * (i + 0.5) / isles + (plan.rnd.nextDouble() - 0.5) * 0.4;
			int layer = size < 3 ? -1 : i % 20 < 10 ? 0 : i % 20 < 17 ? 1 : 2;
			double r, grow;
			int up;
			if (layer < 0) {
				r = size <= 1 ? (7 + plan.rnd.nextDouble() * (towerR - 4)) * size : floorR * 0.5 + plan.rnd.nextDouble() * (towerR * 1.55 - floorR * 0.5);
				grow = 1 + plan.rnd.nextDouble() * Math.max(0, size - 1) * 0.8;
				up = plan.rnd.nextInt(span + 1);
			} else {
				double[] reach = {floorR * 0.4, towerR * 1.1, floorR * 0.6, towerR * 1.35, floorR * 0.8, towerR * 1.6};
				double[] grows = {1, 1 + 1.5 * big, 1 + big, 1 + 3.5 * big, 1 + 2.5 * big, 1 + 5.5 * big};
				double[] heights = {0, 0.3, 0.3, 0.65, 0.65, 1.0};
				r = reach[layer * 2] + plan.rnd.nextDouble() * (reach[layer * 2 + 1] - reach[layer * 2]);
				grow = grows[layer * 2] + plan.rnd.nextDouble() * (grows[layer * 2 + 1] - grows[layer * 2]);
				up = (int) Math.round(span * (heights[layer * 2] + plan.rnd.nextDouble() * (heights[layer * 2 + 1] - heights[layer * 2])));
			}
			isle(plan, pal, style, (int) Math.round(cx + Math.cos(a) * r), gy + 12 + up, (int) Math.round(cz + Math.sin(a) * r), grow);
		}
		// Chains between the towers that stand (the Matriarch's between her great lantern towers only), and the
		// Weaver's threads from every spire down to the heart of its web
		for (int k = 0; k < 8; k++) {
			double[] a = tops.get(k);
			if (a == null) {
				continue;
			}
			if (style == 0 && k % 2 != 0) {
				continue;
			}
			double[] b = tops.get((k + (style == 0 ? 2 : 1)) % 8);
			if (b != null) {
				bp.lines.add(new Line(a[0], a[1], a[2], b[0], b[1], b[2], (style == 2 ? 1.6 : 3.0) * wide));
			}
			if (style == 2) {
				bp.lines.add(new Line(a[0], a[1], a[2], cx + 0.5, gy + 1.15, cz + 0.5, 1.2 * wide));
			}
		}
		bp.pieces.addAll(plan.pieces.values());
		bp.kinds.addAll(plan.kinds);
		bp.isles.addAll(plan.isles);
		bp.cores.addAll(plan.cores);
		bp.arches.addAll(plan.arches);
		bp.style = style;
		bp.cx = cx;
		bp.gy = gy;
		bp.cz = cz;
		bp.floorR = floorR;
		bp.turn = turn;
		bp.size = size;
		bp.wide = wide;
		return bp;
	}

	// ------------------------------------------------------------------ cores, lanes and threads

	/** What an Elder's cores are made of: a green, a sea-blue and a pale violet light. */
	static BlockState coreBlock(int style) {
		return switch (style) {
			case 0 -> mc("verdant_froglight", mc("shroomlight"));
			case 1 -> mc("sea_lantern");
			default -> mc("pearlescent_froglight", mc("amethyst_block"));
		};
	}

	/** A core: a cube of light two blocks a side, set into the great piece (what was there gives way to it). */
	private static void core(Plan plan, BlockState light, int x, int y, int z) {
		plan.begin();
		plan.kind = CORE;
		List<BlockPos> blocks = new ArrayList<>();
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = 0; dz <= 1; dz++) {
					BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
					plan.pieces.remove(pos.asLong());
					plan.group.put(pos.asLong(), new ElderArena.Piece(pos, light, ElderArena.AIR, null));
					blocks.add(pos);
				}
			}
		}
		if (plan.commit(0.5)) {
			plan.cores.add(blocks);
		}
	}

	/** Which lane of the Ferryman's whirlpool (0 to 4) a spot lies on, or -1 if it is on none (or off the whirlpool). */
	static int lane(Blueprint bp, double x, double z) {
		int c0 = (int) Math.floor(x) - bp.cx, c1 = (int) Math.floor(z) - bp.cz;
		double d = Math.sqrt(c0 * c0 + c1 * c1), k = bp.size;
		if (d < 1.6 * bp.wide || d >= 7.5 * k) {
			return -1;
		}
		double phi = Math.atan2(c1, c0) - bp.turn + d * 0.55 / k;
		double seg = TAU / 5;
		double arm = (((phi % seg) + seg) % seg) / seg;
		return arm < 0.4 ? Math.floorMod((int) Math.floor(phi / seg), 5) : -1;
	}

	/** A spot down the middle of a lane of the Ferryman's whirlpool, {@code d} blocks from its heart: {x, z}. */
	static double[] laneAt(Blueprint bp, int lane, double d) {
		double theta = (lane + 0.2) * TAU / 5 - d * 0.55 / bp.size + bp.turn;
		return new double[] {bp.cx + 0.5 + Math.cos(theta) * d, bp.cz + 0.5 + Math.sin(theta) * d};
	}

	/** How far out the lanes of the Ferryman's whirlpool run. */
	static double laneReach(Blueprint bp) {
		return 7.5 * bp.size;
	}

	/** Whether a spot lies on a thread of the Weaver's web (a ring or a spoke). */
	static boolean thread(Blueprint bp, double x, double z) {
		int c0 = (int) Math.floor(x) - bp.cx, c1 = (int) Math.floor(z) - bp.cz;
		double d = Math.sqrt(c0 * c0 + c1 * c1), k = bp.size, w = bp.wide;
		int r = bp.floorR;
		if (d < 1.6 * w || d > r - 1) {
			return false;
		}
		double step = 3 * k / w;
		double m = d % step;
		boolean ring = d < r - 2 && d > step * 0.5 && Math.min(m, step - m) < 0.5;
		return ring || spoke(Math.atan2(c1, c0) - bp.turn, d, 8 * (int) Math.round(w)) < 0.5;
	}

	// ------------------------------------------------------------------ the wall and the great pieces

	/**
	 * The ring wall round the floor: thick and high in the Elder's own stone, crumbled here and there, slit with windows,
	 * lit along its inner face, crenellated, with an arch over each path. Where the ground falls away it hangs on, where
	 * a hill rises it stops.
	 */
	private static void wall(Plan plan, Palette pal, int cx, int gy, int cz, int r, double turn, double size, double wide) {
		int thick = Math.max(2, (int) Math.round(wide));
		int high = (int) Math.round(9 + 0.9 * size);
		double gap = Math.round(wide) + 3.5;
		int slices = Math.max(48, (int) Math.round(TAU * r / 6));
		List<List<int[]>> bySlice = new ArrayList<>();
		for (int i = 0; i < slices; i++) {
			bySlice.add(new ArrayList<>());
		}
		int out = r + thick;
		for (int dx = -out; dx <= out; dx++) {
			for (int dz = -out; dz <= out; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < r || d >= r + thick) {
					continue;
				}
				double ang = ((Math.atan2(dz, dx) - turn) % TAU + TAU) % TAU;
				bySlice.get(Math.min(slices - 1, (int) (ang / TAU * slices))).add(new int[] {dx, dz});
			}
		}
		for (List<int[]> slice : bySlice) {
			plan.begin();
			for (int[] c : slice) {
				int x = cx + c[0], z = cz + c[1];
				if (!plan.loaded(x, gy, z)) {
					continue;
				}
				double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
				double ang = ((Math.atan2(c[1], c[0]) - turn) % TAU + TAU) % TAU;
				int along = (int) Math.floor(ang * r);
				int layer = (int) (d - r);
				boolean face = layer == 0 || layer == thick - 1;
				BlockPos g = plan.ground(x, gy, z);
				int bottom;
				if (g == null || g.getY() < gy - 6) {
					bottom = gy - 2;
				} else if (g.getY() > gy + 6) {
					continue;
				} else {
					bottom = g.getY() + 1;
				}
				// Broken stretches, where the top has fallen away
				double worn = noise(along / 5, 91);
				int top = gy + high - (worn > 0.8 ? 2 + (int) ((worn - 0.8) * 45) : 0);
				// An arch over each path: open from the ground up to its curve
				double off = spoke(ang, d, 8);
				int open = -1;
				if (off < gap) {
					double k = off / gap;
					open = gy + (int) Math.round((high - 4) * Math.sqrt(1 - k * k));
				}
				for (int y = bottom; y <= top; y++) {
					if (open >= 0 && y <= open) {
						continue;
					}
					// Windows through it, in its upper half
					if (open < 0 && along % 11 < 2 && y >= gy + high / 2 && y < gy + high / 2 + 4) {
						continue;
					}
					BlockState st = y <= gy + 1 ? pal.corner : y == top ? pal.cap
							: layer == 0 && along % 12 == 6 && y == gy + high / 2 - 2 ? pal.core
							: face && noise(x * 3 + y, z * 3 - y) > 0.82 ? pal.floor[1] : pal.base;
					plan.air(x, y, z, st);
				}
				if (face && top == gy + high && open < 0 && (along & 1) == 0) {
					plan.air(x, top + 1, z, pal.wall);
				}
			}
			plan.commit(0.5);
		}
	}

	/** A ball of blocks (a root's section), with moss on what faces the sky. */
	private static void blob(Plan plan, double px, double py, double pz, double r, BlockState body, BlockState top) {
		int ir = (int) Math.ceil(r);
		int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double ex = bx + dx + 0.5 - px, ey = by + dy + 0.5 - py, ez = bz + dz + 0.5 - pz;
					if (ex * ex + ey * ey + ez * ez > r * r) {
						continue;
					}
					boolean up = top != null && ey > r * 0.55 && noise(bx + dx, bz + dz + by + dy) > 0.45;
					plan.air(bx + dx, by + dy, bz + dz, up ? top : body);
				}
			}
		}
	}

	/**
	 * The Matriarch's roots: four great arches of root and bark rise from the edge of her floor and meet high over its
	 * heart, thinner roots winding round each, moss on their backs, lanterns and hanging roots beneath.
	 */
	private static List<int[]> rootArches(Plan plan, int cx, int gy, int cz, int floorR, double turn, double wide) {
		List<int[]> cores = new ArrayList<>();
		BlockState bark = mc("dark_oak_wood"), root = mc("mangrove_roots", mc("dark_oak_wood")), moss = mc("moss_block");
		BlockState hanging = mc("hanging_roots", mc("mangrove_roots"));
		double foot = floorR * 0.8, apex = floorR * 0.45;
		double rb = 1.2 * wide + 1, rt = 0.5 * wide + 1;
		for (int i = 0; i < 4; i++) {
			double a = turn + TAU * (i + 0.5) / 8;
			double ux = Math.cos(a), uz = Math.sin(a);
			plan.begin();
			double t = 0;
			double lastLantern = -Double.MAX_VALUE;
			while (t <= 1) {
				double along = (2 * t - 1) * foot;
				double h = apex * Math.pow(Math.sin(Math.PI * t), 0.75);
				double rr = rt + (rb - rt) * Math.pow(Math.abs(2 * t - 1), 1.5);
				double px = cx + 0.5 + ux * along, py = gy + 0.5 + h, pz = cz + 0.5 + uz * along;
				blob(plan, px, py, pz, rr, bark, moss);
				for (int k = 0; k < 2; k++) {
					double ph = t * 16 + k * Math.PI;
					double sx = -uz * Math.cos(ph) * rr * 1.05, sz = ux * Math.cos(ph) * rr * 1.05, sy = Math.sin(ph) * rr * 1.05;
					blob(plan, px + sx, py + sy, pz + sz, Math.max(0.9, rr * 0.32), root, null);
				}
				// Lanterns on chains and hanging roots beneath it, where it is high enough
				if (h > 12 && along - lastLantern > 9 + rr) {
					lastLantern = along;
					int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
					int under = (int) Math.floor(py - rr) - 1;
					int len = 2 + (int) (noise(bx, bz) * 6);
					for (int c = 0; c < len; c++) {
						plan.air(bx, under - c, bz, axis(chain(), Direction.Axis.Y));
					}
					plan.air(bx, under - len, bz, lantern());
					plan.air(bx + 1, under, bz, hanging);
					plan.air(bx - 1, under, bz + 1, hanging);
				}
				t += Math.max(0.35, rr * 0.45) / (2 * foot + apex);
			}
			if (plan.commit(0.0)) {
				plan.arches.add(plan.lastGroup);
			}
			// A core on the floor just in front of one of its feet (each arch the other one, so they stand all round),
			// clear of the bark so it can be seen and struck
			double at = (i % 2 == 0 ? 1 : -1) * (foot - rb - 3);
			cores.add(new int[] {(int) Math.floor(cx + 0.5 + ux * at - 0.5), gy + 1, (int) Math.floor(cz + 0.5 + uz * at - 0.5)});
		}
		return cores;
	}

	/**
	 * The Ferryman's wreck: a great boat lies broken in two on the deck of his whirlpool, keeled over and half sunk, its
	 * ribs showing, its deck holed, soul lanterns burning in it, and the stump of its mast rigged with chains.
	 */
	private static List<int[]> wreck(Plan plan, Blueprint bp, int cx, int gy, int cz, int floorR, double turn) {
		BlockState plank = mc("dark_oak_planks"), rib = mc("stripped_dark_oak_wood", plank), keel = mc("dark_oak_wood", plank);
		BlockState deck = mc("spruce_planks"), soul = hanging(mc("soul_lantern", Blocks.SOUL_LANTERN.defaultBlockState()));
		double len = floorR * 0.5, beam = len * 0.24, depth = len * 0.2;
		double a = turn + TAU / 16, ring = floorR * 0.55;
		double ox = cx + 0.5 + Math.cos(a) * ring, oz = cz + 0.5 + Math.sin(a) * ring;
		double ux = -Math.sin(a), uz = Math.cos(a), vx = Math.cos(a), vz = Math.sin(a);
		double roll = Math.toRadians(24), cos = Math.cos(roll), sin = Math.sin(roll);
		double base = gy + 1 + depth * 0.65;
		double breakAt = len * 0.08;
		plan.begin();
		for (double u = -len / 2; u <= len / 2; u += 0.5) {
			// Broken in two: a ragged gap across it
			if (Math.abs(u - breakAt) < 2.5 + noise((int) (u * 4), 7) * 2) {
				continue;
			}
			double f = 2 * u / len;
			double half = beam / 2 * Math.sqrt(Math.max(0, 1 - Math.pow(f, 4)));
			double deep = depth * Math.sqrt(Math.max(0, 1 - Math.pow(f, 6)));
			if (half < 0.6 || deep < 0.6) {
				continue;
			}
			boolean ribRow = Math.floorMod((int) Math.round(u), 5) == 0;
			for (double v = -half - 0.5; v <= half + 0.5; v += 0.5) {
				for (double h = -deep - 0.5; h <= 1.5; h += 0.5) {
					double e = v * v / (half * half) + (h < 0 ? h * h / (deep * deep) : 0);
					boolean shell = h < 0 ? e >= 0.74 && e <= 1.0 : Math.abs(Math.abs(v) - half) < 0.6;
					boolean planked = h >= -0.25 && h < 0.25 && Math.abs(v) < half && noise((int) (u * 2), (int) (v * 2) + 40) > 0.3;
					if (!shell && !planked) {
						continue;
					}
					// Keeled over about its length, then set down half sunk
					double rv = v * cos - h * sin, rh = v * sin + h * cos;
					int wx = (int) Math.floor(ox + ux * u + vx * rv), wz = (int) Math.floor(oz + uz * u + vz * rv);
					int wy = (int) Math.floor(base + rh);
					BlockState st = planked && !shell ? deck : h <= -deep + 0.6 && Math.abs(v) < 1 ? keel : ribRow ? rib : plank;
					plan.air(wx, wy, wz, st);
					if (planked && !shell && noise(wx, wz + 9) > 0.96) {
						plan.air(wx, wy - 1, wz, soul);
					}
				}
			}
		}
		// The stump of the mast, keeled over with the boat, a yard across it, chains from the yard to bow and stern
		double mu = -len * 0.2, mast = len * 0.45;
		double tx = 0, ty = 0, tz = 0;
		for (double m = 0; m <= mast; m += 0.5) {
			double rv = -m * sin, rh = m * cos;
			tx = ox + ux * mu + vx * rv;
			ty = base + 1 + rh;
			tz = oz + uz * mu + vz * rv;
			plan.air((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz), keel);
		}
		double yard = beam * 0.6;
		for (double w = -yard; w <= yard; w += 0.5) {
			plan.air((int) Math.floor(tx + ux * w), (int) Math.floor(ty - 2), (int) Math.floor(tz + uz * w), plank);
		}
		plan.air((int) Math.floor(tx + ux * yard), (int) Math.floor(ty - 3), (int) Math.floor(tz + uz * yard), soul);
		plan.air((int) Math.floor(tx - ux * yard), (int) Math.floor(ty - 3), (int) Math.floor(tz - uz * yard), soul);
		plan.commit(0.0);
		for (int end = -1; end <= 1; end += 2) {
			double bx = ox + ux * len / 2 * end, bz = oz + uz * len / 2 * end;
			bp.lines.add(new Line(tx + ux * yard * end, ty - 2, tz + uz * yard * end, bx, base + 1, bz, 2.5));
		}
		// Four cores along its side towards the heart of the whirlpool, clear of the break
		List<int[]> cores = new ArrayList<>();
		for (double f : new double[] {-0.38, -0.18, 0.26, 0.42}) {
			double u = f * len, off = beam / 2 + 2;
			cores.add(new int[] {(int) Math.floor(ox + ux * u - vx * off - 0.5), gy + 1, (int) Math.floor(oz + uz * u - vz * off - 0.5)});
		}
		return cores;
	}

	/**
	 * The Weaver's loom: two great posts either side of the heart of its web, a beam across their tops and another
	 * low down, the warp strung between them in threads of light and the first few threads of weft woven in.
	 */
	private static List<int[]> loom(Plan plan, Blueprint bp, Palette pal, int cx, int gy, int cz, int floorR, double turn, double wide) {
		List<int[]> cores = new ArrayList<>();
		double half = floorR * 0.55;
		int high = (int) Math.round(floorR * 0.5);
		int t = Math.max(2, (int) Math.round(wide * 0.7));
		int bh = Math.max(1, t - 1);
		int breast = (int) Math.round(high * 0.22);
		double a = turn + TAU / 16;
		double ux = Math.cos(a), uz = Math.sin(a);
		BlockState calcite = mc("calcite"), lamp = pal.core, cap = mc("chiseled_deepslate");
		for (int side = -1; side <= 1; side += 2) {
			int px = (int) Math.round(cx + ux * half * side), pz = (int) Math.round(cz + uz * half * side);
			plan.begin();
			for (int dx = -t; dx <= t; dx++) {
				for (int dz = -t; dz <= t; dz++) {
					BlockPos g = plan.ground(px + dx, gy, pz + dz);
					int bottom = g == null || g.getY() < gy - 6 ? gy - 2 : Math.min(gy, g.getY() + 1);
					boolean corner = Math.abs(dx) == t && Math.abs(dz) == t;
					boolean face = Math.abs(dx) == t || Math.abs(dz) == t;
					for (int y = bottom; y <= gy + high; y++) {
						BlockState st = corner ? calcite : face && (dx == 0 || dz == 0) && (y - gy) % 8 == 4 ? lamp : pal.base;
						plan.air(px + dx, y, pz + dz, st);
					}
					plan.air(px + dx, gy + high + 1, pz + dz, cap);
				}
			}
			plan.air(px, gy + high + 2, pz, pal.tip);
			plan.commit(0.0);
			// A core standing against each face of the post, across the loom
			for (int face = -1; face <= 1; face += 2) {
				double off = (t + 1.5) * face;
				cores.add(new int[] {(int) Math.floor(px + 0.5 - uz * off - 0.5), gy + 1, (int) Math.floor(pz + 0.5 + ux * off - 0.5)});
			}
		}
		// The beams, post to post
		plan.begin();
		double vx = -uz, vz = ux;
		for (double s = -half; s <= half; s += 0.5) {
			for (int w = -bh; w <= bh; w++) {
				for (int y = -bh; y <= bh; y++) {
					int x0 = (int) Math.floor(cx + 0.5 + ux * s + vx * w), z0 = (int) Math.floor(cz + 0.5 + uz * s + vz * w);
					boolean edge = Math.abs(w) == bh && Math.abs(y) == bh;
					plan.air(x0, gy + high - bh + y, z0, edge ? calcite : pal.shaft);
					plan.air(x0, gy + breast + y, z0, edge ? calcite : pal.shaft);
				}
			}
		}
		plan.commit(0.0);
		// The warp, top beam to breast beam, and the first threads of weft woven across it
		double inner = half - t - 1.5;
		int warp = 24;
		for (int i = 0; i < warp; i++) {
			double s = -inner + 2 * inner * (i + 0.5) / warp;
			double x = cx + 0.5 + ux * s, z = cz + 0.5 + uz * s;
			bp.lines.add(new Line(x, gy + high - bh - 0.5, z, x, gy + breast + bh + 1, z, 0));
		}
		for (int k = 1; k <= 5; k++) {
			double y = gy + breast + bh + 1 + k * 2.5;
			bp.lines.add(new Line(cx + 0.5 - ux * inner, y, cz + 0.5 - uz * inner, cx + 0.5 + ux * inner, y, cz + 0.5 + uz * inner, 0));
		}
		return cores;
	}

	/** Angular distance from the nearest of {@code n} spokes, as blocks across at distance {@code d}. */
	private static double spoke(double theta, double d, int n) {
		double step = TAU / n;
		double m = ((theta % step) + step) % step;
		return Math.min(m, step - m) * d;
	}

	/**
	 * The sigil, inlaid from the middle outwards, its edge ragged where it meets the land; where the ground falls away
	 * (or there is none) it floats on at its own height.
	 */
	private static void floor(Plan plan, Palette pal, int style, int cx, int gy, int cz, int r, double turn, double k, double w) {
		List<int[]> cols = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz <= (r + 0.5) * (r + 0.5)) {
					cols.add(new int[] {dx, dz});
				}
			}
		}
		cols.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		for (int[] c : cols) {
			double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
			if (d > r - 1.5 && plan.rnd.nextDouble() < (d - (r - 1.5)) * 0.35) {
				continue;
			}
			int x = cx + c[0], z = cz + c[1];
			if (!plan.loaded(x, gy, z)) {
				continue;
			}
			BlockPos g = plan.ground(x, gy, z);
			if (g != null && g.getY() > gy + 4) {
				// A hill rises through it: the sigil stops at its foot
				continue;
			}
			double theta = Math.atan2(c[1], c[0]) - turn;
			BlockState s = switch (style) {
				case 0 -> matriarchFloor(plan, pal, d, theta, r, c, k, w);
				case 1 -> ferrymanFloor(plan, pal, d, theta, r, c, k, w);
				default -> weaverFloor(plan, pal, d, theta, r, c, k, w);
			};
			plan.begin();
			plan.kind = FLOOR;
			if (g == null || g.getY() < gy - 4) {
				// No ground near its height (a valley, water, the void): the sigil floats on there
				plan.air(x, gy, z, s);
			} else {
				plan.inlay(g, s);
			}
			plan.commit(1.0);
		}
	}

	private static BlockState base(Plan plan, Palette pal, int[] c) {
		double n = noise(c[0], c[1]);
		return n < 0.6 ? pal.floor[0] : n < 0.8 ? pal.floor[1] : pal.floor[2];
	}

	/**
	 * The floors are drawn at their own size (a radius of 12) and spread out by {@code k}; their lines grow broader by
	 * {@code w}.
	 */
	private static BlockState matriarchFloor(Plan plan, Palette pal, double d, double theta, int r, int[] c, double k, double w) {
		if (d < 1.6 * w) {
			return pal.gem;
		}
		if (Math.abs(d - 3 * k) < 0.6 * w) {
			return pal.glow;
		}
		if (d < 6 * k && spoke(theta, d, 8) < 0.6 * w) {
			return pal.line;
		}
		if (Math.abs(d - 6.5 * k) < 0.55 * w) {
			return pal.line;
		}
		if (Math.abs(d - 9.5 * k) < 0.55 * w) {
			return Math.sin(theta * 12 * w) > 0 ? pal.glow : pal.line;
		}
		if (d > 6.5 * k && spoke(theta, d, 8) < 0.5 * w) {
			return pal.floor[2];
		}
		if (Math.abs(d - (r - 0.5)) < 0.6 * w) {
			return pal.border;
		}
		if (d > r - 2.5 * w && noise(c[0] * 3 + 7, c[1] * 3) > 0.72) {
			// Her roots and moss creep in at the edge
			return noise(c[0], c[1] + 11) > 0.5 ? mc("moss_block") : mc("rooted_dirt");
		}
		return base(plan, pal, c);
	}

	private static BlockState ferrymanFloor(Plan plan, Palette pal, double d, double theta, int r, int[] c, double k, double w) {
		if (d < 1.6 * w) {
			return pal.glow;
		}
		if (d < 7.5 * k) {
			// The whirlpool: five arms winding in
			double phi = theta + d * 0.55 / k;
			double arm = ((phi % (TAU / 5)) + TAU / 5) % (TAU / 5) / (TAU / 5);
			if (arm < 0.28) {
				return Math.abs(arm - 0.14) < 0.05 && ((int) Math.round(d)) % 3 == 0 ? pal.glow : pal.line;
			}
			return arm < 0.4 ? pal.gem : mc("blackstone");
		}
		if (Math.abs(d - 8 * k) < 0.6 * w) {
			return mc("polished_blackstone");
		}
		if (d >= 8 * k + 0.6 * w && d < r - w) {
			return spoke(theta, d, 8) < 0.6 * w ? pal.deckLine : pal.deck;
		}
		if (Math.abs(d - (r - 0.5)) < 0.7 * w) {
			return pal.border;
		}
		return base(plan, pal, c);
	}

	private static BlockState weaverFloor(Plan plan, Palette pal, double d, double theta, int r, int[] c, double k, double w) {
		if (d < 0.8 * w) {
			return pal.glow;
		}
		if (d < 1.6 * w) {
			return mc("chiseled_deepslate");
		}
		// A web: a ring every few blocks and more spokes the larger it is
		double step = 3 * k / w;
		double m = d % step;
		boolean ring = d < r - 2 && d > step * 0.5 && Math.min(m, step - m) < 0.5;
		boolean sp = spoke(theta, d, 8 * (int) Math.round(w)) < 0.5;
		if (ring && sp) {
			return pal.glow;
		}
		if (sp && d < r - 1) {
			return pal.line;
		}
		if (ring) {
			return pal.gem;
		}
		if (Math.abs(d - (r - 0.5)) < 0.6 * w) {
			return ((int) Math.round(theta * 8 / TAU * 6)) % 6 == 0 ? mc("chiseled_deepslate") : pal.border;
		}
		return base(plan, pal, c);
	}

	/** The rim: a low edge of slabs with gaps for the paths, and eight lit pillars between them. */
	private static void rim(Plan plan, Palette pal, int cx, int gy, int cz, int r, double turn, double w) {
		int pillars = 8 * (int) Math.round(w);
		int tall = (int) Math.round(3 * w);
		for (int k = 0; k < pillars; k++) {
			double a = turn + TAU * (k + 0.5) / pillars;
			int x = (int) Math.round(cx + Math.cos(a) * r), z = (int) Math.round(cz + Math.sin(a) * r);
			BlockPos g = plan.ground(x, gy, z);
			if (g == null || Math.abs(g.getY() - gy) > 3 || plan.crowded(x, z, 2.5)) {
				continue;
			}
			int y = g.getY() + 1;
			plan.begin();
			for (int i = 0; i < tall - 1; i++) {
				plan.air(x, y + i, z, pal.pillar);
			}
			plan.air(x, y + tall - 1, z, pal.pillarTop);
			if (!pal.pillarTop.hasProperty(BlockStateProperties.FACING)) {
				plan.air(x, y + tall, z, mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState()));
			}
			plan.commit(0.99);
		}
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (Math.abs(d - r) >= 0.5) {
					continue;
				}
				double theta = Math.atan2(dz, dx) - turn;
				if (spoke(theta, d, 8) < 1.6 * w) {
					continue;
				}
				BlockPos g = plan.ground(cx + dx, gy, cz + dz);
				if (g == null || Math.abs(g.getY() - gy) > 3) {
					continue;
				}
				plan.begin();
				plan.air(cx + dx, g.getY() + 1, cz + dz, pal.rimSlab);
				plan.commit(1.0);
			}
		}
	}

	/** A path out to a tower ({@code half} blocks either side of its line), inlaid where the ground is level with it and bridged where it falls away. */
	private static void path(Plan plan, Palette pal, int cx, int gy, int cz, double a, int from, int to, int half) {
		double ux = Math.cos(a), uz = Math.sin(a);
		int h = gy;
		for (int t = from; t <= to; t++) {
			plan.begin();
			boolean bridge = false;
			for (int w = -half; w <= half; w++) {
				int x = (int) Math.round(cx + ux * t - uz * w), z = (int) Math.round(cz + uz * t + ux * w);
				BlockState s = w == 0 ? pal.deckLine : pal.deck;
				BlockPos g = plan.ground(x, h, z);
				if (g != null && Math.abs(g.getY() - h) <= 1) {
					plan.inlay(g, s);
					if (w == 0) {
						h = g.getY();
					}
				} else if (g == null || g.getY() < h - 1) {
					plan.air(x, h, z, s);
					bridge = true;
					if (w == 0 && t % 3 == 0) {
						plan.air(x, h - 1, z, pal.under);
					}
				} else {
					// The ground rises past the path: it ends here
					plan.group = null;
					return;
				}
			}
			plan.commit(bridge ? 0.66 : 0.0);
		}
	}

	/**
	 * A tower at (x, z), on the ground if there is ground near the arena's height, else on a platform of its own
	 * hanging in the air; null if there was no room for it, else the top (where its chains are hung).
	 */
	private static double[] tower(Plan plan, Palette pal, int style, double fx, int gy, double fz, double a, boolean great, int lift) {
		int x = (int) Math.round(fx), z = (int) Math.round(fz);
		BlockPos g = plan.ground(x, gy, z);
		if (g != null && g.getY() > gy + 6 || plan.crowded(x, z, great ? 5.5 : 3.5)) {
			return null;
		}
		boolean floating = g == null || g.getY() < gy - 6;
		int y0 = floating ? gy + 1 : g.getY() + 1;
		plan.begin();
		if (floating) {
			// A platform of its own, a cone of stone hanging under it
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz > 5) {
						continue;
					}
					plan.air(x + dx, y0 - 1, z + dz, pal.base);
					int hang = 1 + (int) Math.round(Math.max(0, 2.5 - Math.sqrt(dx * dx + dz * dz)) * 1.4);
					for (int i = 1; i <= hang; i++) {
						plan.air(x + dx, y0 - 1 - i, z + dz, i == hang && dx == 0 && dz == 0 ? pal.isleTip : pal.isle);
					}
				}
			}
		} else {
			// A footing down to the ground under each column of its base
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos f = plan.ground(x + dx, y0 - 1, z + dz);
					int bottom = f == null ? y0 - 1 : Math.max(y0 - 4, f.getY() + 1);
					for (int y = bottom; y < y0; y++) {
						plan.air(x + dx, y, z + dz, pal.base);
					}
				}
			}
		}
		double[] top = switch (style) {
			case 0 -> lanternTower(plan, pal, x, y0, z, great, lift);
			case 1 -> gallows(plan, pal, x, y0, z, a, great, lift);
			default -> spire(plan, pal, x, y0, z, great, lift);
		};
		return plan.commit(0.85) ? top : null;
	}

	/** The Matriarch's lantern tower: a base, an obsidian shaft, a cage round a burning core, a crystal spire, lanterns hung off its corners. */
	private static double[] lanternTower(Plan plan, Palette pal, int x, int y, int z, boolean great, int lift) {
		if (!great) {
			plan.air(x, y, z, pal.base);
			for (int i = 1; i <= 2 + lift; i++) {
				plan.air(x, y + i, z, pal.wall);
			}
			plan.air(x, y + 3 + lift, z, pal.core);
			plan.air(x, y + 4 + lift, z, pal.tip);
			return new double[] {x + 0.5, y + 3.5 + lift, z + 0.5};
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean corner = dx != 0 && dz != 0;
				plan.air(x + dx, y, z + dz, corner ? pal.corner : pal.base);
				plan.air(x + dx, y + 1, z + dz, corner ? pal.corner : pal.base);
			}
		}
		for (int i = 2; i <= 5 + lift; i++) {
			plan.air(x, y + i, z, pal.shaft);
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			plan.air(x + d.getStepX(), y + 2, z + d.getStepZ(), stairs(mc("polished_blackstone_brick_stairs"), d.getOpposite(), false));
		}
		int c = y + 6 + lift;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				plan.air(x + dx, c, z + dz, pal.base);
				plan.air(x + dx, c + 4, z + dz, pal.cap);
				if (dx != 0 && dz != 0) {
					for (int i = 1; i <= 3; i++) {
						plan.air(x + dx, c + i, z + dz, pal.wall);
					}
				}
			}
		}
		for (int i = 1; i <= 3; i++) {
			plan.air(x, c + i, z, pal.core);
		}
		plan.air(x, c + 5, z, pal.wall);
		plan.air(x, c + 6, z, pal.wall);
		plan.air(x, c + 7, z, pal.tip);
		// Lanterns hung off the cage on short arms
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int ax = x + d.getStepX() * 2, az = z + d.getStepZ() * 2;
			plan.air(ax, c + 4, az, slab(mc("polished_blackstone_brick_slab"), true));
			plan.air(ax, c + 3, az, axis(chain(), Direction.Axis.Y));
			plan.air(ax, c + 2, az, lantern());
		}
		return new double[] {x + 0.5, c + 4.5, z + 0.5};
	}

	/** The Ferryman's gallows post: a lantern on a chain from a beam; the great ones hang a boat between two posts. */
	private static double[] gallows(Plan plan, Palette pal, int x, int y, int z, double a, boolean great, int lift) {
		// The beam points in, towards the arena
		double ix = -Math.cos(a), iz = -Math.sin(a);
		Direction in = Math.abs(ix) > Math.abs(iz) ? (ix > 0 ? Direction.EAST : Direction.WEST) : (iz > 0 ? Direction.SOUTH : Direction.NORTH);
		Direction side = in.getClockWise();
		Direction.Axis beamAxis = in.getAxis();
		if (!great) {
			int t = y + 6 + lift;
			plan.air(x, y, z, pal.base);
			for (int i = y + 1; i <= t; i++) {
				plan.air(x, i, z, pal.shaft);
			}
			for (int i = 1; i <= 2; i++) {
				plan.air(x + in.getStepX() * i, t, z + in.getStepZ() * i, axis(mc("stripped_crimson_stem"), beamAxis));
			}
			int lx = x + in.getStepX() * 2, lz = z + in.getStepZ() * 2;
			plan.air(lx, t - 1, lz, axis(chain(), Direction.Axis.Y));
			plan.air(lx, t - 2, lz, lantern());
			plan.air(x, t + 1, z, pal.tip);
			return new double[] {x + 0.5, t + 0.5, z + 0.5};
		}
		int h = 8 + lift;
		for (int s = -3; s <= 3; s += 6) {
			int px = x + side.getStepX() * s, pz = z + side.getStepZ() * s;
			plan.air(px, y, pz, pal.base);
			plan.air(px, y - 1, pz, pal.base);
			for (int i = 1; i <= h; i++) {
				plan.air(px, y + i, pz, pal.shaft);
			}
			plan.air(px, y + h + 1, pz, pal.tip);
		}
		for (int s = -3; s <= 3; s++) {
			plan.air(x + side.getStepX() * s, y + h, z + side.getStepZ() * s, axis(mc("stripped_crimson_stem"), side.getAxis()));
		}
		// The boat: a hull of slabs with a raised bow and stern, hung on two chains, a lantern burning in it
		int by = y + h - 4;
		for (int s = -1; s <= 1; s += 2) {
			int px = x + side.getStepX() * s, pz = z + side.getStepZ() * s;
			for (int i = 1; i <= 3; i++) {
				plan.air(px, y + h - i, pz, axis(chain(), Direction.Axis.Y));
			}
		}
		for (int s = -2; s <= 2; s++) {
			int px = x + side.getStepX() * s, pz = z + side.getStepZ() * s;
			if (Math.abs(s) == 2) {
				plan.air(px, by, pz, stairs(mc("dark_oak_stairs"), s < 0 ? side : side.getOpposite(), false));
				plan.air(px, by + 1, pz, mc("dark_oak_fence"));
			} else {
				plan.air(px, by, pz, slab(mc("dark_oak_slab"), false));
			}
		}
		plan.air(x, by + 1, z, mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState()));
		return new double[] {x + 0.5, y + h + 0.5, z + 0.5};
	}

	/** The Weaver's spire: a base, a tall shaft with a lit cage, cobwebs, an end-rod point. */
	private static double[] spire(Plan plan, Palette pal, int x, int y, int z, boolean great, int lift) {
		if (!great) {
			plan.air(x, y, z, pal.base);
			for (int i = 1; i <= 4 + lift; i++) {
				plan.air(x, y + i, z, pal.wall);
			}
			plan.air(x, y + 5 + lift, z, pal.core);
			plan.air(x, y + 6 + lift, z, pal.tip);
			return new double[] {x + 0.5, y + 5.5 + lift, z + 0.5};
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				plan.air(x + dx, y, z + dz, dx != 0 && dz != 0 ? pal.corner : pal.base);
				if (dx == 0 || dz == 0) {
					plan.air(x + dx, y + 1, z + dz, pal.base);
				}
			}
		}
		for (int i = 1; i <= 12 + lift; i++) {
			plan.air(x, y + i, z, pal.shaft);
		}
		int c = y + 8 + lift;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int ax = x + d.getStepX(), az = z + d.getStepZ();
			plan.air(ax, c, az, slab(mc("deepslate_tile_slab"), true));
			plan.air(ax, c + 1, az, pal.wall);
			plan.air(ax, c + 2, az, pal.core);
			plan.air(ax, c + 3, az, slab(mc("deepslate_tile_slab"), false));
			plan.air(ax, y + 4, az, mc("cobweb"));
			plan.air(x + d.getStepX() * 2, c + 2, z + d.getStepZ() * 2, facing(mc("end_rod"), d));
		}
		int top = y + 12 + lift;
		plan.air(x, top + 1, z, pal.wall);
		plan.air(x, top + 2, z, pal.wall);
		plan.air(x, top + 3, z, pal.tip);
		plan.air(x, top + 4, z, facing(mc("end_rod"), Direction.UP));
		return new double[] {x + 0.5, top + 0.5, z + 0.5};
	}

	/**
	 * A broken island hanging over the arena: a top of mixed stone (swelling into a low hill on a larger one) with
	 * things growing on it, a cone of stone narrowing to a spike, lanterns on chains; {@code grow} times its usual
	 * breadth (and deeper, a little less).
	 */
	private static void isle(Plan plan, Palette pal, int style, int x, int top, int z, double grow) {
		Random rnd = plan.rnd;
		double r0 = (1.8 + rnd.nextDouble() * 1.6) * grow;
		int depth = (int) Math.round((4 + rnd.nextInt(3)) * Math.pow(grow, 0.8));
		plan.begin();
		plan.kind = ISLE;
		// A larger island's top swells into a low hill
		int dome = (int) Math.min(4, Math.floor(r0 / 4));
		for (int i = 0; i < depth; i++) {
			double ri = r0 * Math.pow(1 - i / (double) depth, 0.9) + 0.35;
			int ir = (int) Math.ceil(ri);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz) + (noise(x + dx, z + dz + i * 7) - 0.5) * 0.9;
					if (d > ri) {
						continue;
					}
					if (i > 0) {
						plan.air(x + dx, top - i, z + dz, noise(x * 3 + dx, top - i + dz * 5) > 0.78 ? pal.isleVein : pal.isle);
						continue;
					}
					int rise = dome <= 0 ? 0 : (int) Math.floor(dome * Math.max(0, 1 - (d / ri) * (d / ri)));
					for (int k = 0; k < rise; k++) {
						plan.air(x + dx, top + k, z + dz, pal.isle);
					}
					// Its top is of more than one stone, and things grow on it here and there
					double n = noise(x * 5 + dx * 3 + 1, z * 5 + dz * 3 + 3);
					plan.air(x + dx, top + rise, z + dz, n < 0.62 ? pal.isleTop : n < 0.82 ? pal.isleTop2 : pal.isleTop3);
					if (noise(x + dx * 7 + 11, z + dz * 7 + 5) > 0.93) {
						plan.air(x + dx, top + rise + 1, z + dz, pal.decor);
					}
				}
			}
		}
		int spike = (int) Math.round((2 + rnd.nextInt(3)) * Math.sqrt(grow));
		for (int i = 0; i < spike; i++) {
			plan.air(x, top - depth - i, z, i == spike - 1 ? pal.isleTip : style == 1 && i % 2 == 0 ? pal.isleVein : pal.isle);
		}
		// Things hanging from under its edge: lanterns on chains, and roots, vines or webs
		int hung = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int hx = x + d.getStepX() * (int) Math.max(1, Math.round(r0 * 0.6)), hz = z + d.getStepZ() * (int) Math.max(1, Math.round(r0 * 0.6));
			int under = plan.lowest(hx, hz, top + 1) - 1;
			if (under >= top) {
				continue;
			}
			if (hung < 2 && rnd.nextBoolean()) {
				int len = 1 + rnd.nextInt(3 + (int) Math.round(2 * (grow - 1)));
				for (int i = 0; i < len; i++) {
					plan.air(hx, under - i, hz, axis(chain(), Direction.Axis.Y));
				}
				plan.air(hx, under - len, hz, lantern());
				hung++;
			} else {
				plan.air(hx, under, hz, pal.under);
			}
		}
		if (plan.commit(0.9)) {
			plan.isles.add(new Isle(x, top, z, r0, plan.lastGroup));
		}
	}

	/** Smooth-ish value noise in [0, 1) for patterns (deterministic by position). */
	private static double noise(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}
}
