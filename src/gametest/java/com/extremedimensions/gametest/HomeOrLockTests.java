package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.reach.AnchorRites;
import com.extremedimensions.reach.HomeOrLock;
import com.extremedimensions.reach.PaleAnchorDesign;
import com.extremedimensions.reach.PaleReach;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Home or Lock (3.44): the Pale Mason leaves the Homeward Keystone and the Pale Lock while the End has no ending; set in
 * an attuned fitting, the first of the two decides the End (the other only crumbles after), spilling the Endermen's
 * thanks or their hoard; and the ending calms the Endermen or turns them. The End's ending is the world's, so each of
 * these runs alone and leaves the world as it found it.
 */
public class HomeOrLockTests {
	/** An anchor high over the test, its fitting attuned. */
	private static PaleAnchorDesign.Blueprint anchor(GameTestHelper helper, long seed) {
		ServerLevel level = helper.getLevel();
		PaleAnchorDesign.Blueprint bp = PaleReach.buildNow(level, helper.absolutePos(new BlockPos(2, 40, 2)), seed);
		PaleReach.attune(level, bp.fitting, null);
		return bp;
	}

	private static int itemsNear(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(12)).size();
	}

	/**
	 * The Keystone set: not before the fitting is woken, then the End goes home, their thanks spill out, the Mason leaves
	 * nothing more, and the Lock only crumbles after.
	 */
	@GameTest(environment = Alone.CHOICE_HOME, maxTicks = 40)
	public void theKeystoneSendsThemHome(GameTestHelper helper) {
		String was = HomeOrLock.choice();
		HomeOrLock.set("");
		try {
			check(helper, HomeOrLock.spoils().size() == 2, "the Mason leaves %s things while the End has no ending", HomeOrLock.spoils().size());
			ServerLevel level = helper.getLevel();
			PaleAnchorDesign.Blueprint bp = PaleReach.buildNow(level, helper.absolutePos(new BlockPos(2, 40, 2)), 7L);
			ServerPlayer p = TestPlayers.survivor(helper, "home-setter");
			ItemStack stone = new ItemStack(ModItems.HOMEWARD_KEYSTONE);
			HomeOrLock.onUse(p, level, helper.absolutePos(new BlockPos(1, 1, 1)), stone);
			check(helper, HomeOrLock.choice().isEmpty() && stone.getCount() == 1, "the Keystone did something away from a fitting");
			HomeOrLock.onUse(p, level, bp.fitting, stone);
			check(helper, HomeOrLock.choice().isEmpty() && stone.getCount() == 1, "the Keystone was set in a fitting not yet attuned");
			PaleReach.attune(level, bp.fitting, null);
			int before = itemsNear(level, bp.fitting);
			HomeOrLock.onUse(p, level, bp.fitting, stone);
			check(helper, HomeOrLock.home(), "the End's ending is '%s', not home", HomeOrLock.choice());
			check(helper, stone.isEmpty(), "the Keystone was not used up");
			check(helper, itemsNear(level, bp.fitting) > before, "no thanks spilled out of the fitting");
			check(helper, HomeOrLock.spoils().isEmpty(), "the Mason still leaves the two once the End has its ending");
			check(helper, HomeOrLock.weather() == Config.PALE_HOME_QUIET, "the Reach is not quieter once they are home");
			ItemStack lock = new ItemStack(ModItems.PALE_LOCK);
			HomeOrLock.onUse(p, level, bp.fitting, lock);
			check(helper, HomeOrLock.home() && lock.isEmpty(), "the Lock did not crumble, or changed the ending to '%s'", HomeOrLock.choice());
		} finally {
			AnchorRites.clear();
			HomeOrLock.set(was);
		}
		helper.succeed();
	}

	/** The Lock set: the hoard spills out, and the Reach grows wilder. */
	@GameTest(environment = Alone.CHOICE_LOCK, maxTicks = 40)
	public void theLockSpillsTheirHoard(GameTestHelper helper) {
		String was = HomeOrLock.choice();
		HomeOrLock.set("");
		try {
			ServerLevel level = helper.getLevel();
			PaleAnchorDesign.Blueprint bp = anchor(helper, 11L);
			ServerPlayer p = TestPlayers.survivor(helper, "lock-setter");
			int before = itemsNear(level, bp.fitting);
			HomeOrLock.onUse(p, level, bp.fitting, new ItemStack(ModItems.PALE_LOCK));
			check(helper, HomeOrLock.locked(), "the End's ending is '%s', not locked", HomeOrLock.choice());
			check(helper, itemsNear(level, bp.fitting) - before >= 5, "only %s things of their hoard spilled out", itemsNear(level, bp.fitting) - before);
			check(helper, HomeOrLock.weather() >= 1, "the Reach is not wilder once locked");
		} finally {
			AnchorRites.clear();
			HomeOrLock.set(was);
		}
		helper.succeed();
	}

	/** Gone home, an Enderman lets a gaze pass but not a blow; locked out, one turns on a player unprovoked. */
	@GameTest(environment = Alone.CHOICE_ENDERMEN, maxTicks = 20)
	public void theEndingCalmsOrTurnsTheEndermen(GameTestHelper helper) {
		String was = HomeOrLock.choice();
		double chance = Config.PALE_LOCK_HOSTILE_CHANCE;
		int calm = Config.PALE_HOME_CALM;
		try {
			ServerPlayer p = TestPlayers.survivor(helper, "ender-watcher");
			BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
			p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			Mob stared = (Mob) helper.spawn(EntityType.ENDERMAN, new BlockPos(4, 2, 4));
			Mob struck = (Mob) helper.spawn(EntityType.ENDERMAN, new BlockPos(5, 2, 1));
			Config.PALE_HOME_CALM = 1;
			HomeOrLock.set(HomeOrLock.HOME);
			stared.setTarget(p);
			struck.setLastHurtByMob(p);
			struck.setTarget(p);
			HomeOrLock.calm(p);
			check(helper, stared.getTarget() == null, "an Enderman gone home still fights over a gaze");
			check(helper, struck.getTarget() == p, "an Enderman gone home forgave a blow");
			stared.discard();
			struck.discard();
			Mob near = (Mob) helper.spawn(EntityType.ENDERMAN, new BlockPos(3, 2, 3));
			near.setTarget(null);
			Config.PALE_LOCK_HOSTILE_CHANCE = 1;
			HomeOrLock.set(HomeOrLock.LOCK);
			HomeOrLock.turn(p);
			check(helper, near.getTarget() == p, "a locked-out Enderman near the player did not turn on them");
			near.discard();
		} finally {
			Config.PALE_LOCK_HOSTILE_CHANCE = chance;
			Config.PALE_HOME_CALM = calm;
			HomeOrLock.set(was);
		}
		helper.succeed();
	}
}
