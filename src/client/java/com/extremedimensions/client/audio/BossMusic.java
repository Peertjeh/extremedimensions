package com.extremedimensions.client.audio;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The Elders' fight music. Each fight repeats its track to everyone near every couple of seconds; the music plays for
 * as long as some fight near still asks for it and never stops at the end of a track: a few seconds before a track
 * ends the next pass of it fades in under the last bars (the tracks all fade out at the end and swell in at the start,
 * so the seam is not heard). With several fights near, the track already playing keeps playing as long as its fight
 * asks for it; only when it ends does the music cross over to another. Vanilla music is held back meanwhile and the
 * volume follows the Music slider.
 */
public final class BossMusic {
	private BossMusic() {}

	private static final SoundEvent[] TRACKS = {ModSounds.MUSIC_BOSS_MATRIARCH, ModSounds.MUSIC_BOSS_FERRYMAN, ModSounds.MUSIC_BOSS_WEAVER};
	/** Length of each track in ticks (sounds/music/boss_*.ogg: 179.96 s, 189.2 s, 197.2 s). */
	private static final int[] LENGTH = {3599, 3784, 3944};
	/** Ticks before a track's end that its next pass starts, crossfading over its closing fade. */
	private static final int OVERLAP = 140;
	/** A fight that has not asked for its track for this long is over (it asks every 40 ticks). */
	private static final int TIMEOUT = 140;
	private static final int FADE_IN_LOOP = 90;
	private static final int FADE_IN_NEW = 40;
	private static final int FADE_OUT = 60;

	/** Client tick at which each track was last asked for (-1: not asked, or stopped). */
	private static final int[] heard = {-1, -1, -1};
	private static final List<Pass> passes = new ArrayList<>();
	private static int now = 0;
	private static int playing = -1;

	/** A fight near asks for its track. */
	public static void request(int track) {
		if (track >= 0 && track < TRACKS.length) {
			heard[track] = now;
		}
	}

	/** A fight ended: its track is no longer asked for (-1: every track). */
	public static void stop(int track) {
		for (int t = 0; t < heard.length; t++) {
			if (track < 0 || t == track) {
				heard[t] = -1;
			}
		}
	}

	/** Which track is playing (-1: none); for tests. */
	public static int playing() {
		return playing;
	}

	private static boolean live(int track) {
		return heard[track] >= 0 && now - heard[track] <= TIMEOUT;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			for (Pass p : passes) {
				p.stopNow();
			}
			passes.clear();
			playing = -1;
			stop(-1);
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		now++;
		for (Pass p : passes) {
			p.age++;
		}
		passes.removeIf(Pass::done);
		// The track playing stays while its fight still asks for it; otherwise the one asked for last
		int want = -1;
		if (playing >= 0 && live(playing)) {
			want = playing;
		} else {
			for (int t = 0; t < TRACKS.length; t++) {
				if (live(t) && (want < 0 || heard[t] > heard[want])) {
					want = t;
				}
			}
		}
		if (want != playing) {
			for (Pass p : passes) {
				p.fadeOut(FADE_OUT);
			}
			playing = want;
			if (want >= 0) {
				start(mc, want, FADE_IN_NEW);
			}
		} else if (playing >= 0) {
			Pass lead = null;
			for (Pass p : passes) {
				if (p.track == playing && !p.leaving) {
					lead = p;
				}
			}
			if (lead == null) {
				start(mc, playing, FADE_IN_NEW);
			} else if (lead.age >= LENGTH[playing] - OVERLAP) {
				// The next pass swells in under this one's closing bars
				lead.fadeOut(OVERLAP);
				start(mc, playing, FADE_IN_LOOP);
			}
		}
		if (playing >= 0) {
			mc.getMusicManager().stopPlaying();
		}
	}

	private static void start(Minecraft mc, int track, int fadeIn) {
		Pass p = new Pass(track, fadeIn);
		passes.add(p);
		mc.getSoundManager().play(p);
	}

	/** One pass through a track, with its own fade in and out. */
	private static final class Pass extends AbstractTickableSoundInstance {
		final int track;
		final int fadeIn;
		int age = 0;
		int fadeOutTicks = 0;
		float level = 0.02F;
		boolean leaving = false;

		Pass(int track, int fadeIn) {
			super(TRACKS[track], SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.track = track;
			this.fadeIn = Math.max(1, fadeIn);
			this.looping = false;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = this.level;
			this.x = 0;
			this.y = 0;
			this.z = 0;
		}

		void fadeOut(int ticks) {
			if (!leaving) {
				leaving = true;
				fadeOutTicks = Math.max(1, ticks);
			}
		}

		void stopNow() {
			this.stop();
		}

		boolean done() {
			return this.isStopped() || (leaving && level <= 0) || age > LENGTH[track] + 40;
		}

		/** Starts even while it is still nearly silent (it fades in). */
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			if (leaving) {
				level -= 1.0F / fadeOutTicks;
				if (level <= 0) {
					level = 0;
					this.volume = 0;
					this.stop();
					return;
				}
			} else if (level < 1.0F) {
				level = Math.min(1.0F, level + 1.0F / fadeIn);
			}
			this.volume = level;
		}
	}
}
