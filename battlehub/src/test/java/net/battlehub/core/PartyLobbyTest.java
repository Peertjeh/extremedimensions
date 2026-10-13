package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PartyLobbyTest {
	private static UUID id(int n) {
		return new UUID(0, n);
	}

	private static PartyLobby.Tick runUntil(PartyLobby l, PartyLobby.Event e, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			PartyLobby.Tick t = l.tick();
			if (t.event() == e) {
				return t;
			}
		}
		return null;
	}

	@Test
	void startsAfterCountdownOnceMinimumIsReached() {
		PartyLobby l = new PartyLobby(2, 4, 10, 3);
		assertEquals(PartyLobby.JoinResult.JOINED, l.join(id(1)));
		assertEquals(PartyLobby.Event.NONE, l.tick().event());
		l.join(id(2));
		PartyLobby.Tick started = l.tick();
		assertEquals(PartyLobby.Event.COUNTDOWN_STARTED, started.event());
		assertEquals(10, started.secondsLeft());
		PartyLobby.Tick go = runUntil(l, PartyLobby.Event.START, 10 * 20 + 5);
		assertTrue(go != null);
		assertEquals(2, go.players().size());
		assertEquals(0, l.size(), "lobby is emptied for the next round");
	}

	@Test
	void fullLobbyShortensCountdown() {
		PartyLobby l = new PartyLobby(2, 3, 30, 5);
		l.join(id(1));
		l.join(id(2));
		l.tick();
		l.join(id(3));
		assertEquals(PartyLobby.JoinResult.FULL, l.join(id(4)));
		PartyLobby.Tick go = runUntil(l, PartyLobby.Event.START, 5 * 20 + 2);
		assertTrue(go != null, "should start within 5 seconds when full");
		assertEquals(3, go.players().size());
	}

	@Test
	void droppingBelowMinimumCancels() {
		PartyLobby l = new PartyLobby(2, 8, 10, 5);
		l.join(id(1));
		l.join(id(2));
		l.tick();
		for (int i = 0; i < 40; i++) l.tick();
		l.leave(id(2));
		assertEquals(PartyLobby.Event.CANCELLED, l.tick().event());
		assertEquals(-1, l.secondsLeft());
		assertEquals(PartyLobby.JoinResult.ALREADY, l.join(id(1)));
	}

	@Test
	void announcesWholeSeconds() {
		PartyLobby l = new PartyLobby(1, 2, 3, 3);
		l.join(id(1));
		l.tick();
		int announcements = 0;
		for (int i = 0; i < 80; i++) {
			PartyLobby.Tick t = l.tick();
			if (t.event() == PartyLobby.Event.COUNTDOWN) announcements++;
			if (t.event() == PartyLobby.Event.START) break;
		}
		assertEquals(2, announcements, "2 and 1 seconds left");
	}
}
