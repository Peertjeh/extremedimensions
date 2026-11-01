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
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Cave Lurker's mind. Smaller than its kin, low enough for a miner's tunnel, it lives in the dark under the
 * Overworld and hunts by ear, not by eye:
 *
 * <ul>
 *   <li><b>LISTEN</b>: it crouches in the dark some way off, out of sight, and listens. Every sound the player makes
 *       near it (a step, more for a run or a jump, a block broken, a blow) adds to what it has heard, the nearer the
 *       more, and a little of it fades each moment; a player sneaking makes none. Its clicks quicken as it hears more,
 *       and once it has heard a good deal it creeps nearer. Heard enough, or come upon within a few blocks, it hunts;
 *       after a while with nothing heard, it slinks off.</li>
 *   <li><b>HUNT</b>: on all fours, glowing red, it runs the tunnels at the player and pounces: a short wind-up of
 *       rattling clicks, then a grab that hurts and always leaves a curse. A raised shield turns the grab aside; a soul
 *       ward or a Hollowling friend makes it flinch and flee. A prey it cannot get at, it stands and faces, and gives up
 *       after a few seconds.</li>
 *   <li><b>FLEE</b>: after its grab, badly hurt, or giving up, it scuttles off into the dark and is gone into the
 *       stone.</li>
 * </ul>
 *
 * Its way through the tunnels is kept from turning on the spot: a route is worked out again only when its prey has
 * really moved, the first steps of a fresh route that already lie behind it are skipped, close by it steps straight at
 * its prey and stands once it is there, it never turns more than a few tens of degrees a tick, and a walk that gets
 * nowhere is given a fresh route, then given up. Cave Lurkers are never saved: one not driven from here dissolves.
 */
public final class CaveLurkerService {
	private CaveLurkerService() {}

	enum Mode { LISTEN, HUNT, FLEE }

	/** Within this distance (centre to centre, across) it pounces; its grab lands a little further, as it lunges. */
	private static final double POUNCE = 1.9, GRAB = 2.7;
	/** Its prey above or below it by more than this, and it cannot pounce. */
	private static final double LEVEL = 1.5;
	/** Ticks of the pounce's wind-up (rattling clicks), of the pause after a grab turned aside or missed. */
	private static final int WINDUP = 8, RECOIL = 30, MISSED = 12;
	/** How often it asks whether its prey is out of its way, and checks that its walk is getting it somewhere (ticks). */
	private static final int BLOCK_CHECK = 10, STUCK_CHECK = 20;
	/** Walks that got nowhere in a row before it gives up (each given a fresh route first). */
	private static final int STUCK_GIVE_UP = 4;
	/** Within this distance and in sight of a still creeping prey, it hunts at once once it hears anything. */
	private static final double CREEP_STOP = 8;
	/** What a moment of quiet takes off what it has heard. */
	private static final double FADE = 0.15;
	/** Noise of one tick's step, run and jump (before the distance takes its share). */
	private static final double STEP = 0.8, RUN = 2.0, JUMP = 1.5;

	static final class State {
		Mode mode = Mode.LISTEN;
		UUID target;
		int ticks;
		int limit;
		/** What it has heard, and where it last heard its prey. */
		double heard;
		Vec3 heardAt;
		/** Its prey's place a tick ago (for the sound of their steps). */
		Vec3 preyWas;
		int windup;
		int pause;
		int blocked;
		/** Where it stood at the last check of its walk, and how many checks in a row it got nowhere. */
		Vec3 checkedAt;
		int stuck;
		Vec3 away;
		boolean grabbed;
		int curses;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	/** Server tick each player last had a Cave Lurker come (cooldown). */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** Sounds each player made since the last tick (blocks broken, blows), as one sum. */
	private static final Map<UUID, Double> NOISE = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
		LAST.clear();
		NOISE.clear();
	}

	public static void forget(UUID player) {
		NOISE.remove(player);
	}

	/** A sound the player made (a block broken, a blow): heard by a Cave Lurker listening for them, the nearer the more. */
	public static void noise(ServerPlayer p, double loud) {
		if (!STATES.isEmpty() && loud > 0) {
			NOISE.merge(p.getUUID(), loud, Double::sum);
		}
	}

	// ------------------------------------------------------------------ coming

	/** Whether a Cave Lurker may come to this player now: deep under the Overworld, no sky over them, not on Peaceful. */
	public static boolean fits(ServerPlayer p) {
		return Config.CAVE_LURKER_ENABLED > 0 && Dims.OVERWORLD.equals(Dims.of(p)) && Dims.ours(p) && !p.isSpectator() && !p.isCreative()
				&& p.isAlive() && !Rules.peaceful(p.level()) && p.getY() < Config.CAVE_LURKER_MAX_Y && !p.level().canSeeSky(p.blockPosition().above());
	}

	public static void tick(MinecraftServer s) {
		drive();
		NOISE.clear();
		// (half a cycle after the Lurker's own look, so the two never fall on one tick)
		if (Config.CAVE_LURKER_ENABLED <= 0 || (s.getTickCount() + 200) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.CAVE_LURKER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.CAVE_LURKER_SPAWN_CHANCE) {
				continue;
			}
			if (come(p, false) != null) {
				LAST.put(p.getUUID(), (long) s.getTickCount());
			}
		}
	}

	/** Whether a Cave Lurker is after this player already. */
	private static boolean after(ServerPlayer p) {
		for (State st : STATES.values()) {
			if (p.getUUID().equals(st.target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A Cave Lurker comes to wait for this player: in the dark, out of their sight, at a distance, on ground its body fits
	 * and from where it can walk to them. Null if no such place was found. {@code anywhere} (for an operator's summons)
	 * lets it wait in the light and in sight too.
	 */
	public static CaveLurker come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int min = Math.max(4, Config.CAVE_LURKER_DISTANCE_MIN), max = Math.max(min + 1, Config.CAVE_LURKER_DISTANCE_MAX);
		if (anywhere) {
			min = 6;
			max = 16;
		}
		Vec3 eye = p.getEyePosition();
		int tried = 0;
		for (int i = 0; i < 48 && tried < 3; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = min + rnd.nextDouble() * (max - min);
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			BlockPos feet = null;
			for (int dy = 4; dy >= -6 && feet == null; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(p.getY()) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.CAVE_LURKER.getWidth(), ModEntities.CAVE_LURKER.getHeight())) {
					feet = q;
				}
			}
			if (feet == null) {
				continue;
			}
			if (!anywhere) {
				if (level.canSeeSky(feet) || level.getBrightness(LightLayer.BLOCK, feet) > Config.CAVE_LURKER_MAX_LIGHT) {
					continue;
				}
				Vec3 body = Vec3.atBottomCenterOf(feet).add(0, 1.2, 0);
				if (level.clip(new ClipContext(eye, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
					continue;
				}
			}
			// (a route is worked out for it: no more than a few places are tried each time)
			tried++;
			CaveLurker cl = ModEntities.CAVE_LURKER.spawn(level, feet, EntitySpawnReason.EVENT);
			if (cl == null) {
				continue;
			}
			Path path = cl.getNavigation().createPath(p, 1);
			if (path == null || !path.canReach()) {
				cl.discard();
				continue;
			}
			cl.setPersistenceRequired();
			State st = new State();
			st.target = p.getUUID();
			st.limit = Math.max(5, Config.CAVE_LURKER_LISTEN_SECONDS) * 20;
			STATES.put(cl.getUUID(), st);
			LEVELS.put(cl.getUUID(), level);
			cl.setLurkerPose(Lurker.POSE_WATCH);
			cl.setLurkerMood(LurkerBody.MOOD_CALM);
			return cl;
		}
		return null;
	}

	/** Operator command: a Cave Lurker comes to listen near the player, wherever they are in a world of the mod. */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && come(p, true) != null;
	}

	// ------------------------------------------------------------------ what it does

	private static void drive() {
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof CaveLurker cl) || !cl.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != cl.level() || p.distanceToSqr(cl) > 72 * 72 || p.isDeadOrDying() || p.isCreative() || p.isSpectator()) {
				later.add(() -> vanish(cl));
				continue;
			}
			switch (st.mode) {
				case LISTEN -> listen(cl, st, p, later);
				case HUNT -> hunt(cl, st, p);
				case FLEE -> flee(cl, st, p, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** It crouches and listens; it creeps nearer once it has heard a good deal; it hunts once it has heard enough. */
	private static void listen(CaveLurker cl, State st, ServerPlayer p, List<Runnable> later) {
		double d = cl.distanceTo(p);
		double loud = steps(st, p) + NOISE.getOrDefault(p.getUUID(), 0.0);
		double heard = loud * Math.max(0, 1 - d / Math.max(1, Config.CAVE_LURKER_HEAR_RANGE));
		if (heard > 0) {
			st.heardAt = p.position();
		}
		st.heard = Math.max(0, st.heard + heard - FADE);
		double frac = Math.min(1, st.heard / Math.max(1, Config.CAVE_LURKER_HEAR));
		// Its clicks quicken as it hears more: the player can tell they are being listened for
		int every = (int) Math.round(70 - 50 * frac);
		if (st.ticks % Math.max(10, every) == 0) {
			WorldFx.soundIn(Dims.fullId(p), "minecraft:block.sculk_sensor.clicking", "hostile", cl.getX(), cl.getY() + 1.2, cl.getZ(),
					(float) (0.5 + 0.6 * frac), 1.5f + (float) (0.4 * frac));
		}
		if (d <= Config.CAVE_LURKER_NOTICE || st.heard >= Config.CAVE_LURKER_HEAR || (heard > 0 && d <= CREEP_STOP && cl.hasLineOfSight(p))) {
			startHunt(cl, st, p);
			return;
		}
		if (st.ticks > st.limit) {
			later.add(() -> vanish(cl));
			return;
		}
		if (st.heardAt != null) {
			cl.getLookControl().setLookAt(st.heardAt.x, st.heardAt.y + 1, st.heardAt.z, 10, 10);
		}
		// Heard a good deal: it creeps nearer to where it heard them, slowly
		if (frac >= 0.4 && st.heardAt != null && d > CREEP_STOP) {
			cl.setLurkerPose(Lurker.POSE_STALK);
			if (st.ticks % 5 == 0) {
				TribeMoves.go(cl, st.heardAt.x, st.heardAt.y, st.heardAt.z, 0.55);
				TribeMoves.trimBehind(cl);
			}
		} else {
			cl.setLurkerPose(Lurker.POSE_WATCH);
			TribeMoves.stand(cl);
		}
	}

	/** The sound of its prey's steps this tick: nothing while they sneak or stand, more for a run or a jump. */
	private static double steps(State st, ServerPlayer p) {
		Vec3 now = p.position(), was = st.preyWas;
		st.preyWas = now;
		if (was == null || p.isShiftKeyDown()) {
			return 0;
		}
		double dx = now.x - was.x, dz = now.z - was.z;
		double moved = Math.sqrt(dx * dx + dz * dz);
		double out = moved < 0.02 ? 0 : p.isSprinting() ? RUN : STEP;
		if (now.y - was.y > 0.25 && !p.onGround()) {
			out += JUMP;
		}
		return out;
	}

	private static void startHunt(CaveLurker cl, State st, ServerPlayer p) {
		st.mode = Mode.HUNT;
		st.ticks = 0;
		st.limit = Math.max(5, Config.CAVE_LURKER_HUNT_SECONDS) * 20;
		st.windup = 0;
		st.pause = 6;
		st.blocked = 0;
		st.stuck = 0;
		st.checkedAt = cl.position();
		cl.setLurkerPose(Lurker.POSE_RUN);
		cl.setLurkerMood(LurkerBody.MOOD_HUNTER);
		WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", cl.getX(), cl.getY() + 1.2, cl.getZ(), 1.3f, 1.7f);
	}

	/**
	 * It runs at its prey and pounces. Close by and in sight it winds up (rattling clicks, red motes) and grabs whoever is
	 * still within its lunge. A prey it cannot get at, it stands and faces, never circling; it gives up after a while.
	 */
	private static void hunt(CaveLurker cl, State st, ServerPlayer p) {
		cl.getLookControl().setLookAt(p, 30, 30);
		if (st.windup > 0) {
			TribeMoves.stand(cl);
			if (st.windup % 2 == 0) {
				WorldFx.particleIn(Dims.fullId(p), "minecraft:dust{color:[1.0,0.2,0.12],scale:0.8}", cl.getX(), cl.getY() + 1.1, cl.getZ(), 0.3, 0.3, 0.3, 0.0, 3);
			}
			if (--st.windup > 0) {
				return;
			}
			grab(cl, st, p);
			return;
		}
		if (st.ticks > st.limit || cl.getHealth() < cl.getMaxHealth() * 0.4) {
			startFlee(cl, st, p);
			return;
		}
		if (st.pause > 0) {
			st.pause--;
		}
		double dx = p.getX() - cl.getX(), dz = p.getZ() - cl.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		boolean level = Math.abs(p.getY() - cl.getY()) < LEVEL;
		if (st.pause <= 0 && flat <= POUNCE && level && cl.hasLineOfSight(p)) {
			TribeMoves.stand(cl);
			cl.setLurkerPose(Lurker.POSE_TAKE);
			st.windup = WINDUP;
			WorldFx.soundIn(Dims.fullId(p), "minecraft:block.sculk_sensor.clicking", "hostile", cl.getX(), cl.getY() + 1.2, cl.getZ(), 1.4f, 2.0f);
			WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_whisper", "hostile", cl.getX(), cl.getY() + 1.2, cl.getZ(), 1.0f, 1.6f);
			return;
		}
		// A prey out of its way (no whole route there): it stands and faces them, and gives up after a while
		if (st.ticks % BLOCK_CHECK == 0) {
			st.blocked = TribeMoves.blocked(cl, p) ? st.blocked + BLOCK_CHECK : 0;
			if (st.blocked >= Math.max(1, Config.CAVE_LURKER_GIVE_UP_SECONDS) * 20) {
				startFlee(cl, st, p);
				return;
			}
		}
		if (st.blocked >= 2 * BLOCK_CHECK) {
			TribeMoves.stand(cl);
			return;
		}
		// A walk that gets nowhere (caught on an edge, a route that no longer fits): a fresh route; then it gives up
		if (st.ticks % STUCK_CHECK == 0) {
			boolean nowhere = st.checkedAt != null && cl.position().distanceTo(st.checkedAt) < 0.4 && flat > TribeMoves.keepOff(cl, p) + 0.6;
			st.stuck = nowhere ? st.stuck + 1 : 0;
			st.checkedAt = cl.position();
			if (nowhere) {
				cl.getNavigation().stop();
			}
			if (st.stuck >= STUCK_GIVE_UP) {
				startFlee(cl, st, p);
				return;
			}
		}
		if (st.ticks % 2 == 0) {
			TribeMoves.follow(cl, p, Config.CAVE_LURKER_SPEED);
			TribeMoves.trimBehind(cl);
		}
		if (st.ticks % 6 == 0) {
			WorldFx.particleIn(Dims.fullId(p), "minecraft:dust{color:[0.85,0.12,0.1],scale:0.6}", cl.getX(), cl.getY() + 0.8, cl.getZ(), 0.25, 0.3, 0.25, 0.0, 1);
		}
	}

	/** The grab: it lands on a prey still within its lunge, hurts them, and leaves a curse, always. */
	private static void grab(CaveLurker cl, State st, ServerPlayer p) {
		cl.setLurkerPose(Lurker.POSE_RUN);
		double dx = p.getX() - cl.getX(), dz = p.getZ() - cl.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat > GRAB || Math.abs(p.getY() - cl.getY()) >= LEVEL + 0.1 || !cl.hasLineOfSight(p)) {
			// a step back in time, and it snaps at nothing
			st.pause = MISSED;
			return;
		}
		if (HollowService.guarded(p) || com.extremedimensions.binding.Charms.soulward(p)) {
			// a Hollowling friend leaps in, or the helmet's soul light flares: it flinches and flees
			WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.5, 0.0, 14);
			startFlee(cl, st, p);
			return;
		}
		if (p.isBlocking() && facing(p, cl)) {
			// a raised shield turns the grab aside: it is thrown back and tries again
			WorldFx.soundIn(Dims.fullId(p), "minecraft:item.shield.block", "players", p.getX(), p.getY() + 1, p.getZ(), 1.0f, 0.9f);
			double d = Math.max(0.3, flat);
			cl.setDeltaMovement(-dx / d * 0.6, 0.2, -dz / d * 0.6);
			cl.needsSync = true;
			st.pause = RECOIL;
			return;
		}
		boolean landed = true;
		if (Config.CAVE_LURKER_DAMAGE > 0 && cl.level() instanceof ServerLevel level) {
			landed = p.hurtServer(level, cl.damageSources().mobAttack(cl), (float) Config.CAVE_LURKER_DAMAGE);
		}
		if (!landed) {
			st.pause = MISSED;
			return;
		}
		WorldFx.soundIn(Dims.fullId(p), "minecraft:entity.warden.attack_impact", "hostile", p.getX(), p.getY() + 1, p.getZ(), 0.6f, 1.6f);
		boolean overworld = Dims.OVERWORLD.equals(Dims.of(p)) && DataStore.overworldPenalties();
		if (PenaltyService.curse(p, overworld, Config.CAVE_LURKER_CURSE_EXTREME_CHANCE) != null) {
			st.curses++;
			WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.6, 0.06, 20);
			WorldFx.soundFor(p, "extremedimensions:lurker_whisper", "hostile", cl.getX(), cl.getY() + 1.2, cl.getZ(), 1.0f, 1.2f);
		}
		st.grabbed = true;
		startFlee(cl, st, p);
	}

	/** Whether a player faces this creature (their shield is between them). */
	private static boolean facing(ServerPlayer p, Entity e) {
		Vec3 look = p.getViewVector(1.0f);
		double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		return d < 1e-3 || (look.x * dx + look.z * dz) / d > 0;
	}

	private static void startFlee(CaveLurker cl, State st, ServerPlayer p) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.windup = 0;
		st.limit = Math.max(1, Config.CAVE_LURKER_FLEE_SECONDS) * 20;
		st.away = hideaway(cl, p.position(), 14);
		cl.setLurkerPose(Lurker.POSE_RUN);
		cl.setLurkerMood(LurkerBody.MOOD_CALM);
		cl.getNavigation().stop();
		WorldFx.soundIn(Dims.fullId(p), "minecraft:block.sculk_sensor.clicking", "hostile", cl.getX(), cl.getY() + 1, cl.getZ(), 0.8f, 0.8f);
	}

	/** It scuttles off into the dark, and is gone into the stone once there, out of sight, or after a few seconds. */
	private static void flee(CaveLurker cl, State st, ServerPlayer p, List<Runnable> later) {
		boolean far = cl.distanceTo(p) > 16 && !p.hasLineOfSight(cl);
		boolean there = st.away != null && cl.position().distanceToSqr(st.away) < 1.5 * 1.5;
		if (st.away == null || st.ticks > st.limit || far || there) {
			later.add(() -> vanish(cl));
			return;
		}
		if (st.ticks % 5 == 1) {
			TribeMoves.go(cl, st.away.x, st.away.y, st.away.z, Config.CAVE_LURKER_SPEED);
			TribeMoves.trimBehind(cl);
		}
	}

	/**
	 * A spot to run to, away from {@code threat}, near its own height (under the ground, the surface is no way out): tried
	 * straight away first, then turning a little further each time to its own side.
	 */
	private static Vec3 hideaway(CaveLurker cl, Vec3 threat, double dist) {
		if (!(cl.level() instanceof ServerLevel level)) {
			return null;
		}
		double base = Math.atan2(cl.getZ() - threat.z, cl.getX() - threat.x);
		double side = (cl.getId() & 1) == 0 ? 1 : -1;
		for (int i = 0; i < 9; i++) {
			double ang = base + side * (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.4;
			int x = (int) Math.floor(cl.getX() + Math.cos(ang) * dist), z = (int) Math.floor(cl.getZ() + Math.sin(ang) * dist);
			for (int dy = 2; dy >= -4; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(cl.getY()) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, cl)) {
					return Vec3.atBottomCenterOf(q);
				}
			}
		}
		return null;
	}

	/** Gone into the stone: a crumble of grit and a last click. */
	public static void vanish(CaveLurker cl) {
		STATES.remove(cl.getUUID());
		LEVELS.remove(cl.getUUID());
		if (cl.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:smoke", cl.getX(), cl.getY() + 0.6, cl.getZ(), 0.3, 0.4, 0.3, 0.01, 10);
			WorldFx.particleIn(level, "extremedimensions:lurker_wisp", cl.getX(), cl.getY() + 0.9, cl.getZ(), 0.3, 0.5, 0.3, 0.0, 8);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.gravel.break", "hostile", cl.getX(), cl.getY(), cl.getZ(), 0.8f, 0.6f);
		}
		cl.discard();
	}

	/** Struck: one listening hunts at once; one hunting, badly hurt, flees. */
	public static void onHurt(CaveLurker cl, DamageSource source) {
		State st = STATES.get(cl.getUUID());
		if (st == null || st.mode == Mode.FLEE || !TribeMoves.struck(source)) {
			return;
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (p == null) {
			return;
		}
		if (st.mode == Mode.LISTEN) {
			startHunt(cl, st, p);
		} else if (cl.getHealth() < cl.getMaxHealth() * 0.4) {
			startFlee(cl, st, p);
		}
	}

	public static void onDeath(CaveLurker cl) {
		STATES.remove(cl.getUUID());
		LEVELS.remove(cl.getUUID());
	}

	/** Whether a Cave Lurker hunts this player near them (64 blocks) right now. */
	public static boolean hunting(ServerPlayer p) {
		for (Map.Entry<UUID, State> e : STATES.entrySet()) {
			State st = e.getValue();
			ServerLevel level = LEVELS.get(e.getKey());
			if (st.mode == Mode.HUNT && p.getUUID().equals(st.target) && level == p.level() && level.getEntity(e.getKey()) instanceof CaveLurker cl
					&& cl.isAlive() && cl.distanceToSqr(p) <= 64 * 64) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Cave Lurker already in the world after this player: "listen" waits for them, "hunt" comes at once. */
	public static void adopt(ServerLevel level, CaveLurker cl, ServerPlayer p, String mode) {
		State st = new State();
		st.target = p.getUUID();
		st.limit = Math.max(5, Config.CAVE_LURKER_LISTEN_SECONDS) * 20;
		STATES.put(cl.getUUID(), st);
		LEVELS.put(cl.getUUID(), level);
		cl.setPersistenceRequired();
		if ("hunt".equals(mode)) {
			startHunt(cl, st, p);
		}
	}

	/** The mode a driven Cave Lurker is in ("" if it is not driven). */
	public static String mode(CaveLurker cl) {
		State st = STATES.get(cl.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** What it has heard so far. */
	public static double heard(CaveLurker cl) {
		State st = STATES.get(cl.getUUID());
		return st == null ? 0 : st.heard;
	}

	/** Whether its grab landed, and the curses it left. */
	public static boolean grabbed(CaveLurker cl) {
		State st = STATES.get(cl.getUUID());
		return st != null && st.grabbed;
	}

	public static int curses(CaveLurker cl) {
		State st = STATES.get(cl.getUUID());
		return st == null ? 0 : st.curses;
	}
}
