package com.extremedimensions.seam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;

/**
 * The architecture of the Seam: colossal buildings assembled from parts (see {@link Colossus}), giant looms, villages
 * of jettied timber houses, towers hanging upside down beneath their islands, tilted halos around floating crystals,
 * broken obelisks, and stone bridges strung between it all.
 *
 * <p>Built the way a builder would: every surface has <b>depth</b> (pilasters, recessed windows with arched heads and
 * sills, corbels under ledges, water tables, stepped buttresses, stair roofs with dormers, layered portals) and every
 * material runs through a <b>gradient</b> up the hush stone's ladder of tones (blackstone at the foot, dark hush bricks
 * in the body, pale hush stone at the crown) with a little dither and weathering, so nothing reads as a flat wall of
 * one block.
 *
 * <p>Everything is designed into a {@link Plan} first and then built a few thousand blocks per tick from a queue,
 * bottom to top, so even the largest spire never stalls the server. Walls, panes and fences are connected to their
 * neighbours when a plan is committed.
 */
public final class SeamArchitect {
	private SeamArchitect() {}

	// ------------------------------------------------------------------ blocks

	/** Shared with the designing thread, so concurrent. */
	private static final Map<String, BlockState> LOOKUP = new java.util.concurrent.ConcurrentHashMap<>();

	/** A vanilla block by id, looked up at runtime (block constants come and go between versions). */
	static BlockState mc(String id, BlockState fallback) {
		BlockState s = LOOKUP.computeIfAbsent(id, k -> BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", k))
				.map(h -> h.value().defaultBlockState()).orElse(null));
		return s != null ? s : fallback;
	}

	static BlockState mc(String id) {
		return mc(id, null);
	}

	static BlockState hanging(BlockState s) {
		return s != null && s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, true) : s;
	}

	static BlockState axis(BlockState s, Direction.Axis a) {
		return s != null && s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}

	/** Stairs facing a direction (the tall side), the right way up or upside down. */
	static BlockState stairs(BlockState s, Direction facing, boolean upsideDown) {
		if (s == null || !s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return s;
		}
		BlockState r = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		return r.hasProperty(BlockStateProperties.HALF) ? r.setValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM) : r;
	}

	static BlockState slab(BlockState s, boolean top) {
		return s != null && s.hasProperty(BlockStateProperties.SLAB_TYPE) ? s.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : s;
	}

	/** Glow lichen growing on the face of the block in the given direction. */
	static BlockState lichen(Direction towardWall) {
		BlockState s = mc("glow_lichen");
		BooleanProperty p = side(towardWall);
		return s != null && p != null && s.hasProperty(p) ? s.setValue(p, true) : null;
	}

	private static BooleanProperty side(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case EAST -> BlockStateProperties.EAST;
			case WEST -> BlockStateProperties.WEST;
			default -> null;
		};
	}

	private static EnumProperty<WallSide> wallSide(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH_WALL;
			case SOUTH -> BlockStateProperties.SOUTH_WALL;
			case EAST -> BlockStateProperties.EAST_WALL;
			default -> BlockStateProperties.WEST_WALL;
		};
	}

	// The hush masonry (the Seam's own stone, from the darkest cobbles to the pale crowns), and the vanilla stone it is
	// set off against: blackstone at the feet of buildings, deepslate in the slate of their roofs.
	static BlockState stone() { return ModBlocks.HUSH_STONE.defaultBlockState(); }
	static BlockState cobble() { return ModBlocks.COBBLED_HUSH_STONE.defaultBlockState(); }
	static BlockState brick() { return ModBlocks.HUSH_BRICKS.defaultBlockState(); }
	static BlockState tile() { return ModBlocks.HUSH_TILES.defaultBlockState(); }
	static BlockState smooth() { return ModBlocks.POLISHED_HUSH_STONE.defaultBlockState(); }
	static BlockState carved() { return ModBlocks.CHISELED_HUSH_BRICKS.defaultBlockState(); }
	static BlockState cracked() { return ModBlocks.CRACKED_HUSH_BRICKS.defaultBlockState(); }
	static BlockState mossy() { return ModBlocks.MOSSY_HUSH_BRICKS.defaultBlockState(); }
	static BlockState silked() { return ModBlocks.SILKED_HUSH_BRICKS.defaultBlockState(); }
	static BlockState pale() { return ModBlocks.PALE_HUSH_BRICKS.defaultBlockState(); }
	static BlockState palePol() { return ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState(); }
	static BlockState pillar() { return ModBlocks.HUSH_PILLAR.defaultBlockState(); }
	static BlockState vein() { return ModBlocks.SEAM_VEIN.defaultBlockState(); }
	static BlockState brickWall() { return ModBlocks.HUSH_BRICK_WALL.defaultBlockState(); }
	static BlockState tileSlab() { return ModBlocks.HUSH_TILE_SLAB.defaultBlockState(); }
	static BlockState tileStairs() { return ModBlocks.HUSH_TILE_STAIRS.defaultBlockState(); }
	static BlockState brickStairs() { return ModBlocks.HUSH_BRICK_STAIRS.defaultBlockState(); }
	static BlockState paleStairs() { return ModBlocks.PALE_HUSH_BRICK_STAIRS.defaultBlockState(); }
	static BlockState paleSlab() { return ModBlocks.PALE_HUSH_BRICK_SLAB.defaultBlockState(); }
	static BlockState paleWall() { return ModBlocks.PALE_HUSH_BRICK_WALL.defaultBlockState(); }
	static BlockState smoothStairs() { return ModBlocks.POLISHED_HUSH_STONE_STAIRS.defaultBlockState(); }
	static BlockState smoothSlab() { return ModBlocks.POLISHED_HUSH_STONE_SLAB.defaultBlockState(); }
	static BlockState palePolStairs() { return ModBlocks.POLISHED_PALE_HUSH_STONE_STAIRS.defaultBlockState(); }
	static BlockState palePolSlab() { return ModBlocks.POLISHED_PALE_HUSH_STONE_SLAB.defaultBlockState(); }
	static BlockState cobbleStairs() { return ModBlocks.COBBLED_HUSH_STONE_STAIRS.defaultBlockState(); }
	static BlockState cobbleWall() { return ModBlocks.COBBLED_HUSH_STONE_WALL.defaultBlockState(); }
	static BlockState bsBrick() { return mc("polished_blackstone_bricks", brick()); }
	static BlockState bsPol() { return mc("polished_blackstone", smooth()); }
	static BlockState bsWall() { return mc("polished_blackstone_brick_wall", brickWall()); }
	static BlockState bsStairs() { return mc("polished_blackstone_brick_stairs", tileStairs()); }
	static BlockState glass() { return mc("purple_stained_glass", vein()); }
	static BlockState glass2() { return mc("magenta_stained_glass", glass()); }
	static BlockState glass3() { return mc("blue_stained_glass", glass()); }
	static BlockState warmPane() { return mc("orange_stained_glass_pane", mc("orange_stained_glass", glass())); }
	static BlockState amethyst() { return mc("amethyst_block", vein()); }
	static BlockState crying() { return mc("crying_obsidian", vein()); }
	static BlockState chain() { return mc("iron_chain", mc("chain", Blocks.COBWEB.defaultBlockState())); }
	static BlockState lantern() { return mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState()); }
	static BlockState soulLantern() { return Blocks.SOUL_LANTERN.defaultBlockState(); }
	static BlockState endRod() { return mc("end_rod", vein()); }
	static BlockState moss() { return mc("moss_block", stone()); }
	static BlockState wool() { return mc("purple_wool", glass()); }
	static BlockState wool2() { return mc("magenta_wool", wool()); }
	static BlockState woolDark() { return mc("black_wool", tile()); }
	static BlockState planks() { return mc("dark_oak_planks", brick()); }
	static BlockState log() { return mc("dark_oak_log", smooth()); }
	static BlockState strippedLog() { return mc("stripped_dark_oak_log", log()); }
	static BlockState plaster() { return mc("purple_terracotta", brick()); }

	// ------------------------------------------------------------------ palettes: gradients, dither, weathering

	/**
	 * Masonry from the foot of a building to its crown, climbing the hush stone's ladder of tones: blackstone at the
	 * foot, dark hush bricks, the lighter tiles, then the pale bricks and the polished pale stone of the crown.
	 */
	static BlockState[] wallGrad() {
		return new BlockState[] {mc("blackstone", bsBrick()), bsBrick(), brick(), brick(), tile(), pale(), palePol()};
	}

	/** Trim, pilasters and frames: polished stone, a shade off the wall behind it, paling with height. */
	static BlockState[] trimGrad() {
		return new BlockState[] {bsPol(), smooth(), smooth(), palePol()};
	}

	/** Island rock, from just under the turf down to the deepest fang. */
	static BlockState[] rockGrad() {
		return new BlockState[] {stone(), mc("deepslate", stone()), cobble(), mc("tuff", stone()), mc("blackstone", stone())};
	}

	/** Picks from a gradient at t (0..1) with dither, so bands blend into each other. */
	static BlockState grad(BlockState[] g, double t, Random rnd) {
		double v = Math.max(0, Math.min(1, t)) * (g.length - 1) + (rnd.nextDouble() - 0.5) * 1.15;
		int i = (int) Math.round(v);
		return g[Math.max(0, Math.min(g.length - 1, i))];
	}

	/** Cracks, moss and wear, here and there. */
	static BlockState weather(BlockState s, Random rnd, double chance) {
		if (s == null || rnd.nextDouble() >= chance) {
			return s;
		}
		if (s == brick()) {
			return rnd.nextInt(3) == 0 ? mossy() : cracked();
		}
		if (s == stone()) {
			return cobble();
		}
		if (s == bsBrick()) {
			return mc("cracked_polished_blackstone_bricks", s);
		}
		return s;
	}

	/** Masonry at a height fraction, weathered. */
	static BlockState wall(double t, Random rnd) {
		return weather(grad(wallGrad(), t, rnd), rnd, 0.12);
	}

	/** Roof stairs: hush tiles mottled with deepslate and blackstone, the way old slate weathers. */
	static BlockState roofStairs(Random rnd) {
		int k = rnd.nextInt(10);
		return k < 5 ? tileStairs() : k < 8 ? mc("deepslate_tile_stairs", tileStairs()) : k < 9 ? bsStairs() : cobbleStairs();
	}

	/** Stained glass for tall windows: violet, with magenta and blue panes worked in. */
	static BlockState stainedGlass(int u, int ly, int top) {
		if (ly >= top - 1) {
			return glass3();
		}
		if ((u + ly) % 4 == 0) {
			return glass2();
		}
		return glass();
	}

	/** Smooth value noise in 0..1, for patches on the ground. */
	static double noise(long seed, double x, double z) {
		int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
		double fx = x - x0, fz = z - z0;
		double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
		double a = cell(seed, x0, z0), b = cell(seed, x0 + 1, z0), c = cell(seed, x0, z0 + 1), d = cell(seed, x0 + 1, z0 + 1);
		return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
	}

	private static double cell(long seed, int x, int z) {
		long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	// ------------------------------------------------------------------ plans and the build queue

	/** A design in progress: block positions to states, plus things to do once it stands (filling barrels). */
	public static final class Plan {
		final LinkedHashMap<Long, BlockState> blocks = new LinkedHashMap<>();
		final List<Object[]> after = new ArrayList<>();
		/** Light columns falling from island edges: x, y, z, length. */
		public final List<int[]> falls = new ArrayList<>();
		/** The cell this plan builds ("c@x,z"), so unfinished work far from everyone can be forgotten and redone. */
		public String cell;
		/** Keys for the world data (villages...), written when the plan is queued on the server thread. */
		public final List<String> marks = new ArrayList<>();
		/**
		 * Built again after it was left half done (a restart, a crash, work dropped far from everyone): it only fills
		 * open air, never what stands there now, and its veins are plain stone (what was mined does not grow back).
		 */
		public boolean rebuild;

		/** Blocks a design tried to lay outside the heights a plan builds at (a design that runs too tall). */
		public int dropped;

		/** The island's surface under each column it covers, as {@link Terrain} lays it (what stands on the ground reads it). */
		final Map<Long, Integer> ground = new HashMap<>();
		/**
		 * Paving asked for on a plan that has no ground of its own (a structure planned apart from its island): laid on
		 * the island's surface when the structure is merged onto it. Each: x, the height it expected, z, the block.
		 */
		final List<Object[]> paving = new ArrayList<>();

		static long column(int x, int z) {
			return ((long) x << 32) | (z & 0xFFFFFFFFL);
		}

		/** Plain stone where the plan would lay Tetherite ore: for an island laid again on every visit (what was mined would grow back). */
		public void withoutTetherite() {
			blocks.replaceAll((at, state) -> state.is(ModBlocks.TETHERITE_ORE) ? stone() : state);
		}

		static int columnX(long c) {
			return (int) (c >> 32);
		}

		static int columnZ(long c) {
			return (int) c;
		}

		public void setGround(int x, int z, int y) {
			ground.put(column(x, z), y);
		}

		/** The surface of the ground under a column, or {@code fallback} where the plan has none there. */
		public int ground(int x, int z, int fallback) {
			Integer y = ground.get(column(x, z));
			return y == null ? fallback : y;
		}

		/**
		 * Sets a plan designed apart (a structure) down on this one: its blocks over these, its actions, light falls and
		 * marks, then the paving it asked for, on this plan's ground.
		 */
		public void merge(Plan o) {
			blocks.putAll(o.blocks);
			after.addAll(o.after);
			falls.addAll(o.falls);
			marks.addAll(o.marks);
			dropped += o.dropped;
			for (Object[] pv : o.paving) {
				pave(this, (int) pv[0], (int) pv[1], (int) pv[2], (BlockState) pv[3]);
			}
		}

		public void set(int x, int y, int z, BlockState s) {
			if (s != null && y > 1 && y < 250) {
				blocks.put(BlockPos.asLong(x, y, z), s);
			} else if (s != null) {
				dropped++;
			}
		}

		/** How many blocks the plan lays. */
		public int size() {
			return blocks.size();
		}

		/** Where the plan lays blocks (for tests: read only). */
		public java.util.Set<Long> positions() {
			return java.util.Collections.unmodifiableSet(blocks.keySet());
		}

		/**
		 * A number standing for everything the plan lays, block by block (the same plan gives the same number, whatever
		 * order its blocks were laid in; any block laid differently, almost surely another).
		 */
		public long fingerprint() {
			long sum = 0;
			for (var e : blocks.entrySet()) {
				long h = e.getKey() * 0x9E3779B97F4A7C15L ^ net.minecraft.world.level.block.Block.getId(e.getValue()) * 0xC2B2AE3D27D4EB4FL;
				h ^= h >>> 31;
				h *= 0xBF58476D1CE4E5B9L;
				sum += h ^ h >>> 29;
			}
			return sum;
		}

		/** The farthest any block of the plan lies from a column, across the ground. */
		public double reach(int cx, int cz) {
			double far = 0;
			for (long k : blocks.keySet()) {
				double dx = BlockPos.getX(k) - cx, dz = BlockPos.getZ(k) - cz;
				far = Math.max(far, dx * dx + dz * dz);
			}
			return Math.sqrt(far);
		}

		/** The block the plan lays at a spot, or null (for the tests). */
		public BlockState at(int x, int y, int z) {
			return blocks.get(BlockPos.asLong(x, y, z));
		}

		/** The height of the plan's highest block (0 for an empty plan). */
		public int highest() {
			int top = 0;
			for (long k : blocks.keySet()) {
				top = Math.max(top, BlockPos.getY(k));
			}
			return top;
		}

		/** How many of the plan's blocks are of a block. */
		public int count(net.minecraft.world.level.block.Block block) {
			int n = 0;
			for (BlockState s : blocks.values()) {
				if (s.is(block)) {
					n++;
				}
			}
			return n;
		}

		public void soft(int x, int y, int z, BlockState s) {
			if (!has(x, y, z)) {
				set(x, y, z, s);
			}
		}

		public void clear(int x, int y, int z) {
			blocks.remove(BlockPos.asLong(x, y, z));
		}

		public boolean has(int x, int y, int z) {
			return blocks.containsKey(BlockPos.asLong(x, y, z));
		}

		BlockState get(int x, int y, int z) {
			return blocks.get(BlockPos.asLong(x, y, z));
		}

		public void then(int x, int y, int z, Consumer<ServerLevel> action) {
			after.add(new Object[] {new BlockPos(x, y, z), action});
		}

		private boolean connected;

		/** Joins walls, panes, bars and fences to their neighbours, as if a player had placed them one by one (once). */
		void connect() {
			if (connected) {
				return;
			}
			connected = true;
			for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
				BlockState s = e.getValue();
				boolean isWall = s.hasProperty(BlockStateProperties.NORTH_WALL);
				boolean isCross = !isWall && s.hasProperty(BlockStateProperties.NORTH) && s.hasProperty(BlockStateProperties.EAST)
						&& s.hasProperty(BlockStateProperties.WATERLOGGED) && !s.hasProperty(BlockStateProperties.UP);
				if (!isWall && !isCross) {
					continue;
				}
				long key = e.getKey();
				int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
				boolean[] c = new boolean[4];
				Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
				for (int i = 0; i < 4; i++) {
					BlockState n = get(x + dirs[i].getStepX(), y, z + dirs[i].getStepZ());
					c[i] = n != null && (n.hasProperty(BlockStateProperties.NORTH_WALL)
							|| (n.hasProperty(BlockStateProperties.NORTH) && n.hasProperty(BlockStateProperties.WATERLOGGED) && !n.hasProperty(BlockStateProperties.UP))
							|| !n.hasProperty(BlockStateProperties.WATERLOGGED) && n.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
				}
				BlockState r = s;
				if (isWall) {
					for (int i = 0; i < 4; i++) {
						r = r.setValue(wallSide(dirs[i]), c[i] ? WallSide.LOW : WallSide.NONE);
					}
					boolean straight = (c[0] && c[2] && !c[1] && !c[3]) || (c[1] && c[3] && !c[0] && !c[2]);
					boolean above = has(x, y + 1, z);
					r = r.setValue(BlockStateProperties.UP, !straight || above);
				} else {
					for (int i = 0; i < 4; i++) {
						r = r.setValue(side(dirs[i]), c[i]);
					}
				}
				e.setValue(r);
			}
		}

		/** Builds it right away (only for small things a player is about to stand on). */
		public void placeNow(ServerLevel level) {
			connect();
			for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
				level.setBlock(BlockPos.of(e.getKey()), e.getValue(), 2);
			}
			for (Object[] a : after) {
				@SuppressWarnings("unchecked") Consumer<ServerLevel> c = (Consumer<ServerLevel>) a[1];
				c.accept(level);
			}
		}

		/**
		 * Builds what lies within {@code radius} blocks (across) of a point right away, the ground someone is about to
		 * stand on, and queues the rest ahead of all other work, so the island comes together around them.
		 */
		public void placeAround(ServerLevel level, double cx, double cz, double radius) {
			// Which blocks show is judged on the whole island, the core included: the rim of the core is no wall
			prepare();
			double r2 = radius * radius;
			boolean[] now = new boolean[sorted.size()];
			for (int i = 0; i < now.length; i++) {
				Map.Entry<Long, BlockState> e = sorted.get(i);
				long k = e.getKey();
				double dx = BlockPos.getX(k) + 0.5 - cx, dz = BlockPos.getZ(k) + 0.5 - cz;
				if (dx * dx + dz * dz <= r2) {
					level.setBlock(BlockPos.of(k), e.getValue(), 2);
					now[i] = true;
				}
			}
			enqueue(true, now);
		}

		private List<Map.Entry<Long, BlockState>> sorted;
		/** Per block in {@link #sorted}: whether it will show (a side of it is not against another block of the plan). */
		private boolean[] shown;

		/**
		 * Joins its walls and fences, orders it bottom to top and marks which blocks will show; safe off the server
		 * thread (it touches no world).
		 */
		public void prepare() {
			if (sorted == null) {
				connect();
				sorted = new ArrayList<>(blocks.entrySet());
				sorted.sort((a, b) -> Integer.compare(BlockPos.getY(a.getKey()), BlockPos.getY(b.getKey())));
				shown = new boolean[sorted.size()];
				for (int i = 0; i < shown.length; i++) {
					long k = sorted.get(i).getKey();
					int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
					shown[i] = !has(x + 1, y, z) || !has(x - 1, y, z) || !has(x, y + 1, z) || !has(x, y - 1, z) || !has(x, y, z + 1) || !has(x, y, z - 1);
				}
			}
		}

		/** Queues it, bottom to top, so a structure rises from its island. Returns its cell key, or null if it has none. */
		public String queue() {
			return queue(false);
		}

		/** As {@link #queue()}; {@code first} puts it ahead of all other work waiting (something someone is looking at). */
		public String queue(boolean first) {
			prepare();
			return enqueue(first, null);
		}

		/** Queues the blocks not already put down ({@code done}, may be null) and the actions. */
		private String enqueue(boolean first, boolean[] done) {
			int id = ++nextPlan;
			ArrayDeque<Job> into = first ? READY : QUEUE;
			int count = 0;
			for (int i = 0; i < sorted.size(); i++) {
				if (done != null && done[i]) {
					continue;
				}
				Map.Entry<Long, BlockState> e = sorted.get(i);
				into.add(new Job(e.getKey(), e.getValue(), null, id, shown[i]));
				count++;
			}
			if (rebuild) {
				REBUILD.add(id);
			}
			for (Object[] a : after) {
				@SuppressWarnings("unchecked") Consumer<ServerLevel> c = (Consumer<ServerLevel>) a[1];
				QUEUE.add(new Job(((BlockPos) a[0]).asLong(), null, c, id, false));
			}
			UNBUILT.put(id, count);
			LEFT.put(id, count + after.size());
			PLANS.put(id, new Meta(cell == null ? "" : cell, List.copyOf(falls), List.copyOf(marks), count, false));
			return cell == null || cell.isEmpty() ? null : cell;
		}
	}

	/** A queued block to set (and whether it will show), or an action to run once every block of its plan stands. */
	private record Job(long pos, BlockState state, Consumer<ServerLevel> action, int plan, boolean shown) {}

	/** What is remembered of a queued plan: its cell, light falls and marks (to forget them if the work is dropped). */
	public record Meta(String cell, List<int[]> falls, List<String> marks, int blocks, boolean started) {}

	/** A plan's record as handed back, with whether any of its blocks was already put down. */
	private static Meta handed(int id, Meta m) {
		return new Meta(m.cell(), m.falls(), m.marks(), m.blocks(), UNBUILT.getOrDefault(id, 0) < m.blocks());
	}

	/** Fresh work, bottom to top. */
	private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
	/** Work in chunks nobody has loaded, by chunk: it is invisible anyway, so it waits instead of loading the chunk. */
	private static final Map<Long, ArrayDeque<Job>> WAITING = new HashMap<>();
	/** Work whose chunk has just loaded: done before fresh work. */
	private static final ArrayDeque<Job> READY = new ArrayDeque<>();
	/** Actions waiting for the last blocks of their plan. */
	private static final Map<Integer, List<Job>> HELD = new HashMap<>();
	/** Per plan: blocks still to stand, and jobs (blocks and actions) still to do; a plan with none left is forgotten. */
	private static final Map<Integer, Integer> UNBUILT = new HashMap<>();
	private static final Map<Integer, Integer> LEFT = new HashMap<>();
	private static final Map<Integer, Meta> PLANS = new HashMap<>();
	/** Plans built again after they were left half done (see {@link Plan#rebuild}). */
	private static final java.util.Set<Integer> REBUILD = new java.util.HashSet<>();
	/** How often a block in line to fly found someone standing in its place, by position: after a while it gives up. */
	private static final Map<Long, Integer> MISSES = new HashMap<>();
	/** Blocks someone is near enough to see come: they fly to their places, a few at a time, in order. */
	private static final ArrayDeque<Job> NEAR = new ArrayDeque<>();
	/** Blocks in the air now. */
	private static final List<BlockFlight.Flight> FLIGHTS = new ArrayList<>();
	private static double flyCredit;
	private static final String SPARK = "extremedimensions:soul_mote";
	private static int nextPlan;
	private static int waiting;
	private static int held;
	private static int calls;
	private static int sweeps;

	public static void clear() {
		QUEUE.clear();
		WAITING.clear();
		READY.clear();
		HELD.clear();
		UNBUILT.clear();
		LEFT.clear();
		PLANS.clear();
		REBUILD.clear();
		MISSES.clear();
		NEAR.clear();
		FLIGHTS.forEach(BlockFlight::cancel);
		FLIGHTS.clear();
		flyCredit = 0;
		waiting = 0;
		held = 0;
	}

	/**
	 * On stopping, before the world is saved: blocks in the air land at once, and the plans not yet finished are
	 * returned (their cells are forgotten, to be designed and built again when someone comes back).
	 */
	public static List<Meta> stop(ServerLevel level) {
		for (BlockFlight.Flight f : List.copyOf(FLIGHTS)) {
			BlockFlight.rush(f);
		}
		FLIGHTS.clear();
		// Those still in line to fly are simply there (an island coming together round someone is not left half done),
		// where the air is still open and no one stands
		while (level != null && !NEAR.isEmpty()) {
			Job j = NEAR.poll();
			BlockPos pos = BlockPos.of(j.pos());
			if (Loaded.ready(level, pos) && !settle(level, j, pos)) {
				stood(j);
			}
		}
		NEAR.clear();
		List<Meta> left = new ArrayList<>();
		PLANS.forEach((id, m) -> left.add(handed(id, m)));
		return left;
	}

	/** Blocks and actions still to do, waiting and flying ones included. */
	public static int pending() {
		return QUEUE.size() + READY.size() + waiting + held + NEAR.size() + FLIGHTS.size();
	}

	/** Blocks flying to their places now (for tests). */
	public static int flying() {
		return FLIGHTS.size();
	}

	private static long built;

	private static long chunkOf(long pos) {
		return ((long) (BlockPos.getX(pos) >> 4) << 32) | ((BlockPos.getZ(pos) >> 4) & 0xFFFFFFFFL);
	}

	/**
	 * Builds for at most {@code budgetNanos}: work in chunks that have just loaded first, then fresh work bottom to
	 * top. Work in a chunk nobody has loaded waits for it instead of loading it.
	 */
	public static void tick(ServerLevel level, long budgetNanos) {
		long deadline = System.nanoTime() + budgetNanos;
		boolean had = pending() > 0;
		// Where those who can watch stand, read once a tick (x, y, z after one another)
		List<ServerPlayer> players = level.players();
		double[] watchers = new double[players.size() * 3];
		int w = 0;
		for (ServerPlayer p : players) {
			if (!p.isSpectator()) {
				watchers[w++] = p.getX();
				watchers[w++] = p.getY();
				watchers[w++] = p.getZ();
			}
		}
		if (w < watchers.length) {
			watchers = java.util.Arrays.copyOf(watchers, w);
		}
		if (!FLIGHTS.isEmpty()) {
			flights(watchers);
		}
		if (!NEAR.isEmpty()) {
			if (++sweeps % 4 == 0) {
				underfootInLine(level, watchers);
			}
			launch(level, watchers);
		}
		if (!WAITING.isEmpty() && ++calls % 10 == 0) {
			for (var it = WAITING.entrySet().iterator(); it.hasNext();) {
				var e = it.next();
				long key = e.getKey();
				if (Loaded.chunk(level, (int) (key >> 32), (int) key)) {
					READY.addAll(e.getValue());
					waiting -= e.getValue().size();
					it.remove();
				}
			}
		}
		// The clock is read every few blocks, and right after every action (a chest, a creature): those cost more
		int n = 0;
		boolean heavy = false;
		while (!READY.isEmpty() || !QUEUE.isEmpty()) {
			if ((heavy || (++n & 7) == 0) && System.nanoTime() > deadline) {
				break;
			}
			Job j = READY.isEmpty() ? QUEUE.poll() : READY.poll();
			heavy = j.action() != null;
			run(level, j, watchers);
		}
		if (had && pending() == 0) {
			com.extremedimensions.ExtremeDimensions.LOGGER.info("Seam architect: done, {} blocks built so far", built);
		}
	}

	/**
	 * Does a job, or parks it: in its chunk's waiting list, (an action) until its plan's blocks all stand, or (a block
	 * someone is near enough to see come) in line to fly to its place.
	 */
	private static void run(ServerLevel level, Job j, double[] watchers) {
		BlockPos pos = BlockPos.of(j.pos());
		long wait = unready(level, pos);
		if (wait != NONE) {
			park(j, wait);
			return;
		}
		if (j.action() != null) {
			if (UNBUILT.getOrDefault(j.plan(), 0) > 0) {
				HELD.computeIfAbsent(j.plan(), k -> new ArrayList<>()).add(j);
				held++;
			} else {
				j.action().accept(level);
				done(j.plan());
			}
			return;
		}
		if (flies(level, j, pos, watchers)) {
			NEAR.add(j);
			return;
		}
		if (touching(watchers, pos) && solid(level, j, pos) && occupied(level, pos)) {
			// Someone stands where it goes: it waits in line instead of walling them in
			NEAR.add(j);
			return;
		}
		place(level, j, pos);
	}

	/** Whether a watcher's body takes up some of this block (read from where they stand, no world lookup). */
	private static boolean touching(double[] watchers, BlockPos pos) {
		double bx = pos.getX() + 0.5, bz = pos.getZ() + 0.5;
		for (int i = 0; i < watchers.length; i += 3) {
			double dy = watchers[i + 1] - pos.getY();
			if (Math.abs(watchers[i] - bx) < 1.4 && Math.abs(watchers[i + 2] - bz) < 1.4 && dy > -2 && dy < 1) {
				return true;
			}
		}
		return false;
	}

	private static void park(Job j, long chunk) {
		WAITING.computeIfAbsent(chunk, k -> new ArrayDeque<>()).add(j);
		waiting++;
	}

	/** No chunk is missing (see {@link #unready}). */
	private static final long NONE = Long.MIN_VALUE;

	/**
	 * The chunk a block put down at {@code pos} would reach into that is not loaded now, as {@link #chunkOf} keys it, or
	 * {@link #NONE}: its own, and at a chunk's edge the one beside it, where its neighbours' updates land (put down beside
	 * a chunk still loading, the server thread stood and waited for it).
	 */
	private static long unready(ServerLevel level, BlockPos pos) {
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, lx = pos.getX() & 15, lz = pos.getZ() & 15;
		if (!Loaded.ready(level, pos)) {
			return chunkOf(pos.asLong());
		}
		int nx = lx == 0 ? cx - 1 : lx == 15 ? cx + 1 : cx, nz = lz == 0 ? cz - 1 : lz == 15 ? cz + 1 : cz;
		if (nx != cx && !Loaded.chunk(level, nx, cz)) {
			return (long) nx << 32 | (cz & 0xFFFFFFFFL);
		}
		if (nz != cz && !Loaded.chunk(level, cx, nz)) {
			return (long) cx << 32 | (nz & 0xFFFFFFFFL);
		}
		return NONE;
	}

	/** A block of a plan is put down; once its plan's blocks all stand, its actions follow. */
	private static void place(ServerLevel level, Job j, BlockPos pos) {
		BlockState state = j.state();
		if (REBUILD.contains(j.plan())) {
			if (!open(level, pos)) {
				// Built again: what stands there now (a player's work, the half built first time) stays
				stood(j);
				return;
			}
			if (state.is(ModBlocks.SEAM_VEIN)) {
				state = stone();
			}
		}
		level.setBlock(pos, state, 2);
		built++;
		stood(j);
	}

	/** Open air, or something any block may take the place of (grass, snow), holding nothing. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState now = level.getBlockState(pos);
		return now.isAir() || now.canBeReplaced() && !now.hasBlockEntity();
	}

	/**
	 * Puts a block from the line to fly straight down instead: only into open air, never into someone. Returns false
	 * when someone stands there (it may go back in line); a place taken since is given up.
	 */
	private static boolean settle(ServerLevel level, Job j, BlockPos pos) {
		if (!open(level, pos)) {
			stood(j);
			return true;
		}
		if (solid(level, j, pos) && occupied(level, pos)) {
			return false;
		}
		place(level, j, pos);
		return true;
	}

	/** Whether a block would be in the way where it goes (grass, flowers and vines are not). */
	private static boolean solid(ServerLevel level, Job j, BlockPos pos) {
		return !j.state().getCollisionShape(level, pos).isEmpty();
	}

	/** A block of a plan is done with (put down, or found taken by something else). */
	private static void stood(Job j) {
		if (UNBUILT.merge(j.plan(), -1, Integer::sum) <= 0) {
			UNBUILT.remove(j.plan());
			List<Job> actions = HELD.remove(j.plan());
			if (actions != null) {
				READY.addAll(actions);
				held -= actions.size();
			}
		}
		done(j.plan());
	}

	/** One more job of a plan done; once all are, the plan is forgotten. */
	private static void done(int plan) {
		if (LEFT.merge(plan, -1, Integer::sum) <= 0) {
			LEFT.remove(plan);
			REBUILD.remove(plan);
			Meta m = PLANS.remove(plan);
			if (m != null) {
				SeamService.built(m);
			}
		}
	}

	// ------------------------------------------------------------------ the Seam building itself before your eyes

	/**
	 * Whether a block flies to its place instead of simply being there: it will show, its place is open air, and
	 * someone is near enough to see it come ({@code seam_fly_range}), though not so near that it is the ground at
	 * their feet (that is simply there, so no one falls through an island still being built).
	 */
	private static boolean flies(ServerLevel level, Job j, BlockPos pos, double[] watchers) {
		if (Config.SEAM_FLY_RANGE <= 0 || watchers.length == 0 || !j.shown() || j.state().isAir()) {
			return false;
		}
		double range2 = (double) Config.SEAM_FLY_RANGE * Config.SEAM_FLY_RANGE;
		double bx = pos.getX() + 0.5, by = pos.getY(), bz = pos.getZ() + 0.5;
		boolean near = false;
		for (int i = 0; i < watchers.length; i += 3) {
			double dx = watchers[i] - bx, dy = watchers[i + 1] - by, dz = watchers[i + 2] - bz;
			double flat = dx * dx + dz * dz;
			if (flat < 4.5 * 4.5 && dy > -3 && dy < 7) {
				return false;
			}
			near |= flat + dy * dy < range2;
		}
		// Only then the world is read: most blocks are far from everyone
		return near && level.getBlockState(pos).isAir();
	}

	/** Sends blocks in line on their way: as many a tick as fill the air once a flight, never more in the air than allowed. */
	private static void launch(ServerLevel level, double[] watchers) {
		int max = Math.max(1, Config.SEAM_FLY_MAX), ticks = Math.max(10, Config.SEAM_FLY_TICKS);
		flyCredit = Math.min(flyCredit + max / (double) ticks, max);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		// Blocks no longer watched are simply put down, but no more than this a tick (a long line left behind would stall)
		int direct = 0;
		List<Job> later = new ArrayList<>();
		while (flyCredit >= 1 && FLIGHTS.size() < max && !NEAR.isEmpty() && direct < 512) {
			Job j = NEAR.poll();
			BlockPos pos = BlockPos.of(j.pos());
			long wait = unready(level, pos);
			if (wait != NONE) {
				park(j, wait);
				continue;
			}
			if (!flies(level, j, pos, watchers)) {
				direct++;
				if (!settle(level, j, pos)) {
					later.add(j);
				}
				continue;
			}
			BlockFlight.Flight f = BlockFlight.in(level, pos, j.state(), from(rnd), ticks, SPARK, rnd.nextInt(5) == 0 ? 5 : 0, new Arrival(level, j, pos));
			if (f == null) {
				if (!settle(level, j, pos)) {
					later.add(j);
				}
				continue;
			}
			flyCredit--;
			FLIGHTS.add(f);
			if (rnd.nextInt(40) == 0) {
				WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.amethyst_block.resonate", "ambient", f.x(), f.y(), f.z(), 0.6f,
						(float) rnd.nextDouble(0.7, 1.4));
			}
		}
		NEAR.addAll(later);
	}

	/**
	 * Blocks still in line to fly that go just under someone's feet are put down now: no one walks out over an island
	 * coming together and drops through a gap that is still on its way.
	 */
	private static void underfootInLine(ServerLevel level, double[] watchers) {
		if (watchers.length == 0) {
			return;
		}
		for (var it = NEAR.iterator(); it.hasNext();) {
			Job j = it.next();
			long k = j.pos();
			double bx = BlockPos.getX(k) + 0.5, by = BlockPos.getY(k), bz = BlockPos.getZ(k) + 0.5;
			for (int i = 0; i < watchers.length; i += 3) {
				double dx = watchers[i] - bx, dy = watchers[i + 1] - by, dz = watchers[i + 2] - bz;
				if (dx * dx + dz * dz < 2.5 * 2.5 && dy >= 0.5 && dy < 5) {
					BlockPos pos = BlockPos.of(k);
					if (unready(level, pos) == NONE && settle(level, j, pos)) {
						it.remove();
					}
					break;
				}
			}
		}
	}

	/** Where a block sets off from: out of the nothing round and below its place, about {@code seam_fly_from} blocks off. */
	private static double[] from(ThreadLocalRandom rnd) {
		double dist = Math.max(2, Config.SEAM_FLY_FROM) * rnd.nextDouble(0.75, 1.25);
		double az = rnd.nextDouble(Math.PI * 2), el = rnd.nextDouble(-1.1, 0.35);
		return new double[] {Math.cos(az) * Math.cos(el) * dist, Math.sin(el) * dist, Math.sin(az) * Math.cos(el) * dist};
	}

	/** One tick of the blocks in the air; one about to be stepped on lands at once. */
	private static void flights(double[] watchers) {
		var it = FLIGHTS.iterator();
		while (it.hasNext()) {
			BlockFlight.Flight f = it.next();
			if (underfoot(f, watchers)) {
				BlockFlight.rush(f);
				it.remove();
			} else if (BlockFlight.step(f)) {
				it.remove();
			}
		}
	}

	/** Whether a block still flying in goes just under someone's feet. */
	private static boolean underfoot(BlockFlight.Flight f, double[] watchers) {
		if (!BlockFlight.incoming(f)) {
			return false;
		}
		for (int i = 0; i < watchers.length; i += 3) {
			double dx = watchers[i] - (f.pos.getX() + 0.5), dz = watchers[i + 2] - (f.pos.getZ() + 0.5), dy = watchers[i + 1] - f.pos.getY();
			if (dx * dx + dz * dz < 2.5 * 2.5 && dy >= 1 && dy < 4) {
				return true;
			}
		}
		return false;
	}

	/** Whether someone (a player or a creature, not an Elder) stands where a block would go. */
	private static boolean occupied(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos),
				e -> e.isAlive() && (e instanceof net.minecraft.world.entity.player.Player || e instanceof net.minecraft.world.entity.Mob)
						&& !(e instanceof com.extremedimensions.elder.ElderBoss)).isEmpty();
	}

	/** How a block of the Seam lands: in whatever open space is there, never on something someone has put there since. */
	private record Arrival(ServerLevel level, Job job, BlockPos pos) implements BlockFlight.Landing {
		@Override
		public boolean fits() {
			return open(level, pos);
		}

		@Override
		public boolean blocked() {
			return solid(level, job, pos) && occupied(level, pos);
		}

		@Override
		public void land() {
			MISSES.remove(job.pos());
			place(level, job, pos);
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			if (rnd.nextInt(6) == 0) {
				level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, job.state().getSoundType().getPlaceSound(),
						net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, (float) rnd.nextDouble(0.7, 1.1));
			}
		}

		@Override
		public void refused() {
			stood(job);
		}

		@Override
		public void missed() {
			// Someone stood in its place all the while: it gets in line again, though not for ever
			if (MISSES.merge(job.pos(), 1, Integer::sum) >= 8) {
				MISSES.remove(job.pos());
				stood(job);
			} else {
				NEAR.add(job);
			}
		}

		@Override
		public void lost() {
			long wait = unready(level, pos);
			if (wait != NONE) {
				park(job, wait);
			} else if (!settle(level, job, pos)) {
				NEAR.add(job);
			}
		}
	}

	/**
	 * Drops every plan with work waiting farther than {@code range} blocks from all the given positions, and returns
	 * what they were, so their cells can be designed again when someone comes back.
	 */
	public static List<Meta> dropFar(List<double[]> near, double range) {
		java.util.Set<Integer> far = new java.util.HashSet<>();
		for (var e : WAITING.entrySet()) {
			double x = ((int) (e.getKey() >> 32) << 4) + 8, z = ((int) (long) e.getKey() << 4) + 8;
			boolean close = false;
			for (double[] p : near) {
				double dx = p[0] - x, dz = p[1] - z;
				close |= dx * dx + dz * dz < range * range;
			}
			if (!close) {
				for (Job j : e.getValue()) {
					far.add(j.plan());
				}
			}
		}
		if (far.isEmpty()) {
			return List.of();
		}
		for (var it = WAITING.values().iterator(); it.hasNext();) {
			ArrayDeque<Job> jobs = it.next();
			int before = jobs.size();
			jobs.removeIf(j -> far.contains(j.plan()));
			waiting -= before - jobs.size();
			if (jobs.isEmpty()) {
				it.remove();
			}
		}
		QUEUE.removeIf(j -> far.contains(j.plan()));
		READY.removeIf(j -> far.contains(j.plan()));
		NEAR.removeIf(j -> far.contains(j.plan()));
		List<Meta> dropped = new ArrayList<>();
		for (int id : far) {
			LEFT.remove(id);
			List<Job> actions = HELD.remove(id);
			if (actions != null) {
				held -= actions.size();
			}
			Meta m = PLANS.remove(id);
			if (m != null) {
				dropped.add(handed(id, m));
			}
			UNBUILT.remove(id);
			REBUILD.remove(id);
		}
		return dropped;
	}

	/** A rotated local frame on a plan: local +z is the "front" and is turned by rot quarter turns (clockwise). */
	static final class Frame {
		final Plan p;
		final int ox, oy, oz, rot;

		Frame(Plan p, int ox, int oy, int oz, int rot) {
			this.p = p;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.rot = rot & 3;
		}

		int wx(int lx, int lz) {
			return ox + switch (rot) { case 1 -> -lz; case 2 -> -lx; case 3 -> lz; default -> lx; };
		}

		int wz(int lx, int lz) {
			return oz + switch (rot) { case 1 -> lx; case 2 -> -lz; case 3 -> -lx; default -> lz; };
		}

		Direction dir(Direction local) {
			Direction d = local;
			for (int i = 0; i < rot; i++) {
				d = d.getClockWise();
			}
			return d;
		}

		BlockState turn(BlockState s) {
			if (s == null || rot == 0) {
				return s;
			}
			BlockState r = s;
			if (r.hasProperty(BlockStateProperties.AXIS) && (rot & 1) == 1) {
				Direction.Axis a = r.getValue(BlockStateProperties.AXIS);
				if (a == Direction.Axis.X) {
					r = r.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
				} else if (a == Direction.Axis.Z) {
					r = r.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
				}
			}
			if (r.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				r = r.setValue(BlockStateProperties.HORIZONTAL_FACING, dir(r.getValue(BlockStateProperties.HORIZONTAL_FACING)));
			}
			// multiface growth (glow lichen) turns with the frame
			if (r.hasProperty(BlockStateProperties.NORTH) && r.hasProperty(BlockStateProperties.UP)) {
				Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
				boolean[] v = new boolean[4];
				for (int i = 0; i < 4; i++) {
					v[i] = r.getValue(side(dirs[i]));
				}
				for (int i = 0; i < 4; i++) {
					r = r.setValue(side(dir(dirs[i])), v[i]);
				}
			}
			return r;
		}

		void set(int lx, int ly, int lz, BlockState s) {
			p.set(wx(lx, lz), oy + ly, wz(lx, lz), turn(s));
		}

		void soft(int lx, int ly, int lz, BlockState s) {
			p.soft(wx(lx, lz), oy + ly, wz(lx, lz), turn(s));
		}

		void clear(int lx, int ly, int lz) {
			p.clear(wx(lx, lz), oy + ly, wz(lx, lz));
		}

		boolean has(int lx, int ly, int lz) {
			return p.has(wx(lx, lz), oy + ly, wz(lx, lz));
		}

		void then(int lx, int ly, int lz, Consumer<ServerLevel> a) {
			p.then(wx(lx, lz), oy + ly, wz(lx, lz), a);
		}

		BlockPos pos(int lx, int ly, int lz) {
			return new BlockPos(wx(lx, lz), oy + ly, wz(lx, lz));
		}
	}

	/** The four faces of a square building in a frame: where a point on a face lies, which way is out, which way is +u. */
	static final class Face {
		static int lx(int side, int u, int plane) {
			return switch (side) { case 0 -> u; case 1 -> plane; case 2 -> -u; default -> -plane; };
		}

		static int lz(int side, int u, int plane) {
			return switch (side) { case 0 -> plane; case 1 -> -u; case 2 -> -plane; default -> u; };
		}

		static Direction out(int side) {
			return switch (side) { case 0 -> Direction.SOUTH; case 1 -> Direction.EAST; case 2 -> Direction.NORTH; default -> Direction.WEST; };
		}

		static Direction along(int side) {
			return switch (side) { case 0 -> Direction.EAST; case 1 -> Direction.NORTH; case 2 -> Direction.WEST; default -> Direction.SOUTH; };
		}
	}

	// ------------------------------------------------------------------ islands

	/**
	 * The footprint of an island: its centre, surface height, radius and the three phases of its wobbling rim. The
	 * phases are the first three numbers drawn from the island's random source, so a neighbour can predict exactly
	 * where an island's edge is without building it (bridges land on it precisely).
	 */
	public record Shape(int x, int top, int z, int r, double p1, double p2, double p3) {
		public static Shape of(Random rnd, int x, int top, int z, int r) {
			return new Shape(x, top, z, r, rnd.nextDouble() * 6.28, rnd.nextDouble() * 6.28, rnd.nextDouble() * 6.28);
		}

		/**
		 * The coast's distance from the centre at an angle: it wanders in broad bays and headlands with smaller coves in
		 * them; big islands wander less, so what is built on them fits.
		 */
		public double edge(double ang) {
			double w = r >= 20 ? 0.55 : 1.0;
			return r * (0.91 + w * (0.05 * Math.sin(ang * 2 + p1 + p3) + 0.06 * Math.sin(ang * 3 + p1) + 0.04 * Math.sin(ang * 5 + p2)
					+ 0.025 * Math.sin(ang * 7 + p3 * 1.7) + 0.015 * Math.sin(ang * 13 + p2 * 2.3)));
		}

		/** The nearest the coast comes to the centre, all round. */
		public double nearest() {
			double least = r;
			for (int i = 0; i < 360; i += 3) {
				least = Math.min(least, edge(Math.toRadians(i)));
			}
			return least;
		}

		public boolean inside(int px, int pz, double margin) {
			double dx = px - x, dz = pz - z;
			return Math.sqrt(dx * dx + dz * dz) < edge(Math.atan2(dz, dx)) - margin;
		}
	}

	/**
	 * A floating island (see {@link Terrain}): rolling turf over an underside built as an upside-down mountain, its
	 * ground level out to the rim on every side (where a bridge might come in).
	 *
	 * @return the depth of the underside at the centre
	 */
	public static int island(Plan p, Random rnd, Shape s, boolean mossy) {
		return Terrain.of(rnd, s, Terrain.ALL_SIDES, mossy).emit(p);
	}

	/**
	 * Island rock at a depth fraction {@code k}: the bands of the gradient move up and down from column to column,
	 * so they run down the stone in streaks (the way rock weathers, and the way builders blend a gradient: a drip,
	 * not a speckle), crossed every eight blocks by a thin stratum, chalk-pale above and basalt-dark below, that rises
	 * and falls across the island.
	 */
	static BlockState rockAt(BlockState[] rock, long seed, int x, int y, int z, int top, double k, Random rnd) {
		int band = (int) Math.floor(top - y + noise(seed + 11, x / 11.0, z / 11.0) * 5);
		if (Math.floorMod(band, 8) == 3 && k > 0.12 && k < 0.88) {
			return k < 0.5 ? mc("calcite", stone()) : mc("smooth_basalt", stone());
		}
		double streak = (noise(seed + 9, x / 2.2, z / 2.2) - 0.5) * 0.5;
		double v = Math.max(0, Math.min(1, k + streak + (rnd.nextDouble() - 0.5) * 0.14)) * (rock.length - 1);
		return rock[(int) Math.round(v)];
	}

	// ------------------------------------------------------------------ small things

	/** A lamp post: a stepped plinth, a wall shaft and a lantern, sometimes on a bracket with a chain. */
	public static void lamp(Plan p, Random rnd, int x, int y, int z) {
		int h = 2 + rnd.nextInt(3);
		p.set(x, y, z, bsPol());
		for (int k = 1; k < h; k++) {
			p.set(x, y + k, z, bsWall());
		}
		if (rnd.nextBoolean()) {
			p.set(x, y + h, z, rnd.nextBoolean() ? lantern() : soulLantern());
		} else {
			p.set(x, y + h, z, bsWall());
			p.set(x, y + h + 1, z, carved());
			p.set(x + 1, y + h + 1, z, slab(tileSlab(), false));
			p.set(x + 1, y + h, z, chain());
			p.set(x + 1, y + h - 1, z, hanging(lantern()));
		}
	}

	/** A broken gothic arch: stepped bases, stair-curved crown, a lantern hanging in it, lichen on the stone. */
	static void arch(Plan p, Random rnd, int cx, int y, int cz) {
		Frame f = new Frame(p, cx, y, cz, rnd.nextInt(4));
		int half = 3 + rnd.nextInt(2), h = 7 + rnd.nextInt(5);
		boolean broken = rnd.nextBoolean();
		for (int side = -1; side <= 1; side += 2) {
			int top = broken && side == 1 ? h - 3 - rnd.nextInt(3) : h;
			for (int ly = 0; ly < top; ly++) {
				double t = (double) ly / (h + half);
				f.set(side * half, ly, 0, wall(t, rnd));
				f.set(side * half, ly, 1, ly == 0 ? bsBrick() : wall(t, rnd));
			}
			f.set(side * (half + 1), 0, 0, stairs(bsStairs(), side > 0 ? Direction.WEST : Direction.EAST, false));
			f.set(side * (half + 1), 0, 1, stairs(bsStairs(), side > 0 ? Direction.WEST : Direction.EAST, false));
			if (rnd.nextBoolean()) {
				f.soft(side * half, 1 + rnd.nextInt(3), 2, lichen(Direction.NORTH));
			}
		}
		// The pointed crown, curved with stairs
		for (int lx = -half + 1; lx <= half - 1; lx++) {
			if (broken && lx > 0) {
				continue;
			}
			int rise = (int) Math.round((half - Math.abs(lx)) * 0.9);
			for (int lz = 0; lz <= 1; lz++) {
				f.set(lx, h + rise, lz, wall(0.6, rnd));
				f.set(lx, h + rise - 1, lz, lx == 0 ? carved() : stairs(brickStairs(), lx < 0 ? Direction.WEST : Direction.EAST, true));
			}
		}
		int apex = h + (int) Math.round(half * 0.9);
		f.set(0, apex + 1, 0, brickWall());
		f.set(0, apex - 2, 0, chain());
		f.set(0, apex - 3, 0, hanging(soulLantern()));
	}

	/** A crooked dead tree hung with pale moss, like the Pale Garden reached through. */
	static void deadTree(Plan p, Random rnd, int x, int y, int z) {
		BlockState pale = mc("pale_hanging_moss");
		int h = 5 + rnd.nextInt(5);
		int tx = x, tz = z;
		for (int k = 0; k < h; k++) {
			p.set(tx, y + k, tz, log());
			if (k > 2 && rnd.nextInt(3) == 0) {
				tx += rnd.nextInt(3) - 1;
				tz += rnd.nextInt(3) - 1;
				p.set(tx, y + k, tz, log());
			}
		}
		for (int b = 0; b < 3 + rnd.nextInt(3); b++) {
			int bx = tx, bz = tz, by = y + h - 1 - rnd.nextInt(3);
			int sx = rnd.nextInt(3) - 1, sz = rnd.nextInt(3) - 1;
			int len = 2 + rnd.nextInt(3);
			for (int k = 0; k < len; k++) {
				bx += sx;
				bz += sz;
				by += rnd.nextInt(3) == 0 ? 1 : 0;
				p.set(bx, by, bz, log());
			}
			for (int k = 1; k <= 1 + rnd.nextInt(4); k++) {
				p.soft(bx, by - k, bz, pale);
			}
		}
	}

	/** A floating shard of amethyst with a weeping heart. */
	static void crystal(Plan p, Random rnd, int cx, int cy, int cz, int size) {
		for (int dx = -size; dx <= size; dx++) {
			for (int dz = -size; dz <= size; dz++) {
				for (int dy = -size * 3; dy <= size * 3; dy++) {
					double v = Math.abs(dx) + Math.abs(dz) + Math.abs(dy) / 3.0;
					if (v <= size) {
						p.set(cx + dx, cy + dy, cz + dz, v < size - 1.2 ? crying() : (rnd.nextInt(5) == 0 ? glass2() : amethyst()));
					}
				}
			}
		}
	}

	/**
	 * The island's rock breaking up through the turf: a lumpy crag of stone, cobbles and moss-grown cobbles a few
	 * blocks high, its foot sunk into the ground, moss and a fern on its top and glowing lichen in its cracks.
	 */
	static void outcrop(Plan p, Random rnd, int cx, int y, int cz) {
		long seed = rnd.nextLong();
		double rad = 1.6 + rnd.nextDouble() * 1.2;
		int h = 2 + rnd.nextInt(3);
		BlockState[] rock = {stone(), cobble(), mc("mossy_cobblestone", cobble()), mc("tuff", stone())};
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double lump = noise(seed, (cx + dx) / 1.7, (cz + dz) / 1.7);
				double r = Math.sqrt(dx * dx + dz * dz) / (rad * (0.75 + 0.5 * lump));
				if (r > 1) {
					continue;
				}
				int top = (int) Math.round(h * (1 - r * r) * (0.7 + 0.6 * lump));
				// its foot sunk into the turf
				p.set(cx + dx, y - 1, cz + dz, rock[rnd.nextInt(2)]);
				for (int k = 0; k < top; k++) {
					p.set(cx + dx, y + k, cz + dz, rock[rnd.nextInt(rock.length)]);
				}
				if (top > 0 && rnd.nextInt(3) > 0) {
					p.soft(cx + dx, y + top, cz + dz, rnd.nextInt(4) == 0 ? mc("fern") : mc("moss_carpet"));
				}
			}
		}
		for (int i = 0; i < 3; i++) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(seed + i));
			int lx = cx + d.getStepX() * 2, lz = cz + d.getStepZ() * 2;
			if (p.has(lx, y, lz) && !p.has(lx + d.getStepX(), y, lz + d.getStepZ())) {
				p.set(lx + d.getStepX(), y, lz + d.getStepZ(), lichen(d.getOpposite()));
			}
		}
	}

	/** A ring of standing stones around a soul fire, grown over with glowing lichen. */
	static void stones(Plan p, Random rnd, int cx, int y, int cz) {
		int n = 5 + rnd.nextInt(3);
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			int x = cx + (int) Math.round(Math.cos(a) * 4), z = cz + (int) Math.round(Math.sin(a) * 4);
			int h = 2 + rnd.nextInt(4);
			for (int k = 0; k < h; k++) {
				p.set(x, y + k, z, grad(new BlockState[] {mc("blackstone", stone()), mc("tuff", stone()), mc("polished_tuff", stone())}, (double) k / 5, rnd));
			}
			Direction toward = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? Direction.WEST : Direction.EAST)
					: (Math.sin(a) > 0 ? Direction.NORTH : Direction.SOUTH);
			p.soft(x + toward.getStepX(), y + rnd.nextInt(h), z + toward.getStepZ(), lichen(toward.getOpposite()));
		}
		p.set(cx, y - 1, cz, mc("soul_soil", stone()));
		p.set(cx, y, cz, mc("soul_campfire", Blocks.SOUL_LANTERN.defaultBlockState()));
	}

	/** Barrels of the Thinned hold silk, pages and old things. */
	static Consumer<ServerLevel> loot(BlockPos pos, int richness) {
		return level -> {
			level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
			SeamService.fillLoot(level, pos, richness);
		};
	}

	// ------------------------------------------------------------------ the giant loom

	/**
	 * A Weaver's loom the size of a cathedral front: two great pillars darkening to the foot, a beam across the top,
	 * hundreds of threads of chain and silk, and a half-woven tapestry of the Weaver's sign.
	 */
	public static void loom(Plan p, Random rnd, int cx, int top, int cz, int r) {
		Frame f = new Frame(p, cx, top + 1, cz, rnd.nextInt(4));
		int sx = Math.max(8, Math.min(14, r - 9));
		int h = 30 + rnd.nextInt(14);
		// The weaving floor: paving under and around the loom, ragged where the moss takes it back
		for (int lx = -sx - 3; lx <= sx + 3; lx++) {
			for (int lz = -4; lz <= 9; lz++) {
				double t = Math.max(Math.abs(lx) / (double) (sx + 3), Math.abs(lz - 2) / 7.0);
				if (t > 0.8 && rnd.nextInt(3) == 0) {
					continue;
				}
				pave(p, f.wx(lx, lz), top, f.wz(lx, lz), weather(grad(pathGrad(), t, rnd), rnd, 0.1));
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int ly = 0; ly <= h + 4; ly++) {
				double t = ly / (double) (h + 8);
				for (int ax = -1; ax <= 1; ax++) {
					for (int az = -1; az <= 1; az++) {
						boolean core = ax == 0 && az == 0;
						f.set(side * sx + ax, ly, az, core && ly > 2 ? vein() : ly % 8 == 7 ? carved() : wall(t, rnd));
					}
				}
				// a stepped plinth
				if (ly <= 2) {
					for (int ax = -2; ax <= 2; ax++) {
						for (int az = -2; az <= 2; az++) {
							if (Math.abs(ax) == 2 || Math.abs(az) == 2) {
								if (ly == 0) {
									f.set(side * sx + ax, 0, az, bsBrick());
								} else if (ly == 1 && (Math.abs(ax) != 2 || Math.abs(az) != 2)) {
									Direction in = Math.abs(ax) == 2 ? (ax > 0 ? Direction.WEST : Direction.EAST) : (az > 0 ? Direction.NORTH : Direction.SOUTH);
									f.set(side * sx + ax, 1, az, stairs(bsStairs(), in, false));
								}
							}
						}
					}
				}
			}
			f.set(side * sx, h + 5, 0, carved());
			f.set(side * sx, h + 6, 0, brickWall());
			f.set(side * sx, h + 7, 0, brickWall());
			f.set(side * sx, h + 8, 0, endRod());
		}
		// Beams, with corbels under the ends
		for (int lx = -sx; lx <= sx; lx++) {
			for (int az = -1; az <= 1; az++) {
				for (int ly = h; ly <= h + 2; ly++) {
					f.set(lx, ly, az, ly == h + 1 && az == 0 ? vein() : wall(0.8, rnd));
				}
				f.set(lx, 3, az, wall(0.15, rnd));
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			f.set(side * (sx - 2), h - 1, 0, stairs(brickStairs(), side > 0 ? Direction.EAST : Direction.WEST, true));
		}
		// Threads and the tapestry
		int mid = (h + 4) / 2, woven = 8 + rnd.nextInt(Math.max(1, h - 14));
		int halfW = sx - 3;
		for (int lx = -sx + 2; lx <= sx - 2; lx++) {
			for (int ly = 4; ly < h; ly++) {
				boolean cloth = Math.abs(lx) <= halfW && ly >= 6 && ly < 6 + woven;
				if (cloth) {
					int m = Math.abs(lx) + Math.abs(ly - mid);
					BlockState s = Math.abs(lx) == halfW || ly == 6 ? woolDark()
							: m % 6 == 0 ? vein() : m < halfW - 1 && m % 6 < 2 ? wool2() : wool();
					f.set(lx, ly, 0, s);
				} else {
					int k = rnd.nextInt(14);
					f.set(lx, ly, 0, k == 0 ? Blocks.COBWEB.defaultBlockState() : ly % 6 == 0 && (lx & 1) == 0 ? vein() : chain());
				}
			}
		}
		// Lanterns from the beam
		for (int lx = -sx + 1; lx <= sx - 1; lx += 4) {
			for (int az = -2; az <= 2; az += 4) {
				int len = 1 + rnd.nextInt(4);
				for (int k = 1; k <= len; k++) {
					f.set(lx, h - k + 1, az, chain());
				}
				f.set(lx, h - len, az, hanging(lantern()));
			}
		}
		// A great spool on its side, and silk on the ground
		for (int lx = -3; lx <= 3; lx++) {
			for (int a = 0; a < 16; a++) {
				double an = a * Math.PI / 8;
				f.set(lx, 3 + (int) Math.round(Math.sin(an) * 2.5) - 2, 6 + (int) Math.round(Math.cos(an) * 2.5), Math.abs(lx) == 3 ? log() : wool());
			}
		}
		// cobwebs over the weaving floor
		for (int i = 0; i < 10; i++) {
			f.soft(rnd.nextInt(-sx, sx + 1), 0, rnd.nextInt(-3, 9), Blocks.COBWEB.defaultBlockState());
		}
		f.then(sx - 3, 0, -3, loot(f.pos(sx - 3, 0, -3), 1));
		// and a loom the Thinned left, its half-woven tapestry still on it (the First Weaver's trail runs through here)
		f.set(-(sx - 3), 0, -3, Blocks.LOOM.defaultBlockState());
	}

	// ------------------------------------------------------------------ villages

	/**
	 * A village of the Thinned laid out the way villages grow: a paved plaza with a bell spire at the heart, streets
	 * leaving it along the axes to the bridges, and in the four quarters between them jettied timber houses facing the
	 * plaza, each with a path from its door, and a walled graveyard in one quarter.
	 */
	public static void village(Plan p, Random rnd, int cx, int top, int cz, int r) {
		village(p, rnd, cx, top, cz, r, null, null);
	}

	/**
	 * As {@link #village(Plan, Random, int, int, int, int)}, on ground that rolls: {@code ground} gives the island's
	 * surface under a column, and each house is planned apart at the ground where it stands (within two blocks of the
	 * plaza's) and handed to {@code house} with that height, to be set down on its own, so the houses step with the slope.
	 */
	public static void village(Plan p, Random rnd, int cx, int top, int cz, int r, java.util.function.IntBinaryOperator ground,
			java.util.function.BiConsumer<Plan, Integer> house) {
		int plaza = r >= 24 ? 8 : 6;
		// houses stand about 22 degrees off the streets: room to the street, to each other and to the rim
		int ring = (int) Math.round(r * 0.68);
		int graves = rnd.nextInt(10) < 7 ? rnd.nextInt(4) : -1;
		boolean lore = true;
		for (int q = 0; q < 4; q++) {
			double qa = Math.PI / 4 + q * Math.PI / 2;
			if (q == graves) {
				graveyard(p, rnd, cx + (int) Math.round(Math.cos(qa) * r * 0.55), top, cz + (int) Math.round(Math.sin(qa) * r * 0.55), cx, cz);
				continue;
			}
			for (int k = -1; k <= 1; k += 2) {
				double a = qa + k * 0.40;
				int hx = cx + (int) Math.round(Math.cos(a) * ring), hz = cz + (int) Math.round(Math.sin(a) * ring);
				double back = Math.atan2(cz - hz, cx - hx);
				int rot = Math.floorMod((int) Math.round((back - Math.PI / 2) / (Math.PI / 2)), 4);
				int hy = ground == null ? top : Math.max(top - 2, Math.min(top + 2, ground.applyAsInt(hx, hz)));
				Plan hp = house == null ? p : new Plan();
				house(hp, rnd, hx, hy + 1, hz, rot, lore);
				if (house != null) {
					house.accept(hp, hy);
				}
				lore = false;
				// A path from the door step to the plaza
				Frame f = new Frame(p, hx, top + 1, hz, rot);
				for (int lz = 5; lz <= 14; lz++) {
					int x = f.wx(0, lz), z = f.wz(0, lz);
					if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= plaza * plaza) {
						break;
					}
					pave(p, x, top, z, rnd.nextInt(4) == 0 ? cobble() : tile());
				}
			}
		}
		// Plaza: polished pale stone at the heart, darkening to cobbles at the rim
		BlockState[] paving = pathGrad();
		for (int dx = -plaza; dx <= plaza; dx++) {
			for (int dz = -plaza; dz <= plaza; dz++) {
				double dd = Math.sqrt(dx * dx + dz * dz);
				if (dd <= plaza + 0.2) {
					p.set(cx + dx, top, cz + dz, weather(grad(paving, dd / (plaza + 0.5), rnd), rnd, 0.08));
					p.clear(cx + dx, top + 1, cz + dz);
				}
			}
		}
		for (int q = 0; q < 4; q++) {
			double qa = Math.PI / 4 + q * Math.PI / 2;
			lamp(p, rnd, cx + (int) Math.round(Math.cos(qa) * (plaza - 1)), top + 1, cz + (int) Math.round(Math.sin(qa) * (plaza - 1)));
		}
		// The bell spire: a stepped base, an open belfry with stair arches, a slate cap and a needle
		int h = 16 + rnd.nextInt(10);
		for (int ly = 1; ly <= h; ly++) {
			double t = ly / (double) (h + 8);
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					boolean belfry = ly > h - 4 && ly < h;
					if (belfry && (ax == 0 || az == 0)) {
						continue;
					}
					p.set(cx + ax, top + ly, cz + az, ly % 5 == 0 ? carved() : wall(t, rnd));
				}
			}
			if (ly == 1) {
				for (int k = -2; k <= 2; k++) {
					p.set(cx + k, top + 1, cz - 2, stairs(bsStairs(), Direction.SOUTH, false));
					p.set(cx + k, top + 1, cz + 2, stairs(bsStairs(), Direction.NORTH, false));
					p.set(cx - 2, top + 1, cz + k, stairs(bsStairs(), Direction.EAST, false));
					p.set(cx + 2, top + 1, cz + k, stairs(bsStairs(), Direction.WEST, false));
				}
			}
		}
		p.set(cx, top + h - 1, cz, mc("bell", lantern()));
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			p.set(cx + d.getStepX(), top + h - 1, cz + d.getStepZ(), stairs(brickStairs(), d.getOpposite(), true));
		}
		for (int k = 1; k <= 7; k++) {
			int half = k < 3 ? 1 : 0;
			for (int ax = -half; ax <= half; ax++) {
				for (int az = -half; az <= half; az++) {
					p.set(cx + ax, top + h + k, cz + az, k == 7 ? endRod() : half == 1 && (ax != 0 || az != 0) && k == 1
							? stairs(roofStairs(rnd), ax > 0 ? Direction.WEST : ax < 0 ? Direction.EAST : az > 0 ? Direction.NORTH : Direction.SOUTH, false)
							: k > 3 ? brickWall() : tile());
				}
			}
		}
		if (graves < 0) {
			// No graveyard: the dead lie around the bell spire
			for (int i = 0; i < 5 + rnd.nextInt(4); i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				grave(p, rnd, cx + (int) Math.round(Math.cos(a) * 4.5), top + 1, cz + (int) Math.round(Math.sin(a) * 4.5));
			}
		}
	}

	/** A headstone of tuff, sometimes with a skull on it, and candles at its foot. */
	private static void grave(Plan p, Random rnd, int x, int y, int z) {
		p.set(x, y, z, rnd.nextInt(3) == 0 ? mc("chiseled_tuff", mc("tuff", stone())) : mc("tuff", stone()));
		if (rnd.nextInt(3) == 0) {
			p.set(x, y + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
		}
		BlockState candle = mc("candle");
		if (candle != null && candle.hasProperty(BlockStateProperties.CANDLES)) {
			candle = candle.setValue(BlockStateProperties.CANDLES, 1 + rnd.nextInt(3)).setValue(BlockStateProperties.LIT, rnd.nextBoolean());
		}
		p.soft(x + 1, y, z, candle);
	}

	/** A graveyard: a low wall with a gate toward the plaza, mossy ground and soul soil, rows of graves, a dead tree. */
	private static void graveyard(Plan p, Random rnd, int gx, int top, int gz, int cx, int cz) {
		int half = 4;
		// the gate faces the plaza: on the side of the wall nearest to it
		int gateX = Integer.signum(cx - gx), gateZ = Integer.signum(cz - gz);
		boolean gateOnX = Math.abs(cx - gx) >= Math.abs(cz - gz);
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				boolean rim = Math.abs(dx) == half || Math.abs(dz) == half;
				int x = gx + dx, z = gz + dz;
				p.set(x, top, z, rim ? bsBrick() : rnd.nextInt(4) == 0 ? mc("soul_soil", stone()) : rnd.nextBoolean() ? moss() : mc("pale_moss_block", moss()));
				p.clear(x, top + 1, z);
				if (rim) {
					boolean gate = gateOnX ? dx == gateX * half && Math.abs(dz) <= 1 : dz == gateZ * half && Math.abs(dx) <= 1;
					if (!gate) {
						p.set(x, top + 1, z, Math.abs(dx) == half && Math.abs(dz) == half ? bsBrick() : brickWall());
					}
				}
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			int x = gateOnX ? gx + gateX * half : gx + s * 2, z = gateOnX ? gz + s * 2 : gz + gateZ * half;
			p.set(x, top + 2, z, rnd.nextBoolean() ? soulLantern() : lantern());
		}
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				if (rnd.nextInt(5) > 0 && !(dx == 0 && dz == 0)) {
					grave(p, rnd, gx + dx, top + 1, gz + dz);
				}
			}
		}
		deadTree(p, rnd, gx, top + 1, gz);
	}

	/**
	 * A Thinned house: a stone ground floor rising through the gradient, a timber-framed upper floor of violet plaster
	 * jettied out over the street, recessed windows with sills, a steep slate roof with eaves, filled gables with a
	 * small round window, a chimney with a smouldering fire and a door with a lantern on a bracket.
	 */
	private static void house(Plan p, Random rnd, int x, int y, int z, int rot, boolean withLore) {
		Frame f = new Frame(p, x, y, z, rot);
		int w = 2 + rnd.nextInt(2), d = 3, h1 = 4, h2 = 4 + rnd.nextInt(2);
		// Ground floor: masonry
		for (int lx = -w; lx <= w; lx++) {
			for (int lz = -d; lz <= d; lz++) {
				f.set(lx, -1, lz, smooth());
				boolean edgeX = Math.abs(lx) == w, edgeZ = Math.abs(lz) == d;
				for (int ly = 0; ly < h1; ly++) {
					if (!edgeX && !edgeZ) {
						f.clear(lx, ly, lz);
						continue;
					}
					boolean corner = edgeX && edgeZ;
					boolean window = (ly == 1 || ly == 2) && !corner && ((edgeZ && Math.abs(lx) == 1) || (edgeX && Math.abs(lz) == 1));
					boolean door = lz == d && lx == 0 && ly <= 1;
					if (door) {
						f.clear(lx, ly, lz);
					} else if (window) {
						f.clear(lx, ly, lz);
						f.set(edgeX ? lx - Integer.signum(lx) : lx, ly, edgeZ ? lz - Integer.signum(lz) : lz, warmPane());
					} else {
						f.set(lx, ly, lz, corner ? grad(trimGrad(), ly / 12.0, rnd) : weather(grad(wallGrad(), 0.1 + ly * 0.08, rnd), rnd, 0.15));
					}
				}
			}
		}
		// Sills under the ground-floor windows
		for (int s = -1; s <= 1; s += 2) {
			f.set(s, 0, d + 1, stairs(brickStairs(), Direction.NORTH, true));
			f.set(s, 0, -d - 1, stairs(brickStairs(), Direction.SOUTH, true));
		}
		// The jetty: a beam course, then the upper floor overhanging front and back by one
		int dz = d + 1;
		for (int lx = -w; lx <= w; lx++) {
			for (int lz = -dz; lz <= dz; lz++) {
				boolean rim = Math.abs(lx) == w || Math.abs(lz) == dz;
				f.set(lx, h1, lz, rim ? axis(strippedLog(), Math.abs(lz) == dz ? Direction.Axis.X : Direction.Axis.Z) : planks());
			}
		}
		for (int lx = -w; lx <= w; lx += 2) {
			f.set(lx, h1 - 1, dz, stairs(mc("dark_oak_stairs", planks()), Direction.NORTH, true));
			f.set(lx, h1 - 1, -dz, stairs(mc("dark_oak_stairs", planks()), Direction.SOUTH, true));
		}
		for (int lx = -w; lx <= w; lx++) {
			for (int lz = -dz; lz <= dz; lz++) {
				boolean edgeX = Math.abs(lx) == w, edgeZ = Math.abs(lz) == dz;
				for (int ly = h1 + 1; ly <= h1 + h2; ly++) {
					if (!edgeX && !edgeZ) {
						f.clear(lx, ly, lz);
						continue;
					}
					boolean post = (edgeX && edgeZ) || (edgeZ && lx == 0 && w == 3) || (edgeX && lz == 0);
					boolean window = ly >= h1 + 2 && ly <= h1 + 3 && !post && ((edgeZ && Math.abs(lx) == 1) || (edgeX && Math.abs(lz) == 2));
					if (window) {
						f.set(lx, ly, lz, warmPane());
					} else if (post) {
						f.set(lx, ly, lz, log());
					} else if (ly == h1 + h2) {
						f.set(lx, ly, lz, axis(strippedLog(), edgeZ ? Direction.Axis.X : Direction.Axis.Z));
					} else {
						f.set(lx, ly, lz, rnd.nextInt(7) == 0 ? mc("gray_terracotta", plaster()) : plaster());
					}
				}
			}
		}
		// A lantern inside each floor so the windows glow
		f.set(-w + 1, 0, -d + 1, lantern());
		f.set(w - 1, h1 + 1, dz - 1, lantern());
		// Steep slate roof, gable along z, eaves past the walls, ridge tiles
		int ry = h1 + h2 + 1;
		int rw = w + 1;
		for (int k = 0; k <= rw; k++) {
			int half = rw - k;
			for (int lz = -dz - 1; lz <= dz + 1; lz++) {
				f.set(half, ry + k, lz, stairs(roofStairs(rnd), Direction.WEST, false));
				f.set(-half, ry + k, lz, stairs(roofStairs(rnd), Direction.EAST, false));
				if (half == 0) {
					f.set(0, ry + k, lz, tile());
				}
				if (Math.abs(lz) == dz && half > 0) {
					for (int lx = -half + 1; lx <= half - 1; lx++) {
						boolean round = k == 1 && lx == 0;
						f.set(lx, ry + k, lz, round ? warmPane() : (k == 0 ? axis(strippedLog(), Direction.Axis.X) : plaster()));
					}
				}
			}
		}
		// Chimney at the back with a smouldering fire on top
		int chx = w - 1, chz = -dz;
		for (int ly = 0; ly <= ry + rw + 1; ly++) {
			f.set(chx, ly, chz, ly > ry ? mc("bricks", brick()) : grad(wallGrad(), ly / 20.0, rnd));
		}
		f.set(chx, ry + rw + 2, chz, mc("campfire", lantern()));
		// Door, with a lantern on a bracket beside it
		BlockState door = mc("dark_oak_door");
		if (door != null && door.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			f.set(0, 0, d, door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			f.set(0, 1, d, door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}
		f.set(0, -1, d + 1, smooth());
		f.set(1, 2, d + 1, stairs(brickStairs(), Direction.NORTH, true));
		f.set(1, 1, d + 1, hanging(lantern()));
		if (withLore) {
			f.then(-w + 1, 0, d - 1, loot(f.pos(-w + 1, 0, d - 1), 2));
		}
	}

	// ------------------------------------------------------------------ hanging towers, halos

	/** A tower hanging upside down beneath its island, corbelled at every floor, its point dripping light. */
	public static void hangingTower(Plan p, Random rnd, int cx, int bottomY, int cz) {
		int b = 4 + rnd.nextInt(2);
		int len = 22 + rnd.nextInt(16);
		for (int k = 0; k < len; k++) {
			int y = bottomY - k;
			double t = 1 - (double) k / len;
			for (int u = -b; u <= b; u++) {
				for (int side = 0; side < 4; side++) {
					int lx = Face.lx(side, u, b), lz = Face.lz(side, u, b);
					boolean window = Math.abs(u) == 1 && k % 8 >= 3 && k % 8 <= 5;
					if (window) {
						p.set(cx + Face.lx(side, u, b - 1), y, cz + Face.lz(side, u, b - 1), stainedGlass(u, k % 8, 5));
						continue;
					}
					p.set(cx + lx, y, cz + lz, Math.abs(u) == b ? grad(trimGrad(), t, rnd) : k % 8 == 0 ? bsBrick() : wall(t, rnd));
				}
			}
			if (k % 8 == 0) {
				for (int lx = -b + 1; lx <= b - 1; lx++) {
					for (int lz = -b + 1; lz <= b - 1; lz++) {
						p.set(cx + lx, y, cz + lz, smooth());
					}
				}
				p.set(cx, y - 1, cz, hanging(lantern()));
				// a corbelled ring around the floor
				for (int side = 0; side < 4; side++) {
					Direction in = Face.out(side).getOpposite();
					for (int u = -b + 1; u <= b - 1; u += 2) {
						p.set(cx + Face.lx(side, u, b + 1), y, cz + Face.lz(side, u, b + 1), stairs(brickStairs(), in, false));
					}
				}
			}
		}
		// The inverted roof, in stairs
		int y0 = bottomY - len;
		for (int k = 0; k <= b + 6; k++) {
			int half = (int) Math.round((b + 1) * (1 - (double) k / (b + 6)));
			if (half <= 0) {
				break;
			}
			for (int side = 0; side < 4; side++) {
				Direction in = Face.out(side).getOpposite();
				for (int u = -half; u <= half; u++) {
					p.set(cx + Face.lx(side, u, half), y0 - k, cz + Face.lz(side, u, half), stairs(roofStairs(rnd), in, true));
				}
			}
		}
		int tip = y0 - b - 7;
		p.set(cx, tip, cz, vein());
		for (int k = 1; k <= 5; k++) {
			p.set(cx, tip - k, cz, chain());
		}
		p.set(cx, tip - 6, cz, hanging(soulLantern()));
		p.falls.add(new int[] {cx, tip - 6, cz, 30});
	}

	/** A tilted halo of stone turning around a floating crystal, with motes of rock about it. */
	public static void halo(Plan p, Random rnd, int cx, int top, int cz, int r) {
		int radius = Math.max(9, r - 1);
		int cy = top + radius + 6;
		double tilt = 0.35 + rnd.nextDouble() * 0.5, yaw = rnd.nextDouble() * Math.PI;
		ring(p, rnd, cx, cy, cz, radius, tilt, yaw, 2);
		crystal(p, rnd, cx, cy, cz, radius >= 14 ? 4 : 3);
		for (int i = 0; i < 6; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int rx = cx + (int) Math.round(Math.cos(a) * (radius + 6)), rz = cz + (int) Math.round(Math.sin(a) * (radius + 6));
			int ry = cy - radius + rnd.nextInt(radius * 2);
			int rr = 1 + rnd.nextInt(2);
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					for (int dy = -rr; dy <= 0; dy++) {
						if (dx * dx + dz * dz + dy * dy * 2 <= rr * rr + 1) {
							p.set(rx + dx, ry + dy, rz + dz, dy == 0 ? moss() : grad(rockGrad(), -dy / 3.0, rnd));
						}
					}
				}
			}
		}
		stones(p, rnd, cx, top + 1, cz);
	}

	/** A ring of masonry in space, tilted and turned, studded with silk; the stone lightens around its circle. */
	static void ring(Plan p, Random rnd, int cx, int cy, int cz, int radius, double tilt, double yaw, int thick) {
		double ux = Math.cos(yaw), uz = Math.sin(yaw);
		double vx = -Math.sin(yaw) * Math.cos(tilt), vy = Math.sin(tilt), vz = Math.cos(yaw) * Math.cos(tilt);
		int steps = radius * 12;
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			double px = cx + radius * (Math.cos(a) * ux + Math.sin(a) * vx);
			double py = cy + radius * (Math.sin(a) * vy);
			double pz = cz + radius * (Math.cos(a) * uz + Math.sin(a) * vz);
			double t = 0.5 + 0.5 * Math.sin(a);
			for (int dt = 0; dt < thick; dt++) {
				for (int q = 0; q < thick; q++) {
					p.set((int) Math.floor(px) + dt, (int) Math.floor(py) + q, (int) Math.floor(pz),
							i % 24 < 2 ? vein() : grad(wallGrad(), t, rnd));
				}
			}
		}
	}

	/** An ordinary island: ruins, dead trees, lamps, standing stones, crystals, spread over its open ground. */
	public static void plain(Plan p, Random rnd, Shape s) {
		// The land's own shapes first, while the open ground is widest: an arch of rock, a terraced hill on a large island
		Briefs.Brief brief = Briefs.current();
		if (rnd.nextDouble() < brief.arches()) {
			grounds(p, rnd, s, 1, ROCK_ARCH);
		}
		if (s.r >= 18 && rnd.nextDouble() < brief.terraces()) {
			grounds(p, rnd, s, 1, TERRACES);
		}
		grounds(p, rnd, s, 1 + s.r / 6 + rnd.nextInt(2), ARCH, ARCH, TREE, LAMP, STONES, CLUSTER, FLOATING, OUTCROP, OUTCROP);
	}

	static final int ARCH = 0, TREE = 1, LAMP = 2, STONES = 3, CLUSTER = 4, FLOATING = 5, OUTCROP = 6, ROCK_ARCH = 7, TERRACES = 8;

	/**
	 * Dresses the open ground of an island with the given kinds of things: never on a building, a floor, a street
	 * (the island's axes are kept clear for them) or each other, and always with ground under them.
	 */
	public static void grounds(Plan p, Random rnd, Shape s, int count, int... kinds) {
		for (int placed = 0, tries = 0; placed < count && tries < count * 10; tries++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double d = 4 + rnd.nextDouble() * Math.max(1, s.edge(a) - 10);
			int x = s.x + (int) Math.round(Math.cos(a) * d), z = s.z + (int) Math.round(Math.sin(a) * d);
			if (Math.abs(x - s.x) <= 3 || Math.abs(z - s.z) <= 3) {
				continue;
			}
			int kind = kinds[rnd.nextInt(kinds.length)];
			int scale = 9 + rnd.nextInt(8);
			int room = switch (kind) {
				case ARCH -> 6; case STONES -> 5; case CLUSTER, OUTCROP -> 4; case TREE -> 3;
				case ROCK_ARCH -> Landforms.ARCH_ROOM; case TERRACES -> Landforms.TERRACE_ROOM; default -> 2;
			};
			int tall = switch (kind) {
				case ARCH -> 14; case TREE -> 11; case CLUSTER -> scale + 2; case FLOATING -> 30;
				case ROCK_ARCH -> Landforms.ARCH_TALL; case TERRACES -> Landforms.TERRACE_TALL; default -> 6;
			};
			if (!free(p, x, s.top, z, room, tall)) {
				continue;
			}
			int y = p.ground(x, z, s.top) + 1;
			switch (kind) {
				case ARCH -> arch(p, rnd, x, y, z);
				case TREE -> deadTree(p, rnd, x, y, z);
				case LAMP -> lamp(p, rnd, x, y, z);
				case STONES -> stones(p, rnd, x, y, z);
				case CLUSTER -> cluster(p, rnd, x, y, z, scale);
				case OUTCROP -> outcrop(p, rnd, x, y, z);
				case ROCK_ARCH -> {
					if (!Landforms.rockArch(p, rnd, x, y, z)) {
						continue;
					}
				}
				case TERRACES -> Landforms.terraces(p, rnd, x, y, z);
				default -> crystal(p, rnd, x, y + 12 + rnd.nextInt(7), z, 2 + rnd.nextInt(2));
			}
			placed++;
		}
	}

	/** True when a patch of the island's turf is open: ground under all of it, nothing built on it or over it. */
	static boolean free(Plan p, int x, int top, int z, int rad) {
		return free(p, x, top, z, rad, 30);
	}

	/**
	 * As {@link #free(Plan, int, int, int, int)}, with the open air above the middle only checked {@code tall} high. The
	 * ground is read where the island lays it ({@code top} where the plan has none): the patch must be near level, its
	 * surface within a block of the middle's.
	 */
	static boolean free(Plan p, int x, int top, int z, int rad, int tall) {
		int y0 = p.ground(x, z, top);
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				int g0 = p.ground(x + dx, z + dz, top);
				if (Math.abs(g0 - y0) > 1) {
					return false;
				}
				BlockState g = p.get(x + dx, g0, z + dz);
				if (g == null || !turf(g)) {
					return false;
				}
				BlockState on = p.get(x + dx, g0 + 1, z + dz);
				if (on != null && !growth(on)) {
					return false;
				}
				for (int y = g0 + 2; y <= g0 + Math.min(6, tall); y++) {
					if (p.has(x + dx, y, z + dz)) {
						return false;
					}
				}
			}
		}
		for (int y = y0 + 7; y <= y0 + tall; y++) {
			if (p.has(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/** The surface of an island as {@link #island} lays it. */
	static boolean turf(BlockState s) {
		return s == moss() || s == mc("pale_moss_block", moss()) || s == stone() || s == brick() || s == cobble() || s == mc("cobbled_deepslate", stone());
	}

	/** Grass, ferns and flowers: what a path or a building may simply replace. */
	static boolean growth(BlockState s) {
		return s == mc("short_grass") || s == mc("fern") || s == mc("allium") || s == mc("azure_bluet") || s == mc("firefly_bush")
				|| s == mc("open_eyeblossom");
	}

	/**
	 * Re-surfaces turf (and only turf) with paving, clearing the growth on it, on the ground's surface where the island
	 * lays it ({@code top} where the plan has no ground of its own). On a structure planned apart from its island it is
	 * kept, to be laid when the structure is merged onto the island.
	 */
	static void pave(Plan p, int x, int top, int z, BlockState s) {
		if (p.ground.isEmpty()) {
			p.paving.add(new Object[] {x, top, z, s});
			return;
		}
		int y = p.ground(x, z, top);
		BlockState g = p.get(x, y, z);
		if (g == null || !turf(g)) {
			return;
		}
		p.set(x, y, z, s);
		BlockState on = p.get(x, y + 1, z);
		if (on != null && growth(on)) {
			p.clear(x, y + 1, z);
		}
	}

	/** Paving from the middle of a street to its worn edges: polished pale stone, pale bricks, tiles, bricks, cobbles. */
	static BlockState[] pathGrad() {
		return new BlockState[] {palePol(), pale(), tile(), brick(), cobble()};
	}

	/**
	 * Streets along the island's axes, from {@code inner} out to the rim, on the sides that have a bridge (bit 0 east,
	 * 1 west, 2 south, 3 north): bridges always land on an island's axis, so every street meets one. Built streets are
	 * paved in a gradient with moss creeping in at the edges and lamps along the way; a {@code trail} is worn and
	 * patchy instead.
	 */
	public static void streets(Plan p, Random rnd, Shape s, int mask, int inner, boolean trail) {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		BlockState[] path = pathGrad();
		BlockState[] worn = {cobble(), mc("tuff", stone()), mc("deepslate", stone()), stone()};
		for (int i = 0; i < 4; i++) {
			if ((mask & (1 << i)) == 0) {
				continue;
			}
			int sx = dirs[i][0], sz = dirs[i][1];
			int len = (int) Math.floor(s.edge(Math.atan2(sz, sx))) - 1;
			for (int k = inner; k <= len; k++) {
				double t = (double) (k - inner) / Math.max(1, len - inner);
				for (int w = -1; w <= 1; w++) {
					int x = s.x + sx * k + sz * w, z = s.z + sz * k + sx * w;
					BlockState pave;
					if (trail) {
						if (rnd.nextDouble() < (w == 0 ? 0.2 : 0.55)) {
							continue;
						}
						pave = worn[rnd.nextInt(worn.length)];
					} else {
						pave = w != 0 && rnd.nextDouble() < 0.12 + 0.3 * t ? moss() : weather(grad(path, t, rnd), rnd, 0.1);
					}
					pave(p, x, s.top, z, pave);
				}
				if (!trail && (k - inner) % 9 == 5 && k < len - 3) {
					int side = ((k - inner) / 9 & 1) == 0 ? 2 : -2;
					int x = s.x + sx * k + sz * side, z = s.z + sz * k + sx * side;
					if (free(p, x, s.top, z, 1)) {
						lamp(p, rnd, x, p.ground(x, z, s.top) + 1, z);
					}
				}
			}
			// A pair of lamps where the street meets the bridge
			if (!trail) {
				for (int side = -2; side <= 2; side += 4) {
					int x = s.x + sx * (len - 2) + sz * side, z = s.z + sz * (len - 2) + sx * side;
					if (free(p, x, s.top, z, 1)) {
						lamp(p, rnd, x, p.ground(x, z, s.top) + 1, z);
					}
				}
			}
		}
	}

	/**
	 * A cluster of amethyst shards breaking out of the ground: straight faceted prisms (an octagonal section) that run
	 * to a point, the tallest nearly upright with a heart of crying obsidian, the others leaning out around it; the
	 * stone clouds into violet glass toward the tips.
	 */
	static void cluster(Plan p, Random rnd, int cx, int y, int cz, int scale) {
		int shards = 3 + rnd.nextInt(3);
		for (int i = 0; i < shards; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double tilt = i == 0 ? rnd.nextDouble() * 0.12 : 0.35 + rnd.nextDouble() * 0.4;
			double len = (i == 0 ? 1.0 : 0.4 + rnd.nextDouble() * 0.4) * scale;
			double rad = 0.9 + len * 0.11;
			double bx = cx + 0.5 + (i == 0 ? 0 : Math.cos(a) * 1.6), by = y - 1.0, bz = cz + 0.5 + (i == 0 ? 0 : Math.sin(a) * 1.6);
			double dx = Math.sin(tilt) * Math.cos(a), dy = Math.cos(tilt), dz = Math.sin(tilt) * Math.sin(a);
			// two directions across the shard, for its facets
			double e1x = -dz, e1z = dx, el = Math.sqrt(e1x * e1x + e1z * e1z);
			if (el < 1e-6) {
				e1x = 1;
				e1z = 0;
				el = 1;
			}
			e1x /= el;
			e1z /= el;
			double e2x = dy * e1z, e2y = dz * e1x - dx * e1z, e2z = -dy * e1x;
			int reach = (int) Math.ceil(len + rad + 2);
			for (int vx = -reach; vx <= reach; vx++) {
				for (int vy = -2; vy <= reach; vy++) {
					for (int vz = -reach; vz <= reach; vz++) {
						double px = Math.floor(bx) + vx + 0.5 - bx, py = Math.floor(by) + vy + 0.5 - by, pz = Math.floor(bz) + vz + 0.5 - bz;
						double t = px * dx + py * dy + pz * dz;
						if (t < -1.0 || t > len) {
							continue;
						}
						double qx = px - dx * t, qy = py - dy * t, qz = pz - dz * t;
						double u = Math.abs(qx * e1x + qz * e1z), w = Math.abs(qx * e2x + qy * e2y + qz * e2z);
						double facet = Math.max(Math.max(u, w), (u + w) * 0.7071);
						double f = Math.max(0, t) / len;
						// straight body, then a point over the last third
						double rr = f < 0.66 ? rad * (1 - f * 0.25) : rad * 0.835 * (1 - (f - 0.66) / 0.34);
						if (facet <= rr + 0.15) {
							boolean heart = i == 0 && facet < rr - 1.1 && f < 0.7;
							BlockState b = heart ? crying() : f > 0.72 || facet > rr - 0.7 && f > 0.45 ? (rnd.nextInt(3) == 0 ? glass2() : glass()) : amethyst();
							p.set((int) Math.floor(bx) + vx, (int) Math.floor(by) + vy, (int) Math.floor(bz) + vz, b);
						}
					}
				}
			}
		}
		// Buds and small clusters about its foot
		BlockState bud = mc("amethyst_cluster"), large = mc("large_amethyst_bud", bud);
		for (int k = 0; k < 6 + rnd.nextInt(6); k++) {
			int bx = cx + rnd.nextInt(-4, 5), bz = cz + rnd.nextInt(-4, 5);
			BlockState on = p.get(bx, y, bz);
			if (p.has(bx, y - 1, bz) && (on == null || growth(on))) {
				p.set(bx, y, bz, rnd.nextBoolean() ? bud : large);
			}
		}
	}

	// ------------------------------------------------------------------ bridges

	/** Where a bridge between two islands runs: start, end, landing (along the axis) and landing height; null if none fits. */
	private static int[] span(Shape a, Shape b, boolean alongX) {
		int am = alongX ? a.x : a.z, bm = alongX ? b.x : b.z;
		double angA = alongX ? 0 : Math.PI / 2, angB = angA + Math.PI;
		int start = am + (int) Math.floor(a.edge(angA)) - 2;
		int end = bm - (int) Math.floor(b.edge(angB)) + 2;
		if (end - start < 10) {
			return null;
		}
		int mid = (start + end) / 2;
		int ym = Math.floorDiv(a.top + b.top, 2);
		int l1 = (mid - 3) - start, l2 = end - (mid + 3);
		if (Math.abs(ym - a.top) > l1 - 2 || Math.abs(b.top - ym) > l2 - 2) {
			return null;
		}
		return new int[] {start, end, mid, ym};
	}

	/** True when a bridge can be strung between two islands (so their streets can be laid out to meet it). */
	public static boolean canBridge(Shape a, Shape b, boolean alongX) {
		return span(a, b, alongX) != null;
	}

	/**
	 * A bridge from one island to the next along an axis: it starts and ends a couple of blocks inside each island's
	 * predicted rim (where the island's street meets it), runs straight, turns on a landing halfway to line up with the
	 * other island, and climbs with slab ramps or real stairs; the landing rests on a tapering pier. Where it leaves
	 * each island it passes under a gateway that frames the view across (as the towers at the ends of the Charles
	 * Bridge do).
	 *
	 * <p>Every bridge is of stone: kerbs with connected wall railings and posts with lanterns, and under every span a
	 * shallow pointed arch of masonry springing from pendants that end in soul lanterns.
	 *
	 * @return false when the islands are too close or too steep apart for a bridge
	 */
	public static boolean bridge(Plan p, Random rnd, Shape a, Shape b, boolean alongX) {
		int[] sp = span(a, b, alongX);
		if (sp == null) {
			return false;
		}
		int start = sp[0], end = sp[1], mid = sp[2], ym = sp[3];
		int ap = alongX ? a.z : a.x, bp = alongX ? b.z : b.x;
		int ya = a.top, yb = b.top;
		// deck cells: (m, p) -> {fullY, slab, stairFacing(+1 = +m, -1 = -m, 0 none)}
		Map<Long, int[]> deck = new LinkedHashMap<>();
		ramp(deck, start, mid - 3, ap, ya, ym);
		int p0 = Math.min(ap, bp) - 1, p1 = Math.max(ap, bp) + 1;
		for (int m = mid - 3; m <= mid + 3; m++) {
			for (int pp = p0; pp <= p1; pp++) {
				deck.put(key(m, pp), new int[] {ym, 0, 0});
			}
		}
		ramp(deck, mid + 3, end, bp, ym, yb);
		// Full-block steps become stairs
		for (Map.Entry<Long, int[]> e : deck.entrySet()) {
			int m = (int) (e.getKey() >> 32), pp = e.getKey().intValue();
			int[] c = e.getValue();
			for (int dm = -1; dm <= 1; dm += 2) {
				int[] n = deck.get(key(m + dm, pp));
				if (n != null && c[1] == 0 && n[1] == 0 && c[0] == n[0] + 1) {
					c[2] = -dm; // ascending from the neighbour towards this cell
				}
			}
		}
		BlockState[] deckGrad = {tile(), bsBrick(), smooth()};
		BlockState stairB = brickStairs();
		// Place the deck
		for (Map.Entry<Long, int[]> e : deck.entrySet()) {
			int m = (int) (e.getKey() >> 32), pp = e.getKey().intValue();
			int[] c = e.getValue();
			int x = alongX ? m : pp, z = alongX ? pp : m;
			if (a.inside(x, z, 0.5) || b.inside(x, z, 0.5)) {
				// on the island itself, only fill in where the turf lies lower
				p.soft(x, c[0], z, grad(deckGrad, rnd.nextDouble(), rnd));
				continue;
			}
			if (c[2] != 0) {
				Direction up = alongX ? (c[2] > 0 ? Direction.EAST : Direction.WEST) : (c[2] > 0 ? Direction.SOUTH : Direction.NORTH);
				p.set(x, c[0], z, stairs(stairB, up, false));
				p.set(x, c[0] - 1, z, tile());
			} else {
				p.set(x, c[0], z, grad(deckGrad, noise(7, x / 3.0, z / 3.0), rnd));
				if (c[1] == 1) {
					p.set(x, c[0] + 1, z, slab(tileSlab(), false));
				}
			}
		}
		// Arches under both runs
		arcade(p, rnd, deck, start, mid - 3, ap, alongX, a, b);
		arcade(p, rnd, deck, mid + 3, end, bp, alongX, a, b);
		// Kerbs and railings around the deck (as high as the deck's walking surface), corbels under the kerbs
		Map<Long, Integer> rails = new LinkedHashMap<>();
		Map<Long, Direction> railOut = new HashMap<>();
		for (Map.Entry<Long, int[]> e : deck.entrySet()) {
			int m = (int) (e.getKey() >> 32), pp = e.getKey().intValue();
			int[] c = e.getValue();
			int[][] nb = {{m + 1, pp}, {m - 1, pp}, {m, pp + 1}, {m, pp - 1}};
			for (int[] n : nb) {
				long k = key(n[0], n[1]);
				if (deck.containsKey(k)) {
					continue;
				}
				int x = alongX ? n[0] : n[1], z = alongX ? n[1] : n[0];
				if (a.inside(x, z, -0.5) || b.inside(x, z, -0.5)) {
					continue;
				}
				rails.merge(k, c[0] + c[1], Integer::max);
				int dm = n[0] - m, dp = n[1] - pp;
				int ox = alongX ? dm : dp, oz = alongX ? dp : dm;
				railOut.put(k, ox > 0 ? Direction.EAST : ox < 0 ? Direction.WEST : oz > 0 ? Direction.SOUTH : Direction.NORTH);
			}
		}
		int count = 0;
		for (Map.Entry<Long, Integer> e : rails.entrySet()) {
			int m = (int) (e.getKey() >> 32), pp = e.getKey().intValue();
			int y = e.getValue();
			int x = alongX ? m : pp, z = alongX ? pp : m;
			Direction out = railOut.get(e.getKey());
			p.set(x, y, z, bsBrick());
			p.set(x, y + 1, z, brickWall());
			p.soft(x, y - 1, z, stairs(brickStairs(), out.getOpposite(), true));
			if (Math.floorMod(m, 6) == 0) {
				p.set(x, y + 2, z, brickWall());
				if (Math.floorMod(m, 12) == 0) {
					p.set(x, y + 3, z, rnd.nextBoolean() ? lantern() : soulLantern());
				}
			}
			count++;
		}
		// The gateways where the bridge leaves each island, framing the view across
		gateway(p, rnd, deck, start, mid - 3, ap, alongX, a, b, true);
		gateway(p, rnd, deck, mid + 3, end, bp, alongX, a, b, false);
		// The pier under the landing
		int pierP = (p0 + p1) / 2, pierHalf = Math.max(2, (p1 - p0) / 2);
		for (int k = 1; k <= 9; k++) {
			int hm = Math.max(0, 3 - k / 3), hp = Math.max(0, pierHalf - k / 3);
			for (int m = mid - hm; m <= mid + hm; m++) {
				for (int pp = pierP - hp; pp <= pierP + hp; pp++) {
					int x = alongX ? m : pp, z = alongX ? pp : m;
					p.soft(x, ym - k, z, k > 7 ? amethyst() : grad(rockGrad(), k / 9.0, rnd));
				}
			}
		}
		int lx = alongX ? mid : pierP, lz = alongX ? pierP : mid;
		p.falls.add(new int[] {lx, ym - 10, lz, 20});
		return count > 0;
	}

	/**
	 * The first level deck cell of a run out over the nothing (no step of stairs), counting from its island's end
	 * ({@code fromStart}), or null: where a gateway stands.
	 */
	private static int[] firstOut(Map<Long, int[]> deck, int m0, int m1, int pp, boolean alongX, Shape a, Shape b, boolean fromStart) {
		for (int i = 0; i <= m1 - m0; i++) {
			int m = fromStart ? m0 + i : m1 - i;
			int[] c = deck.get(key(m, pp));
			int x = alongX ? m : pp, z = alongX ? pp : m;
			if (c != null && c[2] == 0 && !a.inside(x, z, -1) && !b.inside(x, z, -1)) {
				return new int[] {m, c[0], c[1], c[2]};
			}
		}
		return null;
	}

	/** A gateway over the deck where a run leaves its island: two pillars and a pointed arch with a keystone and a soul lantern hung in it. */
	private static void gateway(Plan p, Random rnd, Map<Long, int[]> deck, int m0, int m1, int pp, boolean alongX, Shape a, Shape b,
			boolean fromStart) {
		int[] g = firstOut(deck, m0, m1, pp, alongX, a, b, fromStart);
		if (g == null) {
			return;
		}
		int m = g[0], y = g[1] + 1;
		Direction across = alongX ? Direction.SOUTH : Direction.EAST;
		for (int w = -2; w <= 2; w += 4) {
			int x = alongX ? m : pp + w, z = alongX ? pp + w : m;
			for (int k = 0; k <= 3; k++) {
				p.set(x, y + k, z, k == 0 ? palePol() : axis(pillar(), Direction.Axis.Y));
			}
			p.set(x, y + 4, z, palePol());
			p.set(x, y + 5, z, paleWall());
		}
		for (int w = -1; w <= 1; w++) {
			int x = alongX ? m : pp + w, z = alongX ? pp + w : m;
			if (w == 0) {
				p.set(x, y + 4, z, carved());
				p.set(x, y + 5, z, palePol());
				p.set(x, y + 3, z, hanging(soulLantern()));
			} else {
				p.set(x, y + 4, z, stairs(palePolStairs(), w > 0 ? across : across.getOpposite(), true));
			}
		}
	}

	/**
	 * The underside of one run of deck: spans of about eleven blocks, each a shallow pointed arch, thick at the
	 * pendants between them and thin at the crown, curved with upside-down stairs; each pendant ends in a carved boss,
	 * a short chain and a soul lantern.
	 */
	private static void arcade(Plan p, Random rnd, Map<Long, int[]> deck, int m0, int m1, int pp, boolean alongX, Shape a, Shape b) {
		int len = m1 - m0;
		if (len < 6) {
			return;
		}
		int spans = Math.max(1, (int) Math.round(len / 11.0));
		double per = len / (double) spans;
		int[] depth = new int[len + 1];
		for (int i = 0; i <= len; i++) {
			double u = (i / per) % 1.0;
			depth[i] = 1 + (int) Math.round(3.4 * (1 - Math.sin(Math.PI * u)));
		}
		BlockState[] under = {tile(), brick(), bsBrick()};
		for (int i = 0; i <= len; i++) {
			int m = m0 + i;
			for (int w = -1; w <= 1; w++) {
				int[] c = deck.get(key(m, pp + w));
				if (c == null) {
					continue;
				}
				int x = alongX ? m : pp + w, z = alongX ? pp + w : m;
				if (a.inside(x, z, -1) || b.inside(x, z, -1)) {
					continue;
				}
				int d = depth[i];
				for (int k = 1; k <= d; k++) {
					BlockState s = grad(under, (double) k / 5, rnd);
					if (k == d && d > 1) {
						// the bottom course curves toward the deeper side of the arch
						int prev = i > 0 ? depth[i - 1] : d, next = i < len ? depth[i + 1] : d;
						if (prev > d || next > d) {
							boolean towardPlus = next > prev;
							Direction f = alongX ? (towardPlus ? Direction.EAST : Direction.WEST) : (towardPlus ? Direction.SOUTH : Direction.NORTH);
							s = stairs(brickStairs(), f, true);
						}
					}
					p.soft(x, c[0] - k, z, s);
				}
			}
			// The pendants between the spans
			boolean pendant = Math.abs(i - Math.round(i / per) * per) < 0.5 && i > 0 && i < len;
			int[] c = deck.get(key(m, pp));
			int x = alongX ? m : pp, z = alongX ? pp : m;
			if (pendant && c != null && !a.inside(x, z, -1) && !b.inside(x, z, -1)) {
				int y = c[0] - depth[i] - 1;
				p.soft(x, y, z, carved());
				p.soft(x, y - 1, z, bsWall());
				int links = 1 + rnd.nextInt(3);
				for (int k = 1; k <= links; k++) {
					p.soft(x, y - 1 - k, z, chain());
				}
				p.soft(x, y - 2 - links, z, hanging(soulLantern()));
			}
		}
	}

	private static long key(int m, int pp) {
		return ((long) m << 32) | (pp & 0xFFFFFFFFL);
	}

	/** One straight, three-wide run of deck from m0 to m1 at p, climbing from y0 to y1 (slabs if there is room). */
	private static void ramp(Map<Long, int[]> deck, int m0, int m1, int pp, int y0, int y1) {
		int len = m1 - m0;
		int d = y1 - y0;
		boolean slabs = Math.abs(d) * 2 <= len - 2;
		for (int i = 0; i <= len; i++) {
			double t = Math.max(0, Math.min(1, (i - 1) / (double) Math.max(1, len - 2)));
			int half = slabs ? (int) Math.round(t * d * 2) : 2 * (int) Math.round(t * d);
			int fullY = y0 + Math.floorDiv(half, 2);
			int slab = Math.floorMod(half, 2);
			for (int w = -1; w <= 1; w++) {
				deck.put(key(m0 + i, pp + w), new int[] {fullY, slab, 0});
			}
		}
	}

	// ------------------------------------------------------------------ the crown of the Heart

	/**
	 * Around the Heart: four broken obelisks on stepped plinths, their shafts split into floating pieces that weep
	 * crying obsidian at every break, runes of silk spiralling up their faces, halos turning about them and crystals
	 * hovering over their points; and a great halo high above the arena with chains hanging from it.
	 */
	public static void crown(Plan p, Random rnd, int hx, int hy, int hz) {
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int ox = hx + (int) Math.round(Math.cos(a) * 64), oz = hz + (int) Math.round(Math.sin(a) * 64);
			int oy = hy - 6 + rnd.nextInt(10);
			island(p, rnd, Shape.of(rnd, ox, oy, oz, 14), true);
			obelisk(p, rnd, ox, oy + 1, oz);
		}
		// The great halo over the arena
		int radius = 34, y = hy + 56;
		int steps = radius * 10;
		for (int i = 0; i < steps; i++) {
			double an = i * Math.PI * 2 / steps;
			double t = 0.5 + 0.5 * Math.sin(an * 2);
			for (int dr = -1; dr <= 1; dr++) {
				int x2 = hx + (int) Math.round(Math.cos(an) * (radius + dr)), z2 = hz + (int) Math.round(Math.sin(an) * (radius + dr));
				p.set(x2, y, z2, i % 20 < 2 ? vein() : grad(wallGrad(), t, rnd));
				if (dr == 0) {
					p.set(x2, y + 1, z2, i % 20 < 2 ? vein() : slab(tileSlab(), false));
				} else {
					p.set(x2, y - 1, z2, stairs(brickStairs(), Direction.NORTH, true));
				}
			}
			if (i % 17 == 0) {
				int x = hx + (int) Math.round(Math.cos(an) * radius), z = hz + (int) Math.round(Math.sin(an) * radius);
				int len = 6 + rnd.nextInt(14);
				for (int k = 2; k <= len; k++) {
					p.soft(x, y - k, z, chain());
				}
				p.soft(x, y - len - 1, z, hanging(soulLantern()));
			}
		}
	}

	/** One broken obelisk, its base at y. */
	static void obelisk(Plan p, Random rnd, int ox, int y, int oz) {
		// A stepped plinth of three courses, stairs climbing inward
		for (int j = 0; j < 3; j++) {
			int half = 7 - j;
			for (int dx = -half; dx <= half; dx++) {
				for (int dz = -half; dz <= half; dz++) {
					boolean rim = Math.abs(dx) == half || Math.abs(dz) == half;
					if (rim && !(Math.abs(dx) == half && Math.abs(dz) == half)) {
						Direction in = Math.abs(dx) == half ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
						p.set(ox + dx, y + j, oz + dz, stairs(bsStairs(), in, false));
					} else {
						p.set(ox + dx, y + j, oz + dz, rim ? bsPol() : (dx + dz & 1) == 0 ? bsBrick() : smooth());
					}
				}
			}
		}
		int base = y + 3;
		int H = 92 + rnd.nextInt(20);
		double[][] segments = {{0, 0.42}, {0.47, 0.76}, {0.81, 1.0}};
		BlockState[] g = {mc("blackstone", bsPol()), bsPol(), smooth(), tile(), mc("polished_tuff", tile()), amethyst()};
		for (double[] seg : segments) {
			int k0 = (int) (seg[0] * H), k1 = (int) (seg[1] * H);
			for (int k = k0; k < k1; k++) {
				double t = (double) k / H;
				int half = (int) Math.round(4 - 3 * t);
				boolean fracture = k <= k0 + 1 && k0 > 0 || k >= k1 - 2 && k1 < H;
				for (int dx = -half; dx <= half; dx++) {
					for (int dz = -half; dz <= half; dz++) {
						boolean face = Math.abs(dx) == half || Math.abs(dz) == half;
						BlockState s;
						if (fracture && rnd.nextInt(3) > 0) {
							s = crying();
						} else if (face) {
							int u = Math.abs(dx) == half ? dz : dx;
							boolean rune = Math.floorMod(u + k, 7) == 0 || (u == 0 && k % 4 == 0);
							s = rune ? vein() : grad(g, t, rnd);
						} else {
							s = vein();
						}
						p.set(ox + dx, base + k, oz + dz, s);
					}
				}
			}
		}
		// Halos about the shaft, slightly tilted, floating free
		ring(p, rnd, ox, base + (int) (0.44 * H), oz, 8, 0.12, rnd.nextDouble() * Math.PI, 1);
		ring(p, rnd, ox, base + (int) (0.78 * H), oz, 6, 0.18, rnd.nextDouble() * Math.PI, 1);
		// A crystal hovering over the point
		crystal(p, rnd, ox, base + H + 8, oz, 3);
		p.falls.add(new int[] {ox, base + H + 2, oz, H});
	}
}
