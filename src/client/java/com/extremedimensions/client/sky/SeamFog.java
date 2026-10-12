package com.extremedimensions.client.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;

/**
 * The mist of the Seam: banks of fog that roll through, so that how far you can see wanders between about fifty and a
 * hundred blocks. Thick banks also start close; thin ones leave the near air clear. The density is a slow field over
 * time and place, smoothed so it never jumps.
 */
public final class SeamFog {
	private SeamFog() {}

	private static double density = 0.5;
	private static long last;

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
		data.environmentalStart = Math.min(data.environmentalStart, start);
		data.environmentalEnd = Math.min(data.environmentalEnd, end);
	}
}
