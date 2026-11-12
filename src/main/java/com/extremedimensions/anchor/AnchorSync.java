package com.extremedimensions.anchor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.ModPackets.AnchorsPayload.Spot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Tells each player where the anchors keeping their own charge stand, and which kind each is, for the waypoints of the
 * map mods (JourneyMap): on joining, when one is made or charged, when one breaks, and every few seconds besides, since
 * charge drawn off on an expedition, leaked from a cracked anchor or lost on a death can leave an anchor holding none
 * of theirs without anything else happening to it. A list is only sent again when it changed, so all of these are cheap.
 */
public final class AnchorSync {
	private AnchorSync() {}

	/** What each player was last sent. */
	private static final Map<UUID, List<Spot>> SENT = new HashMap<>();

	/** The anchors keeping at least a second of this player's own charge, in a steady order. */
	public static List<Spot> of(String uid) {
		List<Spot> out = new ArrayList<>();
		for (Anchor a : DataStore.anchors().values()) {
			if (a.own(uid) >= 1) {
				out.add(new Spot(a.x, a.y, a.z, a.isNether()));
			}
		}
		out.sort(Comparator.comparingInt(Spot::x).thenComparingInt(Spot::z).thenComparingInt(Spot::y));
		return out;
	}

	/** Sends this player their anchors, when the list changed since they were last sent it (and their client has the mod). */
	public static void send(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, ModPackets.AnchorsPayload.TYPE)) {
			return;
		}
		List<Spot> now = of(p.getStringUUID());
		if (now.equals(SENT.get(p.getUUID()))) {
			return;
		}
		SENT.put(p.getUUID(), now);
		ServerPlayNetworking.send(p, new ModPackets.AnchorsPayload(now));
	}

	/** A player joined: they are sent their list whatever they were sent before (their client starts with none). */
	public static void joined(ServerPlayer p) {
		SENT.remove(p.getUUID());
		send(p);
	}

	/** An anchor broke, ran dry or was lost: anyone's list may have changed. */
	public static void changed(MinecraftServer s) {
		if (s == null) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			send(p);
		}
	}

	/** Every five seconds, for whatever drew a player's charge out of an anchor without a word. */
	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 100 == 37) {
			changed(s);
		}
	}

	public static void forget(UUID player) {
		SENT.remove(player);
	}
}
