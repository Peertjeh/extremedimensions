package com.extremedimensions.client.audio;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * An Elder's fight music. The server repeats which track is playing every couple of seconds while a fight is near;
 * the track loops, vanilla music is held back, and when the server stops repeating it (the fight is over, or the
 * player has left it) the track fades out. Volume follows the Music slider.
 */
public final class BossMusic {
	private BossMusic() {}

	private static final SoundEvent[] TRACKS = {ModSounds.MUSIC_BOSS_MATRIARCH, ModSounds.MUSIC_BOSS_FERRYMAN, ModSounds.MUSIC_BOSS_WEAVER};
	/** Ticks without a word from the server before the music fades. */
	private static final int TIMEOUT = 140;
	private static final int FADE_TICKS = 60;

	private static int requested = -1;
	private static int quiet = 0;
	private static Track current;

	/** The server says which track the fight nearby has (-1: the fight is over). */
	public static void request(int track) {
		if (track < 0 || track >= TRACKS.length) {
			requested = -1;
			if (current != null) {
				current.fading = true;
			}
			return;
		}
		requested = track;
		quiet = 0;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			requested = -1;
			if (current != null) {
				current.stopNow();
				current = null;
			}
			return;
		}
		if (requested >= 0 && ++quiet > TIMEOUT) {
			requested = -1;
		}
		if (requested < 0) {
			if (current != null) {
				current.fading = true;
				if (current.isStopped()) {
					current = null;
				}
			}
			return;
		}
		mc.getMusicManager().stopPlaying();
		if (current == null || current.isStopped() || current.track != requested || current.fading) {
			if (current != null) {
				current.stopNow();
			}
			current = new Track(requested);
			mc.getSoundManager().play(current);
		}
	}

	private static final class Track extends AbstractTickableSoundInstance {
		final int track;
		boolean fading = false;

		Track(int track) {
			super(TRACKS[track], SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.track = track;
			this.looping = true;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 1.0f;
			this.x = 0;
			this.y = 0;
			this.z = 0;
		}

		void stopNow() {
			this.stop();
		}

		@Override
		public void tick() {
			if (fading) {
				this.volume -= 1.0f / FADE_TICKS;
				if (this.volume <= 0) {
					this.volume = 0;
					this.stop();
				}
			}
		}
	}
}
