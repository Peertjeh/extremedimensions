package com.extremedimensions.lurker;

import com.extremedimensions.server.Dims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * How the tribe walks, the Lurker, the Hollowling and the Keeper alike. A route is worked out again only when its goal
 * has really moved or the old one is spent, so a creature never keeps turning back to the start of a fresh route; close
 * to its quarry, with nothing in the way, it steps straight at it; it never sets foot in lava or fire and goes round
 * water where it can; and it stands, and is set down, on the ground, never in a tree.
 */
public final class TribeMoves {
	private TribeMoves() {}

	/** How far a goal must have moved before a new route is worked out (blocks). */
	private static final double REPATH = 2.5;
	/** Within this distance of what it follows, in sight and on open ground, it steps straight at it. */
	private static final double STRAIGHT = 4.5;

	/** Keeps a creature of the tribe out of lava and fire, and out of water where it can go round. */
	public static void wary(Mob m) {
		m.setPathfindingMalus(PathType.WATER, 16.0F);
		m.setPathfindingMalus(PathType.WATER_BORDER, 6.0F);
		m.setPathfindingMalus(PathType.LAVA, -1.0F);
		m.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
		m.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
		m.setPathfindingMalus(PathType.DANGER_OTHER, 8.0F);
		m.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
		m.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
		m.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, 8.0F);
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
	}

	/** Goes after a creature: close by, in sight and over safe ground, straight at it; further off, by a kept route. */
	public static void follow(Mob m, Entity t, double speed) {
		if (m.distanceToSqr(t) < STRAIGHT * STRAIGHT && Math.abs(t.getY() - m.getY()) < 1.2 && m.hasLineOfSight(t)
				&& m.level() instanceof ServerLevel level && clear(level, m.position(), t.position())) {
			m.getNavigation().stop();
			m.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), speed);
			return;
		}
		go(m, t.getX(), t.getY(), t.getZ(), speed);
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

	/** Feet on safe ground, with room for a body this many blocks tall above it. */
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
	 * Where a column meets the ground (under the leaves, never on them), with room for a body this tall; null when it is
	 * water, a tree or no room. In the Nether, looked for downwards from {@code top}.
	 */
	public static BlockPos surface(ServerLevel level, int x, int z, int top, int height) {
		if (Dims.NETHER.equals(Dims.of(level))) {
			for (int y = top; y > top - 40; y--) {
				BlockPos feet = new BlockPos(x, y, z);
				if (standable(level, feet, height)) {
					return feet;
				}
			}
			return null;
		}
		BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return standable(level, feet, height) ? feet : null;
	}

	/**
	 * A safe spot to run to, away from {@code threat}: tried straight away first, then turning a little further each
	 * time, so it runs off rather than circling. Null when nothing safe lies that way.
	 */
	public static Vec3 away(Mob m, Vec3 threat, double dist, int height) {
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
			BlockPos spot = surface(level, x, z, (int) m.getY() + 8, height);
			if (spot != null) {
				return Vec3.atBottomCenterOf(spot);
			}
		}
		return null;
	}
}
