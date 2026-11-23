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
import com.extremedimensions.data.PlayerData;
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
import net.minecraft.world.phys.Vec3;

/**
 * The Edgecrawler's mind. In the Seam, when a visitor stands near an island's edge (a drop of open air under the rim),
 * an Edgecrawler may climb up over the rim behind them:
 *
 * <ul>
 *   <li><b>CLIMB</b>: it hauls itself up over the rim, dust and the rasp of claws on stone.</li>
 *   <li><b>CREEP</b>: it comes on only while it is not looked at, as the Thinned do.</li>
 *   <li><b>GRAB</b>: close, it rears up (glowing red, a hiss) and reaches. A raised shield turns the reach.</li>
 *   <li><b>DRAG</b>: it has you, and drags you to the nearest edge; then it throws you over
 *       ({@code edgecrawler_throw}, {@code edgecrawler_damage}). Crouch and you plant your feet: it cannot throw you.
 *       Strike it and it lets go. With no edge near, it claws instead.</li>
 *   <li><b>RETREAT</b>: its throw landed (or it is beaten off, or it has stayed long enough), it climbs back down
 *       under the rim.</li>
 * </ul>
 *
 * Edgecrawlers are never saved.
 */
public final class EdgecrawlerService {
	private EdgecrawlerService() {}

	enum Mode { CLIMB, CREEP, GRAB, DRAG, RETREAT }

	/** Ticks of the climb, the grab's wind-up and the drag at most. */
	private static final int CLIMB_TICKS = 30, GRAB_WINDUP = 14, DRAG_TICKS = 50;
	/** How near it must come to reach, and how far its reach goes (centre to centre, across). */
	private static final double GRAB_FROM = 2.4, GRAB_REACH = 3.2;
	/** How hard it drags (blocks a tick). */
	private static final double PULL = 0.26;
	/** How far round a spot it looks for an edge to drag its prey to. */
	private static final int DRAG_RANGE = 9;
	/** Claws before it gives up on prey with no edge near. */
	private static final int CLAWS = 3;

	static final class State {
		Mode mode = Mode.CLIMB;
		UUID target;
		int ticks;
		int age;
		int pause;
		int claws;
		int hurls;
		boolean anywhere;
		Vec3 edge;
		Vec3 rim;
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

	// ------------------------------------------------------------------ edges

	/** Whether the column from {@code top} down is open air (no ground, no water) for this many blocks. */
	static boolean open(ServerLevel level, BlockPos top, int drop) {
		for (int i = 0; i < drop; i++) {
			BlockPos q = top.below(i);
			if (q.getY() < level.getMinY()) {
				return true;
			}
			if (!Loaded.ready(level, q) || !level.getBlockState(q).getCollisionShape(level, q).isEmpty() || !level.getFluidState(q).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The nearest edge within {@code range} of a spot: along sixteen ways out, the first column with open air under the
	 * rim ({@code edgecrawler_drop} deep), with ground and no wall all the way to it. The centre of that column at foot
	 * height, or null when there is none.
	 */
	public static Vec3 edge(ServerLevel level, Vec3 at, int range) {
		int foot = (int) Math.floor(at.y + 0.01);
		int drop = Math.max(3, Config.EDGECRAWLER_DROP);
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8, cx = Math.cos(a), cz = Math.sin(a);
			for (double r = 1.0; r <= range; r += 0.7) {
				BlockPos col = BlockPos.containing(at.x + cx * r, foot, at.z + cz * r);
				if (!Loaded.ready(level, col) || !level.getBlockState(col).getCollisionShape(level, col).isEmpty()
						|| !level.getBlockState(col.above()).getCollisionShape(level, col.above()).isEmpty()) {
					break;
				}
				if (open(level, col.below(), drop)) {
					if (r < bestD) {
						bestD = r;
						best = new Vec3(col.getX() + 0.5, foot, col.getZ() + 0.5);
					}
					break;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ coming

	public static boolean fits(ServerPlayer p) {
		if (Config.EDGECRAWLER_ENABLED <= 0 || !Dims.SEAM.equals(Dims.of(p)) || p.isSpectator() || p.isCreative() || !p.isAlive()
				|| Rules.peaceful(p.level()) || com.extremedimensions.tribe.Standing.of(p) == com.extremedimensions.tribe.Standing.Level.KIN) {
			return false;
		}
		PlayerData d = DataStore.get(p.getUUID());
		return d.inSeam && d.seamPull <= 0 && p.onGround()
				&& !(com.extremedimensions.mending.MendingService.active() && com.extremedimensions.mending.MendingService.inArena(p));
	}

	public static void tick(MinecraftServer s) {
		drive();
		if (Config.EDGECRAWLER_ENABLED <= 0 || (s.getTickCount() + 350) % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!fits(p) || after(p)) {
				continue;
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.EDGECRAWLER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			if (rnd.nextDouble() >= Config.EDGECRAWLER_SPAWN_CHANCE) {
				continue;
			}
			// Only for those who stand near an edge
			if (edge((ServerLevel) p.level(), p.position(), Math.max(2, Config.EDGECRAWLER_EDGE_RANGE)) == null) {
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

	/** Feet near this column for a body this size, from a little above {@code y} to a little below; null if none. */
	private static BlockPos feetNear(ServerLevel level, int x, int y, int z, double width, double height) {
		for (int dy = 4; dy >= -6; dy--) {
			BlockPos q = new BlockPos(x, y + dy, z);
			if (Loaded.ready(level, q) && TribeMoves.roomFor(level, q, width, height) && level.getFluidState(q).isEmpty()) {
				return q;
			}
		}
		return null;
	}

	/**
	 * An Edgecrawler climbs up over a rim 6-12 blocks from this visitor (behind them where it can), on a spot beside a
	 * drop it could have come up out of. {@code anywhere} (an operator's summons) lets it come up nearer, at any ground.
	 */
	public static Edgecrawler come(ServerPlayer p, boolean anywhere) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int drop = Math.max(3, Config.EDGECRAWLER_DROP);
		Vec3 look = p.getViewVector(1.0f);
		for (int i = 0; i < 40; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = (anywhere ? 4 : 6) + rnd.nextDouble() * 6;
			double ox = Math.cos(a), oz = Math.sin(a);
			// (behind them, the first tries)
			if (!anywhere && i < 24 && ox * look.x + oz * look.z > 0.1) {
				continue;
			}
			int x = (int) Math.floor(p.getX() + ox * d), z = (int) Math.floor(p.getZ() + oz * d);
			BlockPos feet = feetNear(level, x, (int) Math.floor(p.getY()), z, ModEntities.EDGECRAWLER.getWidth(), ModEntities.EDGECRAWLER.getHeight());
			if (feet == null) {
				continue;
			}
			BlockPos rim = null;
			for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				BlockPos side = feet.offset(o[0], 0, o[1]);
				if (Loaded.ready(level, side) && level.getBlockState(side).getCollisionShape(level, side).isEmpty() && open(level, side.below(), drop)) {
					rim = side;
					break;
				}
			}
			if (rim == null && !anywhere) {
				continue;
			}
			Edgecrawler c = ModEntities.EDGECRAWLER.spawn(level, feet, EntitySpawnReason.EVENT);
			if (c == null) {
				continue;
			}
			State st = drive(level, c, p, anywhere);
			st.rim = rim == null ? null : Vec3.atBottomCenterOf(rim);
			// Up out of the drop: dust off the rim and the rasp of claws on stone
			String dim = level.dimension().identifier().toString();
			WorldFx.particleIn(dim, "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", c.getX(), c.getY() + 0.2, c.getZ(),
					0.6, 0.3, 0.6, 0.0, 16);
			WorldFx.particleIn(dim, "extremedimensions:void_mote", c.getX(), c.getY() - 0.5, c.getZ(), 0.6, 0.6, 0.6, 0.0, 10);
			WorldFx.soundIn(dim, "minecraft:entity.spider.ambient", "hostile", c.getX(), c.getY() + 1, c.getZ(), 1.2f, 0.5f);
			WorldFx.soundIn(dim, "minecraft:block.stone.step", "hostile", c.getX(), c.getY(), c.getZ(), 1.0f, 0.6f);
			return c;
		}
		return null;
	}

	private static State drive(ServerLevel level, Edgecrawler c, ServerPlayer p, boolean anywhere) {
		c.setPersistenceRequired();
		State st = new State();
		st.target = p.getUUID();
		st.anywhere = anywhere;
		STATES.put(c.getUUID(), st);
		LEVELS.put(c.getUUID(), level);
		c.setLurkerPose(Lurker.POSE_RUN);
		c.setLurkerMood(LurkerBody.MOOD_CALM);
		return st;
	}

	/** Operator command: an Edgecrawler climbs up near the player. */
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
			if (!(ent instanceof Edgecrawler c) || !c.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			st.age++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			boolean gone = p == null || p.level() != c.level() || p.distanceToSqr(c) > 48 * 48 || p.isSpectator() || p.isDeadOrDying()
					|| (!st.anywhere && DataStore.get(p.getUUID()).seamPull > 0);
			if ((gone || st.age > Math.max(20, Config.EDGECRAWLER_SECONDS) * 20) && st.mode != Mode.RETREAT && st.mode != Mode.DRAG) {
				startRetreat(level, c, st);
			}
			switch (st.mode) {
				case CLIMB -> climb(c, st, p);
				case CREEP -> creep(level, c, st, p);
				case GRAB -> grab(level, c, st, p);
				case DRAG -> drag(level, c, st, p);
				case RETREAT -> retreat(c, st, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** It hauls itself up over the rim. */
	private static void climb(Edgecrawler c, State st, ServerPlayer p) {
		TribeMoves.stand(c);
		if (p != null) {
			c.getLookControl().setLookAt(p, 10, 10);
		}
		if (st.ticks % 5 == 0) {
			WorldFx.particleIn(c.level(), "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", c.getX(), c.getY() + 0.3, c.getZ(),
					0.5, 0.2, 0.5, 0.0, 3);
		}
		if (st.ticks >= CLIMB_TICKS) {
			st.mode = Mode.CREEP;
			st.ticks = 0;
			c.setLurkerPose(Lurker.POSE_STALK);
		}
	}

	private static boolean seen(ServerPlayer p, Edgecrawler c) {
		Vec3 to = c.getEyePosition().subtract(p.getEyePosition()).normalize();
		return p.getViewVector(1.0f).dot(to) > 0.85 && p.hasLineOfSight(c);
	}

	/** It comes on only while it is not looked at; close, it reaches. */
	private static void creep(ServerLevel level, Edgecrawler c, State st, ServerPlayer p) {
		if (p == null) {
			return;
		}
		if (st.pause > 0) {
			st.pause--;
		}
		double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
		if (st.pause <= 0 && dx * dx + dz * dz <= GRAB_FROM * GRAB_FROM && Math.abs(p.getY() - c.getY()) < 2.0) {
			st.mode = Mode.GRAB;
			st.ticks = 0;
			c.getNavigation().stop();
			c.setLurkerPose(Lurker.POSE_TAKE);
			c.setLurkerMood(LurkerBody.MOOD_HUNTER);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.spider.hurt", "hostile", c.getX(), c.getY() + 1.5, c.getZ(), 1.2f, 0.5f);
			return;
		}
		if (seen(p, c)) {
			TribeMoves.stand(c);
			c.getLookControl().setLookAt(p, 30, 30);
			return;
		}
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(c, p, Config.EDGECRAWLER_SPEED);
			TribeMoves.trimBehind(c);
		}
	}

	/** It rears up and reaches; a shield turns the reach; with no edge near, it claws instead of taking hold. */
	private static void grab(ServerLevel level, Edgecrawler c, State st, ServerPlayer p) {
		TribeMoves.stand(c);
		if (p == null) {
			return;
		}
		c.getLookControl().setLookAt(p, 40, 40);
		if (st.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:dust{color:[0.95,0.2,0.18],scale:0.9}", c.getX(), c.getY() + 1.6, c.getZ(), 0.4, 0.3, 0.4, 0.0, 2);
		}
		if (st.ticks < GRAB_WINDUP) {
			return;
		}
		String dim = level.dimension().identifier().toString();
		double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
		if (dx * dx + dz * dz > GRAB_REACH * GRAB_REACH || Math.abs(p.getY() - c.getY()) > 2.5 || !c.hasLineOfSight(p)) {
			backToCreep(c, st, 10);
			return;
		}
		if (p.isBlocking() && facing(p, c)) {
			WorldFx.soundIn(dim, "minecraft:item.shield.block", "players", p.getX(), p.getY() + 1, p.getZ(), 1.0f, 0.8f);
			double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
			c.setDeltaMovement(-dx / d * 0.5, 0.2, -dz / d * 0.5);
			backToCreep(c, st, 40);
			return;
		}
		Vec3 edge = Config.EDGECRAWLER_HURLS > 0 ? edge(level, p.position(), DRAG_RANGE) : null;
		if (edge == null) {
			// Nowhere to throw them: it rakes them with its claws
			if (Config.EDGECRAWLER_DAMAGE > 0) {
				p.hurtServer(level, c.damageSources().mobAttack(c), (float) Config.EDGECRAWLER_DAMAGE);
			}
			WorldFx.soundIn(dim, "minecraft:entity.player.attack.sweep", "hostile", p.getX(), p.getY() + 1, p.getZ(), 1.0f, 0.6f);
			if (++st.claws >= CLAWS) {
				startRetreat(level, c, st);
			} else {
				backToCreep(c, st, 30);
			}
			return;
		}
		st.mode = Mode.DRAG;
		st.ticks = 0;
		st.edge = edge;
		WorldFx.soundIn(dim, "minecraft:entity.spider.ambient", "hostile", c.getX(), c.getY() + 1.5, c.getZ(), 1.4f, 0.4f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.edgecrawler.grab").withStyle(net.minecraft.ChatFormatting.RED), true);
		com.extremedimensions.journal.Journal.unlock(p, DataStore.get(p.getUUID()), com.extremedimensions.journal.JournalEntry.TRIBE);
	}

	/** It has its prey and drags them to the edge; there (or when the drag runs out) it throws them over, unless they crouch. */
	private static void drag(ServerLevel level, Edgecrawler c, State st, ServerPlayer p) {
		if (p == null || st.edge == null) {
			startRetreat(level, c, st);
			return;
		}
		Vec3 dir = new Vec3(st.edge.x - p.getX(), 0, st.edge.z - p.getZ());
		double far = dir.length();
		dir = far < 1e-3 ? new Vec3(1, 0, 0) : dir.scale(1 / far);
		TribeMoves.reachLine(c, p, "minecraft:dust{color:[0.95,0.2,0.18],scale:0.7}");
		c.getLookControl().setLookAt(p, 40, 40);
		// It keeps at their back, pushing them on
		if (st.ticks % 3 == 0) {
			TribeMoves.go(c, p.getX() - dir.x * 1.2, p.getY(), p.getZ() - dir.z * 1.2, Config.EDGECRAWLER_SPEED * 1.3);
		}
		boolean braced = p.isShiftKeyDown();
		if (!braced) {
			p.setDeltaMovement(dir.x * PULL, p.getDeltaMovement().y, dir.z * PULL);
			com.extremedimensions.server.WorldFx.moved(p);
		}
		if (st.ticks % 10 == 0) {
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.gravel.step", "hostile", p.getX(), p.getY(), p.getZ(), 0.8f, 0.7f);
		}
		if (far > 1.3 && st.ticks < DRAG_TICKS) {
			return;
		}
		if (braced) {
			// Feet planted: it cannot throw them, and it slinks off
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.spider.hurt", "hostile", c.getX(), c.getY() + 1.5, c.getZ(), 1.0f, 0.7f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.edgecrawler.braced").withStyle(net.minecraft.ChatFormatting.GRAY), true);
			startRetreat(level, c, st);
			return;
		}
		hurl(level, c, st, p, dir);
	}

	/** Over the edge with them. */
	private static void hurl(ServerLevel level, Edgecrawler c, State st, ServerPlayer p, Vec3 dir) {
		double k = Math.max(0, Config.EDGECRAWLER_THROW);
		if (Config.EDGECRAWLER_DAMAGE > 0) {
			p.hurtServer(level, c.damageSources().mobAttack(c), (float) Config.EDGECRAWLER_DAMAGE);
		}
		p.setDeltaMovement(dir.x * 1.1 * k, 0.4 * Math.min(1, k) + 0.05, dir.z * 1.1 * k);
		com.extremedimensions.server.WorldFx.moved(p);
		st.hurls++;
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "minecraft:entity.ravager.attack", "hostile", c.getX(), c.getY() + 1.5, c.getZ(), 1.2f, 1.2f);
		WorldFx.particleIn(dim, "minecraft:sweep_attack", p.getX(), p.getY() + 1, p.getZ(), 0.2, 0.2, 0.2, 0.0, 1);
		startRetreat(level, c, st);
	}

	/** Whether a player faces this creature (their shield is between them). */
	private static boolean facing(ServerPlayer p, Entity e) {
		Vec3 look = p.getViewVector(1.0f);
		double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		return d < 1e-3 || (look.x * dx + look.z * dz) / d > 0;
	}

	private static void backToCreep(Edgecrawler c, State st, int pause) {
		st.mode = Mode.CREEP;
		st.ticks = 0;
		st.pause = pause;
		st.edge = null;
		c.setLurkerPose(Lurker.POSE_STALK);
		c.setLurkerMood(LurkerBody.MOOD_CALM);
	}

	private static void startRetreat(ServerLevel level, Edgecrawler c, State st) {
		st.mode = Mode.RETREAT;
		st.ticks = 0;
		st.edge = null;
		c.setLurkerPose(Lurker.POSE_RUN);
		c.setLurkerMood(LurkerBody.MOOD_CALM);
		// Back to the nearest drop, to climb down under the rim
		Vec3 down = edge(level, c.position(), 10);
		st.rim = down != null ? down : st.rim;
	}

	/** It scuttles back to a drop and climbs down out of sight. */
	private static void retreat(Edgecrawler c, State st, List<Runnable> later) {
		if (st.rim != null && st.ticks % 5 == 1) {
			TribeMoves.go(c, st.rim.x, st.rim.y, st.rim.z, Config.EDGECRAWLER_SPEED * 1.3);
		}
		boolean there = st.rim == null || c.position().distanceToSqr(st.rim.x, c.getY(), st.rim.z) < 1.6 * 1.6;
		if ((there && st.ticks > 10) || st.ticks > 120) {
			later.add(() -> vanish(c));
		}
	}

	/** Down under the rim, out of sight. */
	public static void vanish(Edgecrawler c) {
		STATES.remove(c.getUUID());
		LEVELS.remove(c.getUUID());
		if (c.level() instanceof ServerLevel level) {
			WorldFx.particleIn(level, "minecraft:falling_dust{block_state:{Name:\"extremedimensions:hush_stone\"}}", c.getX(), c.getY() + 0.3, c.getZ(),
					0.5, 0.3, 0.5, 0.0, 10);
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:entity.spider.step", "hostile", c.getX(), c.getY(), c.getZ(), 1.0f, 0.5f);
		}
		c.discard();
	}

	/** Struck: it lets go of whoever it holds and backs off. */
	public static void onHurt(Edgecrawler c, DamageSource source) {
		State st = STATES.get(c.getUUID());
		if (st == null || !TribeMoves.struck(source)) {
			return;
		}
		if (st.mode == Mode.DRAG || st.mode == Mode.GRAB) {
			WorldFx.soundIn(c.level().dimension().identifier().toString(), "minecraft:entity.spider.hurt", "hostile", c.getX(), c.getY() + 1.5, c.getZ(),
					1.2f, 0.8f);
			backToCreep(c, st, 50);
		}
	}

	/** Slain: the bit of island grown on it, and now and then a scrap of thread. */
	public static void onDeath(Edgecrawler c, Entity killer) {
		STATES.remove(c.getUUID());
		LEVELS.remove(c.getUUID());
		if (!(c.level() instanceof ServerLevel level)) {
			return;
		}
		for (net.minecraft.world.item.ItemStack s : com.extremedimensions.lore.Loot.roll(level, com.extremedimensions.lore.Loot.EDGECRAWLER, c.position())) {
			c.spawnAtLocation(level, s);
		}
		if (killer instanceof ServerPlayer p) {
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.edgecrawler");
		}
	}

	// ------------------------------------------------------------------ for the tests

	/** Drives an Edgecrawler already in the world after this player, creeping already (it may drag anywhere). */
	public static void adopt(ServerLevel level, Edgecrawler c, ServerPlayer p) {
		State st = drive(level, c, p, true);
		st.mode = Mode.CREEP;
		c.setLurkerPose(Lurker.POSE_STALK);
	}

	public static String mode(Edgecrawler c) {
		State st = STATES.get(c.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	/** How many times it has thrown its prey toward an edge. */
	public static int hurls(Edgecrawler c) {
		State st = STATES.get(c.getUUID());
		return st == null ? 0 : st.hurls;
	}
}
