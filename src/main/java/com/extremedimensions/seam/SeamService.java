package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lurker.LurkerService;
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
	private static final int CELL = 34;
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
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:rift_open player @a %.2f %.2f %.2f 1.5 1",
				dim, r.x, r.y + 1, r.z));
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
		int top = 96;
		island(seam, x, top, z, 11, 0, true);
		DataStore.seamCells().add("a@" + x + "," + z);
		d.riftX = x + 3.5;
		d.riftY = top + 1;
		d.riftZ = z + 0.5;
		d.inSeam = true;
		d.seamMax = Math.max(30, Config.SEAM_SECONDS);
		d.seamLeft = d.seamMax;
		d.seamPull = 0;
		d.hasRopeEnd = false;
		WorldFx.tp(p, SEAM_ID, x + 0.5, top + 1, z + 0.5);
		WorldFx.sound(p, "extremedimensions:rift_open", 1.0f, 0.8f);
		WorldFx.fx(p, Fx.START_END, 0);
		Advancements.grant(p, Advancements.SEAM_ENTERED);
		Journal.unlock(p, d, JournalEntry.SEAM);
		DataStore.save();
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer s) {
		tickRifts();
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
		if (!p.isCreative()) {
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
		DataStore.save();
	}

	/** Out of time, or fallen: the Seam drags the player back out over three seconds. */
	private static void collapse(ServerPlayer p, PlayerData d) {
		d.seamPull = PULL_TICKS;
		WorldFx.fx(p, Fx.COLLAPSE, 0);
		WorldFx.sound(p, "extremedimensions:collapse", 1.2f, 1.1f);
		WorldFx.run("effect give " + p.getStringUUID() + " minecraft:levitation 3 0 true");
		WorldFx.run("effect give " + p.getStringUUID() + " minecraft:resistance 5 4 true");
	}

	private static void pull(ServerPlayer p, PlayerData d) {
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 1.2, 0, 4);
		if (--d.seamPull > 0) {
			return;
		}
		d.inSeam = false;
		d.collapses++;
		TetherService.clear(p, d, false);
		WorldFx.run("effect clear " + p.getStringUUID() + " minecraft:levitation");
		WorldFx.tp(p, Dims.fullIdOf(d.homeDim), d.homeX, d.homeY, d.homeZ);
		PenaltyService.curse(p, Dims.OVERWORLD.equals(d.homeDim));
		DataStore.save();
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

	private static int[] center(int cx, int cz) {
		long h = hash(cx, cz, seed());
		int x = cx * CELL + CELL / 2 + (int) (h % 9) - 4;
		int z = cz * CELL + CELL / 2 + (int) ((h >> 8) % 9) - 4;
		int top = 90 + (int) ((h >> 16) % 13);
		int r = 7 + (int) ((h >> 24) % 8);
		int type = (int) ((h >> 32) % 10);
		return new int[] {x, top, z, r, type};
	}

	/** Builds the islands of the cells around the player, a couple at a time. */
	private static void build(ServerPlayer p) {
		ServerLevel seam = (ServerLevel) p.level();
		int pcx = Math.floorDiv(p.getBlockX(), CELL);
		int pcz = Math.floorDiv(p.getBlockZ(), CELL);
		int built = 0;
		for (int dx = -2; dx <= 2 && built < 2; dx++) {
			for (int dz = -2; dz <= 2 && built < 2; dz++) {
				int cx = pcx + dx, cz = pcz + dz;
				String key = cx + "," + cz;
				if (DataStore.seamCells().contains(key)) {
					continue;
				}
				DataStore.seamCells().add(key);
				int[] c = center(cx, cz);
				island(seam, c[0], c[1], c[2], c[3], c[4], false);
				bridge(seam, c, center(cx + 1, cz));
				bridge(seam, c, center(cx, cz + 1));
				built++;
			}
		}
	}

	/**
	 * One floating island: a wobbly disc of hush stone with a hanging, tapering underside threaded with silk veins.
	 * Type 0-5 plain, 6-7 a Weaver loom, 8-9 a village of the Thinned (on big islands).
	 */
	private static void island(ServerLevel level, int cx, int top, int cz, int r, int type, boolean arrival) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockState stone = ModBlocks.HUSH_STONE.defaultBlockState();
		BlockState vein = ModBlocks.SEAM_VEIN.defaultBlockState();
		double p1 = rnd.nextDouble() * 6.28, p2 = rnd.nextDouble() * 6.28;
		for (int dx = -r - 3; dx <= r + 3; dx++) {
			for (int dz = -r - 3; dz <= r + 3; dz++) {
				double ang = Math.atan2(dz, dx);
				double edge = r * (0.85 + 0.12 * Math.sin(ang * 3 + p1) + 0.08 * Math.sin(ang * 5 + p2));
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > edge) {
					continue;
				}
				double f = 1 - dist / edge;
				int depth = 2 + (int) Math.round(Math.pow(f, 0.7) * r * 0.9) + rnd.nextInt(2);
				int bump = f > 0.5 && rnd.nextInt(9) == 0 ? 1 : 0;
				for (int y = top - depth; y <= top + bump; y++) {
					boolean inner = y < top - 1;
					level.setBlock(new BlockPos(cx + dx, y, cz + dz), inner && rnd.nextInt(type == 6 || type == 7 ? 9 : 22) == 0 ? vein : stone, 2);
				}
			}
		}
		if (arrival) {
			lantern(level, cx + 2, top + 1, cz - 2);
			return;
		}
		if (type >= 8 && r >= 10) {
			village(level, cx, top, cz, r);
		} else if (type >= 6) {
			loom(level, cx, top, cz, r);
		} else {
			for (int i = 0; i < 1 + rnd.nextInt(3); i++) {
				int dx = rnd.nextInt(-r / 2, r / 2 + 1), dz = rnd.nextInt(-r / 2, r / 2 + 1);
				if (rnd.nextBoolean()) {
					lantern(level, cx + dx, surface(level, cx + dx, top, cz + dz), cz + dz);
				} else {
					place(level, new BlockPos(cx + dx, surface(level, cx + dx, top, cz + dz), cz + dz), Blocks.COBWEB.defaultBlockState());
				}
			}
		}
	}

	private static int surface(ServerLevel level, int x, int top, int z) {
		for (int y = top + 3; y > top - 4; y--) {
			if (!level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) {
				return y;
			}
		}
		return top + 1;
	}

	private static void place(ServerLevel level, BlockPos p, BlockState s) {
		if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()) {
			level.setBlock(p, s, 2);
		}
	}

	/** A soul lantern on a crooked post. */
	private static void lantern(ServerLevel level, int x, int y, int z) {
		int h = 1 + ThreadLocalRandom.current().nextInt(2);
		for (int k = 0; k < h; k++) {
			level.setBlock(new BlockPos(x, y + k, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
		}
		level.setBlock(new BlockPos(x, y + h, z), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
	}

	/** A Weaver loom: tall pillars of hush brick, strung together with strands of silk. */
	private static void loom(ServerLevel level, int cx, int top, int cz, int r) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int n = 2 + rnd.nextInt(3);
		List<int[]> tops = new ArrayList<>();
		double a0 = rnd.nextDouble() * 6.28;
		for (int i = 0; i < n; i++) {
			double a = a0 + i * 6.28 / n;
			int x = cx + (int) Math.round(Math.cos(a) * r * 0.55);
			int z = cz + (int) Math.round(Math.sin(a) * r * 0.55);
			int y0 = surface(level, x, top, z);
			int h = 7 + rnd.nextInt(7);
			for (int k = 0; k < h; k++) {
				level.setBlock(new BlockPos(x, y0 + k, z), (k % 4 == 3 ? ModBlocks.SEAM_VEIN : ModBlocks.HUSH_BRICKS).defaultBlockState(), 2);
			}
			tops.add(new int[] {x, y0 + h - 1, z});
		}
		for (int i = 0; i < tops.size(); i++) {
			int[] a = tops.get(i), b = tops.get((i + 1) % tops.size());
			strand(level, a, b);
		}
	}

	/** A sagging strand of silk (cobweb and glowing seam vein) between two points. */
	private static void strand(ServerLevel level, int[] a, int[] b) {
		int steps = (int) Math.ceil(Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[2] - a[2], 2))) + 1;
		for (int i = 1; i < steps; i++) {
			double t = (double) i / steps;
			double sag = Math.sin(t * Math.PI) * steps * 0.15;
			BlockPos p = BlockPos.containing(a[0] + (b[0] - a[0]) * t + 0.5, a[1] + (b[1] - a[1]) * t - sag, a[2] + (b[2] - a[2]) * t + 0.5);
			if (level.getBlockState(p).isAir()) {
				level.setBlock(p, (i % 3 == 0 ? ModBlocks.SEAM_VEIN : Blocks.COBWEB).defaultBlockState(), 2);
			}
		}
	}

	/** A village of the Thinned: low huts of hush brick and a field of graves. */
	private static void village(ServerLevel level, int cx, int top, int cz, int r) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int huts = 2 + rnd.nextInt(2);
		double a0 = rnd.nextDouble() * 6.28;
		for (int i = 0; i < huts; i++) {
			double a = a0 + i * 6.28 / huts;
			hut(level, cx + (int) Math.round(Math.cos(a) * r * 0.45), top + 1, cz + (int) Math.round(Math.sin(a) * r * 0.45), i == 0);
		}
		for (int i = 0; i < 4 + rnd.nextInt(3); i++) {
			int x = cx + rnd.nextInt(-2, 3), z = cz + rnd.nextInt(-2, 3);
			int y = surface(level, x, top, z);
			if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
				continue;
			}
			level.setBlock(new BlockPos(x, y, z), ModBlocks.HUSH_STONE.defaultBlockState(), 2);
			level.setBlock(new BlockPos(x, y + 1, z), Blocks.SKELETON_SKULL.defaultBlockState(), 2);
			BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", "candle")).ifPresent(c ->
					place(level, new BlockPos(x + 1, y, z), c.value().defaultBlockState()));
		}
		DataStore.seamCells().add("v@" + cx + "," + top + "," + cz);
	}

	private static void hut(ServerLevel level, int x0, int y0, int z0, boolean withLore) {
		BlockState wall = ModBlocks.HUSH_BRICKS.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				for (int dy = 0; dy < 4; dy++) {
					BlockPos p = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
					boolean door = dz == 2 && dx == 0 && dy < 2;
					boolean window = edge && dy == 1 && (dx == 0 || dz == 0) && !door;
					if (dy == 3) {
						level.setBlock(p, wall, 2);
					} else if (edge && !door && !window) {
						level.setBlock(p, wall, 2);
					} else {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				level.setBlock(new BlockPos(x0 + dx, y0 - 1, z0 + dz), ModBlocks.HUSH_STONE.defaultBlockState(), 2);
			}
		}
		if (withLore) {
			BlockPos b = new BlockPos(x0 - 1, y0, z0 - 1);
			level.setBlock(b, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 3);
			if (level.getBlockEntity(b) instanceof Container c) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				int slot = 0;
				c.setItem(slot++, new ItemStack(ModItems.SEAM_SILK, 2 + rnd.nextInt(5)));
				if (rnd.nextDouble() < 0.6) {
					c.setItem(slot++, new ItemStack(ModItems.WANDERERS_PAGE));
				}
				if (rnd.nextDouble() < 0.35) {
					c.setItem(slot++, new ItemStack(ModItems.TETHER_FRAGMENT));
				}
				Item[] old = {Items.ECHO_SHARD, Items.AMETHYST_SHARD, Items.BONE, Items.STRING, Items.SOUL_TORCH};
				c.setItem(slot, new ItemStack(old[rnd.nextInt(old.length)], 1 + rnd.nextInt(3)));
				c.setChanged();
			}
		}
		level.setBlock(new BlockPos(x0 + 1, y0 + 2, z0 + 1), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
	}

	/** A narrow causeway of hush brick between two islands, about half of the time. */
	private static void bridge(ServerLevel level, int[] a, int[] b) {
		if ((hash(a[0], b[2], seed()) & 1) == 0) {
			return;
		}
		int steps = (int) Math.ceil(Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[2] - a[2], 2)));
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / Math.max(1, steps);
			BlockPos p = BlockPos.containing(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
			if (level.getBlockState(p).isAir() && i % 7 != 4) {
				level.setBlock(p, ModBlocks.HUSH_BRICKS.defaultBlockState(), 2);
			}
		}
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
