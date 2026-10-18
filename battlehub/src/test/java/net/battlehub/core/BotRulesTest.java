package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BotRulesTest {
	private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), BOT1 = UUID.randomUUID(), BOT2 = UUID.randomUUID();
	private static final Set<UUID> BOTS = Set.of(BOT1, BOT2);

	private static BotRules.Count count(UUID... ids) {
		return BotRules.count(new ArrayList<>(List.of(ids)), BOTS::contains);
	}

	@Test
	void countsPeopleAndBots() {
		BotRules.Count c = count(A, BOT1, BOT2);
		assertEquals(1, c.humans());
		assertEquals(2, c.bots());
	}

	@Test
	void aloneAgainstBotsEarnsNothing() {
		// 1v1 duel against a bot, 1v3 team fight against bots, a party lobby filled with bots
		for (BotRules.Count c : List.of(count(A, BOT1), count(A, BOT1, BOT2), count(BOT1, BOT2))) {
			assertTrue(c.alone());
			assertEquals(0.0, BotRules.rewardFactor(c, 0.5));
			assertEquals(0.0, BotRules.rewardFactor(c, 1.0), "the configured share never overrides the rule");
			assertFalse(BotRules.paysPrizes(c));
		}
	}

	@Test
	void otherPeoplePlusBotsEarnTheShare() {
		BotRules.Count c = count(A, B, BOT1);
		assertFalse(c.alone());
		assertEquals(0.5, BotRules.rewardFactor(c, 0.5));
		assertEquals(1.0, BotRules.rewardFactor(c, 3.0));
		assertEquals(0.0, BotRules.rewardFactor(c, -1.0));
		assertTrue(BotRules.paysPrizes(c));
	}

	@Test
	void noBotsPaysEverything() {
		BotRules.Count c = count(A, B);
		assertFalse(c.alone());
		assertEquals(1.0, BotRules.rewardFactor(c, 0.5));
		assertTrue(BotRules.paysPrizes(c));
		// a solo practice run without bots is not "alone against bots"
		assertFalse(count(A).alone());
	}
}
