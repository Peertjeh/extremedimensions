package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.pact.PactService;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Pacts: sworn stacks act only on expeditions, never count as carried curses, follow the rules, and pay out on a safe return. */
public class PactTests {
	private static Penalty firstOf(Penalty.Group group) {
		for (Penalty p : Penalty.values()) {
			if (p.group == group) {
				return p;
			}
		}
		throw new IllegalStateException("no curse in " + group);
	}

	@GameTest
	public void pactStacksActOnlyOnExpeditions(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		d.pact.put(Penalty.GLASS_JAW.id, 1);
		int cost = PenaltyService.cost(d, Penalty.GLASS_JAW);
		check(helper, d.stacks(Penalty.GLASS_JAW) == 1, "a pact acted at home (%s stacks)", d.stacks(Penalty.GLASS_JAW));
		d.inRun = true;
		try {
			check(helper, d.stacks(Penalty.GLASS_JAW) == 2, "a pact did not act on an expedition (%s stacks)", d.stacks(Penalty.GLASS_JAW));
			check(helper, d.owned(Penalty.GLASS_JAW) == 1, "sworn stacks counted as carried ones");
			check(helper, PenaltyService.cost(d, Penalty.GLASS_JAW) == cost, "a pact made atonement dearer");
		} finally {
			d.inRun = false;
		}
		helper.succeed();
	}

	@GameTest
	public void pactsFollowTheRules(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		Penalty home = firstOf(Penalty.Group.OVERWORLD);
		int unlock = Config.PACT_UNLOCK, maxHeat = Config.PACT_MAX_HEAT;
		try {
			Config.PACT_UNLOCK = 2;
			if (!DataStore.starvedSlain()) {
				check(helper, PactService.swear(p, Map.of(Penalty.GLASS_JAW.id, 1)) != null, "a pact was sworn before the Starved One fell");
			}
			Config.PACT_UNLOCK = 0;
			check(helper, PactService.swear(p, Map.of(Penalty.GLASS_JAW.id, 5, home.id, 1, Penalty.DWINDLING.id, 1)) == null, "a fair pact was refused");
			check(helper, d.sworn(Penalty.GLASS_JAW) == Penalty.GLASS_JAW.max - 1, "a sworn curse was not cut to its room (%s)", d.sworn(Penalty.GLASS_JAW));
			check(helper, d.sworn(home) == 0, "an Overworld curse was sworn");
			int heat = (Penalty.GLASS_JAW.max - 1) + 2;
			check(helper, PactService.heat(d) == heat, "heat %s, expected %s (an Extreme stack counts twice)", PactService.heat(d), heat);
			d.inRun = true;
			check(helper, PactService.swear(p, Map.of()) != null, "a pact was changed on an expedition");
			d.inRun = false;
			Config.PACT_MAX_HEAT = 1;
			check(helper, PactService.swear(p, Map.of(Penalty.DWINDLING.id, 1)) != null, "a pact hotter than allowed was sworn");
			Config.PACT_MAX_HEAT = maxHeat;
			check(helper, PactService.swear(p, Map.of()) == null && d.pact.isEmpty(), "a pact could not be renounced");
		} finally {
			Config.PACT_UNLOCK = unlock;
			Config.PACT_MAX_HEAT = maxHeat;
			d.inRun = false;
		}
		helper.succeed();
	}

	@GameTest
	public void aKeptPactPaysOut(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		d.pact.put(Penalty.GLASS_JAW.id, 2);
		d.pact.put(Penalty.DWINDLING.id, 1);
		int heat = PactService.actingHeat(d);
		int full = Config.PACT_FULL_SECONDS;
		try {
			Config.PACT_FULL_SECONDS = 600;
			// Through the portal and straight back: nothing
			d.runActive = 0;
			PactService.kept(p, d);
			check(helper, d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) == 0, "a pact paid for an expedition with no active time");
			check(helper, fragments(p) == 0, "a pact gave fragments for an expedition with no active time");
			// Half the time: half the atonement
			d.runActive = 300;
			PactService.kept(p, d);
			int half = (int) Math.round(heat * Config.PACT_ATONE_PER_HEAT * 0.5);
			check(helper, d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) == half, "half an expedition gave %s atonement, expected %s",
					d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0), half);
			// The full time: the full reward
			d.atonement.clear();
			p.getInventory().clearContent();
			d.runActive = 600;
			PactService.kept(p, d);
			int gained = d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0);
			check(helper, gained == heat * Config.PACT_ATONE_PER_HEAT, "a kept pact of heat %s gave %s atonement, expected %s", heat, gained,
					heat * Config.PACT_ATONE_PER_HEAT);
			int least = (int) (heat / Config.PACT_HEAT_PER_FRAGMENT);
			check(helper, fragments(p) >= least && fragments(p) <= least + 1, "a kept pact of heat %s gave %s fragments", heat, fragments(p));
		} finally {
			Config.PACT_FULL_SECONDS = full;
		}
		helper.succeed();
	}

	private static int fragments(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(ModItems.TETHER_FRAGMENT)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}
}
