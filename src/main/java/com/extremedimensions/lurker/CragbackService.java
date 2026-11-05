package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.mixin.BlockDisplayAccess;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.mojang.math.Transformation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Cragback's mind. Now and then one lies asleep on an island near a visitor to the Seam, hunched and still, its back
 * grown over with the island's stone and a vein or two of Tetherite, a boulder like any other:
 *
 * <ul>
 *   <li><b>DORMANT</b>: it sleeps. Break a block within {@code cragback_wake_range} of it (Tetherite ore from twice as
 *       far: it guards the veins), strike it, or stand by it a while without crouching, and it wakes.</li>
 *   <li><b>WAKE</b>: it heaves itself up, stone dust falling from its back, with a grinding roar.</li>
 *   <li><b>WALK</b>: slow and heavy, after whoever woke it.</li>
 *   <li><b>SLAM</b>: close, it brings both fists down: all near it hurt and thrown back (toward the edge, often); a
 *       raised shield turns it.</li>
 *   <li><b>ROCKFALL</b>: further off, it pounds the ground and rocks fall where its prey stands: a ring of dust on the
 *       ground first, then the rocks.</li>
 *   <li><b>SINK</b>: its prey gone, or awake long enough, it settles and sinks back into the stone.</li>
 * </ul>
 *
 * Slain, it gives up the Tetherite of its back. More players near, more health it has. Cragbacks are never saved.
 */
public final class CragbackService {
	private CragbackService() {}

	enum Mode { DORMANT, WAKE, WALK, SLAM, ROCKFALL, SINK }

	/** Ticks of the waking, the slam's and the rockfall's wind-up, the rocks' fall after their ring shows, the pause after a blow. */
	private static final int WAKE_TICKS = 40, SLAM_WINDUP = 18, ROCK_WINDUP = 12, ROCK_DELAY = 30, PAUSE = 30;
	/** How near it must be to slam (centre to centre, across), and how far the slam and a rock reach. */
	private static final double SLAM_FROM = 3.8, SLAM_REACH = 4.2, ROCK_REACH = 1.6;
	/** How long someone may stand by it, not crouching, before it stirs (ticks), and how near is by it. */
	private static final int STIR_TICKS = 40;
	private static final double STIR_NEAR = 3.5;
	/** Ticks between two rockfalls at most. */
	private static final int ROCK_EVERY = 140;
	/** Seconds it sleeps at most, no one coming near, before it sinks away. */
	private static final int SLEEP_SECONDS = 600;
	/** The tag of the stones on its back. */
	public static final String STONE_TAG = "extremedimensions.cragback_stone";

	static final class State {
		Mode mode = Mode.DORMANT;
		UUID target;
		int ticks;
		int age;
		int awake;
		int pause;
		int stirred;
		int sinceRocks;
		int slams;
		boolean anywhere;
		boolean shed;
		final List<Vec3> rocks = new ArrayList<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
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

	public static boolean fits(ServerPlayer p) {
		if (Config.CRAGBACK_ENABLED <= 0 || !Dims.SEAM.equals(Dims.of(p)) || p.isSpectator() || p.isCreative() || !p.isAlive()
				|| Rules.peaceful(p.level()) || com.extremedimensions.tribe.Standing.of(p) == com.extremedimensions.tribe.Standing.Level.KIN) {
			return false;
		}
		PlayerData d = DataStore.get(p.getUUID());
		return d.inSeam && d.seamPull <= 0 && !(com.extremedimensions.mending.MendingService.active() && com.extremedimensions.mending.MendingService.inArena(p));
	}

	public static void tick(MinecraftServer s) {
		drive();
		if (Config.CRAGBACK_ENABLED <= 0 || (s.getTickCount() + 50) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.CRAGBACK_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.CRAGBACK_SPAWN_CHANCE) {
				continue;
			}
			if (come(p, false) != null) {
				LAST.put(p.getUUID(), (long) s.getTickCount());
			}
		}
	}

	private static boolean after(ServerPlayer p) {
		for (State st : STATES.values()) {
			if (p.getUUID().equals(st.target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A Cragback lies asleep 16-28 blocks from this visitor, on ground its body fits, out of their sight (it was there all
	 * along). {@code anywhere} (an operator's summons) puts it nearer and awake.
	 */
	public static Cragback come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double w = ModEntities.CRAGBACK.getWidth(), h = ModEntities.CRAGBACK.getHeight();
		for (int i = 0; i < 32; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = (anywhere ? 8 : 16) + rnd.nextDouble() * (anywhere ? 4 : 12);
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			BlockPos feet = null;
			for (int dy = 8; dy >= -10 && feet == null; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(p.getY()) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, w, h) && level.getFluidState(q).isEmpty()) {
					feet = q;
				}
			}
			if (feet == null) {
				continue;
			}
			Vec3 body = Vec3.atBottomCenterOf(feet).add(0, 1.5, 0);
			if (!anywhere && level.clip(new ClipContext(p.getEyePosition(), body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
					.getType() == HitResult.Type.MISS) {
				continue;
			}
			Cragback c = ModEntities.CRAGBACK.spawn(level, feet, EntitySpawnReason.EVENT);
			if (c == null) {
				continue;
			}
			c.setYRot(rnd.nextFloat() * 360F);
			c.setYBodyRot(c.getYRot());
			State st = drive(level, c, p, anywhere);
			grow(level, c);
			if (anywhere) {
				wake(level, c, st, p);
			}
			return c;
		}
		return null;
	}

	private static State drive(ServerLevel level, Cragback c, ServerPlayer p, boolean anywhere) {
		c.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		STATES.put(c.getUUID(), st);
		LEVELS.put(c.getUUID(), level);
		c.setLurkerPose(Lurker.POSE_STALK);
		c.setLurkerMood(LurkerBody.MOOD_CALM);
		c.getNavigation().stop();
		return st;
	}

	/** Operator command: a Cragback comes up near the player, already awake. */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && come(p, true) != null;
	}

	// ------------------------------------------------------------------ the stones of its back

	/** One stone of its back: the block, how big, where (from the top of its body) and how far turned (degrees). */
	private record Stone(Block block, float size, float x, float y, float z, float turn) {}

	/**
	 * The stones of its back: pale hush stone and tuff (light against its dark cloak, so it reads as rock) with Tetherite
	 * ore among them, set well up on its hump so they stand clear of it.
	 */
	private static List<Stone> stones() {
		return List.of(new Stone(ModBlocks.POLISHED_PALE_HUSH_STONE, 1.25F, 0.0F, -0.35F, 0.1F, 12F),
				new Stone(net.minecraft.world.level.block.Blocks.TUFF, 0.9F, -0.55F, -0.2F, -0.35F, 38F),
				new Stone(ModBlocks.TETHERITE_ORE, 0.75F, 0.5F, -0.05F, -0.25F, 25F),
				new Stone(ModBlocks.TETHERITE_ORE, 0.55F, -0.1F, 0.85F, 0.25F, 61F),
				new Stone(ModBlocks.POLISHED_PALE_HUSH_STONE, 0.7F, 0.45F, -0.3F, 0.55F, 70F));
	}

	/**
	 * Its back grows over with stone and Tetherite: block displays riding it. The game's own way of setting what a display
	 * shows is reached through a mixin; were that ever missing, its back is simply bare and nothing else changes.
	 */
	private static void grow(ServerLevel level, Cragback c) {
		try {
			for (Stone s : stones()) {
				Display.BlockDisplay d = new Display.BlockDisplay(BlockFlight.blockDisplay(), level);
				d.setPos(c.getX(), c.getY() + c.getBbHeight(), c.getZ());
				d.addTag(STONE_TAG);
				((BlockDisplayAccess) d).extremedimensions$setBlockState(s.block().defaultBlockState());
				Quaternionf turn = new Quaternionf().rotateY((float) Math.toRadians(s.turn()));
				// (centred on its own middle, then turned, then set where it sits on the back)
				Vector3f half = new Vector3f(-s.size() / 2, 0, -s.size() / 2);
				turn.transform(half);
				((DisplayAccess) d).extremedimensions$setTransformation(new Transformation(new Vector3f(s.x() + half.x, s.y(), s.z() + half.z), turn,
						new Vector3f(s.size(), s.size(), s.size()), new Quaternionf()));
				if (!level.addFreshEntity(d)) {
					continue;
				}
				d.startRiding(c);
			}
		} catch (Throwable t) {
			// (its back stays bare)
		}
	}

	/** The stones on its back now. */
	public static int stones(Cragback c) {
		int n = 0;
		for (Entity e : c.getPassengers()) {
			if (e instanceof Display.BlockDisplay && e.entityTags().contains(STONE_TAG)) {
				n++;
			}
		}
		return n;
	}

	/** One stone of its back breaks off and falls in a burst of dust. */
	private static void shedOne(ServerLevel level, Cragback c) {
		for (Entity e : List.copyOf(c.getPassengers())) {
			if (e instanceof Display.BlockDisplay && e.entityTags().contains(STONE_TAG)) {
				e.stopRiding();
				e.discard();
				WorldFx.particleIn(level, "minecraft:block{block_state:{Name:\"extremedimensions:cobbled_hush_stone\"}}", c.getX(), c.getY() + c.getBbHeight(),
						c.getZ(), 0.6, 0.4, 0.6, 0.0, 30);
				WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.stone.break", "hostile", c.getX(), c.getY() + c.getBbHeight(), c.getZ(),
						1.4f, 0.6f);
				return;
			}
		}
	}

	/** Its back's stones taken down (it sank, or it died). */
	private static void bare(Cragback c) {
		for (Entity e : List.copyOf(c.getPassengers())) {
			if (e instanceof Display.BlockDisplay) {
				e.stopRiding();
				e.discard();
			}
		}
	}

	// ------------------------------------------------------------------ waking

	/**
	 * A block was broken: a Cragback asleep near enough wakes (Tetherite ore wakes one from twice as far), and comes for
	 * whoever broke it.
	 */
	public static void noise(ServerPlayer p, BlockPos pos, BlockState state) {
		if (STATES.isEmpty() || p.isCreative() || p.isSpectator()) {
			return;
		}
		double range = Math.max(2, Config.CRAGBACK_WAKE_RANGE) * (state.is(ModBlocks.TETHERITE_ORE) ? 2 : 1);
		for (Map.Entry<UUID, State> e : STATES.entrySet()) {
			State st = e.getValue();
			ServerLevel level = LEVELS.get(e.getKey());
			if (st.mode != Mode.DORMANT || level != p.level()) {
				continue;
			}
			if (level.getEntity(e.getKey()) instanceof Cragback c && c.position().distanceToSqr(Vec3.atCenterOf(pos)) <= range * range) {
				wake(level, c, st, p);
			}
		}
	}

	/** It wakes, and turns on this player. */
	private static void wake(ServerLevel level, Cragback c, State st, ServerPlayer p) {
		st.mode = Mode.WAKE;
		st.ticks = 0;
		st.target = p.getUUID();
		// More of them near, more of it to bring down
		int near = 0;
		for (ServerPlayer o : level.players()) {
			if (!o.isSpectator() && o.distanceToSqr(c) < 48 * 48) {
				near++;
			}
		}
		double health = Config.CRAGBACK_HEALTH * (1 + Math.max(0, Config.CRAGBACK_HEALTH_PER_PLAYER) * Math.max(0, near - 1));
		if (c.getAttribute(Attributes.MAX_HEALTH) != null && c.getMaxHealth() < health) {
			float share = c.getHealth() / c.getMaxHealth();
			c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
			c.setHealth((float) (health * share));
		}
		c.setLurkerPose(Lurker.POSE_WATCH);
		c.setLurkerMood(LurkerBody.MOOD_HUNTER);
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:entity.ravager.roar", "hostile", c.getX(), c.getY() + 2, c.getZ(), 2.5f, 0.45f);
		WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", c.getX(), c.getY() + 2, c.getZ(), 2.0f, 0.5f);
		WorldFx.punch(level, c, c.getX(), c.getZ(), 20, 0.6f, 20);
		com.extremedimensions.journal.Journal.unlock(p, DataStore.get(p.getUUID()), com.extremedimensions.journal.JournalEntry.TRIBE);
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
			if (!(ent instanceof Cragback c) || !c.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			boolean gone = p == null || p.level() != c.level() || p.distanceToSqr(c) > 80 * 80 || p.isSpectator() || p.isDeadOrDying();
			if (st.mode == Mode.DORMANT) {
				if (gone || st.age > SLEEP_SECONDS * 20) {
					later.add(() -> vanish(c));
				} else {
					dormant(level, c, st);
				}
				continue;
			}
			st.awake++;
			if ((gone || st.awake > Math.max(30, Config.CRAGBACK_SECONDS) * 20) && st.mode != Mode.SINK) {
				startSink(c, st);
			}
			if (!st.shed && c.getHealth() < c.getMaxHealth() / 2) {
				st.shed = true;
				shedOne(level, c);
			}
			switch (st.mode) {
				case WAKE -> waking(level, c, st);
				case WALK -> walk(level, c, st, p);
				case SLAM -> slam(level, c, st, p);
				case ROCKFALL -> rockfall(level, c, st, p);
				case SINK -> sink(c, st, later);
				default -> { }
			}
		}
		later.forEach(Runnable::run);
	}

	/** Asleep: still as stone. Someone standing by it, not crouching, stirs it awake. */
	private static void dormant(ServerLevel level, Cragback c, State st) {
		TribeMoves.stand(c);
		ServerPlayer by = null;
		for (ServerPlayer o : level.players()) {
			if (!o.isSpectator() && !o.isCreative() && !o.isShiftKeyDown() && o.distanceToSqr(c) <= (STIR_NEAR + c.getBbWidth() / 2) * (STIR_NEAR + c.getBbWidth() / 2)) {
				by = o;
				break;
			}
		}
		st.stirred = by == null ? Math.max(0, st.stirred - 1) : st.stirred + 1;
		if (by != null && st.stirred % 10 == 1) {
			// a low grinding, as of stone shifting
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.grindstone.use", "hostile", c.getX(), c.getY() + 1, c.getZ(), 0.4f, 0.4f);
		}
		if (by != null && st.stirred >= STIR_TICKS) {
			wake(level, c, st, by);
		}
	}

	/** It heaves itself up, stone dust falling from its back. */
	private static void waking(ServerLevel level, Cragback c, State st) {
		TribeMoves.stand(c);
		if (st.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", c.getX(), c.getY() + c.getBbHeight(), c.getZ(),
					1.0, 0.3, 1.0, 0.0, 6);
		}
		if (st.ticks >= WAKE_TICKS) {
			backToWalk(c, st, 10);
		}
	}

	/** Slow and heavy, after its prey; close, it slams; further off, it brings rocks down on them. */
	private static void walk(ServerLevel level, Cragback c, State st, ServerPlayer p) {
		if (p == null) {
			return;
		}
		c.setLurkerPose(Lurker.POSE_STALK);
		c.getLookControl().setLookAt(p, 20, 20);
		if (st.pause > 0) {
			st.pause--;
		}
		st.sinceRocks++;
		double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (st.pause <= 0 && flat <= SLAM_FROM && Math.abs(p.getY() - c.getY()) < 3) {
			st.mode = Mode.SLAM;
			st.ticks = 0;
			c.getNavigation().stop();
			c.setLurkerPose(Lurker.POSE_TAKE);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.ravager.attack", "hostile", c.getX(), c.getY() + 3, c.getZ(), 2.0f, 0.5f);
			return;
		}
		if (st.pause <= 0 && flat > 6 && st.sinceRocks > ROCK_EVERY && c.hasLineOfSight(p)) {
			st.mode = Mode.ROCKFALL;
			st.ticks = 0;
			st.sinceRocks = 0;
			st.rocks.clear();
			c.getNavigation().stop();
			c.setLurkerPose(Lurker.POSE_DRAIN);
			return;
		}
		if (st.ticks % 4 == 0) {
			double speed = Config.CRAGBACK_SPEED * (st.shed ? 1.25 : 1.0);
			TribeMoves.follow(c, p, speed);
			TribeMoves.trimBehind(c);
		}
	}

	/** Both fists raised, then the ground slammed: all near it hurt and thrown back; a shield turns it. */
	private static void slam(ServerLevel level, Cragback c, State st, ServerPlayer p) {
		TribeMoves.stand(c);
		if (p != null) {
			c.getLookControl().setLookAt(p, 30, 30);
		}
		if (st.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:dust{color:[0.35,0.86,0.8],scale:1.2}", c.getX(), c.getY() + 3.2, c.getZ(), 0.8, 0.4, 0.8, 0.0, 3);
		}
		if (st.ticks < SLAM_WINDUP) {
			return;
		}
		String dim = level.dimension().identifier().toString();
		for (ServerPlayer o : level.players()) {
			if (o.isSpectator() || o.isCreative() || o.distanceToSqr(c) > SLAM_REACH * SLAM_REACH || Math.abs(o.getY() - c.getY()) > 3) {
				continue;
			}
			double dx = o.getX() - c.getX(), dz = o.getZ() - c.getZ();
			double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
			if (o.isBlocking() && facing(o, c)) {
				WorldFx.soundIn(dim, "minecraft:item.shield.block", "players", o.getX(), o.getY() + 1, o.getZ(), 1.0f, 0.7f);
				o.setDeltaMovement(dx / d * 0.6, 0.2, dz / d * 0.6);
			} else {
				if (Config.CRAGBACK_DAMAGE > 0) {
					o.hurtServer(level, c.damageSources().mobAttack(c), (float) Config.CRAGBACK_DAMAGE);
				}
				o.setDeltaMovement(dx / d * 1.3, 0.55, dz / d * 1.3);
			}
			o.needsSync = true;
		}
		st.slams++;
		WorldFx.punch(level, c, c.getX(), c.getZ(), 24, 0.9f, 16);
		WorldFx.soundIn(dim, "minecraft:entity.iron_golem.attack", "hostile", c.getX(), c.getY(), c.getZ(), 2.0f, 0.45f);
		WorldFx.particleIn(level, "minecraft:explosion", c.getX(), c.getY() + 0.3, c.getZ(), 1.6, 0.2, 1.6, 0.0, 4);
		backToWalk(c, st, PAUSE);
	}

	/** It pounds the ground; rings of dust show where rocks will fall round its prey, and then they fall. */
	private static void rockfall(ServerLevel level, Cragback c, State st, ServerPlayer p) {
		TribeMoves.stand(c);
		String dim = level.dimension().identifier().toString();
		if (st.ticks == ROCK_WINDUP && p != null) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			WorldFx.soundIn(dim, "minecraft:entity.iron_golem.attack", "hostile", c.getX(), c.getY(), c.getZ(), 1.6f, 0.4f);
			WorldFx.punch(level, c, c.getX(), c.getZ(), 30, 0.6f, 12);
			st.rocks.add(new Vec3(p.getX(), p.getY(), p.getZ()));
			for (int i = 0; i < 2; i++) {
				double a = rnd.nextDouble() * Math.PI * 2, r = 1.8 + rnd.nextDouble() * 1.6;
				st.rocks.add(new Vec3(p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r));
			}
		}
		if (st.ticks > ROCK_WINDUP && st.ticks < ROCK_WINDUP + ROCK_DELAY && st.ticks % 3 == 0) {
			for (Vec3 r : st.rocks) {
				for (int k = 0; k < 8; k++) {
					double a = k * Math.PI / 4 + st.ticks * 0.1;
					WorldFx.particleIn(level, "minecraft:dust{color:[0.3,0.28,0.36],scale:1.4}", r.x + Math.cos(a) * ROCK_REACH, r.y + 0.1, r.z + Math.sin(a) * ROCK_REACH,
							0, 0, 0, 0, 1);
				}
				WorldFx.particleIn(level, "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", r.x, r.y + 7, r.z, 0.4, 0.2, 0.4, 0.0, 2);
			}
		}
		if (st.ticks == ROCK_WINDUP + ROCK_DELAY) {
			for (Vec3 r : st.rocks) {
				WorldFx.particleIn(level, "minecraft:block{block_state:{Name:\"extremedimensions:cobbled_hush_stone\"}}", r.x, r.y + 0.5, r.z, 0.6, 0.4, 0.6, 0.0, 24);
				WorldFx.soundIn(dim, "minecraft:block.stone.break", "hostile", r.x, r.y, r.z, 1.6f, 0.5f);
				for (ServerPlayer o : level.players()) {
					double dx = o.getX() - r.x, dz = o.getZ() - r.z;
					if (o.isSpectator() || o.isCreative() || dx * dx + dz * dz > ROCK_REACH * ROCK_REACH || Math.abs(o.getY() - r.y) > 3) {
						continue;
					}
					if (Config.CRAGBACK_ROCK_DAMAGE > 0) {
						o.hurtServer(level, c.damageSources().mobAttack(c), (float) Config.CRAGBACK_ROCK_DAMAGE);
					}
				}
			}
			st.rocks.clear();
			backToWalk(c, st, 20);
		}
	}

	/** Whether a player faces this creature (their shield is between them). */
	private static boolean facing(ServerPlayer p, Entity e) {
		Vec3 look = p.getViewVector(1.0f);
		double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		return d < 1e-3 || (look.x * dx + look.z * dz) / d > 0;
	}

	private static void backToWalk(Cragback c, State st, int pause) {
		st.mode = Mode.WALK;
		st.ticks = 0;
		st.pause = pause;
		c.setLurkerPose(Lurker.POSE_STALK);
	}

	private static void startSink(Cragback c, State st) {
		st.mode = Mode.SINK;
		st.ticks = 0;
		c.getNavigation().stop();
		c.setLurkerPose(Lurker.POSE_STALK);
		c.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	/** It settles, and sinks back into the stone. */
	private static void sink(Cragback c, State st, List<Runnable> later) {
		TribeMoves.stand(c);
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(c.level(), "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", c.getX(), c.getY() + 1, c.getZ(),
					1.0, 0.8, 1.0, 0.0, 8);
		}
		if (st.ticks >= 50) {
			later.add(() -> vanish(c));
		}
	}

	/** Gone back into the stone, the stones of its back with it. */
	public static void vanish(Cragback c) {
		STATES.remove(c.getUUID());
		LEVELS.remove(c.getUUID());
		bare(c);
		if (c.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", c.getX(), c.getY() + 1.5, c.getZ(),
					1.2, 1.0, 1.2, 0.0, 30);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.deepslate.break", "hostile", c.getX(), c.getY(), c.getZ(), 1.5f, 0.5f);
		}
		c.discard();
	}

	/** Struck: asleep, it wakes and turns on whoever struck it. */
	public static void onHurt(Cragback c, DamageSource source) {
		State st = STATES.get(c.getUUID());
		if (st == null || st.mode != Mode.DORMANT || !(c.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer by = source.getEntity() instanceof ServerPlayer sp ? sp : WorldFx.server().getPlayerList().getPlayer(st.target);
		if (by != null) {
			wake(level, c, st, by);
		}
	}

	/** Slain: the Tetherite and stone of its back fall where it dies. */
	public static void onDeath(Cragback c, Entity killer) {
		STATES.remove(c.getUUID());
		LEVELS.remove(c.getUUID());
		bare(c);
		if (!(c.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack s : Loot.roll(level, Loot.CRAGBACK, c.position())) {
			c.spawnAtLocation(level, s);
		}
		if (killer instanceof ServerPlayer p) {
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.cragback");
		}
	}

	// ------------------------------------------------------------------ for the tests and the screenshots

	/** Drives a Cragback already in the world, asleep, near this player; its back grown over. */
	public static void adopt(ServerLevel level, Cragback c, ServerPlayer p) {
		drive(level, c, p, true);
		grow(level, c);
	}

	/** Grows its back over (for the screenshots: one standing still, not driven). */
	public static void showBack(Cragback c) {
		if (c.level() instanceof ServerLevel level) {
			grow(level, c);
		}
	}

	public static String mode(Cragback c) {
		State st = STATES.get(c.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many times it has slammed the ground. */
	public static int slams(Cragback c) {
		State st = STATES.get(c.getUUID());
		return st == null ? 0 : st.slams;
	}
}
