package net.battlehub.core.ml;

import java.util.ArrayDeque;

/**
 * The observations of one fighter's last decisions, for a spec's history (v3's observations of earlier decisions): as
 * ml/pvp_sim2/env.py keeps them. At the start of a fight there is none (the observation uses the values now), and the
 * first observation stands for every earlier decision until there are real ones. The mod starts a fight again after
 * more than the spec's {@code pause} decisions without one of the model's (the scripted mind had the fight, or the bot
 * was dead).
 */
public final class ObsHistory {
	private final int keep;
	private final int pauseTicks;
	private final ArrayDeque<float[]> last = new ArrayDeque<>();
	private long lastTick;

	public ObsHistory(CombatSpec spec) {
		this.keep = spec.maxAgo;
		this.pauseTicks = (spec.pause + 1) * spec.decisionTicks;
	}

	/** The observations of the earlier decisions for a decision at {@code tick}, newest first; null at a fight's start. */
	public float[][] past(long tick) {
		if (keep == 0) return null;
		if (!last.isEmpty() && tick - lastTick > pauseTicks) last.clear();
		return last.isEmpty() ? null : last.toArray(new float[0][]);
	}

	/** This decision's observation, for the next ones (the first of a fight fills every earlier place). */
	public void add(long tick, float[] obs) {
		if (keep == 0) return;
		if (last.isEmpty()) {
			for (int a = 0; a < keep; a++) last.addFirst(obs);
		} else {
			last.addFirst(obs);
			while (last.size() > keep) last.removeLast();
		}
		lastTick = tick;
	}

	/** A new fight. */
	public void clear() {
		last.clear();
	}
}
