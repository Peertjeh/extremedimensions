package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.extremedimensions.config.Config;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.mixin.BlockDisplayAccess;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Pale Haulers' mind (3.39). In the Pale Reach they come for those out over the void, in twos and threes:
 *
 * <ul>
 *   <li><b>WATCH</b>: it steps out of nothing a little way off and looks.</li>
 *   <li><b>SEEK</b>: it looks for a block someone laid out over the void (a bridge, a pillar) near its prey, and steps
 *       to it; with none to haul, it closes in.</li>
 *   <li><b>PULL</b>: the block shakes in its grip ({@code pale_hauler_pull_seconds}), then comes loose and flies to
 *       it, and it carries it off over its head. Never a block someone stands on or right beside.</li>
 *   <li><b>CROUCH</b> and <b>LEAP</b>: with nothing to haul it crouches a moment, then leaps at its prey.</li>
 *   <li><b>FLEE</b>: with as many blocks as it can carry ({@code pale_hauler_pulls}), or looked in the eye three
 *       times, or badly hurt, it steps away and is gone, with what it took.</li>
 * </ul>
 *
 * Looked in the eye it lets go of what it was pulling and steps away. Struck, it often steps away too. Slain, it drops
 * every block it took. Which blocks were laid is kept in memory only, for the blocks laid out in the Reach since the
 * server started; Haulers are never saved.
 */
public final class PaleHaulerService {
	private PaleHaulerService() {}

	enum Mode { WATCH, SEEK, PULL, CROUCH, LEAP, FLEE }

	/** Ticks it watches first, crouches before a leap, is in the air, ignores a look after it flinched, lives at most. */
	private static final int WATCH_TICKS = 20, CROUCH_TICKS = 10, LEAP_TICKS = 20, STARE_CALM = 30, AGE = 20 * 150;
	/** How far from its prey (blocks) it looks for something to haul. */
	private static final double HAUL_RANGE = 14;
	/** The tag on the block display it carries. */
	private static final String TAG = "extremedimensions_hauled";

	static final class State {
		Mode mode = Mode.WATCH;
		UUID target;
		int ticks;
		int age;
		int pulls;
		int stares;
		int calm;
		int leapCooldown = 40;
		boolean struck;
		BlockPos pulling;
		final List<BlockState> haul = new ArrayList<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** Blocks laid out in the Reach, by position, the last few thousand (what the Haulers come for). */
	private static final LinkedHashMap<Long, Long> LAID = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
			return size() > 4096;
		}
	};
	/** Blocks pulled loose, flying off to their Hauler. */
	private static final List<BlockFlight.Flight> FLIGHTS = new ArrayList<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
		LAST.clear();
		LAID.clear();
		for (BlockFlight.Flight f : FLIGHTS) {
			BlockFlight.cancel(f);
		}
		FLIGHTS.clear();
	}

	// ------------------------------------------------------------------ what was laid

	/** A block was laid: one laid in the Pale Reach (in the End, far enough out) is remembered, for the Haulers. */
	public static void laid(ServerLevel level, BlockPos pos) {
		if (Dims.END.equals(Dims.of(level)) && com.extremedimensions.reach.PaleReach.far(pos.getX(), pos.getZ()) && !level.getBlockState(pos).isAir()) {
			LAID.put(pos.asLong(), level.getGameTime());
		}
	}

	/** Counts a block as laid, wherever it is (for the tests). */
	public static void markLaid(BlockPos pos) {
		LAID.put(pos.asLong(), 0L);
	}

	// ------------------------------------------------------------------ coming

	public static boolean fits(ServerPlayer p) {
		return Config.PALE_HAULER_ENABLED > 0 && com.extremedimensions.reach.PaleReach.in(p) && !p.isSpectator() && !p.isCreative() && p.isAlive()
				&& !Rules.peaceful(p.level());
	}

	public static void tick(MinecraftServer s) {
		drive(s);
		if (!FLIGHTS.isEmpty()) {
			FLIGHTS.removeIf(BlockFlight::step);
		}
		if (Config.PALE_HAULER_ENABLED <= 0 || (s.getTickCount() + 130) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < 600) {
				continue;
			}
			int room = Math.max(0, Config.PALE_HAULER_MAX) - after(p);
			if (room <= 0 || rnd.nextDouble() >= Config.PALE_HAULER_SPAWN_CHANCE) {
				continue;
			}
			if (come(p, 1 + rnd.nextInt(room)) > 0) {
				LAST.put(p.getUUID(), (long) s.getTickCount());
			}
		}
	}

	/** How many Haulers are after this player. */
	public static int after(ServerPlayer p) {
		int n = 0;
		for (State st : STATES.values()) {
			if (p.getUUID().equals(st.target)) {
				n++;
			}
		}
		return n;
	}

	/** Ground near a spot, this far from it, with room for a Hauler; null if none was found. */
	private static Vec3 ground(ServerLevel level, Vec3 around, double min, double max) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 24; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = min + rnd.nextDouble() * Math.max(0.1, max - min);
			int x = (int) Math.floor(around.x + Math.cos(a) * d), z = (int) Math.floor(around.z + Math.sin(a) * d);
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(around.y) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.PALE_HAULER.getWidth(), ModEntities.PALE_HAULER.getHeight())) {
					return Vec3.atBottomCenterOf(q);
				}
			}
		}
		return null;
	}

	/** Up to this many Haulers step out of nothing near this player; how many came. */
	public static int come(ServerPlayer p, int n) {
		ServerLevel level = (ServerLevel) p.level();
		int came = 0;
		for (int i = 0; i < n; i++) {
			Vec3 at = ground(level, p.position(), 7, 15);
			if (at == null) {
				break;
			}
			PaleHauler h = ModEntities.PALE_HAULER.spawn(level, BlockPos.containing(at), EntitySpawnReason.EVENT);
			if (h == null) {
				break;
			}
			adopt(level, h, p);
			blinkFx(level, h.position());
			came++;
		}
		if (came > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pale_hauler.come").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
		return came;
	}

	/** Operator command: a Hauler steps out near the player, wherever they are. */
	public static boolean summon(ServerPlayer p) {
		return Dims.ours(p) && come(p, 1) > 0;
	}

	/** Drives a Hauler in the world after this player (from watching). */
	public static void adopt(ServerLevel level, PaleHauler h, ServerPlayer p) {
		h.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		STATES.put(h.getUUID(), st);
		LEVELS.put(h.getUUID(), level);
		h.setLurkerPose(Lurker.POSE_WATCH);
		h.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	// ------------------------------------------------------------------ what it does

	private static void drive(MinecraftServer s) {
		if (STATES.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof PaleHauler h) || !h.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			if (st.calm > 0) {
				st.calm--;
			}
			if (st.leapCooldown > 0) {
				st.leapCooldown--;
			}
			ServerPlayer p = s.getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != h.level() || p.distanceToSqr(h) > 64 * 64 || p.isDeadOrDying() || p.isSpectator() || st.age > AGE) {
				later.add(() -> vanish(h));
				continue;
			}
			if (st.mode != Mode.FLEE && st.calm == 0 && com.extremedimensions.events.Unanchored.staring(p, h)) {
				flinch(level, h, st, p);
				continue;
			}
			switch (st.mode) {
				case WATCH -> watch(h, st, p);
				case SEEK -> seek(level, h, st, p);
				case PULL -> pull(level, h, st, p);
				case CROUCH -> crouch(level, h, st, p);
				case LEAP -> leap(level, h, st, p);
				case FLEE -> flee(level, h, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	private static void watch(PaleHauler h, State st, ServerPlayer p) {
		h.getLookControl().setLookAt(p, 30, 30);
		TribeMoves.stand(h);
		if (st.ticks >= WATCH_TICKS) {
			toSeek(h, st);
		}
	}

	private static void toSeek(PaleHauler h, State st) {
		st.mode = Mode.SEEK;
		st.ticks = 0;
		st.pulling = null;
		h.setLurkerPose(Lurker.POSE_STALK);
		h.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	/** It looks for a laid block to haul near its prey, and steps to it; with none, it closes in, and leaps. */
	private static void seek(ServerLevel level, PaleHauler h, State st, ServerPlayer p) {
		h.getLookControl().setLookAt(p, 30, 30);
		if (st.ticks % 10 == 0) {
			BlockPos b = bridge(level, p);
			if (b != null) {
				Vec3 stand = ground(level, Vec3.atCenterOf(b), 1.2, 3.2);
				if (stand != null) {
					blink(level, h, stand);
					st.mode = Mode.PULL;
					st.ticks = 0;
					st.pulling = b;
					h.setLurkerPose(Lurker.POSE_TAKE);
					h.setLurkerMood(LurkerBody.MOOD_THIEF);
					WorldFx.soundIn(dimOf(h), "minecraft:block.stone.hit", "hostile", b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 1.0f, 0.6f);
					return;
				}
			}
			double d = h.distanceTo(p);
			if (st.leapCooldown <= 0 && d > 3 && d < 12 && h.onGround() && h.hasLineOfSight(p)) {
				st.mode = Mode.CROUCH;
				st.ticks = 0;
				h.setLurkerPose(Lurker.POSE_STALK);
				h.setLurkerMood(LurkerBody.MOOD_HUNTER);
				TribeMoves.stand(h);
				WorldFx.soundIn(dimOf(h), "minecraft:entity.enderman.stare", "hostile", h.getX(), h.getY() + 2, h.getZ(), 0.6f, 1.5f);
				return;
			}
		}
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(h, p, 1.25);
		}
	}

	/** The nearest laid block near its prey that it may haul, or null. */
	static BlockPos bridge(ServerLevel level, ServerPlayer p) {
		BlockPos best = null;
		double bestD = HAUL_RANGE * HAUL_RANGE;
		for (long k : LAID.keySet()) {
			int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
			double dx = x + 0.5 - p.getX(), dy = y + 0.5 - p.getY(), dz = z + 0.5 - p.getZ();
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 >= bestD) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			if (haulable(level, pos)) {
				best = pos;
				bestD = d2;
			}
		}
		return best;
	}

	/** Whether a Hauler may take this block: solid, holding nothing, open air under it, nobody on it or by it. */
	static boolean haulable(ServerLevel level, BlockPos pos) {
		if (!Loaded.ready(level, pos)) {
			return false;
		}
		BlockState s = level.getBlockState(pos);
		if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		for (int d = 1; d <= 3; d++) {
			if (!level.getBlockState(pos.below(d)).isAir()) {
				return false;
			}
		}
		if (underfoot(level, pos)) {
			return false;
		}
		for (State o : STATES.values()) {
			if (pos.equals(o.pulling)) {
				return false;
			}
		}
		return true;
	}

	/** Whether someone stands on this block or right beside it (it is never pulled from under anyone's feet). */
	static boolean underfoot(ServerLevel level, BlockPos pos) {
		for (ServerPlayer q : level.players()) {
			double dx = q.getX() - (pos.getX() + 0.5), dz = q.getZ() - (pos.getZ() + 0.5), dy = q.getY() - (pos.getY() + 1);
			if (!q.isSpectator() && Math.abs(dx) < 1.6 && Math.abs(dz) < 1.6 && dy > -1.5 && dy < 2.5) {
				return true;
			}
		}
		return false;
	}

	/** The block shakes in its grip, then comes loose and flies to it, and it holds it up over its head. */
	private static void pull(ServerLevel level, PaleHauler h, State st, ServerPlayer p) {
		BlockPos b = st.pulling;
		TribeMoves.stand(h);
		if (b == null || level.getBlockState(b).isAir()) {
			toSeek(h, st);
			return;
		}
		h.getLookControl().setLookAt(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
		if (st.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:reverse_portal", b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 0.35, 0.35, 0.35, 0.02, 5);
			WorldFx.particleIn(level, "minecraft:portal", h.getX(), h.getY() + 1.8, h.getZ(), 0.3, 0.5, 0.3, 0.3, 3);
		}
		if (st.ticks % 8 == 0) {
			WorldFx.soundIn(dimOf(h), "minecraft:block.stone.hit", "hostile", b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 0.9f, 0.6f);
		}
		if (st.ticks < (int) Math.round(Math.max(0.25, Config.PALE_HAULER_PULL_SECONDS) * 20)) {
			return;
		}
		if (underfoot(level, b) || h.distanceToSqr(Vec3.atCenterOf(b)) > 5.5 * 5.5) {
			toSeek(h, st);
			return;
		}
		BlockState state = level.getBlockState(b);
		level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
		LAID.remove(b.asLong());
		double[] to = {h.getX() - (b.getX() + 0.5), h.getY() + h.getBbHeight() + 0.3 - (b.getY() + 0.5), h.getZ() - (b.getZ() + 0.5)};
		BlockFlight.Flight f = BlockFlight.out(level, b, state, to, 8, "minecraft:portal", 2);
		if (f != null) {
			FLIGHTS.add(f);
		}
		st.haul.add(state);
		carry(level, h, state);
		WorldFx.soundIn(dimOf(h), "minecraft:entity.enderman.teleport", "hostile", b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 1.0f, 1.4f);
		WorldFx.soundIn(dimOf(h), "minecraft:block.stone.break", "hostile", b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 1.0f, 0.8f);
		st.pulls++;
		if (st.pulls >= Math.max(1, Config.PALE_HAULER_PULLS)) {
			startFlee(level, h, st, p);
		} else {
			toSeek(h, st);
		}
	}

	/** A moment crouched, glowing red: then the leap. */
	private static void crouch(ServerLevel level, PaleHauler h, State st, ServerPlayer p) {
		h.getLookControl().setLookAt(p, 30, 30);
		TribeMoves.stand(h);
		if (st.ticks % 2 == 0) {
			WorldFx.particleIn(level, "minecraft:portal", h.getX(), h.getY() + 0.4, h.getZ(), 0.4, 0.1, 0.4, 0.4, 4);
		}
		if (st.ticks < CROUCH_TICKS) {
			return;
		}
		double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ(), d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
		double speed = Math.min(1.25, 0.35 + d * 0.09);
		h.setDeltaMovement(dx / d * speed, 0.48, dz / d * speed);
		st.mode = Mode.LEAP;
		st.ticks = 0;
		st.struck = false;
		WorldFx.soundIn(dimOf(h), "minecraft:entity.enderman.scream", "hostile", h.getX(), h.getY() + 2, h.getZ(), 0.8f, 1.5f);
	}

	/** In the air: whoever it meets is struck, once; landed, it looks for work again. */
	private static void leap(ServerLevel level, PaleHauler h, State st, ServerPlayer p) {
		if (!st.struck && h.getBoundingBox().inflate(0.4).intersects(p.getBoundingBox())) {
			st.struck = true;
			if (Config.PALE_HAULER_DAMAGE > 0) {
				p.hurtServer(level, h.damageSources().mobAttack(h), (float) Config.PALE_HAULER_DAMAGE);
			}
		}
		if (st.ticks >= LEAP_TICKS || st.ticks > 4 && h.onGround()) {
			st.leapCooldown = 60 + ThreadLocalRandom.current().nextInt(40);
			toSeek(h, st);
		}
	}

	/** Looked in the eye: it lets go of what it was pulling and steps away; the third time, it goes. */
	private static void flinch(ServerLevel level, PaleHauler h, State st, ServerPlayer p) {
		st.stares++;
		st.calm = STARE_CALM;
		WorldFx.soundIn(dimOf(h), "minecraft:entity.enderman.hurt", "hostile", h.getX(), h.getY() + 2, h.getZ(), 0.8f, 1.4f);
		if (st.mode == Mode.PULL) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pale_hauler.flinch").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
		Vec3 to = ground(level, h.position(), 5, 9);
		if (to != null) {
			blink(level, h, to);
		}
		if (st.stares >= 3) {
			startFlee(level, h, st, p);
		} else {
			toSeek(h, st);
		}
	}

	private static void startFlee(ServerLevel level, PaleHauler h, State st, ServerPlayer p) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.pulling = null;
		h.setLurkerPose(Lurker.POSE_RUN);
		h.setLurkerMood(LurkerBody.MOOD_CALM);
		if (!st.haul.isEmpty()) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pale_hauler.going", st.haul.size()).withStyle(ChatFormatting.GRAY), true);
		}
	}

	private static void flee(ServerLevel level, PaleHauler h, State st, List<Runnable> later) {
		TribeMoves.stand(h);
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(level, "minecraft:portal", h.getX(), h.getY() + 1.4, h.getZ(), 0.3, 0.8, 0.3, 0.4, 6);
		}
		if (st.ticks >= 30) {
			later.add(() -> vanish(h));
		}
	}

	// ------------------------------------------------------------------ what it carries

	/** Holds this block up over its head (a block display riding it), or shows this one instead of the last. */
	private static void carry(ServerLevel level, PaleHauler h, BlockState state) {
		try {
			Display.BlockDisplay d = carried(h);
			if (d == null) {
				d = BlockFlight.spawn(level, h.getX(), h.getY() + h.getBbHeight(), h.getZ(), state,
						new Transformation(new Vector3f(-0.3F, 0.05F, -0.3F), new Quaternionf(), new Vector3f(0.6F, 0.6F, 0.6F), new Quaternionf()));
				if (d == null) {
					return;
				}
				d.addTag(TAG);
				d.startRiding(h);
			} else {
				((BlockDisplayAccess) d).extremedimensions$setBlockState(state);
			}
		} catch (Throwable t) {
			// (what it carries goes unseen)
		}
	}

	private static Display.BlockDisplay carried(PaleHauler h) {
		for (Entity e : h.getPassengers()) {
			if (e instanceof Display.BlockDisplay d && d.entityTags().contains(TAG)) {
				return d;
			}
		}
		return null;
	}

	/** Puts down what it held up (the display goes). */
	private static void unload(PaleHauler h) {
		for (Entity e : List.copyOf(h.getPassengers())) {
			if (e instanceof Display.BlockDisplay d) {
				d.stopRiding();
				BlockFlight.drop(d);
			}
		}
	}

	// ------------------------------------------------------------------ steps, blows and the end

	private static void blink(ServerLevel level, PaleHauler h, Vec3 to) {
		blinkFx(level, h.position());
		h.getNavigation().stop();
		h.teleportTo(to.x, to.y, to.z);
		blinkFx(level, to);
	}

	private static void blinkFx(ServerLevel level, Vec3 at) {
		WorldFx.particleIn(level, "minecraft:portal", at.x, at.y + 1.4, at.z, 0.35, 0.9, 0.35, 0.5, 24);
		WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.enderman.teleport", "hostile", at.x, at.y + 1, at.z, 0.8f, 1.3f);
	}

	/** Gone into nothing, with what it took. */
	public static void vanish(PaleHauler h) {
		STATES.remove(h.getUUID());
		LEVELS.remove(h.getUUID());
		unload(h);
		if (h.level() instanceof ServerLevel level) {
			blinkFx(level, h.position());
		}
		h.discard();
	}

	/** Struck: it lets go and often steps away; badly hurt, it goes. */
	public static void onHurt(PaleHauler h, DamageSource source) {
		State st = STATES.get(h.getUUID());
		if (st == null || !TribeMoves.struck(source) || !(h.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (p == null) {
			return;
		}
		if (h.getHealth() < h.getMaxHealth() * 0.3) {
			if (st.mode != Mode.FLEE) {
				startFlee(level, h, st, p);
			}
			return;
		}
		if (st.mode == Mode.PULL || ThreadLocalRandom.current().nextBoolean()) {
			Vec3 to = ground(level, h.position(), 4, 8);
			if (to != null) {
				blink(level, h, to);
			}
			if (st.mode != Mode.FLEE) {
				toSeek(h, st);
			}
		}
	}

	/** Slain: every block it took falls, with what it carried out of the Reach. */
	public static void onDeath(PaleHauler h, Entity killer) {
		State st = STATES.remove(h.getUUID());
		LEVELS.remove(h.getUUID());
		unload(h);
		if (!(h.level() instanceof ServerLevel level)) {
			return;
		}
		if (st != null) {
			for (BlockState s : st.haul) {
				Item item = s.getBlock().asItem();
				if (item != Items.AIR) {
					h.spawnAtLocation(level, new ItemStack(item));
				}
			}
			if (!st.haul.isEmpty() && killer instanceof ServerPlayer p) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.pale_hauler.back", st.haul.size()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
		}
		for (ItemStack s : Loot.roll(level, Loot.PALE_HAULER, h.position())) {
			h.spawnAtLocation(level, s);
		}
	}

	private static String dimOf(Entity e) {
		return e.level().dimension().identifier().toString();
	}

	// ------------------------------------------------------------------ for the tests and the lens

	public static String mode(PaleHauler h) {
		State st = STATES.get(h.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many blocks it carries now. */
	public static int hauled(PaleHauler h) {
		State st = STATES.get(h.getUUID());
		return st == null ? 0 : st.haul.size();
	}

	/** Sets it to look for work at once (skipping its first look). */
	public static void seekNow(PaleHauler h) {
		State st = STATES.get(h.getUUID());
		if (st != null) {
			toSeek(h, st);
			st.ticks = 9;
			st.leapCooldown = 200;
		}
	}
}
