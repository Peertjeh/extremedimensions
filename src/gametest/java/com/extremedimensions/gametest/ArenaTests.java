package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderArena;
import com.extremedimensions.elder.ElderDomain;
import com.extremedimensions.elder.Weaver;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The arena fights with its Elder: its blocks break like the world (a blast too) and what hangs loose comes down as
 * rubble; its cores shield the Elder until they are broken; its domain changes as the Elder weakens; and its islands
 * come down on whoever is under them. Each test raises a small arena of its own, for a Weaver that stands still (no
 * service drives it, nor does it fade), and takes it down again before it judges.
 */
public class ArenaTests {
	private static final int BUILT = 1, ISLE = 2, CORE = 3;

	/** A Weaver standing still at a spot of the test, for an arena of its own. */
	private static Weaver weaver(GameTestHelper helper, int x, int y, int z) {
		Weaver w = ModEntities.WEAVER.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)), EntitySpawnReason.COMMAND);
		check(helper, w != null, "no Weaver to fight on the arena");
		w.setNoAi(true);
		return w;
	}

	private static List<BlockPos> blocks(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos q : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(x0, y0, z0)), helper.absolutePos(new BlockPos(x1, y1, z1)))) {
			out.add(q.immutable());
		}
		return out;
	}

	/** Solid ground of the world under a stretch of the test. */
	private static void ground(GameTestHelper helper, int x0, int z0, int x1, int z1) {
		for (BlockPos q : blocks(helper, x0, 0, z0, x1, 0, z1)) {
			helper.getLevel().setBlock(q, Blocks.STONE.defaultBlockState(), 3);
		}
	}

	private static void done(Weaver w) {
		ElderArena.unrig(w);
		w.discard();
	}

	/** A post broken through near its foot: what it held up falls as rubble that lies as the arena's, and goes with it. */
	@GameTest(maxTicks = 100)
	public void aBrokenArenaComesDownWhereItLostItsSupport(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ground(helper, 2, 2, 5, 2);
		Weaver w = weaver(helper, 3, 1, 6);
		List<BlockPos> post = blocks(helper, 2, 1, 2, 2, 4, 2);
		post.addAll(blocks(helper, 3, 4, 2, 5, 4, 2));
		ElderArena.rig(w, helper.absolutePos(new BlockPos(3, 1, 3)), 4, List.of(post), new int[] {BUILT}, Blocks.STONE_BRICKS.defaultBlockState());
		BlockPos foot = helper.absolutePos(new BlockPos(2, 1, 2)), cut = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos beam = helper.absolutePos(new BlockPos(4, 4, 2)), under = helper.absolutePos(new BlockPos(4, 1, 2));
		AABB round = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
		boolean[] seen = {false, false};
		helper.runAfterDelay(1, () -> ElderArena.broken(level, cut));
		helper.runAfterDelay(12, () -> {
			seen[0] = level.getBlockState(beam).isAir() && level.getBlockState(foot).is(Blocks.STONE_BRICKS);
			seen[1] = !level.getEntitiesOfClass(FallingBlockEntity.class, round).isEmpty() || level.getBlockState(under).is(Blocks.STONE_BRICKS);
		});
		helper.runAfterDelay(70, () -> {
			boolean lies = level.getBlockState(under).is(Blocks.STONE_BRICKS) && ElderArena.holds(level, under);
			int falling = level.getEntitiesOfClass(FallingBlockEntity.class, round).size();
			// (only its own: a blast in a test beside it may scatter loot of the world)
			int items = level.getEntitiesOfClass(ItemEntity.class, round, i -> i.getItem().is(Blocks.STONE_BRICKS.asItem())).size();
			done(w);
			int left = 0;
			for (BlockPos q : blocks(helper, 2, 1, 2, 5, 4, 2)) {
				left += level.getBlockState(q).isAir() ? 0 : 1;
			}
			check(helper, seen[0], "the beam did not come down when its post was broken, or the foot of the post went with it");
			check(helper, seen[1], "nothing fell as rubble");
			check(helper, lies, "the rubble does not lie as a block of the arena");
			check(helper, falling == 0, "%s blocks of rubble are still falling", falling);
			check(helper, items == 0, "the rubble dropped %s items", items);
			check(helper, left == 0, "%s blocks of the arena and its rubble were left when it was taken down", left);
			helper.succeed();
		});
	}

	/** The rubble of its arena glances off an Elder: a falling island is what hurts it. */
	@GameTest(maxTicks = 60)
	public void rubbleGlancesOffAnElder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ground(helper, 3, 3, 3, 3);
		Weaver w = weaver(helper, 3, 1, 3);
		float health = w.getHealth();
		BlockPos lands = helper.absolutePos(new BlockPos(3, 1, 3));
		FallingBlockEntity rubble = FallingBlockEntity.fall(level, helper.absolutePos(new BlockPos(3, 7, 3)), Blocks.STONE_BRICKS.defaultBlockState());
		rubble.dropItem = false;
		rubble.setHurtsEntities(2.0F, 40);
		helper.runAfterDelay(50, () -> {
			boolean landed = rubble.isRemoved();
			float now = w.getHealth();
			level.setBlock(lands, Blocks.AIR.defaultBlockState(), 3);
			w.discard();
			check(helper, landed, "the rubble did not land");
			check(helper, now == health, "rubble falling on the Weaver took it from %s to %s health", health, now);
			helper.succeed();
		});
	}

	/** A blast breaks an arena's obsidian like stone (elder_arena_blast_resistance), unless the settings keep it whole. */
	@GameTest(maxTicks = 20)
	public void anArenaGivesWayToABlast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Weaver w = weaver(helper, 3, 1, 3);
		// High in the air, so the blasts break nothing of the world (nor scatter its loot into the tests round this one)
		BlockPos kept = helper.absolutePos(new BlockPos(0, 12, 0)), blown = helper.absolutePos(new BlockPos(7, 12, 7));
		ElderArena.rig(w, helper.absolutePos(new BlockPos(3, 1, 3)), 4, List.of(List.of(kept), List.of(blown)), new int[] {BUILT, BUILT},
				Blocks.OBSIDIAN.defaultBlockState());
		int breakable = Config.ELDER_ARENA_BREAKABLE;
		boolean whole, gone;
		try {
			Config.ELDER_ARENA_BREAKABLE = 0;
			level.explode((Entity) null, kept.getX() + 0.5, kept.getY() + 0.5, kept.getZ() + 1.5, 4.0F, false, Level.ExplosionInteraction.TNT);
			whole = level.getBlockState(kept).is(Blocks.OBSIDIAN);
			Config.ELDER_ARENA_BREAKABLE = 1;
			level.explode((Entity) null, blown.getX() + 0.5, blown.getY() + 0.5, blown.getZ() - 0.5, 4.0F, false, Level.ExplosionInteraction.TNT);
			gone = level.getBlockState(blown).isAir();
		} finally {
			Config.ELDER_ARENA_BREAKABLE = breakable;
			done(w);
		}
		check(helper, whole, "with elder_arena_breakable off a blast broke the arena's obsidian");
		check(helper, gone, "a blast did not break the arena's obsidian");
		helper.succeed();
	}

	/** Two cores shield their Elder until broken; three hits break one, halving the shield. */
	@GameTest(maxTicks = 60)
	public void theCoresShieldTheirElderUntilBroken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper);
		Weaver w = weaver(helper, 3, 1, 3);
		List<BlockPos> one = blocks(helper, 0, 1, 0, 1, 2, 1), two = blocks(helper, 5, 1, 5, 6, 2, 6), wall = blocks(helper, 3, 1, 0, 3, 1, 0);
		ElderArena.rig(w, helper.absolutePos(new BlockPos(3, 1, 3)), 4, List.of(one, two, wall), new int[] {CORE, CORE, BUILT},
				Blocks.STONE_BRICKS.defaultBlockState());
		float[] ward = {0, 0};
		boolean[] hits = {false, false, false};
		helper.runAfterDelay(12, () -> {
			ward[0] = ElderDomain.ward(w);
			hits[0] = ElderDomain.hit(p, level, one.get(0));
			// Neither a block of the arena that is no core, nor the world, takes a hit
			hits[1] = ElderDomain.hit(p, level, wall.get(0)) || ElderDomain.hit(p, level, helper.absolutePos(new BlockPos(7, 1, 7)));
		});
		helper.runAfterDelay(22, () -> ElderDomain.hit(p, level, one.get(3)));
		helper.runAfterDelay(32, () -> hits[2] = ElderDomain.hit(p, level, one.get(5)));
		helper.runAfterDelay(34, () -> {
			ward[1] = ElderDomain.ward(w);
			int[] cores = ElderDomain.cores(w);
			int lit = 0;
			for (BlockPos q : one) {
				lit += level.getBlockState(q).isAir() ? 0 : 1;
			}
			done(w);
			check(helper, Math.abs(ward[0] - Config.ELDER_CORE_WARD) < 1e-3, "two standing cores shield their Elder by %s, expected %s", ward[0],
					Config.ELDER_CORE_WARD);
			check(helper, hits[0] && hits[2], "a core did not take the hits");
			check(helper, !hits[1], "a block that is no core took a hit as one");
			check(helper, cores[0] == 2 && cores[1] == 1, "%s of %s cores stand after three hits on one, expected 1 of 2", cores[1], cores[0]);
			check(helper, lit == 0, "%s blocks of the broken core are still lit", lit);
			check(helper, Math.abs(ward[1] - Config.ELDER_CORE_WARD / 2) < 1e-3, "one core left shields by %s, expected %s", ward[1],
					Config.ELDER_CORE_WARD / 2);
			helper.succeed();
		});
	}

	/** The domain changes as its Elder weakens: once at 60% of its health, again at 30%. */
	@GameTest(maxTicks = 20)
	public void theDomainChangesAsItsElderWeakens(GameTestHelper helper) {
		Weaver w = weaver(helper, 3, 1, 3);
		ElderArena.rig(w, helper.absolutePos(new BlockPos(3, 1, 3)), 4, List.of(blocks(helper, 0, 1, 0, 0, 1, 0)), new int[] {BUILT},
				Blocks.STONE_BRICKS.defaultBlockState());
		int[] phase = {-1, -1, -1};
		helper.runAfterDelay(2, () -> {
			phase[0] = ElderDomain.phase(w);
			w.setHealth(w.getMaxHealth() * 0.55F);
		});
		helper.runAfterDelay(5, () -> {
			phase[1] = ElderDomain.phase(w);
			w.setHealth(w.getMaxHealth() * 0.25F);
		});
		helper.runAfterDelay(8, () -> {
			phase[2] = ElderDomain.phase(w);
			done(w);
			check(helper, phase[0] == 0 && phase[1] == 1 && phase[2] == 2, "the domain went through phases %s, %s, %s; expected 0, 1, 2", phase[0], phase[1],
					phase[2]);
			helper.succeed();
		});
	}

	/** An island over the Elder comes loose, its shadow first; it comes down and the Elder under it loses a tenth of its health. */
	@GameTest(maxTicks = 120)
	public void anIslandComesDownOnTheElderUnderIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ground(helper, 1, 1, 5, 5);
		Weaver w = weaver(helper, 3, 1, 3);
		List<BlockPos> isle = blocks(helper, 2, 10, 2, 4, 10, 4);
		ElderArena.rig(w, helper.absolutePos(new BlockPos(3, 1, 3)), 4, List.of(isle), new int[] {ISLE}, Blocks.BLACKSTONE.defaultBlockState());
		double warn = Config.ELDER_ISLE_WARN_SECONDS;
		Config.ELDER_ISLE_WARN_SECONDS = 0.5;
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
		float health = w.getHealth();
		boolean loose = ElderDomain.loosenOver(w, at.getX() + 0.5, at.getY(), at.getZ() + 0.5) != null;
		boolean[] waited = {false};
		// Its shadow gathers first: it still hangs a few ticks on
		helper.runAfterDelay(5, () -> waited[0] = level.getBlockState(isle.get(4)).is(Blocks.BLACKSTONE) && ElderDomain.falling(w));
		helper.runAfterDelay(90, () -> {
			Config.ELDER_ISLE_WARN_SECONDS = warn;
			boolean down = !ElderDomain.falling(w);
			int hangs = 0;
			for (BlockPos q : isle) {
				hangs += level.getBlockState(q).isAir() ? 0 : 1;
			}
			float now = w.getHealth();
			done(w);
			check(helper, loose, "no island came loose over the Weaver");
			check(helper, waited[0], "the island came down at once, without its shadow gathering first");
			check(helper, down, "the island has not come down after four seconds");
			check(helper, hangs == 0, "%s blocks of the island still hang", hangs);
			float expect = health - (float) (Config.ELDER_ISLE_BOSS_SHARE * w.getMaxHealth());
			check(helper, Math.abs(now - expect) < 1, "the Weaver under the island has %s health, expected %s", now, expect);
			helper.succeed();
		});
	}

	/**
	 * An arena's design drawn up a step at a time, as a great one is over the ticks its Elder takes to form, comes out the
	 * same as one drawn up whole from the same seed, for each Elder's style (at size 3: with its wall, great piece and
	 * cores).
	 */
	@GameTest
	public void anArenaDrawnUpInStepsIsTheSameArena(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double size = Config.ELDER_ARENA_SIZE;
		Config.ELDER_ARENA_SIZE = 3.0;
		try {
			BlockPos centre = helper.absolutePos(new BlockPos(2, 2, 2));
			for (int style = 0; style < 3; style++) {
				String diff = ElderArena.draftDiffers(level, style, centre, 1234L + style);
				check(helper, diff == null, "style %s drawn up in steps: %s", style, diff);
			}
		} finally {
			Config.ELDER_ARENA_SIZE = size;
		}
		helper.succeed();
	}
}
