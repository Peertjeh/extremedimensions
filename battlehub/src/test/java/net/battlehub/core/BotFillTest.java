package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.battlehub.core.BotFill.Wish;

/** How many bots a lobby gets: the standard, the lobby filled, or a number of bots; and in a private game. */
class BotFillTest {
	@Test
	void wishesAreStoredAndReadBack() {
		for (Wish w : List.of(Wish.STANDARD, Wish.FILL, Wish.NONE, Wish.count(3))) assertEquals(w, Wish.parse(w.id()));
		assertEquals(Wish.STANDARD, Wish.parse(""));
		assertEquals(Wish.STANDARD, Wish.parse("lots"));
		assertEquals(Wish.NONE, Wish.parse("0"));
		assertEquals(Wish.count(BotFill.MAX_COUNT), Wish.parse("99"), "at most fifteen by number");
	}

	@Test
	void theMenuButtonGoesRound() {
		Wish w = Wish.STANDARD;
		StringBuilder seen = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			w = w.next(3);
			seen.append(w.id().isEmpty() ? "standard" : w.id()).append(' ');
		}
		assertEquals("1 2 3 fill standard 1 ", seen.toString());
		assertEquals(Wish.FILL, Wish.STANDARD.previous(3));
		assertEquals(Wish.count(3), Wish.FILL.previous(3));
		assertEquals(Wish.STANDARD, Wish.count(1).previous(3));
		assertEquals(Wish.count(3), Wish.count(9).previous(3), "a number above what this game takes comes down to it");
	}

	@Test
	void aPartyLobbyIsFilledToTheBiggestWish() {
		// Bed Wars: 2 to 8 players, the server's standard 4
		BotFill.Target t = BotFill.party(List.of(Wish.STANDARD), 1, 2, 8, 4);
		assertEquals(4, t.size());
		assertFalse(t.now(), "the standard waits for people first");
		t = BotFill.party(List.of(Wish.STANDARD, Wish.count(2)), 2, 2, 8, 4);
		assertEquals(4, t.size(), "two people and two bots");
		assertTrue(t.now(), "asked for: the bots come at once");
		assertEquals(8, BotFill.party(List.of(Wish.count(2), Wish.FILL), 2, 2, 8, 4).size(), "filling the lobby beats a number");
		assertEquals(8, BotFill.party(List.of(Wish.count(15)), 1, 2, 8, 4).size(), "never more than the game takes");
		assertEquals(4, BotFill.party(List.of(Wish.count(1)), 1, 4, 16, 4).size(), "at least the game's minimum, so it can start");
	}

	@Test
	void aPrivateGameGetsItsOwnersBots() {
		assertEquals(0, BotFill.custom(Wish.STANDARD, 2, 8));
		assertEquals(0, BotFill.custom(Wish.NONE, 2, 8));
		assertEquals(3, BotFill.custom(Wish.count(3), 2, 8));
		assertEquals(6, BotFill.custom(Wish.FILL, 2, 8));
		assertEquals(1, BotFill.custom(Wish.count(5), 1, 2), "a private duel: one bot");
		assertEquals(0, BotFill.custom(Wish.FILL, 8, 8), "full already");
	}
}
