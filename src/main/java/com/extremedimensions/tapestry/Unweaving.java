package com.extremedimensions.tapestry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.guide.Hints;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The Unweaving: a curse set the Starved have filled can be unpicked at the Weaver's Loom. The one who carries it
 * pays in Tetherite Ingots and Tether Cords, and the set fights back: its curses are drawn out of them as knots tied in
 * a ring round the loom, the knots lash at them, and the set's own creatures come out of rifts. Cut every knot before
 * the time runs out and every stack of that set comes off at once (Abyssal curses keep their deeds); fall, leave or run
 * out of time and the knots tie back, with part of the price given back.
 *
 * <p>One unweaving runs at a time (there is one loom), never during the Weaving. Each one after a player's first asks
 * more, and they must wait some days between two. It is not kept over a restart: what it cost is given back.
 */
public final class Unweaving {
	private Unweaving() {}

	/** Players within this many blocks of the loom see the bar; the one unweaving must stay that near. */
	private static final double NEAR = 48;
	/** How near the loom a player must stand to begin. */
	private static final double AT_LOOM = 8;
	/** The sets that can be unwoven, in the screen's order (and the knot block's set property). */
	public static final Penalty.Group[] SETS = {Penalty.Group.BEYOND, Penalty.Group.OVERWORLD, Penalty.Group.EVERYWHERE, Penalty.Group.EXTREME};

	private static final class Knot {
		final BlockPos pos;
		int hits;
		boolean cut;

		Knot(BlockPos pos) {
			this.pos = pos;
		}
	}

	private static final class Run {
		ServerLevel level;
		BlockPos loom;
		String dim;
		UUID player;
		Penalty.Group set;
		int ingots, cords;
		int ticks, length, nextWave, nextLash;
		final List<Knot> knots = new ArrayList<>();
		/** When each player may strike a knot again (ticks of the run). */
		final Map<UUID, Integer> struck = new HashMap<>();
		final Set<UUID> foes = new HashSet<>();
		ServerBossEvent bar;
	}

	private static Run run;

	public static boolean active() {
		return run != null;
	}

	/** Knots still tied (for the tests); -1 when none is under way. */
	public static int knotsLeft() {
		if (run == null) {
			return -1;
		}
		int n = 0;
		for (Knot k : run.knots) {
			n += k.cut ? 0 : 1;
		}
		return n;
	}

	/** Where the knots still tied hang (for the tests). */
	public static List<BlockPos> knots() {
		List<BlockPos> out = new ArrayList<>();
		if (run != null) {
			for (Knot k : run.knots) {
				if (!k.cut) {
					out.add(k.pos);
				}
			}
		}
		return out;
	}

	/** Whether a knot of the unweaving under way hangs here (a knot block anywhere else comes undone). */
	public static boolean holds(ServerLevel level, BlockPos pos) {
		Run r = run;
		if (r == null || r.level != level) {
			return false;
		}
		for (Knot k : r.knots) {
			if (!k.cut && k.pos.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ what a set holds and costs

	/** The set's index in {@link #SETS} (the knot's colour). */
	static int index(Penalty.Group g) {
		for (int i = 0; i < SETS.length; i++) {
			if (SETS[i] == g) {
				return i;
			}
		}
		return 0;
	}

	public static Penalty.Group set(String name) {
		for (Penalty.Group g : SETS) {
			if (g.name().equalsIgnoreCase(name)) {
				return g;
			}
		}
		return null;
	}

	/** Stacks this player carries of a set (its Abyssal curses aside: they have deeds of their own). */
	public static int stacks(PlayerData d, Penalty.Group g) {
		int n = 0;
		for (Penalty p : Penalty.values()) {
			if (p.group == g && !p.abyssal) {
				n += d.owned(p);
			}
		}
		return n;
	}

	/** The most stacks the set holds. */
	public static int most(Penalty.Group g) {
		int n = 0;
		for (Penalty p : Penalty.values()) {
			if (p.group == g && !p.abyssal && p.max > 0) {
				n += p.max;
			}
		}
		return n;
	}

	/** Whether the set is full enough to be unwoven ({@code unweave_fill}). */
	public static boolean full(PlayerData d, Penalty.Group g) {
		int most = most(g);
		if (most <= 0) {
			return false;
		}
		int need = Math.max(1, (int) Math.ceil(most * Math.max(0, Math.min(1, Config.UNWEAVE_FILL))));
		return stacks(d, g) >= need;
	}

	public static int ingots(PlayerData d, Penalty.Group g) {
		return cost(Config.UNWEAVE_INGOTS, d, g);
	}

	public static int cords(PlayerData d, Penalty.Group g) {
		return cost(Config.UNWEAVE_CORDS, d, g);
	}

	/** The base price, times the Extreme factor, and more for each unweaving this player has done. */
	private static int cost(int base, PlayerData d, Penalty.Group g) {
		double c = Math.max(0, base) * (g == Penalty.Group.EXTREME ? Math.max(1, Config.UNWEAVE_EXTREME_FACTOR) : 1)
				* (1 + Math.max(0, Config.UNWEAVE_COST_GROWTH) * Math.max(0, d.unwoven));
		return (int) Math.ceil(c - 1e-9);
	}

	private static long now(ServerPlayer p) {
		return p.level().getServer().overworld().getGameTime();
	}

	/** Game ticks before this player may unweave again (0: now). */
	public static long waitTicks(ServerPlayer p, PlayerData d) {
		return d.unweaveAgain == Long.MIN_VALUE ? 0 : Math.max(0, d.unweaveAgain - now(p));
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer p, Item item, int n) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int t = Math.min(n, s.getCount());
				s.shrink(t);
				n -= t;
			}
		}
		inv.setChanged();
	}

	/** Why this player cannot unweave this set now (a translation key), or null when nothing stands in the way. */
	public static String refuse(ServerPlayer p, PlayerData d, Penalty.Group g) {
		if (Config.UNWEAVING <= 0) {
			return "gui.extremedimensions.unweave.off";
		}
		if (run != null || Weaving.active()) {
			return "gui.extremedimensions.unweave.busy";
		}
		if (!full(d, g)) {
			return "gui.extremedimensions.unweave.not_full";
		}
		if (waitTicks(p, d) > 0) {
			return "gui.extremedimensions.unweave.wait";
		}
		if (!p.isCreative() && (count(p, ModItems.TETHERITE_INGOT) < ingots(d, g) || count(p, ModItems.TETHER_CORD) < cords(d, g))) {
			return "gui.extremedimensions.unweave.cost";
		}
		return null;
	}

	// ------------------------------------------------------------------ the screen

	/** Shows this player the Unweaving (and refreshes it if it is open). */
	public static void open(ServerPlayer p) {
		ServerPlayNetworking.send(p, new ModPackets.UnweavePayload(data(p)));
	}

	/**
	 * The Unweaving screen's data: "ingots carried;cords carried;minutes to wait;busy;" then each set as
	 * "SET,stacks,most,ingots,cords,full" joined by '|'.
	 */
	public static String data(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		long wait = waitTicks(p, d);
		StringBuilder sb = new StringBuilder();
		sb.append(count(p, ModItems.TETHERITE_INGOT)).append(';').append(count(p, ModItems.TETHER_CORD)).append(';')
				.append((wait + 1199) / 1200).append(';').append(run != null || Weaving.active() ? 1 : 0).append(';');
		for (int i = 0; i < SETS.length; i++) {
			Penalty.Group g = SETS[i];
			if (i > 0) {
				sb.append('|');
			}
			sb.append(g.name()).append(',').append(stacks(d, g)).append(',').append(most(g)).append(',').append(ingots(d, g)).append(',')
					.append(cords(d, g)).append(',').append(full(d, g) ? 1 : 0);
		}
		return sb.toString();
	}

	/** The world's loom: where it stands, or null when it is not in this player's world or they are not at it. */
	private static BlockPos loomAt(ServerPlayer p) {
		Object[] l = Tapestry.loom();
		if (l == null || !l[0].equals(p.level().dimension().identifier().toString())) {
			return null;
		}
		BlockPos pos = new BlockPos((int) l[1], (int) l[2], (int) l[3]);
		if (!p.level().getBlockState(pos).is(ModBlocks.WEAVERS_LOOM) || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > AT_LOOM * AT_LOOM) {
			return null;
		}
		return pos;
	}

	/** The player asked from the screen: "" to see it, or a set to unweave (at the loom, if nothing stands in the way). */
	public static void request(ServerPlayer p, String set) {
		if (set == null || set.isEmpty()) {
			open(p);
			return;
		}
		Penalty.Group g = set(set);
		if (g == null) {
			return;
		}
		BlockPos loom = loomAt(p);
		if (loom == null) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.unweave.at_loom"), true);
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		String no = refuse(p, d, g);
		if (no != null) {
			p.sendSystemMessage(Component.translatable(no, (waitTicks(p, d) + 1199) / 1200), true);
			open(p);
			return;
		}
		start(p, loom, g);
	}

	// ------------------------------------------------------------------ the fight

	/** Begins unweaving a set: the price is taken, the knots are tied round the loom. False if none could be tied. */
	public static boolean start(ServerPlayer p, BlockPos loom, Penalty.Group g) {
		if (run != null || !(p.level() instanceof ServerLevel level)) {
			return false;
		}
		PlayerData d = DataStore.get(p.getUUID());
		Run r = new Run();
		r.level = level;
		r.loom = loom.immutable();
		r.dim = level.dimension().identifier().toString();
		r.player = p.getUUID();
		r.set = g;
		r.length = Math.max(30, Config.UNWEAVE_SECONDS) * 20;
		r.nextWave = 80;
		r.nextLash = Math.max(1, Config.UNWEAVE_LASH_SECONDS) * 20;
		int n = Math.max(1, Math.min(12, Config.UNWEAVE_KNOTS));
		double turn = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			BlockPos at = hang(level, loom, turn + i * Math.PI * 2 / n, i);
			if (at != null) {
				level.setBlock(at, ModBlocks.CURSE_KNOT.defaultBlockState().setValue(CurseKnotBlock.SET, index(g)), Block.UPDATE_ALL);
				r.knots.add(new Knot(at));
			}
		}
		if (r.knots.isEmpty()) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.unweave.no_room"), true);
			return false;
		}
		if (!p.isCreative()) {
			r.ingots = ingots(d, g);
			r.cords = cords(d, g);
			take(p, ModItems.TETHERITE_INGOT, r.ingots);
			take(p, ModItems.TETHER_CORD, r.cords);
		}
		d.unweaving = g.name() + "|" + r.ingots + "|" + r.cords;
		DataStore.markDirty();
		r.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("gui.extremedimensions.unweave.bar_title"), colour(g),
				BossEvent.BossBarOverlay.NOTCHED_10);
		r.bar.setDarkenScreen(true);
		run = r;
		double x = loom.getX() + 0.5, y = loom.getY() + 1, z = loom.getZ() + 0.5;
		WorldFx.soundIn(r.dim, "minecraft:block.end_portal.spawn", "hostile", x, y, z, 1.0f, 0.7f);
		WorldFx.particleIn(r.dim, "minecraft:reverse_portal", x, y, z, 1.5, 1.5, 1.5, 0.1, 120);
		for (Knot k : r.knots) {
			thread(r, k.pos, x, y, z);
		}
		for (ServerPlayer q : level.players()) {
			if (near(q, r)) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.unweave.begins", p.getName(), setName(g), r.knots.size())
						.withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
		return true;
	}

	/** Where a knot hangs: in the air round the loom, a little above it, at this bearing. Null if there is no room. */
	private static BlockPos hang(ServerLevel level, BlockPos loom, double ang, int i) {
		for (double d : new double[] {6, 4.5, 7.5, 3}) {
			int x = loom.getX() + (int) Math.round(Math.cos(ang) * d), z = loom.getZ() + (int) Math.round(Math.sin(ang) * d);
			for (int dy = 2 + i % 2; dy <= 5; dy++) {
				BlockPos at = new BlockPos(x, loom.getY() + dy, z);
				if (Loaded.ready(level, at) && level.getBlockState(at).isAir()) {
					return at;
				}
			}
		}
		return null;
	}

	private static BossEvent.BossBarColor colour(Penalty.Group g) {
		return switch (g) {
			case BEYOND -> BossEvent.BossBarColor.RED;
			case OVERWORLD -> BossEvent.BossBarColor.BLUE;
			case EVERYWHERE -> BossEvent.BossBarColor.PINK;
			default -> BossEvent.BossBarColor.PURPLE;
		};
	}

	private static Component setName(Penalty.Group g) {
		return Component.translatable("gui.extremedimensions.curses.tab_" + g.id());
	}

	private static boolean near(ServerPlayer p, Run r) {
		return p.level() == r.level && !p.isSpectator()
				&& p.distanceToSqr(r.loom.getX() + 0.5, r.loom.getY() + 0.5, r.loom.getZ() + 0.5) < NEAR * NEAR;
	}

	/** A line of light from a knot to a point (the loom, or the one it lashes). */
	private static void thread(Run r, BlockPos from, double tx, double ty, double tz) {
		double fx = from.getX() + 0.5, fy = from.getY() + 0.5, fz = from.getZ() + 0.5;
		int steps = (int) Math.max(2, Math.sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy) + (tz - fz) * (tz - fz)) * 2);
		for (int s = 0; s <= steps; s++) {
			double t = (double) s / steps;
			WorldFx.particleIn(r.dim, "minecraft:end_rod", fx + (tx - fx) * t, fy + (ty - fy) * t, fz + (tz - fz) * t, 0.02, 0.02, 0.02, 0.0, 1);
		}
	}

	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 100 == 37) {
			hints(s);
		}
		Run r = run;
		if (r == null) {
			return;
		}
		r.ticks++;
		ServerPlayer p = s.getPlayerList().getPlayer(r.player);
		if (!Loaded.ready(r.level, r.loom) || !r.level.getBlockState(r.loom).is(ModBlocks.WEAVERS_LOOM)) {
			fail(r, p, "gui.extremedimensions.unweave.failed_loom");
			return;
		}
		if (p == null || !p.isAlive()) {
			fail(r, p, "gui.extremedimensions.unweave.failed_fell");
			return;
		}
		if (!near(p, r)) {
			fail(r, p, "gui.extremedimensions.unweave.failed_left");
			return;
		}
		if (r.ticks >= r.length) {
			fail(r, p, "gui.extremedimensions.unweave.failed_time");
			return;
		}
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer q : r.level.players()) {
			if (near(q, r)) {
				near.add(q);
			}
		}
		// Off the bar first: anyone not near, and a player's old self after a respawn or a portal
		for (ServerPlayer q : new ArrayList<>(r.bar.getPlayers())) {
			if (!near.contains(q)) {
				r.bar.removePlayer(q);
			}
		}
		for (ServerPlayer q : near) {
			r.bar.addPlayer(q);
		}
		if (--r.nextWave <= 0) {
			r.nextWave = Math.max(5, Config.UNWEAVE_WAVE_SECONDS) * 20;
			wave(r, p, near.size());
		}
		if (--r.nextLash <= 0) {
			r.nextLash = Math.max(1, Config.UNWEAVE_LASH_SECONDS) * 20;
			lash(r, p);
		}
		r.foes.removeIf(id -> !(r.level.getEntity(id) instanceof Mob m) || !m.isAlive());
		for (UUID id : r.foes) {
			if (r.level.getEntity(id) instanceof Mob m && m.getTarget() == null) {
				m.setTarget(p);
			}
		}
		int left = knotsLeft(), secs = Math.max(0, (r.length - r.ticks) / 20);
		r.bar.setProgress((float) Math.max(0, Math.min(1, 1 - (double) r.ticks / r.length)));
		r.bar.setName(Component.translatable("gui.extremedimensions.unweave.bar", setName(r.set), left, r.knots.size(), secs / 60,
				String.format("%02d", secs % 60)));
		if (r.ticks % 20 == 0) {
			double x = r.loom.getX() + 0.5, y = r.loom.getY() + 1, z = r.loom.getZ() + 0.5;
			for (Knot k : r.knots) {
				if (!k.cut) {
					thread(r, k.pos, x, y, z);
					WorldFx.particleIn(r.dim, "minecraft:witch", k.pos.getX() + 0.5, k.pos.getY() + 0.5, k.pos.getZ() + 0.5, 0.3, 0.3, 0.3, 0.02, 3);
				}
			}
		}
	}

	/** A wave: the set's own creatures, out of rifts torn round the loom, after the one unweaving. */
	private static void wave(Run r, ServerPlayer p, int players) {
		if (Rules.peaceful(r.level)) {
			return;
		}
		int n = Math.max(0, Config.UNWEAVE_WAVE) + Math.max(0, Config.UNWEAVE_WAVE_PER_PLAYER) * Math.max(0, players - 1);
		List<EntityType<? extends Mob>> kinds = kinds(r.set);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < n && !kinds.isEmpty(); i++) {
			BlockPos at = null;
			for (int tries = 0; tries < 12 && at == null; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, dist = tries < 6 ? 11 + rnd.nextDouble() * 6 : 5 + rnd.nextDouble() * 4;
				at = ground(r, r.loom.getX() + (int) Math.round(Math.cos(a) * dist), r.loom.getZ() + (int) Math.round(Math.sin(a) * dist));
			}
			if (at == null) {
				continue;
			}
			Mob m = kinds.get(i % kinds.size()).spawn(r.level, at, EntitySpawnReason.EVENT);
			if (m == null) {
				continue;
			}
			m.setPersistenceRequired();
			m.setTarget(p);
			r.foes.add(m.getUUID());
			WorldFx.particleIn(r.dim, "minecraft:reverse_portal", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0.4, 0.9, 0.4, 0.05, 30);
			WorldFx.soundIn(r.dim, "extremedimensions:lurker_whisper", "hostile", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 1.0f, 0.7f);
		}
	}

	/** The creatures of each set: of the worlds beyond the wound, of home, of anywhere, and the worst of them. */
	private static List<EntityType<? extends Mob>> kinds(Penalty.Group g) {
		return switch (g) {
			case BEYOND -> List.of(EntityType.WITHER_SKELETON, ModEntities.THINNED, EntityType.BLAZE);
			case OVERWORLD -> List.of(EntityType.ZOMBIE, EntityType.SKELETON, ModEntities.LURKER);
			case EXTREME -> List.of(EntityType.WITHER_SKELETON, EntityType.VINDICATOR, ModEntities.LURKER, ModEntities.THINNED);
			default -> List.of(EntityType.SPIDER, ModEntities.THINNED, EntityType.WITCH);
		};
	}

	/** Where a foe can stand at (x, z), near the loom's height: room for it on solid ground. Null when there is none. */
	private static BlockPos ground(Run r, int x, int z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = r.loom.getY() + 6; y >= r.loom.getY() - 12; y--) {
			m.set(x, y, z);
			if (!Loaded.ready(r.level, m)) {
				return null;
			}
			if (r.level.getBlockState(m.below()).isSolid() && r.level.getBlockState(m).isAir() && r.level.getBlockState(m.above()).isAir()) {
				return m.immutable();
			}
		}
		return null;
	}

	/** A knot still tied lashes the one unweaving: a strike, and the sting of its set. */
	private static void lash(Run r, ServerPlayer p) {
		List<Knot> tied = new ArrayList<>();
		for (Knot k : r.knots) {
			if (!k.cut) {
				tied.add(k);
			}
		}
		if (tied.isEmpty() || p.isCreative()) {
			return;
		}
		Knot k = tied.get(ThreadLocalRandom.current().nextInt(tied.size()));
		thread(r, k.pos, p.getX(), p.getY() + 1, p.getZ());
		WorldFx.soundIn(r.dim, "minecraft:entity.evoker.prepare_attack", "hostile", p.getX(), p.getY() + 1, p.getZ(), 0.8f, 1.3f);
		p.hurtServer(r.level, p.damageSources().magic(), (float) Math.max(0, Config.UNWEAVE_LASH_DAMAGE));
		switch (r.set) {
			case BEYOND -> p.igniteForSeconds(3f);
			case OVERWORLD -> CurseEffects.effect(p, "slowness", 4, 1);
			case EXTREME -> CurseEffects.effect(p, "wither", 3, 0);
			default -> CurseEffects.effect(p, "weakness", 4, 0);
		}
	}

	/** A player strikes a block: a knot of the unweaving takes a blow instead of being broken (true), and is cut after enough. */
	public static boolean hit(Player player, ServerLevel level, BlockPos pos) {
		Run r = run;
		if (!level.getBlockState(pos).is(ModBlocks.CURSE_KNOT)) {
			return false;
		}
		if (r == null || r.level != level) {
			// a knot left by an unweaving the server lost: it comes undone at a touch
			level.removeBlock(pos, false);
			return true;
		}
		for (Knot k : r.knots) {
			if (k.cut || !k.pos.equals(pos)) {
				continue;
			}
			if (r.ticks < r.struck.getOrDefault(player.getUUID(), 0)) {
				return true;
			}
			r.struck.put(player.getUUID(), r.ticks + 8);
			k.hits++;
			double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
			WorldFx.particleIn(r.dim, "minecraft:enchanted_hit", x, y, z, 0.4, 0.4, 0.4, 0.2, 10);
			WorldFx.soundIn(r.dim, "minecraft:block.wool.hit", "block", x, y, z, 1.5f, 0.6f + 0.1f * k.hits);
			if (k.hits >= Math.max(1, Config.UNWEAVE_KNOT_HITS)) {
				cut(r, k);
			}
			return true;
		}
		level.removeBlock(pos, false);
		return true;
	}

	/** A knot is cut: it comes undone in a burst; with the last one the set lets go. */
	private static void cut(Run r, Knot k) {
		k.cut = true;
		if (Loaded.ready(r.level, k.pos) && r.level.getBlockState(k.pos).is(ModBlocks.CURSE_KNOT)) {
			r.level.removeBlock(k.pos, false);
		}
		double x = k.pos.getX() + 0.5, y = k.pos.getY() + 0.5, z = k.pos.getZ() + 0.5;
		WorldFx.particleIn(r.dim, "minecraft:soul_fire_flame", x, y, z, 0.3, 0.3, 0.3, 0.05, 20);
		WorldFx.particleIn(r.dim, "extremedimensions:soul_mote", x, y, z, 0.5, 0.5, 0.5, 0.02, 14);
		WorldFx.soundIn(r.dim, "minecraft:block.wool.break", "hostile", x, y, z, 2f, 0.5f);
		WorldFx.soundIn(r.dim, "minecraft:block.amethyst_block.break", "hostile", x, y, z, 1.5f, 0.6f);
		if (knotsLeft() == 0) {
			ServerPlayer p = r.level.getServer().getPlayerList().getPlayer(r.player);
			if (p != null) {
				succeed(r, p);
			} else {
				fail(r, null, "gui.extremedimensions.unweave.failed_fell");
			}
		}
	}

	/** Every knot cut: every stack of the set comes off the one who unwove it, at once. */
	private static void succeed(Run r, ServerPlayer p) {
		end(r);
		PlayerData d = DataStore.get(p.getUUID());
		int lifted = lift(p, d, r.set);
		d.unwoven++;
		d.unweaveAgain = now(p) + Math.max(0, Config.UNWEAVE_COOLDOWN_DAYS) * 24000L;
		d.unweaving = "";
		DataStore.markDirty();
		Advancements.grant(p, Advancements.UNWOVEN);
		WorldFx.title(p, "gui.extremedimensions.unweave.done_title", "light_purple", "gui.extremedimensions.unweave.done_subtitle",
				"gui.extremedimensions.curses.tab_" + r.set.name().toLowerCase(Locale.ROOT));
		double x = r.loom.getX() + 0.5, y = r.loom.getY() + 1, z = r.loom.getZ() + 0.5;
		WorldFx.particleIn(r.dim, "extremedimensions:soul_mote", x, y, z, 3, 3, 3, 0.05, 160);
		WorldFx.particleIn(r.dim, "minecraft:end_rod", x, y + 1, z, 1, 2, 1, 0.08, 50);
		WorldFx.soundIn(r.dim, "minecraft:ui.toast.challenge_complete", "master", x, y, z, 1.0f, 0.9f);
		for (ServerPlayer q : r.level.players()) {
			if (near(q, r)) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.unweave.done", p.getName(), setName(r.set), lifted)
						.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	/** Every stack of the set (Abyssal curses aside) off at once, with the scar a lifting leaves. Returns how many. */
	public static int lift(ServerPlayer p, PlayerData d, Penalty.Group g) {
		int n = 0;
		for (Penalty pen : Penalty.values()) {
			if (pen.group != g || pen.abyssal) {
				continue;
			}
			while (d.owned(pen) > 0 && d.removeStack(pen)) {
				n++;
			}
		}
		if (n > 0) {
			d.lifted += n;
			if (d.scars < 5) {
				d.scars++;
				Journal.unlock(p, d, JournalEntry.WOVEN);
			}
			Journal.unlock(p, d, JournalEntry.MENDING);
			Advancements.grant(p, Advancements.MENDED);
		}
		CurseEffects.applyEffects(p, d);
		Sync.state(p, d);
		Sync.curses(p, d, false);
		DataStore.markDirty();
		return n;
	}

	/** It failed: the knots tie back into the one who carries them, and part of the price comes back. */
	private static void fail(Run r, ServerPlayer p, String reason) {
		end(r);
		int ingots = (int) Math.floor(r.ingots * Math.max(0, Math.min(1, Config.UNWEAVE_REFUND)));
		int cords = (int) Math.floor(r.cords * Math.max(0, Math.min(1, Config.UNWEAVE_REFUND)));
		PlayerData d = DataStore.get(r.player);
		d.unweaving = "";
		DataStore.markDirty();
		giveBack(r, p, ingots, cords);
		double x = r.loom.getX() + 0.5, y = r.loom.getY() + 1, z = r.loom.getZ() + 0.5;
		WorldFx.particleIn(r.dim, "minecraft:large_smoke", x, y, z, 1, 1, 1, 0.03, 50);
		WorldFx.soundIn(r.dim, "minecraft:block.beacon.deactivate", "master", x, y, z, 1.0f, 0.6f);
		for (ServerPlayer q : r.level.players()) {
			if (near(q, r) || q == p) {
				q.sendSystemMessage(Component.translatable(reason, setName(r.set), ingots, cords).withStyle(ChatFormatting.RED));
			}
		}
	}

	/** What comes back: into the bag of the one unweaving if they stand near and alive, else at the loom. */
	private static void giveBack(Run r, ServerPlayer p, int ingots, int cords) {
		List<ItemStack> back = new ArrayList<>();
		if (ingots > 0) {
			back.add(new ItemStack(ModItems.TETHERITE_INGOT, ingots));
		}
		if (cords > 0) {
			back.add(new ItemStack(ModItems.TETHER_CORD, cords));
		}
		for (ItemStack s : back) {
			if (p != null && p.isAlive() && near(p, r)) {
				WorldFx.give(p, s);
			} else if (Loaded.ready(r.level, r.loom)) {
				Block.popResource(r.level, r.loom.above(), s);
			}
		}
	}

	/** However it ended: the knots and the bar go, and the set's creatures fade back into their rifts. */
	private static void end(Run r) {
		run = null;
		r.bar.removeAllPlayers();
		for (Knot k : r.knots) {
			if (!k.cut && Loaded.ready(r.level, k.pos) && r.level.getBlockState(k.pos).is(ModBlocks.CURSE_KNOT)) {
				r.level.removeBlock(k.pos, false);
			}
		}
		for (UUID id : r.foes) {
			Entity e = r.level.getEntity(id);
			if (e instanceof Mob m && m.isAlive()) {
				WorldFx.particleIn(r.dim, "minecraft:reverse_portal", m.getX(), m.getY() + 1, m.getZ(), 0.3, 0.8, 0.3, 0.05, 20);
				m.discard();
			}
		}
	}

	/** On joining: an unweaving the server lost (a restart, a crash) gives back all it cost. */
	public static void onJoin(ServerPlayer p, PlayerData d) {
		if (d.unweaving == null || d.unweaving.isEmpty() || run != null && run.player.equals(p.getUUID())) {
			return;
		}
		String[] f = d.unweaving.split("\\|");
		d.unweaving = "";
		DataStore.markDirty();
		int ingots = f.length > 1 ? num(f[1]) : 0, cords = f.length > 2 ? num(f[2]) : 0;
		if (ingots > 0) {
			WorldFx.give(p, new ItemStack(ModItems.TETHERITE_INGOT, ingots));
		}
		if (cords > 0) {
			WorldFx.give(p, new ItemStack(ModItems.TETHER_CORD, cords));
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.unweave.restored", ingots, cords).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static int num(String s) {
		try {
			return Math.max(0, Integer.parseInt(s));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Now and then: a player whose set is full enough learns that the loom can unweave it (the hint, once). */
	private static void hints(MinecraftServer s) {
		if (Config.UNWEAVING <= 0) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!Dims.ours(p)) {
				continue;
			}
			PlayerData d = DataStore.get(p.getUUID());
			if (d.hints.contains(Hints.Hint.UNWEAVING.key())) {
				continue;
			}
			for (Penalty.Group g : SETS) {
				if (full(d, g)) {
					Hints.meet(p, Hints.Hint.UNWEAVING);
					break;
				}
			}
		}
	}

	/** The server stops: an unweaving under way is let go (its knots come undone; the price is given back on joining). */
	public static void clear() {
		Run r = run;
		run = null;
		if (r != null) {
			r.bar.removeAllPlayers();
			for (Knot k : r.knots) {
				if (!k.cut && Loaded.ready(r.level, k.pos) && r.level.getBlockState(k.pos).is(ModBlocks.CURSE_KNOT)) {
					r.level.removeBlock(k.pos, false);
				}
			}
		}
	}

	/** For the tests: cut every knot still tied, as the blows would. */
	public static void cutAll() {
		Run r = run;
		if (r == null) {
			return;
		}
		for (Knot k : new ArrayList<>(r.knots)) {
			if (!k.cut && run == r) {
				cut(r, k);
			}
		}
	}

	/** For the tests: let the time run out. */
	public static void timeOut() {
		if (run != null) {
			run.ticks = run.length;
		}
	}
}
