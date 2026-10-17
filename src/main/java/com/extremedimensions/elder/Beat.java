package com.extremedimensions.elder;

/**
 * An action's own clock. It runs at the fight's tempo (faster with the Elders' attack speed, faster again when they
 * are enraged), and tells when a moment in the action is reached, so every blow keeps its shape at any speed. The
 * client plays the same action at the same tempo (see {@link ElderBoss#getTempo()}).
 */
final class Beat {
	double t, prev;

	void reset() {
		t = 0;
		prev = 0;
	}

	void step(double tempo) {
		prev = t;
		t += tempo;
	}

	/** The moment {@code mark} (in ticks at tempo 1) was reached on this step. */
	boolean at(double mark) {
		return prev < mark && t >= mark;
	}

	boolean past(double mark) {
		return t >= mark;
	}

	boolean between(double from, double to) {
		return t >= from && t < to;
	}

	/** On every {@code period} ticks (at tempo 1), from {@code from} on. */
	boolean every(double period, double from) {
		if (t < from) {
			return false;
		}
		return Math.floor((prev - from) / period) != Math.floor((t - from) / period);
	}
}
