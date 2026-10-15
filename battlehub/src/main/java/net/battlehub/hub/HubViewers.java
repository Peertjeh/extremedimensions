package net.battlehub.hub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.battlehub.bot.Bots;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which players' clients have one set of the hub's client-only entities (the leaderboards, the NPCs). A new
 * player object (respawn) or another level means a new client world, so the set is sent again. Bots get nothing.
 */
final class HubViewers {
	static final class Viewer {
		final ServerPlayer player;
		final ServerLevel level;
		/** Last text sent per entity id, so only changes go out. */
		final Map<Integer, Component> sent = new HashMap<>();

		Viewer(ServerPlayer player, ServerLevel level) {
			this.player = player;
			this.level = level;
		}
	}

	private final Map<UUID, Viewer> viewers = new HashMap<>();

	/**
	 * Brings the list up to date and returns the players who need everything sent. {@code remove} takes the
	 * entities away from a player who is still in the hub but should no longer have them.
	 */
	List<Viewer> update(MinecraftServer server, ServerLevel hub, boolean active, Consumer<ServerPlayer> remove) {
		Set<UUID> online = new HashSet<>();
		List<Viewer> fresh = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			online.add(p.getUUID());
			Viewer v = viewers.get(p.getUUID());
			boolean wants = active && p.level() == hub && !Bots.isBot(p);
			if (v != null && (!wants || v.player != p || v.level != p.level())) {
				if (v.player == p && v.level == p.level()) {
					// Still in the hub: switched off, or the lobby is being rebuilt
					remove.accept(p);
				}
				// Otherwise the client left the hub world (or respawned) and dropped them itself
				viewers.remove(p.getUUID());
				v = null;
			}
			if (wants && v == null) {
				v = new Viewer(p, hub);
				viewers.put(p.getUUID(), v);
				fresh.add(v);
			}
		}
		viewers.keySet().retainAll(online);
		return fresh;
	}

	Collection<Viewer> all() {
		return viewers.values();
	}

	Viewer get(UUID id) {
		return viewers.get(id);
	}

	boolean isEmpty() {
		return viewers.isEmpty();
	}

	/** Takes the entities away from everybody still in the hub and forgets them. */
	void clear(Consumer<ServerPlayer> remove) {
		for (Viewer v : viewers.values()) {
			if (!v.player.hasDisconnected() && v.player.level() == v.level) {
				remove.accept(v.player);
			}
		}
		viewers.clear();
	}

	void forget() {
		viewers.clear();
	}
}
