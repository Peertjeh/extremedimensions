package com.extremedimensions.client.audio;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.title.Menus;
import com.extremedimensions.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The menus' music. "Tick of the Boundary" plays on the title screen and every menu behind it; once a world starts to
 * load it gives way to "Ominous Loading Screen", which plays until the player stands in the world and then fades out.
 * Both loop and follow the Music slider, and Minecraft's own music waits while they play. The display option for the
 * title and loading screens can turn them off.
 */
public final class MenuMusic {
	private MenuMusic() {}

	/** Into the world, or switched off: 3 seconds. */
	private static final int FADE_OUT_TICKS = 60;
	/** From one track to the other, and in at the start: 1.5 seconds. */
	private static final int CROSSFADE_TICKS = 30;
	/** A track that did not start (the sound engine still loading, say) is tried again after this long. */
	private static final int RETRY_TICKS = 40;

	private static Track current = null;
	private static int sinceStart = RETRY_TICKS;
	private static Menus.Phase lastPhase = null;
	private static int loadingStarts = 0;

	/** The track the menus want now, or null for none. */
	private static SoundEvent wanted() {
		if (!ClientOptions.menu().music()) {
			return null;
		}
		return switch (Menus.phase()) {
			case MENU -> ModSounds.MUSIC_MENU_TITLE;
			case JOINING -> ModSounds.MUSIC_MENU_LOADING;
			case WORLD -> null;
		};
	}

	/** Minecraft's own music waits while ours holds the menus and the join. */
	public static boolean holdsVanilla() {
		return ClientOptions.menu().music() && Menus.phase() != Menus.Phase.WORLD;
	}

	/** For tests: how many times the loading track has been started. */
	public static int loadingStarts() {
		return loadingStarts;
	}

	/** For tests: one of the two tracks is still meant to be playing. */
	public static boolean playing() {
		return current != null;
	}

	public static void tick(Minecraft mc) {
		Menus.Phase phase = Menus.phase();
		if (phase != lastPhase && holdsVanilla()) {
			// back from a world, say, with its music still going
			mc.getMusicManager().stopPlaying();
		}
		lastPhase = phase;
		SoundEvent want = wanted();
		sinceStart++;
		if (current != null && current.event != want) {
			// it fades on by itself and stops when silent
			current.fadeOut(want == null ? FADE_OUT_TICKS : CROSSFADE_TICKS);
			current = null;
			sinceStart = RETRY_TICKS;
		}
		if (want == null) {
			return;
		}
		SoundManager sounds = mc.getSoundManager();
		boolean on = current != null && !current.isStopped() && (sinceStart < RETRY_TICKS || sounds.isActive(current));
		if (!on && sinceStart >= RETRY_TICKS) {
			current = new Track(want, CROSSFADE_TICKS);
			sounds.play(current);
			sinceStart = 0;
			if (want == ModSounds.MUSIC_MENU_LOADING) {
				loadingStarts++;
			}
		}
	}

	private static final class Track extends AbstractTickableSoundInstance {
		final SoundEvent event;
		private float target = 1.0f;
		private float step;

		Track(SoundEvent event, int fadeInTicks) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.event = event;
			this.looping = true;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			// not silent: a sound that starts at nothing is skipped
			this.volume = 0.01f;
			this.step = 1.0f / fadeInTicks;
			this.x = 0;
			this.y = 0;
			this.z = 0;
		}

		void fadeOut(int ticks) {
			target = 0;
			step = Math.max(volume, 0.01f) / ticks;
		}

		@Override
		public void tick() {
			if (volume < target) {
				volume = Math.min(target, volume + step);
			} else if (volume > target) {
				volume = Math.max(0, volume - step);
				if (volume <= 0) {
					stop();
				}
			}
		}
	}
}
