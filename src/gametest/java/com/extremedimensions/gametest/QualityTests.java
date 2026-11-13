package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.CurseTotals;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

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
}
