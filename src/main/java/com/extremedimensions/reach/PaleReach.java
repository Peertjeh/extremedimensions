package com.extremedimensions.reach;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Pale Reach (3.39): the End's far islands, from {@code pale_reach_from} blocks out, once the Dragon has fallen.
 * There the Endermen, who were people once and were torn loose when the first wound opened, build anchors to find
 * their way home, and never finish one. The Reach is laid out in squares ({@code pale_anchor_spacing} across); each
 * may hold one Unfinished Anchor ({@link PaleAnchorDesign}) hanging over the void at a spot worked out from the world's
 * seed. When someone comes near, its blocks fly together out of nothing, as the Seam builds itself
 * ({@code pale_anchor_build_seconds}); a beam of light over its fitting shows where it is from far off.
 *
 * <p>The fitting at its heart can be attuned with an Eye of Ender ({@code pale_fitting_cost}): near an attuned fitting
 * ({@code pale_fitting_range}) the Tether drains slower ({@code pale_fitting_drain}), a place to catch one's breath
 * out where the drain runs fastest. The Pale Haulers and the Pale Squalls also belong to the Reach.
 *
 * <p>What is kept with the world ({@link DataStore#reach}): an anchor begun at {@code pb@cx,cz|x,y,z} (where it was set
 * down, so it is built at the same height again after a restart), built {@code pa@cx,cz}, passed over {@code px@cx,cz}
 * (an End City stands there); a fitting {@code pf@dim|x,y,z} and an attuned one {@code pt@dim|x,y,z}.
 */
public final class PaleReach {
	private PaleReach() {}

	/** How near (blocks, across) someone must come to an anchor's spot for it to be built. */
	static final double COME = 176;
	/** How far off its beam is seen. */
	static final double SEEN = 240;
	private static final String SPARK = "minecraft:reverse_portal";

	// ------------------------------------------------------------------ the region

	/** Whether the Reach is awake: switched on, and the Dragon fallen. */
	public static boolean open() {
		return Config.PALE_REACH > 0 && DataStore.sealBroken();
	}

	/** Whether a spot lies in the Reach (far enough from the End's middle). */
	public static boolean far(double x, double z) {
		return Math.hypot(x, z) >= Config.PALE_REACH_FROM;
	}

	/** Whether a player is in the Pale Reach: in the End, far enough out, with the Reach awake. */
	public static boolean in(ServerPlayer p) {
		return open() && Dims.END.equals(Dims.of(p)) && Dims.ours(p) && far(p.getX(), p.getZ());
	}

	// ------------------------------------------------------------------ where the anchors hang

	static long seedOf(long world, int cx, int cz) {
		return world * 31 + cx * 0x632BE59BD9B4E019L + cz * 0x8CB92BA72F3D8DD7L;
	}

	/** Where the anchor of a square hangs (its floor's middle), or null: the square holds none. */
	public static BlockPos site(long world, int cx, int cz) {
		long seed = seedOf(world, cx, cz);
		if (PaleAnchorDesign.unit(seed, 1, cx, cz) >= Config.PALE_ANCHOR_CHANCE) {
			return null;
		}
		int sp = spacing();
		int x = cx * sp + (int) (sp * (0.2 + 0.6 * PaleAnchorDesign.unit(seed, 2, cx, cz)));
		int z = cz * sp + (int) (sp * (0.2 + 0.6 * PaleAnchorDesign.unit(seed, 3, cx, cz)));
		if (Math.hypot(x, z) < Config.PALE_REACH_FROM + 32) {
			return null;
		}
		int y = 58 + (int) (PaleAnchorDesign.unit(seed, 4, cx, cz) * 28);
		return new BlockPos(x, y, z);
	}

	static int spacing() {
		return Math.max(128, Config.PALE_ANCHOR_SPACING);
	}

	static String cell(int cx, int cz) {
		return cx + "," + cz;
	}

	static String spot(Level level, BlockPos p) {
		return Dims.of(level) + "|" + p.getX() + "," + p.getY() + "," + p.getZ();
	}

	// ------------------------------------------------------------------ building

	/** An anchor flying together. */
	static final class Build {
		final String key;
		final ServerLevel level;
		final BlockPos origin;
		final PaleAnchorDesign.Blueprint bp;
		final List<Map.Entry<Long, BlockState>> pieces;
		final List<BlockFlight.Flight> flights = new ArrayList<>();
		final Map<Long, ArrayDeque<Map.Entry<Long, BlockState>>> held = new HashMap<>();
		int next;
		int clock;
		int born;
		double credit;

		Build(String key, ServerLevel level, BlockPos origin, PaleAnchorDesign.Blueprint bp) {
			this.key = key;
			this.level = level;
			this.origin = origin;
			this.bp = bp;
			this.pieces = bp.ordered(origin);
		}
	}

	private static final Map<String, Build> BUILDS = new HashMap<>();

	/** How many anchors are flying together now. */
	public static int building() {
		return BUILDS.size();
	}

	/** Starts an anchor flying together at this spot (its floor's middle), under a key of its own. */
	public static PaleAnchorDesign.Blueprint build(ServerLevel level, String key, BlockPos origin, long seed) {
		Build b = BUILDS.get(key);
		if (b != null) {
			return b.bp;
		}
		b = new Build(key, level, origin, PaleAnchorDesign.design(origin, seed));
		BUILDS.put(key, b);
		WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.end_portal.spawn", "ambient", origin.getX() + 0.5, origin.getY(),
				origin.getZ() + 0.5, 2.5f, 0.6f);
		return b.bp;
	}

	/** Builds an anchor here at once, every block in its place (for the tests and the showcases). */
	public static PaleAnchorDesign.Blueprint buildNow(ServerLevel level, BlockPos origin, long seed) {
		PaleAnchorDesign.Blueprint bp = PaleAnchorDesign.design(origin, seed);
		for (Map.Entry<Long, BlockState> e : bp.ordered(origin)) {
			BlockPos pos = BlockPos.of(e.getKey());
			if (level.getBlockState(pos).isAir()) {
				level.setBlock(pos, e.getValue(), 2);
			}
		}
		stands(level, bp);
		return bp;
	}

	/** A built anchor stands: its barrels are stocked and its fitting known. */
	private static void stands(ServerLevel level, PaleAnchorDesign.Blueprint bp) {
		for (BlockPos c : bp.caches) {
			if (level.getBlockEntity(c) instanceof Container box && box.isEmpty()) {
				Loot.fill(level, c, box, Loot.PALE_ANCHOR);
			}
		}
		if (level.getBlockState(bp.fitting).is(Blocks.LODESTONE)) {
			DataStore.reach().add("pf@" + spot(level, bp.fitting));
			DataStore.markDirty();
			forget();
		}
	}

	/** The anchor of a square, begun if it is not built, under way or passed over (its ground must be loaded). */
	static void ensure(ServerLevel level, int cx, int cz, BlockPos site) {
		String c = cell(cx, cz);
		Set<String> marks = DataStore.reach();
		if (BUILDS.containsKey(c) || marks.contains("pa@" + c) || marks.contains("px@" + c)) {
			return;
		}
		for (int dx = -16; dx <= 16; dx += 16) {
			for (int dz = -16; dz <= 16; dz += 16) {
				if (!Loaded.ready(level, site.offset(dx, 0, dz))) {
					return;
				}
			}
		}
		BlockPos origin = begun(c);
		if (origin == null) {
			// Over an island it stands on the island; over an End City's towers, not at all
			int top = level.getMinY();
			for (int dx = -12; dx <= 12; dx += 3) {
				for (int dz = -12; dz <= 12; dz += 3) {
					top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, site.getX() + dx, site.getZ() + dz));
				}
			}
			int y = site.getY();
			if (top > level.getMinY() + 1) {
				if (top > 150) {
					marks.add("px@" + c);
					DataStore.markDirty();
					return;
				}
				y = Math.max(y, top + 1);
			}
			origin = new BlockPos(site.getX(), y, site.getZ());
			marks.add("pb@" + c + "|" + origin.getX() + "," + origin.getY() + "," + origin.getZ());
			DataStore.markDirty();
		}
		build(level, c, origin, seedOf(level.getSeed(), cx, cz));
	}

	/** Where a square's anchor was begun (kept with the world), or null. */
	private static BlockPos begun(String c) {
		String head = "pb@" + c + "|";
		for (String m : DataStore.reach()) {
			if (m.startsWith(head)) {
				String[] v = m.substring(head.length()).split(",");
				try {
					return new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
				} catch (RuntimeException e) {
					return null;
				}
			}
		}
		return null;
	}

	/** One tick of an anchor flying together; true once it stands whole. */
	private static boolean step(Build b) {
		double rate = b.pieces.size() / Math.max(1.0, Config.PALE_ANCHOR_BUILD_SECONDS * 20.0);
		b.born = 0;
		if (b.next < b.pieces.size()) {
			b.credit += rate;
			while (b.credit >= 1 && b.next < b.pieces.size()) {
				b.credit--;
				launch(b, b.pieces.get(b.next++));
			}
		}
		// Twice a second, what waited for its part of the world goes up where that has come into the world
		if (++b.clock % 10 == 0 && !b.held.isEmpty()) {
			int budget = (int) Math.ceil(rate * 10) + 8;
			Iterator<ArrayDeque<Map.Entry<Long, BlockState>>> it = b.held.values().iterator();
			while (it.hasNext() && budget > 0) {
				ArrayDeque<Map.Entry<Long, BlockState>> q = it.next();
				if (!q.isEmpty() && !Loaded.ready(b.level, BlockPos.of(q.peekFirst().getKey()))) {
					continue;
				}
				while (!q.isEmpty() && budget-- > 0) {
					launch(b, q.pollFirst());
				}
				if (q.isEmpty()) {
					it.remove();
				}
			}
		}
		b.flights.removeIf(BlockFlight::step);
		return b.next >= b.pieces.size() && b.flights.isEmpty() && b.held.isEmpty();
	}

	private static int flyMax() {
		return Math.max(1, Config.ELDER_ARENA_FLY_MAX);
	}

	private static int flightTicks() {
		return Math.max(10, Config.ELDER_ARENA_FLIGHT_TICKS);
	}

	/** A block sets off flying to its place, or (no one near enough to see, or enough in the air) simply appears there. */
	private static void launch(Build b, Map.Entry<Long, BlockState> e) {
		BlockPos pos = BlockPos.of(e.getKey());
		BlockState state = e.getValue();
		if (!Loaded.ready(b.level, pos)) {
			// its part of the world is not loaded: it goes up once it is (the world is never loaded just for it)
			b.held.computeIfAbsent(((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL), k -> new ArrayDeque<>()).add(e);
			return;
		}
		// only ever into open air: never over what stands there
		if (!b.level.getBlockState(pos).isAir()) {
			return;
		}
		if (b.flights.size() < flyMax() && b.born < Math.max(1, (flyMax() + flightTicks() - 1) / flightTicks()) && watched(b.level, pos)) {
			BlockFlight.Flight f = BlockFlight.in(b.level, pos, state, offset(b, pos), flightTicks(), SPARK, 2, new BlockFlight.Landing() {
				@Override
				public boolean fits() {
					return b.level.getBlockState(pos).isAir();
				}

				@Override
				public boolean blocked() {
					return occupied(b.level, pos);
				}

				@Override
				public void land() {
					b.level.setBlock(pos, state, 2);
				}
			});
			if (f != null) {
				b.flights.add(f);
				b.born++;
				return;
			}
		}
		if (!occupied(b.level, pos)) {
			b.level.setBlock(pos, state, 2);
		}
	}

	/** Where a block starts from: the plinth's rise up out of the void, the rest come in from all round. */
	private static double[] offset(Build b, BlockPos pos) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double a = rnd.nextDouble() * Math.PI * 2, d = 9 + rnd.nextDouble() * 6;
		if (pos.getY() < b.origin.getY()) {
			return new double[] {Math.cos(a) * d * 0.5, -8 - rnd.nextDouble() * 8, Math.sin(a) * d * 0.5};
		}
		return new double[] {Math.cos(a) * d, rnd.nextDouble() * 9 - 3, Math.sin(a) * d};
	}

	private static boolean watched(ServerLevel level, BlockPos pos) {
		double r2 = (double) Config.ELDER_ARENA_FLY_RANGE * Config.ELDER_ARENA_FLY_RANGE;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < r2) {
				return true;
			}
		}
		return false;
	}

	/** Whether someone stands where a block goes. */
	static boolean occupied(ServerLevel level, BlockPos pos) {
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.getBoundingBox().intersects(box)) {
				return true;
			}
		}
		return false;
	}

	/** An anchor stands whole: its barrels stocked, its fitting known; everyone near is told, and hears it settle. */
	private static void finish(Build b) {
		stands(b.level, b.bp);
		Set<String> marks = DataStore.reach();
		marks.removeIf(m -> m.startsWith("pb@" + b.key + "|"));
		if (b.key.indexOf('@') < 0) {
			marks.add("pa@" + b.key);
		}
		DataStore.markDirty();
		BlockPos f = b.bp.fitting;
		String dim = b.level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:block.end_portal_frame.fill", "ambient", f.getX() + 0.5, f.getY(), f.getZ() + 0.5, 2f, 0.6f);
		WorldFx.particleIn(b.level, "minecraft:reverse_portal", f.getX() + 0.5, f.getY() + 1, f.getZ() + 0.5, 1.5, 2, 1.5, 0.05, 60);
		for (ServerPlayer p : b.level.players()) {
			if (p.distanceToSqr(f.getX(), f.getY(), f.getZ()) < 96 * 96) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.anchor_stands").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
		}
	}

	// ------------------------------------------------------------------ fittings

	/** The fittings known and those attuned, read from the world's data (and forgotten whenever they change). */
	private static List<String> fittings, attuned;

	private static void forget() {
		fittings = null;
		attuned = null;
	}

	private static List<String> marks(String prefix) {
		List<String> out = new ArrayList<>();
		for (String m : DataStore.reach()) {
			if (m.startsWith(prefix)) {
				out.add(m.substring(prefix.length()));
			}
		}
		return out;
	}

	private static List<String> fittings() {
		if (fittings == null) {
			fittings = marks("pf@");
		}
		return fittings;
	}

	private static List<String> attuned() {
		if (attuned == null) {
			attuned = marks("pt@");
		}
		return attuned;
	}

	/** A spot as kept: its world and its block, or null if this world is not the one. */
	private static double[] where(String spot, String dim) {
		int bar = spot.indexOf('|');
		if (bar < 0 || !spot.substring(0, bar).equals(dim)) {
			return null;
		}
		String[] v = spot.substring(bar + 1).split(",");
		try {
			return new double[] {Integer.parseInt(v[0]) + 0.5, Integer.parseInt(v[1]) + 0.5, Integer.parseInt(v[2]) + 0.5};
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Whether this block is the fitting of an Unfinished Anchor. */
	public static boolean isFitting(Level level, BlockPos pos) {
		return DataStore.reach().contains("pf@" + spot(level, pos)) && level.getBlockState(pos).is(Blocks.LODESTONE);
	}

	/** Whether this fitting is attuned. */
	public static boolean isAttuned(Level level, BlockPos pos) {
		return DataStore.reach().contains("pt@" + spot(level, pos));
	}

	/**
	 * A fitting used: with enough Eyes of Ender in hand ({@code pale_fitting_cost}) it is attuned; otherwise the player is
	 * told what it wants. PASS for any other block.
	 */
	public static InteractionResult onUse(ServerPlayer p, ServerLevel level, BlockPos pos, ItemStack held) {
		if (!isFitting(level, pos)) {
			return InteractionResult.PASS;
		}
		if (isAttuned(level, pos)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.fitting_attuned_already").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return InteractionResult.SUCCESS;
		}
		int cost = Math.max(0, Config.PALE_FITTING_COST);
		if (cost > 0 && !p.isCreative() && (!held.is(Items.ENDER_EYE) || held.getCount() < cost)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.fitting_wants", cost).withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		if (cost > 0 && !p.isCreative()) {
			held.shrink(cost);
		}
		attune(level, pos, p);
		return InteractionResult.SUCCESS;
	}

	/** A fitting is attuned: light runs up it, everyone near is told, and whoever did it earns the advancement. */
	public static void attune(ServerLevel level, BlockPos pos, ServerPlayer by) {
		DataStore.reach().add("pt@" + spot(level, pos));
		DataStore.markDirty();
		forget();
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:block.end_portal_frame.fill", "blocks", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.5f, 1.2f);
		WorldFx.soundIn(dim, "minecraft:block.beacon.activate", "blocks", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.5f, 0.8f);
		WorldFx.particleIn(level, "minecraft:end_rod", pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, 0.4, 3, 0.4, 0.05, 80);
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.fitting_attuned", Math.round((1 - drainEase()) * 100),
						Math.max(8, Config.PALE_FITTING_RANGE)).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		if (by != null) {
			Advancements.grant(by, Advancements.PALE_ATTUNED);
		}
	}

	private static double drainEase() {
		return Math.max(0, Math.min(1, Config.PALE_FITTING_DRAIN));
	}

	/** The Tether's drain for this player, near an attuned fitting ({@code pale_fitting_drain}); 1 elsewhere. */
	public static double drainFactor(ServerPlayer p) {
		if (Config.PALE_REACH <= 0) {
			return 1.0;
		}
		String dim = Dims.of(p);
		double r2 = (double) Math.max(8, Config.PALE_FITTING_RANGE) * Math.max(8, Config.PALE_FITTING_RANGE);
		for (String a : attuned()) {
			double[] at = where(a, dim);
			if (at != null && p.distanceToSqr(at[0], at[1], at[2]) < r2) {
				return drainEase();
			}
		}
		return 1.0;
	}

	// ------------------------------------------------------------------ ticking

	/** Whether each player was held by an attuned fitting at the last look, and when they were last shown a fitting. */
	private static final Map<UUID, Boolean> HELD = new HashMap<>();
	private static final Map<UUID, Long> SHOWN = new HashMap<>();

	public static void tick(MinecraftServer s) {
		if (!BUILDS.isEmpty()) {
			Iterator<Build> it = BUILDS.values().iterator();
			while (it.hasNext()) {
				Build b = it.next();
				if (step(b)) {
					it.remove();
					finish(b);
				}
			}
		}
		int t = s.getTickCount();
		if (t % 4 == 0) {
			lights(s);
		}
		ServerLevel end = s.getLevel(Level.END);
		if (end == null || end.players().isEmpty() || Config.PALE_REACH <= 0) {
			return;
		}
		if (t % 40 == 0 && open()) {
			approach(end);
		}
		if (t % 20 == 10) {
			look(end, t);
		}
	}

	/** Players coming out into the Reach set the anchors near them flying together. */
	private static void approach(ServerLevel end) {
		long world = end.getSeed();
		int sp = spacing();
		for (ServerPlayer p : end.players()) {
			if (p.isSpectator() || !Dims.ours(p) || Math.hypot(p.getX(), p.getZ()) < Config.PALE_REACH_FROM - sp) {
				continue;
			}
			int pcx = Math.floorDiv((int) Math.floor(p.getX()), sp), pcz = Math.floorDiv((int) Math.floor(p.getZ()), sp);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos site = site(world, pcx + dx, pcz + dz);
					if (site == null) {
						continue;
					}
					double ex = site.getX() + 0.5 - p.getX(), ez = site.getZ() + 0.5 - p.getZ();
					if (ex * ex + ez * ez <= COME * COME) {
						ensure(end, pcx + dx, pcz + dz, site);
					}
				}
			}
		}
	}

	/**
	 * Once a second: the page of the Reach for whoever comes out into it, a word when an attuned fitting takes hold of
	 * their Tether (and lets go), and a word on what a fitting wants for whoever stands by one not yet attuned.
	 */
	private static void look(ServerLevel end, int t) {
		for (ServerPlayer p : end.players()) {
			if (p.isSpectator()) {
				continue;
			}
			if (in(p)) {
				PlayerData d = DataStore.get(p.getUUID());
				Journal.unlock(p, d, JournalEntry.PALE_REACH);
			}
			boolean held = drainFactor(p) < 1;
			Boolean was = HELD.put(p.getUUID(), held);
			if (was != null && was != held && DataStore.get(p.getUUID()).inRun) {
				p.sendSystemMessage(Component.translatable(held ? "gui.extremedimensions.reach.fitting_holds" : "gui.extremedimensions.reach.fitting_lets_go")
						.withStyle(held ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
			}
			String dim = Dims.of(p);
			for (String f : fittings()) {
				double[] at = where(f, dim);
				if (at == null || p.distanceToSqr(at[0], at[1], at[2]) > 8 * 8 || attuned().contains(f)) {
					continue;
				}
				Long last = SHOWN.get(p.getUUID());
				if (last == null || t - last >= 1200 || t < last) {
					SHOWN.put(p.getUUID(), (long) t);
					p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.fitting_here", Math.max(0, Config.PALE_FITTING_COST),
							Math.round((1 - drainEase()) * 100)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
				}
				break;
			}
		}
	}

	/** A beam over every fitting, seen from far off: purple and restless, or pale and steady once attuned. */
	private static void lights(MinecraftServer s) {
		List<String> fs = fittings();
		if (fs.isEmpty()) {
			return;
		}
		for (ServerLevel level : s.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			String dim = Dims.of(level), full = level.dimension().identifier().toString();
			for (String f : fs) {
				double[] at = where(f, dim);
				if (at == null) {
					continue;
				}
				boolean seen = false;
				for (ServerPlayer p : level.players()) {
					double dx = p.getX() - at[0], dz = p.getZ() - at[2];
					if (dx * dx + dz * dz < SEEN * SEEN) {
						seen = true;
						break;
					}
				}
				if (!seen) {
					continue;
				}
				boolean on = attuned().contains(f);
				WorldFx.particleFar(full, on ? "minecraft:end_rod" : "minecraft:reverse_portal", at[0], at[1] + 16, at[2], 0.25, 15, 0.25, 0.0,
						on ? 10 : 14, SEEN);
				if (on) {
					WorldFx.particleFar(full, "minecraft:wax_on", at[0], at[1] + 6, at[2], 0.6, 5, 0.6, 0.0, 3, SEEN);
				}
			}
		}
	}

	/** Leaving the world: the blocks in the air land at once; an anchor left half built is built again when someone comes back. */
	public static void clear() {
		for (Build b : BUILDS.values()) {
			for (BlockFlight.Flight f : List.copyOf(b.flights)) {
				BlockFlight.rush(f);
			}
		}
		BUILDS.clear();
		HELD.clear();
		SHOWN.clear();
		forget();
	}
}
