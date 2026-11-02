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
import com.extremedimensions.lore.Loot;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Lockwarden's mind. In the End, once the Dragon has fallen, it comes for players who carry ender pearls:
 *
 * <ul>
 *   <li><b>WATCH</b>: it steps out of nowhere on ground a little way off and watches.</li>
 *   <li><b>CLOSE</b>: it comes on in steps, as an Enderman does, from one spot near its prey to another, closer each
 *       time.</li>
 *   <li><b>SNATCH</b>: a moment's reach (motes gathering in its hands), then it takes their ender pearls
 *       ({@code lockwarden_takes} at most), hurts them, and sometimes ({@code lockwarden_maroon_chance}) takes them with
 *       it: they find themselves on a lost islet of end stone some way out over the void, one pearl lying at their feet
 *       to get back with. A raised shield turns the reach.</li>
 *   <li><b>FLEE</b>: it steps away and is gone, with what it took.</li>
 * </ul>
 *
 * A blow makes it drop half of what it took and step away; slain, it drops it all. Carry no pearls and it has no
 * business with you. Lockwardens are never saved.
 */
public final class LockwardenService {
	private LockwardenService() {}

	enum Mode { WATCH, CLOSE, SNATCH, FLEE }

	/** Ticks between its steps while it closes in, of the snatch's reach, of watching first. */
	private static final int STEP_EVERY = 20, REACH_TICKS = 10, WATCH_TICKS = 50;
	/** How near it must be to reach for its prey (centre to centre, across). */
	private static final double REACH = 2.8;

	static final class State {
		Mode mode = Mode.WATCH;
		UUID target;
		int ticks;
		int age;
		int stolen;
		int pause;
		boolean marooned;
		boolean anywhere;
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

	/** How many ender pearls this player carries. */
	public static int pearls(ServerPlayer p) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.ENDER_PEARL)) {
				n += s.getCount();
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ coming

	public static boolean fits(ServerPlayer p) {
		return Config.LOCKWARDEN_ENABLED > 0 && Dims.END.equals(Dims.of(p)) && Dims.ours(p) && !p.isSpectator() && !p.isCreative()
				&& p.isAlive() && !Rules.peaceful(p.level()) && DataStore.sealBroken() && pearls(p) > 0
				&& com.extremedimensions.tribe.Standing.of(p) != com.extremedimensions.tribe.Standing.Level.KIN;
	}

	public static void tick(MinecraftServer s) {
		drive();
		if (Config.LOCKWARDEN_ENABLED <= 0 || (s.getTickCount() + 350) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.LOCKWARDEN_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.LOCKWARDEN_SPAWN_CHANCE) {
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

	/** Ground near a spot, this far from it (between the two), its body fitting there; null if none was found. */
	private static Vec3 ground(ServerLevel level, Vec3 around, double min, double max, Vec3 awayFrom) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 24; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			if (awayFrom != null) {
				double base = Math.atan2(around.z - awayFrom.z, around.x - awayFrom.x);
				a = base + (rnd.nextDouble() - 0.5) * 1.6;
			}
			double d = min + rnd.nextDouble() * Math.max(0.1, max - min);
			int x = (int) Math.floor(around.x + Math.cos(a) * d), z = (int) Math.floor(around.z + Math.sin(a) * d);
			for (int dy = 4; dy >= -6; dy--) {
				BlockPos q = new BlockPos(x, (int) Math.floor(around.y) + dy, z);
				if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, ModEntities.LOCKWARDEN.getWidth(), ModEntities.LOCKWARDEN.getHeight())) {
					return Vec3.atBottomCenterOf(q);
				}
			}
		}
		return null;
	}

	/** A Lockwarden steps out of nowhere near this player. Null if there is no ground for it. */
	public static Lockwarden come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = ground(level, p.position(), anywhere ? 5 : 10, anywhere ? 8 : 16, null);
		if (at == null) {
			return null;
		}
		Lockwarden l = ModEntities.LOCKWARDEN.spawn(level, BlockPos.containing(at), EntitySpawnReason.EVENT);
		if (l == null) {
			return null;
		}
		drive(level, l, p, anywhere);
		fx(level, l.position());
		return l;
	}

	private static State drive(ServerLevel level, Lockwarden l, ServerPlayer p, boolean anywhere) {
		l.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		STATES.put(l.getUUID(), st);
		LEVELS.put(l.getUUID(), level);
		l.setLurkerPose(Lurker.POSE_WATCH);
		l.setLurkerMood(LurkerBody.MOOD_THIEF);
		return st;
	}

	/** Operator command: a Lockwarden steps out near the player, wherever they are. */
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
			if (!(ent instanceof Lockwarden l) || !l.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != l.level() || p.distanceToSqr(l) > 96 * 96 || p.isDeadOrDying() || p.isSpectator()
					|| st.age > Math.max(20, Config.LOCKWARDEN_SECONDS) * 20) {
				later.add(() -> vanish(l));
				continue;
			}
			switch (st.mode) {
				case WATCH -> watch(l, st, p);
				case CLOSE -> close(level, l, st, p);
				case SNATCH -> snatch(level, l, st, p, later);
				case FLEE -> flee(level, l, st, p, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** It watches a while; with nothing on them worth taking, it goes. */
	private static void watch(Lockwarden l, State st, ServerPlayer p) {
		l.getLookControl().setLookAt(p, 30, 30);
		TribeMoves.stand(l);
		if (st.ticks >= WATCH_TICKS) {
			st.mode = Mode.CLOSE;
			st.ticks = 0;
			l.setLurkerPose(Lurker.POSE_STALK);
		}
	}

	/** It comes on in steps, from one spot near its prey to another, closer each time; close enough, it reaches. */
	private static void close(ServerLevel level, Lockwarden l, State st, ServerPlayer p) {
		l.getLookControl().setLookAt(p, 30, 30);
		if (st.pause > 0) {
			st.pause--;
		}
		double dx = p.getX() - l.getX(), dz = p.getZ() - l.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (st.pause <= 0 && flat <= REACH && Math.abs(p.getY() - l.getY()) < 2 && l.hasLineOfSight(p)) {
			st.mode = Mode.SNATCH;
			st.ticks = 0;
			l.setLurkerPose(Lurker.POSE_TAKE);
			TribeMoves.stand(l);
			WorldFx.soundIn(dimOf(l), "minecraft:entity.enderman.stare", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.0f, 0.7f);
			return;
		}
		if (st.ticks % STEP_EVERY == 0 && flat > REACH) {
			// a step to a spot nearer, behind or beside them
			Vec3 to = ground(level, p.position(), 1.6, Math.max(2.0, Math.min(flat * 0.5, 6)), null);
			if (to != null) {
				blink(level, l, to);
				return;
			}
		}
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(l, p, 1.1);
		}
	}

	/** The reach, then the snatch: their pearls taken, a wound, and sometimes a lost islet. */
	private static void snatch(ServerLevel level, Lockwarden l, State st, ServerPlayer p, List<Runnable> later) {
		l.getLookControl().setLookAt(p, 30, 30);
		TribeMoves.stand(l);
		if (st.ticks % 2 == 0) {
			WorldFx.particleIn(level, "minecraft:portal", l.getX(), l.getY() + 2, l.getZ(), 0.4, 0.4, 0.4, 0.4, 6);
		}
		if (st.ticks < REACH_TICKS) {
			return;
		}
		double dx = p.getX() - l.getX(), dz = p.getZ() - l.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat > REACH + 0.8) {
			// it reached for nothing: it comes on again
			backToClose(l, st, 12);
			return;
		}
		if (p.isBlocking() && facing(p, l)) {
			WorldFx.soundIn(dimOf(l), "minecraft:item.shield.block", "players", p.getX(), p.getY() + 1, p.getZ(), 1.0f, 0.8f);
			Vec3 to = ground(level, l.position(), 5, 9, p.position());
			if (to != null) {
				blink(level, l, to);
			}
			backToClose(l, st, 30);
			return;
		}
		int took = take(p, Math.max(1, Config.LOCKWARDEN_TAKES));
		if (took <= 0) {
			// nothing worth taking after all
			startFlee(level, l, st, p);
			return;
		}
		st.stolen += took;
		if (Config.LOCKWARDEN_DAMAGE > 0) {
			p.hurtServer(level, l.damageSources().mobAttack(l), (float) Config.LOCKWARDEN_DAMAGE);
		}
		WorldFx.soundIn(dimOf(l), "minecraft:entity.enderman.scream", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.0f, 0.8f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.lockwarden.snatch", took), true);
		if (!st.marooned && ThreadLocalRandom.current().nextDouble() < Config.LOCKWARDEN_MAROON_CHANCE && maroon(level, p)) {
			st.marooned = true;
		}
		startFlee(level, l, st, p);
	}

	/** Takes up to this many ender pearls from a player; how many it took. */
	private static int take(ServerPlayer p, int most) {
		int took = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && took < most; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.ENDER_PEARL)) {
				int n = Math.min(most - took, s.getCount());
				s.shrink(n);
				took += n;
			}
		}
		if (took > 0) {
			inv.setChanged();
		}
		return took;
	}

	/**
	 * Takes the player with it to a lost islet: a little disc of end stone some way out over the void (or, where the
	 * land is near, high over it), one ender pearl at their feet to get back with. False if no open air was found.
	 */
	public static boolean maroon(ServerLevel level, ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int far = Math.max(16, Config.LOCKWARDEN_MAROON_DISTANCE);
		for (int i = 0; i < 12; i++) {
			// (nearer, where the far air is not loaded or not open)
			int dist = i < 6 ? far : Math.max(16, far * (12 - i) / 8);
			double a = rnd.nextDouble() * Math.PI * 2;
			int cx = (int) Math.floor(p.getX() + Math.cos(a) * dist), cz = (int) Math.floor(p.getZ() + Math.sin(a) * dist);
			for (int up : new int[] {0, 8, 16, -8}) {
				int y = (int) Math.floor(p.getY()) + up;
				if (open(level, cx, y, cz)) {
					WorldFx.particleIn(level, "minecraft:portal", p.getX(), p.getY() + 1, p.getZ(), 0.5, 1.0, 0.5, 0.6, 40);
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							if (dx * dx + dz * dz <= 5) {
								BlockPos q = new BlockPos(cx + dx, y - 1, cz + dz);
								if (level.getBlockState(q).isAir()) {
									level.setBlockAndUpdate(q, Blocks.END_STONE.defaultBlockState());
								}
							}
						}
					}
					p.teleportTo(cx + 0.5, y, cz + 0.5);
					ItemEntity pearl = new ItemEntity(level, cx + 0.5, y + 0.2, cz + 0.5, new ItemStack(Items.ENDER_PEARL));
					pearl.setDeltaMovement(Vec3.ZERO);
					pearl.setDefaultPickUpDelay();
					level.addFreshEntity(pearl);
					WorldFx.particleIn(level, "minecraft:portal", cx + 0.5, y + 1, cz + 0.5, 0.5, 1.0, 0.5, 0.6, 40);
					WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.enderman.teleport", "hostile", cx + 0.5, y + 1, cz + 0.5, 1.0f, 0.6f);
					p.sendSystemMessage(Component.translatable("gui.extremedimensions.lockwarden.marooned"), true);
					return true;
				}
			}
		}
		return false;
	}

	/** Whether the air is open for an islet here: nothing in a 7 by 7 from just under its floor to well over a head. */
	private static boolean open(ServerLevel level, int cx, int y, int cz) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos q = new BlockPos(cx + dx, y + dy, cz + dz);
					if (!Loaded.ready(level, q) || !level.getBlockState(q).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void backToClose(Lockwarden l, State st, int pause) {
		st.mode = Mode.CLOSE;
		st.ticks = 1;
		st.pause = pause;
		l.setLurkerPose(Lurker.POSE_STALK);
	}

	private static void startFlee(ServerLevel level, Lockwarden l, State st, ServerPlayer p) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		l.setLurkerPose(Lurker.POSE_RUN);
		Vec3 to = ground(level, l.position(), 14, 22, p.position());
		if (to != null) {
			blink(level, l, to);
		}
	}

	/** It steps away once more and is gone, with what it took. */
	private static void flee(ServerLevel level, Lockwarden l, State st, ServerPlayer p, List<Runnable> later) {
		TribeMoves.stand(l);
		if (st.ticks >= 40) {
			later.add(() -> vanish(l));
		}
	}

	/** A step through nothing to a spot: a puff of the End's motes where it was and where it is. */
	private static void blink(ServerLevel level, Lockwarden l, Vec3 to) {
		fx(level, l.position());
		l.getNavigation().stop();
		l.teleportTo(to.x, to.y, to.z);
		fx(level, to);
	}

	private static void fx(ServerLevel level, Vec3 at) {
		WorldFx.particleIn(level, "minecraft:portal", at.x, at.y + 1.6, at.z, 0.4, 1.0, 0.4, 0.5, 30);
		WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.enderman.teleport", "hostile", at.x, at.y + 1, at.z, 0.9f, 0.7f);
	}

	/** Whether a player faces this creature (their shield is between them). */
	private static boolean facing(ServerPlayer p, Entity e) {
		Vec3 look = p.getViewVector(1.0f);
		double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		return d < 1e-3 || (look.x * dx + look.z * dz) / d > 0;
	}

	/** Gone into nothing, with what it took. */
	public static void vanish(Lockwarden l) {
		STATES.remove(l.getUUID());
		LEVELS.remove(l.getUUID());
		if (l.level() instanceof ServerLevel level) {
			fx(level, l.position());
		}
		l.discard();
	}

	/** Struck: half of what it took falls, and it steps away; badly hurt, it goes. */
	public static void onHurt(Lockwarden l, DamageSource source) {
		State st = STATES.get(l.getUUID());
		if (st == null || !TribeMoves.struck(source) || !(l.level() instanceof ServerLevel level)) {
			return;
		}
		if (st.stolen > 0) {
			int drop = (st.stolen + 1) / 2;
			st.stolen -= drop;
			l.spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL, drop));
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (p == null) {
			return;
		}
		if (l.getHealth() < l.getMaxHealth() * 0.4) {
			if (st.mode != Mode.FLEE) {
				startFlee(level, l, st, p);
			}
			return;
		}
		Vec3 to = ground(level, l.position(), 5, 9, source.getEntity() != null ? source.getEntity().position() : p.position());
		if (to != null) {
			blink(level, l, to);
		}
		if (st.mode == Mode.WATCH || st.mode == Mode.SNATCH) {
			backToClose(l, st, 20);
		}
	}

	/** Slain: all it took falls, with what it carried out of the End. */
	public static void onDeath(Lockwarden l, Entity killer) {
		State st = STATES.remove(l.getUUID());
		LEVELS.remove(l.getUUID());
		if (!(l.level() instanceof ServerLevel level)) {
			return;
		}
		if (st != null && st.stolen > 0) {
			l.spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL, st.stolen));
		}
		for (ItemStack s : Loot.roll(level, Loot.LOCKWARDEN, l.position())) {
			l.spawnAtLocation(level, s);
		}
		if (killer instanceof ServerPlayer p) {
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.lockwarden");
		}
	}

	private static String dimOf(Entity e) {
		return e.level().dimension().identifier().toString();
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Lockwarden already in the world after this player: "watch" first, "close" comes on at once. */
	public static void adopt(ServerLevel level, Lockwarden l, ServerPlayer p, String mode) {
		State st = drive(level, l, p, true);
		if ("close".equals(mode)) {
			st.mode = Mode.CLOSE;
			l.setLurkerPose(Lurker.POSE_STALK);
		}
	}

	public static String mode(Lockwarden l) {
		State st = STATES.get(l.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many pearls it carries now. */
	public static int stolen(Lockwarden l) {
		State st = STATES.get(l.getUUID());
		return st == null ? 0 : st.stolen;
	}

	/** Whether it left its prey on a lost islet. */
	public static boolean marooned(Lockwarden l) {
		State st = STATES.get(l.getUUID());
		return st != null && st.marooned;
	}
}
