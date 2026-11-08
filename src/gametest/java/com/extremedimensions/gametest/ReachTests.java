package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.events.PaleSquall;
import com.extremedimensions.events.WorldEvents;
import com.extremedimensions.lurker.PaleHauler;
import com.extremedimensions.lurker.PaleHaulerService;
import com.extremedimensions.reach.PaleAnchorDesign;
import com.extremedimensions.reach.PaleReach;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 3.39: the Pale Reach. Its squares hold anchors only far out; an Unfinished Anchor has its fitting, posts, halo and
 * barrels, is built only into open air, and its fitting, attuned with an Eye of Ender, eases the drain near it. A Pale
 * Hauler pulls a block laid out over the void, never the one under someone's feet. A Pale Squall pushes less on a
 * sneaking or Anchored player, and not at all on one braced against a wall.
 */
public class ReachTests {
	/** Anchors hang only far out, in about the share of squares the setting asks, the same ones every time. */
	@GameTest
	public void theReachLiesFarOutAndItsSquaresHoldAnchors(GameTestHelper helper) {
		check(helper, !PaleReach.far(0, 0) && !PaleReach.far(Config.PALE_REACH_FROM - 1, 0) && PaleReach.far(Config.PALE_REACH_FROM + 1, 0),
				"the Reach does not begin at %s blocks", Config.PALE_REACH_FROM);
		long world = 12345L;
		int sp = Math.max(128, Config.PALE_ANCHOR_SPACING);
		for (int cx = -1; cx <= 0; cx++) {
			for (int cz = -1; cz <= 0; cz++) {
				check(helper, PaleReach.site(world, cx, cz) == null, "an anchor hangs in the square %s,%s by the End's middle", cx, cz);
			}
		}
		int with = 0, all = 0;
		for (int cx = 10; cx < 30; cx++) {
			for (int cz = 10; cz < 30; cz++) {
				BlockPos a = PaleReach.site(world, cx, cz), b = PaleReach.site(world, cx, cz);
				check(helper, a == null ? b == null : a.equals(b), "the square %s,%s gave two different anchors", cx, cz);
				all++;
				if (a != null) {
					with++;
					check(helper, a.getX() >= cx * sp && a.getX() < (cx + 1) * sp && a.getZ() >= cz * sp && a.getZ() < (cz + 1) * sp,
							"the anchor of %s,%s hangs outside its square (%s)", cx, cz, a);
					check(helper, PaleReach.far(a.getX(), a.getZ()), "an anchor hangs short of the Reach (%s)", a);
				}
			}
		}
		double share = with / (double) all;
		check(helper, Math.abs(share - Config.PALE_ANCHOR_CHANCE) < 0.12, "%s of the squares hold an anchor, not about %s", share, Config.PALE_ANCHOR_CHANCE);
		helper.succeed();
	}

	/** The design: a lodestone fitting between golden posts, barrels to fill, a plinth under the floor, the same every time. */
	@GameTest
	public void anUnfinishedAnchorHasItsParts(GameTestHelper helper) {
		BlockPos o = new BlockPos(5000, 70, 5000);
		PaleAnchorDesign.Blueprint bp = PaleAnchorDesign.design(o, 77L), again = PaleAnchorDesign.design(o, 77L);
		check(helper, bp.fitting.equals(o.above()) && bp.at(bp.fitting) != null && bp.at(bp.fitting).is(Blocks.LODESTONE), "the fitting is not a lodestone over the floor's middle");
		check(helper, bp.count(Blocks.LODESTONE) == 1, "%s fittings", bp.count(Blocks.LODESTONE));
		check(helper, bp.count(Blocks.GOLD_BLOCK) >= 4, "the posts and the halo's spokes are missing (%s gold)", bp.count(Blocks.GOLD_BLOCK));
		check(helper, bp.caches.size() >= 2 && bp.caches.stream().allMatch(c -> bp.at(c) != null && bp.at(c).is(Blocks.BARREL)), "the barrels are missing");
		check(helper, bp.at(o.below(3)) != null && bp.at(o.below(3)).is(Blocks.END_STONE) || bp.at(o.below(3)) != null && bp.at(o.below(3)).is(Blocks.OBSIDIAN),
				"nothing hangs under the floor");
		check(helper, bp.size() > 1500 && bp.size() < 8000, "the anchor has %s blocks", bp.size());
		check(helper, bp.size() == again.size() && bp.caches.equals(again.caches), "the same anchor came out differently");
		helper.succeed();
	}

	/**
	 * Built at once it fills only open air (a block in its way stays), its barrels are stocked and its fitting is known;
	 * attuned with an Eye of Ender, the fitting eases the drain near it, and only once.
	 */
	@GameTest(environment = Alone.REACH_ANCHOR, maxTicks = 40)
	public void anAnchorIsBuiltIntoOpenAirAndItsFittingEasesTheDrain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(2, 40, 2));
		BlockPos inWay = o.offset(4, 0, 0);
		level.setBlock(inWay, Blocks.GLASS.defaultBlockState(), 2);
		PaleAnchorDesign.Blueprint bp = PaleReach.buildNow(level, o, 99L);
		ServerPlayer p = TestPlayers.survivor(helper, "reach-attuner");
		int cost = Config.PALE_FITTING_COST;
		double ease = Config.PALE_FITTING_DRAIN;
		Runnable restore = () -> {
			Config.PALE_FITTING_COST = cost;
			Config.PALE_FITTING_DRAIN = ease;
			DataStore.reach().removeIf(m -> m.endsWith("|" + bp.fitting.getX() + "," + bp.fitting.getY() + "," + bp.fitting.getZ()));
			for (Map.Entry<Long, BlockState> e : bp.ordered(o)) {
				level.setBlock(BlockPos.of(e.getKey()), Blocks.AIR.defaultBlockState(), 2);
			}
			level.setBlock(inWay, Blocks.AIR.defaultBlockState(), 2);
		};
		try {
			check(helper, level.getBlockState(inWay).is(Blocks.GLASS), "the anchor was built over a block in its way");
			check(helper, level.getBlockState(bp.fitting).is(Blocks.LODESTONE) && PaleReach.isFitting(level, bp.fitting), "the fitting stands, but is not known");
			BlockPos cache = bp.caches.get(0);
			check(helper, level.getBlockEntity(cache) instanceof Container c && !c.isEmpty(), "the barrel at %s was not stocked", cache);
			Config.PALE_FITTING_COST = 1;
			Config.PALE_FITTING_DRAIN = 0.5;
			p.teleportTo(bp.fitting.getX() + 0.5, bp.fitting.getY() + 1, bp.fitting.getZ() + 2.5);
			check(helper, PaleReach.drainFactor(p) == 1.0, "the fitting eases the drain before it is attuned");
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			PaleReach.onUse(p, level, bp.fitting, p.getMainHandItem());
			check(helper, !PaleReach.isAttuned(level, bp.fitting), "the fitting was attuned without an Eye of Ender");
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE, 2));
			PaleReach.onUse(p, level, bp.fitting, p.getMainHandItem());
			check(helper, PaleReach.isAttuned(level, bp.fitting) && p.getMainHandItem().getCount() == 1, "attuning took %s eyes, or did not attune",
					2 - p.getMainHandItem().getCount());
			check(helper, PaleReach.drainFactor(p) == 0.5, "near the attuned fitting the drain is %s, not half", PaleReach.drainFactor(p));
			PaleReach.onUse(p, level, bp.fitting, p.getMainHandItem());
			check(helper, p.getMainHandItem().getCount() == 1, "an attuned fitting took another eye");
			p.teleportTo(bp.fitting.getX() + 200.5, bp.fitting.getY(), bp.fitting.getZ() + 0.5);
			check(helper, PaleReach.drainFactor(p) == 1.0, "far from the fitting the drain is still eased");
		} finally {
			restore.run();
		}
		helper.succeed();
	}

	/**
	 * A Pale Hauler, looking for work near its prey, pulls a block laid out over the void (after its grip shakes it) and
	 * carries it off; the laid block its prey stands on, it leaves alone.
	 */
	@GameTest(environment = Alone.REACH_HAULER, maxTicks = 120)
	public void aPaleHaulerPullsALaidBlockButNeverTheOneUnderfoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int y = 6;
		// A bridge three wide out over open air, a block laid at each end, the player on the near one
		for (int x = 1; x <= 8; x++) {
			for (int z = 1; z <= 3; z++) {
				boolean end = x == 1 || x == 8;
				if (!end || z == 2) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
				}
				for (int below = 1; below <= 4; below++) {
					helper.setBlock(new BlockPos(x, y - below, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		BlockPos near = helper.absolutePos(new BlockPos(1, y, 2)), far = helper.absolutePos(new BlockPos(8, y, 2));
		PaleHaulerService.markLaid(near);
		PaleHaulerService.markLaid(far);
		ServerPlayer p = TestPlayers.survivor(helper, "reach-bridger");
		p.teleportTo(near.getX() + 0.5, near.getY() + 1, near.getZ() + 0.5);
		PaleHauler h = ModEntities.PALE_HAULER.spawn(level, helper.absolutePos(new BlockPos(5, y + 1, 2)), EntitySpawnReason.EVENT);
		check(helper, h != null, "no Hauler could be made");
		double pull = Config.PALE_HAULER_PULL_SECONDS;
		Config.PALE_HAULER_PULL_SECONDS = 0.25;
		PaleHaulerService.adopt(level, h, p);
		PaleHaulerService.seekNow(h);
		helper.runAfterDelay(80, () -> {
			boolean farGone = level.getBlockState(far).isAir(), nearStays = level.getBlockState(near).is(Blocks.STONE);
			int hauled = PaleHaulerService.hauled(h);
			Config.PALE_HAULER_PULL_SECONDS = pull;
			PaleHaulerService.vanish(h);
			check(helper, nearStays, "the Hauler pulled the block from under the player's feet");
			check(helper, farGone && hauled >= 1, "the Hauler did not haul the laid block (gone %s, carries %s)", farGone, hauled);
			helper.succeed();
		});
	}

	/** A squall pushes; sneaking halves it, Anchored lessens it, braced against a wall it does nothing. */
	@GameTest
	public void aSquallPushesLessOnTheSneakingTheAnchoredAndTheBraced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "reach-squalled");
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		double push = Config.PALE_SQUALL_PUSH, anchoredEase = Config.PALE_SQUALL_ANCHORED;
		try {
			Config.PALE_SQUALL_PUSH = 0.06;
			Config.PALE_SQUALL_ANCHORED = 0.25;
			double open = PaleSquall.push(p, 1, 0);
			check(helper, Math.abs(open - 0.06) < 1e-9, "the squall pushes %s in the open", open);
			p.setShiftKeyDown(true);
			check(helper, Math.abs(PaleSquall.push(p, 1, 0) - 0.03) < 1e-9, "sneaking does not halve the push (%s)", PaleSquall.push(p, 1, 0));
			p.setShiftKeyDown(false);
			ItemStack legs = new ItemStack(Items.IRON_LEGGINGS);
			legs.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.ANCHORED), 2);
			p.setItemSlot(EquipmentSlot.LEGS, legs);
			check(helper, Math.abs(PaleSquall.push(p, 1, 0) - 0.03) < 1e-9, "Anchored II does not take half the push off (%s)", PaleSquall.push(p, 1, 0));
			p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE.defaultBlockState());
			check(helper, PaleSquall.push(p, 1, 0) == 0, "braced against a wall, the squall still pushes (%s)", PaleSquall.push(p, 1, 0));
			check(helper, PaleSquall.push(p, -1, 0) > 0, "the wall at one side shelters from the other side too");
		} finally {
			Config.PALE_SQUALL_PUSH = push;
			Config.PALE_SQUALL_ANCHORED = anchoredEase;
		}
		helper.succeed();
	}

	/**
	 * A squall takes hold of a player standing in creative (as an explosion's push does) and sends them its gust every
	 * tick it blows, but not of one flying in creative.
	 */
	@GameTest(maxTicks = 60)
	public void aSquallTakesHoldOfTheStandingNotTheFlyingInCreative(GameTestHelper helper) {
		ServerPlayer standing = TestPlayers.creative(helper, "reach-standing");
		ServerPlayer flying = TestPlayers.creative(helper, "reach-flying");
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		standing.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		flying.teleportTo(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
		flying.getAbilities().flying = true;
		check(helper, PaleSquall.blown(standing), "a squall does not take hold of a player standing in creative");
		check(helper, !PaleSquall.blown(flying), "a squall takes hold of a player flying in creative");
		int warn = Config.PALE_SQUALL_WARNING_SECONDS, secs = Config.PALE_SQUALL_SECONDS;
		int before = PaleSquall.felt(standing.getUUID());
		Config.PALE_SQUALL_WARNING_SECONDS = 0;
		Config.PALE_SQUALL_SECONDS = 1;
		String refused = WorldEvents.force(standing, "squall");
		if (refused != null) {
			Config.PALE_SQUALL_WARNING_SECONDS = warn;
			Config.PALE_SQUALL_SECONDS = secs;
		}
		check(helper, refused == null, "the squall did not start (%s)", refused);
		helper.runAfterDelay(30, () -> {
			Config.PALE_SQUALL_WARNING_SECONDS = warn;
			Config.PALE_SQUALL_SECONDS = secs;
			int gusts = PaleSquall.felt(standing.getUUID()) - before;
			check(helper, gusts >= 15, "the player standing in creative was sent %s ticks of gust", gusts);
			check(helper, PaleSquall.felt(flying.getUUID()) == 0, "the player flying in creative was sent %s ticks of gust", PaleSquall.felt(flying.getUUID()));
			check(helper, !PaleSquall.blowing(standing.getUUID()), "the squall did not pass");
			helper.succeed();
		});
	}
}
