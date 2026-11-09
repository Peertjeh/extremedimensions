package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderArena;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.ElderTerrain;
import com.extremedimensions.elder.ElderUpheaval;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.Matriarch;
import com.extremedimensions.elder.MatriarchService;
import com.extremedimensions.elder.PaleMason;
import com.extremedimensions.elder.PaleMasonService;
import com.extremedimensions.elder.Weaver;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.server.FightBlocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * Nothing a boss fight puts into the world is left once it is over. The ground an Elder marks goes back when the fight
 * ends and when its time is up; a furrow throws no block that lands anywhere; no arena is built of anything that grows,
 * spreads or falls by itself; the journal puts back what a fight cut short by a crash left; and a whole fight with each
 * of the five Elders (its arena, its blows, its ultimate), ended by its death or its going, leaves not one block of its
 * own and none of the things its arena could drop (chorus fruit, saplings, lanterns...). The whole fights are fought on a
 * platform of their own high over the world, far from every other test, in chunks forced round it.
 */
public class LeftoverTests {
	/** Items a fight's blocks could leave lying about if they came down as they should not. */
	private static final Set<String> STRAY_ITEMS = Set.of("chorus_fruit", "popped_chorus_fruit", "chorus_flower", "hanging_roots", "moss_carpet",
			"mangrove_roots", "crimson_roots", "dead_bush", "lantern", "soul_lantern", "end_rod", "amethyst_shard", "chain", "iron_chain",
			"glow_lichen", "vine", "sculk_catalyst", "small_amethyst_bud", "amethyst_cluster", "cobweb", "string", "apple", "stick");

	private static String id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	private static BlockState mc(String id) {
		return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", id)).map(h -> h.value().defaultBlockState())
				.orElse(Blocks.STONE.defaultBlockState());
	}

	/** Whether an item lying about is one a fight's blocks could have dropped (a sapling of any tree among them). */
	private static boolean stray(ItemEntity e) {
		String item = BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath();
		return STRAY_ITEMS.contains(item) || item.endsWith("_sapling") || item.equals("mangrove_propagule");
	}

	// ------------------------------------------------------------------ the ground an Elder marks

	/**
	 * The ground an Elder strikes (roots and moss on grass, soul soil on sand, glass and magma on stone) goes back exactly
	 * as it was when the fight is over, and again once its time is up; no real fire is lit by any of it.
	 */
	@GameTest(maxTicks = 40)
	public void theGroundAnElderMarksGoesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int seconds = Config.ELDER_MARK_SECONDS, grief = Config.ELDER_GRIEFING;
		GameRules rules = level.getGameRules();
		boolean griefing = rules.get(GameRules.MOB_GRIEFING);
		List<BlockPos> box = new ArrayList<>();
		List<BlockState> before = new ArrayList<>(), world = new ArrayList<>();
		for (int x = -1; x <= 9; x++) {
			for (int z = -1; z <= 9; z++) {
				for (int y = 0; y <= 5; y++) {
					BlockPos q = helper.absolutePos(new BlockPos(x, y, z));
					box.add(q);
					world.add(level.getBlockState(q));
				}
			}
		}
		try {
			Config.ELDER_GRIEFING = 1;
			rules.set(GameRules.MOB_GRIEFING, true, level.getServer());
			Config.ELDER_MARK_SECONDS = 600;
			for (int x = 0; x <= 8; x++) {
				for (int z = 0; z <= 8; z++) {
					helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
					helper.setBlock(new BlockPos(x, 1, z), x < 3 ? Blocks.GRASS_BLOCK : x < 6 ? Blocks.SAND : Blocks.STONE);
					for (int y = 2; y <= 5; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
					}
				}
			}
			// Short grass on some of the grass, to give way to the mark and come back
			for (int z = 1; z <= 7; z += 2) {
				level.setBlock(helper.absolutePos(new BlockPos(1, 2, z)), mc("short_grass"), 2 | 16);
			}
			for (BlockPos q : box) {
				before.add(level.getBlockState(q));
			}
			BlockPos root = helper.absolutePos(new BlockPos(2, 2, 4)), soul = helper.absolutePos(new BlockPos(4, 2, 4)),
					scorch = helper.absolutePos(new BlockPos(6, 2, 4));
			// (each within the floor laid here: roots on the grass, soul soil on the sand, scorch on the sand and stone)
			Runnable strike = () -> {
				ElderTerrain.mark(level, root.getX() + 0.5, root.getY(), root.getZ() + 0.5, 2.0, 1.0, ElderTerrain.Mark.ROOT);
				ElderTerrain.mark(level, soul.getX() + 0.5, soul.getY(), soul.getZ() + 0.5, 1.5, 1.0, ElderTerrain.Mark.SOUL);
				ElderTerrain.mark(level, scorch.getX() + 0.5, scorch.getY(), scorch.getZ() + 0.5, 2.0, 1.0, ElderTerrain.Mark.SCORCH);
			};
			// Struck, then the fight is over
			strike.run();
			int changed = 0, held = 0, fire = 0;
			for (int i = 0; i < box.size(); i++) {
				BlockState now = level.getBlockState(box.get(i));
				if (now != before.get(i)) {
					changed++;
					held += FightBlocks.holds(level, box.get(i)) ? 1 : 0;
				}
				fire += id(now).equals("fire") ? 1 : 0;
			}
			check(helper, changed >= 8, "the marks changed only %s blocks", changed);
			check(helper, held == changed, "%s of the %s blocks the marks changed are not held for the fight", changed - held, changed);
			check(helper, fire == 0, "the marks lit %s fires", fire);
			ElderTerrain.release(level, soul.getX() + 0.5, soul.getZ() + 0.5, 10);
			check(helper, wrong(level, box, before) == null, "after the fight the ground is not as it was: %s", wrong(level, box, before));
			// Struck again, and its time runs out
			Config.ELDER_MARK_SECONDS = 1;
			strike.run();
			int again = 0;
			for (int i = 0; i < box.size(); i++) {
				again += level.getBlockState(box.get(i)) != before.get(i) ? 1 : 0;
			}
			check(helper, again >= 8, "the second marks changed only %s blocks", again);
			// (a second, and up to two more as it frays: ticked here, where the test's own ground counts as loaded)
			for (BlockPos q : box) {
				ElderTerrain.tickAt(level, q, 80);
			}
			check(helper, wrong(level, box, before) == null, "once its time was up the ground is not as it was: %s", wrong(level, box, before));
		} finally {
			Config.ELDER_MARK_SECONDS = seconds;
			Config.ELDER_GRIEFING = grief;
			rules.set(GameRules.MOB_GRIEFING, griefing, level.getServer());
			// (anything still marked goes back, and the ground is as the test found it)
			BlockPos mid = helper.absolutePos(new BlockPos(4, 2, 4));
			ElderTerrain.release(level, mid.getX() + 0.5, mid.getZ() + 0.5, 10);
			for (int i = 0; i < box.size(); i++) {
				level.setBlock(box.get(i), world.get(i), 2 | 16);
			}
		}
		helper.succeed();
	}

	private static String where(BlockPos q) {
		return q.getX() + ", " + q.getY() + ", " + q.getZ();
	}

	/** The first block of a box that is not as it was (or still held for a fight), or null. */
	private static String wrong(ServerLevel level, List<BlockPos> box, List<BlockState> before) {
		for (int i = 0; i < box.size(); i++) {
			BlockState now = level.getBlockState(box.get(i));
			if (now != before.get(i) || FightBlocks.holds(level, box.get(i))) {
				return where(box.get(i)) + " was " + before.get(i) + ", is " + now + (FightBlocks.holds(level, box.get(i)) ? " (held)" : "");
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ a furrow

	/** A furrow throws every block it tears out into the air, and none of them comes down anywhere as a block or an item. */
	@GameTest(maxTicks = 60)
	public void aFurrowThrowsNoBlockThatComesDownAnywhere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int grief = Config.ELDER_GRIEFING;
		List<BlockPos> box = new ArrayList<>();
		List<BlockState> before = new ArrayList<>(), world = new ArrayList<>();
		for (int x = -2; x <= 10; x++) {
			for (int z = -2; z <= 10; z++) {
				for (int y = 0; y <= 14; y++) {
					BlockPos q = helper.absolutePos(new BlockPos(x, y, z));
					box.add(q);
					world.add(level.getBlockState(q));
					boolean floor = x >= 0 && x <= 8 && z >= 0 && z <= 8 && y <= 2;
					level.setBlock(q, (floor ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2 | 16);
				}
			}
		}
		for (BlockPos q : box) {
			before.add(level.getBlockState(q));
		}
		BlockPos mid = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos lo = helper.absolutePos(new BlockPos(-2, 0, -2)), hi = helper.absolutePos(new BlockPos(10, 14, 10));
		AABB round = new AABB(Math.min(lo.getX(), hi.getX()), lo.getY(), Math.min(lo.getZ(), hi.getZ()), Math.max(lo.getX(), hi.getX()) + 1, hi.getY() + 1,
				Math.max(lo.getZ(), hi.getZ()) + 1).inflate(1);
		int items = level.getEntitiesOfClass(ItemEntity.class, round).size();
		GameRules rules = level.getGameRules();
		boolean griefing = rules.get(GameRules.MOB_GRIEFING);
		try {
			Config.ELDER_GRIEFING = 1;
			rules.set(GameRules.MOB_GRIEFING, true, level.getServer());
			// Every block it tears out is thrown
			ElderTerrain.furrow(level, mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, 2.5, 1, 0, 1);
		} finally {
			Config.ELDER_GRIEFING = grief;
			rules.set(GameRules.MOB_GRIEFING, griefing, level.getServer());
		}
		int torn = 0;
		for (int i = 0; i < box.size(); i++) {
			torn += !before.get(i).isAir() && level.getBlockState(box.get(i)).isAir() ? 1 : 0;
		}
		int tornNow = torn;
		helper.runAfterDelay(40, () -> {
			int falling = level.getEntitiesOfClass(FallingBlockEntity.class, round).size();
			int flying = level.getEntitiesOfClass(Display.BlockDisplay.class, round).size();
			int dropped = level.getEntitiesOfClass(ItemEntity.class, round).size() - items;
			String landed = null;
			for (int i = 0; i < box.size() && landed == null; i++) {
				BlockState now = level.getBlockState(box.get(i));
				if (!now.isAir() && now != before.get(i)) {
					landed = where(box.get(i)) + ": " + now;
				}
			}
			// The ground as the test found it, for the tests round this one
			for (int i = 0; i < box.size(); i++) {
				level.setBlock(box.get(i), world.get(i), 2 | 16);
			}
			check(helper, tornNow >= 8, "the furrow tore out only %s blocks", tornNow);
			check(helper, falling == 0, "%s torn blocks are falling, to land as blocks", falling);
			check(helper, landed == null, "a torn block came down as a block at %s", landed);
			check(helper, flying == 0, "%s torn blocks are still in the air", flying);
			check(helper, dropped <= 0, "the furrow dropped %s items", dropped);
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ what arenas are built of

	/** No arena of any Elder is built of a block that grows, spreads, decays, burns or falls by itself. */
	@GameTest(environment = Alone.LEFTOVER_DESIGN, maxTicks = 40)
	public void noArenaIsBuiltOfWhatChangesByItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double size = Config.ELDER_ARENA_SIZE;
		String[] names = {"Matriarch's", "Ferryman's", "Last Weaver's", "Pale Mason's"};
		try {
			// (large enough for its wall, its great piece and many islands, small enough to be drawn up at once)
			Config.ELDER_ARENA_SIZE = 3.0;
			BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
			for (int style = 0; style < names.length; style++) {
				String grows = ElderArena.changesOfItself(level, style, centre, 4242L + style);
				check(helper, grows == null, "the %s arena is built of what changes by itself: %s", names[style], grows);
			}
		} finally {
			Config.ELDER_ARENA_SIZE = size;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the journal

	/**
	 * A fight's blocks are in the journal as they are put down; after a crash (everything in memory forgotten, the journal
	 * cut off in the middle of a record) the world's own blocks go back where the fight's stood.
	 */
	@GameTest(environment = Alone.LEFTOVER_JOURNAL, maxTicks = 40)
	public void theJournalPutsBackWhatACrashLeft(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		check(helper, FightBlocks.journal() != null, "the world keeps no journal of fight blocks");
		BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2)), over = ground.above();
		level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(over, Blocks.AIR.defaultBlockState(), 3);
		FightBlocks.place(level, ground, Blocks.MOSS_BLOCK.defaultBlockState(), 2 | 16);
		FightBlocks.place(level, over, Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
		check(helper, FightBlocks.holds(level, ground) && FightBlocks.holds(level, over), "the fight's blocks are not held");
		// (a few ticks on, the journal has had them appended)
		helper.runAfterDelay(3, () -> {
			// (whatever this tick changed is written first, as the end of every tick writes it)
			FightBlocks.tick(level.getServer());
			try {
				// A record cut off in the middle, as a crash while writing it leaves it
				Files.write(FightBlocks.journal(), new byte[] {3, 0, 0}, StandardOpenOption.APPEND);
			} catch (IOException e) {
				throw helper.assertionException("could not write to the journal: %s", e);
			}
			FightBlocks.restart(level.getServer());
			boolean known = FightBlocks.holds(level, ground) && FightBlocks.holds(level, over);
			boolean standing = level.getBlockState(over).is(Blocks.OAK_PLANKS) && level.getBlockState(ground).is(Blocks.MOSS_BLOCK);
			FightBlocks.settle(level);
			BlockState g = level.getBlockState(ground), a = level.getBlockState(over);
			boolean gone = !FightBlocks.holds(level, ground) && !FightBlocks.holds(level, over);
			level.setBlock(ground, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(over, Blocks.AIR.defaultBlockState(), 3);
			check(helper, known, "after the crash the journal did not know the fight's blocks");
			check(helper, standing, "the fight's blocks were gone before their ground was looked at");
			check(helper, g.is(Blocks.STONE) && a.isAir(), "after the crash the ground was not put back: %s under %s", g, a);
			check(helper, gone, "the fight's blocks are still held after going back");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ whole fights

	/**
	 * An Elder for a whole fight: its name and kind, how it is called for a player to a spot, how a blow of it is forced,
	 * the blows forced in turn before its ultimate, and how its fight ends (slain, or sent away).
	 */
	private record Elder(String name, Class<? extends ElderBoss> kind, BiPredicate<ServerPlayer, BlockPos> call, BiPredicate<Entity, Integer> force,
			int[] blows, boolean slain, Consumer<ElderBoss> fade) {}

	/** Ticks the forced chunks are given to load, and when (after the Elder is called) each part of the fight comes. */
	private static final int LOAD = 60, BLOWS = 10, BLOW_GAP = 70, END = 340, WAIT = 460;
	/** The platform: its top, and how far it (and what is looked at) reaches from where the player stands. */
	private static final int TOP = 200, REACH = 52, UNDER = 12, OVER = 52;

	/** What a whole fight keeps while it runs. */
	private static final class Fight {
		ServerPlayer p;
		BlockPos at;
		BlockState[] world, before;
		ElderBoss boss;
		Runnable restore;
		int arenas, upheavals, quiet = -1;
		boolean judged;
	}

	@GameTest(environment = Alone.LEFTOVER_MATRIARCH, maxTicks = LOAD + 5 + END + WAIT + 60)
	public void theMatriarchsFightLeavesNothing(GameTestHelper helper) {
		whole(helper, new Elder("Matriarch", Matriarch.class, (p, at) -> MatriarchService.summon(p, "", at), MatriarchService::force,
				new int[] {Matriarch.ROOTS, Matriarch.CATHEDRAL, Matriarch.BARRAGE}, false, b -> MatriarchService.fade((Matriarch) b)), 24008,
				(t, r) -> helper.runAfterDelay(t, r));
	}

	@GameTest(environment = Alone.LEFTOVER_FERRYMAN, maxTicks = LOAD + 5 + END + WAIT + 60)
	public void theFerrymansFightLeavesNothing(GameTestHelper helper) {
		whole(helper, new Elder("Ferryman", Ferryman.class, (p, at) -> FerrymanService.summonAt(p, at) != null, FerrymanService::force,
				new int[] {Ferryman.SLAM, Ferryman.CROSSING, Ferryman.TIDE}, true, b -> FerrymanService.fade((Ferryman) b)), 27008,
				(t, r) -> helper.runAfterDelay(t, r));
	}

	@GameTest(environment = Alone.LEFTOVER_WEAVER, maxTicks = LOAD + 5 + END + WAIT + 60)
	public void theWeaversFightLeavesNothing(GameTestHelper helper) {
		whole(helper, new Elder("Last Weaver", Weaver.class, WeaverService::summon, WeaverService::force,
				new int[] {Weaver.NEEDLEFALL, Weaver.SPINDLE, Weaver.LANCE}, false, b -> WeaverService.fade((Weaver) b)), 30008,
				(t, r) -> helper.runAfterDelay(t, r));
	}

	@GameTest(environment = Alone.LEFTOVER_MASON, maxTicks = LOAD + 5 + END + WAIT + 60)
	public void thePaleMasonsFightLeavesNothing(GameTestHelper helper) {
		whole(helper, new Elder("Pale Mason", PaleMason.class, (p, at) -> PaleMasonService.summonAt(p, at) != null, PaleMasonService::force,
				new int[] {PaleMason.WALLS, PaleMason.PILLARS, PaleMason.STRIKE}, true, b -> PaleMasonService.fade((PaleMason) b)), 33008,
				(t, r) -> helper.runAfterDelay(t, r));
	}

	/** The Knot Mother is one of a kind: her fight waits until no other test has her (see {@link KnotMotherSlot}). */
	@GameTest(environment = Alone.LEFTOVER_KNOT_MOTHER, maxTicks = 600 + LOAD + 5 + END + WAIT + 60)
	public void theKnotMothersFightLeavesNothing(GameTestHelper helper) {
		KnotMotherSlot.Steps steps = new KnotMotherSlot.Steps();
		String who = "leftovers";
		whole(helper, new Elder("Knot Mother", KnotMother.class, WeaverService::summonKnotMother, WeaverService::force,
				new int[] {KnotMother.SNARE, KnotMother.THREADLINE, Weaver.THREAD}, false, b -> {
					WeaverService.fade((Weaver) b);
					KnotMotherSlot.release(who);
				}), 36008, steps::at);
		steps.run(helper, who, 600, LOAD + 5 + END + WAIT + 50);
	}

	/**
	 * A whole fight on a platform of stone (grass round the player) high over the world, far east of the test: the Elder
	 * is called with its arena at its own size, its blows are forced one after another and then its ultimate, and it is
	 * slain or sent away. Once its arena and its broken ground are gone, every block round the fight is as it was (or
	 * broken out: what a fight destroys may stay destroyed), nothing there is held for a fight any more, and nothing its
	 * blocks could drop lies there. {@code at} schedules each step, all of them now.
	 */
	private static void whole(GameTestHelper helper, Elder elder, int east, BiConsumer<Integer, Runnable> at) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		Fight f = new Fight();
		f.at = new BlockPos((o.getX() >> 4 << 4) + east, TOP + 1, (o.getZ() >> 4 << 4) + 8);
		int c0 = (f.at.getX() - REACH - 16) >> 4, c1 = (f.at.getX() + REACH + 16) >> 4;
		int d0 = (f.at.getZ() - REACH - 16) >> 4, d1 = (f.at.getZ() + REACH + 16) >> 4;
		int side = 2 * REACH + 1, high = UNDER + OVER + 1;
		AABB round = new AABB(f.at.getX() - REACH, f.at.getY() - UNDER, f.at.getZ() - REACH, f.at.getX() + REACH + 1, f.at.getY() + OVER,
				f.at.getZ() + REACH + 1).inflate(4);
		// Every setting the fight changes, as it was
		int arena = Config.ELDER_ARENA, linger = Config.ELDER_ARENA_LINGER_SECONDS, flight = Config.ELDER_ARENA_FLIGHT_TICKS;
		double size = Config.ELDER_ARENA_SIZE, build = Config.ELDER_ARENA_BUILD_SECONDS, dissolve = Config.ELDER_ARENA_DISSOLVE_SECONDS;
		int grief = Config.ELDER_GRIEFING, upheaval = Config.ELDER_UPHEAVAL, ultimate = Config.ELDER_ULTIMATE_SECONDS;
		int sink = Config.ELDER_UPHEAVAL_LINGER_SECONDS, marks = Config.ELDER_MARK_SECONDS;
		double hoards = Config.HOARD_WILD_CHANCE;
		Difficulty difficulty = level.getDifficulty();
		GameRules rules = level.getGameRules();
		boolean griefing = rules.get(GameRules.MOB_GRIEFING);
		f.restore = () -> {
			if (f.boss != null && f.boss.isAlive()) {
				elder.fade().accept(f.boss);
			}
			if (f.boss != null && !f.boss.isRemoved()) {
				f.boss.discard();
			}
			Config.ELDER_ARENA = arena;
			Config.ELDER_ARENA_LINGER_SECONDS = linger;
			Config.ELDER_ARENA_FLIGHT_TICKS = flight;
			Config.ELDER_ARENA_SIZE = size;
			Config.ELDER_ARENA_BUILD_SECONDS = build;
			Config.ELDER_ARENA_DISSOLVE_SECONDS = dissolve;
			Config.ELDER_GRIEFING = grief;
			Config.ELDER_UPHEAVAL = upheaval;
			Config.ELDER_ULTIMATE_SECONDS = ultimate;
			Config.ELDER_UPHEAVAL_LINGER_SECONDS = sink;
			Config.ELDER_MARK_SECONDS = marks;
			Config.HOARD_WILD_CHANCE = hoards;
			level.getServer().setDifficulty(difficulty, true);
			rules.set(GameRules.MOB_GRIEFING, griefing, level.getServer());
			if (f.p != null) {
				f.p.getAbilities().invulnerable = false;
			}
			// The world as it was before the platform (whatever the fight left, if it left anything), and nothing lying about
			if (f.world != null) {
				int i = 0;
				for (int x = -REACH; x <= REACH; x++) {
					for (int z = -REACH; z <= REACH; z++) {
						for (int y = -UNDER; y <= OVER; y++, i++) {
							BlockPos q = f.at.offset(x, y, z);
							if (level.getBlockState(q) != f.world[i]) {
								level.setBlock(q, f.world[i], 2 | 16);
							}
						}
					}
				}
			}
			level.getEntitiesOfClass(ItemEntity.class, round).forEach(Entity::discard);
			level.getEntitiesOfClass(FallingBlockEntity.class, round).forEach(Entity::discard);
			for (int cx = c0; cx <= c1; cx++) {
				for (int cz = d0; cz <= d1; cz++) {
					level.setChunkForced(cx, cz, false);
				}
			}
		};
		at.accept(1, () -> {
			for (int cx = c0; cx <= c1; cx++) {
				for (int cz = d0; cz <= d1; cz++) {
					level.setChunkForced(cx, cz, true);
				}
			}
		});
		// The platform: four thick, grass round where the player stands, the air over it clear
		at.accept(LOAD, () -> {
			f.world = new BlockState[side * side * high];
			int i = 0;
			for (int x = -REACH; x <= REACH; x++) {
				for (int z = -REACH; z <= REACH; z++) {
					for (int y = -UNDER; y <= OVER; y++, i++) {
						BlockPos q = f.at.offset(x, y, z);
						BlockState was = level.getBlockState(q);
						f.world[i] = was;
						BlockState want = y > -1 || y < -4 ? Blocks.AIR.defaultBlockState()
								: y == -1 && x * x + z * z <= 100 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
						if (was != want) {
							level.setBlock(q, want, 2 | 16);
						}
					}
				}
			}
		});
		at.accept(LOAD + 2, () -> {
			f.before = new BlockState[side * side * high];
			int i = 0;
			for (int x = -REACH; x <= REACH; x++) {
				for (int z = -REACH; z <= REACH; z++) {
					for (int y = -UNDER; y <= OVER; y++, i++) {
						f.before[i] = level.getBlockState(f.at.offset(x, y, z));
					}
				}
			}
		});
		int call = LOAD + 5;
		at.accept(call, () -> {
			f.p = TestPlayers.survivor(helper, "leftovers-" + elder.name().toLowerCase(Locale.ROOT).replace(' ', '-'));
			f.p.teleportTo(f.at.getX() + 0.5, f.at.getY(), f.at.getZ() + 0.5);
			// It lives through the fight, to be fought all through it
			f.p.getAbilities().invulnerable = true;
			Config.ELDER_ARENA = 1;
			Config.ELDER_ARENA_SIZE = 1.0;
			Config.ELDER_ARENA_BUILD_SECONDS = 1;
			Config.ELDER_ARENA_FLIGHT_TICKS = 10;
			Config.ELDER_ARENA_LINGER_SECONDS = 1;
			Config.ELDER_ARENA_DISSOLVE_SECONDS = 1;
			Config.ELDER_GRIEFING = 1;
			Config.ELDER_UPHEAVAL = 1;
			Config.ELDER_ULTIMATE_SECONDS = 4;
			Config.ELDER_UPHEAVAL_LINGER_SECONDS = 5;
			// (what its blows mark stays until the fight is over: only its end takes it back)
			Config.ELDER_MARK_SECONDS = 3600;
			// (no old hoard is found lying on the platform meanwhile: its nest is no block of the fight's)
			Config.HOARD_WILD_CHANCE = 0;
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			rules.set(GameRules.MOB_GRIEFING, true, level.getServer());
			f.arenas = ElderArena.count();
			f.upheavals = ElderUpheaval.count();
			List<? extends ElderBoss> had = level.getEntitiesOfClass(elder.kind(), new AABB(f.at).inflate(64));
			boolean came = elder.call().test(f.p, f.at.offset(14, 0, 0));
			f.boss = level.getEntitiesOfClass(elder.kind(), new AABB(f.at).inflate(64)).stream().filter(b -> !had.contains(b)).findFirst().orElse(null);
			if (!came || f.boss == null) {
				f.restore.run();
			}
			check(helper, came && f.boss != null, "the %s did not come", elder.name());
		});
		// The player is kept where the fight is (its blows throw them about)
		for (int t = call + 20; t < call + END; t += 20) {
			at.accept(t, () -> {
				if (f.p != null && !f.judged) {
					f.p.teleportTo(f.at.getX() + 0.5, f.at.getY(), f.at.getZ() + 0.5);
					f.p.setDeltaMovement(0, 0, 0);
				}
			});
		}
		for (int k = 0; k < elder.blows().length; k++) {
			int blow = elder.blows()[k];
			at.accept(call + BLOWS + k * BLOW_GAP, () -> {
				if (f.boss != null && f.boss.isAlive()) {
					elder.force().test(f.boss, blow);
				}
			});
		}
		at.accept(call + BLOWS + elder.blows().length * BLOW_GAP, () -> {
			if (f.boss != null && f.boss.isAlive()) {
				elder.force().test(f.boss, ElderBoss.ULTIMATE);
			}
		});
		// Its fight ends
		at.accept(call + END, () -> {
			if (f.boss == null || !f.boss.isAlive()) {
				return;
			}
			if (elder.slain()) {
				f.boss.kill(level);
			} else {
				elder.fade().accept(f.boss);
			}
		});
		// Once its arena and its broken ground are gone (and a moment more), every block round it is looked at
		for (int t = call + END + 10; t <= call + END + WAIT; t += 10) {
			int now = t;
			at.accept(t, () -> {
				if (f.judged || f.before == null) {
					return;
				}
				boolean over = ElderArena.count() <= f.arenas && ElderUpheaval.count() <= f.upheavals && (f.boss == null || !f.boss.isAlive());
				if (over && f.quiet < 0) {
					f.quiet = now;
				}
				if (!over || now - f.quiet < 30) {
					if (now >= call + END + WAIT) {
						f.judged = true;
						int arenas = ElderArena.count(), upheavals = ElderUpheaval.count();
						f.restore.run();
						throw helper.assertionException("the %s's fight was not over after %s ticks (%s arenas, %s upheavals)", elder.name(), WAIT, arenas, upheavals);
					}
					return;
				}
				f.judged = true;
				judge(helper, level, elder, f, round);
			});
		}
	}

	/** Every block round a fight that is over: none of the fight's own left, none held, nothing it could drop lying there. */
	private static void judge(GameTestHelper helper, ServerLevel level, Elder elder, Fight f, AABB round) {
		FightBlocks.settle(level);
		int wrong = 0, held = 0, i = 0;
		List<String> told = new ArrayList<>();
		for (int x = -REACH; x <= REACH; x++) {
			for (int z = -REACH; z <= REACH; z++) {
				for (int y = -UNDER; y <= OVER; y++, i++) {
					BlockPos q = f.at.offset(x, y, z);
					BlockState now = level.getBlockState(q), was = f.before[i];
					held += FightBlocks.holds(level, q) ? 1 : 0;
					// As it was, or broken out (what a fight destroys may stay so), or its own block in another shape, or
					// grass died back to dirt, or snow fallen on it
					if (now == was || now.isAir() || now.is(was.getBlock()) || was.is(Blocks.GRASS_BLOCK) && now.is(Blocks.DIRT) || id(now).equals("snow")) {
						continue;
					}
					wrong++;
					if (told.size() < 6) {
						told.add(String.format(Locale.ROOT, "(%d, %d, %d): %s, was %s", x, y, z, id(now), id(was)));
					}
				}
			}
		}
		int falling = level.getEntitiesOfClass(FallingBlockEntity.class, round).size();
		List<String> strays = new ArrayList<>();
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, round)) {
			if (stray(e)) {
				strays.add(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath());
			}
		}
		int standing = ElderArena.standing();
		f.restore.run();
		check(helper, wrong == 0, "the %s's fight left %s blocks of its own behind: %s", elder.name(), wrong, String.join("; ", told));
		check(helper, held == 0, "%s blocks round the %s's fight are still held for it", held, elder.name());
		check(helper, standing == 0, "%s blocks of the %s's arena still stand somewhere", standing, elder.name());
		check(helper, falling == 0, "%s blocks of the %s's fight are still falling, to land as blocks", falling, elder.name());
		check(helper, strays.isEmpty(), "the %s's fight left %s lying about: %s", elder.name(), strays.size(), String.join(", ", strays));
		helper.succeed();
	}
}
