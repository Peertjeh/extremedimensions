package com.extremedimensions.client.fx;

import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * While an Elder forms or dies, the view is held on it: over the first moment the camera swings round to it, then
 * follows it until it is whole or has broken apart. The player is turned with the camera, so the view stays where it was when the hold ends.
 */
public final class CameraLock {
	private CameraLock() {}

	private static int entity = -1;
	private static long from, until;
	private static boolean started;
	private static float startYaw, startPitch;

	/** Holds the view on this entity for this long. */
	public static void lock(int entityId, long millis) {
		entity = entityId;
		from = System.currentTimeMillis();
		until = from + Math.max(0, millis);
		started = false;
	}

	public static void release() {
		entity = -1;
	}

	/** Whether the view is being held (for tests). */
	public static boolean locked() {
		return entity >= 0 && System.currentTimeMillis() <= until;
	}

	/** Where the camera at {@code eye} looks now ({yaw, pitch}), or null when the view is free. */
	public static float[] apply(Vec3 eye) {
		if (entity < 0) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		Entity e = mc.level == null ? null : mc.level.getEntity(entity);
		if (e == null && mc.player != null && now <= until && now - from < 1500) {
			// An Elder just summoned may reach this client a moment after the word to look at it
			return null;
		}
		if (mc.player == null || e == null || now > until) {
			entity = -1;
			return null;
		}
		double dx = e.getX() - eye.x, dy = e.getY() + e.getBbHeight() * 0.6 - eye.y, dz = e.getZ() - eye.z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		if (!started) {
			started = true;
			startYaw = mc.player.getYRot();
			startPitch = mc.player.getXRot();
		}
		float k = (float) Ui.ease(Math.min(1, (now - from) / 700.0));
		float y = startYaw + Mth.wrapDegrees(yaw - startYaw) * k;
		float p = Mth.clamp(startPitch + (pitch - startPitch) * k, -90F, 90F);
		mc.player.setYRot(y);
		mc.player.setXRot(p);
		mc.player.yRotO = y;
		mc.player.xRotO = p;
		mc.player.setYHeadRot(y);
		return new float[] {y, p};
	}
}
