package net.battlehub.fair;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Nobody stands around doing nothing in a match: after a while without moving, looking around or swinging, an
 * idle player is taken out (it counts as leaving). Idle players in a queue are taken out of it. Props in Prop
 * Hunt stand still on purpose and are left alone.
 */
public final class Afk {
	private Afk() {}

	private record Seen(Vec3 pos, float yaw, float pitch) {}

	private static final Map<UUID, Seen> SEEN = new HashMap<>();
	private static final Map<UUID, Integer> IDLE = new HashMap<>();
	private static int ticks;

	private static HubConfig.Fairness cfg() {
		return ConfigManager.config().fairness;
	}

	/** A swing or a click counts as doing something. */
	public static void active(UUID id) {
		IDLE.remove(id);
	}

	public static int idleSeconds(UUID id) {
		return IDLE.getOrDefault(id, 0);
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			if (Bots.isBot(p)) continue;
			Seen now = new Seen(p.position(), p.getYRot(), p.getXRot());
			Seen before = SEEN.put(id, now);
			boolean moved = before == null || before.pos.distanceToSqr(now.pos) > 0.02
					|| Math.abs(before.yaw - now.yaw) > 1 || Math.abs(before.pitch - now.pitch) > 1;
			int idle = moved ? 0 : IDLE.getOrDefault(id, 0) + 1;
			IDLE.put(id, idle);
			check(p, idle);
		}
		SEEN.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		IDLE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void check(ServerPlayer p, int idle) {
		HubConfig.Fairness c = cfg();
		Sessions.State s = Sessions.state(p.getUUID());
		if (s == Sessions.State.MATCH && c.afkMatchSeconds > 0 && idle >= c.afkMatchSeconds) {
			Match m = Matches.of(p.getUUID());
			if (m == null || m.mode == GameMode.PROP_HUNT || !m.inPlay(p.getUUID())) {
				return;
			}
			IDLE.remove(p.getUUID());
			Msg.send(p, "fair.afk_match");
			m.forfeit(p);
		} else if (s == Sessions.State.QUEUED && c.afkQueueSeconds > 0 && idle >= c.afkQueueSeconds) {
			IDLE.remove(p.getUUID());
			Queues.leaveAll(p);
			Msg.send(p, "fair.afk_queue");
		}
	}

	public static void clear() {
		SEEN.clear();
		IDLE.clear();
	}
}
