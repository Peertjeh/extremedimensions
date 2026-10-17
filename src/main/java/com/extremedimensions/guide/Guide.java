package com.extremedimensions.guide;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The first expedition, step by step. A small card on the HUD (switched off in the display settings) names the next
 * thing to do; each step is done by doing it, in any order: a later step done first counts the earlier ones too.
 *
 * <ol>
 * <li>Set a lodestone beside a portal and feed it (it becomes an anchor).</li>
 * <li>Bind a Tether to the anchor.</li>
 * <li>Step through the portal.</li>
 * <li>Come back through it before the charge runs out.</li>
 * </ol>
 *
 * Players who already came back from an expedition never see it.
 */
public final class Guide {
	private Guide() {}

	public static final int ANCHOR = 1, TETHER = 2, THROUGH = 3, HOME = 4;
	/** Steps in all: a player's progress at this number has finished the guide. */
	public static final int STEPS = 4;

	/** The steps this player has done, from 0 (none) to {@link #STEPS} (finished). */
	public static int progress(PlayerData d) {
		if (d.guide < STEPS && d.expeditions > 0) {
			// Came back from an expedition before the guide existed
			d.guide = STEPS;
		}
		return d.guide;
	}

	/** A step was done: the guide moves on to the next one (earlier steps count as done too). */
	public static void done(ServerPlayer p, PlayerData d, int step) {
		if (progress(d) >= step) {
			return;
		}
		d.guide = step;
		DataStore.markDirty();
		if (step >= STEPS) {
			WorldFx.soundSelf(p, "minecraft:ui.toast.challenge_complete", 0.5f, 1.2f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.guide.done"));
		} else {
			WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 0.8f, 1.3f);
		}
		Sync.state(p, d);
	}
}
