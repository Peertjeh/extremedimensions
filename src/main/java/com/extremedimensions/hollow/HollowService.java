package com.extremedimensions.hollow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.anchor.SoulWards;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tribe.BarterService;
import com.extremedimensions.tribe.RaidService;
import com.extremedimensions.tribe.Standing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;

/**
 * Everything that lives around a Lurker hoard:
 *
 * <ul>
 *   <li><b>Hoards</b>: hidden nests with a barrel of stolen things. A Lurker that escapes with your loot carries it
 *       to your hoard (and builds one if there is none); old hoards also lie in dark forests, pale gardens and soul
 *       sand valleys. A column of ink rises from every hoard, and it whispers.</li>
 *   <li><b>Hollowlings</b>: the young. They play around the hoard, freeze when watched, and steal small things
 *       when you look away. Near soul light, a Tether Fragment may earn one's trust: it follows you, warns you of
 *       danger, chases Lurkers off and now and then brings you something it found.</li>
 *   <li><b>The Keeper</b>: open or break a hoard, or hurt its young, and the Keeper comes for you.</li>
 * </ul>
 *
 * Its creatures are never saved: anything not driven from here dissolves, and is respawned when needed.
 */
public final class HollowService {
	private HollowService() {}

	enum Mode { IDLE, FROZEN, SNEAK, CARRY, FLEE, COMPANION, BARTER }

	static final class Little {
		Mode mode = Mode.IDLE;
		String hoard;
		UUID owner;
		ItemStack carried = ItemStack.EMPTY;
		int ticks;
		int unseen;
		int stared;
		int wander;
		/** Called by the Keeper or the Matriarch: not one of the hoard's own young. */
		boolean brood;
		/** Out of a raided hoard: being watched does not stop it, and it steals from anyone. */
		boolean bold;
		/** The player it barters with. */
		UUID trader;
	}

	static final class Guard {
		String hoard;
		UUID target;
		ServerBossEvent bar;
		int ticks;
		int nextTrick = 120;
	}

	private static final Map<UUID, Little> LITTLES = new HashMap<>();
	private static final Map<UUID, Guard> KEEPERS = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	/** Owner UUID to companion entity UUID. */
	private static final Map<UUID, UUID> COMPANIONS = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return LITTLES.containsKey(e.getUUID()) || KEEPERS.containsKey(e.getUUID());
	}

	public static void clear() {
		for (Guard g : KEEPERS.values()) {
			g.bar.removeAllPlayers();
		}
		LITTLES.clear();
		KEEPERS.clear();
		LEVELS.clear();
		COMPANIONS.clear();
	}

	// ------------------------------------------------------------------ hoards

	public static Hoard hoardAt(String dim, BlockPos pos) {
		for (Hoard h : DataStore.hoards()) {
			if (h.dim.equals(dim) && h.x == pos.getX() && h.y == pos.getY() && h.z == pos.getZ()) {
				return h;
			}
		}
		return null;
	}

	public static Hoard hoardByKey(String key) {
		return byKey(key);
	}

	private static Hoard byKey(String key) {
		for (Hoard h : DataStore.hoards()) {
			if (h.key().equals(key)) {
				return h;
			}
		}
		return null;
	}

	private static ServerLevel levelOf(String dim) {
		MinecraftServer s = WorldFx.server();
		if (s == null) {
			return null;
		}
		return switch (dim) {
			case Dims.NETHER -> s.getLevel(Level.NETHER);
			case Dims.END -> s.getLevel(Level.END);
			default -> s.overworld();
		};
	}

	/**
	 * The hoard a Lurker carries this player's loot to: their own hoard nearby, or a new one 40-70 blocks further
	 * on, away from the player. Null where no hoard fits (the End, or no ground).
	 */
	public static Hoard hoardFor(ServerPlayer p, Vec3 from) {
		String dim = Dims.of(p);
		if (Dims.END.equals(dim)) {
			return null;
		}
		String uid = p.getStringUUID();
		for (Hoard h : DataStore.hoards()) {
			if (h.dim.equals(dim) && uid.equals(h.owner) && h.distSq(from.x, from.y, from.z) < 160 * 160) {
				return h;
			}
		}
		Vec3 away = from.subtract(p.position());
		double base = away.lengthSqr() < 0.01 ? ThreadLocalRandom.current().nextDouble() * Math.PI * 2 : Math.atan2(away.z, away.x);
		return create((ServerLevel) p.level(), from, base, 1.2, uid);
	}

	/** Builds a new hoard 40-70 blocks from {@code from}, roughly in direction {@code angle}. */
	private static Hoard create(ServerLevel level, Vec3 from, double angle, double spread, String owner) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		boolean nether = Dims.NETHER.equals(Dims.of(level));
		for (int i = 0; i < 16; i++) {
			double a = angle + (rnd.nextDouble() - 0.5) * spread;
			double dist = 40 + rnd.nextDouble() * 30;
			int x = (int) Math.floor(from.x + Math.cos(a) * dist);
			int z = (int) Math.floor(from.z + Math.sin(a) * dist);
			if (!level.isLoaded(new BlockPos(x, (int) from.y, z))) {
				continue;
			}
			BlockPos spot = nether ? standable(level, x, (int) from.y + 10, z, 30) : surface(level, x, z);
			if (spot == null) {
				continue;
			}
			Hoard h = new Hoard(Dims.of(level), spot.getX(), spot.getY(), spot.getZ(), owner);
			build(level, spot, nether);
			DataStore.hoards().add(h);
			DataStore.markDirty();
			return h;
		}
		return null;
	}

	private static BlockPos surface(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		BlockState below = level.getBlockState(feet.below());
		if (below.liquid() || below.isAir() || !level.getBlockState(feet).canBeReplaced()) {
			return null;
		}
		return feet;
	}

	private static BlockPos standable(ServerLevel level, int x, int top, int z, int depth) {
		for (int y = top; y > top - depth; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
					&& !level.getBlockState(feet.below()).isAir() && !level.getBlockState(feet.below()).liquid()) {
				return feet;
			}
		}
		return null;
	}

	/** Can the nest floor be laid over this block? Only natural ground, never someone's build. */
	private static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(Blocks.NETHERRACK) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL)
				|| s.is(Blocks.CRIMSON_NYLIUM) || s.is(Blocks.WARPED_NYLIUM) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL)
				|| s.is(Blocks.STONE) || s.is(Blocks.BASALT);
	}

	/**
	 * The nest: a floor of trampled mud (or soul soil), a barrel in the middle, crooked posts, cobwebs, scattered
	 * bone, a skull and black candles nobody lights. Only placed on natural ground and into empty space.
	 */
	private static void build(ServerLevel level, BlockPos c, boolean nether) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Block[] floor = nether ? new Block[] {Blocks.SOUL_SOIL, Blocks.SOUL_SAND, Blocks.SOUL_SOIL}
				: new Block[] {Blocks.MUD, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT};
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx * dx + dz * dz > 10 || (dx * dx + dz * dz > 6 && rnd.nextInt(3) == 0)) {
					continue;
				}
				for (int dy = 2; dy >= -2; dy--) {
					BlockPos g = c.offset(dx, dy - 1, dz);
					BlockState gs = level.getBlockState(g);
					BlockState above = level.getBlockState(g.above());
					if (!gs.isAir() && above.canBeReplaced()) {
						if (natural(gs)) {
							level.setBlock(g, floor[rnd.nextInt(floor.length)].defaultBlockState(), 3);
						}
						if (!above.isAir() && !above.liquid()) {
							level.setBlock(g.above(), Blocks.AIR.defaultBlockState(), 3);
						}
						break;
					}
				}
			}
		}
		level.setBlock(c, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 3);
		Block post = nether ? Blocks.BONE_BLOCK : Blocks.DARK_OAK_FENCE;
		for (int[] o : new int[][] {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
			if (rnd.nextInt(4) == 0) {
				continue;
			}
			BlockPos base = groundAt(level, c.offset(o[0], 0, o[1]));
			if (base == null) {
				continue;
			}
			int h = 1 + rnd.nextInt(2);
			for (int k = 0; k < h; k++) {
				place(level, base.above(k), post.defaultBlockState());
			}
			if (rnd.nextBoolean()) {
				place(level, base.above(h), Blocks.COBWEB.defaultBlockState());
			}
		}
		decorate(level, c, Blocks.COBWEB.defaultBlockState(), 2 + rnd.nextInt(2));
		decorate(level, c, Blocks.BONE_BLOCK.defaultBlockState(), 1 + rnd.nextInt(2));
		decorate(level, c, (nether ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState(), 1);
		BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", "black_candle")).ifPresent(candle -> {
			BlockState cs = candle.value().defaultBlockState();
			if (cs.hasProperty(CandleBlock.CANDLES)) {
				cs = cs.setValue(CandleBlock.CANDLES, 1 + rnd.nextInt(3));
			}
			decorate(level, c, cs, 2);
		});
	}

	private static BlockPos groundAt(ServerLevel level, BlockPos p) {
		for (int dy = 2; dy >= -2; dy--) {
			BlockPos q = p.offset(0, dy, 0);
			if (level.getBlockState(q).canBeReplaced() && !level.getBlockState(q.below()).canBeReplaced()) {
				return q;
			}
		}
		return null;
	}

	private static void decorate(ServerLevel level, BlockPos c, BlockState state, int count) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < count * 4 && count > 0; i++) {
			int dx = rnd.nextInt(-2, 3);
			int dz = rnd.nextInt(-2, 3);
			if (dx == 0 && dz == 0) {
				continue;
			}
			BlockPos g = groundAt(level, c.offset(dx, 0, dz));
			if (g != null && level.getBlockState(g).isAir()) {
				level.setBlock(g, state, 3);
				count--;
			}
		}
	}

	private static void place(ServerLevel level, BlockPos p, BlockState s) {
		if (level.getBlockState(p).canBeReplaced()) {
			level.setBlock(p, s, 3);
		}
	}

	/** Puts things into a hoard's barrel; anything that does not fit is dropped beside it. */
	public static void deposit(Hoard h, List<ItemStack> items) {
		ServerLevel level = levelOf(h.dim);
		if (level == null || items.isEmpty()) {
			return;
		}
		BlockPos pos = new BlockPos(h.x, h.y, h.z);
		if (!level.getBlockState(pos).is(Blocks.BARREL)) {
			build(level, pos, Dims.NETHER.equals(h.dim));
		}
		if (!(level.getBlockEntity(pos) instanceof Container c)) {
			return;
		}
		for (ItemStack stack : items) {
			ItemStack rest = stack.copy();
			for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
				ItemStack in = c.getItem(i);
				if (in.isEmpty()) {
					c.setItem(i, rest);
					rest = ItemStack.EMPTY;
				} else if (ItemStack.isSameItemSameComponents(in, rest) && in.getCount() < in.getMaxStackSize()) {
					int move = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
					in.grow(move);
					rest.shrink(move);
				}
			}
			if (!rest.isEmpty()) {
				Block.popResource(level, pos.above(), rest);
			}
		}
		c.setChanged();
	}

	/** Loot a Lurker carries home: the item, and experience as bottles. */
	public static void stash(Hoard h, ItemStack stack, int levels) {
		List<ItemStack> items = new ArrayList<>();
		if (!stack.isEmpty()) {
			items.add(stack);
		}
		if (levels > 0) {
			items.add(new ItemStack(Items.EXPERIENCE_BOTTLE, Math.min(64, levels * 3)));
		}
		deposit(h, items);
	}

	/** Trinkets in an old hoard found in the wild. */
	private static List<ItemStack> trinkets() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<ItemStack> out = new ArrayList<>();
		Item[] small = {Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.STRING, Items.BONE, Items.FEATHER, Items.AMETHYST_SHARD,
				Items.TORCH, Items.SOUL_TORCH, Items.FLINT, Items.LEATHER, Items.COPPER_INGOT};
		for (int i = 0; i < 4 + rnd.nextInt(4); i++) {
			out.add(new ItemStack(small[rnd.nextInt(small.length)], 1 + rnd.nextInt(6)));
		}
		Item[] rare = {Items.COMPASS, Items.CLOCK, Items.NAME_TAG, Items.LEAD, Items.SADDLE, Items.SPYGLASS, Items.EMERALD,
				Items.ECHO_SHARD, Items.MUSIC_DISC_13, Items.GOLDEN_APPLE};
		for (int i = 0; i < 1 + rnd.nextInt(2); i++) {
			out.add(new ItemStack(rare[rnd.nextInt(rare.length)]));
		}
		if (rnd.nextDouble() < 0.6) {
			out.add(new ItemStack(ModItems.WANDERERS_PAGE));
		}
		if (rnd.nextDouble() < 0.4) {
			out.add(new ItemStack(ModItems.TETHER_FRAGMENT));
		}
		return out;
	}

	/** Opened by a player: the hoard is found, and its Keeper wakes. */
	public static void onHoardOpened(ServerPlayer p, Hoard h) {
		PlayerData d = DataStore.get(p.getUUID());
		Advancements.grant(p, Advancements.HOARD_FOUND);
		Journal.unlock(p, d, JournalEntry.HOLLOWS);
		if (RaidService.start(p, h)) {
			return;
		}
		if (p.getStringUUID().equals(h.owner) && !h.keeperSlain && Standing.atLeast(p, Standing.Level.TRUSTED)) {
			// The tribe gives back what was taken from a friend
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tribe.own_hoard"), true);
			return;
		}
		wake(h, p);
	}

	public static void onBroken(Level level, BlockPos pos, ServerPlayer p) {
		Hoard h = hoardAt(Dims.of(level), pos);
		if (h == null) {
			return;
		}
		abandon(h);
		if (p != null) {
			if (!p.getStringUUID().equals(h.owner)) {
				Standing.change(p, Config.TRIBE_REP_HOARD_BROKEN, "gui.extremedimensions.tribe.why.hoard");
			}
			Advancements.grant(p, Advancements.HOARD_FOUND);
			Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.HOLLOWS);
			wake(h, p);
		}
	}

	// ------------------------------------------------------------------ ticking

	private static int slow = 0;

	public static void tick(MinecraftServer s) {
		driveLittles();
		driveKeepers();
		if (++slow < 20) {
			return;
		}
		slow = 0;
		boolean wildCheck = s.getTickCount() % 600 < 20;
		List<Hoard> wrecked = new ArrayList<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			// No nests in another mod's world, and a companion waits for its player to come back
			if (p.isSpectator() || !Dims.ours(p)) {
				continue;
			}
			PlayerData d = DataStore.get(p.getUUID());
			companionUpkeep(p, d);
			String dim = Dims.of(p);
			for (Hoard h : DataStore.hoards()) {
				if (!h.dim.equals(dim)) {
					continue;
				}
				double d2 = h.distSq(p.getX(), p.getY(), p.getZ());
				if (d2 < 96 * 96 && !intact(h)) {
					// Its barrel is gone (broken, blown up, burnt): the nest is no more
					if (!wrecked.contains(h)) {
						wrecked.add(h);
					}
					continue;
				}
				if (d2 < 96 * 96) {
					ambience(h, d2);
				}
				if (d2 < 44 * 44) {
					populate(h);
				}
			}
			if (wildCheck && Config.HOARD_WILD_CHANCE > 0) {
				wild(p);
			}
		}
		wrecked.forEach(HollowService::abandon);
		cull(s);
	}

	private static final Map<String, Long> WHISPERED = new HashMap<>();

	/** Ink rising from the hoard, visible from afar, and whispers up close. */
	private static void ambience(Hoard h, double d2) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		WorldFx.particleIn(Dims.fullIdOf(h.dim), "extremedimensions:lurker_wisp", h.x + 0.5, h.y + 1.6, h.z + 0.5, 0.25, 1.2, 0.25, 0.0, 3);
		// A faint whisper now and then, never overlapping itself, and once per hoard however many players are near
		long now = System.currentTimeMillis();
		Long last = WHISPERED.get(h.key());
		if (d2 < 12 * 12 && (last == null || now - last > 20_000) && rnd.nextInt(4) == 0) {
			WHISPERED.put(h.key(), now);
			WorldFx.soundIn(Dims.fullIdOf(h.dim), "extremedimensions:hoard_ambient", "ambient", h.x + 0.5, h.y, h.z + 0.5, 0.3f, 1f);
		}
	}

	/** Keeps a few Hollowlings around a hoard someone is near (fewer for a while after some were killed). */
	private static void populate(Hoard h) {
		int count = 0;
		for (Little st : LITTLES.values()) {
			if (h.key().equals(st.hoard) && !st.brood && st.mode != Mode.COMPANION) {
				count++;
			}
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (count >= youngAllowed(h, System.currentTimeMillis()) || rnd.nextInt(3) != 0) {
			return;
		}
		ServerLevel level = levelOf(h.dim);
		BlockPos spot = groundAt(level, new BlockPos(h.x + rnd.nextInt(-3, 4), h.y, h.z + rnd.nextInt(-3, 4)));
		if (spot == null) {
			return;
		}
		Little st = new Little();
		st.hoard = h.key();
		spawnLittle(level, spot, st);
	}

	/** Whether a hoard's barrel still stands (taken as standing where its chunk is not loaded: nothing can have changed). */
	public static boolean intact(Hoard h) {
		ServerLevel level = levelOf(h.dim);
		BlockPos core = new BlockPos(h.x, h.y, h.z);
		return level == null || !level.isLoaded(core) || level.getBlockState(core).is(Blocks.BARREL);
	}

	/** The nest is no more: the hoard is struck off and its young scatter into the dark (a companion stays). */
	private static void abandon(Hoard h) {
		DataStore.hoards().remove(h);
		DataStore.markDirty();
		List<Mob> young = new ArrayList<>();
		for (Map.Entry<UUID, Little> e : LITTLES.entrySet()) {
			if (h.key().equals(e.getValue().hoard) && e.getValue().mode != Mode.COMPANION) {
				ServerLevel level = LEVELS.get(e.getKey());
				if (level != null && level.getEntity(e.getKey()) instanceof Mob m) {
					young.add(m);
				}
			}
		}
		young.forEach(HollowService::fade);
	}

	/**
	 * One of a hoard's young was killed. It is replaced only after {@code hollowling_respawn_seconds}, and several
	 * killed come back one at a time, each a full wait after the last.
	 */
	public static void youngLost(Hoard h, long now) {
		long wait = Math.max(0, Config.HOLLOWLING_RESPAWN_SECONDS) * 1000L;
		if (h.lost <= 0 || h.youngBack < now) {
			h.youngBack = now + wait;
		}
		h.lost = Math.min(Math.max(1, Config.HOLLOWLINGS_PER_HOARD), h.lost + 1);
		DataStore.markDirty();
	}

	/** How many young a hoard may have now: all of them, less those killed and not yet replaced. */
	public static int youngAllowed(Hoard h, long now) {
		long wait = Math.max(1, Math.max(0, Config.HOLLOWLING_RESPAWN_SECONDS) * 1000L);
		while (h.lost > 0 && now >= h.youngBack) {
			h.lost--;
			h.youngBack += wait;
			DataStore.markDirty();
		}
		return Math.max(0, Config.HOLLOWLINGS_PER_HOARD - h.lost);
	}

	private static Hollowling spawnLittle(ServerLevel level, BlockPos pos, Little st) {
		Hollowling l = ModEntities.HOLLOWLING.spawn(level, pos, EntitySpawnReason.EVENT);
		if (l == null) {
			return null;
		}
		l.setPersistenceRequired();
		LITTLES.put(l.getUUID(), st);
		LEVELS.put(l.getUUID(), level);
		return l;
	}

	/** Old hoards lie in the darkest places: dark forests, pale gardens and soul sand valleys. */
	private static void wild(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		var biome = level.getBiome(p.blockPosition());
		if (!(biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN) || biome.is(Biomes.SOUL_SAND_VALLEY))) {
			return;
		}
		for (Hoard h : DataStore.hoards()) {
			if (h.dim.equals(Dims.of(p)) && h.distSq(p.getX(), p.getY(), p.getZ()) < 256 * 256) {
				return;
			}
		}
		if (ThreadLocalRandom.current().nextDouble() >= Config.HOARD_WILD_CHANCE) {
			return;
		}
		// Somewhere out of sight, behind the player
		double behind = Math.toRadians(p.getYRot()) - Math.PI / 2;
		Hoard h = create(level, p.position(), behind, 2.0, "");
		if (h != null) {
			deposit(h, trinkets());
		}
	}

	/** Hollowlings far from every player fade; they come back when someone returns. */
	private static void cull(MinecraftServer s) {
		List<Mob> gone = new ArrayList<>();
		for (Map.Entry<UUID, Little> e : LITTLES.entrySet()) {
			if (e.getValue().mode == Mode.COMPANION) {
				continue;
			}
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (ent instanceof Mob m && level.getNearestPlayer(m, 80) == null) {
				gone.add(m);
			}
		}
		for (Mob m : gone) {
			if (m instanceof Hollowling h) {
				Little st = LITTLES.get(h.getUUID());
				if (st != null && !st.carried.isEmpty() && st.hoard != null) {
					Hoard home = byKey(st.hoard);
					if (home != null) {
						deposit(home, List.of(st.carried));
					}
				}
			}
			LITTLES.remove(m.getUUID());
			LEVELS.remove(m.getUUID());
			m.discard();
		}
	}

	// ------------------------------------------------------------------ hollowlings

	private static void driveLittles() {
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, Little>> it = LITTLES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Little> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof Hollowling h) || !h.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			Little st = e.getValue();
			st.ticks++;
			if (st.mode == Mode.COMPANION) {
				companion(h, st, later);
			} else {
				little(h, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	private static boolean seen(ServerPlayer p, Entity e, double cone) {
		Vec3 to = e.getEyePosition().subtract(p.getEyePosition()).normalize();
		return p.getViewVector(1.0f).dot(to) > cone && p.hasLineOfSight(e);
	}

	private static void giggle(Entity e) {
		WorldFx.soundIn(e.level().dimension().identifier().toString(), "extremedimensions:hollowling_giggle", "neutral", e.getX(), e.getY() + 0.8, e.getZ(),
				0.9f, (float) (0.9 + ThreadLocalRandom.current().nextDouble() * 0.3));
	}

	/** A wild Hollowling: plays near the hoard, freezes when watched, sneaks up and steals when you look away. */
	private static void little(Hollowling h, Little st, List<Runnable> later) {
		Hoard home = byKey(st.hoard);
		if (home == null) {
			later.add(() -> fade(h));
			return;
		}
		Vec3 nest = new Vec3(home.x + 0.5, home.y, home.z + 0.5);
		ServerPlayer p = (ServerPlayer) h.level().getNearestPlayer(h, 20);
		if (p != null && (p.isSpectator() || p.isCreative())) {
			p = null;
		}
		switch (st.mode) {
			case IDLE -> {
				h.setLurkerPose(Lurker.POSE_WATCH);
				if (--st.wander <= 0) {
					ThreadLocalRandom rnd = ThreadLocalRandom.current();
					st.wander = 60 + rnd.nextInt(80);
					h.getNavigation().moveTo(nest.x + rnd.nextInt(-7, 8), nest.y, nest.z + rnd.nextInt(-7, 8), 0.8);
				}
				if (st.bold && p != null && hasLoot(p)) {
					st.mode = Mode.SNEAK;
					st.ticks = 0;
				} else if (p != null && h.distanceToSqr(p) < 14 * 14) {
					if (seen(p, h, 0.93)) {
						freeze(h, st);
					} else if (st.ticks % 40 == 0 && ThreadLocalRandom.current().nextInt(3) < Standing.theftOdds(p) && hasLoot(p)) {
						st.mode = Mode.SNEAK;
						st.ticks = 0;
					}
				}
			}
			case FROZEN -> {
				h.getNavigation().stop();
				h.setLurkerPose(Lurker.POSE_WATCH);
				if (p == null) {
					st.mode = Mode.IDLE;
					break;
				}
				h.getLookControl().setLookAt(p, 30, 30);
				if (seen(p, h, 0.93)) {
					st.unseen = 0;
					if (++st.stared > 60) {
						giggle(h);
						flee(h, st);
					}
				} else if (++st.unseen > 20) {
					st.mode = hasLoot(p) && Standing.theftOdds(p) > 0 ? Mode.SNEAK : Mode.IDLE;
					st.ticks = 0;
				}
			}
			case SNEAK -> {
				h.setLurkerPose(Lurker.POSE_STALK);
				if (p == null || st.ticks > 400) {
					st.mode = Mode.IDLE;
					break;
				}
				if (!st.bold && seen(p, h, 0.93)) {
					freeze(h, st);
					break;
				}
				if (st.ticks % 5 == 0) {
					h.getNavigation().moveTo(p, 1.15);
				}
				if (h.distanceToSqr(p) < 1.9 * 1.9) {
					steal(h, st, p);
				}
			}
			case CARRY -> {
				h.setLurkerPose(Lurker.POSE_RUN);
				if (st.ticks % 10 == 0) {
					h.getNavigation().moveTo(nest.x, nest.y, nest.z, 1.35);
				}
				if (h.position().distanceToSqr(nest) < 2.5 * 2.5 || st.ticks > 400) {
					deposit(home, List.of(st.carried));
					st.carried = ItemStack.EMPTY;
					st.mode = Mode.IDLE;
					st.ticks = 0;
				}
			}
			case BARTER -> {
				h.getNavigation().stop();
				h.setLurkerPose(Lurker.POSE_WATCH);
				ServerPlayer t = st.trader == null ? null : WorldFx.server().getPlayerList().getPlayer(st.trader);
				if (t == null || t.level() != h.level() || h.distanceToSqr(t) > BarterService.REACH * BarterService.REACH || st.ticks > 1200) {
					st.mode = Mode.IDLE;
					st.trader = null;
					st.ticks = 0;
					break;
				}
				h.getLookControl().setLookAt(t, 30, 30);
			}
			case FLEE -> {
				h.setLurkerPose(Lurker.POSE_RUN);
				if (st.ticks % 10 == 0) {
					Vec3 away = p == null ? nest : h.position().add(h.position().subtract(p.position()).normalize().scale(10));
					h.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
				}
				if (st.ticks > 70) {
					st.mode = st.carried.isEmpty() ? Mode.IDLE : Mode.CARRY;
					st.ticks = 0;
				}
			}
			default -> { }
		}
	}

	private static void freeze(Hollowling h, Little st) {
		st.mode = Mode.FROZEN;
		st.stared = 0;
		st.unseen = 0;
		st.ticks = 0;
	}

	private static void flee(Hollowling h, Little st) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
	}

	/** Small things only: no tools, weapons, armour or anything of the mod's. */
	private static boolean stealable(ItemStack s) {
		if (s.isEmpty() || s.isDamageableItem() || s.getMaxStackSize() == 1) {
			return false;
		}
		String id = s.getItem().toString();
		return !id.contains("extremedimensions") && !id.contains("shulker_box");
	}

	private static boolean hasLoot(ServerPlayer p) {
		for (int i = 0; i < 36; i++) {
			if (stealable(p.getInventory().getItem(i))) {
				return true;
			}
		}
		return false;
	}

	private static void steal(Hollowling h, Little st, ServerPlayer p) {
		var inv = p.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			if (stealable(inv.getItem(i))) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			flee(h, st);
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		ItemStack from = inv.getItem(slots.get(rnd.nextInt(slots.size())));
		st.carried = from.split(Math.min(from.getCount(), 1 + rnd.nextInt(3)));
		giggle(h);
		WorldFx.particles(p, "extremedimensions:lurker_wisp", 0.8, 0.3, 0.0, 6);
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.LITTLE_THIEVES);
		st.mode = Mode.FLEE;
		st.ticks = 0;
	}

	public static boolean ignoreHurt(Hollowling h, DamageSource source) {
		Little st = LITTLES.get(h.getUUID());
		return st != null && st.mode == Mode.COMPANION && source.getEntity() != null && source.getEntity().getUUID().equals(st.owner);
	}

	/** Hurt: it runs. Hurting the young of a hoard may wake its Keeper. */
	public static void onHollowlingHurt(Hollowling h, DamageSource source) {
		Little st = LITTLES.get(h.getUUID());
		if (st == null || st.mode == Mode.COMPANION) {
			return;
		}
		flee(h, st);
		if (source.getEntity() instanceof ServerPlayer p && ThreadLocalRandom.current().nextDouble() < 0.35) {
			Hoard home = byKey(st.hoard);
			if (home != null) {
				wake(home, p);
			}
		}
	}

	public static void onHollowlingDeath(Hollowling h, Entity killer) {
		Little st = LITTLES.remove(h.getUUID());
		LEVELS.remove(h.getUUID());
		if (st == null) {
			return;
		}
		if (killer instanceof ServerPlayer p && !st.brood) {
			Standing.change(p, Config.TRIBE_REP_HOLLOWLING, "gui.extremedimensions.tribe.why.hollowling");
		}
		if (st.mode != Mode.COMPANION && !st.brood && st.hoard != null) {
			Hoard home = byKey(st.hoard);
			if (home != null) {
				youngLost(home, System.currentTimeMillis());
			}
		}
		if (!st.carried.isEmpty()) {
			h.spawnAtLocation((ServerLevel) h.level(), st.carried);
		}
		if (st.mode == Mode.COMPANION && st.owner != null) {
			COMPANIONS.remove(st.owner);
			DataStore.get(st.owner).companion = false;
			DataStore.markDirty();
		}
	}

	// ------------------------------------------------------------------ companions

	/**
	 * A Tether Fragment, offered in soul light, may earn a Hollowling's trust. Outside soul light it only startles.
	 */
	public static InteractionResult onInteract(ServerPlayer p, Hollowling h, ItemStack stack) {
		Little st = LITTLES.get(h.getUUID());
		if (st == null) {
			return InteractionResult.PASS;
		}
		if (Config.TRIBE_ENABLED > 0 && stack.isEmpty()) {
			return barter(p, h, st);
		}
		if (Config.TRIBE_ENABLED > 0 && BarterService.LIKES.containsKey(stack.getItem()) && st.carried.isEmpty() && !st.bold) {
			ItemStack gift = BarterService.gift(p, stack);
			if (!gift.isEmpty()) {
				giggle(h);
				WorldFx.particleIn(h.level().dimension().identifier().toString(), "extremedimensions:soul_mote", h.getX(), h.getY() + 0.8, h.getZ(), 0.3, 0.3, 0.3, 0.0, 8);
				if (st.mode != Mode.COMPANION && byKey(st.hoard) != null) {
					// It hurries home with it
					st.carried = gift;
					st.mode = Mode.CARRY;
					st.ticks = 0;
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (st.mode == Mode.COMPANION || !stack.is(ModItems.TETHER_FRAGMENT)) {
			return InteractionResult.PASS;
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (d.companion) {
			return InteractionResult.PASS;
		}
		ServerLevel level = (ServerLevel) h.level();
		if (!SoulWards.warded(level, h.getBlockX(), h.getBlockY(), h.getBlockZ())) {
			giggle(h);
			flee(h, st);
			return InteractionResult.SUCCESS;
		}
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		String dim = level.dimension().identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", h.getX(), h.getY() + 0.8, h.getZ(), 0.4, 0.4, 0.4, 0.0, 14);
		if (!Standing.atLeast(p, Standing.Level.TRUSTED) && ThreadLocalRandom.current().nextDouble() >= Config.HOLLOWLING_BOND_CHANCE) {
			h.getLookControl().setLookAt(p, 30, 30);
			st.mode = Mode.FROZEN;
			st.stared = 0;
			return InteractionResult.SUCCESS;
		}
		bond(p, d, h, st);
		return InteractionResult.SUCCESS;
	}

	private static void bond(ServerPlayer p, PlayerData d, Hollowling h, Little st) {
		if (!st.carried.isEmpty()) {
			h.spawnAtLocation((ServerLevel) h.level(), st.carried);
			st.carried = ItemStack.EMPTY;
		}
		st.mode = Mode.COMPANION;
		st.owner = p.getUUID();
		st.hoard = null;
		d.companion = true;
		d.companionGiftIn = Config.COMPANION_GIFT_MINUTES * 60;
		COMPANIONS.put(p.getUUID(), h.getUUID());
		toughen(h);
		WorldFx.soundIn(h.level().dimension().identifier().toString(), "extremedimensions:hollowling_bond", "neutral", h.getX(), h.getY() + 0.8, h.getZ(), 1f, 1f);
		Advancements.grant(p, Advancements.HOLLOW_BOUND);
		Journal.unlock(p, d, JournalEntry.LITTLE_THIEVES);
		Standing.change(p, Config.TRIBE_REP_BOND, "gui.extremedimensions.tribe.why.bond");
		DataStore.markDirty();
	}

	/** An empty hand: the Hollowling barters, if the tribe tolerates the player; otherwise it giggles and runs. */
	private static InteractionResult barter(ServerPlayer p, Hollowling h, Little st) {
		if (st.bold || st.mode == Mode.FLEE || st.mode == Mode.CARRY) {
			return InteractionResult.PASS;
		}
		if (!Standing.atLeast(p, Standing.Level.TOLERATED) && !p.isCreative()) {
			giggle(h);
			if (st.mode != Mode.COMPANION) {
				flee(h, st);
			}
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.barter.distrust_now", Standing.describe(p)), true);
			return InteractionResult.SUCCESS;
		}
		if (st.mode != Mode.COMPANION) {
			st.mode = Mode.BARTER;
			st.trader = p.getUUID();
			st.ticks = 0;
		}
		h.getLookControl().setLookAt(p, 30, 30);
		WorldFx.soundIn(h.level().dimension().identifier().toString(), "extremedimensions:hollowling_ambient", "neutral", h.getX(), h.getY() + 0.8, h.getZ(), 0.8f, 1.3f);
		BarterService.open(p, h);
		return InteractionResult.SUCCESS;
	}

	/** Whether this entity is a Hollowling this player can barter with right now (for the barter screen's buttons). */
	public static boolean canBarter(ServerPlayer p, Entity e) {
		Little st = e == null ? null : LITTLES.get(e.getUUID());
		return st != null && !st.bold && e.isAlive() && e.level() == p.level() && e.distanceToSqr(p) <= BarterService.REACH * BarterService.REACH;
	}

	private static void toughen(Hollowling h) {
		AttributeInstance hp = h.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(30.0);
		}
		h.setHealth(h.getMaxHealth());
	}

	/** Makes sure a bound player's companion is with them, wherever they go. Also counts down to its next gift. */
	private static void companionUpkeep(ServerPlayer p, PlayerData d) {
		if (!d.companion || p.tickCount < 60) {
			return;
		}
		UUID id = COMPANIONS.get(p.getUUID());
		Entity e = null;
		if (id != null) {
			ServerLevel level = LEVELS.get(id);
			e = level == null ? null : level.getEntity(id);
		}
		if (!(e instanceof Hollowling h) || !h.isAlive() || h.level() != p.level()) {
			if (e instanceof Mob m) {
				LITTLES.remove(m.getUUID());
				LEVELS.remove(m.getUUID());
				m.discard();
			}
			BlockPos spot = groundAt((ServerLevel) p.level(), p.blockPosition().offset(1, 0, 1));
			Little st = new Little();
			st.mode = Mode.COMPANION;
			st.owner = p.getUUID();
			Hollowling h2 = spawnLittle((ServerLevel) p.level(), spot == null ? p.blockPosition() : spot, st);
			if (h2 != null) {
				toughen(h2);
				COMPANIONS.put(p.getUUID(), h2.getUUID());
			}
			return;
		}
		if (--d.companionGiftIn <= 0) {
			d.companionGiftIn = Math.max(60, Config.COMPANION_GIFT_MINUTES * 60);
			gift(p, h);
		}
	}

	private static void gift(ServerPlayer p, Hollowling h) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double r = rnd.nextDouble();
		ItemStack s;
		if (r < 0.12) {
			s = new ItemStack(ModItems.WANDERERS_PAGE);
		} else if (r < 0.24) {
			s = new ItemStack(ModItems.TETHER_FRAGMENT);
		} else {
			Item[] finds = {Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.AMETHYST_SHARD, Items.STRING, Items.SOUL_TORCH,
					Items.GLOW_BERRIES, Items.FLINT, Items.COPPER_INGOT, Items.EMERALD, Items.LAPIS_LAZULI};
			s = new ItemStack(finds[rnd.nextInt(finds.length)], 1 + rnd.nextInt(3));
		}
		h.spawnAtLocation((ServerLevel) h.level(), s);
		WorldFx.soundIn(h.level().dimension().identifier().toString(), "extremedimensions:hollowling_bond", "neutral", h.getX(), h.getY() + 0.8, h.getZ(), 0.7f, 1.3f);
	}

	/** Follows its friend, and chitters towards danger they have not seen. */
	private static void companion(Hollowling h, Little st, List<Runnable> later) {
		ServerPlayer owner = WorldFx.server().getPlayerList().getPlayer(st.owner);
		if (owner == null || owner.level() != h.level()) {
			later.add(() -> {
				LITTLES.remove(h.getUUID());
				LEVELS.remove(h.getUUID());
				h.discard();
			});
			return;
		}
		double d2 = h.distanceToSqr(owner);
		if (d2 > 24 * 24) {
			h.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
			h.getNavigation().stop();
		} else if (d2 > 4 * 4) {
			if (st.ticks % 10 == 0) {
				h.getNavigation().moveTo(owner, d2 > 10 * 10 ? 1.45 : 1.15);
			}
			h.setLurkerPose(d2 > 8 * 8 ? Lurker.POSE_RUN : Lurker.POSE_WATCH);
		} else {
			h.getNavigation().stop();
			h.setLurkerPose(Lurker.POSE_WATCH);
			h.getLookControl().setLookAt(owner, 20, 20);
		}
		if (st.ticks % 40 == 0) {
			AABB box = owner.getBoundingBox().inflate(16, 6, 16);
			for (Monster m : h.level().getEntitiesOfClass(Monster.class, box, m -> m.isAlive() && !(m instanceof Lurker))) {
				if (!seen(owner, m, 0.6)) {
					h.getLookControl().setLookAt(m, 30, 30);
					WorldFx.soundIn(h.level().dimension().identifier().toString(), "extremedimensions:hollowling_ambient", "neutral", h.getX(), h.getY() + 0.8, h.getZ(), 1f, 1.4f);
					WorldFx.particleIn(h.level().dimension().identifier().toString(), "extremedimensions:soul_mote",
							m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 0.1, 0.1, 0.1, 0.0, 3);
					break;
				}
			}
		}
	}

	/** Is a companion close enough to stand up for this player? */
	public static boolean guarded(ServerPlayer p) {
		UUID id = COMPANIONS.get(p.getUUID());
		if (id == null || ThreadLocalRandom.current().nextDouble() >= Config.COMPANION_GUARD_CHANCE) {
			return false;
		}
		Entity e = p.level() instanceof ServerLevel sl ? sl.getEntity(id) : null;
		if (e instanceof Hollowling h && h.distanceToSqr(p) < 16 * 16) {
			giggle(h);
			h.getNavigation().moveTo(p, 1.5);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the Keeper

	/** Wakes the Keeper of a hoard and sets it on this player, unless it is already hunting or was slain. */
	public static void wake(Hoard h, ServerPlayer p) {
		wake(h, p, false);
	}

	/** Whether the next hoard this player wakes is guarded by the Matriarch: after enough Keepers, until she falls. */
	public static boolean matriarchFor(PlayerData d) {
		return Config.MATRIARCH_AFTER_KEEPERS > 0 && !d.matriarchSlain && d.keepersSlain >= Config.MATRIARCH_AFTER_KEEPERS;
	}

	/** Operator command: a hoard ahead of the player, guarded by the Matriarch, woken at once. */
	public static boolean summonMatriarch(ServerPlayer p) {
		if (!summonHoard(p)) {
			return false;
		}
		return wake(DataStore.hoards().get(DataStore.hoards().size() - 1), p, true);
	}

	/** Wakes a hoard's guard; forced (an operator calling the Matriarch), she comes whatever the mode or rules. */
	private static boolean wake(Hoard h, ServerPlayer p, boolean forceMatriarch) {
		if (!forceMatriarch && (h.keeperSlain || Config.KEEPER_ENABLED == 0 || p.isCreative() || p.isSpectator())) {
			return false;
		}
		for (Guard g : KEEPERS.values()) {
			if (h.key().equals(g.hoard)) {
				return false;
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double behind = Math.toRadians(p.getYRot()) + Math.PI / 2;
		BlockPos spot = null;
		boolean nether = Dims.NETHER.equals(Dims.of(level));
		for (int i = 0; i < 12 && spot == null; i++) {
			double a = behind + (rnd.nextDouble() - 0.5) * 1.6;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * (10 + rnd.nextInt(6)));
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * (10 + rnd.nextInt(6)));
			spot = nether ? standable(level, x, (int) p.getY() + 6, z, 14) : surface(level, x, z);
		}
		if (spot == null) {
			spot = p.blockPosition().offset(6, 0, 6);
		}
		if (forceMatriarch || matriarchFor(DataStore.get(p.getUUID()))) {
			// The mother of the hollows comes instead of a Keeper
			return com.extremedimensions.elder.MatriarchService.summon(p, h.key(), new BlockPos(h.x, h.y, h.z));
		}
		spawnKeeper(p, level, spot, h.key());
		return true;
	}

	/** Whether the Keeper of this hoard is awake and hunting. */
	public static boolean keeperAwake(String hoard) {
		for (Guard g : KEEPERS.values()) {
			if (hoard.equals(g.hoard)) {
				return true;
			}
		}
		return false;
	}

	/** The tribe hunts this player: a Keeper of no hoard comes for them out of the dark, behind them. */
	public static boolean hunt(ServerPlayer p) {
		String dim = Dims.of(p);
		if (Config.KEEPER_ENABLED == 0 || p.isCreative() || p.isSpectator() || Dims.END.equals(dim) || Dims.SEAM.equals(dim)) {
			return false;
		}
		for (Guard g : KEEPERS.values()) {
			if (g.hoard == null && p.getUUID().equals(g.target)) {
				return false;
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double behind = Math.toRadians(p.getYRot()) + Math.PI / 2;
		boolean nether = Dims.NETHER.equals(dim);
		BlockPos spot = null;
		for (int i = 0; i < 12 && spot == null; i++) {
			double a = behind + (rnd.nextDouble() - 0.5) * 1.6;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * (14 + rnd.nextInt(8)));
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * (14 + rnd.nextInt(8)));
			spot = nether ? standable(level, x, (int) p.getY() + 6, z, 14) : surface(level, x, z);
		}
		if (spot == null || Math.abs(spot.getY() - p.getY()) > 24) {
			return false;
		}
		if (spawnKeeper(p, level, spot, null) == null) {
			return false;
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.tribe.hunted").withStyle(net.minecraft.ChatFormatting.DARK_RED));
		return true;
	}

	private static Keeper spawnKeeper(ServerPlayer p, ServerLevel level, BlockPos spot, String hoard) {
		Keeper k = ModEntities.KEEPER.spawn(level, spot, EntitySpawnReason.EVENT);
		if (k == null) {
			return null;
		}
		k.setPersistenceRequired();
		k.setTarget(p);
		Guard g = new Guard();
		g.hoard = hoard;
		g.target = p.getUUID();
		g.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.keeper"), BossEvent.BossBarColor.WHITE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		g.bar.addPlayer(p);
		KEEPERS.put(k.getUUID(), g);
		LEVELS.put(k.getUUID(), level);
		roar(k);
		hush(k, 4);
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.KEEPER);
		return k;
	}

	private static void roar(Keeper k) {
		String dim = k.level().dimension().identifier().toString();
		WorldFx.soundIn(dim, "extremedimensions:keeper_roar", "hostile", k.getX(), k.getY() + 3, k.getZ(), 3f, 1f);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", k.getX(), k.getY() + 2.5, k.getZ(), 0.8, 1.5, 0.8, 0.0, 40);
	}

	/** The Keeper's screech: darkness for everyone close, and slowness for its prey. */
	private static void hush(Keeper k, int secs) {
		for (ServerPlayer p : WorldFx.server().getPlayerList().getPlayers()) {
			if (p.level() == k.level() && p.distanceToSqr(k) < 32 * 32) {
				WorldFx.effect(p, MobEffects.DARKNESS, secs + 1, 0);
			}
		}
	}

	private static void driveKeepers() {
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, Guard>> it = KEEPERS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Guard> e = it.next();
			Guard g = e.getValue();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof Keeper k) || !k.isAlive()) {
				g.bar.removeAllPlayers();
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			g.ticks++;
			Hoard home = byKey(g.hoard);
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(g.target);
			boolean lost = p == null || !p.isAlive() || p.level() != k.level()
					|| (home != null && home.distSq(p.getX(), p.getY(), p.getZ()) > 64 * 64)
					|| (home == null && k.distanceToSqr(p) > 64 * 64);
			if (lost) {
				// Its prey left what it keeps: it returns to the shadows, whole again
				later.add(() -> fade(k));
				continue;
			}
			if (g.ticks % 20 == 0) {
				k.setTarget(p);
			}
			g.bar.setProgress(k.getHealth() / k.getMaxHealth());
			double d2 = k.distanceToSqr(p);
			if (d2 < 4.5 * 4.5) {
				k.setLurkerPose(Lurker.POSE_TAKE);
			} else if (k.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
				k.setLurkerPose(Lurker.POSE_RUN);
			} else {
				k.setLurkerPose(Lurker.POSE_WATCH);
			}
			if (--g.nextTrick <= 0) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				g.nextTrick = 140 + rnd.nextInt(100);
				if (d2 > 10 * 10) {
					blinkBehind(k, p);
				} else {
					roar(k);
					hush(k, 3);
					WorldFx.effect(p, MobEffects.SLOWNESS, 2, 1);
				}
			}
		}
		later.forEach(Runnable::run);
	}

	private static void blinkBehind(Keeper k, ServerPlayer p) {
		String dim = k.level().dimension().identifier().toString();
		Vec3 look = p.getViewVector(1.0f);
		double x = p.getX() - look.x * 3;
		double z = p.getZ() - look.z * 3;
		BlockPos g = groundAt((ServerLevel) k.level(), BlockPos.containing(x, p.getY(), z));
		if (g == null) {
			return;
		}
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", k.getX(), k.getY() + 2, k.getZ(), 0.6, 1.4, 0.6, 0.0, 30);
		k.teleportTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", g.getX() + 0.5, g.getY() + 2, g.getZ() + 0.5, 0.6, 1.4, 0.6, 0.0, 30);
		WorldFx.soundIn(dim, "extremedimensions:lurker_vanish", "hostile", g.getX() + 0.5, g.getY() + 2.0, g.getZ() + 0.5, 1.4f, 0.7f);
	}

	/** Hollowlings of a hoard called to a fight (the Matriarch's brood), who steal while their mother fights. */
	public static void brood(ServerLevel level, BlockPos center, String hoard, int count) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		String dim = level.dimension().identifier().toString();
		for (int i = 0; i < count; i++) {
			BlockPos spot = groundAt(level, center.offset(rnd.nextInt(-6, 7), 0, rnd.nextInt(-6, 7)));
			if (spot != null) {
				Little st = new Little();
				st.hoard = hoard;
				st.brood = true;
				spawnLittle(level, spot, st);
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 0.3, 0.4, 0.3, 0.0, 12);
			}
		}
	}

	/** The young of a raided hoard swarm out, bold, to steal from anyone near; their ids. */
	public static List<UUID> raidYoung(ServerLevel level, BlockPos center, String hoard, int count) {
		List<UUID> out = new ArrayList<>();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		String dim = level.dimension().identifier().toString();
		for (int i = 0; i < count; i++) {
			BlockPos spot = groundAt(level, center.offset(rnd.nextInt(-4, 5), 0, rnd.nextInt(-4, 5)));
			if (spot == null) {
				spot = center.above();
			}
			Little st = new Little();
			st.hoard = hoard;
			st.brood = true;
			st.bold = true;
			Hollowling l = spawnLittle(level, spot, st);
			if (l != null) {
				out.add(l.getUUID());
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 0.3, 0.4, 0.3, 0.0, 12);
			}
		}
		return out;
	}

	/** Whether this Hollowling is still about. */
	public static boolean youngAlive(UUID id) {
		return LITTLES.containsKey(id);
	}

	/** A hoard's guardian has fallen: what it kept is free to take. */
	public static void markSlain(String hoard) {
		Hoard home = byKey(hoard);
		if (home != null) {
			home.keeperSlain = true;
			DataStore.markDirty();
		}
	}

	/** Slain: the hoard is free to take, and the Keeper leaves something of its own. */
	public static void onKeeperDeath(Keeper k, Entity killer) {
		Guard g = KEEPERS.remove(k.getUUID());
		LEVELS.remove(k.getUUID());
		if (g == null) {
			return;
		}
		g.bar.removeAllPlayers();
		Hoard home = byKey(g.hoard);
		if (home != null) {
			home.keeperSlain = true;
			DataStore.markDirty();
		}
		// A Keeper slain in a raid (already paid for) or sent to hunt the player costs nothing more
		if (killer instanceof ServerPlayer kp && g.hoard != null && !RaidService.raiding(g.hoard)) {
			Standing.change(kp, Config.TRIBE_REP_KEEPER, "gui.extremedimensions.tribe.why.keeper");
		}
		ServerLevel level = (ServerLevel) k.level();
		k.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 2));
		k.spawnAtLocation(level, new ItemStack(ModItems.WANDERERS_PAGE));
		if (ThreadLocalRandom.current().nextDouble() < Config.RELIC_KEEPER_CHANCE) {
			// What the Keeper guarded most of all
			k.spawnAtLocation(level, com.extremedimensions.relic.RelicService.random());
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(k) < 48 * 48) {
				PlayerData d = DataStore.get(p.getUUID());
				d.keepersSlain++;
				Advancements.grant(p, Advancements.KEEPER_SLAIN);
				Journal.unlock(p, d, JournalEntry.KEEPER);
			}
		}
		DataStore.markDirty();
	}

	/** Gone: a curl of ink and a breath. */
	public static void fade(Mob m) {
		String dim = m.level().dimension().identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 0.3, m.getBbHeight() / 3, 0.3, 0.0, 16);
		Guard g = KEEPERS.remove(m.getUUID());
		if (g != null) {
			g.bar.removeAllPlayers();
		}
		LITTLES.remove(m.getUUID());
		LEVELS.remove(m.getUUID());
		m.discard();
	}

	/** Operator command: an old hoard right in front of the player. */
	public static boolean summonHoard(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		if (Dims.END.equals(Dims.of(level))) {
			return false;
		}
		Vec3 ahead = p.position().add(p.getViewVector(1.0f).multiply(1, 0, 1).normalize().scale(8));
		boolean nether = Dims.NETHER.equals(Dims.of(level));
		int x = (int) Math.floor(ahead.x), z = (int) Math.floor(ahead.z);
		BlockPos spot = nether ? standable(level, x, (int) p.getY() + 4, z, 10) : surface(level, x, z);
		if (spot == null) {
			return false;
		}
		Hoard h = new Hoard(Dims.of(level), spot.getX(), spot.getY(), spot.getZ(), "");
		build(level, spot, nether);
		DataStore.hoards().add(h);
		deposit(h, trinkets());
		DataStore.markDirty();
		return true;
	}
}
