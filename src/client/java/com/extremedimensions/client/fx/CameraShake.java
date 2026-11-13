package com.extremedimensions.client.fx;

import com.extremedimensions.client.options.ClientOptions;

import net.minecraft.client.Minecraft;

/**
 * The screen shaking under an Elder's blows: the camera (not the player's aim) is turned a little, in a smooth,
 * restless way, for as long as the shake lasts; a stronger shake wins over a weaker one and every shake dies away at
 * its end. How much of it shows is the player's Camera option (3.55.0: full, gentle a quarter, off none), times
 * Minecraft's own Distortion Effects slider; both are read as the frame is drawn, so a change shows at once.
 */
public final class CameraShake {
	private CameraShake() {}

	private static float strength = 0;
	private static int ticks = 0;
	private static int total = 1;
	/** A blow's jolt: the view knocked down and coming back (when it began, ms, and how hard, degrees). */
	private static long joltAt = -1;
	private static float joltDeg = 0;

	/** A heavy blow landing near: the view is knocked down a little and springs back (as much as {@link #factor()} lets). */
	public static void jolt(float s) {
		float deg = Math.max(0F, Math.min(1F, s)) * 2.6F;
		if (deg <= 0.05F) {
			return;
		}
		joltAt = System.currentTimeMillis();
		joltDeg = deg;
	}

	/** The jolt's pitch now: a sharp knock down, then a damped spring back. */
	private static float joltPitch() {
		if (joltAt < 0) {
			return 0;
		}
		double k = (System.currentTimeMillis() - joltAt) / 380.0;
		if (k >= 1) {
			joltAt = -1;
			return 0;
		}
		double attack = Math.min(1.0, k / 0.08);
		return (float) (joltDeg * attack * Math.pow(1 - k, 2) * Math.cos(k * Math.PI * 2.2));
	}

	/** Shakes the screen with this strength (about degrees) for this many ticks. */
	public static void add(float s, int t) {
		if (s <= 0 || t <= 0) {
			return;
		}
		float now = current();
		if (s >= now) {
			strength = s;
			ticks = t;
			total = t;
		} else {
			ticks = Math.max(ticks, Math.min(t, 10));
		}
	}

	public static void tick() {
		if (ticks > 0) {
			ticks--;
		}
	}

	/** The shake's strength right now (it dies away over its last ticks). */
	public static float current() {
		if (ticks <= 0) {
			return 0;
		}
		return strength * Math.min(1F, ticks / Math.max(1F, Math.min(12F, total)));
	}

	/**
	 * The share of the shake and the jolt shown (3.55.0): the Camera option's (full, gentle a quarter, off none) times
	 * Minecraft's Distortion Effects, which players turn down for the same reason.
	 */
	public static float factor() {
		float share = ClientOptions.camera().shake;
		if (share <= 0) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		double distortion = mc.options == null ? 1.0 : mc.options.screenEffectScale().get();
		return share * (float) Math.max(0.0, Math.min(1.0, distortion));
	}

	/** How far to turn the camera now: {yaw, pitch} in degrees, or null when still. */
	public static float[] offset() {
		float k = factor();
		float s = current() * k;
		float jolt = joltPitch() * k;
		if (s < 0.01F && Math.abs(jolt) < 0.01F) {
			return null;
		}
		if (s < 0.01F) {
			return new float[] {0, jolt};
		}
		double t = System.nanoTime() / 1.0e9;
		float yaw = (float) (s * (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 23.0 + 1.3) * 0.4));
		float pitch = (float) (s * (Math.sin(t * 41.0 + 0.7) * 0.6 + Math.sin(t * 29.0 + 2.1) * 0.4));
		return new float[] {yaw, pitch + jolt};
	}
}
