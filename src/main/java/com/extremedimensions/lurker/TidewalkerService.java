package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.lore.Loot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Tidewalker's mind. Where a player rows or swims over deep water in the Overworld, it may rise out of the dark
 * beneath them:
 *
 * <ul>
 *   <li><b>STALK</b>: it circles under the surface a few blocks off, closing in, a trail of bubbles breaking above it
 *       the only sign.</li>
 *   <li><b>STRIKE</b>: it rushes up at them. A rower is thrown out of the boat (the boat is shoved away, not broken);
 *       a swimmer is seized and <b>dragged</b> down for a moment, hurt, breath torn out of them, and maybe cursed.
 *       A blow makes it let go. Then it circles again, {@code tidewalker_strikes} times at most.</li>
 *   <li><b>RETREAT</b>: done, badly hurt, or warded off, it sinks away into the deep.</li>
 * </ul>
 *
 * A light held up over the water (a torch or a lantern in hand) and the Conduit's power keep it off; and it will not
 * follow anyone ashore. Tidewalkers are never saved.
 */
public final class TidewalkerService {
	private TidewalkerService() {}

	enum Mode { STALK, STRIKE, DRAG, RETREAT }

	/** How near it must be to seize or capsize (blocks), and how far under the surface it swims while it circles. */
	private static final double REACH = 2.4, DEPTH = 1.8;
	/** Ticks its prey may stay out of the water before it gives up, and it may spend out of the water itself. */
	private static final int ASHORE = 100, DRY = 60;

	static final class State {
		Mode mode = Mode.STALK;
		UUID target;
		int ticks;
		int limit;
		int strikes;
		int curses;
		int ashore;
		int dry;
		double angle;
		double side = 1;
		boolean anywhere;
		boolean seized;
		Vec3 vel = Vec3.ZERO;
		Vec3 away;
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

	// ------------------------------------------------------------------ the water and what wards it

	/** Water here (never reading a chunk not loaded: that would load it). */
	private static boolean water(Level level, BlockPos pos) {
		return Loaded.ready(level, pos) && level.getFluidState(pos).is(FluidTags.WATER);
	}

	/** Whether there are at least three blocks of water at and under this spot. */
	public static boolean deep(Level level, BlockPos pos) {
		int n = 0;
		for (int dy = 0; dy >= -6 && n < 3; dy--) {
			BlockPos q = pos.offset(0, dy, 0);
			if (water(level, q)) {
				n++;
			} else if (n > 0) {
				return false;
			}
		}
		return n >= 3;
	}

	/** Where the air over the water begins in this column, near this height (or NaN when there is no surface there). */
	public static double surface(Level level, int x, int y, int z) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int dy = 6; dy >= -10; dy--) {
			q.set(x, y + dy, z);
			if (water(level, q) && !water(level, q.above())) {
				return y + dy + 1;
			}
		}
		return Double.NaN;
	}

	/** Whether this player is in a boat (or a raft) on deep water. */
	public static boolean rowing(ServerPlayer p) {
		Entity v = p.getVehicle();
		if (v == null) {
			return false;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()).getPath();
		return (path.contains("boat") || path.contains("raft")) && deep(p.level(), v.blockPosition());
	}

	/** Whether this player swims in deep water. */
	public static boolean swimming(ServerPlayer p) {
		return p.getVehicle() == null && wet(p) && deep(p.level(), p.blockPosition());
	}

	/** Whether this player is in the water (touching it, or the water stands where they are). */
	public static boolean wet(ServerPlayer p) {
		return p.isInWater() || water(p.level(), p.blockPosition()) || water(p.level(), BlockPos.containing(p.getEyePosition()));
	}

	/** Whether a light held up over the water (a torch or a lantern in hand) or the Conduit's power keeps it off. */
	public static boolean warded(ServerPlayer p) {
		return p.hasEffect(MobEffects.CONDUIT_POWER) || light(p.getMainHandItem()) || light(p.getOffhandItem());
	}

	private static boolean light(ItemStack s) {
		return s.is(Items.TORCH) || s.is(Items.SOUL_TORCH) || s.is(Items.LANTERN) || s.is(Items.SOUL_LANTERN);
	}

	// ------------------------------------------------------------------ coming

	public static boolean fits(ServerPlayer p) {
		return Config.TIDEWALKER_ENABLED > 0 && Dims.OVERWORLD.equals(Dims.of(p)) && Dims.ours(p) && !p.isSpectator() && !p.isCreative()
				&& p.isAlive() && !Rules.peaceful(p.level()) && (rowing(p) || swimming(p)) && !warded(p);
	}

	public static void tick(MinecraftServer s) {
		drive();
		// (an eighth of the way round the Lurker's own look)
		if (Config.TIDEWALKER_ENABLED <= 0 || (s.getTickCount() + 50) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.TIDEWALKER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			// (likelier in the dark)
			double chance = Config.TIDEWALKER_SPAWN_CHANCE * (p.level().isDarkOutside() ? 2 : 1);
			if (rnd.nextDouble() >= chance) {
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
	 * A Tidewalker rises under this player: in deep water some way off, a little under the surface. Null when there is
	 * no deep water there. {@code anywhere} (an operator's summons) lets it come close by.
	 */
	public static Tidewalker come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 32; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = (anywhere ? 5 : 10) + rnd.nextDouble() * (anywhere ? 4 : 6);
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (!Loaded.ready(level, new BlockPos(x, (int) p.getY(), z))) {
				continue;
			}
			double top = surface(level, x, (int) Math.floor(p.getY()), z);
			if (Double.isNaN(top)) {
				continue;
			}
			BlockPos at = BlockPos.containing(x, top - 3, z);
			if (!deep(level, at.above(2)) || !water(level, at) || !water(level, at.above()) || !water(level, at.above(2))) {
				continue;
			}
			Tidewalker t = ModEntities.TIDEWALKER.spawn(level, at, EntitySpawnReason.EVENT);
			if (t == null) {
				continue;
			}
			drive(level, t, p, anywhere);
			return t;
		}
		return null;
	}

	private static State drive(ServerLevel level, Tidewalker t, ServerPlayer p, boolean anywhere) {
		t.setPersistenceRequired();
		t.setNoGravity(true);
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		st.side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
		st.angle = Math.atan2(t.getZ() - p.getZ(), t.getX() - p.getX());
		st.limit = stalkTicks();
		STATES.put(t.getUUID(), st);
		LEVELS.put(t.getUUID(), level);
		t.setLurkerPose(Lurker.POSE_STALK);
		t.setLurkerMood(LurkerBody.MOOD_CALM);
		return st;
	}

	private static int stalkTicks() {
		return Math.max(2, Config.TIDEWALKER_STALK_SECONDS) * 20 + ThreadLocalRandom.current().nextInt(40);
	}

	/** Operator command: a Tidewalker rises near the player, if there is deep water about. */
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
			if (!(ent instanceof Tidewalker t) || !t.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != t.level() || p.distanceToSqr(t) > 64 * 64 || p.isDeadOrDying() || p.isSpectator()) {
				later.add(() -> vanish(t));
				continue;
			}
			// It swims; out of the water it falls and soon slips away
			t.setNoGravity(t.isInWater());
			st.dry = t.isInWater() ? 0 : st.dry + 1;
			if (st.dry > DRY) {
				later.add(() -> vanish(t));
				continue;
			}
			if (st.mode != Mode.RETREAT) {
				st.ashore = wet(p) || rowing(p) ? 0 : st.ashore + 1;
				if (st.ashore > ASHORE || (warded(p) && st.mode != Mode.DRAG)) {
					startRetreat(t, st, p);
				}
			}
			switch (st.mode) {
				case STALK -> stalk(level, t, st, p);
				case STRIKE -> strike(level, t, st, p);
				case DRAG -> drag(level, t, st, p);
				case RETREAT -> retreat(level, t, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** Steers it toward a spot through the water, easing in, and turns it the way it swims. */
	private static void swimTo(Tidewalker t, State st, Vec3 to, double speed) {
		Vec3 d = to.subtract(t.position());
		double len = d.length();
		Vec3 want = len < 1e-3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
		st.vel = st.vel.lerp(want, t.isInWater() ? 0.2 : 0.05);
		t.setDeltaMovement(t.isInWater() ? st.vel : new Vec3(st.vel.x * 0.3, t.getDeltaMovement().y, st.vel.z * 0.3));
		if (st.vel.horizontalDistanceSqr() > 1e-4) {
			float yaw = (float) (Mth.atan2(st.vel.z, st.vel.x) * Mth.RAD_TO_DEG) - 90.0F;
			t.setYRot(yaw);
			t.yBodyRot = yaw;
			t.yHeadRot = yaw;
		}
	}

	/** A spot near this one that is water (this one, or a block up or down), or null. */
	private static Vec3 wet(Level level, Vec3 at) {
		for (int dy : new int[] {0, 1, -1, 2}) {
			Vec3 q = at.add(0, dy, 0);
			if (water(level, BlockPos.containing(q)) && water(level, BlockPos.containing(q).above())) {
				return q;
			}
		}
		return null;
	}

	/** It circles under the surface, closing in; bubbles break above it. */
	private static void stalk(ServerLevel level, Tidewalker t, State st, ServerPlayer p) {
		t.setLurkerPose(Lurker.POSE_STALK);
		double k = Math.min(1, st.ticks / (double) Math.max(1, st.limit));
		double r = 8.5 - 4.0 * k;
		st.angle += 0.035 * st.side;
		double top = surface(level, Mth.floor(p.getX()), Mth.floor(p.getY()), Mth.floor(p.getZ()));
		double y = (Double.isNaN(top) ? p.getY() : top) - DEPTH - t.getBbHeight();
		Vec3 aim = wet(level, new Vec3(p.getX() + Math.cos(st.angle) * r, y, p.getZ() + Math.sin(st.angle) * r));
		if (aim == null) {
			aim = wet(level, new Vec3(p.getX(), y, p.getZ()));
		}
		if (aim != null) {
			swimTo(t, st, aim, 0.16 * Config.TIDEWALKER_SPEED);
		}
		if (st.ticks % 5 == 0) {
			double above = surface(level, Mth.floor(t.getX()), Mth.floor(t.getY()), Mth.floor(t.getZ()));
			if (!Double.isNaN(above)) {
				WorldFx.particleIn(level, "minecraft:bubble_pop", t.getX(), above + 0.05, t.getZ(), 0.3, 0.0, 0.3, 0.0, 3);
			}
		}
		if (st.ticks > st.limit) {
			startStrike(t, st, p);
		}
	}

	private static void startStrike(Tidewalker t, State st, ServerPlayer p) {
		st.mode = Mode.STRIKE;
		st.ticks = 0;
		t.setLurkerPose(Lurker.POSE_RUN);
		t.setLurkerMood(LurkerBody.MOOD_HUNTER);
		WorldFx.soundIn(dimOf(t), "minecraft:entity.drowned.shoot", "hostile", t.getX(), t.getY() + 1, t.getZ(), 1.2f, 0.6f);
	}

	/** It rushes up at its prey: a rower it throws out of the boat, a swimmer it seizes. */
	private static void strike(ServerLevel level, Tidewalker t, State st, ServerPlayer p) {
		Vec3 aim = p.position().add(0, rowing(p) ? -0.8 : -0.4, 0);
		swimTo(t, st, aim, 0.42 * Config.TIDEWALKER_SPEED);
		if (st.ticks % 2 == 0) {
			WorldFx.particleIn(level, "minecraft:bubble", t.getX(), t.getY() + 1.2, t.getZ(), 0.3, 0.5, 0.3, 0.05, 4);
		}
		if (t.position().add(0, t.getBbHeight() * 0.6, 0).distanceTo(p.position().add(0, 0.5, 0)) <= REACH) {
			if (rowing(p)) {
				capsize(level, t, st, p);
			} else if (wet(p)) {
				startDrag(level, t, st, p);
			}
			return;
		}
		if (st.ticks > 80) {
			again(t, st);
		}
	}

	/** Thrown out of the boat: the boat shoved away, the rower in the water. */
	private static void capsize(ServerLevel level, Tidewalker t, State st, ServerPlayer p) {
		Entity boat = p.getVehicle();
		p.stopRiding();
		if (boat != null) {
			Vec3 d = new Vec3(boat.getX() - t.getX(), 0, boat.getZ() - t.getZ());
			d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
			boat.setDeltaMovement(d.x * 0.9, 0.45, d.z * 0.9);
			boat.setYRot(boat.getYRot() + 75.0F);
			boat.needsSync = true;
		}
		p.setDeltaMovement(p.getDeltaMovement().add(0, -0.25, 0));
		p.needsSync = true;
		WorldFx.particleIn(level, "minecraft:splash", p.getX(), p.getY() + 0.5, p.getZ(), 1.0, 0.3, 1.0, 0.2, 40);
		WorldFx.soundIn(dimOf(t), "minecraft:entity.generic.splash", "hostile", p.getX(), p.getY(), p.getZ(), 1.4f, 0.7f);
		st.strikes++;
		again(t, st);
	}

	private static void startDrag(ServerLevel level, Tidewalker t, State st, ServerPlayer p) {
		st.mode = Mode.DRAG;
		st.ticks = 0;
		st.seized = true;
		t.setLurkerPose(Lurker.POSE_TAKE);
		if (Config.TIDEWALKER_DAMAGE > 0) {
			p.hurtServer(level, t.damageSources().mobAttack(t), (float) Config.TIDEWALKER_DAMAGE);
		}
		WorldFx.soundIn(dimOf(t), "minecraft:entity.drowned.hurt_water", "hostile", p.getX(), p.getY(), p.getZ(), 1.3f, 0.5f);
		if (st.curses < 1 && ThreadLocalRandom.current().nextDouble() < Config.TIDEWALKER_CURSE_CHANCE) {
			boolean overworld = DataStore.overworldPenalties();
			if (PenaltyService.curse(p, overworld, 0, com.extremedimensions.penalty.CurseLog.Source.TIDEWALKER) != null) {
				st.curses++;
				WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.6, 0.06, 16);
			}
		}
	}

	/** It holds them under and pulls them down, the breath going out of them; then it lets go. */
	private static void drag(ServerLevel level, Tidewalker t, State st, ServerPlayer p) {
		int hold = Math.max(1, Config.TIDEWALKER_DRAG_SECONDS) * 20;
		if (st.ticks > hold || !wet(p)) {
			release(t, st);
			return;
		}
		swimTo(t, st, p.position().add(0, -t.getBbHeight() * 0.7, 0), 0.3);
		Vec3 to = t.position().subtract(p.position());
		p.setDeltaMovement(to.x * 0.08, -0.14, to.z * 0.08);
		p.needsSync = true;
		p.setAirSupply(Math.max(0, p.getAirSupply() - Math.max(0, Config.TIDEWALKER_AIR_DRAIN)));
		if (st.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:bubble", p.getX(), p.getY() + 1.2, p.getZ(), 0.3, 0.3, 0.3, 0.05, 6);
		}
	}

	/** It lets go and swims off to circle again (or, its strikes spent, goes). */
	private static void release(Tidewalker t, State st) {
		st.strikes++;
		again(t, st);
	}

	private static void again(Tidewalker t, State st) {
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (st.strikes >= Math.max(1, Config.TIDEWALKER_STRIKES) || p == null) {
			startRetreat(t, st, p);
			return;
		}
		st.mode = Mode.STALK;
		st.ticks = 0;
		st.limit = stalkTicks();
		st.angle = p == null ? st.angle : Math.atan2(t.getZ() - p.getZ(), t.getX() - p.getX());
		t.setLurkerPose(Lurker.POSE_STALK);
		t.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	private static void startRetreat(Tidewalker t, State st, ServerPlayer p) {
		st.mode = Mode.RETREAT;
		st.ticks = 0;
		Vec3 from = p == null ? t.position() : p.position();
		Vec3 d = new Vec3(t.getX() - from.x, 0, t.getZ() - from.z);
		d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
		st.away = t.position().add(d.scale(12)).add(0, -4, 0);
		t.setLurkerPose(Lurker.POSE_RUN);
		t.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	/** It sinks away into the deep, and is gone. */
	private static void retreat(ServerLevel level, Tidewalker t, State st, List<Runnable> later) {
		Vec3 aim = st.away == null ? null : wet(level, st.away);
		if (aim == null) {
			aim = t.position().add(0, -1, 0);
		}
		swimTo(t, st, aim, 0.3);
		if (st.ticks > 60) {
			later.add(() -> vanish(t));
		}
	}

	/** Gone into the deep: a last stream of bubbles. */
	public static void vanish(Tidewalker t) {
		STATES.remove(t.getUUID());
		LEVELS.remove(t.getUUID());
		if (t.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:bubble_column_up", t.getX(), t.getY() + 1, t.getZ(), 0.4, 0.8, 0.4, 0.1, 20);
			WorldFx.particleIn(level, "extremedimensions:lurker_wisp", t.getX(), t.getY() + 1.2, t.getZ(), 0.3, 0.6, 0.3, 0.0, 8);
		}
		t.discard();
	}

	/** Struck: it lets go of whoever it holds; one circling is found out and strikes at once; badly hurt, it goes. */
	public static void onHurt(Tidewalker t, DamageSource source) {
		State st = STATES.get(t.getUUID());
		if (st == null || !TribeMoves.struck(source) || st.mode == Mode.RETREAT) {
			return;
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (t.getHealth() < t.getMaxHealth() * 0.4) {
			startRetreat(t, st, p);
		} else if (st.mode == Mode.DRAG) {
			release(t, st);
		} else if (st.mode == Mode.STALK && p != null) {
			startStrike(t, st, p);
		}
	}

	/** Slain: it gives up what it took from the wrecks of the deep. */
	public static void onDeath(Tidewalker t, Entity killer) {
		STATES.remove(t.getUUID());
		LEVELS.remove(t.getUUID());
		if (!(t.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack s : Loot.roll(level, Loot.TIDEWALKER, t.position())) {
			t.spawnAtLocation(level, s);
		}
		if (killer instanceof ServerPlayer p) {
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.tidewalker");
		}
	}

	private static String dimOf(Entity e) {
		return e.level().dimension().identifier().toString();
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Tidewalker already in the world after this player: "stalk" circles, "strike" comes at once. */
	public static void adopt(ServerLevel level, Tidewalker t, ServerPlayer p, String mode) {
		State st = drive(level, t, p, true);
		if ("strike".equals(mode)) {
			startStrike(t, st, p);
		}
	}

	public static String mode(Tidewalker t) {
		State st = STATES.get(t.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** Whether it has seized anyone yet. */
	public static boolean seized(Tidewalker t) {
		State st = STATES.get(t.getUUID());
		return st != null && st.seized;
	}
}
