package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.world.WorldService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Gatebreaker's mind. In a Wound Storm, after the Dragon has fallen, a giant of the tribe may come through a tear
 * in the storm for whoever the storm rages round:
 *
 * <ul>
 *   <li><b>WALK</b>: slow and heavy, straight for them. Where walls stand between it and them (no way round), it
 *       breaks through.</li>
 *   <li><b>SMASH</b>: it draws back its fists (dust trickling from the wall, a rumble) and brings them down: the blocks
 *       of the wall before it break and fall (where mobGriefing allows and {@code gatebreaker_breaks} is on), up to
 *       {@code gatebreaker_smash_blocks} at a blow, but nothing as hard as {@code gatebreaker_hardness} (obsidian,
 *       for one, holds), no chest or furnace, no anchor.</li>
 *   <li><b>SLAM</b>: close enough, it raises both fists and slams the ground: all near it are hurt and thrown back; a
 *       raised shield turns it.</li>
 *   <li><b>LEAVE</b>: when the storm passes, or it has stood long enough, it steps back into the tear.</li>
 * </ul>
 *
 * More players near, more health it has. Gatebreakers are never saved.
 */
public final class GatebreakerService {
	private GatebreakerService() {}

	enum Mode { WALK, SMASH, SLAM, LEAVE }

	/** Ticks of the smash's and the slam's wind-up, and of the pause after each. */
	private static final int SMASH_WINDUP = 24, SLAM_WINDUP = 16, PAUSE = 30;
	/** How near it must be to slam (centre to centre, across), and how far the slam reaches. */
	private static final double SLAM_FROM = 3.4, SLAM_REACH = 3.6;
	/** Seconds walled off with nothing it can break before it gives up. */
	private static final int GIVE_UP_SECONDS = 15;

	static final class State {
		Mode mode = Mode.WALK;
		UUID target;
		int ticks;
		int age;
		int pause;
		int walled;
		int stuck;
		int smashes;
		boolean anywhere;
		Vec3 checkedAt;
		List<BlockPos> wall = List.of();
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
		return Config.GATEBREAKER_ENABLED > 0 && Dims.OVERWORLD.equals(Dims.of(p)) && Dims.ours(p) && !p.isSpectator() && !p.isCreative()
				&& p.isAlive() && !Rules.peaceful(p.level()) && WorldService.storming(p)
				&& com.extremedimensions.tribe.Standing.of(p) != com.extremedimensions.tribe.Standing.Level.KIN;
	}

	public static void tick(MinecraftServer s) {
		drive();
		if (Config.GATEBREAKER_ENABLED <= 0 || (s.getTickCount() + 250) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.GATEBREAKER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.GATEBREAKER_CHANCE) {
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
	 * A Gatebreaker steps out of a tear in the storm 20-28 blocks from this player, on ground its body fits. Null if
	 * no such ground was found. {@code anywhere} (an operator's summons) lets it come closer, storm or no storm.
	 */
	public static Gatebreaker come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 24; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = (anywhere ? 10 : 20) + rnd.nextDouble() * 8;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (!Loaded.ready(level, new BlockPos(x, (int) p.getY(), z))) {
				continue;
			}
			BlockPos feet = TribeMoves.surface(level, x, z, (int) p.getY() + 10, ModEntities.GATEBREAKER.getWidth(), ModEntities.GATEBREAKER.getHeight());
			if (feet == null || Math.abs(feet.getY() - p.getY()) > 16) {
				continue;
			}
			Gatebreaker g = ModEntities.GATEBREAKER.spawn(level, feet, EntitySpawnReason.EVENT);
			if (g == null) {
				continue;
			}
			drive(level, g, p, anywhere);
			// Out of a tear in the storm, with a crack of thunder
			String dim = level.dimension().identifier().toString();
			WorldFx.particleIn(dim, "minecraft:reverse_portal", g.getX(), g.getY() + 2.5, g.getZ(), 1.0, 2.0, 1.0, 0.4, 80);
			WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", g.getX(), g.getY() + 2.5, g.getZ(), 1.0, 2.0, 1.0, 0.0, 20);
			WorldFx.soundIn(dim, "minecraft:entity.lightning_bolt.thunder", "hostile", g.getX(), g.getY() + 2, g.getZ(), 3.0f, 0.5f);
			WorldFx.soundIn(dim, "minecraft:entity.ravager.roar", "hostile", g.getX(), g.getY() + 3, g.getZ(), 3.0f, 0.5f);
			return g;
		}
		return null;
	}

	private static State drive(ServerLevel level, Gatebreaker g, ServerPlayer p, boolean anywhere) {
		g.setPersistenceRequired();
		// More of them near, more of it to bring down
		int near = 0;
		for (ServerPlayer o : level.players()) {
			if (!o.isSpectator() && o.distanceToSqr(g) < 48 * 48) {
				near++;
			}
		}
		double health = Config.GATEBREAKER_HEALTH * (1 + Math.max(0, Config.GATEBREAKER_HEALTH_PER_PLAYER) * Math.max(0, near - 1));
		if (g.getAttribute(Attributes.MAX_HEALTH) != null) {
			g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
			g.setHealth(g.getMaxHealth());
		}
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		st.checkedAt = g.position();
		st.pause = 20;
		STATES.put(g.getUUID(), st);
		LEVELS.put(g.getUUID(), level);
		g.setLurkerPose(Lurker.POSE_STALK);
		g.setLurkerMood(LurkerBody.MOOD_HUNTER);
		return st;
	}

	/** Operator command: a Gatebreaker comes for the player, storm or no storm. */
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
			if (!(ent instanceof Gatebreaker g) || !g.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			boolean gone = p == null || p.level() != g.level() || p.distanceToSqr(g) > 96 * 96 || p.isSpectator() || p.isDeadOrDying();
			boolean over = st.age > Math.max(30, Config.GATEBREAKER_SECONDS) * 20 || (!st.anywhere && p != null && !WorldService.storming(p));
			if ((gone || over) && st.mode != Mode.LEAVE) {
				startLeave(g, st);
			}
			switch (st.mode) {
				case WALK -> walk(level, g, st, p);
				case SMASH -> smash(level, g, st, p);
				case SLAM -> slam(level, g, st, p);
				case LEAVE -> leave(g, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** Slow and heavy, straight for them; close, it slams; walled off, it breaks through (or, at last, gives up). */
	private static void walk(ServerLevel level, Gatebreaker g, State st, ServerPlayer p) {
		g.setLurkerPose(Lurker.POSE_STALK);
		g.getLookControl().setLookAt(p, 20, 20);
		if (st.pause > 0) {
			st.pause--;
		}
		double dx = p.getX() - g.getX(), dz = p.getZ() - g.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (st.pause <= 0 && flat <= SLAM_FROM && Math.abs(p.getY() - g.getY()) < 2.5) {
			startSlam(g, st);
			return;
		}
		if (st.ticks % 10 == 0) {
			boolean blocked = TribeMoves.blocked(g, p);
			boolean nowhere = false;
			if (st.ticks % 20 == 0) {
				nowhere = st.checkedAt != null && g.position().distanceTo(st.checkedAt) < 0.3 && flat > SLAM_FROM + 1;
				st.checkedAt = g.position();
				st.stuck = nowhere ? st.stuck + 1 : 0;
			}
			if (st.pause <= 0 && (blocked || st.stuck >= 2)) {
				List<BlockPos> wall = canBreak(level) ? wall(level, g, p) : List.of();
				if (!wall.isEmpty()) {
					startSmash(level, g, st, wall);
					return;
				}
				st.walled += 10;
				if (st.walled > GIVE_UP_SECONDS * 20) {
					WorldFx.soundIn(dimOf(g), "minecraft:entity.ravager.roar", "hostile", g.getX(), g.getY() + 3, g.getZ(), 2.5f, 0.5f);
					startLeave(g, st);
					return;
				}
			} else if (!blocked) {
				st.walled = 0;
			}
		}
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(g, p, Config.GATEBREAKER_SPEED);
			TribeMoves.trimBehind(g);
		}
	}

	/** Whether it may break blocks here: the world's mobGriefing rule and its own setting. */
	public static boolean canBreak(ServerLevel level) {
		return Config.GATEBREAKER_BREAKS > 0 && TribeMoves.griefing(level);
	}

	/** Whether it can break this block: solid, softer than its limit, no block entity (chest, furnace), no anchor. */
	public static boolean breakable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.isAir() || s.getCollisionShape(level, pos).isEmpty() || s.is(BlockTags.WITHER_IMMUNE) || level.getBlockEntity(pos) != null) {
			return false;
		}
		if (com.extremedimensions.anchor.AnchorService.isAnchorBlock(s)) {
			return false;
		}
		float hard = s.getDestroySpeed(level, pos);
		return hard >= 0 && hard < Config.GATEBREAKER_HARDNESS;
	}

	/**
	 * The wall between it and its prey: the first solid block on the way to them, and the blocks of the wall round it
	 * (three wide, as tall as it is, two deep) that it can break, nearest first, as many as one blow breaks. Empty when
	 * nothing on the way can be broken.
	 */
	public static List<BlockPos> wall(ServerLevel level, Gatebreaker g, Entity t) {
		Vec3 d = new Vec3(t.getX() - g.getX(), 0, t.getZ() - g.getZ());
		if (d.lengthSqr() < 1e-4) {
			return List.of();
		}
		d = d.normalize();
		double half = g.getBbWidth() / 2;
		int foot = (int) Math.floor(g.getY() + 0.01);
		int tall = (int) Math.ceil(g.getBbHeight());
		BlockPos hit = null;
		for (double s = 0.3; s <= 4.0 && hit == null; s += 0.35) {
			double x = g.getX() + d.x * (half + s), z = g.getZ() + d.z * (half + s);
			for (int dy = 0; dy < tall && hit == null; dy++) {
				BlockPos q = BlockPos.containing(x, foot + dy, z);
				if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
					hit = q;
				}
			}
		}
		if (hit == null) {
			return List.of();
		}
		boolean alongX = Math.abs(d.x) >= Math.abs(d.z);
		int ax = alongX ? (d.x > 0 ? 1 : -1) : 0, az = alongX ? 0 : (d.z > 0 ? 1 : -1);
		List<BlockPos> out = new ArrayList<>();
		for (int deep = 0; deep <= 1; deep++) {
			for (int side = -1; side <= 1; side++) {
				for (int dy = 0; dy < tall; dy++) {
					BlockPos q = new BlockPos(hit.getX() + ax * deep + (alongX ? 0 : side), foot + dy, hit.getZ() + az * deep + (alongX ? side : 0));
					if (breakable(level, q)) {
						out.add(q);
					}
				}
			}
		}
		Vec3 at = g.position();
		out.sort(Comparator.comparingDouble(q -> q.distToCenterSqr(at)));
		int most = Math.max(1, Config.GATEBREAKER_SMASH_BLOCKS);
		return out.size() > most ? new ArrayList<>(out.subList(0, most)) : out;
	}

	private static void startSmash(ServerLevel level, Gatebreaker g, State st, List<BlockPos> wall) {
		st.mode = Mode.SMASH;
		st.ticks = 0;
		st.wall = wall;
		g.getNavigation().stop();
		g.setLurkerPose(Lurker.POSE_TAKE);
		WorldFx.soundIn(dimOf(g), "minecraft:entity.ravager.roar", "hostile", g.getX(), g.getY() + 3, g.getZ(), 2.0f, 0.6f);
	}

	/** It draws back (dust trickling from the wall) and brings its fists down on it. */
	private static void smash(ServerLevel level, Gatebreaker g, State st, ServerPlayer p) {
		TribeMoves.stand(g);
		if (st.wall.isEmpty()) {
			backToWalk(g, st, 10);
			return;
		}
		BlockPos mid = st.wall.get(0);
		g.getLookControl().setLookAt(mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5, 30, 30);
		if (st.ticks % 4 == 0) {
			for (int i = 0; i < Math.min(3, st.wall.size()); i++) {
				BlockPos q = st.wall.get(i);
				WorldFx.particleIn(level, "minecraft:falling_dust{block_state:{Name:\"minecraft:stone\"}}", q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5,
						0.4, 0.4, 0.4, 0.0, 2);
			}
		}
		if (st.ticks == 1 || st.ticks == 12) {
			WorldFx.soundIn(dimOf(g), "minecraft:block.deepslate.hit", "hostile", mid.getX(), mid.getY(), mid.getZ(), 1.5f, 0.5f);
		}
		if (st.ticks < SMASH_WINDUP) {
			return;
		}
		int broke = 0;
		if (canBreak(level)) {
			for (BlockPos q : st.wall) {
				if (breakable(level, q) && level.destroyBlock(q, true, g)) {
					broke++;
				}
			}
		}
		st.smashes++;
		WorldFx.punch(level, g, mid.getX() + 0.5, mid.getZ() + 0.5, 24, 0.8f, 14);
		WorldFx.soundIn(dimOf(g), "minecraft:entity.generic.explode", "hostile", mid.getX(), mid.getY(), mid.getZ(), 1.6f, 0.6f);
		WorldFx.particleIn(level, "minecraft:explosion", mid.getX() + 0.5, mid.getY() + 1, mid.getZ() + 0.5, 0.6, 0.6, 0.6, 0.0, 2);
		st.walled = broke > 0 ? 0 : st.walled + SMASH_WINDUP;
		st.stuck = 0;
		backToWalk(g, st, 10);
	}

	private static void startSlam(Gatebreaker g, State st) {
		st.mode = Mode.SLAM;
		st.ticks = 0;
		g.getNavigation().stop();
		g.setLurkerPose(Lurker.POSE_TAKE);
		WorldFx.soundIn(dimOf(g), "minecraft:entity.ravager.attack", "hostile", g.getX(), g.getY() + 3, g.getZ(), 2.0f, 0.5f);
	}

	/** Both fists raised, then the ground slammed: all near it hurt and thrown back; a shield turns it. */
	private static void slam(ServerLevel level, Gatebreaker g, State st, ServerPlayer p) {
		TribeMoves.stand(g);
		if (p != null) {
			g.getLookControl().setLookAt(p, 30, 30);
		}
		if (st.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:dust{color:[0.9,0.2,0.2],scale:1.2}", g.getX(), g.getY() + 3.5, g.getZ(), 0.6, 0.4, 0.6, 0.0, 3);
		}
		if (st.ticks < SLAM_WINDUP) {
			return;
		}
		for (ServerPlayer o : level.players()) {
			if (o.isSpectator() || o.isCreative() || o.distanceToSqr(g) > SLAM_REACH * SLAM_REACH || Math.abs(o.getY() - g.getY()) > 3) {
				continue;
			}
			double dx = o.getX() - g.getX(), dz = o.getZ() - g.getZ();
			double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
			if (o.isBlocking() && facing(o, g)) {
				WorldFx.soundIn(dimOf(g), "minecraft:item.shield.block", "players", o.getX(), o.getY() + 1, o.getZ(), 1.0f, 0.7f);
				o.setDeltaMovement(dx / d * 0.6, 0.2, dz / d * 0.6);
			} else {
				if (Config.GATEBREAKER_DAMAGE > 0) {
					o.hurtServer(level, g.damageSources().mobAttack(g), (float) Config.GATEBREAKER_DAMAGE);
				}
				o.setDeltaMovement(dx / d * 1.2, 0.6, dz / d * 1.2);
			}
			o.needsSync = true;
		}
		WorldFx.punch(level, g, g.getX(), g.getZ(), 24, 0.9f, 16);
		WorldFx.soundIn(dimOf(g), "minecraft:entity.iron_golem.attack", "hostile", g.getX(), g.getY(), g.getZ(), 2.0f, 0.5f);
		WorldFx.particleIn(level, "minecraft:explosion", g.getX(), g.getY() + 0.3, g.getZ(), 1.5, 0.2, 1.5, 0.0, 4);
		backToWalk(g, st, PAUSE);
	}

	/** Whether a player faces this creature (their shield is between them). */
	private static boolean facing(ServerPlayer p, Entity e) {
		Vec3 look = p.getViewVector(1.0f);
		double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		return d < 1e-3 || (look.x * dx + look.z * dz) / d > 0;
	}

	private static void backToWalk(Gatebreaker g, State st, int pause) {
		st.mode = Mode.WALK;
		st.ticks = 0;
		st.pause = pause;
		st.wall = List.of();
		st.checkedAt = g.position();
		g.setLurkerPose(Lurker.POSE_STALK);
	}

	private static void startLeave(Gatebreaker g, State st) {
		st.mode = Mode.LEAVE;
		st.ticks = 0;
		g.getNavigation().stop();
		g.setLurkerPose(Lurker.POSE_WATCH);
		g.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	/** It steps back into a tear in the storm. */
	private static void leave(Gatebreaker g, State st, List<Runnable> later) {
		TribeMoves.stand(g);
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(g.level(), "minecraft:reverse_portal", g.getX(), g.getY() + 2.5, g.getZ(), 0.8, 1.8, 0.8, 0.3, 12);
		}
		if (st.ticks >= 40) {
			later.add(() -> vanish(g));
		}
	}

	public static void vanish(Gatebreaker g) {
		STATES.remove(g.getUUID());
		LEVELS.remove(g.getUUID());
		if (g.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:reverse_portal", g.getX(), g.getY() + 2.5, g.getZ(), 1.0, 2.0, 1.0, 0.5, 60);
			WorldFx.soundIn(level.dimension().identifier().toString(), "extremedimensions:lurker_vanish", "hostile", g.getX(), g.getY() + 2, g.getZ(), 1.5f, 0.5f);
		}
		g.discard();
	}

	/** Slain: what it carried out of the storm falls where it dies. */
	public static void onDeath(Gatebreaker g, Entity killer) {
		STATES.remove(g.getUUID());
		LEVELS.remove(g.getUUID());
		if (!(g.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack s : Loot.roll(level, Loot.GATEBREAKER, g.position())) {
			g.spawnAtLocation(level, s);
		}
		if (killer instanceof ServerPlayer p) {
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.gatebreaker");
		}
	}

	private static String dimOf(Entity e) {
		return e.level().dimension().identifier().toString();
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives a Gatebreaker already in the world after this player (storm or no storm). */
	public static void adopt(ServerLevel level, Gatebreaker g, ServerPlayer p) {
		State st = drive(level, g, p, true);
		st.pause = 0;
	}

	public static String mode(Gatebreaker g) {
		State st = STATES.get(g.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many times it has brought its fists down on a wall. */
	public static int smashes(Gatebreaker g) {
		State st = STATES.get(g.getUUID());
		return st == null ? 0 : st.smashes;
	}
}
