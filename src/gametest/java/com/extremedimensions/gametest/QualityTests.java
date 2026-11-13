package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.penalty.CurseTotals;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The 3.55.0 overhaul of what the player is told: curse totals, fragments that move on to the next curse, the reason a
 * wound stays shut, an Ember or Void Tether that would be wasted, and what a Lurker took. None of it changes what
 * anything costs or gives: these check that what is said is what the code does.
 */
public class QualityTests {
	/**
	 * A curse's total is worked out as the code applies it: Debt and Leaky Tank compound (Debt keeping a twentieth of
	 * every stack), Toll stops at its ceiling, Vertigo comes more often, and a curse without a number has no total.
	 */
	@GameTest
	public void curseTotalsAreWhatTheCurseDoes(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.DEBT, 3, Penalty.ACCELERATED_DRAIN, 2);
		PlayerData d = DataStore.get(p.getUUID());
		near(helper, CurseTotals.total(Penalty.DEBT, Penalty.DEBT.value, d.owned(Penalty.DEBT)), (1 - d.debtFactor()) * 100, "Debt at 3 stacks");
		near(helper, CurseTotals.total(Penalty.ACCELERATED_DRAIN, Penalty.ACCELERATED_DRAIN.value, 2), Penalty.ACCELERATED_DRAIN.value * 2,
				"Accelerated Drain at 2 stacks");
		near(helper, CurseTotals.total(Penalty.LEAKY_TANK, 10, 3), (1 - Math.pow(0.9, 3)) * 100, "Leaky Tank at 3 stacks of 10%");
		near(helper, CurseTotals.total(Penalty.DEBT, 99, 2), (1 - 0.05 * 0.05) * 100, "Debt keeps a twentieth of every stack");
		near(helper, CurseTotals.total(Penalty.TOLL, 50, 3), 90, "Toll held at its ceiling");
		check(helper, CurseTotals.capped(Penalty.TOLL, 50, 3) && !CurseTotals.capped(Penalty.TOLL, 15, 3), "Toll's ceiling not told right");
		near(helper, CurseTotals.total(Penalty.VERTIGO, 90, 2), 45, "Vertigo at 2 stacks");
		near(helper, CurseTotals.total(Penalty.VERTIGO, 90, 20), 10, "Vertigo never comes oftener than every 10 s");
		near(helper, CurseTotals.total(Penalty.SCAR, 2, 4), 8, "Scar takes at most 8 hearts");
		check(helper, CurseTotals.spec(Penalty.FAMINE) == null && CurseTotals.spec(Penalty.DWINDLING) == null
				&& CurseTotals.spec(Penalty.SUNSCORN) == null, "a curse whose card names no number has a total");
		check(helper, CurseTotals.total(Penalty.HEAVY_LEGS, 8, 0) == 0, "no stacks, yet a total");
		helper.succeed();
	}

	/**
	 * Bounced by a portal whose anchor the Tether is bound to: the player hears what is wrong with that anchor (none of
	 * their time in it, or blocked), not that it stands by another portal, though another anchor beside this one holds
	 * their time. Bound to one that really stands elsewhere, they still hear that.
	 */
	@GameTest(environment = Alone.BOUND_ANCHOR)
	public void aBoundAnchorBesideThePortalSaysWhatIsWrong(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "bound-here");
		PlayerData d = DataStore.get(p.getUUID());
		// Far off, where no other test's anchor stands
		d.hasLast = true;
		d.lastX = 71000.5;
		d.lastY = 70;
		d.lastZ = 71000.5;
		Anchor full = new Anchor(71002, 70, 71000, Dims.NETHER);
		Anchor bound = new Anchor(71000, 70, 71002, Dims.NETHER);
		Anchor far = new Anchor(72000, 70, 72000, Dims.NETHER);
		full.add(p.getStringUUID(), 300);
		far.add(p.getStringUUID(), 300);
		for (Anchor a : new Anchor[] {full, bound, far}) {
			DataStore.anchors().put(a.key(), a);
		}
		String was = d.tetherAnchor;
		try {
			d.tetherAnchor = bound.key();
			check(helper, "bound_empty".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 300)), "bound to an empty anchor here: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 300));
			bound.blocked = true;
			check(helper, "blocked".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 300)), "bound to a blocked anchor here: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 300));
			d.tetherAnchor = far.key();
			check(helper, "elsewhere".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 300)), "bound to an anchor by another portal: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 300));
		} finally {
			d.tetherAnchor = was;
			for (Anchor a : new Anchor[] {full, bound, far}) {
				DataStore.anchors().remove(a.key());
			}
		}
		helper.succeed();
	}

	/**
	 * Several curses owed in a row: the screen is told which of them the offer is ("Curse 2 of 3") and how long before
	 * the Tether chooses, until the last is answered.
	 */
	@GameTest
	public void cursesOwedInARowSayWhichThisIs(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "owing-three");
		PlayerData d = DataStore.get(p.getUUID());
		check(helper, PenaltyService.offerState(p, d).isEmpty(), "an offer is told with none waiting");
		for (int i = 0; i < 3; i++) {
			PenaltyService.owe(d, false);
		}
		String[] s = PenaltyService.offerState(p, d).split(":");
		check(helper, s.length == 3 && "1".equals(s[1]) && "3".equals(s[2]), "the first of three reads %s", String.join(":", s));
		check(helper, Config.OFFER_TIMEOUT_SECONDS <= 0 || Integer.parseInt(s[0]) == Config.OFFER_TIMEOUT_SECONDS,
				"a fresh offer has %s s left, not the whole %s", s[0], Config.OFFER_TIMEOUT_SECONDS);
		PenaltyService.choose(p, d.pendingOffer.get(0));
		s = PenaltyService.offerState(p, d).split(":");
		check(helper, s.length == 3 && "-1".equals(s[0]) && "2".equals(s[1]) && "3".equals(s[2]),
				"the second of three (its cards not shown yet) reads %s", String.join(":", s));
		PenaltyService.choose(p, d.pendingOffer.get(0));
		PenaltyService.choose(p, d.pendingOffer.get(0));
		check(helper, d.pendingOffer.isEmpty() && PenaltyService.offerState(p, d).isEmpty() && d.offerSeries == 0,
				"after the third the offer still reads %s", PenaltyService.offerState(p, d));
		helper.succeed();
	}

	/**
	 * Fragments go on to the next curse once the chosen curse's stack has had its five: the chosen one first, then the
	 * heaviest other, each fragment giving what it always gave; with every stack's fragments given, the next is kept;
	 * and a player with no curse keeps theirs.
	 */
	@GameTest
	public void fragmentsMoveOnToTheNextCurse(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.KINDLING, 1, Penalty.GLASS_JAW, 2, Penalty.VOLATILE, 1);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.FRAGMENT_ATONE_SHARE;
		int max = Config.FRAGMENT_MAX_PER_STACK;
		try {
			Config.FRAGMENT_ATONE_SHARE = 0.1;
			Config.FRAGMENT_MAX_PER_STACK = 2;
			// Kindling is the one being atoned for, though Glass Jaw is heavier
			d.atoning = Penalty.KINDLING.id;
			BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
			ItemStack fragments = new ItemStack(ModItems.TETHER_FRAGMENT, 16);
			for (int i = 0; i < 2; i++) {
				PenaltyService.mend(p, d, at, fragments);
			}
			check(helper, d.fragmentsOffered(Penalty.KINDLING) == 2 && d.fragmentsOffered(Penalty.GLASS_JAW) == 0,
					"the first two fragments did not go to the curse being atoned for");
			// The third: Kindling's stack has had its two, so it goes to the heaviest other, Glass Jaw, and gives a tenth
			int left = PenaltyService.cost(d, Penalty.GLASS_JAW) - d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0);
			PenaltyService.mend(p, d, at, fragments);
			check(helper, d.fragmentsOffered(Penalty.GLASS_JAW) == 1 && fragments.getCount() == 13,
					"the third fragment did not go on to Glass Jaw (%s offered there, %s left in hand)", d.fragmentsOffered(Penalty.GLASS_JAW),
					fragments.getCount());
			check(helper, d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) == Math.max(1, (int) Math.ceil(left * 0.1)),
					"a fragment gone on gave %s, not a tenth of the %s left", d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0), left);
			// Glass Jaw's second, then Volatile's two; after that every stack has had its fragments, and the next is kept
			for (int i = 0; i < 3; i++) {
				PenaltyService.mend(p, d, at, fragments);
			}
			check(helper, d.fragmentsOffered(Penalty.GLASS_JAW) == 2 && d.fragmentsOffered(Penalty.VOLATILE) == 2 && fragments.getCount() == 10,
					"the fragments did not go on to Volatile once Glass Jaw had its two");
			PenaltyService.mend(p, d, at, fragments);
			check(helper, fragments.getCount() == 10, "an anchor took a fragment no curse's stack had room for");
			check(helper, d.owned(Penalty.KINDLING) == 1 && d.owned(Penalty.GLASS_JAW) == 2 && d.owned(Penalty.VOLATILE) == 1,
					"two fragments a stack lifted a stack");
		} finally {
			Config.FRAGMENT_ATONE_SHARE = share;
			Config.FRAGMENT_MAX_PER_STACK = max;
		}
		// No curse at all: the fragment is kept
		ServerPlayer clean = TestPlayers.survivor(helper, "clean-mender");
		ItemStack one = new ItemStack(ModItems.TETHER_FRAGMENT, 1);
		PenaltyService.mend(clean, DataStore.get(clean.getUUID()), helper.absolutePos(new BlockPos(1, 1, 1)), one);
		check(helper, one.getCount() == 1, "a player with no curse lost a fragment to an anchor");
		helper.succeed();
	}
}
