package com.extremedimensions.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * Test players. The game's own mock player always counts as creative, and curses rightly leave creative players
 * alone, so the curse tests use a survival player joined the same way (a real server-side connection to nowhere).
 */
final class TestPlayers {
	private TestPlayers() {}

	static ServerPlayer survivor(GameTestHelper helper) {
		return survivor(helper, "test-cursed");
	}

	/** A survival test player by another name (players by one name are never allies of each other, see Allies). */
	static ServerPlayer survivor(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation()) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return false;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}
}
