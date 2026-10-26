package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tapestry.Unweaving;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Unweaving: a full set (its Abyssal curses aside) is priced in Tetherite and cords, dearer for the Extreme set and
 * with each unweaving; the loom refuses until all is ready; the knots are tied round it and cut by blows, and with the
 * last the whole set comes off; a failed unweaving gives back its share, a lost one all of it. Each test that ties knots
 * lets them go again within the same tick.
 */
public class UnweavingTests {
	/** The settings these tests change, to be put back. */
	private record Saved(int on, double fill, int ingots, int cords, double extreme, double growth, int days, int knots, int hits, double refund) {
		static Saved take() {
			return new Saved(Config.UNWEAVING, Config.UNWEAVE_FILL, Config.UNWEAVE_INGOTS, Config.UNWEAVE_CORDS, Config.UNWEAVE_EXTREME_FACTOR,
					Config.UNWEAVE_COST_GROWTH, Config.UNWEAVE_COOLDOWN_DAYS, Config.UNWEAVE_KNOTS, Config.UNWEAVE_KNOT_HITS, Config.UNWEAVE_REFUND);
		}

		/** The settings as shipped, so the numbers below hold whatever the test world was set to. */
		static void defaults() {
			Config.UNWEAVING = 1;
			Config.UNWEAVE_FILL = 1.0;
			Config.UNWEAVE_INGOTS = 2;
			Config.UNWEAVE_CORDS = 4;
			Config.UNWEAVE_EXTREME_FACTOR = 1.5;
			Config.UNWEAVE_COST_GROWTH = 0.5;
			Config.UNWEAVE_COOLDOWN_DAYS = 3;
			Config.UNWEAVE_KNOTS = 6;
			Config.UNWEAVE_KNOT_HITS = 6;
			Config.UNWEAVE_REFUND = 0.5;
		}

		void restore() {
			Config.UNWEAVING = on;
			Config.UNWEAVE_FILL = fill;
			Config.UNWEAVE_INGOTS = ingots;
			Config.UNWEAVE_CORDS = cords;
			Config.UNWEAVE_EXTREME_FACTOR = extreme;
			Config.UNWEAVE_COST_GROWTH = growth;
			Config.UNWEAVE_COOLDOWN_DAYS = days;
			Config.UNWEAVE_KNOTS = knots;
			Config.UNWEAVE_KNOT_HITS = hits;
			Config.UNWEAVE_REFUND = refund;
		}
	}

	/** Every curse of the set (its Abyssal curses aside) at its maximum. */
	static void fill(PlayerData d, Penalty.Group g) {
		for (Penalty pen : Penalty.values()) {
			if (pen.group == g && !pen.abyssal && pen.max > 0) {
				d.addStacks(pen, pen.max);
			}
		}
	}

	private static Penalty first(Penalty.Group g, boolean abyssal) {
		for (Penalty pen : Penalty.values()) {
			if (pen.group == g && pen.abyssal == abyssal && pen.max > 0) {
				return pen;
			}
		}
		throw new IllegalStateException("no curse in " + g);
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	/** A loom in the test's corner, made the world's loom, and the player stood beside it with the price. */
	private static BlockPos loom(GameTestHelper helper, ServerPlayer p, int ingots, int cords) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(at, ModBlocks.WEAVERS_LOOM.defaultBlockState());
		DataStore.setLoom(level.dimension().identifier() + "|" + at.getX() + "|" + at.getY() + "|" + at.getZ());
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		p.getInventory().clearContent();
		if (ingots > 0) {
			p.getInventory().add(new ItemStack(ModItems.TETHERITE_INGOT, ingots));
		}
		if (cords > 0) {
			p.getInventory().add(new ItemStack(ModItems.TETHER_CORD, cords));
		}
		return at;
	}

	@GameTest
	public void aFullSetIsPricedAndDearerEachTime(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			Saved.defaults();
			ServerPlayer p = cursed(helper);
			PlayerData d = DataStore.get(p.getUUID());
			Penalty.Group beyond = Penalty.Group.BEYOND, extreme = Penalty.Group.EXTREME;
			check(helper, !Unweaving.full(d, beyond), "an empty set counts as full");
			fill(d, beyond);
			check(helper, Unweaving.full(d, beyond) && Unweaving.stacks(d, beyond) == Unweaving.most(beyond), "a full set does not count as full (%s of %s)",
					Unweaving.stacks(d, beyond), Unweaving.most(beyond));
			d.removeStack(first(beyond, false));
			check(helper, !Unweaving.full(d, beyond), "a set one stack short counts as full");
			Config.UNWEAVE_FILL = 0.5;
			check(helper, Unweaving.full(d, beyond), "a set past half does not count as full enough at unweave_fill 0.5");
			Config.UNWEAVE_FILL = 1.0;

			// An Abyssal curse is not part of its set here: it has a deed of its own
			d.addStacks(first(extreme, true), 1);
			check(helper, Unweaving.stacks(d, extreme) == 0, "an Abyssal curse counts toward the Extreme set (%s)", Unweaving.stacks(d, extreme));
			int most = 0;
			for (Penalty pen : Penalty.values()) {
				most += pen.group == extreme && !pen.abyssal ? Math.max(0, pen.max) : 0;
			}
			check(helper, Unweaving.most(extreme) == most, "the Extreme set holds %s stacks, not %s", Unweaving.most(extreme), most);

			// The price: the Extreme set half again, and each unweaving after the first half again more
			check(helper, Unweaving.ingots(d, beyond) == 2 && Unweaving.cords(d, beyond) == 4, "the first unweaving costs %s ingots and %s cords",
					Unweaving.ingots(d, beyond), Unweaving.cords(d, beyond));
			check(helper, Unweaving.ingots(d, extreme) == 3 && Unweaving.cords(d, extreme) == 6, "the Extreme set costs %s ingots and %s cords",
					Unweaving.ingots(d, extreme), Unweaving.cords(d, extreme));
			d.unwoven = 1;
			check(helper, Unweaving.ingots(d, beyond) == 3 && Unweaving.cords(d, beyond) == 6, "a second unweaving costs %s ingots and %s cords",
					Unweaving.ingots(d, beyond), Unweaving.cords(d, beyond));
			check(helper, Unweaving.ingots(d, extreme) == 5 && Unweaving.cords(d, extreme) == 9, "a second Extreme unweaving costs %s ingots and %s cords",
					Unweaving.ingots(d, extreme), Unweaving.cords(d, extreme));
			d.unwoven = 2;
			check(helper, Unweaving.ingots(d, beyond) == 4 && Unweaving.cords(d, beyond) == 8, "a third unweaving costs %s ingots and %s cords",
					Unweaving.ingots(d, beyond), Unweaving.cords(d, beyond));
			helper.succeed();
		} finally {
			saved.restore();
		}
	}

	@GameTest
	public void theLoomRefusesUntilAllIsReady(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			Saved.defaults();
			ServerPlayer p = cursed(helper);
			PlayerData d = DataStore.get(p.getUUID());
			Penalty.Group g = Penalty.Group.OVERWORLD;
			p.getInventory().clearContent();
			check(helper, "gui.extremedimensions.unweave.not_full".equals(Unweaving.refuse(p, d, g)), "a set not full was not refused as such: %s",
					Unweaving.refuse(p, d, g));
			fill(d, g);
			check(helper, "gui.extremedimensions.unweave.cost".equals(Unweaving.refuse(p, d, g)), "a player without the price was not refused: %s",
					Unweaving.refuse(p, d, g));
			p.getInventory().add(new ItemStack(ModItems.TETHERITE_INGOT, 2));
			p.getInventory().add(new ItemStack(ModItems.TETHER_CORD, 3));
			check(helper, "gui.extremedimensions.unweave.cost".equals(Unweaving.refuse(p, d, g)), "a cord short of the price was not refused");
			p.getInventory().add(new ItemStack(ModItems.TETHER_CORD, 1));
			check(helper, Unweaving.refuse(p, d, g) == null, "a full set with its price was refused: %s", Unweaving.refuse(p, d, g));

			long now = helper.getLevel().getServer().overworld().getGameTime();
			d.unweaveAgain = now + 1200;
			check(helper, "gui.extremedimensions.unweave.wait".equals(Unweaving.refuse(p, d, g)) && Unweaving.waitTicks(p, d) == 1200,
					"the loom took a thread again before the wait was over (%s ticks to wait)", Unweaving.waitTicks(p, d));
			d.unweaveAgain = now;
			check(helper, Unweaving.refuse(p, d, g) == null, "the loom still refused once the wait was over");
			Config.UNWEAVING = 0;
			check(helper, "gui.extremedimensions.unweave.off".equals(Unweaving.refuse(p, d, g)), "the Unweaving went on while switched off");
			Config.UNWEAVING = 1;

			// A creative player is never asked for the price
			ServerPlayer op = TestPlayers.creative(helper, "unweave-op");
			PlayerData od = DataStore.get(op.getUUID());
			op.getInventory().clearContent();
			fill(od, g);
			check(helper, Unweaving.refuse(op, od, g) == null, "a creative player was asked for the price: %s", Unweaving.refuse(op, od, g));
			check(helper, Unweaving.set("overworld") == g && Unweaving.set("abyssal") == null, "a set's name does not find the set");
			helper.succeed();
		} finally {
			saved.restore();
		}
	}

	@GameTest
	public void cuttingEveryKnotLiftsTheWholeSet(GameTestHelper helper) {
		Saved saved = Saved.take();
		String loomBefore = DataStore.loom();
		try {
			Saved.defaults();
			ServerLevel level = helper.getLevel();
			ServerPlayer p = TestPlayers.survivor(helper, "unweaver");
			PlayerData d = DataStore.get(p.getUUID());
			Penalty.Group g = Penalty.Group.BEYOND;
			fill(d, g);
			Penalty everywhere = first(Penalty.Group.EVERYWHERE, false), abyssal = first(Penalty.Group.EXTREME, true);
			d.addStacks(everywhere, 1);
			d.addStacks(abyssal, 1);
			int scars = d.scars;
			BlockPos at = loom(helper, p, 3, 5);

			// Away from the loom nothing begins
			p.teleportTo(at.getX() + 30.5, at.getY(), at.getZ() + 0.5);
			Unweaving.request(p, g.name());
			check(helper, !Unweaving.active(), "an unweaving began away from the loom");
			p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);

			Unweaving.request(p, g.name());
			check(helper, Unweaving.active(), "an unweaving did not begin at the loom");
			List<BlockPos> knots = new ArrayList<>(Unweaving.knots());
			check(helper, !knots.isEmpty() && knots.size() == Unweaving.knotsLeft() && knots.size() <= 6, "%s knots were tied", knots.size());
			for (BlockPos k : knots) {
				check(helper, level.getBlockState(k).is(ModBlocks.CURSE_KNOT) && Unweaving.holds(level, k), "no knot hangs at %s", k);
				check(helper, k.getY() > at.getY() && k.distSqr(at) <= 8 * 8 + 5 * 5, "a knot hangs at %s, away from the loom at %s", k, at);
			}
			check(helper, count(p, ModItems.TETHERITE_INGOT) == 1 && count(p, ModItems.TETHER_CORD) == 1, "the price taken left %s ingots and %s cords",
					count(p, ModItems.TETHERITE_INGOT), count(p, ModItems.TETHER_CORD));
			check(helper, d.unweaving.equals("BEYOND|2|4"), "the unweaving under way is kept as '%s'", d.unweaving);

			// While it lasts, the loom is busy for anyone else
			ServerPlayer other = cursed(helper);
			PlayerData od = DataStore.get(other.getUUID());
			fill(od, g);
			check(helper, "gui.extremedimensions.unweave.busy".equals(Unweaving.refuse(other, od, g)), "a second unweaving was let begin");

			// A blow cuts a knot (one blow each here); a second blow in the same breath does not land
			Config.UNWEAVE_KNOT_HITS = 1;
			int left = Unweaving.knotsLeft();
			BlockPos cut = Unweaving.knots().get(0);
			check(helper, Unweaving.hit(p, level, cut), "a blow at a knot was not taken");
			check(helper, Unweaving.knotsLeft() == left - 1 && level.getBlockState(cut).isAir(), "a blow did not cut the knot (%s of %s left)",
					Unweaving.knotsLeft(), left);
			if (Unweaving.active()) {
				Unweaving.hit(p, level, Unweaving.knots().get(0));
				check(helper, Unweaving.knotsLeft() == left - 1, "two blows landed in the same breath");
			}

			Unweaving.cutAll();
			check(helper, !Unweaving.active(), "the unweaving went on with every knot cut");
			check(helper, Unweaving.stacks(d, g) == 0, "%s stacks of the set stayed", Unweaving.stacks(d, g));
			check(helper, d.owned(everywhere) == 1 && d.owned(abyssal) == 1, "the unweaving took curses of other sets (%s, %s)", d.owned(everywhere),
					d.owned(abyssal));
			check(helper, d.unwoven == 1 && d.unweaving.isEmpty() && d.scars == Math.min(5, scars + 1), "after it: unwoven %s, kept '%s', scars %s",
					d.unwoven, d.unweaving, d.scars);
			check(helper, Unweaving.waitTicks(p, d) == 3 * 24000L, "the wait after an unweaving is %s ticks", Unweaving.waitTicks(p, d));
			check(helper, WorldFx.hasAdvancement(p, Advancements.UNWOVEN), "the unweaving gave no advancement");
			for (BlockPos k : knots) {
				check(helper, !level.getBlockState(k).is(ModBlocks.CURSE_KNOT), "a knot stayed at %s", k);
			}
			check(helper, Unweaving.refuse(p, d, g) != null, "the loom would unweave the empty set again at once");
			helper.succeed();
		} finally {
			Unweaving.clear();
			DataStore.setLoom(loomBefore);
			saved.restore();
		}
	}

	@GameTest
	public void aFailedUnweavingGivesBackItsShare(GameTestHelper helper) {
		Saved saved = Saved.take();
		String loomBefore = DataStore.loom();
		try {
			Saved.defaults();
			ServerLevel level = helper.getLevel();
			ServerPlayer p = TestPlayers.survivor(helper, "unweave-failed");
			PlayerData d = DataStore.get(p.getUUID());
			Penalty.Group g = Penalty.Group.EVERYWHERE;
			fill(d, g);
			int most = Unweaving.stacks(d, g);
			loom(helper, p, 2, 4);
			check(helper, Unweaving.start(p, helper.absolutePos(new BlockPos(1, 1, 1)), g), "the unweaving did not begin");
			List<BlockPos> knots = new ArrayList<>(Unweaving.knots());
			check(helper, count(p, ModItems.TETHERITE_INGOT) == 0 && count(p, ModItems.TETHER_CORD) == 0, "the price was not taken");

			Unweaving.timeOut();
			Unweaving.tick(level.getServer());
			check(helper, !Unweaving.active(), "the unweaving went on past its time");
			check(helper, count(p, ModItems.TETHERITE_INGOT) == 1 && count(p, ModItems.TETHER_CORD) == 2, "a failed unweaving gave back %s ingots and %s cords",
					count(p, ModItems.TETHERITE_INGOT), count(p, ModItems.TETHER_CORD));
			check(helper, Unweaving.stacks(d, g) == most && d.unwoven == 0 && d.unweaving.isEmpty() && Unweaving.waitTicks(p, d) == 0,
					"a failed unweaving changed the player (stacks %s of %s, unwoven %s, kept '%s', wait %s)", Unweaving.stacks(d, g), most, d.unwoven,
					d.unweaving, Unweaving.waitTicks(p, d));
			for (BlockPos k : knots) {
				check(helper, !level.getBlockState(k).is(ModBlocks.CURSE_KNOT), "a knot stayed at %s after it failed", k);
			}
			helper.succeed();
		} finally {
			Unweaving.clear();
			DataStore.setLoom(loomBefore);
			saved.restore();
		}
	}

	@GameTest
	public void aLostUnweavingIsGivenBackOnJoining(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "unweave-lost");
		PlayerData d = DataStore.get(p.getUUID());
		p.getInventory().clearContent();
		d.unweaving = "EXTREME|3|6";
		Unweaving.onJoin(p, d);
		check(helper, count(p, ModItems.TETHERITE_INGOT) == 3 && count(p, ModItems.TETHER_CORD) == 6 && d.unweaving.isEmpty(),
				"a lost unweaving gave back %s ingots and %s cords (kept '%s')", count(p, ModItems.TETHERITE_INGOT), count(p, ModItems.TETHER_CORD), d.unweaving);
		Unweaving.onJoin(p, d);
		check(helper, count(p, ModItems.TETHERITE_INGOT) == 3, "a lost unweaving was given back twice");

		// A knot nobody holds comes undone at a touch
		BlockPos stray = helper.absolutePos(new BlockPos(2, 2, 2));
		level.setBlockAndUpdate(stray, ModBlocks.CURSE_KNOT.defaultBlockState());
		check(helper, !Unweaving.holds(level, stray) && Unweaving.hit(p, level, stray) && level.getBlockState(stray).isAir(), "a stray knot did not come undone");
		helper.succeed();
	}
}
