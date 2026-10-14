package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tether.TetherService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffects;

/**
 * The Seam: the place between the wounds.
 *
 * <ul>
 *   <li><b>Rifts</b>: a Seam Needle used on crying obsidian tears a rift for fifteen seconds. Step into it and you
 *       are in the Seam, with a fixed amount of time.</li>
 *   <li><b>The way back</b>: you arrive on an island next to a return rift. The rift drifts, slowly, and a thread
 *       of silk is laid behind you so you can find it again. Step into it before the time runs out.</li>
 *   <li><b>Collapse</b>: run out of time or fall into the nothing and the Seam spits you out at your rift, cursed.</li>
 *   <li><b>Islands</b>: built as you explore, on a grid: quiet hush stone islands, Weaver looms strung with silk,
 *       and rare villages of the Thinned, with graves and a barrel of lore.</li>
 * </ul>
 */
public final class SeamService {
	private SeamService() {}

	public static final ResourceKey<Level> SEAM = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("extremedimensions", "the_seam"));
	private static final String SEAM_ID = "extremedimensions:the_seam";
	private static final int CELL = 100;
	private static final int PULL_TICKS = 60;

	/** A rift torn open in the world, waiting for someone to step in. */
	private static final class Rift {
		ServerLevel level;
		double x, y, z;
		int ticks;
	}

	private static final List<Rift> RIFTS = new ArrayList<>();

	public static void clear() {
		RIFTS.clear();
		SeamArchitect.clear();
		falls = null;
	}

	public static boolean inSeam(ServerPlayer p) {
		return Dims.SEAM.equals(Dims.of(p));
	}

	private static ServerLevel seam() {
		MinecraftServer s = WorldFx.server();
		return s == null ? null : s.getLevel(SEAM);
	}

	// ------------------------------------------------------------------ rifts

	/** A Seam Needle on crying obsidian: the air in front of it tears open. */
	public static InteractionResult openRift(ServerPlayer p, BlockPos obsidian, Direction face, ItemStack needle) {
		ServerLevel level = (ServerLevel) p.level();
		if (inSeam(p) || Dims.END.equals(Dims.of(level))) {
			return InteractionResult.FAIL;
		}
		BlockPos at = obsidian.relative(face);
		if (!level.getBlockState(at).canBeReplaced()) {
			return InteractionResult.FAIL;
		}
		if (!p.isCreative()) {
			needle.shrink(1);
		}
		Rift r = new Rift();
		r.level = level;
		r.x = at.getX() + 0.5;
		r.y = at.getY() + 0.1;
		r.z = at.getZ() + 0.5;
		RIFTS.add(r);
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "extremedimensions:rift_open", "player", r.x, r.y + 1, r.z, 1.5f, 1f);
		return InteractionResult.SUCCESS;
	}

	private static void tickRifts() {
		Iterator<Rift> it = RIFTS.iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Rift r = it.next();
			r.ticks++;
			String dim = r.level.dimension().identifier().toString();
			if (r.ticks % 2 == 0) {
				double a = r.ticks * 0.35;
				for (int k = 0; k < 3; k++) {
					double ang = a + k * 2.094;
					WorldFx.particleIn(dim, "extremedimensions:soul_mote", r.x + Math.cos(ang) * 0.6, r.y + 0.9 + Math.sin(a * 0.7 + k) * 0.6,
							r.z + Math.sin(ang) * 0.6, 0, 0, 0, 0, 1);
				}
				WorldFx.particleIn(dim, "minecraft:reverse_portal", r.x, r.y + 1, r.z, 0.2, 0.6, 0.2, 0.05, 4);
			}
			if (r.ticks % 10 == 0) {
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", r.x, r.y + 1, r.z, 0.3, 0.5, 0.3, 0, 2);
			}
			for (ServerPlayer p : r.level.players()) {
				if (!p.isSpectator() && p.distanceToSqr(r.x, r.y + 0.5, r.z) < 1.3 * 1.3) {
					later.add(() -> enter(p, r));
				}
			}
			if (r.ticks > 300) {
				WorldFx.particleIn(dim, "minecraft:reverse_portal", r.x, r.y + 1, r.z, 0.3, 0.8, 0.3, 0.3, 30);
				it.remove();
			}
		}
		later.forEach(Runnable::run);
	}

	/** Through the rift: an island is built where the player lands, with the return rift beside it. */
	private static void enter(ServerPlayer p, Rift from) {
		ServerLevel seam = seam();
		PlayerData d = DataStore.get(p.getUUID());
		if (seam == null || d.inSeam) {
			return;
		}
		d.homeDim = Dims.of(from.level);
		d.homeX = from.x;
		d.homeY = from.y;
		d.homeZ = from.z;
		int x = (int) Math.floor(from.x);
		int z = (int) Math.floor(from.z);
		if (MendingService.opensNearHeart()) {
			// The seal is broken: every rift opens near the Heart, where all the threads meet
			int[] a = MendingService.arrival();
			x = a[0];
			z = a[1];
		}
		int top = 96;
		SeamArchitect.Plan arrival = new SeamArchitect.Plan();
		Random rnd = new Random(x * 31L + z);
		SeamArchitect.island(arrival, rnd, SeamArchitect.Shape.of(rnd, x, top, z, 12), true);
		SeamArchitect.lamp(arrival, rnd, x - 3, top + 1, z - 3);
		arrival.placeNow(seam);
		for (int dx = -1; dx <= 5; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				// the landing and the rift stand clear
				seam.setBlock(new BlockPos(x + dx, top + 1, z + dz), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		addFalls(arrival.falls);
		DataStore.seamCells().add("a@" + x + "," + z);
		d.riftX = x + 3.5;
		d.riftY = top + 1;
		d.riftZ = z + 0.5;
		d.inSeam = true;
		d.seamMax = Math.max(30, Config.SEAM_SECONDS) + Config.WOVEN_SEAM_SECONDS * d.tetherTier;
		d.seamLeft = d.seamMax;
		d.seamPull = 0;
		d.hasRopeEnd = false;
		WorldFx.tp(p, SEAM_ID, x + 0.5, top + 1, z + 0.5);
		WorldFx.sound(p, "extremedimensions:rift_open", 1.0f, 0.8f);
		WorldFx.fx(p, Fx.START_END, 0);
		Advancements.grant(p, Advancements.SEAM_ENTERED);
		Journal.unlock(p, d, JournalEntry.SEAM);
		DataStore.markDirty();
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer s) {
		tickRifts();
		ServerLevel level = s.getLevel(SEAM);
		if (level != null) {
			if (SeamArchitect.pending() > 0) {
				SeamArchitect.tick(level, 4000);
			}
			lights(level, s.getTickCount());
		}
		boolean slow = s.getTickCount() % 20 == 0;
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			if (!d.inSeam) {
				continue;
			}
			if (!inSeam(p)) {
				// Left the Seam some other way (a command, a death): the thread is cut
				d.inSeam = false;
				TetherService.clear(p, d, false);
				continue;
			}
			tickPlayer(p, d, s.getTickCount());
			if (slow) {
				build(p);
				Sync.state(p, d);
			}
		}
	}

	private static void tickPlayer(ServerPlayer p, PlayerData d, int tick) {
		if (d.seamPull > 0) {
			pull(p, d);
			return;
		}
		if (!p.isCreative() && !(MendingService.active() && MendingService.inArena(p))) {
			d.seamLeft -= 1.0 / 20.0;
		}
		TetherService.lay(p, d);
		String dim = SEAM_ID;
		// The return rift: a column of soul light that drifts
		if (tick % 3 == 0) {
			double a = tick * 0.2;
			WorldFx.particleIn(dim, "extremedimensions:soul_mote", d.riftX + Math.cos(a) * 0.5, d.riftY + 0.6 + (tick % 30) / 15.0,
					d.riftZ + Math.sin(a) * 0.5, 0, 0, 0, 0, 1);
			WorldFx.particleIn(dim, "minecraft:reverse_portal", d.riftX, d.riftY + 1, d.riftZ, 0.15, 0.5, 0.15, 0.04, 2);
		}
		if (tick % 200 == 0) {
			drift(d);
		}
		if (tick % 160 == 0) {
			WorldFx.soundSelf(p, "extremedimensions:seam_ambient", 0.5f, 1.0f);
		}
		if (tick % 600 == 0 && ThreadLocalRandom.current().nextInt(3) == 0) {
			// The Thinned watch, but here they do not take
			LurkerService.spawnWatch(p);
		}
		if (p.distanceToSqr(d.riftX, d.riftY + 0.5, d.riftZ) < 1.4 * 1.4) {
			exit(p, d);
		} else if (d.seamLeft <= 0 || p.getY() < 8) {
			collapse(p, d);
		}
		villages(p);
	}

	/** The rift wanders a block at a time, but never off solid ground. */
	private static void drift(PlayerData d) {
		ServerLevel seam = seam();
		Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(seam.getRandom());
		BlockPos next = BlockPos.containing(d.riftX + dir.getStepX(), d.riftY, d.riftZ + dir.getStepZ());
		if (seam.getBlockState(next).isAir() && !seam.getBlockState(next.below()).isAir()) {
			d.riftX = next.getX() + 0.5;
			d.riftZ = next.getZ() + 0.5;
		}
	}

	private static void exit(ServerPlayer p, PlayerData d) {
		d.inSeam = false;
		TetherService.clear(p, d, false);
		WorldFx.tp(p, Dims.fullIdOf(d.homeDim), d.homeX, d.homeY, d.homeZ);
		WorldFx.fx(p, Fx.SAFE, 0);
		WorldFx.soundSelf(p, "extremedimensions:portal_home", 0.6f, 1.1f);
		Advancements.grant(p, Advancements.SEAM_RETURNED);
		DataStore.markDirty();
	}

	/** Out of time, or fallen: the Seam drags the player back out over three seconds. */
	private static void collapse(ServerPlayer p, PlayerData d) {
		d.seamPull = PULL_TICKS;
		WorldFx.fx(p, Fx.COLLAPSE, 0);
		WorldFx.sound(p, "extremedimensions:collapse", 1.2f, 1.1f);
		WorldFx.effect(p, MobEffects.LEVITATION, 3, 0);
		WorldFx.effect(p, MobEffects.RESISTANCE, 5, 4);
	}

	private static void pull(ServerPlayer p, PlayerData d) {
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 1.2, 0, 4);
		if (--d.seamPull > 0) {
			return;
		}
		d.inSeam = false;
		d.collapses++;
		TetherService.clear(p, d, false);
		p.removeEffect(MobEffects.LEVITATION);
		WorldFx.tp(p, Dims.fullIdOf(d.homeDim), d.homeX, d.homeY, d.homeZ);
		PenaltyService.curse(p, Dims.OVERWORLD.equals(d.homeDim));
		DataStore.markDirty();
	}

	/** Seam Silk and lore are found in villages; the first visit to one is remembered. */
	private static void villages(ServerPlayer p) {
		if (p.tickCount % 40 != 0) {
			return;
		}
		for (String key : DataStore.seamCells()) {
			if (!key.startsWith("v@")) {
				continue;
			}
			String[] xyz = key.substring(2).split(",");
			double dx = Integer.parseInt(xyz[0]) - p.getX();
			double dz = Integer.parseInt(xyz[2]) - p.getZ();
			if (dx * dx + dz * dz < 16 * 16) {
				Advancements.grant(p, Advancements.THINNED_FOUND);
				Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.THINNED);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ islands

	private static long hash(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}

	private static long seed() {
		return seam().getSeed();
	}

	private static final int PLAIN = 0, LOOM = 1, VILLAGE = 2, SPIRE = 3, HANGING = 4, HALO = 5;

	private static int kind(int type) {
		return type < 7 ? PLAIN : type < 10 ? LOOM : type < 13 ? VILLAGE : type < 16 ? SPIRE : type < 18 ? HANGING : HALO;
	}

	/** The island of a cell: x, top, z, radius, type. */
	private static int[] center(int cx, int cz) {
		long h = hash(cx, cz, seed());
		int type = (int) ((h >> 32) % 20);
		int x = cx * CELL + CELL / 2 + (int) (h % 13) - 6;
		int z = cz * CELL + CELL / 2 + (int) ((h >> 8) % 13) - 6;
		int top = 80 + (int) ((h >> 16) % 20);
		int v = (int) ((h >> 24) % 5);
		int r = switch (kind(type)) {
			case SPIRE -> 34 + v;
			case VILLAGE -> 26 + v;
			case LOOM -> 22 + v;
			case HANGING -> 14 + v;
			case HALO -> 16 + v;
			default -> 11 + (int) ((h >> 24) % 9);
		};
		return new int[] {x, top, z, r, type};
	}

	/** Cells around the Heart are left to its crown (and no island may reach into it). */
	private static boolean nearHeart(int[] c) {
		double dx = c[0] - MendingService.HX, dz = c[2] - MendingService.HZ;
		double reach = 84 + c[3];
		return dx * dx + dz * dz < reach * reach;
	}

	/** True when a rift's arrival island lies within {@code dist} of this point. */
	private static boolean nearArrival(int x, int z, int dist) {
		for (String key : DataStore.seamCells()) {
			if (key.startsWith("a@")) {
				String[] xz = key.substring(2).split(",");
				double dx = Integer.parseInt(xz[0]) - x, dz = Integer.parseInt(xz[1]) - z;
				if (dx * dx + dz * dz < (double) dist * dist) {
					return true;
				}
			}
		}
		return false;
	}

	/** Designs the cells around the player, nearest first, one per call; the architect builds them over time. */
	private static void build(ServerPlayer p) {
		if (SeamArchitect.pending() > 150_000) {
			return;
		}
		int pcx = Math.floorDiv(p.getBlockX(), CELL);
		int pcz = Math.floorDiv(p.getBlockZ(), CELL);
		for (int ring = 0; ring <= 2; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = pcx + dx, cz = pcz + dz;
					String key = "c@" + cx + "," + cz;
					if (DataStore.seamCells().contains(key)) {
						if (ring <= 1 && SeamArchitect.pending() == 0 && missing(p, cx, cz)) {
							// The Seam was regenerated (its folder deleted): design the cell again
							design(cx, cz);
							return;
						}
						continue;
					}
					DataStore.seamCells().add(key);
					design(cx, cz);
					return;
				}
			}
		}
	}

	/** True when a cell is marked as built but its island is not there (the chunk is loaded and the centre is air). */
	private static boolean missing(ServerPlayer p, int cx, int cz) {
		int[] c = center(cx, cz);
		if (nearHeart(c)) {
			return false;
		}
		BlockPos pos = new BlockPos(c[0], c[1] - 1, c[2]);
		ServerLevel level = (ServerLevel) p.level();
		return level.isLoaded(pos) && level.getBlockState(pos).isAir();
	}

	/** The island of a cell as it is (or will be) built: the same random source, so the rim is predicted exactly. */
	private static SeamArchitect.Shape shapeOf(int cx, int cz, Random rnd) {
		int[] c = center(cx, cz);
		int r = c[3];
		if (nearArrival(c[0], c[2], r + 18)) {
			r = Math.min(r, 10);
		}
		return SeamArchitect.Shape.of(rnd, c[0], c[1], c[2], r);
	}

	private static void design(int cx, int cz) {
		int[] c = center(cx, cz);
		if (nearHeart(c)) {
			return;
		}
		Random rnd = new Random(hash(cx, cz, seed() + 7));
		SeamArchitect.Shape shape = shapeOf(cx, cz, rnd);
		SeamArchitect.Plan plan = new SeamArchitect.Plan();
		int x = shape.x(), top = shape.top(), z = shape.z(), r = shape.r();
		int kind = nearArrival(x, z, r + 30) ? PLAIN : kind(c[4]);
		// Which sides get a bridge: this cell strings them east and south, its neighbours west and north
		SeamArchitect.Shape east = neighbour(cx + 1, cz), west = neighbour(cx - 1, cz), south = neighbour(cx, cz + 1), north = neighbour(cx, cz - 1);
		boolean toEast = east != null && (hash(cx, cz, seed() + 11) & 3) != 0 && SeamArchitect.canBridge(shape, east, true);
		boolean toSouth = south != null && (hash(cx, cz, seed() + 13) & 3) != 0 && SeamArchitect.canBridge(shape, south, false);
		boolean fromWest = west != null && (hash(cx - 1, cz, seed() + 11) & 3) != 0 && SeamArchitect.canBridge(west, shape, true);
		boolean fromNorth = north != null && (hash(cx, cz - 1, seed() + 13) & 3) != 0 && SeamArchitect.canBridge(north, shape, false);
		int mask = (toEast ? 1 : 0) | (fromWest ? 2 : 0) | (toSouth ? 4 : 0) | (fromNorth ? 8 : 0);
		int depth = SeamArchitect.island(plan, rnd, shape, true);
		build(plan, rnd, kind, shape, depth, mask);
		if (toEast) {
			SeamArchitect.bridge(plan, rnd, shape, east, true);
		}
		if (toSouth) {
			SeamArchitect.bridge(plan, rnd, shape, south, false);
		}
		plan.queue();
		addFalls(plan.falls);
	}

	/** The island of a neighbouring cell as it is (or will be) built, or null if the Heart's crown has that cell. */
	private static SeamArchitect.Shape neighbour(int cx, int cz) {
		return nearHeart(center(cx, cz)) ? null : shapeOf(cx, cz, new Random(hash(cx, cz, seed() + 7)));
	}

	/**
	 * Builds what stands on an island, then lays its streets out to the bridges ({@code mask}: bit 0 east, 1 west,
	 * 2 south, 3 north) and dresses the open ground that is left.
	 */
	private static void build(SeamArchitect.Plan plan, Random rnd, int kind, SeamArchitect.Shape s, int depth, int mask) {
		int x = s.x(), top = s.top(), z = s.z(), r = s.r();
		switch (kind) {
			case SPIRE -> {
				SeamArchitect.spire(plan, rnd, x, top, z, r);
				SeamArchitect.streets(plan, rnd, s, mask, 0, false);
				SeamArchitect.grounds(plan, rnd, s, 6, SeamArchitect.ARCH, SeamArchitect.TREE, SeamArchitect.LAMP, SeamArchitect.STONES, SeamArchitect.CLUSTER);
			}
			case LOOM -> {
				SeamArchitect.loom(plan, rnd, x, top, z, r);
				SeamArchitect.streets(plan, rnd, s, mask, 0, false);
				SeamArchitect.grounds(plan, rnd, s, 5, SeamArchitect.ARCH, SeamArchitect.TREE, SeamArchitect.LAMP, SeamArchitect.STONES);
			}
			case VILLAGE -> {
				SeamArchitect.village(plan, rnd, x, top, z, r);
				SeamArchitect.streets(plan, rnd, s, mask, r >= 24 ? 9 : 7, false);
				SeamArchitect.grounds(plan, rnd, s, 3, SeamArchitect.TREE, SeamArchitect.LAMP);
				DataStore.seamCells().add("v@" + x + "," + top + "," + z);
			}
			case HANGING -> {
				SeamArchitect.hangingTower(plan, rnd, x, top - depth, z);
				SeamArchitect.streets(plan, rnd, s, mask, 0, true);
				SeamArchitect.plain(plan, rnd, s);
			}
			case HALO -> {
				SeamArchitect.halo(plan, rnd, x, top, z, r);
				SeamArchitect.streets(plan, rnd, s, mask, 6, true);
				SeamArchitect.grounds(plan, rnd, s, 3 + rnd.nextInt(2), SeamArchitect.CLUSTER, SeamArchitect.CLUSTER, SeamArchitect.LAMP);
			}
			default -> {
				SeamArchitect.streets(plan, rnd, s, mask, 0, true);
				SeamArchitect.plain(plan, rnd, s);
			}
		}
	}

	/**
	 * Operator showcase: designs one of every kind of structure in a row of cells far from everything (x = 40..45
	 * cells), at different heights and joined by bridges, so they can be visited with a teleport.
	 */
	public static int showcase(MinecraftServer s) {
		if (s.getLevel(SEAM) == null) {
			return 0;
		}
		int[] kinds = {SPIRE, LOOM, VILLAGE, HANGING, HALO, PLAIN};
		int n = kinds.length;
		Random[] rnds = new Random[n];
		SeamArchitect.Shape[] shapes = new SeamArchitect.Shape[n];
		for (int i = 0; i < n; i++) {
			int cx = 40 + i, cz = 40;
			rnds[i] = new Random(hash(cx, cz, 99));
			int x = cx * CELL + CELL / 2, z = cz * CELL + CELL / 2, top = 90 + (i % 3) * 6 - 6;
			int r = switch (kinds[i]) { case SPIRE -> 36; case VILLAGE -> 28; case LOOM -> 24; case HANGING -> 16; case HALO -> 18; default -> 16; };
			shapes[i] = SeamArchitect.Shape.of(rnds[i], x, top, z, r);
		}
		for (int i = 0; i < n; i++) {
			DataStore.seamCells().add("c@" + (40 + i) + ",40");
			boolean east = i < n - 1 && SeamArchitect.canBridge(shapes[i], shapes[i + 1], true);
			boolean west = i > 0 && SeamArchitect.canBridge(shapes[i - 1], shapes[i], true);
			SeamArchitect.Plan plan = new SeamArchitect.Plan();
			int depth = SeamArchitect.island(plan, rnds[i], shapes[i], true);
			build(plan, rnds[i], kinds[i], shapes[i], depth, (east ? 1 : 0) | (west ? 2 : 0));
			if (east) {
				SeamArchitect.bridge(plan, rnds[i], shapes[i], shapes[i + 1], true);
			}
			plan.queue();
			addFalls(plan.falls);
		}
		ExtremeDimensions.LOGGER.info("Seam showcase: {} structures, {} blocks queued at x={}..{} z={}", n, SeamArchitect.pending(),
				40 * CELL, 46 * CELL, 40 * CELL + CELL / 2);
		return n;
	}

	// ------------------------------------------------------------------ light falls, the heart beam

	private static List<int[]> falls;

	private static List<int[]> falls() {
		if (falls == null) {
			falls = new ArrayList<>();
			for (String key : DataStore.seamCells()) {
				if (key.startsWith("f@")) {
					String[] v = key.substring(2).split(",");
					falls.add(new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])});
				}
			}
		}
		return falls;
	}

	public static void addFalls(List<int[]> list) {
		for (int[] f : list) {
			DataStore.seamCells().add("f@" + f[0] + "," + f[1] + "," + f[2] + "," + f[3]);
			falls().add(f);
		}
	}

	/** Light pouring off island edges, and the beam over the Heart, seen from far away. */
	private static void lights(ServerLevel level, int tick) {
		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return;
		}
		if (tick % 8 == 0) {
			for (int[] f : falls()) {
				for (ServerPlayer p : players) {
					double dx = p.getX() - f[0], dz = p.getZ() - f[2];
					if (dx * dx + dz * dz < 110 * 110) {
						double half = f[3] / 2.0;
						WorldFx.particleFar(SEAM_ID, "minecraft:end_rod", f[0] + 0.5, f[1] - half, f[2] + 0.5, 0.2, half * 0.6, 0.2, 0.0, 5, 130);
						break;
					}
				}
			}
		}
		if (DataStore.heartBuilt() && tick % 4 == 0) {
			for (ServerPlayer p : players) {
				if (p.distanceToSqr(MendingService.HX, MendingService.HY, MendingService.HZ) < 200 * 200) {
					double hx = MendingService.HX + 0.5, hy = MendingService.HY + 30, hz = MendingService.HZ + 0.5;
					WorldFx.particleFar(SEAM_ID, "minecraft:end_rod", hx, hy, hz, 0.3, 18, 0.3, 0.0, 14, 220);
					WorldFx.particleFar(SEAM_ID, "minecraft:reverse_portal", hx, hy, hz, 1.2, 18, 1.2, 0.05, 18, 220);
					break;
				}
			}
		}
	}

	/** Barrels of the Thinned: silk, pages, fragments and old things; richer the harder they are to reach. */
	public static void fillLoot(ServerLevel level, BlockPos b, int richness) {
		if (!(level.getBlockEntity(b) instanceof Container c)) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int slot = 0;
		c.setItem(slot++, new ItemStack(ModItems.SEAM_SILK, 2 + rnd.nextInt(3 + 2 * richness)));
		if (rnd.nextDouble() < 0.35 + 0.2 * richness) {
			c.setItem(slot++, new ItemStack(ModItems.WANDERERS_PAGE));
		}
		if (rnd.nextDouble() < 0.15 + 0.15 * richness) {
			c.setItem(slot++, new ItemStack(ModItems.TETHER_FRAGMENT));
		}
		if (richness >= 3) {
			c.setItem(slot++, new ItemStack(Items.ECHO_SHARD, 1 + rnd.nextInt(3)));
		}
		Item[] old = {Items.ECHO_SHARD, Items.AMETHYST_SHARD, Items.BONE, Items.STRING, Items.SOUL_TORCH, Items.CANDLE};
		c.setItem(slot, new ItemStack(old[rnd.nextInt(old.length)], 1 + rnd.nextInt(3)));
		c.setChanged();
	}

	/** Operator command: straight into the Seam, as if through a rift at the player's feet. */
	public static boolean summon(ServerPlayer p) {
		if (inSeam(p)) {
			return false;
		}
		Rift r = new Rift();
		r.level = (ServerLevel) p.level();
		r.x = p.getX();
		r.y = p.getY();
		r.z = p.getZ();
		enter(p, r);
		return true;
	}

	/** Rift distance for the HUD. */
	public static int riftDistance(ServerPlayer p, PlayerData d) {
		double dx = p.getX() - d.riftX, dz = p.getZ() - d.riftZ;
		return (int) Math.sqrt(dx * dx + dz * dz);
	}
}
