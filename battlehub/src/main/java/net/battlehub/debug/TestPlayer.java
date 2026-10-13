package net.battlehub.debug;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** A real online player without a client, for the self-test. Ticks its own physics like a connected player. */
public final class TestPlayer extends ServerPlayer {
	private TestPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
		super(server, level, profile, ClientInformation.createDefault());
	}

	public static UUID uuid(String name) {
		return UUID.nameUUIDFromBytes(("BattleHubTest:" + name).getBytes(StandardCharsets.UTF_8));
	}

	public static TestPlayer join(MinecraftServer server, String name) {
		GameProfile profile = new GameProfile(uuid(name), name);
		TestPlayer p = new TestPlayer(server, server.overworld(), profile);
		server.getPlayerList().placeNewPlayer(new TestConnection(), p, new CommonListenerCookie(profile, 0, p.clientInformation(), false));
		return p;
	}

	public void leave() {
		connection.onDisconnect(new DisconnectionDetails(Component.literal("self-test")));
	}

	@Override
	public void tick() {
		super.tick();
		try {
			doTick();
		} catch (RuntimeException ignored) {
			// Some client-driven state is missing for simulated players; physics still runs.
		}
	}
}
