package com.extremedimensions.tribe;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the tribe (the Lurkers, their young and their Keepers) thinks of each player, from -100 to 100. Gifts, barter
 * and a bond win standing; killing its own, breaking up its hoards and raiding them lose it. A grudge fades slowly
 * with time. Each level changes how the tribe treats the player:
 *
 * <ul>
 *   <li><b>Hunted</b>: its young steal twice as often, and a Keeper comes for them now and then.</li>
 *   <li><b>Hated</b>: its young steal twice as often and will not barter.</li>
 *   <li><b>Wary</b> (where everyone starts): as it always was.</li>
 *   <li><b>Tolerated</b>: its young no longer steal from them, and barter with them.</li>
 *   <li><b>Trusted</b>: better prices, a Hollowling always takes to them, their own hoards open without a fight.</li>
 *   <li><b>Kin</b>: the best prices and rare wares, and Lurkers often let them be.</li>
 * </ul>
 */
public final class Standing {
	private Standing() {}

	public static final int MIN = -100, MAX = 100;

	public enum Level {
		HUNTED, HATED, WARY, TOLERATED, TRUSTED, KIN;

		public String key() {
			return "gui.extremedimensions.tribe.level." + name().toLowerCase(java.util.Locale.ROOT);
		}

		public ChatFormatting colour() {
			return switch (this) {
				case HUNTED -> ChatFormatting.DARK_RED;
				case HATED -> ChatFormatting.RED;
				case WARY -> ChatFormatting.GRAY;
				case TOLERATED -> ChatFormatting.YELLOW;
				case TRUSTED -> ChatFormatting.GREEN;
				case KIN -> ChatFormatting.AQUA;
			};
		}
	}

	public static Level level(int rep) {
		if (rep >= Config.TRIBE_KIN_AT) {
			return Level.KIN;
		}
		if (rep >= Config.TRIBE_TRUSTED_AT) {
			return Level.TRUSTED;
		}
		if (rep >= Config.TRIBE_TOLERATED_AT) {
			return Level.TOLERATED;
		}
		if (rep <= Config.TRIBE_HUNTED_AT) {
			return Level.HUNTED;
		}
		if (rep <= Config.TRIBE_HATED_AT) {
			return Level.HATED;
		}
		return Level.WARY;
	}

	/** The player's level with the tribe (Wary for everyone while the tribe forgets nothing, see tribe_enabled). */
	public static Level of(ServerPlayer p) {
		return Config.TRIBE_ENABLED <= 0 ? Level.WARY : level(DataStore.get(p.getUUID()).tribeRep);
	}

	public static boolean atLeast(ServerPlayer p, Level l) {
		return of(p).ordinal() >= l.ordinal();
	}

	/** Odds (out of 3) that a Hollowling near this player starts to sneak up on them: none once tolerated, double when hated. */
	public static int theftOdds(ServerPlayer p) {
		Level l = of(p);
		return l.ordinal() >= Level.TOLERATED.ordinal() ? 0 : l.ordinal() <= Level.HATED.ordinal() ? 2 : 1;
	}

	/** Whether a Lurker about to take from this player lets them be (kin only, by chance). */
	public static boolean spared(ServerPlayer p) {
		return of(p) == Level.KIN && ThreadLocalRandom.current().nextDouble() >= Math.max(0, Math.min(1, Config.TRIBE_KIN_THEFT_CHANCE));
	}

	/** Changes the player's standing; says so above the hotbar, and in the chat when their level changes. */
	public static void change(ServerPlayer p, int delta, String why) {
		if (Config.TRIBE_ENABLED <= 0 || delta == 0 || p.isCreative() || p.isSpectator()) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		Level before = level(d.tribeRep);
		d.tribeRep = Math.max(MIN, Math.min(MAX, d.tribeRep + delta));
		Level after = level(d.tribeRep);
		String sign = delta > 0 ? "+" + delta : String.valueOf(delta);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.tribe.changed", sign, Component.translatable(why))
				.withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		if (after != before) {
			announce(p, after, after.ordinal() > before.ordinal());
		}
		if (delta < 0 && d.tribeForgetIn <= 0) {
			d.tribeForgetIn = Math.max(1, Config.TRIBE_FORGET_MINUTES) * 60;
		}
		if (after == Level.HUNTED && d.tribeHuntIn <= 0) {
			d.tribeHuntIn = Math.max(1, Config.TRIBE_HUNT_MINUTES) * 60;
		}
		DataStore.markDirty();
	}

	private static void announce(ServerPlayer p, Level l, boolean up) {
		Component name = Component.translatable(l.key()).withStyle(l.colour(), ChatFormatting.BOLD);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.tribe.now", name));
		p.sendSystemMessage(Component.translatable(l.key() + ".desc").withStyle(ChatFormatting.GRAY));
		if (up) {
			WorldFx.soundSelf(p, "extremedimensions:hollowling_bond", 1.0f, 1.0f);
		} else {
			WorldFx.soundSelf(p, "extremedimensions:lurker_vanish", 0.8f, 0.7f);
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (l.ordinal() >= Level.TOLERATED.ordinal()) {
			Journal.unlock(p, d, JournalEntry.BARTER);
		}
		if (l.ordinal() >= Level.TRUSTED.ordinal()) {
			Advancements.grant(p, Advancements.TRIBE_TRUSTED);
		}
		if (l == Level.KIN) {
			Advancements.grant(p, Advancements.TRIBE_KIN);
		}
	}

	/** Once a second for each player in the mod's worlds: a grudge fades, and the hunted are hunted. */
	public static void second(ServerPlayer p, PlayerData d) {
		if (Config.TRIBE_ENABLED <= 0 || p.isCreative() || p.isSpectator() || !Dims.ours(p)) {
			return;
		}
		if (d.tribeRep < 0 && Config.TRIBE_FORGET_MINUTES > 0) {
			if (d.tribeForgetIn <= 0) {
				d.tribeForgetIn = Config.TRIBE_FORGET_MINUTES * 60;
			} else if (--d.tribeForgetIn == 0) {
				Level before = level(d.tribeRep);
				d.tribeRep++;
				if (level(d.tribeRep) != before) {
					announce(p, level(d.tribeRep), true);
				}
				d.tribeForgetIn = d.tribeRep < 0 ? Config.TRIBE_FORGET_MINUTES * 60 : 0;
				DataStore.markDirty();
			}
		}
		if (level(d.tribeRep) == Level.HUNTED && Config.TRIBE_HUNT_MINUTES > 0) {
			if (d.tribeHuntIn <= 0) {
				d.tribeHuntIn = Config.TRIBE_HUNT_MINUTES * 60;
			} else if (--d.tribeHuntIn == 0) {
				d.tribeHuntIn = Config.TRIBE_HUNT_MINUTES * 60;
				com.extremedimensions.hollow.HollowService.hunt(p);
			}
		}
	}

	/** The standing as a line of text, for the command and the barter screen. */
	public static Component describe(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		Level l = level(d.tribeRep);
		return Component.translatable("gui.extremedimensions.tribe.standing",
				Component.translatable(l.key()).withStyle(l.colour(), ChatFormatting.BOLD), d.tribeRep);
	}
}
