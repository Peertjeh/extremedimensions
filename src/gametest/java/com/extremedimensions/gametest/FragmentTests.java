package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A Tether Fragment offered at an anchor atones for a tenth of what the curse's next stack still asks (much at first,
 * little later), and an anchor takes at most five of them for one stack.
 */
public class FragmentTests {
	private static void clear(PlayerData d) {
		d.curses.clear();
		d.atonement.clear();
		d.fragmentsOffered.clear();
		d.atoning = null;
	}

	/** Each fragment gives a tenth of what is left (rounded up); five for one stack, then none until a stack comes off. */
	@GameTest
	public void aFragmentAtonesForATenthOfWhatIsLeft(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 2);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.FRAGMENT_ATONE_SHARE;
		int max = Config.FRAGMENT_MAX_PER_STACK;
		try {
			Config.FRAGMENT_ATONE_SHARE = 0.1;
			Config.FRAGMENT_MAX_PER_STACK = 5;
			d.atoning = Penalty.GLASS_JAW.id;
			d.atonement.remove(Penalty.GLASS_JAW.id);
			BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
			int need = PenaltyService.cost(d, Penalty.GLASS_JAW);
			ItemStack fragments = new ItemStack(ModItems.TETHER_FRAGMENT, 8);
			int have = 0;
			for (int i = 1; i <= 5; i++) {
				int tenth = Math.max(1, (int) Math.ceil((need - have) * 0.1));
				PenaltyService.mend(p, d, at, fragments);
				int now = d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0);
				check(helper, now - have == tenth, "fragment %s atoned for %s, not a tenth of the %s left (%s)", i, now - have, need - have, tenth);
				have = now;
			}
			check(helper, d.owned(Penalty.GLASS_JAW) == 2, "five fragments lifted a stack (%s of %s atoned)", have, need);
			check(helper, have < need / 2, "five fragments atoned for %s of %s: more than they should", have, need);
			check(helper, fragments.getCount() == 3, "five offerings took %s fragments", 8 - fragments.getCount());
			// A sixth for the same stack: refused, and kept
			PenaltyService.mend(p, d, at, fragments);
			check(helper, fragments.getCount() == 3 && d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) == have,
					"the anchor took a sixth fragment for one stack");
			// Once the stack comes off, it takes them afresh
			PenaltyService.lift(p, d, Penalty.GLASS_JAW, "gui.extremedimensions.curses.mended");
			check(helper, d.owned(Penalty.GLASS_JAW) == 1 && d.fragmentsOffered(Penalty.GLASS_JAW) == 0,
					"after a stack came off the anchor still counts %s fragments", d.fragmentsOffered(Penalty.GLASS_JAW));
			PenaltyService.mend(p, d, at, fragments);
			check(helper, fragments.getCount() == 2 && d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) > 0,
					"after a stack came off the anchor took no fragment");
		} finally {
			Config.FRAGMENT_ATONE_SHARE = share;
			Config.FRAGMENT_MAX_PER_STACK = max;
			clear(d);
		}
		helper.succeed();
	}

	/** A fragment that covers the last of what a stack asks takes the stack off. */
	@GameTest
	public void theLastFragmentTakesTheStackOff(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.atoning = Penalty.GLASS_JAW.id;
			int need = PenaltyService.cost(d, Penalty.GLASS_JAW);
			d.atonement.put(Penalty.GLASS_JAW.id, need - 1);
			check(helper, PenaltyService.fragmentGain(d, Penalty.GLASS_JAW) == 1, "with 1 point left a fragment would give %s",
					PenaltyService.fragmentGain(d, Penalty.GLASS_JAW));
			ItemStack fragments = new ItemStack(ModItems.TETHER_FRAGMENT, 2);
			PenaltyService.mend(p, d, helper.absolutePos(new BlockPos(1, 1, 1)), fragments);
			check(helper, d.owned(Penalty.GLASS_JAW) == 0 && fragments.getCount() == 1, "the last fragment did not take the stack off");
		} finally {
			clear(d);
		}
		helper.succeed();
	}
}
