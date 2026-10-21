package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An Elder's arena fights with it. Its domain changes as it weakens ({@code elder_arena_phase_one},
 * {@code elder_arena_phase_two}): the Matriarch's floor throws up ridges of root to hide behind, then roots hang down
 * under her arches and drag at whoever walks through them; the lanes of the Ferryman's whirlpool burn in waves of soul
 * fire, glowing first, faster and two at a time later on; the threads of the Weaver's web hold whoever steps on them,
 * then patches of its floor fall away. Glowing cores set in each Elder's great piece shield it
 * ({@code elder_core_ward}) until they are broken, a few hits each, and it reels as each one goes. And the islands hung
 * over the arena break loose one after another: their shadow gathers on the ground, then they come down, tearing out a
 * crater and hurting everyone near; an Elder caught under one loses a share of its health and is stunned.
 */
public final class ElderDomain {
	private ElderDomain() {}

	private static final double TAU = Math.PI * 2;

	/** A core: its blocks and light, the hits it has taken, whether it ever stood and whether it is broken. */
	static final class Core {
		final List<BlockPos> blocks;
		final BlockState light;
		int hits;
		boolean seen, broken;

		Core(List<BlockPos> blocks, BlockState light) {
			this.blocks = blocks;
			this.light = light;
		}

		boolean stands(ServerLevel level) {
			for (BlockPos p : blocks) {
				if (level.isLoaded(p) && level.getBlockState(p) == light) {
					return true;
				}
			}
			return false;
		}

		boolean holds(BlockPos pos) {
			return blocks.contains(pos);
		}

		double[] middle() {
			double x = 0, y = 0, z = 0;
			for (BlockPos p : blocks) {
				x += p.getX() + 0.5;
				y += p.getY() + 0.5;
				z += p.getZ() + 0.5;
			}
			int n = Math.max(1, blocks.size());
			return new double[] {x / n, y / n, z / n};
		}
	}

	/** An island on its way down: its shadow first, then its blocks falling, then where it strikes. */
	static final class Fall {
		final ArenaDesign.Isle isle;
		final double x, z;
		final int ground;
		int warn;
		final List<FallingBlockEntity> blocks = new ArrayList<>();
		boolean loose;
		int age;

		Fall(ArenaDesign.Isle isle, int ground, int warn) {
			this.isle = isle;
			this.x = isle.x() + 0.5;
			this.z = isle.z() + 0.5;
			this.ground = ground;
			this.warn = warn;
		}
	}

	/** What an arena's fight has come to: its phase, its cores, its burning lanes, falling floor and islands. */
	static final class State {
		int phase, clock;
		List<Core> cores;
		int nextLane = -1, laneWarn = -1, lastLane = -1;
		int[] lanes = new int[0];
		int nextDrop = -1, dropWarn = -1;
		double dropX, dropZ;
		int nextIsle = -1;
		Fall fall;
		final Set<Integer> fallen = new HashSet<>();
		/** When each player may be held by a thread again. */
		final Map<UUID, Integer> held = new HashMap<>();
		/** When each player may hit a core again. */
		final Map<UUID, Integer> struck = new HashMap<>();
		/** Where the Matriarch's roots hang under her arches. */
		final Set<Long> curtains = new HashSet<>();
	}

	// ------------------------------------------------------------------ every tick

	/** One tick of an arena's fight (from the arena's own tick, while its Elder lives and fights). */
	static void tick(ElderArena.Arena a, ElderBoss b) {
		State st = a.domain;
		st.clock++;
		if (b.getAction() == ElderBoss.SPAWN || a.bp == null) {
			return;
		}
		if (Config.ELDER_CORES > 0) {
			cores(a, b, st);
		}
		if (Config.ELDER_ARENA_PHASES > 0) {
			float frac = b.getHealth() / Math.max(1, b.getMaxHealth());
			int want = frac <= Config.ELDER_ARENA_PHASE_TWO ? 2 : frac <= Config.ELDER_ARENA_PHASE_ONE ? 1 : 0;
			while (st.phase < want) {
				st.phase++;
				enter(a, b, st);
			}
			if (st.phase >= 1) {
				during(a, b, st);
			}
		}
		if (Config.ELDER_ISLE_FALL > 0) {
			isles(a, b, st);
		}
	}

	private static List<ServerPlayer> fighters(ElderBoss b) {
		return ElderFx.targets(b, Math.max(8, Config.ELDER_FIGHTERS_RANGE));
	}

	private static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", id)).map(h -> h.value().defaultBlockState())
				.orElse(Blocks.STONE.defaultBlockState());
	}

	// ------------------------------------------------------------------ phases

	/** The domain changes: a sound and a shudder through the ground, and what that change brings. */
	private static void enter(ElderArena.Arena a, ElderBoss b, State st) {
		ArenaDesign.Blueprint bp = a.bp;
		ElderFx.roar(b, 6f, st.phase == 1 ? 0.7f : 0.55f);
		ElderFx.shake(b, bp.cx + 0.5, bp.cz + 0.5, bp.floorR + 32, 0.5f, 30);
		switch (bp.style) {
			case 0 -> {
				if (st.phase == 1) {
					ridges(a, b);
				} else {
					curtains(a, st);
				}
			}
			case 1 -> st.nextLane = st.clock + 20;
			default -> {
				if (st.phase >= 2) {
					st.nextDrop = st.clock + 40;
				}
			}
		}
	}

	private static void during(ElderArena.Arena a, ElderBoss b, State st) {
		switch (a.bp.style) {
			case 0 -> {
				if (st.phase >= 2 && st.clock % 5 == 0) {
					roots(a, b, st);
				}
			}
			case 1 -> lanes(a, b, st);
			default -> {
				if (st.clock % 4 == 0) {
					threads(a, b, st);
				}
				if (st.phase >= 2) {
					drops(a, b, st);
				}
			}
		}
	}

	/** The Matriarch's floor throws up ridges of root: low walls to hide behind, never over anyone's head. */
	private static void ridges(ElderArena.Arena a, ElderBoss b) {
		ArenaDesign.Blueprint bp = a.bp;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockState wood = block("dark_oak_wood"), root = block("mangrove_roots"), moss = block("moss_block"), dirt = block("rooted_dirt");
		List<ServerPlayer> near = fighters(b);
		List<ElderArena.Piece> out = new ArrayList<>();
		int n = Math.max(0, Config.MATRIARCH_RIDGES);
		double scale = Math.max(0.4, Math.min(1, bp.floorR / 48.0));
		for (int i = 0; i < n; i++) {
			double ang = bp.turn + TAU * (i + rnd.nextDouble() * 0.6) / Math.max(1, n);
			double r = bp.floorR * (0.3 + rnd.nextDouble() * 0.5);
			double ox = bp.cx + 0.5 + Math.cos(ang) * r, oz = bp.cz + 0.5 + Math.sin(ang) * r;
			// Across the way to the heart, give or take
			double dir = ang + Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.9;
			double ux = Math.cos(dir), uz = Math.sin(dir);
			double len = (8 + rnd.nextDouble() * 6) * scale + 2;
			for (double s = -len / 2; s <= len / 2; s += 0.5) {
				int h = (int) Math.round(1 + 2 * Math.sin(Math.PI * (s / len + 0.5)));
				for (int side = 0; side <= 1; side++) {
					int x = (int) Math.floor(ox + ux * s - uz * side * 0.8), z = (int) Math.floor(oz + uz * s + ux * side * 0.8);
					boolean clear = true;
					for (ServerPlayer p : near) {
						double dx = p.getX() - (x + 0.5), dz = p.getZ() - (z + 0.5);
						clear &= dx * dx + dz * dz > 2.5 * 2.5;
					}
					if (!clear) {
						continue;
					}
					for (int y = 1; y <= h; y++) {
						BlockState st = y == h ? (rnd.nextInt(3) == 0 ? moss : root) : y == 1 && rnd.nextInt(4) == 0 ? dirt : wood;
						out.add(new ElderArena.Piece(new BlockPos(x, bp.gy + y, z), st, ElderArena.AIR, null));
					}
				}
			}
		}
		ElderArena.grow(a, out);
	}

	/** Roots let down under the Matriarch's arches, in curtains that drag at whoever walks through them. */
	private static void curtains(ElderArena.Arena a, State st) {
		ArenaDesign.Blueprint bp = a.bp;
		ServerLevel level = a.level;
		BlockState hanging = block("hanging_roots");
		List<ElderArena.Piece> out = new ArrayList<>();
		for (int g : bp.arches) {
			if (g < 0 || g >= a.groups.size()) {
				continue;
			}
			for (ElderArena.Piece p : a.groups.get(g)) {
				if (out.size() > 1600) {
					break;
				}
				int x = p.pos.getX(), y = p.pos.getY(), z = p.pos.getZ();
				if (!p.placed || y - bp.gy < 6 || Math.floorMod(x * 7 + z * 13, 4) != 0 || !level.isLoaded(p.pos)
						|| !level.getBlockState(p.pos.below()).isAir()) {
					continue;
				}
				int len = Math.min(y - bp.gy - 3, 3 + Math.floorMod(x * 31 + z * 17, 6));
				for (int i = 1; i <= len; i++) {
					BlockPos at = p.pos.below(i);
					out.add(new ElderArena.Piece(at, hanging, ElderArena.AIR, null));
					st.curtains.add(at.asLong());
				}
			}
		}
		ElderArena.grow(a, out);
	}

	/** Whoever walks through the Matriarch's hanging roots is dragged at. */
	private static void roots(ElderArena.Arena a, ElderBoss b, State st) {
		if (Config.MATRIARCH_ROOTS_SLOW <= 0 || st.curtains.isEmpty()) {
			return;
		}
		for (ServerPlayer p : fighters(b)) {
			BlockPos feet = p.blockPosition();
			if (st.curtains.contains(feet.asLong()) || st.curtains.contains(feet.above().asLong())) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, Config.MATRIARCH_ROOTS_SLOW - 1, false, true));
				if (st.clock % 10 == 0) {
					WorldFx.particleIn(a.level, "minecraft:spore_blossom_air", p.getX(), p.getY() + 1.2, p.getZ(), 0.4, 0.6, 0.4, 0.0, 4);
				}
			}
		}
	}

	/** The lanes of the Ferryman's whirlpool glow, then burn: one at a time, later two. */
	private static void lanes(ElderArena.Arena a, ElderBoss b, State st) {
		ArenaDesign.Blueprint bp = a.bp;
		ServerLevel level = a.level;
		String dim = ElderFx.dim(b);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double reach = ArenaDesign.laneReach(bp);
		if (st.laneWarn < 0) {
			if (st.nextLane < 0 || st.clock < st.nextLane) {
				return;
			}
			int first = rnd.nextInt(5);
			if (first == st.lastLane) {
				first = (first + 1 + rnd.nextInt(4)) % 5;
			}
			st.lanes = st.phase >= 2 ? new int[] {first, (first + 2 + rnd.nextInt(2)) % 5} : new int[] {first};
			st.laneWarn = Math.max(5, (int) Math.round(Config.FERRYMAN_LANE_WARN_SECONDS * 20));
			WorldFx.soundIn(dim, "minecraft:block.respawn_anchor.charge", "hostile", bp.cx + 0.5, bp.gy + 1, bp.cz + 0.5, 5f, 0.6f);
		}
		double step = Math.max(2, reach / 30);
		if (st.laneWarn > 0) {
			if (st.laneWarn % 4 == 0) {
				for (int lane : st.lanes) {
					for (double d = 2; d < reach; d += step) {
						double[] at = ArenaDesign.laneAt(bp, lane, d);
						WorldFx.particleIn(level, "minecraft:soul_fire_flame", at[0], bp.gy + 1.1, at[1], 0.6, 0.05, 0.6, 0.0, 1);
					}
				}
			}
			st.laneWarn--;
			return;
		}
		// They burn
		for (int lane : st.lanes) {
			for (double d = 2; d < reach; d += step) {
				double[] at = ArenaDesign.laneAt(bp, lane, d);
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", at[0], bp.gy + 1.3, at[1], 0.8, 0.6, 0.8, 0.03, 4);
			}
			double[] mid = ArenaDesign.laneAt(bp, lane, reach / 2);
			WorldFx.soundIn(dim, "minecraft:item.firecharge.use", "hostile", mid[0], bp.gy + 1, mid[1], 4f, 0.5f);
		}
		for (ServerPlayer p : fighters(b)) {
			int on = ArenaDesign.lane(bp, p.getX(), p.getZ());
			if (on < 0 || Math.abs(p.getY() - (bp.gy + 1)) > 3) {
				continue;
			}
			for (int lane : st.lanes) {
				if (lane == on) {
					p.hurtServer(level, level.damageSources().mobAttack(b), (float) Config.FERRYMAN_LANE_DAMAGE);
					p.setRemainingFireTicks(60);
				}
			}
		}
		st.lastLane = st.lanes[0];
		st.laneWarn = -1;
		double every = st.phase >= 2 ? Config.FERRYMAN_LANE_SECONDS_LATE : Config.FERRYMAN_LANE_SECONDS;
		st.nextLane = st.clock + Math.max(20, (int) Math.round(every * 20));
	}

	/** The threads of the Weaver's web hold whoever steps on them (and let them go again a while). */
	private static void threads(ElderArena.Arena a, ElderBoss b, State st) {
		if (Config.WEAVER_THREAD_SECONDS <= 0) {
			return;
		}
		ArenaDesign.Blueprint bp = a.bp;
		int hold = (int) Math.round(Config.WEAVER_THREAD_SECONDS * 20);
		for (ServerPlayer p : fighters(b)) {
			if (!p.onGround() || Math.abs(p.getY() - (bp.gy + 1)) > 1.2 || st.clock < st.held.getOrDefault(p.getUUID(), 0)
					|| !ArenaDesign.thread(bp, p.getX(), p.getZ())) {
				continue;
			}
			// Only where the thread is still there
			if (ElderArena.standing(a.level, BlockPos.containing(p.getX(), bp.gy, p.getZ())) == null
					&& ElderArena.standing(a.level, p.blockPosition().below()) == null) {
				continue;
			}
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, hold, 6, false, true));
			st.held.put(p.getUUID(), st.clock + hold + (int) Math.round(Config.WEAVER_THREAD_COOLDOWN * 20));
			WorldFx.particleIn(a.level, "minecraft:end_rod", p.getX(), p.getY() + 0.3, p.getZ(), 0.5, 0.2, 0.5, 0.01, 10);
			WorldFx.soundIn(ElderFx.dim(b), "minecraft:block.wool.place", "hostile", p.getX(), p.getY(), p.getZ(), 1.5f, 0.6f);
		}
	}

	/** Patches of the Weaver's floor crack, then fall away into the deep. */
	private static void drops(ElderArena.Arena a, ElderBoss b, State st) {
		ArenaDesign.Blueprint bp = a.bp;
		ServerLevel level = a.level;
		int r = Math.max(1, Config.WEAVER_DROP_RADIUS);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (st.dropWarn < 0) {
			if (st.nextDrop < 0 || st.clock < st.nextDrop) {
				return;
			}
			List<ServerPlayer> near = fighters(b);
			st.nextDrop = st.clock + Math.max(20, (int) Math.round(Config.WEAVER_DROP_SECONDS * 20));
			if (near.isEmpty()) {
				return;
			}
			ServerPlayer p = near.get(rnd.nextInt(near.size()));
			double x = p.getX() + rnd.nextDouble(-2, 2), z = p.getZ() + rnd.nextDouble(-2, 2);
			double dx = x - (bp.cx + 0.5), dz = z - (bp.cz + 0.5);
			if (dx * dx + dz * dz > (bp.floorR - r) * (double) (bp.floorR - r)) {
				return;
			}
			st.dropX = x;
			st.dropZ = z;
			st.dropWarn = 30;
		}
		if (st.dropWarn > 0) {
			if (st.dropWarn % 3 == 0) {
				for (int i = 0; i < 16; i++) {
					double t = TAU * i / 16;
					WorldFx.particleIn(level, "minecraft:crit", st.dropX + Math.cos(t) * r, bp.gy + 1.1, st.dropZ + Math.sin(t) * r, 0.1, 0.05, 0.1, 0.0, 1);
				}
				WorldFx.soundIn(ElderFx.dim(b), "minecraft:block.amethyst_block.break", "hostile", st.dropX, bp.gy + 1, st.dropZ, 1.5f, 0.5f);
			}
			st.dropWarn--;
			return;
		}
		// It falls away
		st.dropWarn = -1;
		int cx = (int) Math.floor(st.dropX), cz = (int) Math.floor(st.dropZ);
		int shown = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r + r) {
					continue;
				}
				for (int y = bp.gy + 1; y >= bp.gy - 4; y--) {
					BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
					ElderArena.Piece piece = ElderArena.standing(level, pos);
					if (piece == null || piece.group < 0 || piece.group >= bp.kinds.size() || bp.kinds.get(piece.group) != ArenaDesign.FLOOR
							|| level.getBlockState(pos) != piece.state) {
						continue;
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
					if (piece.kind != ElderArena.GROUND) {
						// Floor that floated: gone for good (inlaid floor comes back as the ground it replaced)
						ElderArena.fell(a, piece);
					}
					if (shown++ < 40) {
						BlockFlight.Flight f = BlockFlight.out(level, pos, piece.state, new double[] {0, -10, 0}, 24, "minecraft:end_rod", 4);
						if (f != null) {
							a.flights.add(f);
						}
					}
				}
			}
		}
		ElderTerrain.furrow(level, st.dropX, bp.gy, st.dropZ, r, 4, 0, 0);
		WorldFx.soundIn(ElderFx.dim(b), "minecraft:block.glass.break", "hostile", st.dropX, bp.gy + 1, st.dropZ, 3f, 0.5f);
	}

	// ------------------------------------------------------------------ cores

	private static List<Core> cores(ElderArena.Arena a) {
		State st = a.domain;
		if (st.cores == null) {
			st.cores = new ArrayList<>();
			BlockState light = ArenaDesign.coreBlock(a.bp.style);
			for (List<BlockPos> c : a.bp.cores) {
				st.cores.add(new Core(c, light));
			}
		}
		return st.cores;
	}

	/** Twice a second: cores that came to stand count, cores something broke are gone, and each standing one is tied to its Elder. */
	private static void cores(ElderArena.Arena a, ElderBoss b, State st) {
		if (st.clock % 10 != 0) {
			return;
		}
		ServerLevel level = a.level;
		String spark = switch (a.bp.style) {
			case 0 -> "minecraft:happy_villager";
			case 1 -> "minecraft:soul_fire_flame";
			default -> "minecraft:end_rod";
		};
		double bx = b.getX(), by = b.getY() + b.getBbHeight() * 0.5, bz = b.getZ();
		for (Core c : cores(a)) {
			if (c.broken) {
				continue;
			}
			boolean stands = c.stands(level);
			if (stands) {
				c.seen = true;
			} else if (c.seen) {
				// Something else broke it (a blast, a crater)
				crack(a, b, c);
				continue;
			}
			if (!stands) {
				continue;
			}
			// A thread of light from the core to the Elder it shields
			double[] m = c.middle();
			double dx = bx - m[0], dy = by - m[1], dz = bz - m[2];
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			int n = (int) Math.min(30, Math.max(3, len / 4));
			for (int i = 1; i < n; i++) {
				double k = i / (double) n;
				WorldFx.particleIn(level, spark, m[0] + dx * k, m[1] + dy * k, m[2] + dz * k, 0.05, 0.05, 0.05, 0.0, 1);
			}
		}
	}

	/** Share of the damage an Elder shrugs off for the cores of its arena still standing. */
	public static float ward(ElderBoss b) {
		if (Config.ELDER_CORES <= 0 || Config.ELDER_CORE_WARD <= 0) {
			return 0;
		}
		ElderArena.Arena a = ElderArena.of(b);
		if (a == null || a.bp == null || a.bp.cores.isEmpty()) {
			return 0;
		}
		List<Core> cores = cores(a);
		int up = 0;
		for (Core c : cores) {
			if (c.seen && !c.broken) {
				up++;
			}
		}
		return (float) (Math.min(0.9, Config.ELDER_CORE_WARD) * up / cores.size());
	}

	/**
	 * A player hits a block: if it is part of a standing core it takes a hit instead of being broken (true), and it
	 * breaks after {@code elder_core_hits}.
	 */
	public static boolean hit(Player player, ServerLevel level, BlockPos pos) {
		if (Config.ELDER_CORES <= 0) {
			return false;
		}
		ElderArena.Arena a = ElderArena.owner(level, pos);
		if (a == null || a.bp == null || a.bp.cores.isEmpty()) {
			return false;
		}
		for (Core c : cores(a)) {
			if (c.broken || !c.holds(pos)) {
				continue;
			}
			State st = a.domain;
			if (st.clock < st.struck.getOrDefault(player.getUUID(), 0)) {
				return true;
			}
			st.struck.put(player.getUUID(), st.clock + 8);
			c.hits++;
			c.seen = true;
			double[] m = c.middle();
			String dim = level.dimension().identifier().toString();
			WorldFx.particleIn(level, "minecraft:enchanted_hit", m[0], m[1], m[2], 0.6, 0.6, 0.6, 0.3, 12);
			WorldFx.soundIn(dim, "minecraft:block.amethyst_block.hit", "block", m[0], m[1], m[2], 2f, 0.5f + 0.15f * c.hits);
			if (c.hits >= Math.max(1, Config.ELDER_CORE_HITS)) {
				net.minecraft.world.entity.Entity boss = level.getEntity(a.boss);
				crack(a, boss instanceof ElderBoss e ? e : null, c);
			}
			return true;
		}
		return false;
	}

	/** A core breaks: its light goes out in a burst, and its Elder reels. */
	private static void crack(ElderArena.Arena a, ElderBoss b, Core c) {
		c.broken = true;
		ServerLevel level = a.level;
		for (BlockPos p : c.blocks) {
			if (level.isLoaded(p) && level.getBlockState(p) == c.light) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		double[] m = c.middle();
		String dim = level.dimension().identifier().toString();
		WorldFx.particleIn(level, "minecraft:explosion", m[0], m[1], m[2], 0.5, 0.5, 0.5, 0.0, 3);
		WorldFx.particleIn(level, "minecraft:end_rod", m[0], m[1], m[2], 0.4, 0.4, 0.4, 0.25, 30);
		WorldFx.soundIn(dim, "minecraft:block.beacon.deactivate", "hostile", m[0], m[1], m[2], 4f, 0.8f);
		WorldFx.soundIn(dim, "minecraft:block.glass.break", "hostile", m[0], m[1], m[2], 3f, 0.6f);
		if (b != null) {
			ElderFight.daze(b, (int) Math.round(Config.ELDER_CORE_DAZE_SECONDS * 20));
			ElderFx.roar(b, 5f, 1.3f);
		}
	}

	/** Cores of an Elder's arena: how many it has, and how many still stand (for tests). */
	public static int[] cores(ElderBoss b) {
		ElderArena.Arena a = ElderArena.of(b);
		if (a == null || a.bp == null) {
			return new int[] {0, 0};
		}
		int up = 0;
		for (Core c : cores(a)) {
			up += c.seen && !c.broken ? 1 : 0;
		}
		return new int[] {cores(a).size(), up};
	}

	/** Where the blocks of an Elder's cores stand (for tests). */
	public static List<BlockPos> coreBlocks(ElderBoss b) {
		ElderArena.Arena a = ElderArena.of(b);
		List<BlockPos> out = new ArrayList<>();
		if (a != null && a.bp != null) {
			for (Core c : cores(a)) {
				if (!c.broken) {
					out.add(c.blocks.get(0));
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ falling islands

	private static void isles(ElderArena.Arena a, ElderBoss b, State st) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double lo = Math.max(3, Config.ELDER_ISLE_MIN_SECONDS), hi = Math.max(lo, Config.ELDER_ISLE_MAX_SECONDS);
		if (st.fall != null) {
			fall(a, b, st);
			return;
		}
		if (st.nextIsle < 0) {
			st.nextIsle = st.clock + (int) Math.round((lo + rnd.nextDouble() * (hi - lo)) * 20);
			return;
		}
		if (st.clock < st.nextIsle) {
			return;
		}
		st.nextIsle = st.clock + (int) Math.round((lo + rnd.nextDouble() * (hi - lo)) * 20);
		loosen(a, b, st, null);
	}

	/**
	 * An island over someone (a fighter at random, or the Elder) starts to come loose; with {@code over} given, the one
	 * nearest over that spot. With none hanging over any of them, the one nearest the first comes down instead (in the
	 * open heart of a great arena no island hangs overhead). False if no island is left to fall.
	 */
	static boolean loosen(ElderArena.Arena a, ElderBoss b, State st, double[] over) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<double[]> spots = new ArrayList<>();
		if (over != null) {
			spots.add(over);
		} else {
			List<ServerPlayer> near = fighters(b);
			if (!near.isEmpty()) {
				ServerPlayer p = near.get(rnd.nextInt(near.size()));
				spots.add(new double[] {p.getX(), p.getY(), p.getZ()});
			}
			spots.add(new double[] {b.getX(), b.getY(), b.getZ()});
		}
		for (int pass = 0; pass < 2; pass++) {
			for (double[] at : pass == 0 ? spots : spots.subList(0, Math.min(1, spots.size()))) {
				ArenaDesign.Isle best = nearest(a, st, at, pass == 0 ? 32 : Double.MAX_VALUE);
				if (best == null) {
					continue;
				}
				st.fallen.add(best.group());
				st.fall = new Fall(best, ground(a, best), Math.max(10, (int) Math.round(Config.ELDER_ISLE_WARN_SECONDS * 20)));
				WorldFx.soundIn(a.level.dimension().identifier().toString(), "minecraft:block.deepslate.break", "hostile", best.x() + 0.5, best.top(),
						best.z() + 0.5, 6f, 0.4f);
				return true;
			}
		}
		return false;
	}

	/** The standing island nearest a spot (within {@code reach} across) that hangs well over it, or null. */
	private static ArenaDesign.Isle nearest(ElderArena.Arena a, State st, double[] at, double reach) {
		ArenaDesign.Isle best = null;
		double bestD = reach == Double.MAX_VALUE ? Double.MAX_VALUE : reach * reach;
		for (ArenaDesign.Isle isle : a.bp.isles) {
			if (st.fallen.contains(isle.group()) || isle.top() - at[1] < 8) {
				continue;
			}
			double dx = isle.x() + 0.5 - at[0], dz = isle.z() + 0.5 - at[2];
			double d = dx * dx + dz * dz;
			if (d < bestD && standing(a, isle) >= 0.6) {
				best = isle;
				bestD = d;
			}
		}
		return best;
	}

	/** Share of an island's blocks still standing (a sample of them). */
	private static double standing(ElderArena.Arena a, ArenaDesign.Isle isle) {
		if (isle.group() < 0 || isle.group() >= a.groups.size()) {
			return 0;
		}
		List<ElderArena.Piece> group = a.groups.get(isle.group());
		if (group.isEmpty()) {
			return 0;
		}
		int step = Math.max(1, group.size() / 40), seen = 0, up = 0;
		for (int i = 0; i < group.size(); i += step) {
			ElderArena.Piece p = group.get(i);
			seen++;
			if (p.placed && a.level.isLoaded(p.pos) && a.level.getBlockState(p.pos) == p.state) {
				up++;
			}
		}
		return seen == 0 ? 0 : up / (double) seen;
	}

	/** Where an island would strike: the first solid ground under its middle that is not the island itself. */
	private static int ground(ElderArena.Arena a, ArenaDesign.Isle isle) {
		ServerLevel level = a.level;
		int x = isle.x(), z = isle.z();
		for (int y = isle.top() - 2; y > level.getMinY(); y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isLoaded(pos)) {
				break;
			}
			ElderArena.Piece p = ElderArena.standing(level, pos);
			if (p != null && p.group == isle.group()) {
				continue;
			}
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
			if (!s.isAir() && !s.canBeReplaced()) {
				return y + 1;
			}
		}
		return a.bp.gy + 1;
	}

	/** One tick of a falling island: its shadow gathering, then its blocks loosed, then where it strikes. */
	private static void fall(ElderArena.Arena a, ElderBoss b, State st) {
		Fall f = st.fall;
		ServerLevel level = a.level;
		String dim = level.dimension().identifier().toString();
		f.age++;
		if (f.warn > 0) {
			if (f.warn % 4 == 0) {
				double r = f.isle.r() + 1;
				int n = (int) Math.max(12, r * 6);
				for (int i = 0; i < n; i++) {
					double t = TAU * i / n;
					WorldFx.particleIn(level, "minecraft:squid_ink", f.x + Math.cos(t) * r, f.ground + 0.1, f.z + Math.sin(t) * r, 0.05, 0.0, 0.05, 0.0, 1);
				}
				WorldFx.particleIn(level, "minecraft:ash", f.x, f.isle.top() - 2, f.z, f.isle.r(), 1.5, f.isle.r(), 0.0, 12);
			}
			if (f.warn % 20 == 0) {
				WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", f.x, f.isle.top(), f.z, 4f, 0.4f);
			}
			f.warn--;
			return;
		}
		if (!f.loose) {
			f.loose = true;
			List<ElderArena.Piece> pieces = new ArrayList<>();
			for (ElderArena.Piece p : a.groups.get(f.isle.group())) {
				if (p.placed && level.isLoaded(p.pos) && level.getBlockState(p.pos) == p.state) {
					pieces.add(p);
				}
			}
			pieces.sort((p, q) -> Integer.compare(p.pos.getY(), q.pos.getY()));
			int room = 150, every = Math.max(1, pieces.size() / room);
			for (int i = 0; i < pieces.size(); i++) {
				ElderArena.Piece p = pieces.get(i);
				ElderArena.fell(a, p);
				if (i % every == 0 && f.blocks.size() < room) {
					f.blocks.add(ArenaRuin.rubble(a, p.pos, p.state, 0));
				} else {
					level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
			WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", f.x, f.isle.top(), f.z, 4f, 0.5f);
			if (f.blocks.isEmpty()) {
				st.fall = null;
			}
			return;
		}
		boolean down = f.age > 240;
		for (FallingBlockEntity e : f.blocks) {
			down |= e.isRemoved();
		}
		if (down) {
			strike(a, b, f);
			st.fall = null;
		}
	}

	/** Where an island strikes: a crater, everyone near hurt, and an Elder under it badly shaken. */
	private static void strike(ElderArena.Arena a, ElderBoss b, Fall f) {
		ServerLevel level = a.level;
		String dim = level.dimension().identifier().toString();
		double r = Math.max(1, Config.ELDER_ISLE_RADIUS);
		ElderTerrain.furrow(level, f.x, f.ground - 1, f.z, Math.min(6, f.isle.r() + 1), 3, 1, 5);
		WorldFx.particleIn(level, "minecraft:explosion_emitter", f.x, f.ground + 0.5, f.z, 0.5, 0.2, 0.5, 0.0, 1);
		WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", f.x, f.ground + 0.5, f.z, r * 0.6, 0.4, r * 0.6, 0.02, 20);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", f.x, f.ground, f.z, 6f, 0.6f);
		WorldFx.soundIn(dim, "minecraft:block.anvil.land", "hostile", f.x, f.ground, f.z, 4f, 0.5f);
		ElderFx.shake(b, f.x, f.z, r * 6, 0.8f, 20);
		for (ServerPlayer p : fighters(b)) {
			double dx = p.getX() - f.x, dy = p.getY() - f.ground, dz = p.getZ() - f.z;
			if (dx * dx + dz * dz <= r * r && Math.abs(dy) < 6) {
				p.hurtServer(level, level.damageSources().mobAttack(b), (float) Config.ELDER_ISLE_DAMAGE);
				ElderFx.shove(b, p, 0.8, 0.5);
			}
		}
		double reach = r + b.getBbWidth() / 2;
		double dx = b.getX() - f.x, dz = b.getZ() - f.z;
		if (dx * dx + dz * dz <= reach * reach && Math.abs(b.getY() - f.ground) < b.getBbHeight() + 6) {
			b.setHealth(Math.max(1, b.getHealth() - (float) (Math.max(0, Config.ELDER_ISLE_BOSS_SHARE) * b.getMaxHealth())));
			ElderFight.daze(b, (int) Math.round(Config.ELDER_ISLE_DAZE_SECONDS * 20));
			ElderFx.roar(b, 6f, 1.4f);
			WorldFx.particleIn(level, "minecraft:damage_indicator", b.getX(), b.getY() + b.getBbHeight() * 0.7, b.getZ(), 1.5, 1.5, 1.5, 0.2, 20);
			for (ServerPlayer p : fighters(b)) {
				Advancements.grant(p, Advancements.ISLAND_FELL);
			}
		}
	}

	/**
	 * An island over a spot comes loose now (for tests and shots): where it will strike and how high it hangs
	 * ({x, ground, z, top}), or null if none hangs near enough over it.
	 */
	public static double[] loosenOver(ElderBoss b, double x, double y, double z) {
		ElderArena.Arena a = ElderArena.of(b);
		if (a == null || a.bp == null || a.domain.fall != null || !loosen(a, b, a.domain, new double[] {x, y, z})) {
			return null;
		}
		Fall f = a.domain.fall;
		return new double[] {f.x, f.ground, f.z, f.isle.top()};
	}

	/** Whether an island of an Elder's arena is on its way down (for tests). */
	public static boolean falling(ElderBoss b) {
		ElderArena.Arena a = ElderArena.of(b);
		return a != null && a.domain.fall != null;
	}

	/** The phase an Elder's domain is in (for tests). */
	public static int phase(ElderBoss b) {
		ElderArena.Arena a = ElderArena.of(b);
		return a == null ? -1 : a.domain.phase;
	}
}
