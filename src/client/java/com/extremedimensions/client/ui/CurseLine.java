package com.extremedimensions.client.ui;

import com.extremedimensions.penalty.CurseTotals;
import com.extremedimensions.penalty.Penalty;

/**
 * A curse's total in words (3.55.0): "Now: 30% (3 × 10%)" for what the player carries, "After: 40% (4 × 10%)" on a
 * card. The number is worked out by {@link CurseTotals} as the code applies the curse; a compounding curse, one held
 * at its ceiling and Vertigo (which comes more often, not harder) each say so, so the sum never looks wrong.
 */
public final class CurseLine {
	private CurseLine() {}

	/** One stack's value (or a total) with its unit: "25%", "1♥", "0.5 blocks", "3 min". */
	public static String amount(CurseTotals.Unit unit, double v) {
		String n = CurseTotals.format(v);
		if (unit == CurseTotals.Unit.BLOCKS && "1".equals(n)) {
			return Ui.tr("gui.extremedimensions.curses.unit.block", n);
		}
		return Ui.tr("gui.extremedimensions.curses.unit." + unit.id(), n);
	}

	/**
	 * The total of a curse at so many stacks, as "now" or "after" says it ({@code value}: one stack's, as the server
	 * sent it). Null for a curse without a number and for no stacks.
	 */
	public static String total(String when, Penalty pen, int stacks, String value) {
		CurseTotals.Spec spec = CurseTotals.spec(pen);
		if (spec == null || stacks <= 0) {
			return null;
		}
		double v;
		try {
			v = Double.parseDouble(value);
		} catch (RuntimeException e) {
			v = pen.value;
		}
		double total = CurseTotals.total(pen, v, stacks);
		String key = "gui.extremedimensions.curses.total." + when;
		if (spec.rule() == CurseTotals.Rule.DIVIDES) {
			return Ui.tr(key + "_divided", amount(spec.unit(), total), stacks, amount(spec.unit(), v));
		}
		String suffix = spec.rule() == CurseTotals.Rule.COMPOUNDS && stacks > 1 ? "_compound" : CurseTotals.capped(pen, v, stacks) ? "_capped" : "";
		return Ui.tr(key + suffix, amount(spec.unit(), total), stacks, amount(spec.unit(), v));
	}
}
