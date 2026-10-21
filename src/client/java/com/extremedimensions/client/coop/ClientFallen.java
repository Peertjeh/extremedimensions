package com.extremedimensions.client.coop;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.extremedimensions.coop.Crawl;

/** The players the server says are fallen: drawn crawling, and the player themselves kept to a crawl. */
public final class ClientFallen {
	private ClientFallen() {}

	private static volatile Set<UUID> fallen = Set.of();

	public static void init() {
		Crawl.client = p -> fallen.contains(p.getUUID());
	}

	public static void receive(List<UUID> ids) {
		fallen = new HashSet<>(ids);
	}

	public static boolean fallen(UUID id) {
		return fallen.contains(id);
	}

	public static void reset() {
		fallen = Set.of();
	}
}
