package com.extremedimensions.client.hud;

import com.extremedimensions.client.ModKeys;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The cards can be put away (3.55.0). The Seam briefing and the Collapse recap, which stand round the crosshair, fade
 * out on the Put a card away key (Controls; unbound as it comes) or as soon as the player moves once they have been
 * on the screen three seconds, long enough to see what they are. The guide card in its corner goes on the key until
 * the guide's next step; a step does not put it away, since walking is how its steps are done (its cross, with the
 * chat open, still switches the guide off for good).
 */
public final class PutAway {
	private PutAway() {}

	/** How long a card shows before a step puts it away, and how long it takes to fade out. */
	static final long MOVE_AFTER_MS = 3000, FADE_MS = 600;
	/** How far the player must move (blocks, across the ground) for it to count as a step. */
	private static final double STEP = 0.6;

	/** The guide's step whose card was put away (-1: none); the card comes back with the next step. */
	private static int guideAway = -1;

	/** One card's putting away, for the card it was last asked about (known by when it came). */
	static final class Card {
		private long of = Long.MIN_VALUE;
		private long shownAt = -1, awayAt = -1;
		private double x, z;
		private boolean placed;

		/** A card that came anew starts afresh. */
		private void track(long id) {
			if (id != of) {
				of = id;
				shownAt = -1;
				awayAt = -1;
				placed = false;
			}
		}

		/** The share of the card still drawn: all of it until it is put away, then less until nothing. */
		double fade(long id, long now) {
			track(id);
			return awayAt < 0 ? 1.0 : Ui.clamp01(1 - (now - awayAt) / (double) FADE_MS);
		}

		/** Whether it was put away and has faded out. */
		boolean gone(long id, long now) {
			track(id);
			return awayAt >= 0 && now - awayAt >= FADE_MS;
		}

		/** A tick while the card is on the screen: the key puts it away, and so does a step from three seconds on. */
		void watch(long id, long now, boolean key, LocalPlayer player) {
			track(id);
			if (awayAt >= 0) {
				return;
			}
			if (key) {
				awayAt = now;
				return;
			}
			if (shownAt < 0) {
				shownAt = now;
			}
			if (player == null || now - shownAt < MOVE_AFTER_MS) {
				return;
			}
			// (where they stand once it has been read a moment; a step away from there puts it away)
			if (!placed) {
				x = player.getX();
				z = player.getZ();
				placed = true;
				return;
			}
			double dx = player.getX() - x, dz = player.getZ() - z;
			if (dx * dx + dz * dz > STEP * STEP) {
				awayAt = now;
			}
		}
	}

	/** Whether the guide card of this step was put away. */
	public static boolean guideAway(int step) {
		return guideAway == step;
	}

	/** Every tick: the key (its presses are taken here) and the player's steps, for the card on the screen. */
	public static void tick(Minecraft mc) {
		boolean key = false;
		while (ModKeys.PUT_AWAY.consumeClick()) {
			key = true;
		}
		long now = System.currentTimeMillis();
		// (one card at a time, the one in front: the briefing, then the recap waiting behind it, then the guide)
		if (SeamBriefing.showing()) {
			SeamBriefing.watch(now, key, mc.player);
		} else if (CollapseRecap.showing()) {
			CollapseRecap.watch(now, key, mc.player);
		} else if (key && ChargeHud.guideClose != null) {
			guideAway = ClientState.guide;
		}
	}

	/** Leaving a world: the guide card shows again on the next. */
	public static void reset() {
		guideAway = -1;
	}
}
