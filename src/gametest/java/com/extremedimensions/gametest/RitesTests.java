package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.reach.AnchorRites;
import com.extremedimensions.reach.PaleAnchorDesign;
import com.extremedimensions.reach.PaleReach;

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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The rites of an anchor (3.44). Finished, an Unfinished Anchor stands whole, its halo a full ring, and a door opens
 * in it that an Enderman goes home through; then it closes and nothing of it is left. Locked, its halo, arches and
 * pillar tops break away and fall into the void, all gone within seconds, while its floor, plinth and barrels stand and
 * its fitting is a lodestone no more. Each builds an anchor of its own high over its test and runs alone; neither reads
 * or sets the End's ending.
 */
public class RitesTests {
	/** Takes the anchor down again (as built, as finished) and forgets its marks. */
	private static void takeDown(ServerLevel level, BlockPos o, PaleAnchorDesign.Blueprint... bps) {
		for (PaleAnchorDesign.Blueprint bp : bps) {
			for (Map.Entry<Long, BlockState> e : bp.ordered(o)) {
				level.setBlock(BlockPos.of(e.getKey()), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		BlockPos f = o.above();
		DataStore.reach().removeIf(m -> m.endsWith("|" + f.getX() + "," + f.getY() + "," + f.getZ()));
		DataStore.markDirty();
	}

	/** Whether the world keeps a mark of this kind for the fitting. */
	private static boolean marked(String kind, BlockPos f) {
		String tail = "|" + f.getX() + "," + f.getY() + "," + f.getZ();
		return DataStore.reach().stream().anyMatch(m -> m.startsWith(kind) && m.endsWith(tail));
	}

	private static int displaysNear(ServerLevel level, BlockPos at, double r) {
		return level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(at).inflate(r)).size();
	}

	private static Mob enderman(ServerLevel level, BlockPos at) {
		Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "enderman")).spawn(level, at, EntitySpawnReason.COMMAND);
		return e instanceof Mob m ? m : null;
	}

	/**
	 * Home: the anchor is finished (every block of the whole design stands, the halo closed) before its door opens; an
	 * Enderman by the fitting goes home through the door while it stands open; then it closes, leaving no display, and
	 * the anchor is kept as finished.
	 */
	@GameTest(environment = Alone.RITES_HOME, maxTicks = 1000)
	public void aFinishedAnchorOpensADoorAndTheyGoHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(2, 40, 2));
		long seed = 23L;
		PaleAnchorDesign.Blueprint bp = PaleReach.buildNow(level, o, seed), whole = PaleAnchorDesign.whole(o, seed);
		// someone on the floor to see it finished (so its parts fly in), facing away from where the Enderman will stand
		ServerPlayer p = TestPlayers.survivor(helper, "home-watcher");
		p.snapTo(o.getX() - 3.5, o.getY() + 1, o.getZ() + 0.5, 90f, 0f);
		int seconds = Config.PALE_DOOR_SECONDS;
		Difficulty was = level.getDifficulty();
		Mob[] walker = {null};
		Runnable restore = () -> {
			AnchorRites.clear();
			Config.PALE_DOOR_SECONDS = seconds;
			level.getServer().setDifficulty(was, true);
			if (walker[0] != null && !walker[0].isRemoved()) {
				walker[0].discard();
			}
			takeDown(level, o, bp, whole);
		};
		int[] open = {-1}, gone = {-1}, shut = {-1};
		boolean[] done = {false};
		try {
			AnchorRites.clear();
			int gap = 0;
			for (BlockPos q : whole.ring()) {
				gap += level.getBlockState(q).isAir() ? 1 : 0;
			}
			check(helper, gap > 0, "the unfinished halo had no gap to close");
			check(helper, whole.size() > bp.size(), "the finished anchor lays %s blocks, the unfinished %s", whole.size(), bp.size());
			Config.PALE_DOOR_SECONDS = 6;
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			AnchorRites.home(level, bp.fitting);
			check(helper, marked("ph@", bp.fitting), "the anchor is not kept as finished");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		for (int t = 1; t <= 990; t++) {
			int now = t;
			helper.runAfterDelay(t, () -> {
				if (done[0]) {
					return;
				}
				try {
					boolean standing = AnchorRites.doorOpen(level, bp.fitting);
					if (standing && open[0] < 0) {
						open[0] = now;
						int missing = 0;
						for (Map.Entry<Long, BlockState> e : whole.ordered(o)) {
							if (!e.getValue().hasBlockEntity() && level.getBlockState(BlockPos.of(e.getKey())).isAir()) {
								missing++;
							}
						}
						check(helper, missing == 0, "%s blocks of the finished anchor are missing as its door opens", missing);
						int holes = 0;
						for (BlockPos q : whole.ring()) {
							holes += level.getBlockState(q).isAir() ? 1 : 0;
						}
						check(helper, holes == 0, "the halo is still open at %s blocks as its door opens", holes);
						// an Enderman by the fitting, angry at no one
						walker[0] = enderman(level, o.offset(3, 1, 0));
						check(helper, walker[0] != null, "no Enderman could be made");
						walker[0].setPersistenceRequired();
					}
					if (open[0] >= 0 && gone[0] < 0 && AnchorRites.homeward() > 0) {
						gone[0] = now;
						check(helper, standing, "an Enderman went home with the door not open");
						check(helper, walker[0].isRemoved(), "someone went home, but not the Enderman by the fitting");
					}
					if (open[0] >= 0 && !standing && shut[0] < 0) {
						shut[0] = now;
					}
					if (shut[0] >= 0 && now - shut[0] >= 40) {
						done[0] = true;
						check(helper, gone[0] > 0, "no Enderman went home through the door while it stood open (%s ticks)", shut[0] - open[0]);
						int left = displaysNear(level, whole.halo, 12);
						check(helper, left == 0, "%s displays of the door are left after it closed", left);
						check(helper, level.getBlockState(bp.fitting).is(Blocks.LODESTONE), "the finished anchor's fitting is gone");
						AnchorRites.home(level, bp.fitting);
						check(helper, !AnchorRites.doorOpen(level, bp.fitting), "the door opened again");
						restore.run();
						helper.succeed();
					} else if (now == 990) {
						done[0] = true;
						throw helper.assertionException("the door did not open and close (opened %s, Enderman gone %s, closed %s)", open[0], gone[0], shut[0]);
					}
				} catch (RuntimeException e) {
					done[0] = true;
					restore.run();
					throw e;
				}
			});
		}
	}

	/**
	 * Lock: blocks break away and fall, all gone within seconds; the floor, the plinth and the barrels stand, the halo is
	 * gone, the fitting is no lodestone and no longer a fitting, and the anchor is kept as locked.
	 */
	@GameTest(environment = Alone.RITES_LOCK, maxTicks = 400)
	public void aLockedAnchorBreaksAndFallsAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(2, 40, 2));
		long seed = 31L;
		PaleAnchorDesign.Blueprint bp = PaleReach.buildNow(level, o, seed), whole = PaleAnchorDesign.whole(o, seed);
		Runnable restore = () -> {
			AnchorRites.clear();
			takeDown(level, o, bp, whole);
		};
		// someone on the floor to see it go (what no one could see is simply gone)
		ServerPlayer p = TestPlayers.survivor(helper, "lock-watcher");
		p.teleportTo(o.getX() + 3.5, o.getY() + 1, o.getZ() + 0.5);
		int[] fell = {-1}, most = {0};
		boolean[] done = {false};
		try {
			AnchorRites.clear();
			check(helper, PaleReach.isFitting(level, bp.fitting), "the anchor's fitting is not known");
			AnchorRites.lock(level, bp.fitting);
			check(helper, marked("pl@", bp.fitting), "the anchor is not kept as locked");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		for (int t = 1; t <= 390; t++) {
			int now = t;
			helper.runAfterDelay(t, () -> {
				if (done[0]) {
					return;
				}
				try {
					int falling = AnchorRites.falling();
					most[0] = Math.max(most[0], falling);
					if (falling > 0 && fell[0] < 0) {
						fell[0] = now;
					}
					if (now == 40 && fell[0] < 0) {
						done[0] = true;
						throw helper.assertionException("nothing of the locked anchor fell in 40 ticks");
					}
					if (fell[0] >= 0 && now >= 60 && falling == 0) {
						done[0] = true;
						check(helper, now <= 240, "the locked anchor took %s ticks to fall away", now);
						int floor = 0;
						for (Map.Entry<Long, BlockState> e : bp.ordered(o)) {
							BlockPos at = BlockPos.of(e.getKey());
							if (at.getY() <= o.getY() && level.getBlockState(at).isAir()) {
								floor++;
							}
						}
						check(helper, floor == 0, "%s blocks of the floor and the plinth are gone", floor);
						for (BlockPos c : bp.caches) {
							check(helper, level.getBlockState(c).is(Blocks.BARREL), "the barrel at %s is gone", c);
						}
						int ring = 0;
						for (BlockPos q : bp.ring()) {
							ring += level.getBlockState(q).isAir() ? 0 : 1;
						}
						check(helper, ring <= 3, "%s blocks of the halo still stand", ring);
						check(helper, !level.getBlockState(bp.fitting).is(Blocks.LODESTONE) && !PaleReach.isFitting(level, bp.fitting),
								"the fitting is still a lodestone, or still a fitting");
						int left = displaysNear(level, o, 48);
						check(helper, left == 0, "%s displays of falling blocks are left", left);
						check(helper, most[0] >= 20, "only %s blocks were seen falling at once", most[0]);
						restore.run();
						helper.succeed();
					} else if (now == 390) {
						done[0] = true;
						throw helper.assertionException("the locked anchor never finished falling (%s still falling)", falling);
					}
				} catch (RuntimeException e) {
					done[0] = true;
					restore.run();
					throw e;
				}
			});
		}
	}
}
