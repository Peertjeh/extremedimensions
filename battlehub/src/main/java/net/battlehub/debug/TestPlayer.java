package net.battlehub.debug;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** A real online player without a client, for the self-test. Ticks its own physics like a connected player. */
public final class TestPlayer extends ServerPlayer {
	private TestConnection link;
	private static final java.util.Set<String> TICK_FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private TestPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
		super(server, level, profile, ClientInformation.createDefault());
	}

	public static UUID uuid(String name) {
		return UUID.nameUUIDFromBytes(("BattleHubTest:" + name).getBytes(StandardCharsets.UTF_8));
	}

	public static TestPlayer join(MinecraftServer server, String name) {
		GameProfile profile = new GameProfile(uuid(name), name);
		TestPlayer p = new TestPlayer(server, server.overworld(), profile);
		p.link = new TestConnection();
		server.getPlayerList().placeNewPlayer(p.link, p, new CommonListenerCookie(profile, 0, p.clientInformation(), false));
		return p;
	}

	/** Ids of the entities this player's client would have now. */
	public java.util.Set<Integer> clientEntities() {
		return link.entities;
	}

	/** The brightness override this player's client got for a display entity, or null. */
	public Integer clientBrightness(int entityId) {
		return link.brightness.get(entityId);
	}

	/** How many arm swings of this entity this player's client was shown. */
	public int clientSwings(int entityId) {
		return link.swings.getOrDefault(entityId, 0);
	}

	/** Whether this player's client shows the entity glowing (false when never told). */
	public boolean clientGlowing(int entityId) {
		return link.glowing.getOrDefault(entityId, false);
	}

	/** The hotbar slot this player's client holds. */
	public int clientSlot() {
		return link.heldSlot;
	}

	/** Whether this player's client got a player's chat message containing the text. */
	public boolean clientChatHas(String text) {
		for (String said : link.chat) {
			if (said.contains(text)) return true;
		}
		return false;
	}

	/** The player picks a hotbar slot on the client (the server hears about it, as with a real client). */
	public void pickSlot(int slot) {
		link.heldSlot = slot;
		getInventory().setSelectedSlot(slot);
	}

	public void leave() {
		connection.onDisconnect(new DisconnectionDetails(Component.literal("self-test")));
	}

	@Override
	public void tick() {
		// A real client confirms it finished loading the world after joining or changing dimension; until
		// then the server keeps the player invulnerable.
		if (!connection.hasClientLoaded()) {
			connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		}
		// A real client also acknowledges a teleport into another dimension; until then the player is
		// "changing dimension" and immune to damage.
		if (isChangingDimension()) {
			hasChangedDimension();
		}
		super.tick();
		try {
			doTick();
		} catch (RuntimeException e) {
			// Some client-driven state is missing for simulated players; physics still runs. Each kind of failure
			// is logged once, so a tick that stops halfway (frozen hurt time, no physics) shows in the log.
			if (TICK_FAILURES.add(e.getClass().getName() + ": " + e.getMessage())) {
				StackTraceElement[] st = e.getStackTrace();
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST {} tick failed: {} at {}", getScoreboardName(), e,
						java.util.Arrays.toString(java.util.Arrays.copyOf(st, Math.min(8, st.length))));
			}
		}
		// The server moves a player's loaded chunks along when the client reports a move; this player has no
		// client, so after a teleport within a world it would keep loading the chunks round the old spot.
		level().getChunkSource().move(this);
	}
}
