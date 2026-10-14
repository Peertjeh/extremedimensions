package com.extremedimensions.seam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import com.extremedimensions.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * The architecture of the Seam: colossal gothic spires, giant looms, villages of jettied timber houses, towers hanging
 * upside down beneath their islands, tilted halos around floating crystals, broken obelisks, and stone bridges strung
 * between it all.
 *
 * <p>Built the way a builder would: every surface has <b>depth</b> (pilasters, recessed windows with arched heads and
 * sills, corbels under ledges, water tables, stepped buttresses, stair roofs with dormers, layered portals) and every
 * material runs through a <b>gradient</b> (blackstone at the foot, deepslate in the body, tuff at the crown) with a
 * little dither and weathering, so nothing reads as a flat wall of one block.
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

	private static BlockState stone() { return ModBlocks.HUSH_STONE.defaultBlockState(); }
	private static BlockState brick() { return ModBlocks.HUSH_BRICKS.defaultBlockState(); }
	private static BlockState vein() { return ModBlocks.SEAM_VEIN.defaultBlockState(); }
	private static BlockState dsBrick() { return mc("deepslate_bricks", brick()); }
	private static BlockState dsTile() { return mc("deepslate_tiles", brick()); }
	private static BlockState dsPol() { return mc("polished_deepslate", stone()); }
	private static BlockState dsChis() { return mc("chiseled_deepslate", dsPol()); }
	private static BlockState bsBrick() { return mc("polished_blackstone_bricks", dsBrick()); }
	private static BlockState bsPol() { return mc("polished_blackstone", dsPol()); }
	private static BlockState dsWall() { return mc("deepslate_brick_wall", dsBrick()); }
	private static BlockState bsWall() { return mc("polished_blackstone_brick_wall", dsWall()); }
	private static BlockState dsSlab() { return mc("deepslate_tile_slab", dsTile()); }
	private static BlockState dsStairs() { return mc("deepslate_tile_stairs", dsTile()); }
	private static BlockState dsBrickStairs() { return mc("deepslate_brick_stairs", dsStairs()); }
	private static BlockState bsStairs() { return mc("polished_blackstone_brick_stairs", dsStairs()); }
	private static BlockState glass() { return mc("purple_stained_glass", vein()); }
	private static BlockState glass2() { return mc("magenta_stained_glass", glass()); }
	private static BlockState glass3() { return mc("blue_stained_glass", glass()); }
	private static BlockState warmPane() { return mc("orange_stained_glass_pane", mc("orange_stained_glass", glass())); }
	private static BlockState amethyst() { return mc("amethyst_block", vein()); }
	private static BlockState crying() { return mc("crying_obsidian", vein()); }
	private static BlockState chain() { return mc("iron_chain", mc("chain", Blocks.COBWEB.defaultBlockState())); }
	private static BlockState lantern() { return mc("lantern", Blocks.SOUL_LANTERN.defaultBlockState()); }
	private static BlockState soulLantern() { return Blocks.SOUL_LANTERN.defaultBlockState(); }
	private static BlockState endRod() { return mc("end_rod", vein()); }
	private static BlockState moss() { return mc("moss_block", stone()); }
	private static BlockState wool() { return mc("purple_wool", glass()); }
	private static BlockState wool2() { return mc("magenta_wool", wool()); }
	private static BlockState woolDark() { return mc("black_wool", dsTile()); }
	private static BlockState planks() { return mc("dark_oak_planks", brick()); }
	private static BlockState log() { return mc("dark_oak_log", dsPol()); }
	private static BlockState strippedLog() { return mc("stripped_dark_oak_log", log()); }
	private static BlockState plaster() { return mc("purple_terracotta", brick()); }

	// ------------------------------------------------------------------ palettes: gradients, dither, weathering

	/** Masonry from the foot of a building to its crown: black, then deepslate, then pale tuff. */
	static BlockState[] wallGrad() {
		BlockState ds = dsBrick();
		return new BlockState[] {mc("blackstone", ds), bsBrick(), ds, dsTile(), mc("tuff_bricks", dsTile()), mc("polished_tuff", mc("tuff_bricks", dsTile()))};
	}

	/** Trim, pilasters and frames: smooth stone, a shade off the wall behind it. */
	static BlockState[] trimGrad() {
		return new BlockState[] {bsPol(), dsPol(), dsPol(), mc("polished_tuff", dsPol())};
	}

	/** Island rock, from just under the turf down to the deepest fang. */
	static BlockState[] rockGrad() {
		return new BlockState[] {stone(), mc("deepslate", stone()), mc("cobbled_deepslate", stone()), mc("tuff", stone()), mc("blackstone", stone())};
	}

	/** Picks from a gradient at t (0..1) with dither, so bands blend into each other. */
	static BlockState grad(BlockState[] g, double t, Random rnd) {
		double v = Math.max(0, Math.min(1, t)) * (g.length - 1) + (rnd.nextDouble() - 0.5) * 1.15;
		int i = (int) Math.round(v);
		return g[Math.max(0, Math.min(g.length - 1, i))];
	}

	/** Cracks and wear, here and there. */
	static BlockState weather(BlockState s, Random rnd, double chance) {
		if (s == null || rnd.nextDouble() >= chance) {
			return s;
		}
		if (s == dsBrick()) {
			return mc("cracked_deepslate_bricks", s);
		}
		if (s == dsTile()) {
			return mc("cracked_deepslate_tiles", s);
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

	/** Roof stairs: deepslate tiles mottled with blackstone, the way old slate weathers. */
	static BlockState roofStairs(Random rnd) {
		int k = rnd.nextInt(10);
		return k < 6 ? dsStairs() : k < 9 ? bsStairs() : mc("cobbled_deepslate_stairs", dsStairs());
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

		public void set(int x, int y, int z, BlockState s) {
			if (s != null && y > 1 && y < 250) {
				blocks.put(BlockPos.asLong(x, y, z), s);
			}
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

		/** Joins walls, panes, bars and fences to their neighbours, as if a player had placed them one by one. */
		void connect() {
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

		private List<Map.Entry<Long, BlockState>> sorted;

		/** Joins its walls and fences and orders it bottom to top; safe off the server thread (it touches no world). */
		public void prepare() {
			if (sorted == null) {
				connect();
				sorted = new ArrayList<>(blocks.entrySet());
				sorted.sort((a, b) -> Integer.compare(BlockPos.getY(a.getKey()), BlockPos.getY(b.getKey())));
			}
		}

		/** Queues it, bottom to top, so a structure rises from its island. */
		public void queue() {
			prepare();
			int id = ++nextPlan;
			List<Map.Entry<Long, BlockState>> list = sorted;
			for (Map.Entry<Long, BlockState> e : list) {
				QUEUE.add(new Job(e.getKey(), e.getValue(), null, id));
			}
			for (Object[] a : after) {
				@SuppressWarnings("unchecked") Consumer<ServerLevel> c = (Consumer<ServerLevel>) a[1];
				QUEUE.add(new Job(((BlockPos) a[0]).asLong(), null, c, id));
			}
			UNBUILT.put(id, list.size());
			PLANS.put(id, new Meta(cell == null ? "" : cell, List.copyOf(falls), List.copyOf(marks)));
		}
	}

	/** A queued block to set, or an action to run once every block of its plan stands. */
	private record Job(long pos, BlockState state, Consumer<ServerLevel> action, int plan) {}

	/** What is remembered of a queued plan: its cell, light falls and marks (to forget them if the work is dropped). */
	public record Meta(String cell, List<int[]> falls, List<String> marks) {}

	/** Fresh work, bottom to top. */
	private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
	/** Work in chunks nobody has loaded, by chunk: it is invisible anyway, so it waits instead of loading the chunk. */
	private static final Map<Long, ArrayDeque<Job>> WAITING = new HashMap<>();
	/** Work whose chunk has just loaded: done before fresh work. */
	private static final ArrayDeque<Job> READY = new ArrayDeque<>();
	/** Actions waiting for the last blocks of their plan. */
	private static final Map<Integer, List<Job>> HELD = new HashMap<>();
	private static final Map<Integer, Integer> UNBUILT = new HashMap<>();
	private static final Map<Integer, Meta> PLANS = new HashMap<>();
	private static int nextPlan;
	private static int waiting;
	private static int held;
	private static int calls;

	public static void clear() {
		QUEUE.clear();
		WAITING.clear();
		READY.clear();
		HELD.clear();
		UNBUILT.clear();
		PLANS.clear();
		waiting = 0;
		held = 0;
	}

	/** Blocks and actions still to do, waiting ones included. */
	public static int pending() {
		return QUEUE.size() + READY.size() + waiting + held;
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
		if (!WAITING.isEmpty() && ++calls % 10 == 0) {
			for (var it = WAITING.entrySet().iterator(); it.hasNext();) {
				var e = it.next();
				long key = e.getKey();
				if (level.hasChunk((int) (key >> 32), (int) key)) {
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
			run(level, j);
		}
		if (had && pending() == 0) {
			com.extremedimensions.ExtremeDimensions.LOGGER.info("Seam architect: done, {} blocks built so far", built);
		}
	}

	/** Does a job, or parks it: in its chunk's waiting list, or (an action) until its plan's blocks all stand. */
	private static void run(ServerLevel level, Job j) {
		BlockPos pos = BlockPos.of(j.pos());
		if (!level.isLoaded(pos)) {
			WAITING.computeIfAbsent(chunkOf(j.pos()), k -> new ArrayDeque<>()).add(j);
			waiting++;
			return;
		}
		if (j.action() != null) {
			if (UNBUILT.getOrDefault(j.plan(), 0) > 0) {
				HELD.computeIfAbsent(j.plan(), k -> new ArrayList<>()).add(j);
				held++;
			} else {
				j.action().accept(level);
			}
			return;
		}
		level.setBlock(pos, j.state(), 2);
		built++;
		if (UNBUILT.merge(j.plan(), -1, Integer::sum) <= 0) {
			UNBUILT.remove(j.plan());
			List<Job> actions = HELD.remove(j.plan());
			if (actions != null) {
				READY.addAll(actions);
				held -= actions.size();
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
		List<Meta> dropped = new ArrayList<>();
		for (int id : far) {
			UNBUILT.remove(id);
			List<Job> actions = HELD.remove(id);
			if (actions != null) {
				held -= actions.size();
			}
			Meta m = PLANS.remove(id);
			if (m != null) {
				dropped.add(m);
			}
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

		/** The rim's distance from the centre at an angle; big islands wobble less, so what is built on them fits. */
		public double edge(double ang) {
			double w = r >= 20 ? 0.55 : 1.0;
			return r * (0.92 + w * (0.07 * Math.sin(ang * 3 + p1) + 0.045 * Math.sin(ang * 5 + p2) + 0.02 * Math.sin(ang * 11 + p3)));
		}

		public boolean inside(int px, int pz, double margin) {
			double dx = px - x, dz = pz - z;
			return Math.sqrt(dx * dx + dz * dz) < edge(Math.atan2(dz, dx)) - margin;
		}
	}

	/**
	 * A floating island: turf of moss and pale moss with grass, flowers, firefly bushes and eyeblossoms over a deep,
	 * tapering underside whose rock darkens with depth and is threaded with silk, hung with vines, roots, chains and
	 * soul lanterns, with long fangs of stone tipped with amethyst, and light pouring off its edges.
	 *
	 * @return the depth of the underside at the centre
	 */
	public static int island(Plan p, Random rnd, Shape s, boolean mossy) {
		int cx = s.x, top = s.top, cz = s.z, r = s.r;
		int span = r + 3;
		int[][] depth = new int[span * 2 + 1][span * 2 + 1];
		long seed = rnd.nextLong();
		BlockState grass = mc("short_grass"), fern = mc("fern"), allium = mc("allium"), bluet = mc("azure_bluet");
		BlockState firefly = mc("firefly_bush"), eye = mc("open_eyeblossom"), paleMoss = mc("pale_moss_block", moss());
		BlockState vines = mc("cave_vines"), pale = mc("pale_hanging_moss"), roots = mc("hanging_roots");
		BlockState[] rock = rockGrad();
		// deep under small islands, relatively shallower under the great ones (still thirty blocks of rock)
		double deep = r <= 16 ? r * 1.15 : 18.4 + (r - 16) * 0.65;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double ang = Math.atan2(dz, dx);
				double edge = s.edge(ang);
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > edge) {
					continue;
				}
				double f = 1 - dist / edge;
				int d = 2 + (int) Math.round(Math.pow(f, 0.62) * deep) + rnd.nextInt(2);
				depth[dx + span][dz + span] = d;
				int x = cx + dx, z = cz + dz;
				double patch = noise(seed, x / 7.0, z / 7.0) * 0.7 + noise(seed + 1, x / 3.0, z / 3.0) * 0.3;
				for (int y = top - d; y <= top; y++) {
					BlockState b;
					if (y == top) {
						if (!mossy) {
							b = rnd.nextInt(6) == 0 ? brick() : stone();
						} else if (patch < 0.2 || f < 0.06) {
							b = rnd.nextInt(3) == 0 ? mc("cobbled_deepslate", stone()) : stone();
						} else if (patch < 0.38) {
							b = paleMoss;
						} else {
							b = moss();
						}
					} else if (y > top - 3) {
						b = stone();
					} else {
						double k = (double) (top - y) / Math.max(1, d);
						b = rnd.nextDouble() < 0.015 + 0.05 * k ? vein() : grad(rock, k, rnd);
					}
					p.set(x, y, z, b);
				}
				// Growth on the top
				if (mossy && f > 0.1 && patch >= 0.2) {
					int k = rnd.nextInt(1000);
					BlockState g = k < 150 ? grass : k < 190 ? fern : k < 210 ? allium : k < 220 ? bluet
							: k < 232 ? firefly : k < 240 ? eye : null;
					p.set(x, top + 1, z, g);
				}
				// Growth hanging from the underside near the rim
				if (f < 0.4 && rnd.nextInt(6) == 0) {
					int len = 2 + rnd.nextInt(7);
					int kind = rnd.nextInt(3);
					for (int k = 1; k <= len; k++) {
						BlockState h = kind == 0 && vines != null
								? (rnd.nextInt(3) == 0 && vines.hasProperty(BlockStateProperties.BERRIES) ? vines.setValue(BlockStateProperties.BERRIES, true) : vines)
								: kind == 1 ? pale : roots;
						p.set(x, top - d - k, z, h);
						if (kind == 2) {
							break;
						}
					}
				}
			}
		}
		// Fangs of stone hanging far below, darkening and ending in amethyst
		int fangs = 2 + r / 5;
		for (int i = 0; i < fangs; i++) {
			int fx = (int) ((rnd.nextDouble() - 0.5) * r), fz = (int) ((rnd.nextDouble() - 0.5) * r);
			int base = depth[fx + span][fz + span];
			if (base == 0) {
				continue;
			}
			int len = 6 + rnd.nextInt(Math.max(2, Math.min(r, 26)));
			double rad = 1.5 + rnd.nextDouble() * 2.0;
			for (int k = 0; k < len; k++) {
				double rr = rad * (1 - (double) k / len);
				int ir = (int) Math.ceil(rr);
				double t = (double) k / len;
				for (int ax = -ir; ax <= ir; ax++) {
					for (int az = -ir; az <= ir; az++) {
						if (ax * ax + az * az <= rr * rr + 0.3) {
							BlockState b = t > 0.82 ? (rnd.nextInt(3) == 0 ? crying() : amethyst()) : grad(rock, 0.55 + t * 0.45, rnd);
							p.set(cx + fx + ax, top - base - k, cz + fz + az, b);
						}
					}
				}
			}
		}
		// Soul lanterns on long chains beneath
		for (int i = 0; i < 2 + rnd.nextInt(3); i++) {
			int hx = (int) ((rnd.nextDouble() - 0.5) * r * 1.2), hz = (int) ((rnd.nextDouble() - 0.5) * r * 1.2);
			int base = depth[hx + span][hz + span];
			if (base == 0) {
				continue;
			}
			int len = 2 + rnd.nextInt(7);
			for (int k = 1; k <= len; k++) {
				p.soft(cx + hx, top - base - k, cz + hz, chain());
			}
			p.soft(cx + hx, top - base - len - 1, cz + hz, hanging(soulLantern()));
		}
		// Light pouring off the edges
		for (int i = 0; i < 1 + r / 14 + rnd.nextInt(2); i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int ex = cx + (int) Math.round(Math.cos(a) * r * 0.8), ez = cz + (int) Math.round(Math.sin(a) * r * 0.8);
			p.falls.add(new int[] {ex, top, ez, 18 + rnd.nextInt(30)});
		}
		return depth[span][span];
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
			p.set(x, y + h + 1, z, dsChis());
			p.set(x + 1, y + h + 1, z, slab(dsSlab(), false));
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
				f.set(lx, h + rise - 1, lz, lx == 0 ? dsChis() : stairs(dsBrickStairs(), lx < 0 ? Direction.WEST : Direction.EAST, true));
			}
		}
		int apex = h + (int) Math.round(half * 0.9);
		f.set(0, apex + 1, 0, dsWall());
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

	// ------------------------------------------------------------------ the great spire

	/**
	 * A colossal gothic spire: tiers of masonry stepping inward from blackstone to tuff, pilastered faces with recessed
	 * stained-glass windows under arched heads, sills and corbelled ledges, diamond rose windows, stepped buttresses
	 * crowned with pinnacles, a stair roof with dormers and a crowned needle, a glowing core with a spiral stair up to
	 * a reliquary; in front a nave with a layered portal, and three satellite towers joined by sky bridges.
	 */
	public static void spire(Plan p, Random rnd, int cx, int top, int cz, int r) {
		Frame base = new Frame(p, cx, top + 1, cz, rnd.nextInt(4));
		int b0 = Math.max(9, Math.min(12, r / 2 - 1));
		int b = b0;
		int tiers = 3 + rnd.nextInt(2);
		int[] heights = {24 + rnd.nextInt(8), 18 + rnd.nextInt(6), 14 + rnd.nextInt(5), 11 + rnd.nextInt(4)};
		int total = 0;
		for (int t = 0; t < tiers; t++) {
			total += heights[t] + 1;
		}
		total += 20;
		BlockState vines = mc("cave_vines");
		int y = 0;
		int firstH = heights[0];
		int lastFloor = 0;
		for (int t = 0; t < tiers && b >= 5; t++) {
			int h = heights[t];
			Frame f = new Frame(p, cx, top + 1 + y, cz, base.rot);
			final int yy = y;
			// Floor (a ring once off the ground: the stair well stays open), reaching out over the terrace below
			int fl = t == 0 ? b : b + 2;
			for (int lx = -fl; lx <= fl; lx++) {
				for (int lz = -fl; lz <= fl; lz++) {
					if (t == 0 || lx * lx + lz * lz > 20) {
						f.set(lx, 0, lz, t == 0 && (lx + lz & 1) == 0 ? bsBrick() : dsPol());
					}
				}
			}
			lastFloor = y;
			int wTop = h - 4;
			for (int side = 0; side < 4; side++) {
				boolean front = side == 0;
				int[] centers = front && t == 0 ? new int[] {0} : (b >= 10 ? new int[] {0, 5, -5} : new int[] {0, 4, -4});
				Direction out = Face.out(side), along = Face.along(side);
				for (int ly = 1; ly <= h; ly++) {
					double tt = (double) (yy + ly) / total;
					for (int u = -b + 2; u <= b - 2; u++) {
						int lx = Face.lx(side, u, b), lz = Face.lz(side, u, b);
						if (t == 0 && front && Math.abs(u) <= 1 && ly <= (u == 0 ? 6 : 5)) {
							f.clear(lx, ly, lz); // the door
							continue;
						}
						if (t > 0 && u == 0 && ly <= 2) {
							f.clear(lx, ly, lz); // out onto the terrace
							continue;
						}
						int win = Integer.MIN_VALUE;
						for (int k : centers) {
							int du = Math.abs(u - k);
							if (Math.abs(k) <= b - 3 && du <= 1 && ly >= 3 && ly <= (du == 0 ? wTop : wTop - 1)) {
								win = k;
							}
						}
						if (win != Integer.MIN_VALUE) {
							// Recessed: the opening in the wall, the glass a block behind it
							f.clear(lx, ly, lz);
							f.set(Face.lx(side, u, b - 1), ly, Face.lz(side, u, b - 1), ly % 6 == 0 ? vein() : stainedGlass(u, ly, wTop));
						} else {
							f.set(lx, ly, lz, ly == 1 || ly == h ? bsBrick() : wall(tt, rnd));
						}
					}
				}
				// Window heads and sills
				for (int k : centers) {
					if (Math.abs(k) > b - 3) {
						continue;
					}
					f.set(Face.lx(side, k - 1, b), wTop, Face.lz(side, k - 1, b), stairs(dsBrickStairs(), along.getOpposite(), true));
					f.set(Face.lx(side, k + 1, b), wTop, Face.lz(side, k + 1, b), stairs(dsBrickStairs(), along, true));
					f.set(Face.lx(side, k, b), wTop + 1, Face.lz(side, k, b), dsChis());
					if (!(t == 0 && front)) {
						for (int du = -1; du <= 1; du++) {
							f.set(Face.lx(side, k + du, b + 1), 2, Face.lz(side, k + du, b + 1), stairs(dsBrickStairs(), out.getOpposite(), true));
						}
					}
				}
				// Pilasters between the windows: a stepped base, a smooth shaft, a carved capital
				int[] pil = b >= 10 ? new int[] {-3, 3, -8, 8} : new int[] {-2, 2, -6, 6};
				for (int u : pil) {
					if (Math.abs(u) > b - 2 || (t == 0 && front && Math.abs(u) <= 4)) {
						continue;
					}
					for (int ly = 1; ly < h; ly++) {
						double tt = (double) (yy + ly) / total;
						BlockState s = ly == 1 ? bsBrick() : ly == h - 1 ? dsChis() : grad(trimGrad(), tt, rnd);
						f.set(Face.lx(side, u, b + 1), ly, Face.lz(side, u, b + 1), s);
					}
					f.set(Face.lx(side, u, b + 2), 1, Face.lz(side, u, b + 2), stairs(bsStairs(), out.getOpposite(), false));
				}
				// Corbels under the ledge, a water table at the foot, glowing lichen low on the stone
				for (int u = -b + 2; u <= b - 2; u++) {
					int clx = Face.lx(side, u, b + 1), clz = Face.lz(side, u, b + 1);
					if ((u & 1) == 0 && !f.has(clx, h, clz)) {
						f.set(clx, h, clz, stairs(dsBrickStairs(), out.getOpposite(), true));
					}
					if (t == 0 && !(front && Math.abs(u) <= 4)) {
						f.soft(clx, 1, clz, bsBrick());
						f.soft(clx, 2, clz, stairs(bsStairs(), out.getOpposite(), false));
					}
					if (t == 0 && rnd.nextInt(9) == 0) {
						f.soft(clx, 3 + rnd.nextInt(5), clz, lichen(out.getOpposite()));
					}
				}
			}
			// The diamond rose window above the door, front and back, recessed behind a ring of silk
			if (h >= 13) {
				int c = h - 5;
				for (int u = -3; u <= 3; u++) {
					for (int dy = -3; dy <= 3; dy++) {
						int m = Math.abs(u) + Math.abs(dy);
						if (m > 3) {
							continue;
						}
						for (int side : new int[] {0, 2}) {
							if (m == 3) {
								f.set(Face.lx(side, u, b), c + dy, Face.lz(side, u, b), vein());
							} else {
								f.clear(Face.lx(side, u, b), c + dy, Face.lz(side, u, b));
								f.set(Face.lx(side, u, b - 1), c + dy, Face.lz(side, u, b - 1), m == 0 ? crying() : (m == 1 ? glass3() : glass2()));
							}
						}
					}
				}
			}
			// Corner buttresses, stepping back as they rise, crowned with pinnacles
			int low = (int) (h * 0.45);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					for (int ly = 0; ly <= h + 3; ly++) {
						double tt = (double) (yy + ly) / total;
						for (int ax = -1; ax <= 1; ax++) {
							for (int az = -1; az <= 1; az++) {
								f.set(sx * b + ax, ly, sz * b + az, ly % 7 == 6 ? dsTile() : grad(trimGrad(), tt, rnd));
							}
						}
						if (ly <= low) {
							f.set(sx * (b + 2), ly, sz * b, wall(tt, rnd));
							f.set(sx * b, ly, sz * (b + 2), wall(tt, rnd));
							f.set(sx * (b + 2), ly, sz * (b + 1), wall(tt, rnd));
							f.set(sx * (b + 1), ly, sz * (b + 2), wall(tt, rnd));
						}
					}
					f.set(sx * (b + 2), low + 1, sz * b, stairs(dsBrickStairs(), sx > 0 ? Direction.WEST : Direction.EAST, false));
					f.set(sx * (b + 2), low + 1, sz * (b + 1), stairs(dsBrickStairs(), sx > 0 ? Direction.WEST : Direction.EAST, false));
					f.set(sx * b, low + 1, sz * (b + 2), stairs(dsBrickStairs(), sz > 0 ? Direction.NORTH : Direction.SOUTH, false));
					f.set(sx * (b + 1), low + 1, sz * (b + 2), stairs(dsBrickStairs(), sz > 0 ? Direction.NORTH : Direction.SOUTH, false));
					int px = sx * b, pz = sz * b;
					f.set(px, h + 4, pz, dsChis());
					f.set(px, h + 5, pz, dsWall());
					f.set(px, h + 6, pz, dsWall());
					f.set(px, h + 7, pz, dsWall());
					f.set(px, h + 8, pz, endRod());
					for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
						f.set(px + d.getStepX(), h + 5, pz + d.getStepZ(), stairs(dsBrickStairs(), d.getOpposite(), false));
					}
					f.set(px + sx * 2, h + 2, pz, slab(dsSlab(), false));
					f.set(px + sx * 2, h + 1, pz, chain());
					f.set(px + sx * 2, h, pz, hanging(soulLantern()));
				}
			}
			// The ledge: an overhang with chains, lanterns and vines hanging from it
			int e = b + 1;
			for (int u = -e; u <= e; u++) {
				int[][] ring = {{u, e}, {u, -e}, {e, u}, {-e, u}};
				for (int[] q : ring) {
					f.soft(q[0], h + 1, q[1], slab(dsSlab(), false));
					if ((u & 3) == 2 && rnd.nextInt(3) > 0) {
						int len = 1 + rnd.nextInt(3);
						for (int k = 1; k <= len; k++) {
							f.soft(q[0], h + 1 - k, q[1], chain());
						}
						f.soft(q[0], h - len, q[1], hanging(rnd.nextInt(3) == 0 ? soulLantern() : lantern()));
					} else if (vines != null && rnd.nextInt(6) == 0) {
						int len = 2 + rnd.nextInt(8);
						for (int k = 0; k < len; k++) {
							f.soft(q[0], h - k, q[1], vines);
						}
					}
				}
			}
			// Warm light inside so the windows glow
			for (int[] q : new int[][] {{b - 1, b - 2}, {-b + 1, b - 2}, {b - 1, -b + 2}, {-b + 1, -b + 2}}) {
				f.set(q[0], 1, q[1], lantern());
			}
			// A parapet around the terrace above
			for (int u = -b; u <= b; u++) {
				f.soft(u, h + 2, b, dsWall());
				f.soft(u, h + 2, -b, dsWall());
				f.soft(b, h + 2, u, dsWall());
				f.soft(-b, h + 2, u, dsWall());
			}
			y += h + 1;
			b -= 2;
		}
		roof(base, rnd, y, b + 2);
		// The core: a column of silk inside violet glass, from the ground to the roof
		for (int ly = 1; ly < y; ly++) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					base.set(ax, ly, az, ax == 0 && az == 0 ? (ly % 6 == 0 ? crying() : vein()) : glass());
				}
			}
		}
		// The spiral stair around it
		for (int ly = 1; ly < y; ly++) {
			double a = ly * Math.PI / 8;
			for (double rr = 2.2; rr <= 4.4; rr += 0.7) {
				for (double da = 0; da < 0.4; da += 0.2) {
					int sx = (int) Math.round(Math.cos(a + da) * rr), sz = (int) Math.round(Math.sin(a + da) * rr);
					if (Math.abs(sx) <= 1 && Math.abs(sz) <= 1) {
						continue;
					}
					base.set(sx, ly, sz, dsPol());
					for (int hh = 1; hh <= 3; hh++) {
						BlockPos above = base.pos(sx, ly + hh, sz);
						if (Math.abs(sx) < 5 && Math.abs(sz) < 5) {
							p.clear(above.getX(), above.getY(), above.getZ());
						}
					}
				}
			}
		}
		int bl = b + 2;
		base.then(-bl + 1, lastFloor + 1, -bl + 3, loot(base.pos(-bl + 1, lastFloor + 1, -bl + 3), 3));
		nave(base, rnd, b0);
		// The parvis: a paved forecourt before the portal, narrowing as it runs out toward the street
		BlockState[] path = pathGrad();
		int z1 = b0 + 10;
		for (int lz = z1 + 2; lz <= z1 + 12; lz++) {
			int hw = lz <= z1 + 7 ? 8 : 8 - (lz - z1 - 7) * 2;
			for (int lx = -hw; lx <= hw; lx++) {
				double t = Math.min(1, (Math.abs(lx) / 9.0 + (lz - z1 - 2) / 12.0) / 1.4);
				pave(p, base.wx(lx, lz), top, base.wz(lx, lz), weather(grad(path, t, rnd), rnd, 0.1));
			}
		}
		int terrace = firstH + 1;
		for (int side = 1; side < 4; side++) {
			Frame g = new Frame(p, cx, top + 1, cz, base.rot + side);
			satellite(g, rnd, b0, b0 + 9, terrace + 8 + rnd.nextInt(10), terrace);
		}
	}

	/** A stair roof rising to a needle, with dormers on every side, crockets on the hips and a floating crown. */
	private static void roof(Frame f, Random rnd, int y, int half0) {
		int s = 14 + rnd.nextInt(10);
		int dormerK = s / 3;
		for (int k = 0; k <= s; k++) {
			int half = (int) Math.round(half0 * (1 - (double) k / s));
			if (half <= 0) {
				break;
			}
			for (int side = 0; side < 4; side++) {
				Direction in = Face.out(side).getOpposite();
				for (int u = -half + 1; u <= half - 1; u++) {
					f.set(Face.lx(side, u, half), y + k, Face.lz(side, u, half), stairs(roofStairs(rnd), in, false));
					f.set(Face.lx(side, u, half - 1), y + k, Face.lz(side, u, half - 1), dsTile());
				}
				// the hip, with a crocket every third course
				int hx = Face.lx(side, half, half), hz = Face.lz(side, half, half);
				f.set(hx, y + k, hz, bsPol());
				if (k % 3 == 1) {
					f.set(Face.lx(side, half + 1, half + 1), y + k, Face.lz(side, half + 1, half + 1), dsWall());
				}
			}
		}
		// Dormers: a small gabled window standing out of each face
		int dh = (int) Math.round(half0 * (1 - (double) dormerK / s));
		for (int side = 0; side < 4; side++) {
			Direction along = Face.along(side);
			int pl = dh + 1;
			for (int ly = 0; ly <= 2; ly++) {
				f.set(Face.lx(side, -1, pl), y + dormerK + ly, Face.lz(side, -1, pl), dsBrick());
				f.set(Face.lx(side, 1, pl), y + dormerK + ly, Face.lz(side, 1, pl), dsBrick());
				f.set(Face.lx(side, 0, pl), y + dormerK + ly, Face.lz(side, 0, pl), ly == 2 ? dsChis() : glass());
				f.clear(Face.lx(side, 0, pl - 1), y + dormerK + ly, Face.lz(side, 0, pl - 1));
			}
			for (int d = 0; d <= 1; d++) {
				f.set(Face.lx(side, -1, pl - d), y + dormerK + 3, Face.lz(side, -1, pl - d), stairs(dsStairs(), along, false));
				f.set(Face.lx(side, 1, pl - d), y + dormerK + 3, Face.lz(side, 1, pl - d), stairs(dsStairs(), along.getOpposite(), false));
				f.set(Face.lx(side, 0, pl - d), y + dormerK + 3, Face.lz(side, 0, pl - d), dsTile());
				f.set(Face.lx(side, 0, pl - d), y + dormerK + 4, Face.lz(side, 0, pl - d), slab(dsSlab(), false));
			}
		}
		// The needle and its floating crown
		int tip = y + s;
		for (int k = 0; k < 6; k++) {
			f.set(0, tip + k, 0, k < 2 ? dsChis() : dsWall());
		}
		f.set(0, tip + 6, 0, vein());
		f.set(0, tip + 7, 0, endRod());
		for (int a = 0; a < 16; a++) {
			double an = a * Math.PI / 8;
			f.set((int) Math.round(Math.cos(an) * 3), tip + 4, (int) Math.round(Math.sin(an) * 3), a % 4 == 0 ? vein() : bsPol());
		}
	}

	/** The nave in front of a spire: buttressed walls, recessed windows, a steep stair roof and a layered portal. */
	private static void nave(Frame f, Random rnd, int b0) {
		int z0 = b0 + 1, z1 = b0 + 10, w = 4, h = 13;
		for (int lz = z0; lz <= z1; lz++) {
			boolean bay = (lz - z0) % 3 == 1;
			for (int lx = -w; lx <= w; lx++) {
				f.set(lx, 0, lz, (lx + lz & 1) == 0 ? bsBrick() : dsPol());
				boolean sideWall = Math.abs(lx) == w;
				boolean frontWall = lz == z1;
				for (int ly = 1; ly <= h; ly++) {
					double t = ly / 40.0;
					if (!sideWall && !frontWall) {
						f.clear(lx, ly, lz);
						continue;
					}
					boolean window = sideWall && bay && ly >= 3 && ly <= 9;
					boolean door = frontWall && Math.abs(lx) <= 1 && ly <= (lx == 0 ? 6 : 5);
					if (door) {
						f.clear(lx, ly, lz);
					} else if (window) {
						f.clear(lx, ly, lz);
						f.set(lx - Integer.signum(lx), ly, lz, ly == 9 ? vein() : stainedGlass(lz, ly, 9));
					} else {
						f.set(lx, ly, lz, ly == 1 || ly == h ? bsBrick() : wall(t, rnd));
					}
				}
				// side buttresses between the bays, with a sloped cap
				if (sideWall && (lz - z0) % 3 == 0) {
					int sx = Integer.signum(lx);
					for (int ly = 1; ly <= 9; ly++) {
						f.set(lx + sx, ly, lz, grad(trimGrad(), ly / 40.0, rnd));
					}
					f.set(lx + sx, 10, lz, stairs(bsStairs(), sx > 0 ? Direction.WEST : Direction.EAST, false));
					f.set(lx + sx * 2, 1, lz, stairs(bsStairs(), sx > 0 ? Direction.WEST : Direction.EAST, false));
				}
				// window heads
				if (sideWall && bay) {
					f.set(lx, 10, lz, dsChis());
				}
			}
			// Steep stair roof with a tile ridge
			for (int k = 0; k <= w + 1; k++) {
				int half = w + 1 - k;
				f.set(half, h + 1 + k, lz, stairs(roofStairs(rnd), Direction.WEST, false));
				f.set(-half, h + 1 + k, lz, stairs(roofStairs(rnd), Direction.EAST, false));
				if (half == 0) {
					f.set(0, h + 1 + k, lz, dsTile());
				}
			}
			f.set(w + 1, h, lz, stairs(dsBrickStairs(), Direction.WEST, true));
			f.set(-w - 1, h, lz, stairs(dsBrickStairs(), Direction.EAST, true));
			if ((lz - z0) % 3 == 2) {
				f.set(0, h, lz, chain());
				f.set(0, h - 1, lz, chain());
				f.set(0, h - 2, lz, hanging(lantern()));
			}
		}
		// The gable front filled in, a small rose in it, a pinnacle on top
		for (int k = 0; k <= w; k++) {
			int half = w - k;
			for (int lx = -half; lx <= half; lx++) {
				f.set(lx, h + 1 + k, z1, Math.abs(lx) + Math.abs(k - 2) <= 1 ? (lx == 0 && k == 2 ? crying() : glass2()) : wall(0.45, rnd));
			}
		}
		f.set(0, h + w + 3, z1, dsWall());
		f.set(0, h + w + 4, z1, dsWall());
		f.set(0, h + w + 5, z1, endRod());
		// The portal: three receding orders of arches, each a block deeper and a block smaller
		for (int order = 1; order <= 3; order++) {
			int lz = z1 + order;
			int hw = 1 + order, ah = 5 + order;
			for (int ly = 0; ly <= ah + 1; ly++) {
				for (int lx = -hw - 1; lx <= hw + 1; lx++) {
					boolean jamb = Math.abs(lx) == hw + 1 && ly <= ah;
					boolean head = ly == ah + 1 && Math.abs(lx) <= hw + 1;
					if (order < 3 && !jamb && !head) {
						continue;
					}
					if (order == 3 && !jamb && !head) {
						continue;
					}
					f.set(lx, ly, lz, ly == 0 ? bsBrick() : jamb ? grad(trimGrad(), ly / 30.0, rnd) : wall(0.35, rnd));
				}
				// arch corners curved with stairs
				f.set(-hw, ah, lz, stairs(dsBrickStairs(), Direction.WEST, true));
				f.set(hw, ah, lz, stairs(dsBrickStairs(), Direction.EAST, true));
			}
			f.set(0, ah + 1, lz, dsChis());
			f.set(-hw - 1, ah + 2, lz, slab(dsSlab(), false));
			f.set(hw + 1, ah + 2, lz, slab(dsSlab(), false));
		}
		// Banners either side of the portal
		for (int side = -1; side <= 1; side += 2) {
			int uc = side * 6;
			for (int k = 0; k < 10; k++) {
				for (int du = -1; du <= 1; du++) {
					if (k == 9 && du != 0) {
						continue;
					}
					int m = Math.abs(du) + Math.abs(k - 4);
					BlockState cl = k == 0 ? woolDark() : (m == 1 ? wool2() : m == 0 ? vein() : wool());
					f.set(uc + du, 12 - k, z1 + 1, cl);
				}
			}
		}
		// Steps and lamps before the door
		for (int lx = -3; lx <= 3; lx++) {
			f.set(lx, 0, z1 + 4, stairs(bsStairs(), Direction.NORTH, false));
		}
		lamp(f.p, rnd, f.wx(-6, z1 + 5), f.oy, f.wz(-6, z1 + 5));
		lamp(f.p, rnd, f.wx(6, z1 + 5), f.oy, f.wz(6, z1 + 5));
	}

	/** A satellite tower at local (0, d) with pilastered corners, a cone of stairs and a sky bridge back to the spire. */
	private static void satellite(Frame f, Random rnd, int b0, int d, int h, int bridgeY) {
		int w = 2;
		for (int ly = 0; ly <= h; ly++) {
			double t = ly / (double) (h + 14);
			for (int lx = -w - 1; lx <= w + 1; lx++) {
				for (int lz = d - w - 1; lz <= d + w + 1; lz++) {
					boolean outer = Math.abs(lx) == w + 1 || Math.abs(lz - d) == w + 1;
					boolean edgeX = Math.abs(lx) == w, edgeZ = Math.abs(lz - d) == w;
					if (outer) {
						// pilasters at the corners only
						if (Math.abs(lx) == w + 1 && Math.abs(lz - d) == w + 1) {
							continue;
						}
						if ((Math.abs(lx) == w + 1 && Math.abs(lz - d) == w) || (Math.abs(lz - d) == w + 1 && Math.abs(lx) == w)) {
							f.set(lx, ly, lz, ly % 8 == 0 ? dsChis() : grad(trimGrad(), t, rnd));
						}
						continue;
					}
					if (ly == 0 || ly == bridgeY) {
						f.set(lx, ly, lz, dsPol());
					} else if (edgeX || edgeZ) {
						boolean window = !(edgeX && edgeZ) && (lx == 0 || lz == d) && ly % 7 >= 3 && ly % 7 <= 5;
						if (window) {
							f.clear(lx, ly, lz);
							int ix = edgeX ? lx - Integer.signum(lx) : lx, iz = edgeZ ? lz - Integer.signum(lz - d) : lz;
							f.set(ix, ly, iz, stainedGlass(lx + lz, ly % 7, 5));
						} else {
							f.set(lx, ly, lz, ly % 7 == 0 ? bsBrick() : wall(t, rnd));
						}
					} else if (ly != bridgeY) {
						f.clear(lx, ly, lz);
					}
				}
			}
		}
		// A cone of stairs with a needle
		for (int k = 0; k <= 9; k++) {
			int half = (int) Math.round((w + 2) * (1 - k / 9.0));
			if (half <= 0) {
				break;
			}
			for (int u = -half; u <= half; u++) {
				f.set(u, h + 1 + k, d + half, stairs(roofStairs(rnd), Direction.NORTH, false));
				f.set(u, h + 1 + k, d - half, stairs(roofStairs(rnd), Direction.SOUTH, false));
				f.set(half, h + 1 + k, d + u, stairs(roofStairs(rnd), Direction.WEST, false));
				f.set(-half, h + 1 + k, d + u, stairs(roofStairs(rnd), Direction.EAST, false));
			}
			for (int s = -1; s <= 1; s += 2) {
				for (int s2 = -1; s2 <= 1; s2 += 2) {
					f.set(s * half, h + 1 + k, d + s2 * half, bsPol());
				}
			}
		}
		f.set(0, h + 10, d, dsWall());
		f.set(0, h + 11, d, dsWall());
		f.set(0, h + 12, d, endRod());
		f.set(0, bridgeY + 3, d, hanging(soulLantern()));
		// The sky bridge from the terrace's edge, through a gap in its parapet, to a doorway in the tower
		for (int lx = -1; lx <= 1; lx++) {
			f.clear(lx, bridgeY + 1, b0);
		}
		for (int lz = b0 + 1; lz < d - w; lz++) {
			for (int lx = -1; lx <= 1; lx++) {
				f.soft(lx, bridgeY, lz, lx == 0 ? planks() : dsTile());
			}
			f.soft(-2, bridgeY, lz, bsBrick());
			f.soft(2, bridgeY, lz, bsBrick());
			f.soft(-2, bridgeY + 1, lz, dsWall());
			f.soft(2, bridgeY + 1, lz, dsWall());
			f.soft(-2, bridgeY - 1, lz, stairs(dsBrickStairs(), Direction.EAST, true));
			f.soft(2, bridgeY - 1, lz, stairs(dsBrickStairs(), Direction.WEST, true));
			if (lz % 4 == 2) {
				f.soft(0, bridgeY - 1, lz, chain());
				f.soft(0, bridgeY - 2, lz, hanging(lantern()));
			}
		}
		for (int lx = -1; lx <= 1; lx++) {
			for (int ly = 1; ly <= 3; ly++) {
				f.clear(lx, bridgeY + ly, d - w);
			}
		}
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
						f.set(side * sx + ax, ly, az, core && ly > 2 ? vein() : ly % 8 == 7 ? dsChis() : wall(t, rnd));
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
			f.set(side * sx, h + 5, 0, dsChis());
			f.set(side * sx, h + 6, 0, dsWall());
			f.set(side * sx, h + 7, 0, dsWall());
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
			f.set(side * (sx - 2), h - 1, 0, stairs(dsBrickStairs(), side > 0 ? Direction.EAST : Direction.WEST, true));
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
		for (int i = 0; i < 10; i++) {
			f.soft(rnd.nextInt(-r / 2, r / 2 + 1), 0, rnd.nextInt(-r / 2, r / 2 + 1), Blocks.COBWEB.defaultBlockState());
		}
		f.then(sx - 3, 0, -3, loot(f.pos(sx - 3, 0, -3), 1));
	}

	// ------------------------------------------------------------------ villages

	/**
	 * A village of the Thinned laid out the way villages grow: a paved plaza with a bell spire at the heart, streets
	 * leaving it along the axes to the bridges, and in the four quarters between them jettied timber houses facing the
	 * plaza, each with a path from its door, and a walled graveyard in one quarter.
	 */
	public static void village(Plan p, Random rnd, int cx, int top, int cz, int r) {
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
				house(p, rnd, hx, top + 1, hz, rot, lore);
				lore = false;
				// A path from the door step to the plaza
				Frame f = new Frame(p, hx, top + 1, hz, rot);
				for (int lz = 5; lz <= 14; lz++) {
					int x = f.wx(0, lz), z = f.wz(0, lz);
					if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= plaza * plaza) {
						break;
					}
					pave(p, x, top, z, rnd.nextInt(4) == 0 ? mc("cobbled_deepslate", stone()) : mc("tuff_bricks", dsTile()));
				}
			}
		}
		// Plaza: dressed tuff at the heart, darkening to the rim
		BlockState[] paving = {mc("polished_tuff", dsPol()), mc("tuff_bricks", dsTile()), dsTile(), bsBrick(), mc("cobbled_deepslate", stone())};
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
					p.set(cx + ax, top + ly, cz + az, ly % 5 == 0 ? dsChis() : wall(t, rnd));
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
			p.set(cx + d.getStepX(), top + h - 1, cz + d.getStepZ(), stairs(dsBrickStairs(), d.getOpposite(), true));
		}
		for (int k = 1; k <= 7; k++) {
			int half = k < 3 ? 1 : 0;
			for (int ax = -half; ax <= half; ax++) {
				for (int az = -half; az <= half; az++) {
					p.set(cx + ax, top + h + k, cz + az, k == 7 ? endRod() : half == 1 && (ax != 0 || az != 0) && k == 1
							? stairs(roofStairs(rnd), ax > 0 ? Direction.WEST : ax < 0 ? Direction.EAST : az > 0 ? Direction.NORTH : Direction.SOUTH, false)
							: k > 3 ? dsWall() : dsTile());
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
						p.set(x, top + 1, z, Math.abs(dx) == half && Math.abs(dz) == half ? bsBrick() : dsWall());
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
				f.set(lx, -1, lz, dsPol());
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
			f.set(s, 0, d + 1, stairs(dsBrickStairs(), Direction.NORTH, true));
			f.set(s, 0, -d - 1, stairs(dsBrickStairs(), Direction.SOUTH, true));
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
					f.set(0, ry + k, lz, dsTile());
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
			f.set(chx, ly, chz, ly > ry ? mc("bricks", dsBrick()) : grad(wallGrad(), ly / 20.0, rnd));
		}
		f.set(chx, ry + rw + 2, chz, mc("campfire", lantern()));
		// Door, with a lantern on a bracket beside it
		BlockState door = mc("dark_oak_door");
		if (door != null && door.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			f.set(0, 0, d, door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			f.set(0, 1, d, door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}
		f.set(0, -1, d + 1, dsPol());
		f.set(1, 2, d + 1, stairs(dsBrickStairs(), Direction.NORTH, true));
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
						p.set(cx + lx, y, cz + lz, dsPol());
					}
				}
				p.set(cx, y - 1, cz, hanging(lantern()));
				// a corbelled ring around the floor
				for (int side = 0; side < 4; side++) {
					Direction in = Face.out(side).getOpposite();
					for (int u = -b + 1; u <= b - 1; u += 2) {
						p.set(cx + Face.lx(side, u, b + 1), y, cz + Face.lz(side, u, b + 1), stairs(dsBrickStairs(), in, false));
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
		grounds(p, rnd, s, 1 + s.r / 6 + rnd.nextInt(2), ARCH, ARCH, TREE, LAMP, STONES, CLUSTER, FLOATING);
	}

	static final int ARCH = 0, TREE = 1, LAMP = 2, STONES = 3, CLUSTER = 4, FLOATING = 5;

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
			int room = switch (kind) { case ARCH -> 6; case STONES -> 5; case CLUSTER -> 4; case TREE -> 3; default -> 2; };
			int tall = switch (kind) { case ARCH -> 14; case TREE -> 11; case CLUSTER -> scale + 2; case FLOATING -> 30; default -> 6; };
			if (!free(p, x, s.top, z, room, tall)) {
				continue;
			}
			int y = s.top + 1;
			switch (kind) {
				case ARCH -> arch(p, rnd, x, y, z);
				case TREE -> deadTree(p, rnd, x, y, z);
				case LAMP -> lamp(p, rnd, x, y, z);
				case STONES -> stones(p, rnd, x, y, z);
				case CLUSTER -> cluster(p, rnd, x, y, z, scale);
				default -> crystal(p, rnd, x, s.top + 13 + rnd.nextInt(7), z, 2 + rnd.nextInt(2));
			}
			placed++;
		}
	}

	/** True when a patch of the island's turf is open: ground under all of it, nothing built on it or over it. */
	static boolean free(Plan p, int x, int top, int z, int rad) {
		return free(p, x, top, z, rad, 30);
	}

	/** As {@link #free(Plan, int, int, int, int)}, with the open air above the middle only checked {@code tall} high. */
	static boolean free(Plan p, int x, int top, int z, int rad, int tall) {
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				BlockState g = p.get(x + dx, top, z + dz);
				if (g == null || !turf(g)) {
					return false;
				}
				BlockState on = p.get(x + dx, top + 1, z + dz);
				if (on != null && !growth(on)) {
					return false;
				}
				for (int y = top + 2; y <= top + Math.min(6, tall); y++) {
					if (p.has(x + dx, y, z + dz)) {
						return false;
					}
				}
			}
		}
		for (int y = top + 7; y <= top + tall; y++) {
			if (p.has(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/** The surface of an island as {@link #island} lays it. */
	static boolean turf(BlockState s) {
		return s == moss() || s == mc("pale_moss_block", moss()) || s == stone() || s == brick() || s == mc("cobbled_deepslate", stone());
	}

	/** Grass, ferns and flowers: what a path or a building may simply replace. */
	static boolean growth(BlockState s) {
		return s == mc("short_grass") || s == mc("fern") || s == mc("allium") || s == mc("azure_bluet") || s == mc("firefly_bush")
				|| s == mc("open_eyeblossom");
	}

	/** Re-surfaces turf (and only turf) with paving, clearing the growth on it. */
	static void pave(Plan p, int x, int top, int z, BlockState s) {
		BlockState g = p.get(x, top, z);
		if (g == null || !turf(g)) {
			return;
		}
		p.set(x, top, z, s);
		BlockState on = p.get(x, top + 1, z);
		if (on != null && growth(on)) {
			p.clear(x, top + 1, z);
		}
	}

	/** Paving from the centre of a building out to its rim: light dressed tuff, darkening to rough deepslate. */
	static BlockState[] pathGrad() {
		return new BlockState[] {mc("polished_tuff", dsPol()), mc("tuff_bricks", dsTile()), dsTile(), bsBrick(), mc("cobbled_deepslate", stone())};
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
		BlockState[] worn = {mc("cobbled_deepslate", stone()), mc("tuff", stone()), mc("deepslate", stone()), stone()};
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
						lamp(p, rnd, x, s.top + 1, z);
					}
				}
			}
			// A pair of lamps where the street meets the bridge
			if (!trail) {
				for (int side = -2; side <= 2; side += 4) {
					int x = s.x + sx * (len - 2) + sz * side, z = s.z + sz * (len - 2) + sx * side;
					if (free(p, x, s.top, z, 1)) {
						lamp(p, rnd, x, s.top + 1, z);
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
	 * A stone bridge from one island to the next along an axis: it starts and ends a couple of blocks inside each
	 * island's predicted rim (where the island's street meets it), runs straight, turns on a landing halfway to line up
	 * with the other island, and climbs with slab ramps or real stairs. Kerbs with connected wall railings and posts
	 * with lanterns line it; underneath, every span is a shallow pointed arch of masonry springing from pendants that
	 * end in soul lanterns, and the landing rests on a tapering pier.
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
		BlockState[] deckGrad = {dsTile(), bsBrick(), dsPol()};
		BlockState stairB = dsBrickStairs();
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
				p.set(x, c[0] - 1, z, dsTile());
			} else {
				p.set(x, c[0], z, grad(deckGrad, noise(7, x / 3.0, z / 3.0), rnd));
				if (c[1] == 1) {
					p.set(x, c[0] + 1, z, slab(dsSlab(), false));
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
			p.set(x, y, z, bsBrick());
			p.set(x, y + 1, z, dsWall());
			Direction out = railOut.get(e.getKey());
			p.soft(x, y - 1, z, stairs(dsBrickStairs(), out.getOpposite(), true));
			if (Math.floorMod(m, 6) == 0) {
				p.set(x, y + 2, z, dsWall());
				if (Math.floorMod(m, 12) == 0) {
					p.set(x, y + 3, z, rnd.nextBoolean() ? lantern() : soulLantern());
				}
			}
			count++;
		}
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
		BlockState[] under = {dsTile(), dsBrick(), bsBrick()};
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
							s = stairs(dsBrickStairs(), f, true);
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
				p.soft(x, y, z, dsChis());
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
					p.set(x2, y + 1, z2, i % 20 < 2 ? vein() : slab(dsSlab(), false));
				} else {
					p.set(x2, y - 1, z2, stairs(dsBrickStairs(), Direction.NORTH, true));
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
						p.set(ox + dx, y + j, oz + dz, rim ? bsPol() : (dx + dz & 1) == 0 ? bsBrick() : dsPol());
					}
				}
			}
		}
		int base = y + 3;
		int H = 92 + rnd.nextInt(20);
		double[][] segments = {{0, 0.42}, {0.47, 0.76}, {0.81, 1.0}};
		BlockState[] g = {mc("blackstone", bsPol()), bsPol(), dsPol(), dsTile(), mc("polished_tuff", dsTile()), amethyst()};
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
