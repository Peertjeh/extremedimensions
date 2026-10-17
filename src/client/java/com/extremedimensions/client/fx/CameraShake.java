package com.extremedimensions.client.fx;

import com.extremedimensions.client.ui.Ui;

/**
 * The screen shaking under an Elder's blows: the camera (not the player's aim) is turned a little, in a smooth,
 * restless way, for as long as the shake lasts; a stronger shake wins over a weaker one and every shake dies away at
 * its end. Calm mode keeps only a quarter of it.
 */
public final class CameraShake {
	private CameraShake() {}

	private static float strength = 0;
	private static int ticks = 0;
	private static int total = 1;

	/** Shakes the screen with this strength (about degrees) for this many ticks. */
	public static void add(float s, int t) {
		if (Ui.calm()) {
			s *= 0.25F;
		}
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

	/** How far to turn the camera now: {yaw, pitch} in degrees, or null when still. */
	public static float[] offset() {
		float s = current();
		if (s < 0.01F) {
			return null;
		}
		double t = System.nanoTime() / 1.0e9;
		float yaw = (float) (s * (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 23.0 + 1.3) * 0.4));
		float pitch = (float) (s * (Math.sin(t * 41.0 + 0.7) * 0.6 + Math.sin(t * 29.0 + 2.1) * 0.4));
		return new float[] {yaw, pitch};
	}
}
