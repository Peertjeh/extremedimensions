package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class GameRulesTest {
	private static UUID id(int n) {
		return new UUID(0, n);
	}

	@Test
	void slotsAreReused() {
		SlotAllocator s = new SlotAllocator(2);
		assertEquals(0, s.acquire());
		assertEquals(1, s.acquire());
		assertEquals(-1, s.acquire());
		s.release(0);
		assertEquals(0, s.acquire());
		assertEquals(2, s.busy());
		SlotAllocator r = new SlotAllocator(3);
		assertTrue(r.reserve(0));
		assertFalse(r.reserve(0));
		assertFalse(r.reserve(7));
		assertEquals(1, r.acquire());
	}

	@Test
	void tntRunBlocksDecayOnce() {
		BlockDecay d = new BlockDecay(10);
		assertTrue(d.step(42L, 0));
		assertFalse(d.step(42L, 5), "already scheduled");
		assertTrue(d.collect(9).isEmpty());
		assertEquals(List.of(42L), d.collect(10));
		assertEquals(0, d.pending());
	}

	@Test
	void kothScoresOnlyForALoneKing() {
		KothScore k = new KothScore(1, false);
		k.second(Set.of(id(1)));
		k.second(Set.of(id(1), id(2)));
		k.second(Set.of());
		k.second(Set.of(id(2)));
		k.second(Set.of(id(2)));
		assertEquals(1, k.points(id(1)));
		assertEquals(2, k.points(id(2)));
		assertEquals(id(2), k.leader());
		assertEquals(id(2), k.reached(2));
		assertNull(k.reached(3));
	}

	@Test
	void ctfFullCaptureCycle() {
		CtfGame g = new CtfGame(2);
		g.assign(List.of(id(1), id(2), id(3), id(4)));
		assertEquals(CtfGame.Team.RED, g.teamOf(id(1)));
		assertEquals(CtfGame.Team.BLUE, g.teamOf(id(2)));
		// Red player takes the blue flag
		assertEquals(CtfGame.EventType.PICKED_UP, g.touchFlag(id(1), CtfGame.Team.BLUE).type());
		assertEquals(CtfGame.EventType.NONE, g.touchFlag(id(3), CtfGame.Team.BLUE).type(), "already carried");
		assertEquals(CtfGame.EventType.NONE, g.touchFlag(id(1), CtfGame.Team.RED).type(), "own flag at home");
		// Blue steals the red flag: red cannot score while its own flag is away
		assertEquals(CtfGame.EventType.PICKED_UP, g.touchFlag(id(2), CtfGame.Team.RED).type());
		assertEquals(CtfGame.EventType.NONE, g.touchBase(id(1)).type());
		// Blue carrier dies, red returns its flag, then red scores
		assertEquals(CtfGame.EventType.DROPPED, g.drop(id(2), 100).type());
		assertEquals(CtfGame.FlagState.DROPPED, g.state(CtfGame.Team.RED));
		assertEquals(CtfGame.EventType.RETURNED, g.touchFlag(id(3), CtfGame.Team.RED).type());
		assertEquals(CtfGame.EventType.CAPTURED, g.touchBase(id(1)).type());
		assertEquals(1, g.captures(CtfGame.Team.RED));
		assertEquals(CtfGame.FlagState.HOME, g.state(CtfGame.Team.BLUE));
		g.touchFlag(id(3), CtfGame.Team.BLUE);
		assertEquals(CtfGame.EventType.WON, g.touchBase(id(3)).type());
		assertEquals(CtfGame.Team.RED, g.winner());
	}

	@Test
	void ctfDroppedFlagReturnsAutomatically() {
		CtfGame g = new CtfGame(3);
		g.assign(List.of(id(1), id(2)));
		g.touchFlag(id(1), CtfGame.Team.BLUE);
		g.drop(id(1), 0);
		assertTrue(g.tick(100, 200).isEmpty());
		assertEquals(CtfGame.EventType.AUTO_RETURNED, g.tick(200, 200).get(0).type());
		assertEquals(CtfGame.FlagState.HOME, g.state(CtfGame.Team.BLUE));
	}

	@Test
	void ctfReturnHomeClearsTheCarrier() {
		CtfGame g = new CtfGame(3);
		g.assign(List.of(id(1), id(2)));
		g.touchFlag(id(1), CtfGame.Team.BLUE);
		g.returnHome(CtfGame.Team.BLUE);
		assertEquals(CtfGame.FlagState.HOME, g.state(CtfGame.Team.BLUE));
		assertNull(g.carriedBy(id(1)));
		assertEquals(CtfGame.EventType.NONE, g.touchBase(id(1)).type());
	}

	@Test
	void ctfLeavingCarrierSendsFlagHome() {
		CtfGame g = new CtfGame(3);
		g.assign(List.of(id(1), id(2)));
		g.touchFlag(id(1), CtfGame.Team.BLUE);
		g.removePlayer(id(1));
		assertEquals(CtfGame.FlagState.HOME, g.state(CtfGame.Team.BLUE));
		assertNull(g.leading());
	}

	@Test
	void parkourRankingByTimeThenProgress() {
		ParkourRace r = new ParkourRace(3, 0);
		assertEquals(ParkourRace.Reach.CHECKPOINT, r.reach(id(1), 1, 20));
		assertEquals(ParkourRace.Reach.NOTHING, r.reach(id(1), 1, 25));
		r.reach(id(2), 1, 30);
		r.reach(id(2), 2, 40);
		r.reach(id(3), 1, 10);
		r.reach(id(3), 2, 20);
		assertEquals(ParkourRace.Reach.FINISH, r.reach(id(3), 3, 300));
		assertEquals(300, r.time(id(3)));
		List<UUID> rank = r.ranking(List.of(id(1), id(2), id(3), id(4)));
		assertEquals(List.of(id(3), id(2), id(1), id(4)), rank);
		assertFalse(r.allFinished(List.of(id(1), id(3))));
		assertTrue(r.allFinished(List.of(id(3))));
	}

	@Test
	void borderShrinksLinearly() {
		BorderShrink b = new BorderShrink(100, 10, 200, 900);
		assertEquals(100, b.sizeAt(0), 1e-9);
		assertEquals(100, b.sizeAt(200), 1e-9);
		assertEquals(55, b.sizeAt(650), 1e-9);
		assertEquals(10, b.sizeAt(5000), 1e-9);
		assertTrue(b.shrinking(300));
		assertEquals(0, BorderShrink.outside(4, 4, 0, 0, 10), 1e-9);
		assertEquals(3, BorderShrink.outside(8, -2, 0, 0, 10), 1e-9);
	}

	@Test
	void leaderboardSharesRanksOnTies() {
		List<Leaderboard.Row<String>> rows = Leaderboard.top(List.of("a", "bb", "cc", "ddd", ""), String::length, 3, 0);
		assertEquals(3, rows.size());
		assertEquals("ddd", rows.get(0).item());
		assertEquals(1, rows.get(0).rank());
		assertEquals(2, rows.get(1).rank());
		assertEquals(2, rows.get(2).rank());
	}

	@Test
	void modeIdsRoundTrip() {
		for (GameMode m : GameMode.values()) {
			assertEquals(m, GameMode.byId(m.id()));
		}
		assertNull(GameMode.byId("nope"));
		assertEquals(5, GameMode.duels().length);
		assertEquals(9, GameMode.parties().length);
		assertEquals("1v2", GameMode.ONE_V_TWO.id());
		assertEquals(GameMode.ONE_V_THREE, GameMode.byId("1v3"));
		assertEquals(3, GameMode.ONE_V_THREE.opponents());
		assertTrue(GameMode.BOW.isRanged());
		assertFalse(GameMode.MACE.isRanged());
	}
}
