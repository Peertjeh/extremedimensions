package com.extremedimensions.lurker;

import java.util.Map;
import java.util.WeakHashMap;

import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How the tribe walks, the Lurker, the Hollowling and the Keeper alike. A route is worked out again only when its goal
 * has really moved or the old one is spent, so a creature never keeps turning back to the start of a fresh route; close
 * to its quarry, with nothing in the way, it steps straight at it; it never sets foot in lava or fire and goes round
 * water where it can; and it stands, and is set down, on the ground, never in a tree, and only where its whole body fits.
 *
 * <p>The tribe drops from heights without harm and steps out of a wall it is pressed into, so neither a fall nor a low
 * roof hurts it; a ledge too high to step up, it leaps; the big ones push through a tree's leaves.
 */
public final class TribeMoves {
	private TribeMoves() {}

	/** How far a goal must have moved before a new route is worked out (blocks). */
	private static final double REPATH = 2.5;
	/** Within this distance of what it follows, in sight and on open ground, it steps straight at it. */
	private static final double STRAIGHT = 4.5;
	/** Ticks between two leaps of one creature. */
	private static final int LEAP_PAUSE = 40;
	/** When each creature last leapt (its age then). */
	private static final Map<Mob, Integer> LEAPT = new WeakHashMap<>();

	/** Keeps a creature of the tribe out of lava and fire, and out of water where it can go round. */
	public static void wary(Mob m) {
		m.setPathfindingMalus(PathType.WATER, 16.0F);
		m.setPathfindingMalus(PathType.WATER_BORDER, 6.0F);
		m.setPathfindingMalus(PathType.LAVA, -1.0F);
		m.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 16.0F);
		m.setPathfindingMalus(PathType.FIRE, -1.0F);
		m.setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 8.0F);
		m.setPathfindingMalus(PathType.DAMAGING, -1.0F);
		m.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
	}

	/** Harm the tribe shrugs off: a fall (it drops where it must), a wall it is pressed into (it steps out), a crowd. */
	public static boolean shrugs(DamageSource s) {
		return s.is(DamageTypeTags.IS_FALL) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.CRAMMING);
	}

	/** Whether a creature dealt this (a player, a beast, their arrow): only that makes one of the tribe flee or turn. */
	public static boolean struck(DamageSource s) {
		return s.getEntity() instanceof LivingEntity;
	}

	/** Walks to a spot. The route it has is kept while it still leads there; a new one only when the spot moved. */
	public static void go(Mob m, double x, double y, double z, double speed) {
		PathNavigation nav = m.getNavigation();
		BlockPos target = nav.getTargetPos();
		if (!nav.isDone() && target != null && target.distToCenterSqr(x, y, z) < REPATH * REPATH) {
			nav.setSpeedModifier(speed);
			return;
		}
		nav.moveTo(x, y, z, speed);
		Path path = nav.getPath();
		// No way round to a spot above it (a cliff, a ledge): it leaps up
		if ((path == null || !path.canReach()) && leap(m, x, y, z)) {
			nav.stop();
		}
	}

	/**
	 * Skips the first steps of its route that already lie behind the creature. A route is worked out from the block it
	 * stands in, and the middle of that block (and of the next, round a corner) may be a little behind it: walking to
	 * them, it would turn back for half a step and then round again, the turn on the spot that looks like a spin.
	 */
	public static void trimBehind(Mob m) {
		Path path = m.getNavigation().getPath();
		if (path == null || path.isDone()) {
			return;
		}
		for (int guard = 0; guard < 3 && path.getNextNodeIndex() + 1 < path.getNodeCount(); guard++) {
			int i = path.getNextNodeIndex();
			Vec3 a = path.getEntityPosAtNode(m, i), b = path.getEntityPosAtNode(m, i + 1);
			double ax = a.x - m.getX(), az = a.z - m.getZ();
			double wx = b.x - a.x, wz = b.z - a.z, w = Math.sqrt(wx * wx + wz * wz);
			// behind: on its level, near, and clearly passed (a quarter block back along the way on from it; less, and the
			// route's own step to the next point takes it, while a corner just ahead is never cut)
			if (w > 1e-3 && Math.abs(a.y - m.getY()) < 0.6 && ax * ax + az * az < 1.2 * 1.2 && (ax * wx + az * wz) / w < -0.25) {
				path.advance();
			} else {
				break;
			}
		}
	}

	/** It stops where it is and stands (facing whatever its eyes are set on). */
	public static void stand(Mob m) {
		m.getNavigation().stop();
		m.getMoveControl().setWait();
	}

	/** How near it comes to what it follows: body to body, with a little room between them. */
	static double keepOff(Mob m, Entity t) {
		return (m.getBbWidth() + t.getBbWidth()) / 2 + 0.5;
	}

	/**
	 * Goes after a creature: close by, in sight and over safe ground its whole body passes, straight at it; further off,
	 * by a kept route. It aims a step short of its prey and stands once it is there, facing it: aimed at the middle of
	 * something it already touches, it would turn on the spot round and round.
	 */
	public static void follow(Mob m, Entity t, double speed) {
		double dx = t.getX() - m.getX(), dz = t.getZ() - m.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		double off = keepOff(m, t);
		boolean level = Math.abs(t.getY() - m.getY()) < 1.2;
		if (level && flat < off + 0.25) {
			m.getNavigation().stop();
			m.getMoveControl().setWait();
			m.getLookControl().setLookAt(t, 20, 20);
			return;
		}
		if (level && flat < STRAIGHT && m.hasLineOfSight(t) && m.level() instanceof ServerLevel sl && clear(sl, m, t.position())) {
			m.getNavigation().stop();
			double k = (flat - off) / flat;
			m.getMoveControl().setWantedPosition(m.getX() + dx * k, t.getY(), m.getZ() + dz * k, speed);
			return;
		}
		go(m, t.getX(), t.getY(), t.getZ(), speed);
	}

	/** As {@link #clear(ServerLevel, Vec3, Vec3)}, and the creature's whole body passes all the way (no low lintel). */
	static boolean clear(ServerLevel level, Mob m, Vec3 to) {
		if (!clear(level, m.position(), to)) {
			return false;
		}
		Vec3 d = to.subtract(m.position());
		int steps = Math.max(1, (int) Math.ceil(d.horizontalDistance()));
		for (int i = 1; i <= steps; i++) {
			Vec3 q = d.scale(i / (double) steps);
			if (!level.noCollision(m, m.getBoundingBox().move(q.x, q.y + 0.05, q.z))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a creature cannot get to what it follows: not near on open ground it fits through, and no whole route there
	 * (a house too low for it, a pillar, a hole). Works out a route, so it is asked now and then, not every tick.
	 */
	public static boolean blocked(Mob m, Entity t) {
		if (!(m.level() instanceof ServerLevel level)) {
			return false;
		}
		if (m.distanceToSqr(t) < STRAIGHT * STRAIGHT && Math.abs(t.getY() - m.getY()) < 1.2 && m.hasLineOfSight(t) && clear(level, m, t.position())) {
			return false;
		}
		Path path = m.getNavigation().createPath(t, 1);
		return path == null || !path.canReach();
	}

	/**
	 * Whether a creature just set down on ground could walk from there to its target. One that has only now come into
	 * the world has not yet touched the ground (that comes with its first step), and no route is worked out for a
	 * creature off the ground: it is stood on the ground it was set down on first.
	 */
	public static boolean canWalkTo(Mob m, Entity t) {
		m.setOnGround(true);
		Path path = m.getNavigation().createPath(t, 1);
		return path != null && path.canReach();
	}

	/**
	 * A place near its prey from which this creature would see them (a door, a window, round the corner of a house): the
	 * nearest of a ring of spots round the prey on ground it fits, its eyes there with a clear line to theirs. Null when
	 * there is none within the radius. Looks at a few hundred spots, so it is asked now and then, not every tick.
	 */
	public static BlockPos sightFrom(ServerLevel level, Mob m, Entity t, int radius) {
		Vec3 eye = t.getEyePosition();
		BlockPos at = t.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int r = 2; r <= radius; r++) {
			int steps = Math.max(8, r * 6);
			for (int i = 0; i < steps; i++) {
				double a = i * Math.PI * 2 / steps;
				int x = at.getX() + (int) Math.round(Math.cos(a) * r);
				int z = at.getZ() + (int) Math.round(Math.sin(a) * r);
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos feet = new BlockPos(x, at.getY() + dy, z);
					double d = feet.distToCenterSqr(m.position());
					if (d >= bestD || !com.extremedimensions.server.Loaded.ready(level, feet) || !roomFor(level, feet, m)) {
						continue;
					}
					Vec3 from = new Vec3(x + 0.5, feet.getY() + m.getEyeHeight(), z + 0.5);
					if (level.clip(new ClipContext(from, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m)).getType() == HitResult.Type.MISS) {
						best = feet;
						bestD = d;
					}
				}
			}
		}
		return best;
	}

	/** Whether there is a roof over someone standing here (within three blocks over their head): a house, a tunnel. */
	public static boolean sheltered(net.minecraft.world.level.Level level, BlockPos feet) {
		for (int dy = 2; dy <= 4; dy++) {
			BlockPos q = feet.above(dy);
			if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Whether the straight way between two points runs over solid, safe ground with nothing to trip on. */
	static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(d.horizontalDistance()));
		for (int i = 1; i <= steps; i++) {
			Vec3 pt = from.add(d.scale(i / (double) steps));
			BlockPos feet = BlockPos.containing(pt.x, pt.y + 0.2, pt.z);
			BlockState at = level.getBlockState(feet);
			if (!at.getCollisionShape(level, feet).isEmpty() || hazard(at) || !ground(level.getBlockState(feet.below()))) {
				return false;
			}
		}
		return true;
	}

	/** How high a ledge this creature leaps: a Hollowling some blocks, a Lurker five, a Keeper six. */
	static double leapHeight(Mob m) {
		return Math.max(2.5, Math.min(6.0, m.getBbHeight() * 1.5));
	}

	/**
	 * A ledge between it and where it is going, too high to step or jump onto and with no way round: it leaps up onto it,
	 * if there is ground up there near the goal its whole body fits on and nothing over its head. Not again for two
	 * seconds. True when it leapt.
	 */
	static boolean leap(Mob m, double x, double y, double z) {
		if (!m.onGround() || !(m.level() instanceof ServerLevel level)) {
			return false;
		}
		double rise = y - m.getY();
		double dx = x - m.getX(), dz = z - m.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (rise < 1.6 || rise > leapHeight(m) || flat > 7 || flat < 0.5) {
			return false;
		}
		Integer last = LEAPT.get(m);
		if (last != null && m.tickCount - last < LEAP_PAUSE) {
			return false;
		}
		// Where to land: by the goal, on its level (a block either way), as near to the creature as can be
		BlockPos goal = BlockPos.containing(x, y, z);
		BlockPos land = null;
		double best = Double.MAX_VALUE;
		for (int ox = -2; ox <= 2; ox++) {
			for (int oz = -2; oz <= 2; oz++) {
				for (int oy = 1; oy >= -1; oy--) {
					BlockPos q = goal.offset(ox, oy, oz);
					double d = q.distToCenterSqr(m.getX(), q.getY() + 0.5, m.getZ());
					if (d < best && q.getY() - m.getY() >= 1.0 && roomFor(level, q, m)) {
						best = d;
						land = q;
					}
				}
			}
		}
		if (land == null) {
			return false;
		}
		double top = land.getY() - m.getY() + 1.2;
		// nothing over its head on the way up
		if (!level.noCollision(m, m.getBoundingBox().expandTowards(0, top, 0))) {
			return false;
		}
		// Up a little over the ledge, and across in the same time (a mob falls 0.08 a tick; air slows it 9% a tick across)
		double vy = Math.sqrt(2 * 0.08 * top) * 1.1;
		double ticks = vy / 0.08 + Math.sqrt(2 * 1.2 / 0.08);
		double lx = land.getX() + 0.5 - m.getX(), lz = land.getZ() + 0.5 - m.getZ();
		double across = Math.sqrt(lx * lx + lz * lz);
		double v = across * 0.09 / (1 - Math.pow(0.91, ticks));
		m.getNavigation().stop();
		m.getMoveControl().setWait();
		m.setDeltaMovement(across < 1e-3 ? 0 : lx / across * v, vy, across < 1e-3 ? 0 : lz / across * v);
		m.needsSync = true;
		LEAPT.put(m, m.tickCount);
		WorldFx.particleIn(level, "minecraft:poof", m.getX(), m.getY() + 0.2, m.getZ(), 0.4, 0.1, 0.4, 0.02, 8);
		WorldFx.soundIn(level.dimension().identifier().toString(), "extremedimensions:lurker_step", "hostile", m.getX(), m.getY(), m.getZ(), 1.0f, 0.7f);
		return true;
	}

	/** What burns or drowns: never walked into. */
	public static boolean hazard(BlockState s) {
		return s.liquid() || s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)
				|| s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.CACTUS) || s.is(Blocks.POWDER_SNOW);
	}

	/** Ground the tribe stands on and is set down on: solid, safe, and no part of a tree or a fungus. */
	public static boolean ground(BlockState s) {
		return !s.isAir() && !hazard(s) && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.WART_BLOCKS)
				&& !s.is(Blocks.SHROOMLIGHT) && !s.is(Blocks.BAMBOO) && !s.is(Blocks.MANGROVE_ROOTS);
	}

	/** Feet on safe ground, with room for a body this many blocks tall above it (one column). */
	public static boolean standable(ServerLevel level, BlockPos feet, int height) {
		if (!ground(level.getBlockState(feet.below()))) {
			return false;
		}
		for (int i = 0; i < height; i++) {
			BlockState s = level.getBlockState(feet.above(i));
			if (!s.getCollisionShape(level, feet.above(i)).isEmpty() || hazard(s)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a body this wide and tall fits standing at these feet (centred in the block): safe ground under it, nothing
	 * in the way of any part of it, and no water or fire where it would stand.
	 */
	public static boolean roomFor(ServerLevel level, BlockPos feet, double width, double height) {
		if (!ground(level.getBlockState(feet.below()))) {
			return false;
		}
		double h = width / 2.0;
		AABB box = new AABB(feet.getX() + 0.5 - h, feet.getY(), feet.getZ() + 0.5 - h, feet.getX() + 0.5 + h, feet.getY() + height,
				feet.getZ() + 0.5 + h);
		if (!level.noCollision(box)) {
			return false;
		}
		for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.minY + 1, box.maxZ))) {
			if (hazard(level.getBlockState(q))) {
				return false;
			}
		}
		return true;
	}

	/** Whether this creature's whole body fits standing at these feet. */
	public static boolean roomFor(ServerLevel level, BlockPos feet, Entity e) {
		return roomFor(level, feet, e.getBbWidth(), e.getBbHeight());
	}

	/**
	 * Where a column meets the ground (under the leaves, never on them), with room for a body this wide and tall; null
	 * when it is water, a tree or no room. In the Nether, looked for downwards from {@code top}.
	 */
	public static BlockPos surface(ServerLevel level, int x, int z, int top, double width, double height) {
		if (Dims.NETHER.equals(Dims.of(level))) {
			for (int y = top; y > top - 40; y--) {
				BlockPos feet = new BlockPos(x, y, z);
				if (roomFor(level, feet, width, height)) {
					return feet;
				}
			}
			return null;
		}
		BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return roomFor(level, feet, width, height) ? feet : null;
	}

	/**
	 * A safe spot to run to, away from {@code threat}, with room for this creature: tried straight away first, then
	 * turning a little further each time, so it runs off rather than circling. Null when nothing safe lies that way.
	 */
	public static Vec3 away(Mob m, Vec3 threat, double dist) {
		if (!(m.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 dir = new Vec3(m.getX() - threat.x, 0, m.getZ() - threat.z);
		if (dir.lengthSqr() < 0.01) {
			dir = new Vec3(1, 0, 0);
		}
		double base = Math.atan2(dir.z, dir.x);
		// (each creature turns to its own side, always the same, so a run that is cut off never swings back and forth)
		double side = (m.getId() & 1) == 0 ? 1 : -1;
		for (int i = 0; i < 9; i++) {
			double ang = base + side * (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.35;
			int x = (int) Math.floor(m.getX() + Math.cos(ang) * dist);
			int z = (int) Math.floor(m.getZ() + Math.sin(ang) * dist);
			BlockPos spot = surface(level, x, z, (int) m.getY() + 8, m.getBbWidth(), m.getBbHeight());
			if (spot != null) {
				return Vec3.atBottomCenterOf(spot);
			}
		}
		return null;
	}

	/**
	 * Pressed into blocks (knocked into a wall, caught under a low roof): it steps out to the nearest spot its whole body
	 * fits, a few blocks off at most, in a puff of ink. True when it moved.
	 */
	public static boolean unstick(Mob m) {
		if (!(m.level() instanceof ServerLevel level) || !m.isInWall()) {
			return false;
		}
		BlockPos at = m.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dy = -2; dy <= 3; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					BlockPos q = at.offset(dx, dy, dz);
					double d = dx * dx + dz * dz + dy * dy * 2;
					if (d < bestD && roomFor(level, q, m)) {
						bestD = d;
						best = q;
					}
				}
			}
		}
		if (best == null) {
			return false;
		}
		WorldFx.particleIn(level, "extremedimensions:lurker_wisp", m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 0.4, 0.6, 0.4, 0.0, 12);
		m.getNavigation().stop();
		m.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
		return true;
	}

	/** The nearest each creature has come to its prey while it could not reach them. */
	private static final Map<Mob, double[]> CLOSEST = new WeakHashMap<>();

	/**
	 * Ticks its prey has stayed out of its reach yet in sight within {@code range}, without the creature getting any
	 * nearer (a hole too low for it, a pillar, a run kept at a distance): one more each call; back to nothing once it
	 * comes a block nearer, loses sight of them, or can reach them ({@code near}).
	 */
	public static int outOfReach(Mob m, Entity t, boolean near, double range, int count) {
		double d = m.distanceTo(t);
		if (near || d > range || !m.hasLineOfSight(t)) {
			CLOSEST.remove(m);
			return 0;
		}
		double[] best = CLOSEST.computeIfAbsent(m, k -> new double[] {d});
		if (d < best[0] - 1.0) {
			best[0] = d;
			return 0;
		}
		return count + 1;
	}

	/** The tell of a reach: a line of motes from its arms to its prey, the moment it winds up. */
	public static void reachLine(Mob m, Entity t, String particle) {
		Vec3 from = m.position().add(0, m.getBbHeight() * 0.7, 0);
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0);
		Vec3 d = to.subtract(from);
		int steps = Math.max(2, (int) (d.length() * 2));
		for (int i = 0; i <= steps; i++) {
			Vec3 q = from.add(d.scale(i / (double) steps));
			WorldFx.particleIn(m.level(), particle, q.x, q.y, q.z, 0.05, 0.05, 0.05, 0.0, 1);
		}
	}

	/** Drags a player toward the creature that reached them: out of a hole, or down from a perch above it. */
	public static void yank(Entity from, ServerPlayer p) {
		double dx = from.getX() - p.getX(), dz = from.getZ() - p.getZ();
		double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		double pull = Math.min(1.6, 0.3 + d * 0.12);
		double up = p.getY() - from.getY() > 2 ? -0.8 : 0.35;
		p.setDeltaMovement(dx / d * pull, up, dz / d * pull);
		p.needsSync = true;
	}

	/** Whether the world lets creatures break blocks (the mobGriefing game rule). */
	public static boolean griefing(ServerLevel level) {
		return Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_GRIEFING));
	}

	/**
	 * The big ones push through a tree's leaves, as a ravager does: where the world lets creatures break blocks, its route
	 * may run through leaves, and natural leaves it walks into give way (never leaves someone placed).
	 */
	public static void brush(Mob m) {
		if (!(m.level() instanceof ServerLevel level)) {
			return;
		}
		boolean may = griefing(level);
		if (m.tickCount % 20 == 0) {
			m.setPathfindingMalus(PathType.LEAVES, may ? 2.0F : -1.0F);
		}
		if (!may || !m.horizontalCollision) {
			return;
		}
		AABB box = m.getBoundingBox().inflate(0.25);
		for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState s = level.getBlockState(q);
			if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
				level.destroyBlock(q, true, m);
			}
		}
	}
}
