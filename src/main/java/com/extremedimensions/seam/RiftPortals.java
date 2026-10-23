package com.extremedimensions.seam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Puts the rifts where they can be seen ({@link RiftPortal}): over each visitor's rift home in the Seam, a tear only
 * they see, following the rift as it wanders, with the world they came from at the far end of its tunnel; and at each
 * rift a Seam Needle tears in the world, a smaller one with the Seam through it, closing when the rift does. Either
 * is passed through by walking into its opening ({@link #through}).
 */
public final class RiftPortals {
	private RiftPortals() {}

	/** Each visitor's tear over their rift home. */
	private static final Map<UUID, RiftPortal> HOME = new HashMap<>();

	static void clear() {
		HOME.clear();
	}

	static boolean on() {
		return Config.SEAM_PORTAL > 0;
	}

	/** The rift home's radius: as set, but within the gate's opening when there is one. */
	public static float homeRadius() {
		float r = Math.max(0.6F, Config.SEAM_PORTAL_RADIUS / 10F);
		return Config.SEAM_RETURN_GATE > 0 ? Math.min(2.4F, r) : r;
	}

	static float entryRadius() {
		return Math.max(0.6F, Config.SEAM_PORTAL_ENTRY_RADIUS / 10F);
	}

	/**
	 * Keeps a visitor's tear open over their rift home, where the rift stands now and turned the way it faces, opening
	 * one if there is none (and waiting while the ground under it is not loaded). Called every tick they are in the Seam.
	 */
	static void keepHome(ServerLevel seam, ServerPlayer p, PlayerData d) {
		if (!on()) {
			closeHome(p);
			return;
		}
		RiftPortal portal = HOME.get(p.getUUID());
		if (portal == null || portal.isRemoved() || portal.level() != seam || portal.closing() >= 0) {
			if (!Loaded.ready(seam, BlockPos.containing(d.riftX, d.riftY, d.riftZ))) {
				return;
			}
			portal = new RiftPortal(ModEntities.RIFT_PORTAL, seam);
			portal.setOwner(p.getId());
			portal.setPos(d.riftX, d.riftY, d.riftZ);
			portal.setYRot(d.riftYaw);
			portal.setRadius(homeRadius());
			portal.setView(homeView(seam, d));
			if (!seam.addFreshEntity(portal)) {
				return;
			}
			HOME.put(p.getUUID(), portal);
		}
		portal.keep();
		if (portal.distanceToSqr(d.riftX, d.riftY, d.riftZ) > 0.01 || portal.getYRot() != d.riftYaw) {
			portal.setPos(d.riftX, d.riftY, d.riftZ);
			portal.setYRot(d.riftYaw);
		}
		if (p.tickCount % 40 == 0) {
			portal.setView(homeView(seam, d));
			portal.setRadius(homeRadius());
		}
	}

	/** The tear over a visitor's rift home closes behind them (or as they are pulled out). */
	static void closeHome(Player p) {
		RiftPortal portal = HOME.remove(p.getUUID());
		if (portal != null && !portal.isRemoved() && portal.closing() < 0) {
			portal.closeIn(14);
		}
	}

	/** What a visitor sees through their rift home: the Nether, or the world above by day or by night. */
	static int homeView(ServerLevel seam, PlayerData d) {
		if (Dims.NETHER.equals(d.homeDim)) {
			return RiftPortal.NETHER;
		}
		ServerLevel home = seam.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
		return home != null && home.isDarkOutside() ? RiftPortal.OVERWORLD_NIGHT : RiftPortal.OVERWORLD_DAY;
	}

	/**
	 * The tear over a rift a Seam Needle opened on the {@code face} of a block, its floor at ({@code x}, {@code y},
	 * {@code z}), closing with the rift in {@code ticks}: across the face it was opened on (facing whoever opened it on
	 * a top or a bottom). Null when tears are off or the level would not take it.
	 */
	static RiftPortal entry(ServerLevel level, ServerPlayer opener, double x, double y, double z, Direction face, int ticks) {
		if (!on()) {
			return null;
		}
		float r = entryRadius();
		RiftPortal portal = new RiftPortal(ModEntities.RIFT_PORTAL, level);
		// under a ceiling it hangs from it rather than standing up into it
		double floor = face == Direction.DOWN ? Math.floor(y) + 1 - 2 * r - 0.3 : y;
		portal.setPos(x, floor, z);
		portal.setYRot(face.getAxis().isHorizontal() ? face.toYRot() : opener.getYRot());
		portal.setRadius(r);
		portal.setView(RiftPortal.SEAM);
		portal.closeIn(ticks);
		return level.addFreshEntity(portal) ? portal : null;
	}

	/**
	 * Whether a player is passing through the opening of a tear standing on ({@code x}, {@code y}, {@code z}), facing
	 * {@code yaw}, {@code r} round: within a step of its plane and inside its rim (the middle of their body, that is).
	 */
	public static boolean through(Entity p, double x, double y, double z, float yaw, float r) {
		return through(p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ(), x, y, z, yaw, r);
	}

	/** Whether the middle of a body at ({@code px}, {@code py}, {@code pz}) is passing through a tear (as above). */
	public static boolean through(double px, double py, double pz, double x, double y, double z, float yaw, float r) {
		double a = Math.toRadians(yaw);
		double dx = px - x, dz = pz - z, dy = py - (y + r + 0.15);
		double along = dx * -Math.sin(a) + dz * Math.cos(a), across = dx * Math.cos(a) + dz * Math.sin(a);
		double rim = r * 0.85;
		return Math.abs(along) < 0.7 && across * across + dy * dy < rim * rim;
	}
}
