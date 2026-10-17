package com.extremedimensions.client.audio;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Menu music: "The Tether Knows" and "The Tether" alternate while no world is loaded and fade out once one is.
 * Vanilla menu music is held back; volume follows the Music slider.
 */
public final class MenuMusic {
	private MenuMusic() {}

	private static final SoundEvent[] TRACKS = {
			ModSounds.MUSIC_MENU_1,
			ModSounds.MUSIC_MENU_2,
	};
	/** Fade duration in ticks (3 seconds). */
	private static final int FADE_TICKS = 60;
	/** Minimum ticks between two starts (avoids spam if a file is missing). */
	private static final int MIN_GAP_TICKS = 200;

	private static Track current = null;
	private static int nextTrack = 0;
	private static int sinceStart = MIN_GAP_TICKS;

	public static void tick(Minecraft mc) {
		SoundManager sounds = mc.getSoundManager();
		sinceStart++;
		if (mc.level == null) {
			mc.getMusicManager().stopPlaying();
			boolean playing = current != null && !current.isStopped() && !current.fading
					&& (sinceStart < 40 || sounds.isActive(current));
			if (!playing && sinceStart >= MIN_GAP_TICKS) {
				current = new Track(TRACKS[nextTrack]);
				nextTrack = (nextTrack + 1) % TRACKS.length;
				sounds.play(current);
				sinceStart = 0;
			}
		} else if (current != null && !current.fading) {
			current.fading = true;
		}
	}

	private static final class Track extends AbstractTickableSoundInstance {
		boolean fading = false;

		Track(SoundEvent event) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = false;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 1.0f;
			this.x = 0;
			this.y = 0;
			this.z = 0;
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
