package com.extremedimensions.coop;

import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Who stands together. By default everyone is an ally of everyone who is not on another scoreboard team; with
 * {@code coop_allies} at 1 only players on the same team are allies. Teams are looked up by name, so it also holds
 * for a player who is away (by the name they last played under).
 */
public final class Allies {
	private Allies() {}

	private static MinecraftServer server;

	/** Hooks the anchors up to the allies (whose charge may be drawn on by whom). */
	public static void init(MinecraftServer s) {
		server = s;
		Anchor.allied = (a, b) -> allied(nameOf(a), nameOf(b));
		Anchor.open = owner -> {
			if (Config.COOP_SHARED_ANCHORS <= 0) {
				return false;
			}
			PlayerData d = data(owner);
			return d != null && d.shareAnchors;
		};
	}

	/** On stopping: lets go of the server (a single-player world closed is not kept in memory). */
	public static void clear() {
		server = null;
	}

	/** The player opens their anchors to their allies (or closes them again). */
	public static void share(ServerPlayer p, boolean on) {
		if (Config.COOP_SHARED_ANCHORS <= 0) {
			p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.share.disabled"), true);
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		d.shareAnchors = on;
		p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(on ? "gui.extremedimensions.share.on" : "gui.extremedimensions.share.off"));
		DataStore.markDirty();
	}

	public static boolean allied(ServerPlayer a, ServerPlayer b) {
		return a != b && allied(a.getScoreboardName(), b.getScoreboardName());
	}

	/** Whether two players, by name, are allies (unknown names never are). */
	public static boolean allied(String a, String b) {
		if (a == null || b == null || a.equals(b) || server == null) {
			return false;
		}
		Scoreboard board = server.getScoreboard();
		PlayerTeam ta = board.getPlayersTeam(a), tb = board.getPlayersTeam(b);
		if (Config.COOP_ALLIES >= 1) {
			return ta != null && ta == tb;
		}
		return ta == null || tb == null || ta == tb;
	}

	private static PlayerData data(String uid) {
		try {
			return DataStore.peek(UUID.fromString(uid));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A player's name by UUID: as they are online now, else as they last played. */
	private static String nameOf(String uid) {
		if (server != null) {
			try {
				ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(uid));
				if (p != null) {
					return p.getScoreboardName();
				}
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		PlayerData d = data(uid);
		return d == null ? null : d.name;
	}
}
