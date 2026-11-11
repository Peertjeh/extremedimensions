package com.extremedimensions.penalty;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Fresh Wound (3.49): every death in the mod's worlds (where a death costs its penance) leaves the player wounded
 * for {@code death_wound_minutes} of play. While it lasts their maximum health is {@code death_wound_health} lower (see
 * CurseEffects), the Tether drains {@code death_wound_drain} times as fast beyond the wound (see ExpeditionService), and
 * for its first {@code death_wound_bind_minutes} no Tether binds to an anchor (see TetherService). A death while wounded
 * starts it over; it never stacks. It counts seconds of play in the mod's worlds, as the tribe's gifts do, so it waits
 * through a logout and sleeps in another mod's world.
 */
public final class Wound {
	private Wound() {}

	/** A death: the wound opens, or starts over if it was still open. */
	public static void open(PlayerData d) {
		int secs = Math.max(0, Config.DEATH_WOUND_MINUTES) * 60;
		d.woundIn = secs;
		d.woundBindIn = Math.min(secs, Math.max(0, Config.DEATH_WOUND_BIND_MINUTES) * 60);
		DataStore.markDirty();
	}

	/** Whether the player is wounded now. */
	public static boolean active(PlayerData d) {
		return d.woundIn > 0;
	}

	/** The maximum health the wound takes now: none once it closed, nor in another mod's world, where it sleeps. */
	public static double health(PlayerData d) {
		return active(d) && !d.away ? Math.max(0, Config.DEATH_WOUND_HEALTH) : 0;
	}

	/** How many times as fast the Tether drains for the wound (1 without one). */
	public static double drainFactor(PlayerData d) {
		return active(d) ? Math.max(1, Config.DEATH_WOUND_DRAIN) : 1.0;
	}

	/** Seconds of play before a Tether binds to an anchor again (0: it may now). */
	public static int bindIn(PlayerData d) {
		return active(d) ? Math.max(0, d.woundBindIn) : 0;
	}

	/** A second of play in the mod's worlds, alive: the wound closes a little, and once it is closed, the health comes back. */
	public static void second(ServerPlayer p, PlayerData d) {
		if (d.woundIn <= 0 || !p.isAlive()) {
			return;
		}
		if (d.woundBindIn > 0 && --d.woundBindIn == 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.wound.bind_free"), true);
		}
		if (--d.woundIn > 0) {
			if (d.woundIn % 60 == 0) {
				// (written with the world's saves a minute at a time, so a restart loses little of it)
				DataStore.markDirty();
			}
			return;
		}
		d.woundBindIn = 0;
		CurseEffects.applyEffects(p, d);
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.chime", 0.8f, 1.4f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.wound.closed"), true);
		Sync.state(p, d);
		Sync.curses(p, d, false);
		DataStore.markDirty();
	}

	/**
	 * Whether the wound is too fresh for a Tether to bind to an anchor; if it is, the player is told for how long yet (and
	 * the Tether stays in their hand).
	 */
	public static boolean refusesBinding(ServerPlayer p, PlayerData d) {
		int left = bindIn(d);
		if (left <= 0) {
			return false;
		}
		WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.wound.bind_refused", minutes(left)), true);
		return true;
	}

	/** At the respawn after the death that opened it: what the wound does, and for how long. */
	public static void tell(ServerPlayer p, PlayerData d) {
		if (!active(d)) {
			return;
		}
		String hearts = Config.format(Math.max(0, Config.DEATH_WOUND_HEALTH) / 2.0);
		long faster = Math.round((Math.max(1, Config.DEATH_WOUND_DRAIN) - 1) * 100);
		MutableComponent line = bindIn(d) > 0
				? Component.translatable("gui.extremedimensions.wound.opened_bind", hearts, faster, minutes(d.woundIn), minutes(bindIn(d)))
				: Component.translatable("gui.extremedimensions.wound.opened", hearts, faster, minutes(d.woundIn));
		p.sendSystemMessage(line.withStyle(ChatFormatting.RED));
	}

	/** Seconds as whole minutes, rounded up (as the tribe's wait for a gift is told). */
	private static int minutes(int seconds) {
		return (seconds + 59) / 60;
	}
}
