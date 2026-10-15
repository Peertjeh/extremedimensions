package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RetentionTest {
	private static final List<QuestDef> POOL = List.of(
			new QuestDef("d1", "daily", "play", "", 3, 50, 50, 100),
			new QuestDef("d2", "daily", "win", "", 2, 80, 80, 150),
			new QuestDef("d3", "daily", "kill", "", 10, 60, 60, 120),
			new QuestDef("d4", "daily", "win", "sumo", 1, 70, 70, 120),
			new QuestDef("d5", "daily", "damage", "", 40, 60, 60, 120),
			new QuestDef("w1", "weekly", "win", "", 15, 400, 400, 800),
			new QuestDef("w2", "weekly", "ranked_win", "", 5, 500, 500, 900));

	@Test
	void questsArePickedPerPlayerAndDay() {
		UUID a = UUID.nameUUIDFromBytes("a".getBytes());
		UUID b = UUID.nameUUIDFromBytes("b".getBytes());
		List<QuestDef> today = Quests.pick(POOL, false, a, 20000, 3);
		assertEquals(3, today.size());
		assertEquals(today, Quests.pick(POOL, false, a, 20000, 3));
		assertTrue(today.stream().noneMatch(QuestDef::weekly));
		assertEquals(2, Quests.pick(POOL, true, a, 2857, 3).size());
		boolean differs = false;
		for (long d = 20000; d < 20010; d++) {
			if (!Quests.pick(POOL, false, a, d, 3).equals(Quests.pick(POOL, false, b, d, 3))) differs = true;
		}
		assertTrue(differs);
	}

	@Test
	void matchesMoveQuestsForward() {
		QuestDef win = POOL.get(1);
		QuestDef sumo = POOL.get(3);
		QuestDef dmg = POOL.get(4);
		QuestProgress p = new QuestProgress("d2");
		Quests.Match lost = new Quests.Match(GameMode.SWORD, false, false, 2, 6.5, false, false);
		Quests.Match won = new Quests.Match(GameMode.SUMO, true, false, 0, 0, false, false);
		assertFalse(Quests.advance(p, win, lost));
		assertFalse(Quests.advance(p, win, won));
		assertTrue(Quests.advance(p, win, won));
		assertTrue(p.done);
		assertFalse(Quests.advance(p, win, won));
		assertEquals(0, Quests.amount(sumo, lost));
		assertEquals(1, Quests.amount(sumo, won));
		assertEquals(6, Quests.amount(dmg, lost));
		// Weeks start on Monday: 2026-10-12 (Mon) and 2026-10-18 (Sun) share a week, 10-19 does not
		long mon = java.time.LocalDate.of(2026, 10, 12).toEpochDay();
		assertEquals(Quests.week(mon), Quests.week(mon + 6));
		assertNotEquals(Quests.week(mon), Quests.week(mon + 7));
	}

	@Test
	void achievementsHaveTiers() {
		PlayerProfile p = new PlayerProfile();
		p.overall.kills = 120;
		assertEquals(2, Achievement.FIGHTER.tier(p));
		p.overall.kills = 5;
		assertEquals(0, Achievement.FIGHTER.tier(p));
		p.questsDone = 55;
		assertEquals(2, Achievement.QUESTER.tier(p));
		assertEquals(400, Achievement.reward(2, 100));
	}

	@Test
	void loginPassPrestige() {
		assertEquals(1, Daily.nextStreak(0, -1, 100));
		assertEquals(4, Daily.nextStreak(3, 99, 100));
		assertEquals(1, Daily.nextStreak(3, 97, 100));
		assertEquals(3, Daily.nextStreak(3, 100, 100));
		List<Integer> steps = List.of(50, 75, 100, 150, 200, 250, 300);
		assertEquals(50, Daily.loginCoins(1, steps));
		assertEquals(300, Daily.loginCoins(12, steps));
		assertEquals(0, Daily.passTier(999, 1000, 50));
		assertEquals(7, Daily.passTier(7500, 1000, 50));
		assertEquals(50, Daily.passTier(999999, 1000, 50));
		assertEquals(0.1, Daily.prestigeBonus(2), 1e-9);
		assertEquals(0.5, Daily.prestigeBonus(20), 1e-9);
		assertEquals("✦✦", Daily.prestigeStars(2));
		assertEquals("✦7", Daily.prestigeStars(7));
	}
}
