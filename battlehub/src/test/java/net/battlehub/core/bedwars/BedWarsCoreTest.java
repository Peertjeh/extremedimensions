package net.battlehub.core.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BedWarsCoreTest {
	private static UUID u(int n) {
		return new UUID(1, n);
	}

	@Test
	void generatorDropsOnItsIntervalAndRespectsTheCap() {
		GeneratorClock g = new GeneratorClock(new int[] {4, 2}, 1, 3);
		int dropped = 0;
		for (int t = 0; t < 12; t++) dropped += g.tick(0);
		assertEquals(3, dropped, "every 4 ticks at tier 1");
		g.setTier(2);
		assertEquals(2, g.tier());
		dropped = 0;
		for (int t = 0; t < 12; t++) dropped += g.tick(0);
		assertEquals(6, dropped, "every 2 ticks at tier 2");
		g.setTier(9);
		assertEquals(2, g.tier(), "no tier above the last");
		int full = 0;
		for (int t = 0; t < 12; t++) full += g.tick(3);
		assertEquals(0, full, "full generator drops nothing");
	}

	@Test
	void scheduleFiresEachEventOnceInOrder() {
		EventSchedule s = new EventSchedule(List.of(new EventSchedule.Event(600, "sudden_death", 0),
				new EventSchedule.Event(300, "diamond", 2), new EventSchedule.Event(300, "emerald", 2)));
		assertEquals("diamond", s.upcoming().type());
		assertTrue(s.due(299).isEmpty());
		assertEquals(2, s.due(300).size());
		assertTrue(s.due(301).isEmpty(), "never twice");
		assertEquals("sudden_death", s.upcoming().type());
		assertEquals(1, s.due(10_000).size());
		assertNull(s.upcoming());
	}

	@Test
	void bedsDecideRespawnsAndTheWinner() {
		TeamState red = new TeamState(0, List.of(u(1), u(2)));
		TeamState blue = new TeamState(1, List.of(u(3)));
		List<TeamState> teams = List.of(red, blue);
		assertTrue(blue.died(u(3)), "bed stands: back again");
		assertFalse(BedWarsRules.over(teams));
		assertTrue(blue.destroyBed());
		assertFalse(blue.destroyBed(), "only once");
		assertFalse(blue.died(u(3)), "no bed: out");
		assertTrue(blue.isOut());
		assertSame(red, BedWarsRules.winner(teams));
		red.left(u(1));
		assertEquals(List.of(u(2)), red.playing());
		assertFalse(red.died(u(1)), "someone who left does not come back");
	}

	@Test
	void timeUpTieBreaks() {
		TeamState a = new TeamState(0, List.of(u(1), u(2)));
		TeamState b = new TeamState(1, List.of(u(3), u(4)));
		assertNull(BedWarsRules.onTimeUp(List.of(a, b)), "even: a draw");
		b.destroyBed();
		assertSame(a, BedWarsRules.onTimeUp(List.of(a, b)), "same players, a still has its bed");
		a.destroyBed();
		b.addFinalKill();
		assertSame(b, BedWarsRules.onTimeUp(List.of(a, b)), "then final kills");
		a.died(u(1));
		assertSame(b, BedWarsRules.onTimeUp(List.of(a, b)));
	}

	@Test
	void teamCounts() {
		assertEquals(2, BedWarsRules.teamCount(2, 0, 4));
		assertEquals(2, BedWarsRules.teamCount(4, 0, 4));
		assertEquals(3, BedWarsRules.teamCount(6, 0, 4));
		assertEquals(4, BedWarsRules.teamCount(16, 0, 4));
		assertEquals(4, BedWarsRules.teamCount(9, 0, 2), "at most 4 teams");
		assertEquals(3, BedWarsRules.teamCount(3, 4, 4), "never more teams than players");
		assertEquals(2, BedWarsRules.teamCount(8, 2, 4));
	}

	/** A wallet with a resource count per currency and a fixed number of free slots. */
	private static final class FakeWallet implements Shop.Wallet {
		final Map<Resource, Integer> money = new EnumMap<>(Resource.class);
		int freeSlots;
		final List<String> goods = new ArrayList<>();

		@Override
		public int count(Resource c) {
			return money.getOrDefault(c, 0);
		}

		@Override
		public boolean take(Resource c, int amount) {
			if (count(c) < amount) return false;
			money.put(c, count(c) - amount);
			return true;
		}

		@Override
		public boolean canFit(Shop.Offer o) {
			return o.isUpgrade() || freeSlots > 0;
		}

		@Override
		public void give(Shop.Offer o) {
			goods.add(o.id());
			if (!o.isUpgrade()) freeSlots--;
		}
	}

	@Test
	void purchasesAreAllOrNothing() {
		Shop.Offer wool = new Shop.Offer("wool", "blocks", "wool", 16, Resource.IRON, 4, "", 0);
		FakeWallet w = new FakeWallet();
		w.money.put(Resource.IRON, 10);
		w.freeSlots = 5;
		Map<String, Integer> tiers = new HashMap<>();
		assertEquals(Shop.Result.BOUGHT, Shop.buy(wool, w, tiers));
		assertEquals(Shop.Result.BOUGHT, Shop.buy(wool, w, tiers));
		assertEquals(Shop.Result.TOO_EXPENSIVE, Shop.buy(wool, w, tiers), "2 iron left");
		assertEquals(2, w.count(Resource.IRON));
		assertEquals(2, w.goods.size());
		w.money.put(Resource.IRON, 100);
		w.freeSlots = 0;
		assertEquals(Shop.Result.NO_SPACE, Shop.buy(wool, w, tiers));
		assertEquals(100, w.count(Resource.IRON), "nothing paid without room");
	}

	@Test
	void upgradesOnlyGoUp() {
		Shop.Offer iron = new Shop.Offer("iron_armor", "armor", "iron", 1, Resource.GOLD, 12, "armor", 2);
		Shop.Offer chain = new Shop.Offer("chain_armor", "armor", "chain", 1, Resource.IRON, 24, "armor", 1);
		FakeWallet w = new FakeWallet();
		w.money.put(Resource.GOLD, 30);
		w.money.put(Resource.IRON, 30);
		Map<String, Integer> tiers = new HashMap<>();
		assertEquals(Shop.Result.BOUGHT, Shop.buy(iron, w, tiers));
		assertEquals(Shop.Result.ALREADY_OWNED, Shop.buy(iron, w, tiers));
		assertEquals(Shop.Result.ALREADY_OWNED, Shop.buy(chain, w, tiers), "no step down");
		assertEquals(18, w.count(Resource.GOLD));
		assertEquals(30, w.count(Resource.IRON));
	}

	@Test
	void spammingNeverOverspends() {
		Shop.Offer apple = new Shop.Offer("apple", "utility", "apple", 1, Resource.GOLD, 3, "", 0);
		FakeWallet w = new FakeWallet();
		w.money.put(Resource.GOLD, 10);
		w.freeSlots = 100;
		int bought = 0;
		for (int i = 0; i < 50; i++) {
			if (Shop.buy(apple, w, new HashMap<>()) == Shop.Result.BOUGHT) bought++;
		}
		assertEquals(3, bought);
		assertEquals(1, w.count(Resource.GOLD));
		assertEquals(Resource.GOLD, Resource.byItem("minecraft:gold_ingot"));
	}
}
