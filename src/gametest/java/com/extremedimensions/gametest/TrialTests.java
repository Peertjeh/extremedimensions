package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.util.EnumSet;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.trial.Trial;
import com.extremedimensions.trial.TrialBoard;
import com.extremedimensions.trial.TrialRun;
import com.extremedimensions.trial.TrialService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Rift Trials: the week picks the trial, it is taken up at home, only a safe return counts, and medals pay once a week. */
public class TrialTests {
	/** A week (far from the calendar's) whose trial is of this kind and, if given, has this twist. */
	private static int weekOf(Trial.Kind kind, Trial.Twist twist) {
		for (int w = 5000; w < 5200; w++) {
			if (TrialService.kind(w) == kind && (twist == null || TrialService.twist(w) == twist)) {
				return w;
			}
		}
		throw new IllegalStateException("no week has " + kind + " with " + twist);
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

	@GameTest
	public void trialsRotateByTheWeek(GameTestHelper helper) {
		Set<Trial.Kind> kinds = EnumSet.noneOf(Trial.Kind.class);
		Set<Trial.Twist> twists = EnumSet.noneOf(Trial.Twist.class);
		for (int w = 3000; w < 3005; w++) {
			kinds.add(TrialService.kind(w));
			check(helper, TrialService.kind(w) != TrialService.kind(w + 1), "two weeks in a row ask the same thing");
		}
		for (int w = 3000; w < 3025; w++) {
			twists.add(TrialService.twist(w));
		}
		check(helper, kinds.size() == Trial.Kind.values().length, "five weeks did not bring every trial (%s)", kinds);
		check(helper, twists.size() == Trial.Twist.values().length, "the twists do not all come round (%s)", twists);
		check(helper, TrialService.kind(3001) == TrialService.kind(3001) && TrialService.twist(3001) == TrialService.twist(3001), "a week's trial is not fixed");
		helper.succeed();
	}

	@GameTest
	public void aTrialIsTakenUpAtHome(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int unlock = Config.TRIAL_UNLOCK;
		try {
			Config.TRIAL_UNLOCK = 2;
			if (DataStore.ending().isEmpty()) {
				check(helper, TrialService.arm(p, true) != null && !d.trialArmed, "a trial was taken up before trials open");
			}
			Config.TRIAL_UNLOCK = 0;
			d.inSeam = true;
			check(helper, TrialService.arm(p, true) != null && !d.trialArmed, "a trial was taken up in the Seam");
			d.inSeam = false;
			check(helper, TrialService.arm(p, true) == null && d.trialArmed, "a trial could not be taken up at home");
			check(helper, TrialService.arm(p, false) == null && !d.trialArmed, "a trial could not be let go");
		} finally {
			Config.TRIAL_UNLOCK = unlock;
			d.inSeam = false;
			d.trialArmed = false;
		}
		helper.succeed();
	}

	@GameTest
	public void aTrialCountsAndPaysOnce(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 1);
		PlayerData d = DataStore.get(p.getUUID());
		int week = weekOf(Trial.Kind.VEINS, Trial.Twist.SHORT);
		int unlock = Config.TRIAL_UNLOCK;
		try {
			TrialService.weekOverride = week;
			Config.TRIAL_UNLOCK = 0;
			int[] need = Trial.Kind.VEINS.thresholds();
			// Through a rift with the trial taken up: it begins, with the twist
			d.trialArmed = true;
			d.seamMax = 400;
			d.seamLeft = 400;
			TrialService.begin(p, d, 10, 20);
			check(helper, d.trial != null && !d.trialArmed, "the trial did not begin with the visit");
			double shortened = Math.max(30, Math.floor(400 * Config.TRIAL_SHORT_FACTOR));
			check(helper, d.seamMax == shortened && d.seamLeft == shortened, "the Short Thread left %s seconds, expected %s", d.seamMax, shortened);
			// Only this kind of progress counts, and only in the Seam
			TrialService.progress(p, Trial.Kind.VEINS, 1);
			check(helper, d.trial.score == 0, "a vein counted outside the Seam");
			d.inSeam = true;
			TrialService.progress(p, Trial.Kind.HUNT, 3);
			check(helper, d.trial.score == 0, "a kill counted for the vein-cutting trial");
			TrialService.progress(p, Trial.Kind.VEINS, need[1]);
			check(helper, d.trial.score == need[1], "veins counted %s, expected %s", d.trial.score, need[1]);
			// A safe return: on the board, and bronze and silver pay
			d.inSeam = false;
			d.seamLeft = 100;
			int before = fragments(p);
			TrialService.finish(p, d);
			check(helper, d.trial == null, "the trial outlived the visit");
			TrialBoard.Entry mine = DataStore.trials().scores.get(p.getUUID().toString());
			check(helper, mine != null && mine.score == need[1] && mine.medal == 2 && mine.left == 100, "the board did not keep the run");
			int paid = fragments(p) - before;
			check(helper, paid == Config.TRIAL_BRONZE_FRAGMENTS + Config.TRIAL_SILVER_FRAGMENTS, "silver paid %s fragments", paid);
			check(helper, d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) == Config.TRIAL_BRONZE_ATONE + Config.TRIAL_SILVER_ATONE,
					"silver paid %s atonement", d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0));
			// The same medal again pays nothing, and a worse run does not replace the best one
			d.trialArmed = true;
			TrialService.begin(p, d, 10, 20);
			d.inSeam = true;
			TrialService.progress(p, Trial.Kind.VEINS, need[0]);
			d.inSeam = false;
			before = fragments(p);
			TrialService.finish(p, d);
			check(helper, fragments(p) == before, "a medal paid twice in one week");
			check(helper, DataStore.trials().scores.get(p.getUUID().toString()).score == need[1], "a worse run replaced the best one");
			// A lost trial counts for nothing
			d.trialArmed = true;
			TrialService.begin(p, d, 10, 20);
			d.inSeam = true;
			TrialService.progress(p, Trial.Kind.VEINS, need[2] + 5);
			d.inSeam = false;
			TrialService.fail(p, d);
			check(helper, d.trial == null && DataStore.trials().scores.get(p.getUUID().toString()).score == need[1], "a lost trial counted");
		} finally {
			TrialService.weekOverride = null;
			Config.TRIAL_UNLOCK = unlock;
			d.inSeam = false;
			d.trial = null;
			d.trialArmed = false;
		}
		helper.succeed();
	}

	@GameTest
	public void freshBarrelsCountOnce(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		BlockPos at = new BlockPos(987_654, 120, -987_654);
		try {
			TrialRun run = new TrialRun();
			run.week = TrialService.week();
			run.kind = Trial.Kind.PLUNDER.id;
			run.twist = Trial.Twist.CALM.id;
			d.trial = run;
			d.inSeam = true;
			TrialService.plundered(p, Dims.SEAM, at);
			check(helper, run.score == 0, "a barrel nobody stocked counted");
			TrialService.stocked(at);
			TrialService.plundered(p, Dims.OVERWORLD, at);
			check(helper, run.score == 0, "a barrel outside the Seam counted");
			TrialService.plundered(p, Dims.SEAM, at);
			TrialService.plundered(p, Dims.SEAM, at);
			check(helper, run.score == 1, "a fresh barrel counted %s times", run.score);
		} finally {
			d.inSeam = false;
			d.trial = null;
		}
		helper.succeed();
	}

	@GameTest
	public void theBoardRollsOverWithTheWeek(GameTestHelper helper) {
		TrialBoard board = DataStore.trials();
		int week = 7000;
		try {
			TrialService.weekOverride = week;
			TrialService.roll();
			board.scores.clear();
			board.scores.put("a", new TrialBoard.Entry("Ada", 5, 10, 1));
			board.scores.put("b", new TrialBoard.Entry("Bo", 9, 3, 2));
			board.scores.put("c", new TrialBoard.Entry("Cy", 9, 40, 2));
			board.scores.put("d", new TrialBoard.Entry("Di", 1, 99, 0));
			TrialService.weekOverride = week + 1;
			TrialService.roll();
			check(helper, board.week == week + 1 && board.scores.isEmpty(), "the board did not start over");
			check(helper, board.lastWeek == week && TrialService.kind(week).id.equals(board.lastKind), "the last trial was not remembered");
			check(helper, board.lastTop.size() == 3 && "Cy".equals(board.lastTop.get(0).name) && "Bo".equals(board.lastTop.get(1).name)
					&& "Ada".equals(board.lastTop.get(2).name), "the last trial's best three are wrong");
		} finally {
			TrialService.weekOverride = null;
		}
		helper.succeed();
	}
}
