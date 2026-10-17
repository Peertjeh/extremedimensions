package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Relics bind to the Tether one at a time, come back when unbound, and give what they promise and take their price. */
public class RelicTests {
	private static int count(ServerPlayer p, Relic r) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(RelicService.item(r))) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	@GameTest
	public void relicsBindOneAtATime(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicService.item(Relic.EMBER_HEART)));
		check(helper, RelicService.bind(p, p.getMainHandItem()) == InteractionResult.SUCCESS, "binding a relic failed");
		check(helper, RelicService.has(d, Relic.EMBER_HEART) && p.getMainHandItem().isEmpty(), "the relic was not bound, or stayed in the hand");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicService.item(Relic.PALE_SHARD)));
		RelicService.bind(p, p.getMainHandItem());
		check(helper, RelicService.has(d, Relic.PALE_SHARD), "a second relic did not take the first one's place");
		check(helper, count(p, Relic.EMBER_HEART) == 1, "the first relic did not come back when the second was bound");
		RelicService.unbind(p);
		check(helper, RelicService.bound(d) == null && count(p, Relic.PALE_SHARD) == 1, "unbinding did not give the relic back");
		helper.succeed();
	}

	@GameTest
	public void relicsGiveAndTake(GameTestHelper helper) {
		ServerPlayer clean = cursed(helper);
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		double reach = clean.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		double fall = clean.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicService.item(Relic.HUNGERING_HAND)));
		RelicService.bind(p, p.getMainHandItem());
		near(helper, p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), reach + Config.RELIC_HUNGERING_HAND_REACH, "reach with the Hungering Hand");
		near(helper, p.getMaxHealth(), 20 - Config.RELIC_HUNGERING_HAND_HEALTH, "max health with the Hungering Hand");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicService.item(Relic.FEATHERBONE)));
		RelicService.bind(p, p.getMainHandItem());
		near(helper, p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), reach, "reach after the Hungering Hand came off");
		near(helper, p.getMaxHealth(), 20, "max health after the Hungering Hand came off");
		near(helper, p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), fall + Config.RELIC_FEATHERBONE_FALL, "safe fall with the Featherbone");
		// Drain: the Ember Heart eases the Nether and burdens the End; the Pale Shard the other way round
		d.relic = Relic.EMBER_HEART.id;
		near(helper, RelicService.drainFactor(d, Dims.NETHER), Config.RELIC_EMBER_HEART_NETHER, "Nether drain with the Ember Heart");
		near(helper, RelicService.drainFactor(d, Dims.END), Config.RELIC_EMBER_HEART_END, "End drain with the Ember Heart");
		d.relic = Relic.PALE_SHARD.id;
		near(helper, RelicService.drainFactor(d, Dims.END), Config.RELIC_PALE_SHARD_END, "End drain with the Pale Shard");
		d.relic = null;
		near(helper, RelicService.drainFactor(d, Dims.NETHER), 1.0, "drain without a relic");
		// The Keeper's Eye takes half of every kill's atonement (a leftover half is a chance at a point)
		d.relic = Relic.KEEPERS_EYE.id;
		int sum = 0;
		for (int i = 0; i < 200; i++) {
			int pts = PenaltyService.killPoints(d, 3);
			check(helper, pts == 1 || pts == 2, "a 3-point kill with the Keeper's Eye gave %s points", pts);
			sum += pts;
		}
		check(helper, sum > 200 && sum < 400, "200 kills of 3 points with the Keeper's Eye gave %s points (about 300 expected)", sum);
		d.relic = null;
		check(helper, PenaltyService.killPoints(d, 3) == 3, "a kill without the Keeper's Eye lost points");
		helper.succeed();
	}
}
