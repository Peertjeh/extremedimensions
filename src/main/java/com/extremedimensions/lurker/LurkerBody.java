package com.extremedimensions.lurker;

/** Anything drawn with the Lurker's body (Lurkers, Hollowlings, Keepers): it only needs to tell its pose. */
public interface LurkerBody {
	/** Calm (its own glow), come to steal (a blue pulse), come to hurt (a red glow). */
	int MOOD_CALM = 0, MOOD_THIEF = 1, MOOD_HUNTER = 2;

	int getLurkerPose();

	/** What its glow tells of what it came for (only the Lurker's ever changes). */
	default int getLurkerMood() {
		return MOOD_CALM;
	}
}
