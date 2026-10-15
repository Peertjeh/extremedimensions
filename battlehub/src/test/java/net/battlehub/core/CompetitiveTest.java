package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CompetitiveTest {
	@Test
	void seasons() {
		long anchor = 20000;
		assertEquals(1, Season.number(19990, anchor, 56));
		assertEquals(1, Season.number(20055, anchor, 56));
		assertEquals(2, Season.number(20056, anchor, 56));
		assertEquals(20056, Season.start(2, anchor, 56));
		assertEquals(1, Season.daysLeft(20055, anchor, 56));
		assertEquals(56, Season.daysLeft(20056, anchor, 56));
		assertEquals(1200, Season.softReset(1400, 1000, 0.5));
		assertEquals(900, Season.softReset(800, 1000, 0.5));
		assertEquals(64, Season.kFactor(32, 2, 5));
		assertEquals(32, Season.kFactor(32, 5, 5));
	}

	@Test
	void divisions() {
		RankTiers t = new RankTiers(RankTiers.defaults());
		// Silver 1100-1249: three parts of 50
		assertEquals(3, Season.division(t, 1100, 100));
		assertEquals(2, Season.division(t, 1160, 100));
		assertEquals(1, Season.division(t, 1240, 100));
		// Netherite has no ceiling: steps of 100
		assertEquals(3, Season.division(t, 1700, 100));
		assertEquals(1, Season.division(t, 2500, 100));
		assertEquals("II", Season.roman(2));
		assertEquals(0, Season.tierIndex(t, 900));
		assertEquals(4, Season.tierIndex(t, 1800));
	}

	@Test
	void recap() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		MatchRecap r = new MatchRecap();
		r.swing(a);
		r.swing(a);
		r.swing(a);
		r.swing(a);
		r.hit(a, b, 6, false);
		r.hit(a, b, 6, false);
		r.hit(a, b, 6, false);
		r.hit(b, a, 4, false);
		r.hit(a, b, 7, false);
		r.shot(b);
		r.shot(b);
		r.hit(b, a, 5, true);
		r.kill(a);
		assertEquals(25, r.line(a).dealt, 1e-9);
		assertEquals(9, r.line(a).taken, 1e-9);
		assertEquals(100, r.line(a).accuracy());
		assertEquals(3, r.line(a).bestCombo);
		assertEquals(50, r.line(b).bowAccuracy());
		assertEquals(a, r.mvp(List.of(a, b)));
		assertNull(new MatchRecap().mvp(List.of(a)));
	}

	@Test
	void penalties() {
		List<Integer> steps = List.of(30, 120, 300, 900);
		assertEquals(0, Penalties.cooldownSeconds(0, steps));
		assertEquals(30, Penalties.cooldownSeconds(1, steps));
		assertEquals(300, Penalties.cooldownSeconds(3, steps));
		assertEquals(900, Penalties.cooldownSeconds(9, steps));
		assertEquals(2, Penalties.stillCounting(2, 0, 1000));
		assertEquals(0, Penalties.stillCounting(2, 0, Penalties.DAY_MS + 1));
	}

	@Test
	void clickRate() {
		ClickRate c = new ClickRate(20);
		for (int t = 0; t < 20; t++) {
			c.click(t);
		}
		assertEquals(20.0, c.perSecond(19), 1e-9);
		assertEquals(0.0, c.perSecond(100), 1e-9);
	}

	@Test
	void kitLayouts() {
		List<Integer> kit = List.of(0, 1, 2, 8);
		assertEquals(1, KitLayout.slotFor(1, kit, null));
		List<Integer> l = KitLayout.swap(kit, null, 0, 1);
		assertEquals(List.of(1, 0, 2, 8), l);
		assertEquals(0, KitLayout.slotFor(1, kit, l));
		l = KitLayout.swap(kit, l, 8, 5);
		assertEquals(5, KitLayout.slotFor(8, kit, l));
		assertFalse(KitLayout.valid(kit, List.of(0, 0, 2, 8)));
		assertTrue(KitLayout.valid(kit, l));
		assertEquals(2, KitLayout.slotFor(2, kit, List.of(1, 2)));
		assertEquals(8, (int) KitLayout.bySlot(kit, l).get(5));
	}

	@Test
	void closingASeason() {
		RankTiers tiers = new RankTiers(RankTiers.defaults());
		java.util.Map<String, Integer> rewards = java.util.Map.of("gold", 500, "diamond", 1000);
		PlayerProfile p = new PlayerProfile();
		p.season = 1;
		p.elo.put("sword", 1400);
		p.elo.put("axe", 1100);
		SeasonRules.played(p, GameMode.SWORD, 1380);
		SeasonRules.played(p, GameMode.SWORD, 1460);
		SeasonRules.played(p, GameMode.SWORD, 1400);
		SeasonRules.played(p, GameMode.AXE, 1100);
		assertEquals(3, SeasonRules.playedThisSeason(p, GameMode.SWORD));
		SeasonRecord r = SeasonRules.close(p, 1, 2, tiers, 1000, 0.5, rewards, 100);
		assertEquals(1460, r.peakElo);
		assertEquals("sword", r.mode);
		assertEquals("diamond", r.tier);
		assertEquals(3, r.tierIndex);
		assertEquals(1000, r.coins);
		assertEquals(4, r.matches);
		assertEquals(3, p.bestSeasonTier);
		assertEquals(1200, (int) p.elo.get("sword"));
		assertEquals(1050, (int) p.elo.get("axe"));
		assertTrue(p.seasonPlayed.isEmpty());
		assertEquals(2, p.season);
		// A season without ranked matches leaves no record but still resets the rating
		assertNull(SeasonRules.close(p, 2, 3, tiers, 1000, 0.5, rewards, 100));
		assertEquals(1100, (int) p.elo.get("sword"));
		TitleDef title = new TitleDef("s", "S", "season", 3, "");
		assertTrue(TitleRules.unlocked(title, p, 1000));
		assertFalse(TitleRules.unlocked(new TitleDef("t", "T", "season", 4, ""), p, 1000));
	}
}
