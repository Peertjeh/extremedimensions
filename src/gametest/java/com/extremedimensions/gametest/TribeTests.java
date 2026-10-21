package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tribe.BarterService;
import com.extremedimensions.tribe.RaidService;
import com.extremedimensions.tribe.Standing;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The tribe: standing and its levels, gifts, barter, a raid from the young to the spoils, and a friend's own hoard. */
public class TribeTests {
	@GameTest
	public void standingFollowsItsLevels(GameTestHelper helper) {
		check(helper, Standing.level(0) == Standing.Level.WARY, "a fresh standing is %s", Standing.level(0));
		check(helper, Standing.level(Config.TRIBE_TOLERATED_AT) == Standing.Level.TOLERATED, "at the tolerated mark: %s", Standing.level(Config.TRIBE_TOLERATED_AT));
		check(helper, Standing.level(Config.TRIBE_TRUSTED_AT) == Standing.Level.TRUSTED, "at the trusted mark: %s", Standing.level(Config.TRIBE_TRUSTED_AT));
		check(helper, Standing.level(Standing.MAX) == Standing.Level.KIN, "at the top: %s", Standing.level(Standing.MAX));
		check(helper, Standing.level(Config.TRIBE_HATED_AT) == Standing.Level.HATED, "at the hated mark: %s", Standing.level(Config.TRIBE_HATED_AT));
		check(helper, Standing.level(Standing.MIN) == Standing.Level.HUNTED, "at the bottom: %s", Standing.level(Standing.MIN));
		ServerPlayer p = TestPlayers.survivor(helper, "tribe-levels");
		PlayerData d = DataStore.get(p.getUUID());
		Standing.change(p, -500, "gui.extremedimensions.tribe.why.raid");
		check(helper, d.tribeRep == Standing.MIN, "standing went below the bottom: %s", d.tribeRep);
		check(helper, Standing.theftOdds(p) == 2, "the hunted are stolen from %s in 3", Standing.theftOdds(p));
		d.tribeRep = Config.TRIBE_TOLERATED_AT;
		check(helper, Standing.theftOdds(p) == 0, "the tolerated are still stolen from (%s in 3)", Standing.theftOdds(p));
		helper.succeed();
	}

	@GameTest
	public void giftsWinTheTribeOver(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "tribe-gifts");
		PlayerData d = DataStore.get(p.getUUID());
		ItemStack few = new ItemStack(Items.GOLD_NUGGET, 2);
		check(helper, BarterService.gift(p, few).isEmpty() && few.getCount() == 2, "two nuggets were taken as a gift of four");
		ItemStack dirt = new ItemStack(Items.DIRT, 10);
		check(helper, BarterService.gift(p, dirt).isEmpty() && dirt.getCount() == 10, "dirt was taken as a gift");
		ItemStack nuggets = new ItemStack(Items.GOLD_NUGGET, 9);
		ItemStack taken = BarterService.gift(p, nuggets);
		check(helper, taken.getCount() == 4 && nuggets.getCount() == 5, "a gift took %s nuggets and left %s", taken.getCount(), nuggets.getCount());
		check(helper, d.tribeRep == Config.TRIBE_REP_GIFT, "a gift brought %s standing", d.tribeRep);
		d.tribeGifts = Config.TRIBE_GIFTS_PER_DAY;
		check(helper, BarterService.gift(p, nuggets).isEmpty() && nuggets.getCount() == 5, "a gift past the day's limit was taken");
		helper.succeed();
	}

	@GameTest
	public void barterNeedsTrustAndKeepsCount(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "tribe-barter");
		PlayerData d = DataStore.get(p.getUUID());
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.EMERALD, 10));
		check(helper, BarterService.trade(p, BarterService.FRAGMENT) != null, "the tribe bartered with a stranger");
		d.tribeRep = Config.TRIBE_TRUSTED_AT;
		BarterService.Ware w = BarterService.ware(BarterService.FRAGMENT);
		int price = BarterService.price(p, w);
		check(helper, price < w.price(), "the trusted pay full price (%s)", price);
		String refused = BarterService.trade(p, BarterService.FRAGMENT);
		check(helper, refused == null, "a fair barter was refused: %s", refused);
		int emeralds = 0, fragments = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			emeralds += s.is(Items.EMERALD) ? s.getCount() : 0;
			fragments += s.is(ModItems.TETHER_FRAGMENT) ? s.getCount() : 0;
		}
		check(helper, emeralds == 10 - price && fragments == 1, "after the barter: %s emeralds, %s fragments", emeralds, fragments);
		check(helper, "gui.extremedimensions.barter.sold_out".equals(BarterService.trade(p, BarterService.FRAGMENT)), "a ware was sold more often than it may be");
		boolean kinWares = BarterService.offers(p).stream().anyMatch(o -> o.id() == BarterService.CLOTH);
		check(helper, !kinWares, "the merely trusted were offered kin's wares");
		d.tribeRep = Standing.MAX;
		kinWares = BarterService.offers(p).stream().anyMatch(o -> o.id() == BarterService.CLOTH);
		check(helper, kinWares, "kin were not offered their wares");
		check(helper, BarterService.offers(p).size() == 1 + Config.TRIBE_BARTER_OFFERS + 2, "kin saw %s wares", BarterService.offers(p).size());
		helper.succeed();
	}

	private static Hoard hoard(GameTestHelper helper, String owner) {
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.getLevel().setBlockAndUpdate(at, Blocks.BARREL.defaultBlockState());
		Hoard h = new Hoard(Dims.of(helper.getLevel()), at.getX(), at.getY(), at.getZ(), owner);
		DataStore.hoards().add(h);
		return h;
	}

	/**
	 * The Keepers woken for this player: one wakes 10 to 16 blocks behind them on whatever ground is highest there (a
	 * tower of a test beside this one, perhaps), so they are looked for the whole height of the world, and only those
	 * hunting this player (a test beside this one may have woken its own).
	 */
	private static java.util.List<Keeper> ownKeepers(ServerLevel level, ServerPlayer p) {
		AABB column = new AABB(p.getX() - 24, level.getMinY(), p.getZ() - 24, p.getX() + 24, level.getMaxY(), p.getZ() + 24);
		return level.getEntitiesOfClass(Keeper.class, column, k -> k.getTarget() == p);
	}

	private static void standBeside(GameTestHelper helper, ServerPlayer p) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	@GameTest(maxTicks = 200)
	public void aRaidEndsInSpoils(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "tribe-raider");
		standBeside(helper, p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		Hoard h = hoard(helper, "");
		PlayerData d = DataStore.get(p.getUUID());
		check(helper, RaidService.start(p, h), "opening a stranger's hoard started no raid");
		check(helper, d.tribeRep == Config.TRIBE_REP_RAID, "the raid cost %s standing", d.tribeRep);
		check(helper, "young".equals(RaidService.wave(h.key())), "the raid began with %s", RaidService.wave(h.key()));
		AABB around = new AABB(new BlockPos(h.x, h.y, h.z)).inflate(12);
		helper.runAfterDelay(5, () -> {
			var young = level.getEntitiesOfClass(Hollowling.class, around);
			check(helper, !young.isEmpty(), "no young swarmed out of the hoard");
			young.forEach(net.minecraft.world.entity.Entity::discard);
		});
		helper.runAfterDelay(15, () -> {
			check(helper, "keeper".equals(RaidService.wave(h.key())), "with the young gone the raid is in %s", RaidService.wave(h.key()));
			var keepers = ownKeepers(level, p);
			check(helper, !keepers.isEmpty(), "no Keeper woke");
			keepers.forEach(k -> k.kill(level));
		});
		helper.runAfterDelay(30, () -> {
			check(helper, h.raided && !RaidService.raiding(h.key()), "the Keeper fell but the raid did not end (raided %s, raiding %s)", h.raided, RaidService.raiding(h.key()));
			check(helper, d.raidsWon == 1, "the raid was not counted");
			boolean fragment = false;
			if (level.getBlockEntity(new BlockPos(h.x, h.y, h.z)) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					fragment |= c.getItem(i).is(ModItems.TETHER_FRAGMENT);
				}
			}
			check(helper, fragment, "the spoils held no Tether Fragment");
			check(helper, !RaidService.start(p, h), "a raided hoard was raided again");
			DataStore.hoards().remove(h);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aFriendsOwnHoardOpensWithoutAFight(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "tribe-friend");
		standBeside(helper, p);
		Hoard h = hoard(helper, p.getStringUUID());
		PlayerData d = DataStore.get(p.getUUID());
		d.tribeRep = Config.TRIBE_TRUSTED_AT;
		HollowService.onHoardOpened(p, h);
		check(helper, !HollowService.keeperAwake(h.key()) && !RaidService.raiding(h.key()), "the Keeper woke for a trusted friend's own hoard");
		d.tribeRep = 0;
		HollowService.onHoardOpened(p, h);
		check(helper, HollowService.keeperAwake(h.key()), "the Keeper slept on for a stranger to the tribe");
		check(helper, !RaidService.raiding(h.key()), "opening one's own hoard started a raid");
		helper.runAfterDelay(2, () -> {
			ownKeepers(helper.getLevel(), p).forEach(HollowService::fade);
			DataStore.hoards().remove(h);
			helper.succeed();
		});
	}
}
