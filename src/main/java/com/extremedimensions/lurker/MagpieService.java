package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.math.Transformation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.mixin.BillboardAccess;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.mixin.ItemDisplayAccess;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.world.WorldService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Magpie's mind. It never touches the player; it goes for what they keep.
 *
 * <ul>
 *   <li><b>SNEAK</b>: it comes out of sight near a chest or a barrel that holds something, not far from the player, and
 *       creeps to it. Seen close by (a player looking right at it), it bolts empty-handed.</li>
 *   <li><b>RAID</b>: at the chest it takes the brightest things in it, one at a time (enchanted and rare things first,
 *       then gems and metals, then the fullest stacks), {@code magpie_takes} of them at most. The player it came for
 *       hears the lid and is told something is at their things. Come upon within a few blocks, it runs with what it
 *       has.</li>
 *   <li><b>FLEE</b>: it runs its loot home to a hoard (the one the Lurkers fill), the last thing it took held up over its
 *       head and glittering, so it can be followed. A blow shakes the last thing it took loose; slain, it drops it all.
 *       What reaches the hoard waits there.</li>
 * </ul>
 *
 * Magpies are never saved: one not driven from here goes home with what it carries.
 */
public final class MagpieService {
	private MagpieService() {}

	enum Mode { SNEAK, RAID, FLEE }

	/** Ticks between two things taken, and how near (blocks) it must be to the chest's middle to reach in. */
	private static final int TAKE_EVERY = 12;
	private static final double REACH = 2.1;
	/** How near a player looking right at it must be to see it, and how near a player startles it off a chest. */
	private static final double SPOT = 10, STARTLE = 4;
	/** How often it checks that its walk is getting it somewhere (ticks), and how many walks to nowhere it allows. */
	private static final int STUCK_CHECK = 20, STUCK_GIVE_UP = 4;
	/** The display that holds its loot up is tagged so, and stands this far down from the top of its head. */
	private static final String TAG = "extremedimensions.magpie_loot";

	static final class State {
		Mode mode = Mode.SNEAK;
		UUID target;
		BlockPos chest;
		int ticks;
		int limit;
		int taken;
		boolean anywhere;
		final List<ItemStack> loot = new ArrayList<>();
		Hoard hoard;
		Vec3 away;
		Vec3 checkedAt;
		int stuck;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	/** Server tick each player last had a Magpie come (cooldown). */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
		LAST.clear();
	}

	// ------------------------------------------------------------------ coming

	/** Whether a Magpie may come for this player's things now: in the Overworld or the Nether, while the tribe steals. */
	public static boolean fits(ServerPlayer p) {
		String dim = Dims.of(p);
		return Config.MAGPIE_ENABLED > 0 && (Dims.OVERWORLD.equals(dim) || Dims.NETHER.equals(dim)) && Dims.ours(p) && !p.isSpectator()
				&& !p.isCreative() && p.isAlive() && !Rules.peaceful(p.level()) && !kin(p) && WorldService.tribeSteals();
	}

	private static boolean kin(ServerPlayer p) {
		return com.extremedimensions.tribe.Standing.of(p) == com.extremedimensions.tribe.Standing.Level.KIN;
	}

	public static void tick(MinecraftServer s) {
		drive();
		// (a quarter of the way round the Lurker's own look, so they never fall on one tick)
		if (Config.MAGPIE_ENABLED <= 0 || (s.getTickCount() + 100) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.MAGPIE_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.MAGPIE_SPAWN_CHANCE) {
				continue;
			}
			if (come(p, false) != null) {
				LAST.put(p.getUUID(), (long) s.getTickCount());
			}
		}
	}

	/** Whether a Magpie is after this player's things already. */
	private static boolean after(ServerPlayer p) {
		for (State st : STATES.values()) {
			if (p.getUUID().equals(st.target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The chests and barrels near this spot that hold something and are a player's own (not a hoard, not one still
	 * sealed with its loot table), in chunks already loaded; nearest first.
	 */
	public static List<BlockPos> chests(ServerLevel level, BlockPos around, int range) {
		List<BlockPos> out = new ArrayList<>();
		int reach = (range >> 4) + 1;
		int cx = around.getX() >> 4, cz = around.getZ() >> 4;
		String dim = Dims.of(level);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) || !(be instanceof Container c) || c.isEmpty()
							|| be.getBlockPos().distSqr(around) > (double) range * range) {
						continue;
					}
					if (be instanceof net.minecraft.world.RandomizableContainer r && r.getLootTable() != null) {
						continue;
					}
					if (HollowService.hoardAt(dim, be.getBlockPos()) != null) {
						continue;
					}
					out.add(be.getBlockPos());
				}
			}
		}
		out.sort(java.util.Comparator.comparingDouble(q -> q.distSqr(around)));
		return out;
	}

	/**
	 * A Magpie comes for this player's things: near a chest of theirs a little way off, out of their sight, on ground
	 * its body fits and from where it can walk to the chest. Null if no chest or no such place was found.
	 * {@code anywhere} (an operator's summons) lets it come in sight too, and to a chest close by.
	 */
	public static Magpie come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		List<BlockPos> found = chests(level, p.blockPosition(), Math.max(4, Config.MAGPIE_RANGE));
		if (found.isEmpty()) {
			return null;
		}
		// Not the one at their elbow: one a little way off, left alone (any, when summoned)
		List<BlockPos> far = new ArrayList<>();
		for (BlockPos c : found) {
			if (anywhere || c.distSqr(p.blockPosition()) >= 6 * 6) {
				far.add(c);
			}
		}
		if (far.isEmpty()) {
			return null;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos chest = far.get(rnd.nextInt(far.size()));
		Vec3 eye = p.getEyePosition();
		int tried = 0;
		for (int i = 0; i < 40 && tried < 4; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * 8;
			int x = (int) Math.floor(chest.getX() + 0.5 + Math.cos(a) * d), z = (int) Math.floor(chest.getZ() + 0.5 + Math.sin(a) * d);
			BlockPos feet = null;
			for (int dy = 4; dy >= -5 && feet == null; dy--) {
				BlockPos q = new BlockPos(x, chest.getY() + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.MAGPIE.getWidth(), ModEntities.MAGPIE.getHeight())
						&& !level.getFluidState(q).isSource()) {
					feet = q;
				}
			}
			if (feet == null || (!anywhere && feet.distSqr(p.blockPosition()) < 10 * 10)) {
				continue;
			}
			if (!anywhere) {
				Vec3 body = Vec3.atBottomCenterOf(feet).add(0, 1.4, 0);
				if (level.clip(new ClipContext(eye, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
					continue;
				}
			}
			tried++;
			Magpie m = setDown(level, feet, p, chest, anywhere);
			if (m != null) {
				return m;
			}
		}
		return null;
	}

	/** A Magpie set down here to rob this chest of this player's, if it could walk to it from there (else null). */
	private static Magpie setDown(ServerLevel level, BlockPos feet, ServerPlayer p, BlockPos chest, boolean anywhere) {
		Magpie m = ModEntities.MAGPIE.spawn(level, feet, EntitySpawnReason.EVENT);
		if (m == null) {
			return null;
		}
		if (!canReach(m, chest)) {
			m.discard();
			return null;
		}
		drive(level, m, p, chest, anywhere);
		return m;
	}

	/** Whether a creature just set down could walk to beside this block. */
	private static boolean canReach(Magpie m, BlockPos chest) {
		m.setOnGround(true);
		Path path = m.getNavigation().createPath(chest, 1);
		return path != null && path.canReach();
	}

	private static State drive(ServerLevel level, Magpie m, ServerPlayer p, BlockPos chest, boolean anywhere) {
		m.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		st.chest = chest.immutable();
		st.anywhere = anywhere;
		st.limit = Math.max(10, Config.MAGPIE_SNEAK_SECONDS) * 20;
		st.checkedAt = m.position();
		STATES.put(m.getUUID(), st);
		LEVELS.put(m.getUUID(), level);
		m.setLurkerPose(Lurker.POSE_STALK);
		m.setLurkerMood(LurkerBody.MOOD_THIEF);
		return st;
	}

	/** Operator command: a Magpie comes for a chest near the player (one of theirs within magpie_range). */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && come(p, true) != null;
	}

	// ------------------------------------------------------------------ what it does

	private static void drive() {
		if (STATES.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof Magpie m) || !m.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != m.level() || p.distanceToSqr(m) > 96 * 96) {
				later.add(() -> vanish(m));
				continue;
			}
			switch (st.mode) {
				case SNEAK -> sneak(level, m, st, p, later);
				case RAID -> raid(level, m, st, p);
				case FLEE -> flee(m, st, p, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** It creeps to the chest; it bolts if it is seen; it gives up after a while, or when it cannot get there. */
	private static void sneak(ServerLevel level, Magpie m, State st, ServerPlayer p, List<Runnable> later) {
		if (!(level.getBlockEntity(st.chest) instanceof Container c) || c.isEmpty()) {
			later.add(() -> vanish(m));
			return;
		}
		if (st.ticks > st.limit) {
			later.add(() -> vanish(m));
			return;
		}
		if (seen(m, p)) {
			bolt(m, st, p);
			return;
		}
		Vec3 at = Vec3.atCenterOf(st.chest);
		double dx = at.x - m.getX(), dz = at.z - m.getZ();
		if (dx * dx + dz * dz <= REACH * REACH && Math.abs(at.y - (m.getY() + 0.5)) < 2.2) {
			startRaid(level, m, st, p);
			return;
		}
		if (st.ticks % STUCK_CHECK == 0) {
			boolean nowhere = st.checkedAt != null && m.position().distanceTo(st.checkedAt) < 0.4;
			st.stuck = nowhere ? st.stuck + 1 : 0;
			st.checkedAt = m.position();
			if (nowhere) {
				m.getNavigation().stop();
			}
			if (st.stuck >= STUCK_GIVE_UP) {
				later.add(() -> vanish(m));
				return;
			}
		}
		if (st.ticks % 5 == 1) {
			TribeMoves.go(m, at.x, st.chest.getY(), at.z, Config.MAGPIE_SPEED * 0.7);
			TribeMoves.trimBehind(m);
		}
	}

	/** Whether the player sees it: close by, looking right at it, nothing between them. */
	private static boolean seen(Magpie m, ServerPlayer p) {
		double d = p.distanceTo(m);
		if (d > SPOT) {
			return false;
		}
		Vec3 to = m.getBoundingBox().getCenter().subtract(p.getEyePosition());
		double len = to.length();
		return len < 1e-3 || (to.scale(1 / len).dot(p.getViewVector(1.0F)) > 0.93 && p.hasLineOfSight(m));
	}

	/** Seen: a startled chatter, and it runs off empty-handed. */
	private static void bolt(Magpie m, State st, ServerPlayer p) {
		WorldFx.soundIn(dimOf(m), "minecraft:entity.parrot.hurt", "hostile", m.getX(), m.getY() + 1.8, m.getZ(), 1.0f, 0.6f);
		startFlee(m, st, p);
	}

	private static void startRaid(ServerLevel level, Magpie m, State st, ServerPlayer p) {
		st.mode = Mode.RAID;
		st.ticks = 0;
		m.getNavigation().stop();
		m.setLurkerPose(Lurker.POSE_TAKE);
		boolean barrel = level.getBlockEntity(st.chest) instanceof BarrelBlockEntity;
		WorldFx.soundIn(dimOf(m), barrel ? "minecraft:block.barrel.open" : "minecraft:block.chest.open", "blocks",
				st.chest.getX() + 0.5, st.chest.getY() + 0.5, st.chest.getZ() + 0.5, 0.8f, 1.1f);
		// The player it came for hears it: something is at their things
		if (p.distanceToSqr(Vec3.atCenterOf(st.chest)) < 64 * 64) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.magpie.raid"), true);
		}
	}

	/** It takes the brightest things, one at a time; startled, or done, it runs. */
	private static void raid(ServerLevel level, Magpie m, State st, ServerPlayer p) {
		Vec3 at = Vec3.atCenterOf(st.chest);
		m.getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
		TribeMoves.stand(m);
		if (p.distanceTo(m) < STARTLE || !(level.getBlockEntity(st.chest) instanceof Container c)) {
			closeChest(level, m, st);
			startFlee(m, st, p);
			return;
		}
		if (st.ticks % TAKE_EVERY != 0) {
			return;
		}
		int slot = brightest(c);
		if (slot < 0 || st.taken >= Math.max(1, Config.MAGPIE_TAKES)) {
			closeChest(level, m, st);
			startFlee(m, st, p);
			return;
		}
		ItemStack took = c.removeItemNoUpdate(slot);
		c.setChanged();
		if (took.isEmpty()) {
			return;
		}
		st.loot.add(took);
		st.taken++;
		show(m, took);
		WorldFx.soundIn(dimOf(m), "minecraft:entity.item.pickup", "hostile", m.getX(), m.getY() + 1.6, m.getZ(), 0.7f, 1.4f);
		WorldFx.particleIn(dimOf(m), "minecraft:wax_off", at.x, at.y + 0.6, at.z, 0.3, 0.2, 0.3, 0.0, 6);
	}

	private static void closeChest(ServerLevel level, Magpie m, State st) {
		boolean barrel = level.getBlockEntity(st.chest) instanceof BarrelBlockEntity;
		WorldFx.soundIn(dimOf(m), barrel ? "minecraft:block.barrel.close" : "minecraft:block.chest.close", "blocks",
				st.chest.getX() + 0.5, st.chest.getY() + 0.5, st.chest.getZ() + 0.5, 0.7f, 1.1f);
	}

	/** The slot of the brightest thing in a container (enchanted and rare first, then gems and metals, then fullest), or -1. */
	public static int brightest(Container c) {
		int best = -1;
		double bestWorth = -1;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			double w = worth(s);
			if (w > bestWorth) {
				bestWorth = w;
				best = i;
			}
		}
		return best;
	}

	/** Gems and metals catch its eye. */
	private static final Set<net.minecraft.world.item.Item> SHINY = Set.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.GOLD_NUGGET,
			Items.RAW_GOLD, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.AMETHYST_SHARD, Items.IRON_INGOT, Items.COPPER_INGOT,
			Items.LAPIS_LAZULI, Items.QUARTZ, Items.ENDER_PEARL, Items.ECHO_SHARD, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.GOLD_BLOCK,
			Items.NETHERITE_BLOCK, Items.IRON_BLOCK, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.CLOCK,
			Items.COMPASS, Items.SPYGLASS, Items.PRISMARINE_CRYSTALS, Items.GLOWSTONE_DUST, Items.REDSTONE, Items.EXPERIENCE_BOTTLE);

	/** How much a stack catches its eye. */
	public static double worth(ItemStack s) {
		double w = s.getRarity().ordinal() * 100;
		if (s.isEnchanted() || s.hasFoil()) {
			w += 60;
		}
		if (SHINY.contains(s.getItem())) {
			w += 40;
		}
		return w + s.getCount() / 8.0;
	}

	private static void startFlee(Magpie m, State st, ServerPlayer p) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.away = null;
		st.limit = Math.max(3, Config.MAGPIE_FLEE_SECONDS) * 20;
		m.setLurkerPose(Lurker.POSE_RUN);
		m.setLurkerMood(LurkerBody.MOOD_THIEF);
		m.getNavigation().stop();
		if (!st.loot.isEmpty() && st.hoard == null) {
			// It runs home (struck or not): follow it, and you find where your things went
			st.hoard = HollowService.hoardFor(p, m.position());
		}
		WorldFx.soundIn(dimOf(m), "minecraft:entity.parrot.fly", "hostile", m.getX(), m.getY() + 1.6, m.getZ(), 1.0f, 0.8f);
	}

	/** It runs home with its loot held high, glittering; with none, it just runs. Gone once there, or after a while. */
	private static void flee(Magpie m, State st, ServerPlayer p, List<Runnable> later) {
		double speed = Config.MAGPIE_SPEED * (0.8 + 0.5 * Math.min(1.0, st.ticks / 30.0));
		if (st.ticks % 3 == 0 && !st.loot.isEmpty()) {
			WorldFx.particleIn(dimOf(m), "minecraft:wax_off", m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 0.25, 0.25, 0.25, 0.0, 2);
		}
		if (st.hoard != null) {
			Vec3 home = new Vec3(st.hoard.x + 0.5, st.hoard.y, st.hoard.z + 0.5);
			if (st.ticks % 8 == 1 || m.getNavigation().isDone()) {
				TribeMoves.go(m, home.x, home.y, home.z, speed);
			}
			if (m.position().distanceToSqr(home) < 3.5 * 3.5 || st.ticks > st.limit) {
				later.add(() -> vanish(m));
			}
			return;
		}
		if (st.ticks % 8 == 1 || m.getNavigation().isDone()) {
			if (st.away == null || m.position().distanceToSqr(st.away) < 3.0 * 3.0 || m.getNavigation().isDone()) {
				st.away = TribeMoves.away(m, p.position(), 22);
			}
			if (st.away != null) {
				TribeMoves.go(m, st.away.x, st.away.y, st.away.z, speed);
			}
		}
		boolean far = m.distanceTo(p) > 24 && !p.hasLineOfSight(m);
		if (st.ticks > st.limit || (far && st.loot.isEmpty())) {
			later.add(() -> vanish(m));
		}
	}

	// ------------------------------------------------------------------ its loot, held up for all to see

	/**
	 * Holds this up over its head (a display riding it, full-bright, always turned to whoever looks). The game's own
	 * way of setting what a display shows is reached through a mixin; were that ever missing, the loot is simply not
	 * shown and nothing else changes.
	 */
	private static void show(Magpie m, ItemStack stack) {
		try {
			Display.ItemDisplay d = held(m);
			if (d == null && m.level() instanceof ServerLevel level) {
				d = new Display.ItemDisplay(itemDisplay(), level);
				d.setPos(m.getX(), m.getY() + m.getBbHeight(), m.getZ());
				d.addTag(TAG);
				((DisplayAccess) d).extremedimensions$setTransformation(new Transformation(new Vector3f(0, -0.15F, 0), new Quaternionf(),
						new Vector3f(0.6F, 0.6F, 0.6F), new Quaternionf()));
				((DisplayAccess) d).extremedimensions$setBrightnessOverride(new Brightness(15, 15));
				((BillboardAccess) d).extremedimensions$setBillboard(Display.BillboardConstraints.CENTER);
				if (!level.addFreshEntity(d)) {
					return;
				}
				d.startRiding(m);
			}
			if (d != null) {
				((ItemDisplayAccess) d).extremedimensions$setItemStack(stack.copyWithCount(1));
			}
		} catch (Throwable t) {
			// (the loot goes unseen)
		}
	}

	/** Holds this up over its head as its loot does (for the screenshots). */
	public static void hold(Magpie m, ItemStack stack) {
		show(m, stack);
	}

	/** What it holds up now: the last thing it took, or nothing (the display taken down). */
	private static void reshow(Magpie m, State st) {
		if (st.loot.isEmpty()) {
			drop(m);
		} else {
			show(m, st.loot.get(st.loot.size() - 1));
		}
	}

	/** The display it holds its loot up on, if any. */
	private static Display.ItemDisplay held(Magpie m) {
		for (Entity e : m.getPassengers()) {
			if (e instanceof Display.ItemDisplay d && d.entityTags().contains(TAG)) {
				return d;
			}
		}
		return null;
	}

	/** Takes its display down. */
	private static void drop(Magpie m) {
		for (Entity e : List.copyOf(m.getPassengers())) {
			if (e instanceof Display.ItemDisplay) {
				e.stopRiding();
				e.discard();
			}
		}
	}

	/** What its display shows (empty if it holds nothing up): for the tests and the lens. */
	public static ItemStack shown(Magpie m) {
		Display.ItemDisplay d = held(m);
		if (d == null) {
			return ItemStack.EMPTY;
		}
		try {
			return ((ItemDisplayAccess) d).extremedimensions$getItemStack().copy();
		} catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}

	private static EntityType<?> itemDisplay() {
		if (itemDisplay == null) {
			itemDisplay = BuiltInRegistries.ENTITY_TYPE.get(Identifier.fromNamespaceAndPath("minecraft", "item_display")).map(h -> h.value()).orElseThrow();
		}
		return itemDisplay;
	}

	private static EntityType<?> itemDisplay;

	// ------------------------------------------------------------------ the end of it

	/**
	 * Gone: a flutter of black and a glint. What it carried is never simply gone: it ends up in the hoard it ran to (or
	 * would have), or falls where it stood.
	 */
	public static void vanish(Magpie m) {
		State st = STATES.remove(m.getUUID());
		LEVELS.remove(m.getUUID());
		drop(m);
		if (m.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:smoke", m.getX(), m.getY() + 1.2, m.getZ(), 0.3, 0.6, 0.3, 0.02, 12);
			WorldFx.particleIn(level, "extremedimensions:lurker_wisp", m.getX(), m.getY() + 1.4, m.getZ(), 0.3, 0.6, 0.3, 0.0, 10);
			WorldFx.soundIn(dimOf(m), "minecraft:entity.parrot.fly", "hostile", m.getX(), m.getY() + 1.5, m.getZ(), 0.8f, 1.3f);
			if (st != null && !st.loot.isEmpty()) {
				Hoard h = st.hoard;
				ServerPlayer from = WorldFx.server() == null ? null : WorldFx.server().getPlayerList().getPlayer(st.target);
				if (h == null && from != null && from.level() == level) {
					h = HollowService.hoardFor(from, m.position());
				}
				if (h != null) {
					HollowService.stash(h, new ArrayList<>(st.loot), 0);
				} else {
					for (ItemStack stack : st.loot) {
						m.spawnAtLocation(level, stack);
					}
				}
				st.loot.clear();
			}
		}
		m.discard();
	}

	/** Struck: the last thing it took is shaken loose; sneaking or robbing, it runs. */
	public static void onHurt(Magpie m, DamageSource source) {
		State st = STATES.get(m.getUUID());
		if (st == null || !TribeMoves.struck(source)) {
			return;
		}
		if (!st.loot.isEmpty() && m.level() instanceof ServerLevel level) {
			ItemStack lost = st.loot.remove(st.loot.size() - 1);
			m.spawnAtLocation(level, lost);
			WorldFx.soundIn(dimOf(m), "minecraft:entity.item.pickup", "hostile", m.getX(), m.getY() + 1.6, m.getZ(), 0.8f, 0.7f);
			reshow(m, st);
		}
		if (st.mode != Mode.FLEE) {
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p != null) {
				if (st.mode == Mode.RAID && m.level() instanceof ServerLevel level) {
					closeChest(level, m, st);
				}
				startFlee(m, st, p);
			}
		}
	}

	/** Slain: all it took falls where it dies. */
	public static void onDeath(Magpie m, Entity killer) {
		State st = STATES.remove(m.getUUID());
		LEVELS.remove(m.getUUID());
		drop(m);
		if (st != null && m.level() instanceof ServerLevel level) {
			for (ItemStack stack : st.loot) {
				m.spawnAtLocation(level, stack);
			}
			st.loot.clear();
		}
		if (killer instanceof ServerPlayer p) {
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.magpie");
		}
	}

	private static String dimOf(Entity e) {
		return e.level().dimension().identifier().toString();
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Magpie already in the world after this player's chest: "sneak" creeps to it, "raid" reaches in at once. */
	public static void adopt(ServerLevel level, Magpie m, ServerPlayer p, BlockPos chest, String mode) {
		State st = drive(level, m, p, chest, true);
		if ("raid".equals(mode)) {
			startRaid(level, m, st, p);
		}
	}

	/** The mode a driven Magpie is in ("" if it is not driven). */
	public static String mode(Magpie m) {
		State st = STATES.get(m.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** What it carries now (copies). */
	public static List<ItemStack> carried(Magpie m) {
		State st = STATES.get(m.getUUID());
		List<ItemStack> out = new ArrayList<>();
		if (st != null) {
			for (ItemStack s : st.loot) {
				out.add(s.copy());
			}
		}
		return out;
	}
}
