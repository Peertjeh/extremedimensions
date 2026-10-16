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

	/** The hotbar slot this player's client holds. */
	public int clientSlot() {
		return link.heldSlot;
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
		} catch (RuntimeException ignored) {
			// Some client-driven state is missing for simulated players; physics still runs.
		}
	}
}
