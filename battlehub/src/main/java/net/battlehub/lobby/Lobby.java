package net.battlehub.lobby;

import java.util.Set;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.hub.HubWorld;
import net.battlehub.session.Sessions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The lobby in BattleHub's own world: where players arrive, and sending them there. */
public final class Lobby {
	private Lobby() {}

	public record Spot(ServerLevel level, Vec3 pos, float yaw, float pitch) {}

	public static ServerLevel level(MinecraftServer server) {
		return HubWorld.level(server);
	}

	/** The position set with /battlehub setlobby, or the spawn of the lobby BattleHub built. */
	public static Spot spot(MinecraftServer server) {
		HubConfig.Lobby l = ConfigManager.config().lobby;
		ServerLevel level = level(server);
		if (l.set && HubWorld.KEY.identifier().toString().equals(l.dimension)) {
			return new Spot(level, new Vec3(l.x, l.y, l.z), l.yaw, l.pitch);
		}
		return new Spot(level, HubWorld.defaultSpawn(), HubWorld.defaultYaw(), 0);
	}

	public static void teleport(ServerPlayer p) {
		Spot s = spot(p.level().getServer());
		p.stopRiding();
		p.teleportTo(s.level(), s.pos().x, s.pos().y, s.pos().z, Set.of(), s.yaw(), s.pitch(), true);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
	}

	/** Anywhere in the hub world (the whole world is the lobby). */
	public static boolean inArea(ServerPlayer p) {
		return HubWorld.is(p.level());
	}

	public static boolean isLobbyDimension(Level level) {
		return HubWorld.is(level);
	}

	/** Heal, feed and hand out the lobby items. */
	public static void refresh(ServerPlayer p) {
		HubItems.ensure(p, Sessions.state(p.getUUID()) == Sessions.State.QUEUED);
	}
}
