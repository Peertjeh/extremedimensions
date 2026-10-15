package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.battlehub.core.BoardStats.Stat;

class BoardStatsTest {
	private static ModeStats stats(int played, int wins, int kills, int deaths, long coins) {
		ModeStats s = new ModeStats();
		s.played = played;
		s.wins = wins;
		s.kills = kills;
		s.deaths = deaths;
		s.coins = coins;
		return s;
	}

	private static PlayerProfile profile() {
		PlayerProfile p = new PlayerProfile();
		p.modes.put("sword", stats(10, 6, 30, 10, 500));
		p.modes.put("ffa", stats(3, 1, 9, 6, 120));
		p.modes.put("bed_wars", stats(5, 2, 99, 1, 400));
		p.ranked.put("sword", stats(4, 3, 12, 4, 200));
		p.overall = stats(18, 9, 138, 17, 1020);
		p.coinsEarned = 1500;
		p.elo.put("sword", 1300);
		p.elo.put("axe", 1100);
		return p;
	}

	@Test
	void scopes() {
		assertEquals(8, BoardStats.scope("pvp").modes().size());
		assertTrue(BoardStats.scope(" ALL ").everything());
		assertEquals(GameMode.ONE_V_TWO, BoardStats.scope("1v2").modes().get(0));
		assertNull(BoardStats.scope("nope"));
		assertNull(BoardStats.scope(null));
	}

	@Test
	void statNames() {
		assertEquals(Stat.KD, Stat.byId("K/D"));
		assertEquals(Stat.KD, Stat.byId("kdr"));
		assertEquals(Stat.COINS, Stat.byId("coins_earned"));
		assertEquals(Stat.ELO, Stat.byId("Elo"));
		assertNull(Stat.byId("level"));
	}

	@Test
	void pvpLeavesPartyGamesOut() {
		PlayerProfile p = profile();
		BoardStats.Scope pvp = BoardStats.scope("pvp");
		assertEquals(39, BoardStats.value(p, Stat.KILLS, pvp, false, 10));
		assertEquals(16, BoardStats.value(p, Stat.DEATHS, pvp, false, 10));
		assertEquals(7, BoardStats.value(p, Stat.WINS, pvp, false, 10));
		assertEquals(620, BoardStats.value(p, Stat.COINS, pvp, false, 10));
		// 39 / 16 = 2.4375
		assertEquals(244, BoardStats.value(p, Stat.KD, pvp, false, 10));
	}

	@Test
	void everythingUsesTheOverallCountersAndAllCoins() {
		PlayerProfile p = profile();
		BoardStats.Scope all = BoardStats.scope("all");
		assertEquals(138, BoardStats.value(p, Stat.KILLS, all, false, 10));
		assertEquals(1500, BoardStats.value(p, Stat.COINS, all, false, 10));
		// Ranked "all" adds up the ranked counters instead
		assertEquals(12, BoardStats.value(p, Stat.KILLS, all, true, 10));
		assertEquals(200, BoardStats.value(p, Stat.COINS, all, true, 10));
	}

	@Test
	void kdNeedsEnoughKillsAndSurvivesNoDeaths() {
		PlayerProfile p = new PlayerProfile();
		p.modes.put("axe", stats(2, 2, 9, 0, 0));
		BoardStats.Scope axe = BoardStats.scope("axe");
		assertEquals(0, BoardStats.value(p, Stat.KD, axe, false, 10));
		p.modes.get("axe").kills = 12;
		assertEquals(1200, BoardStats.value(p, Stat.KD, axe, false, 10));
	}

	@Test
	void eloIsTheBestRatingOfTheScope() {
		PlayerProfile p = profile();
		assertEquals(1300, BoardStats.value(p, Stat.ELO, BoardStats.scope("all"), true, 10));
		assertEquals(1100, BoardStats.value(p, Stat.ELO, BoardStats.scope("axe"), true, 10));
		assertEquals(Long.MIN_VALUE, BoardStats.value(p, Stat.ELO, BoardStats.scope("bow"), true, 10));
		assertEquals(Long.MIN_VALUE, Stat.ELO.floor());
	}

	@Test
	void ranksPlayersOnTheBoard() {
		PlayerProfile a = profile();
		PlayerProfile b = new PlayerProfile();
		b.modes.put("mace", stats(1, 0, 50, 2, 10));
		PlayerProfile c = new PlayerProfile();
		BoardStats.Scope pvp = BoardStats.scope("pvp");
		var rows = Leaderboard.top(java.util.List.of(a, b, c), p -> BoardStats.value(p, Stat.KILLS, pvp, false, 10), 10, Stat.KILLS.floor());
		assertEquals(2, rows.size());
		assertEquals(b, rows.get(0).item());
		assertEquals(39, rows.get(1).value());
	}

	@Test
	void formatting() {
		assertEquals("2.50", BoardStats.format(Stat.KD, 250));
		assertEquals("0.05", BoardStats.format(Stat.KD, 5));
		assertEquals("1,234,567", BoardStats.format(Stat.COINS, 1234567));
		assertEquals("42", BoardStats.format(Stat.KILLS, 42));
	}

	@Test
	void coinsEarnedAreCounted() {
		PlayerProfile p = new PlayerProfile();
		Progression.addCoins(p, 75);
		Progression.addCoins(p, -50);
		assertEquals(25, p.coins);
		assertEquals(75, p.coinsEarned);
		StatsRecorder.recordCoins(p, GameMode.SWORD, true, 75);
		assertEquals(75, p.peek(GameMode.SWORD).coins);
		assertEquals(75, p.rankedStats(GameMode.SWORD).coins);
		assertEquals(75, p.overall.coins);
		LevelCurve curve = new LevelCurve(100, 0, 1.0, 100);
		Progression.addXp(p, 500, curve, 5, 100);
		assertEquals(175, p.coinsEarned);
	}

	@Test
	void oldProfilesGetAnEstimateOnce() {
		RewardSettings duel = new RewardSettings(50, 10, 5, 0, 0, 0);
		RewardSettings party = new RewardSettings(75, 15, 5, 0, 0, 0);
		RewardSettings ranked = new RewardSettings(80, 15, 5, 0, 0, 0);
		PlayerProfile p = new PlayerProfile();
		p.version = 1;
		p.coins = 40;
		// 10 sword matches of which 4 ranked
		p.modes.put("sword", stats(10, 6, 20, 5, 0));
		p.ranked.put("sword", stats(4, 2, 5, 1, 0));
		p.modes.put("ffa", stats(2, 1, 6, 3, 0));
		p.modes.put("parkour", stats(5, 5, 0, 0, 0));
		CoinHistory.estimate(p, duel, party, ranked, 500);
		// ranked: 4*15 + 2*80 + 5*5 = 245; casual: 6*10 + 4*50 + 15*5 = 335
		assertEquals(245, p.ranked.get("sword").coins);
		assertEquals(580, p.modes.get("sword").coins);
		// ffa: 2*15 + 1*75 + 6*5 = 135
		assertEquals(135, p.modes.get("ffa").coins);
		assertEquals(0, p.modes.get("parkour").coins);
		assertEquals(715, p.overall.coins);
		// at least what they have plus what they spent
		assertEquals(715, p.coinsEarned);
		assertEquals(2, p.version);
		p.coinsEarned = 1;
		CoinHistory.estimate(p, duel, party, ranked, 500);
		assertEquals(1, p.coinsEarned);

		PlayerProfile rich = new PlayerProfile();
		rich.version = 1;
		rich.coins = 900;
		CoinHistory.estimate(rich, duel, party, ranked, 600);
		assertEquals(1500, rich.coinsEarned);
		assertTrue(!CoinHistory.needsEstimate(new PlayerProfile()));
	}
}
