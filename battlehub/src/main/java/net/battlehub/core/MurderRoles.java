package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Murder Mystery: who is the murderer and who the detective (two different players, at random). */
public final class MurderRoles {
	private MurderRoles() {}

	public enum Role { MURDERER, DETECTIVE, INNOCENT }

	public record Roles(UUID murderer, UUID detective) {
		public Role of(UUID id) {
			return id.equals(murderer) ? Role.MURDERER : id.equals(detective) ? Role.DETECTIVE : Role.INNOCENT;
		}
	}

	public static Roles pick(List<UUID> players, Random rnd) {
		if (players.size() < 2) {
			throw new IllegalArgumentException("Murder Mystery needs two players or more");
		}
		List<UUID> l = new ArrayList<>(players);
		java.util.Collections.shuffle(l, rnd);
		return new Roles(l.get(0), l.get(1));
	}

	/**
	 * Who won once {@code alive} players are left: the innocents when the murderer is gone, the murderer when
	 * nobody else is left, nobody yet otherwise.
	 */
	public static Role winner(Roles roles, java.util.Collection<UUID> alive) {
		if (!alive.contains(roles.murderer())) {
			return Role.INNOCENT;
		}
		return alive.size() <= 1 ? Role.MURDERER : null;
	}
}
