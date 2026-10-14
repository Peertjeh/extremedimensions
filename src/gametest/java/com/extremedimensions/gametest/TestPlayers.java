package com.extremedimensions.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
		ServerLevel level = helper.getLevel();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "test-cursed");
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

	/** Ends the grace a freshly joined player has (spawn protection and waiting for the client to load). */
	static void vulnerable(ServerPlayer p) {
		try {
			Method loaded = findMethod(p.getClass(), "setClientLoaded", boolean.class);
			if (loaded != null) {
				loaded.setAccessible(true);
				loaded.invoke(p, true);
			}
			Field spawn = findField(p.getClass(), "spawnInvulnerableTime");
			if (spawn != null) {
				spawn.setAccessible(true);
				spawn.setInt(p, 0);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not end the spawn grace of a test player", e);
		}
	}

	private static Method findMethod(Class<?> c, String name, Class<?>... args) {
		for (; c != null; c = c.getSuperclass()) {
			try {
				return c.getDeclaredMethod(name, args);
			} catch (NoSuchMethodException e) {
				// keep looking up
			}
		}
		return null;
	}

	private static Field findField(Class<?> c, String name) {
		for (; c != null; c = c.getSuperclass()) {
			try {
				return c.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				// keep looking up
			}
		}
		return null;
	}
}
