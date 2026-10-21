package com.extremedimensions.client.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;

/**
 * The mist of the Seam: banks of fog that roll through, so that how far you can see wanders between about fifty and a
 * hundred blocks. Thick banks also start close; thin ones leave the near air clear. The density is a slow field over
 * time and place, smoothed so it never jumps. Down in the Fray, under the islands, it closes in to a few dozen blocks.
 */
public final class SeamFog {
	private SeamFog() {}

	private static double density = 0.5;
	private static long last;
	/** How far the fog let anyone see when it was last set up (blocks). */
	private static float lastEnd = 75;
	/** How far the fog of a frayed island has closed in (0 to 1), eased. */
	private static double frayedK = 0;

	/** How far the fog lets anyone see now (blocks), for what must show through it. */
	public static float end() {
		return lastEnd;
	}

	public static void apply(FogData data) {
		Minecraft mc = Minecraft.getInstance();
		if (!SeamAmbience.inSeam(mc) || mc.player == null) {
			return;
		}
		long now = System.nanoTime();
		double dt = last == 0 ? 0.05 : Math.min(0.25, (now - last) / 1.0e9);
		last = now;
		double t = mc.level.getGameTime() / 20.0;
		double x = mc.player.getX(), z = mc.player.getZ();
		double target = 0.5
				+ 0.30 * Math.sin(t * 0.045 + x * 0.011) * Math.cos(z * 0.009 - t * 0.031)
				+ 0.15 * Math.sin(t * 0.13 + 1.7)
				+ 0.08 * Math.sin(t * 0.31 + x * 0.02 + z * 0.017);
		target = Mth.clamp(target, 0, 1);
		density += (target - density) * Math.min(1, dt * 0.6);
		float end = (float) (100 - 50 * density);
		float start = (float) (end * (0.55 - 0.47 * density));
		// Down in the Fray the dark closes in: a few dozen blocks, and thicker the deeper
		if (com.extremedimensions.config.Config.SEAM_FRAY > 0) {
			double depth = Mth.clamp((com.extremedimensions.config.Config.SEAM_FRAY_TOP + 10 - mc.player.getY()) / 20.0, 0, 1);
			end = (float) Mth.lerp(depth, end, 26 + 6 * (1 - density));
			start = (float) Mth.lerp(depth, start, 2);
		}
		// A frayed island: the fog closes in as down in the Fray, over the island only
		frayedK += ((com.extremedimensions.client.state.ClientState.frayed(x, z) ? 1 : 0) - frayedK) * Math.min(1, dt * 0.5);
		if (frayedK > 0.01) {
			end = (float) Mth.lerp(frayedK, end, 34);
			start = (float) Mth.lerp(frayedK, start, 3);
		}
		// Veilsight: the fog keeps farther off
		if (com.extremedimensions.registry.ModEnchantments.level(mc.level.registryAccess(),
				mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), com.extremedimensions.registry.ModEnchantments.VEILSIGHT) > 0) {
			float far = (float) Math.max(1, com.extremedimensions.config.Config.ENCH_VEILSIGHT_FOG);
			end *= far;
			start *= far;
		}
		data.environmentalStart = Math.min(data.environmentalStart, start);
		data.environmentalEnd = Math.min(data.environmentalEnd, end);
		lastEnd = data.environmentalEnd;
	}
}
