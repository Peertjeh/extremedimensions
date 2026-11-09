package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import com.extremedimensions.config.Config;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.FightBlocks;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * What the Elders do to the world around them. Their great blows are real explosions (their fire is only seen, never
 * lit: fire would spread and burn on after the fight); their charges and beams tear furrows through the ground and throw
 * the torn blocks into the air, where they crumble away (they never come down as blocks); the ground they strike
 * changes for a while ({@code elder_mark_seconds}): the Matriarch's turns to root and moss, the Ferryman's to soul soil
 * burning with soul fire, the Weaver's is scorched and its sand fused to glass. Thorns and chains they raise are
 * temporary too and sink back after a while; whatever is still marked or raised when a fight is over goes back then.
 * Nothing of this happens with {@code elder_griefing} off or when the mobGriefing rule forbids it: then the blows still
 * hurt and throw players, but the world stays as it was. Containers, blocks that hold data and the blocks a Wither
 * cannot break are never touched. A block of an arena torn out is neither thrown nor dropped. Every block put down here
 * is held by {@link FightBlocks}, which sees the world's own block back in the end, even after a crash.
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
		/** Raised for a while (thorns, the bars of a cage): no blow tears it up. The ground an Elder marked is torn up like the rest. */
		boolean raised;
		/** Times it may still wait for its ground to load again (each a second, an hour in all) before it is handed on. */
		int tries = 3600;
		/** What the last look at its ground saw (for a test's failure message). */
		String seen = "not looked yet";
	}

	private static final List<Temporary> TEMPORARY = new ArrayList<>();
	/** The same by level and position (a place has one at most). */
	private static final Map<ServerLevel, Long2ObjectOpenHashMap<Temporary>> AT = new HashMap<>();
	/** Torn blocks in the air, flying off as block displays. */
	private static final List<BlockFlight.Flight> THROWN = new ArrayList<>();
	/** Most torn blocks in the air at once; past that a torn block simply breaks. */
	private static final int THROWN_MOST = 160;
	/** How far round an Elder whose fight is over what it raised and marked goes back at once (it fights within its reach). */
	static final double SWEEP = 256;
	/** Blocks changed this tick (a cap per tick keeps a ultimate's shockwave from stalling the server). */
	private static int budget = 0;
	private static final int BUDGET_PER_TICK = 900;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** Whether the world may be torn up here. */
	public static boolean griefing(ServerLevel level) {
		return Config.ELDER_GRIEFING > 0 && Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_GRIEFING));
	}

	/**
	 * A great blow: a real explosion; without griefing it hurts and throws but breaks nothing. Its fire, if asked, is only
	 * seen: a fire lit would spread through the world and burn on after the fight (on netherrack for good).
	 */
	static void blast(ElderBoss b, double x, double y, double z, float power, boolean fire) {
		ServerLevel level = (ServerLevel) b.level();
		boolean grief = griefing(level);
		level.explode(b, x, y, z, power, false, grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		if (fire) {
			WorldFx.particleIn(level, "minecraft:flame", x, y + 0.4, z, power * 0.6, 0.4, power * 0.6, 0.04, (int) (power * 12));
			WorldFx.particleIn(level, "minecraft:lava", x, y + 0.3, z, power * 0.5, 0.2, power * 0.5, 0.0, (int) (power * 3));
		}
		AttackFx.impact(level, AttackFx.of(b), AttackFx.IMPACT_BLAST, x, y, z, power);
	}

	/**
	 * Whether a block is of a kind an Elder may tear up at all: not air, nothing that holds data (a chest, a sign),
	 * nothing a Wither cannot break, no portal, nothing as hard as obsidian. (The upheaval of an ultimate asks this of an
	 * arena's own floor, see {@link ElderUpheaval}.)
	 */
	static boolean tearable(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || s.hasBlockEntity() || s.is(BlockTags.WITHER_IMMUNE) || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL)) {
			return false;
		}
		float hard = s.getDestroySpeed(level, pos);
		return hard >= 0 && hard < 30;
	}

	/** Whether an Elder may tear up this block here (see {@link #tearable}). */
	static boolean breakable(ServerLevel level, BlockPos pos, BlockState s) {
		if (!tearable(level, pos, s)) {
			return false;
		}
		// What an Elder raised for a while is not torn up (it sinks back itself); an arena's blocks break like the rest of
		// the world unless the settings keep them whole (they drop nothing either way, and what is left comes down)
		return !temporary(level, pos) && !(Config.ELDER_ARENA_BREAKABLE <= 0 && ElderArena.holds(level, pos));
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
		boolean arena = false;
		dig:
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
						break dig;
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
						arena = true;
					} else if (flingOneIn > 0 && s.isSolid() && rnd.nextInt(flingOneIn) == 0) {
						fling(level, pos, s, dx, dz);
					} else {
						level.destroyBlock(pos, rnd.nextInt(6) == 0, null, 512);
					}
				}
			}
		}
		if (arena) {
			// What is left of the arena round it may have lost its support
			ArenaRuin.struck(level, x, y, z, radius + 1);
		}
	}

	/**
	 * One block torn out and thrown into the air, away from (0, 0) by its offset: it flies off as a display, crumbling to
	 * nothing as it goes. (A falling block would land wherever it came down and lie there for good.)
	 */
	private static void fling(ServerLevel level, BlockPos pos, BlockState s, double ox, double oz) {
		level.destroyBlock(pos, false, null, 512);
		if (THROWN.size() >= THROWN_MOST) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double d = Math.max(0.5, Math.sqrt(ox * ox + oz * oz));
		double far = 2.5 + rnd.nextDouble() * 3.5;
		double[] to = {ox / d * far + rnd.nextGaussian() * 0.6, 2.5 + rnd.nextDouble() * 3.0, oz / d * far + rnd.nextGaussian() * 0.6};
		BlockFlight.Flight f = BlockFlight.out(level, pos, s, to, 14 + rnd.nextInt(8), "minecraft:poof", 6);
		if (f != null) {
			THROWN.add(f);
		}
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

	/**
	 * The ground in a disc around a point takes the Elder's mark (about {@code share} of it, ragged at the rim) for
	 * {@code elder_mark_seconds}, a little more or less block by block, so it frays back rather than going all at once.
	 */
	public static void mark(ServerLevel level, double x, double y, double z, double radius, double share, Mark mark) {
		if (!griefing(level)) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int ticks = Math.max(1, Config.ELDER_MARK_SECONDS) * 20;
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
				BlockPos above = pos.above();
				BlockState over = level.getBlockState(above);
				// (marked already, it is left as it is; and never under water or lava, where soul sand and magma would raise
				// bubble columns that outlast the mark)
				if (!breakable(level, pos, s) || at(level, pos) != null || !over.getFluidState().isEmpty()) {
					continue;
				}
				budget++;
				int left = ticks + rnd.nextInt(40);
				BlockState to = changed(s, mark, rnd);
				if (to != null && to != s) {
					lay(level, pos, to, left, false);
				}
				if (over.canBeReplaced() && at(level, above) == null && !ElderArena.holds(level, above) && !ElderUpheaval.holds(level, above)
						&& !over.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
					BlockState top = cover(mark, to != null ? to : s, rnd);
					if (top != null) {
						lay(level, above, top, left - 1, false);
					} else if (!over.isAir()) {
						// Grass, flowers and the like give way to it while it lasts
						lay(level, above, AIR, left - 1, false);
					}
				}
				if (mark == Mark.SCORCH && rnd.nextInt(12) == 0) {
					// It smoulders: flames are seen, but no fire is lit
					WorldFx.particleIn(level, "minecraft:flame", above.getX() + 0.5, above.getY() + 0.2, above.getZ() + 0.5, 0.3, 0.1, 0.3, 0.01, 4);
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

	/**
	 * What lies on the marked ground for a while: roots and dead bushes for the Matriarch; soul fire for the Ferryman, only
	 * on soul soil or soul sand (where it burns without spreading; anywhere else it would go out at once); for the Weaver
	 * nothing (real fire would spread through the world and stay).
	 */
	private static BlockState cover(Mark mark, BlockState ground, ThreadLocalRandom rnd) {
		return switch (mark) {
			case ROOT -> rnd.nextInt(5) == 0 ? Blocks.HANGING_ROOTS.defaultBlockState() : rnd.nextInt(4) == 0 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
			case SOUL -> rnd.nextInt(5) == 0 && (ground.is(Blocks.SOUL_SOIL) || ground.is(Blocks.SOUL_SAND)) ? Blocks.SOUL_FIRE.defaultBlockState() : null;
			default -> null;
		};
	}

	/**
	 * A column raised out of the ground for a while (thorns, chains), {@code height} blocks of {@code block} into the
	 * air above the ground; it sinks back after {@code ticks}. Only air and plants are taken (and what a mark laid there,
	 * which gives way to it), and only what is still the raised block is put back.
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
			Temporary t = at(level, pos);
			if (t != null) {
				if (t.raised) {
					break;
				}
				// (what a mark laid there goes first)
				TEMPORARY.remove(t);
				restore(t);
			}
			BlockState was = level.getBlockState(pos);
			// (nor into ground an ultimate broke: it goes back as it was)
			if (!was.canBeReplaced() || was.liquid() || was.hasBlockEntity() || ElderArena.holds(level, pos) || ElderUpheaval.holds(level, pos)) {
				break;
			}
			lay(level, pos, block.defaultBlockState(), ticks + i * 2, true);
		}
	}

	/** A block put down for {@code ticks} (held by {@link FightBlocks} meanwhile): what stood there goes back after. */
	private static void lay(ServerLevel level, BlockPos pos, BlockState state, int ticks, boolean raised) {
		BlockState was = FightBlocks.place(level, pos, state, 2 | 16);
		Temporary t = new Temporary();
		t.level = level;
		t.pos = pos.immutable();
		t.placed = level.getBlockState(pos);
		t.original = FightBlocks.whole(was);
		t.left = Math.max(1, ticks);
		t.raised = raised;
		TEMPORARY.add(t);
		AT.computeIfAbsent(level, k -> new Long2ObjectOpenHashMap<>()).put(t.pos.asLong(), t);
	}

	private static Temporary at(Level level, BlockPos pos) {
		Long2ObjectOpenHashMap<Temporary> m = level instanceof ServerLevel sl ? AT.get(sl) : null;
		return m == null ? null : m.get(pos.asLong());
	}

	/**
	 * Whether this block was raised by an Elder for a while (thorns, the bars of a cage), or changed by the ground
	 * breaking in an ultimate ({@link ElderUpheaval}): it drops nothing, and no blow tears it up.
	 */
	public static boolean temporary(Level level, BlockPos pos) {
		if (ElderUpheaval.holds(level, pos)) {
			return true;
		}
		Temporary t = at(level, pos);
		return t != null && t.raised;
	}

	/** Blocks an Elder has raised or marked for a while, all told (for tests). */
	public static int count() {
		return TEMPORARY.size();
	}

	/** Ticks run so far (for tests and logs). */
	private static long ticked;

	/** What is known of a raised block here, for a test's failure message. */
	public static String describe(Level level, BlockPos pos) {
		Temporary t = at(level, pos);
		if (t != null) {
			return "ticked " + ticked + ", left " + t.left + ", tries " + t.tries + ", loaded " + Loaded.ready(level, pos) + ", last look: " + t.seen;
		}
		return "ticked " + ticked + ", not held, loaded " + Loaded.ready(level, pos);
	}

	public static void tick(MinecraftServer s) {
		ticked++;
		budget = 0;
		if (!THROWN.isEmpty()) {
			THROWN.removeIf(BlockFlight::step);
		}
		if (TEMPORARY.isEmpty()) {
			return;
		}
		TEMPORARY.removeIf(t -> {
			if (!due(t)) {
				return false;
			}
			restore(t);
			return true;
		});
	}

	/** One tick of a raised block: whether it is to sink back now (or has waited too long for its ground). */
	private static boolean due(Temporary t) {
		if (--t.left > 0) {
			return false;
		}
		if (!loaded(t)) {
			// Its ground is not loaded (everyone left): it sinks back once someone comes near again, not never (after an
			// hour it is handed on, and goes back once its ground loads)
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
			Temporary t = at(level, pos);
			if (t != null && due(t)) {
				TEMPORARY.remove(t);
				restore(t);
			}
		}
	}

	/** Whether a raised block's ground is in memory (asked two ways: the chunk itself, and the level's own answer). */
	private static boolean loaded(Temporary t) {
		boolean chunk = t.level.getChunkSource().getChunkNow(t.pos.getX() >> 4, t.pos.getZ() >> 4) != null;
		boolean level = Loaded.ready(t.level, t.pos);
		t.seen = "at " + ticked + " chunk " + chunk + ", level " + level + ", on " + Thread.currentThread().getName();
		return chunk || level;
	}

	/**
	 * A raised or marked block goes back: where it is still what was put there, what stood there before is put back; then
	 * it is let go (and with nothing else holding the place, the world's own block is there again, now or once its ground
	 * loads).
	 */
	private static void restore(Temporary t) {
		Long2ObjectOpenHashMap<Temporary> m = AT.get(t.level);
		if (m != null && m.get(t.pos.asLong()) == t) {
			m.remove(t.pos.asLong());
			if (m.isEmpty()) {
				AT.remove(t.level);
			}
		}
		if (loaded(t) && t.level.getBlockState(t.pos).is(t.placed.getBlock())) {
			FightBlocks.put(t.level, t.pos, t.original, 2 | 16);
		}
		FightBlocks.release(t.level, t.pos);
	}

	/**
	 * An Elder's fight is over: what was raised and marked within {@code reach} of (x, z) goes back now, the last laid
	 * first (what lies on the ground before the ground under it).
	 */
	public static void release(ServerLevel level, double x, double z, double reach) {
		if (TEMPORARY.isEmpty()) {
			return;
		}
		List<Temporary> back = new ArrayList<>();
		TEMPORARY.removeIf(t -> {
			double dx = t.pos.getX() + 0.5 - x, dz = t.pos.getZ() + 0.5 - z;
			if (t.level == level && dx * dx + dz * dz <= reach * reach) {
				back.add(t);
				return true;
			}
			return false;
		});
		for (int i = back.size() - 1; i >= 0; i--) {
			restore(back.get(i));
		}
	}

	/** On stopping: every raised and marked block sinks back now (the last first), so none is left behind in the world. */
	public static void clear() {
		for (int i = TEMPORARY.size() - 1; i >= 0; i--) {
			restore(TEMPORARY.get(i));
		}
		TEMPORARY.clear();
		AT.clear();
		THROWN.forEach(BlockFlight::cancel);
		THROWN.clear();
	}
}
