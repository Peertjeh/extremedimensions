package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProgressionTest {
	private final LevelCurve curve = new LevelCurve(100, 50, 1.0, 100);

	@Test
	void levelCurveIsLinearWithIncrement() {
		assertEquals(100, curve.xpToNext(1));
		assertEquals(150, curve.xpToNext(2));
		assertEquals(200, curve.xpToNext(3));
		assertEquals(0, curve.totalFor(1));
		assertEquals(250, curve.totalFor(3));
		assertEquals(1, curve.levelFor(0));
		assertEquals(1, curve.levelFor(99));
		assertEquals(2, curve.levelFor(100));
		assertEquals(3, curve.levelFor(250));
		LevelCurve.Progress p = curve.progress(120);
		assertEquals(2, p.level());
		assertEquals(20, p.xpIntoLevel());
		assertEquals(150, p.xpForNext());
	}

	@Test
	void levelCurveCapsAtMax() {
		LevelCurve small = new LevelCurve(10, 0, 1.0, 3);
		LevelCurve.Progress p = small.progress(1_000_000);
		assertEquals(3, p.level());
		assertTrue(p.maxed());
		assertEquals(1.0, p.fraction());
	}

	@Test
	void growthMakesLevelsSteeper() {
		LevelCurve g = new LevelCurve(100, 0, 1.5, 50);
		assertEquals(100, g.xpToNext(1));
		assertEquals(150, g.xpToNext(2));
		assertEquals(225, g.xpToNext(3));
	}

	@Test
	void addXpReportsLevelUpsAndMilestoneBonus() {
		PlayerProfile p = new PlayerProfile();
		LevelCurve flat = new LevelCurve(100, 0, 1.0, 100);
		Progression.LevelChange c = Progression.addXp(p, 450, flat, 5, 100);
		assertEquals(1, c.oldLevel());
		assertEquals(5, c.newLevel());
		assertEquals(100, c.bonusCoins());
		assertEquals(100, p.coins);
		assertEquals(5, p.level);
		Progression.LevelChange none = Progression.addXp(p, 10, flat, 5, 100);
		assertFalse(none.leveledUp());
	}

	@Test
	void rewardsAddWinAndKills() {
		RewardSettings s = new RewardSettings(50, 10, 5, 100, 30, 10);
		assertEquals(new Rewards.Reward(70, 150), Rewards.compute(s, Outcome.WIN, 2, 1.0));
		assertEquals(new Rewards.Reward(10, 30), Rewards.compute(s, Outcome.LOSS, 0, 1.0));
		assertEquals(new Rewards.Reward(98, 210), Rewards.compute(s, Outcome.WIN, 1, 1.5));
	}

	@Test
	void streaksCountAndResetPerModeAndOverall() {
		PlayerProfile p = new PlayerProfile();
		StatsRecorder.record(p, GameMode.SWORD, false, Outcome.WIN, 1, 0);
		StatsRecorder.record(p, GameMode.BOW, false, Outcome.WIN, 1, 0);
		StatsRecorder.Result r = StatsRecorder.record(p, GameMode.SWORD, true, Outcome.WIN, 1, 0);
		assertEquals(3, r.overallStreak());
		assertEquals(2, r.modeStreak());
		assertTrue(r.newBestOverall());
		assertEquals(1, p.rankedStats(GameMode.SWORD).wins);
		StatsRecorder.record(p, GameMode.SWORD, false, Outcome.LOSS, 0, 1);
		assertEquals(0, p.overall.streak);
		assertEquals(3, p.overall.bestStreak);
		assertEquals(0, p.peek(GameMode.SWORD).streak);
		assertEquals(1, p.peek(GameMode.BOW).streak);
		assertEquals(4, p.overall.played);
		assertEquals(3, p.overall.wins);
		assertEquals(1, p.overall.losses);
		assertEquals(3, p.overall.kills);
		assertEquals(1, p.overall.deaths);
		StatsRecorder.record(p, GameMode.SUMO, false, Outcome.DRAW, 0, 0);
		assertEquals(1, p.overall.draws);
	}

	@Test
	void milestones() {
		assertTrue(StatsRecorder.isMilestone(10, List.of(5, 10, 25)));
		assertFalse(StatsRecorder.isMilestone(11, List.of(5, 10, 25)));
	}

	@Test
	void normalizeRepairsMissingCollections() {
		PlayerProfile p = new PlayerProfile();
		p.modes = null;
		p.elo = null;
		p.overall = null;
		p.level = 0;
		p.normalize();
		assertEquals(1, p.level);
		assertEquals(1000, p.elo(GameMode.MACE, 1000));
		assertEquals(0, p.peek(GameMode.MACE).wins);
	}
}
