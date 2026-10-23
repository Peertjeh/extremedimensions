package com.extremedimensions.lore;

import java.util.Random;

import com.extremedimensions.server.Loaded;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Held's chamber, at the other end of a warm chain: a sealed room of blackstone in the Nether, carved where the
 * player came through, with a short way in from the side of their portal. At the back the Held still sits, holding the
 * chain up; a lodestone they never managed to bind, a barrel with what they kept, soul lanterns that never went out.
 *
 * <p>Built straight into the world (the chunks next to an arriving player are loaded), once per warm gate.
 */
public final class HeldChamber {
	private HeldChamber() {}

	/** Half the room's width: the walls stand at plus and minus this. */
	public static final int HALF = 4;
	/** Height of the room inside (floor to ceiling, exclusive). */
	public static final int TALL = 4;
	/** How far from the arrival the chamber's middle may lie. */
	public static final int NEAR = 13, FAR = 21;

	/** Where a chamber stands: its middle at floor height and the way its door faces (towards the arrival). */
	public record Site(BlockPos centre, Direction front) {}

	/**
	 * The best place for a chamber near an arrival: straight along one of the four ways from it, inside rock rather than
	 * over a lava sea, in loaded chunks, clear of bedrock. Null when there is none (the next crossing tries again).
	 */
	public static Site find(ServerLevel level, BlockPos arrival) {
		int y0 = Math.max(level.getMinY() + 6, Math.min(arrival.getY(), level.getMinY() + level.getHeight() - TALL - 8));
		Site best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Direction front : Direction.Plane.HORIZONTAL) {
			for (int dist = NEAR; dist <= FAR; dist += 4) {
				BlockPos c = new BlockPos(arrival.getX(), y0, arrival.getZ()).relative(front.getOpposite(), dist);
				int score = score(level, c);
				if (score > bestScore) {
					bestScore = score;
					best = new Site(c, front);
				}
			}
		}
		return bestScore == Integer.MIN_VALUE ? null : best;
	}

	/**
	 * How well a room fits here: rock is good, lava and open air less so; unloaded chunks, bedrock, or anything not of
	 * the Nether's own making (someone's walls and chests, a fortress) rule it out.
	 */
	private static int score(ServerLevel level, BlockPos c) {
		int score = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int u = -HALF; u <= HALF; u++) {
			for (int v = -HALF; v <= HALF; v++) {
				for (int y = -1; y <= TALL; y++) {
					m.set(c.getX() + u, c.getY() + y, c.getZ() + v);
					if (!Loaded.ready(level, m)) {
						return Integer.MIN_VALUE;
					}
					BlockState s = level.getBlockState(m);
					if (s.is(Blocks.BEDROCK) || !natural(s) || level.getBlockEntity(m) != null) {
						return Integer.MIN_VALUE;
					}
					if (!s.getFluidState().isEmpty()) {
						score -= 6;
					} else if (!s.isAir()) {
						score += 1;
					}
				}
			}
		}
		return score;
	}

	/** Of the Nether's own making: its rock, sand, ores, fungus and growth, lava, air, and the portal the player came by. */
	public static boolean natural(BlockState s) {
		return s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced() || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.NYLIUM)
				|| s.is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || s.is(Blocks.CRIMSON_STEM) || s.is(Blocks.WARPED_STEM) || s.is(Blocks.CRIMSON_HYPHAE)
				|| s.is(Blocks.WARPED_HYPHAE)
				|| s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.GRAVEL) || s.is(Blocks.GLOWSTONE) || s.is(Blocks.NETHER_GOLD_ORE)
				|| s.is(Blocks.NETHER_QUARTZ_ORE) || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.BONE_BLOCK) || s.is(Blocks.NETHER_WART_BLOCK)
				|| s.is(Blocks.WARPED_WART_BLOCK) || s.is(Blocks.SHROOMLIGHT) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)
				|| s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.WEEPING_VINES) || s.is(Blocks.WEEPING_VINES_PLANT) || s.is(Blocks.TWISTING_VINES)
				|| s.is(Blocks.TWISTING_VINES_PLANT) || s.is(Blocks.CRIMSON_FUNGUS) || s.is(Blocks.WARPED_FUNGUS) || s.is(Blocks.CRIMSON_ROOTS)
				|| s.is(Blocks.WARPED_ROOTS) || s.is(Blocks.NETHER_SPROUTS) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM);
	}

	/** Raises the chamber at a site, the barrel filled from a loot table, and lays the chain from its door towards the arrival. */
	public static void build(ServerLevel level, Site site, String table, long seed) {
		Random rnd = new Random(seed);
		BlockPos c = site.centre();
		Direction f = site.front(), side = f.getClockWise();
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState column = axis(Blocks.POLISHED_BASALT.defaultBlockState(), Direction.Axis.Y);
		// The shell and the hollow inside it
		for (int u = -HALF; u <= HALF; u++) {
			for (int v = -HALF; v <= HALF; v++) {
				for (int y = -1; y <= TALL; y++) {
					boolean wall = Math.abs(u) == HALF || Math.abs(v) == HALF || y == -1 || y == TALL;
					BlockState s;
					if (!wall) {
						s = Blocks.AIR.defaultBlockState();
					} else if (Math.abs(u) == HALF && Math.abs(v) == HALF) {
						s = column;
					} else if (y == -1) {
						s = rnd.nextInt(5) == 0 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
					} else {
						s = rnd.nextInt(10) < 3 ? cracked : bricks;
					}
					set(level, at(c, f, side, u, v, y), s);
				}
			}
		}
		// The door, on the side the arrival lies
		set(level, at(c, f, side, HALF, 0, 0), Blocks.AIR.defaultBlockState());
		set(level, at(c, f, side, HALF, 0, 1), Blocks.AIR.defaultBlockState());
		// The Held: sitting against the back wall, the chain held up from the ceiling over them
		int back = -HALF + 1;
		set(level, at(c, f, side, back, 0, 0), axis(Blocks.BONE_BLOCK.defaultBlockState(), Direction.Axis.Y));
		set(level, at(c, f, side, back, 0, 1), rotated(Blocks.SKELETON_SKULL.defaultBlockState(), f));
		set(level, at(c, f, side, back, 0, 2), chain(Direction.Axis.Y));
		set(level, at(c, f, side, back, 0, 3), chain(Direction.Axis.Y));
		// The chain runs over the floor from the Held to the door
		for (int u = back + 1; u < HALF; u++) {
			set(level, at(c, f, side, u, 0, 0), chain(f.getAxis()));
		}
		// The anchor they never bound, and what they kept
		set(level, at(c, f, side, back, -2, 0), Blocks.LODESTONE.defaultBlockState());
		BlockPos barrel = at(c, f, side, back, 2, 0);
		set(level, barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, f));
		if (level.getBlockEntity(barrel) instanceof Container box) {
			Loot.fill(level, barrel, box, table);
		}
		// Soul lanterns that never went out, and the dust of a long time
		BlockState lantern = hanging(Blocks.SOUL_LANTERN.defaultBlockState());
		for (int u : new int[] {-2, 2}) {
			for (int v : new int[] {-2, 2}) {
				set(level, at(c, f, side, u, v, TALL - 1), lantern);
			}
		}
		set(level, at(c, f, side, back, -3, TALL - 1), Blocks.COBWEB.defaultBlockState());
		set(level, at(c, f, side, back, 3, TALL - 1), Blocks.COBWEB.defaultBlockState());
		set(level, at(c, f, side, HALF - 1, -3, TALL - 1), Blocks.COBWEB.defaultBlockState());
	}

	/**
	 * The way in: from the door towards the arrival, a passage two high is cut through whatever stands there, with
	 * floor under it and no lava beside it, until it comes out into the open; the chain is laid along it. Returns how
	 * many links were laid.
	 */
	public static int passage(ServerLevel level, Site site, int dist) {
		BlockPos c = site.centre();
		Direction f = site.front(), side = f.getClockWise();
		BlockState stone = Blocks.BLACKSTONE.defaultBlockState();
		int links = 0, open = 0;
		for (int u = HALF + 1; u <= dist - 4; u++) {
			BlockPos foot = at(c, f, side, u, 0, 0), head = foot.above(), floor = foot.below();
			// never into the player's own portal (the way in stops at its frame), nor through anything someone built
			if (!Loaded.ready(level, foot) || kept(level.getBlockState(foot)) || kept(level.getBlockState(head)) || !natural(level.getBlockState(foot))
					|| !natural(level.getBlockState(head)) || level.getBlockEntity(foot) != null || level.getBlockEntity(head) != null) {
				break;
			}
			boolean wasOpen = level.getBlockState(foot).isAir() && level.getBlockState(head).isAir() && level.getBlockState(floor).isSolid();
			open = wasOpen ? open + 1 : 0;
			if (open >= 2 && u > HALF + 3) {
				// Out in the open: the chain goes on only as far as the ground carries it
				if (level.getBlockState(foot).isAir() && level.getBlockState(floor).isSolid() && u % 2 == 0) {
					set(level, foot, chain(f.getAxis()));
					links++;
				}
				continue;
			}
			set(level, foot, Blocks.AIR.defaultBlockState());
			set(level, head, Blocks.AIR.defaultBlockState());
			if (!level.getBlockState(floor).isSolid() && !level.getBlockState(floor).is(Blocks.BEDROCK)) {
				set(level, floor, stone);
			}
			// Lava beside or over the passage is stopped with blackstone
			for (BlockPos q : new BlockPos[] {foot.relative(side), foot.relative(side.getOpposite()), head.relative(side),
					head.relative(side.getOpposite()), head.above()}) {
				if (!level.getBlockState(q).getFluidState().isEmpty()) {
					set(level, q, stone);
				}
			}
			set(level, foot, chain(f.getAxis()));
			links++;
		}
		return links;
	}

	/** A position in the chamber's own terms: u towards the door, v to the door's right, y up from the floor. */
	static BlockPos at(BlockPos c, Direction f, Direction side, int u, int v, int y) {
		return c.offset(f.getStepX() * u + side.getStepX() * v, y, f.getStepZ() * u + side.getStepZ() * v);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState s) {
		if (!kept(level.getBlockState(pos))) {
			level.setBlock(pos, s, 2);
		}
	}

	/** Blocks the chamber never touches: bedrock, and any portal and its frame. */
	private static boolean kept(BlockState s) {
		return s.is(Blocks.BEDROCK) || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN);
	}

	/** An iron chain along an axis (named "chain" before the copper chains came). */
	public static BlockState chain(Direction.Axis a) {
		BlockState s = block("iron_chain");
		if (s == null) {
			s = block("chain");
		}
		return axis(s == null ? Blocks.COBWEB.defaultBlockState() : s, a);
	}

	public static boolean isChain(BlockState s) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
		return id.getPath().equals("iron_chain") || id.getPath().equals("chain");
	}

	private static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", id)).map(h -> h.value().defaultBlockState()).orElse(null);
	}

	private static BlockState axis(BlockState s, Direction.Axis a) {
		return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
	}

	private static BlockState hanging(BlockState s) {
		return s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, true) : s;
	}

	/** A skull turned to look along a direction. */
	private static BlockState rotated(BlockState s, Direction f) {
		if (!s.hasProperty(BlockStateProperties.ROTATION_16)) {
			return s;
		}
		int r = switch (f) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		return s.setValue(BlockStateProperties.ROTATION_16, r);
	}
}
