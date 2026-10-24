package net.battlehub.lobby;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.hub.HubWorld;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * People waiting in the same queue glow for each other in the lobby (duel queues, party lobbies, 1v2 and 1v3), so
 * you see who you are about to play with. Only you see your queue mates glow: the glow goes to your client alone
 * (the entity's flags as the game keeps them, plus glowing), and it is sent again every few ticks because the server
 * sends a player's real flags whenever they sneak, sprint or swim. Bots do not glow.
 */
public final class QueueGlow {
	private QueueGlow() {}

	/** The shared flags of an entity (on fire, sneaking, sprinting, ...) and the bit that makes it glow. */
	private static final int SHARED_FLAGS = 0;
	private static final byte GLOWING = 0x40;
	/** Ticks between the glow packets of one pair (a flag change in between shows for this long without the glow). */
	private static final int RESEND = 2;
	/** Ticks between working out who waits with whom. */
	private static final int RECOUNT = 10;

	/** Per person in the lobby, the queue mates their client shows glowing. */
	private static final Map<UUID, Set<UUID>> SHOWN = new HashMap<>();
	private static Map<UUID, Set<UUID>> mates = new HashMap<>();
	private static int tick;

	public static void tick(MinecraftServer server) {
		tick++;
		if (tick % RECOUNT == 0) mates = count(server);
		if (tick % RESEND != 0) return;
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			UUID id = viewer.getUUID();
			Set<UUID> want = mates.getOrDefault(id, Set.of());
			Set<UUID> shown = SHOWN.computeIfAbsent(id, k -> new HashSet<>());
			// mates who no longer wait with them: back to how the game shows them
			for (UUID gone : List.copyOf(shown)) {
				if (want.contains(gone)) continue;
				ServerPlayer p = server.getPlayerList().getPlayer(gone);
				if (p != null && p.level() == viewer.level()) send(viewer, p, false);
				shown.remove(gone);
			}
			for (UUID m : want) {
				ServerPlayer p = server.getPlayerList().getPlayer(m);
				if (p == null || p.level() != viewer.level()) {
					shown.remove(m);
					continue;
				}
				send(viewer, p, true);
				shown.add(m);
			}
			if (shown.isEmpty()) SHOWN.remove(id);
		}
		SHOWN.keySet().removeIf(v -> server.getPlayerList().getPlayer(v) == null);
	}

	/** Who sees whom glowing: people in the lobby, and the people (not bots) waiting with them. */
	private static Map<UUID, Set<UUID>> count(MinecraftServer server) {
		Map<UUID, Set<UUID>> out = new HashMap<>();
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(viewer.getUUID()) || !inLobby(viewer)) continue;
			Set<UUID> set = new HashSet<>();
			for (UUID m : Queues.mates(viewer.getUUID())) {
				ServerPlayer p = server.getPlayerList().getPlayer(m);
				if (p != null && !Bots.isBot(m) && inLobby(p)) set.add(m);
			}
			if (!set.isEmpty()) out.put(viewer.getUUID(), set);
		}
		return out;
	}

	private static boolean inLobby(ServerPlayer p) {
		Sessions.State st = Sessions.state(p.getUUID());
		return HubWorld.isBattleHub(p.level()) && (st == Sessions.State.LOBBY || st == Sessions.State.QUEUED) && !p.isSpectator();
	}

	/** Whether {@code viewer}'s client is shown {@code other} glowing for being in its queue (self-test, inspect). */
	public static boolean shows(UUID viewer, UUID other) {
		Set<UUID> s = SHOWN.get(viewer);
		return s != null && s.contains(other);
	}

	/** {@code other}'s flags as the game keeps them, with or without the glow, to {@code viewer}'s client only. */
	private static void send(ServerPlayer viewer, ServerPlayer other, boolean glow) {
		byte b = 0;
		if (other.isOnFire()) b |= 0x01;
		if (other.isShiftKeyDown()) b |= 0x02;
		if (other.isSprinting()) b |= 0x08;
		if (other.isSwimming()) b |= 0x10;
		if (other.isInvisible()) b |= 0x20;
		if (glow || other.hasGlowingTag()) b |= GLOWING;
		if (other.isFallFlying()) b |= (byte) 0x80;
		viewer.connection.send(new ClientboundSetEntityDataPacket(other.getId(),
				List.<SynchedEntityData.DataValue<?>>of(new SynchedEntityData.DataValue<>(SHARED_FLAGS, EntityDataSerializers.BYTE, b))));
	}

	public static void clear() {
		SHOWN.clear();
		mates = new HashMap<>();
	}
}
