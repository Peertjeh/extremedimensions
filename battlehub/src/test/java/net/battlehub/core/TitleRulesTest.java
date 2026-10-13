package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TitleRulesTest {
	private final TitleDef rookie = new TitleDef("rookie", "&7Rookie", "default", 0, "");
	private final TitleDef unstoppable = new TitleDef("unstoppable", "&cUnstoppable", "streak", 10, "");
	private final TitleDef sumoKing = new TitleDef("sumo_king", "&eSumo King", "mode_wins", 3, "sumo");
	private final TitleDef rich = new TitleDef("rich", "&6Rich", "coins", 500, "");
	private final TitleDef netherite = new TitleDef("netherite", "&8Netherite", "elo", 1700, "");

	@Test
	void requirementsAreChecked() {
		PlayerProfile p = new PlayerProfile();
		assertTrue(TitleRules.unlocked(rookie, p, 1000));
		assertFalse(TitleRules.unlocked(unstoppable, p, 1000));
		p.overall.bestStreak = 10;
		assertTrue(TitleRules.unlocked(unstoppable, p, 1000));
		p.mode(GameMode.SUMO).wins = 3;
		assertTrue(TitleRules.unlocked(sumoKing, p, 1000));
		assertFalse(TitleRules.unlocked(netherite, p, 1000));
		p.setElo(GameMode.BOW, 1750);
		assertTrue(TitleRules.unlocked(netherite, p, 1000));
	}

	@Test
	void newlyUnlockedIsAnnouncedOnce() {
		PlayerProfile p = new PlayerProfile();
		List<TitleDef> all = List.of(rookie, unstoppable);
		assertTrue(TitleRules.newlyUnlocked(all, p, 1000).isEmpty(), "default titles are not announced");
		p.overall.bestStreak = 12;
		assertEquals(List.of(unstoppable), TitleRules.newlyUnlocked(all, p, 1000));
		assertTrue(TitleRules.newlyUnlocked(all, p, 1000).isEmpty());
	}

	@Test
	void buyingTitles() {
		PlayerProfile p = new PlayerProfile();
		p.coins = 400;
		assertEquals(TitleRules.BuyResult.TOO_EXPENSIVE, TitleRules.buy(rich, p));
		p.coins = 600;
		assertEquals(TitleRules.BuyResult.BOUGHT, TitleRules.buy(rich, p));
		assertEquals(100, p.coins);
		assertEquals(TitleRules.BuyResult.ALREADY_OWNED, TitleRules.buy(rich, p));
		assertEquals(TitleRules.BuyResult.NOT_FOR_SALE, TitleRules.buy(rookie, p));
		assertTrue(TitleRules.select(rich, p, 1000));
		assertEquals("rich", p.title);
		assertFalse(TitleRules.select(unstoppable, p, 1000));
		assertTrue(TitleRules.select(null, p, 1000));
		assertEquals("", p.title);
	}
}
