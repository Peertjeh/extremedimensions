package net.battlehub.lobby;

import java.util.Set;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.session.Sessions;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/** Where the lobby is, sending players there, and what counts as "in the lobby area". */
public final class Lobby {
	private Lobby() {}

	public record Spot(ServerLevel level, Vec3 pos, float yaw, float pitch) {}

	public static ServerLevel level(MinecraftServer server) {
		HubConfig.Lobby l = ConfigManager.config().lobby;
		if (l.set) {
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Ids.id(l.dimension)));
			if (level != null) {
				return level;
			}
		}
		LevelData.RespawnData spawn = server.getRespawnData();
		ServerLevel level = server.getLevel(spawn.dimension());
		return level == null ? server.overworld() : level;
	}

	public static Spot spot(MinecraftServer server) {
		HubConfig.Lobby l = ConfigManager.config().lobby;
		ServerLevel level = level(server);
		if (l.set) {
			return new Spot(level, new Vec3(l.x, l.y, l.z), l.yaw, l.pitch);
		}
		LevelData.RespawnData spawn = server.getRespawnData();
		BlockPos pos = spawn.pos();
		BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
		return new Spot(level, Vec3.atBottomCenterOf(top), spawn.yaw(), spawn.pitch());
	}

	public static void teleport(ServerPlayer p) {
		Spot s = spot(p.level().getServer());
		p.stopRiding();
		p.teleportTo(s.level(), s.pos().x, s.pos().y, s.pos().z, Set.of(), s.yaw(), s.pitch(), true);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
	}

	/** In the lobby dimension and within the protection radius (or anywhere there with radius -1). */
	public static boolean inArea(ServerLevel level, Vec3 pos) {
		MinecraftServer server = level.getServer();
		Spot s = spot(server);
		if (level.dimension() != s.level().dimension()) {
			return false;
		}
		int r = ConfigManager.config().lobby.protectionRadius;
		if (r < 0) {
			return true;
		}
		double dx = pos.x - s.pos().x;
		double dz = pos.z - s.pos().z;
		return dx * dx + dz * dz <= (double) r * r;
	}

	public static boolean inArea(ServerPlayer p) {
		return inArea(p.level(), p.position());
	}

	public static boolean isLobbyDimension(Level level) {
		return level instanceof ServerLevel sl && sl.dimension() == level(sl.getServer()).dimension();
	}

	/** Heal, feed and hand out the lobby items. */
	public static void refresh(ServerPlayer p) {
		HubItems.ensure(p, Sessions.state(p.getUUID()) == Sessions.State.QUEUED);
	}
}
