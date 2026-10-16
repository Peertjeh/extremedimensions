package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * What the Elders do to the world around them. Their great blows are real explosions; their charges and beams tear
 * furrows through the ground and throw the torn blocks into the air (they come down again where they fall); the
 * ground they strike changes: the Matriarch's turns to root and moss, the Ferryman's to soul soil burning with soul
 * fire, the Weaver's is scorched and its sand fused to glass. Thorns and chains they raise are temporary and sink back
 * after a while. Nothing of this happens with {@code elder_griefing} off or when the mobGriefing rule forbids it: then
 * the blows still hurt and throw players, but the world stays as it was. Containers, blocks that hold data and the
 * blocks a Wither cannot break are never touched. A block of an arena torn out is neither thrown nor dropped.
 */
public final class ElderTerrain {
	private ElderTerrain() {}

	/** How the ground changes under a blow. */
	public enum Mark { ROOT, SOUL, SCORCH }

	private static final class Temporary {
		ServerLevel level;
		BlockPos pos;
		BlockState placed, original;
		int left;
		/** Times it may still wait for its ground to load again (each a second, an hour in all) before it is forgotten. */
		int tries = 3600;
		/** What the last look at its ground saw (for a test's failure message). */
		String seen = "not looked yet";
	}

	private static final List<Temporary> TEMPORARY = new ArrayList<>();
	/** Blocks changed this tick (a cap per tick keeps a ultimate's shockwave from stalling the server). */
	private static int budget = 0;
	private static final int BUDGET_PER_TICK = 900;

	/** Whether the world may be torn up here. */
	public static boolean griefing(ServerLevel level) {
		return Config.ELDER_GRIEFING > 0 && Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_GRIEFING));
	}

	/** A great blow: a real explosion (fire if asked); without griefing it hurts and throws but breaks nothing. */
	static void blast(ElderBoss b, double x, double y, double z, float power, boolean fire) {
		ServerLevel level = (ServerLevel) b.level();
		boolean grief = griefing(level);
		level.explode(b, x, y, z, power, fire && grief, grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		AttackFx.impact(level, AttackFx.of(b), AttackFx.IMPACT_BLAST, x, y, z, power);
	}

	private static boolean breakable(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || s.hasBlockEntity() || s.is(BlockTags.WITHER_IMMUNE) || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL)) {
			return false;
		}
		// An arena's blocks are not torn up or changed (it puts the ground back itself, and what it built drops nothing)
		if (ElderArena.holds(level, pos) || temporary(level, pos)) {
			return false;
		}
		float hard = s.getDestroySpeed(level, pos);
		return hard >= 0 && hard < 30;
	}

	/**
	 * Tears out the ground in a bowl around a point: the blocks within {@code radius} and down to {@code depth} below
	 * it are broken, about one in {@code flingOneIn} thrown into the air away from the point. Liquids are left.
	 */
	public static void furrow(ServerLevel level, double x, double y, double z, double radius, int depth, int up, int flingOneIn) {
		if (!griefing(level)) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int r = (int) Math.ceil(radius);
		int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d2 = dx * dx + dz * dz;
				if (d2 > radius * radius) {
					continue;
				}
				// Deeper in the middle, shallow at the rim
				int deep = (int) Math.round(depth * (1 - Math.sqrt(d2) / (radius + 0.5)));
				for (int dy = up; dy >= -deep; dy--) {
					if (budget >= BUDGET_PER_TICK) {
						return;
					}
					BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
					BlockState s = level.getBlockState(pos);
					if (s.liquid() || !breakable(level, pos, s)) {
						continue;
					}
					budget++;
					if (ElderArena.holds(level, pos)) {
						// An arena's block is torn out but neither thrown nor dropped
						level.destroyBlock(pos, false, null, 512);
					} else if (flingOneIn > 0 && s.isSolid() && rnd.nextInt(flingOneIn) == 0) {
						fling(level, pos, s, dx, dz);
					} else {
						level.destroyBlock(pos, rnd.nextInt(6) == 0, null, 512);
					}
				}
			}
		}
	}

	/** One block thrown into the air, away from (0, 0) by its offset; it lands as a block wherever it comes down. */
	private static void fling(ServerLevel level, BlockPos pos, BlockState s, double ox, double oz) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, s);
		double d = Math.max(0.5, Math.sqrt(ox * ox + oz * oz));
		double sp = 0.25 + rnd.nextDouble() * 0.45;
		fb.setDeltaMovement(ox / d * sp + rnd.nextGaussian() * 0.08, 0.55 + rnd.nextDouble() * 0.6, oz / d * sp + rnd.nextGaussian() * 0.08);
		fb.dropItem = false;
	}

	/** The top block of the ground at a column near y (searching a few blocks up and down); null if none. */
	static BlockPos top(ServerLevel level, int x, int y, int z) {
		for (int by = y + 4; by >= y - 6; by--) {
			BlockPos pos = new BlockPos(x, by, z);
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && !s.canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced()) {
				return pos;
			}
		}
		return null;
	}

	/** The ground in a disc around a point takes the Elder's mark (about {@code share} of it, ragged at the rim). */
	public static void mark(ServerLevel level, double x, double y, double z, double radius, double share, Mark mark) {
		if (!griefing(level)) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int r = (int) Math.ceil(radius);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius || rnd.nextDouble() > share * (1.2 - 0.5 * d / Math.max(1, radius)) || budget >= BUDGET_PER_TICK) {
					continue;
				}
				BlockPos pos = top(level, (int) Math.floor(x) + dx, (int) Math.floor(y), (int) Math.floor(z) + dz);
				if (pos == null) {
					continue;
				}
				BlockState s = level.getBlockState(pos);
				if (!breakable(level, pos, s)) {
					continue;
				}
				budget++;
				BlockState to = changed(s, mark, rnd);
				if (to != null) {
					level.setBlock(pos, to, 3);
				}
				BlockPos above = pos.above();
				BlockState over = level.getBlockState(above);
				if (over.canBeReplaced() && !over.liquid() && !ElderArena.holds(level, above)) {
					BlockState top = cover(mark, rnd);
					if (top != null) {
						level.setBlock(above, top, 3);
					} else if (!over.isAir()) {
						// Grass, flowers and the like do not survive it
						level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	private static BlockState changed(BlockState s, Mark mark, ThreadLocalRandom rnd) {
		boolean soft = s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.CLAY);
		boolean sand = s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL);
		boolean stone = s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.NETHERRACK) || s.is(Blocks.END_STONE)
				|| s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.TUFF) || s.is(Blocks.BLACKSTONE);
		switch (mark) {
			case ROOT -> {
				if (soft) {
					int k = rnd.nextInt(4);
					return (k == 0 ? Blocks.ROOTED_DIRT : k == 1 ? Blocks.MOSS_BLOCK : k == 2 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState();
				}
				if (stone && rnd.nextInt(3) == 0) {
					return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
				}
				return null;
			}
			case SOUL -> {
				if (soft || sand || stone) {
					return (rnd.nextInt(3) == 0 ? Blocks.SOUL_SAND : Blocks.SOUL_SOIL).defaultBlockState();
				}
				return null;
			}
			default -> {
				if (sand) {
					return (rnd.nextInt(3) == 0 ? Blocks.TINTED_GLASS : Blocks.GLASS).defaultBlockState();
				}
				if (soft) {
					return Blocks.COARSE_DIRT.defaultBlockState();
				}
				if (stone && rnd.nextInt(3) == 0) {
					return Blocks.MAGMA_BLOCK.defaultBlockState();
				}
				return null;
			}
		}
	}

	private static BlockState cover(Mark mark, ThreadLocalRandom rnd) {
		return switch (mark) {
			case ROOT -> rnd.nextInt(5) == 0 ? Blocks.HANGING_ROOTS.defaultBlockState() : rnd.nextInt(4) == 0 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
			case SOUL -> rnd.nextInt(5) == 0 ? Blocks.SOUL_FIRE.defaultBlockState() : null;
			default -> rnd.nextInt(12) == 0 ? Blocks.FIRE.defaultBlockState() : null;
		};
	}

	/**
	 * A column raised out of the ground for a while (thorns, chains), {@code height} blocks of {@code block} into the
	 * air above the ground; it sinks back after {@code ticks}. Only air and plants are taken, and only what is still the
	 * raised block is put back.
	 */
	public static void column(ServerLevel level, double x, double y, double z, int height, Block block, int ticks) {
		if (!griefing(level)) {
			return;
		}
		BlockPos ground = top(level, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		if (ground == null) {
			return;
		}
		for (int i = 1; i <= height; i++) {
			BlockPos pos = ground.above(i);
			BlockState was = level.getBlockState(pos);
			if (!was.canBeReplaced() || was.liquid() || was.hasBlockEntity() || ElderArena.holds(level, pos)) {
				break;
			}
			BlockState put = block.defaultBlockState();
			level.setBlock(pos, put, 3);
			Temporary t = new Temporary();
			t.level = level;
			t.pos = pos;
			t.placed = level.getBlockState(pos);
			t.original = was.isAir() ? was : Blocks.AIR.defaultBlockState();
			t.left = ticks + i * 2;
			TEMPORARY.add(t);
		}
	}

	/** Whether this block was raised by an Elder for a while (thorns, the bars of a cage): it drops nothing. */
	public static boolean temporary(Level level, BlockPos pos) {
		for (Temporary t : TEMPORARY) {
			if (t.level == level && t.pos.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Ticks run so far (for tests and logs). */
	private static long ticked;

	/** What is known of a raised block here, for a test's failure message. */
	public static String describe(Level level, BlockPos pos) {
		for (Temporary t : TEMPORARY) {
			if (t.level == level && t.pos.equals(pos)) {
				return "ticked " + ticked + ", left " + t.left + ", tries " + t.tries + ", loaded " + level.isLoaded(pos) + ", last look: " + t.seen;
			}
		}
		return "ticked " + ticked + ", not held, loaded " + level.isLoaded(pos);
	}

	public static void tick(MinecraftServer s) {
		ticked++;
		budget = 0;
		if (TEMPORARY.isEmpty()) {
			return;
		}
		Iterator<Temporary> it = TEMPORARY.iterator();
		while (it.hasNext()) {
			Temporary t = it.next();
			if (due(t)) {
				it.remove();
				restore(t);
			}
		}
	}

	/** One tick of a raised block: whether it is to sink back now (or has waited too long for its ground). */
	private static boolean due(Temporary t) {
		if (--t.left > 0) {
			return false;
		}
		if (!loaded(t)) {
			// Its ground is not loaded (everyone left): it sinks back once someone comes near again, not never
			t.left = 20;
			return --t.tries <= 0;
		}
		return true;
	}

	/**
	 * For tests: {@code ticks} ticks go by now for the raised blocks at a spot. A test's own ground counts as loaded
	 * only while the test itself runs, not when the server ticks the raised blocks, as a player's ground always does.
	 */
	public static void tickAt(Level level, BlockPos pos, int ticks) {
		for (int i = 0; i < ticks; i++) {
			Iterator<Temporary> it = TEMPORARY.iterator();
			while (it.hasNext()) {
				Temporary t = it.next();
				if (t.level == level && t.pos.equals(pos) && due(t)) {
					it.remove();
					restore(t);
				}
			}
		}
	}

	/** Whether a raised block's ground is in memory (asked two ways: the chunk itself, and the level's own answer). */
	private static boolean loaded(Temporary t) {
		boolean chunk = t.level.getChunkSource().getChunkNow(t.pos.getX() >> 4, t.pos.getZ() >> 4) != null;
		boolean level = t.level.isLoaded(t.pos);
		t.seen = "at " + ticked + " chunk " + chunk + ", level " + level + ", on " + Thread.currentThread().getName();
		return chunk || level;
	}

	private static void restore(Temporary t) {
		if (loaded(t) && t.level.getBlockState(t.pos).is(t.placed.getBlock())) {
			t.level.setBlock(t.pos, t.original, 3);
		}
	}

	/** On stopping: every raised block sinks back now, so none is left behind in the world. */
	public static void clear() {
		for (Temporary t : TEMPORARY) {
			restore(t);
		}
		TEMPORARY.clear();
	}
}
