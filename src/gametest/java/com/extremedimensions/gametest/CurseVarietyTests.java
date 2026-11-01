package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * A curse laid without a choice (a Lurker's touch, a Collapse in the Seam) is varied: never the last one again, and the
 * curses not yet carried are likelier.
 */
public class CurseVarietyTests {
	private static List<Penalty> three() {
		List<Penalty> out = new ArrayList<>();
		for (Penalty p : Penalty.values()) {
			if (p.group == Penalty.Group.BEYOND && !p.abyssal && p.max > 1 && out.size() < 3) {
				out.add(p);
			}
		}
		return out;
	}

	/** The last curse so laid never comes twice in a row, while there is anything else to lay. */
	@GameTest
	public void theLastCurseDoesNotComeTwiceInARow(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "curse-variety-last");
		PlayerData d = DataStore.get(p.getUUID());
		List<Penalty> pool = three();
		Random rnd = new Random(42);
		d.lastCurse = pool.get(0).id;
		for (int i = 0; i < 300; i++) {
			check(helper, Penalty.pickVaried(d, pool, rnd) != pool.get(0), "the last curse came again on roll %s", i);
		}
		d.lastCurse = pool.get(0).id;
		check(helper, Penalty.pickVaried(d, List.of(pool.get(0)), rnd) == pool.get(0), "a pool of one gave nothing");
		// Laid for real, twice: two different curses
		d.lastCurse = null;
		Penalty first = PenaltyService.curse(p, false), second = PenaltyService.curse(p, false);
		check(helper, first != null && second != null && first != second, "two curses in a row were %s and %s", first, second);
		d.curses.clear();
		d.lastCurse = null;
		helper.succeed();
	}

	/** Curses not yet carried are curse_fresh_weight times likelier than one already carried. */
	@GameTest
	public void freshCursesAreLikelier(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "curse-variety-fresh");
		PlayerData d = DataStore.get(p.getUUID());
		List<Penalty> pool = three();
		double weight = Config.CURSE_FRESH_WEIGHT;
		try {
			Config.CURSE_FRESH_WEIGHT = 3;
			d.lastCurse = null;
			d.addStacks(pool.get(0), 1);
			Random rnd = new Random(7);
			int carried = 0, n = 7000;
			for (int i = 0; i < n; i++) {
				if (Penalty.pickVaried(d, pool, rnd) == pool.get(0)) {
					carried++;
				}
			}
			double share = carried / (double) n;
			// one of weight 1 against two of weight 3: about 1 in 7
			check(helper, share > 0.10 && share < 0.19, "the carried curse came %s of the time, not about 1 in 7", share);
		} finally {
			Config.CURSE_FRESH_WEIGHT = weight;
			d.curses.clear();
		}
		helper.succeed();
	}
}
