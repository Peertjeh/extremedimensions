package net.battlehub.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleFunction;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.Orbit;
import net.battlehub.hub.ClientEntities;
import net.battlehub.match.Match;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Camera flights: at the start of a match the camera flies over the arena, at the end it circles the winner.
 * The camera is an invisible display entity that exists only on that player's client; the player's own entity
 * does not move, so nothing in the match changes. The view goes back to the player when the flight ends, when
 * the player leaves, and when the match closes.
 */
public final class Cinematics {
	private Cinematics() {}

	private static final class Shot {
		final UUID viewer;
		final int matchId;
		final ServerLevel level;
		final ClientEntities.Handle camera;
		final int length;
		final DoubleFunction<Orbit.Pose> path;
		int age;
		boolean shown;

		Shot(UUID viewer, int matchId, ServerLevel level, ClientEntities.Handle camera, int length, DoubleFunction<Orbit.Pose> path) {
			this.viewer = viewer;
			this.matchId = matchId;
			this.level = level;
			this.camera = camera;
			this.length = length;
			this.path = path;
		}
	}

	private static final Map<UUID, Shot> SHOTS = new HashMap<>();

	private static EntityType<?> cameraType() {
		return ClientEntities.type("minecraft:item_display");
	}

	/** The intro of a match that just reached its countdown; returns its length in ticks (0: no intro). */
	public static int intro(Match m) {
		int ticks = ConfigManager.config().effects.introSeconds * 20;
		if (!ConfigManager.config().effects.intro || ticks <= 0 || m.arena() == null) {
			return 0;
		}
		AABB b = m.arena().bounds();
		double cx = (b.minX + b.maxX) / 2;
		double cz = (b.minZ + b.maxZ) / 2;
		double size = Math.max(12, Math.min(40, Math.max(b.getXsize(), b.getZsize()) / 2));
		double ySum = 0;
		List<ServerPlayer> viewers = new ArrayList<>();
		for (UUID id : m.alive()) {
			ServerPlayer p = m.player(id);
			if (p == null) continue;
			ySum += p.getY();
			if (!Bots.isBot(p)) viewers.add(p);
		}
		if (viewers.isEmpty()) {
			return 0;
		}
		double cy = ySum / Math.max(1, m.alive().size());
		for (ServerPlayer p : viewers) {
			start(p, m.id, m.arena().level(), ticks, t -> Orbit.intro(cx, cy, cz, size, t));
		}
		return ticks;
	}

	/** The victory shot: the camera circles the winner for the players in the match and the spectators. */
	public static void victory(Match m, UUID winner, int ticks) {
		if (!ConfigManager.config().effects.victoryCamera || m.arena() == null || ticks < 20) {
			return;
		}
		ServerPlayer w = m.player(winner);
		if (w == null || w.level() != m.arena().level()) {
			return;
		}
		Vec3 first = w.position();
		float start = w.getYRot() + 90f;
		DoubleFunction<Orbit.Pose> path = t -> {
			ServerPlayer now = m.player(winner);
			Vec3 at = now != null && now.level() == m.arena().level() ? now.position() : first;
			return Orbit.at(at.x, at.y, at.z, 4.5, 2.0, start, 150, 1.2, t);
		};
		List<UUID> viewers = new ArrayList<>(m.participants());
		viewers.addAll(m.watchers());
		for (UUID id : viewers) {
			ServerPlayer p = m.player(id);
			if (p == null || Bots.isBot(p) || m.leftPlayers().contains(id) || p.level() != m.arena().level()) continue;
			start(p, m.id, m.arena().level(), ticks, path);
		}
	}

	private static void start(ServerPlayer p, int matchId, ServerLevel level, int ticks, DoubleFunction<Orbit.Pose> path) {
		stop(p);
		ClientEntities.Handle h = ClientEntities.handle(level, cameraType());
		if (h == null) {
			return;
		}
		SHOTS.put(p.getUUID(), new Shot(p.getUUID(), matchId, level, h, ticks, path));
	}

	/** Every tick: the cameras move on. */
	public static void tick(MinecraftServer server) {
		if (SHOTS.isEmpty()) {
			return;
		}
		for (Shot s : new ArrayList<>(SHOTS.values())) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.viewer);
			if (p == null || p.hasDisconnected()) {
				SHOTS.remove(s.viewer);
				continue;
			}
			if (p.level() != s.level) {
				// Another world on the client: the camera is gone with it
				SHOTS.remove(s.viewer);
				continue;
			}
			Orbit.Pose pose = s.path.apply(Math.min(1.0, s.age / (double) s.length));
			Vec3 pos = new Vec3(pose.x(), pose.y(), pose.z());
			if (!s.shown) {
				ClientEntities.add(p, s.camera, cameraType(), pos, pose.yaw(), pose.pitch(), pose.yaw());
				ClientEntities.glide(p, s.camera.id(), 2);
				p.connection.send(new ClientboundSetCameraPacket(s.camera.entity()));
				s.shown = true;
			} else {
				ClientEntities.move(p, s.camera.id(), pos, pose.yaw(), pose.pitch());
			}
			s.age++;
			if (s.age > s.length) {
				stop(p);
			}
		}
	}

	/** Back to the player's own view. */
	public static void stop(ServerPlayer p) {
		Shot s = SHOTS.remove(p.getUUID());
		if (s == null || !s.shown || p.hasDisconnected()) {
			return;
		}
		p.connection.send(new ClientboundSetCameraPacket(p));
		ClientEntities.remove(p, new int[] {s.camera.id()});
	}

	/** Every camera of a match back to its player (the match closes or a round restarts). */
	public static void stopMatch(MinecraftServer server, int matchId) {
		for (Shot s : new ArrayList<>(SHOTS.values())) {
			if (s.matchId != matchId) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(s.viewer);
			if (p != null) {
				stop(p);
			} else {
				SHOTS.remove(s.viewer);
			}
		}
	}

	public static boolean active(UUID id) {
		return SHOTS.containsKey(id);
	}

	/** The camera entity a player looks through, or -1. */
	public static int cameraId(UUID id) {
		Shot s = SHOTS.get(id);
		return s == null ? -1 : s.camera.id();
	}

	public static void clear() {
		SHOTS.clear();
	}
}
