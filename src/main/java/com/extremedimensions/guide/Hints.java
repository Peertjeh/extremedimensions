package com.extremedimensions.guide;

import java.util.Locale;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A short word the first time a player meets each part of the mod, as a toast in the corner, the way the game's own
 * tutorial tells a new player what to do. Each comes once per player. The world turns them off with {@code hints} = 0,
 * each player for themselves under Settings, Display. A player who walked part of the way before the hints existed is
 * not told what they already know.
 */
public final class Hints {
	private Hints() {}

	public enum Hint {
		/** Their first Collapse: what follows. */
		COLLAPSE,
		/** Their first curse taken: how it is lifted. */
		CURSE,
		/** A Tether Fragment in hand for the first time. */
		FRAGMENT,
		/** The first time a Lurker watches them. */
		LURKER,
		/** Their first hoard. */
		HOARD,
		/** A relic in hand for the first time. */
		RELIC,
		/** A Seam Needle in hand for the first time. */
		NEEDLE,
		/** Seam Silk in hand for the first time. */
		SILK,
		/** The first Elder they see forming. */
		ELDER,
		/** The first Wound Storm to reach them. */
		STORM,
		/** The first time they fall beside an ally. */
		FALLEN,
		/** Their first Abyssal curse taken: how it breaks. */
		ABYSSAL,
		/** A curse set full enough to be unwoven: the Weaver's Loom can unpick it. */
		UNWEAVING;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String titleKey() {
			return "gui.extremedimensions.hint." + key() + ".title";
		}

		public String textKey() {
			return "gui.extremedimensions.hint." + key() + ".text";
		}
	}

	/** The player meets this part of the mod: the hint comes, if it is their first time and hints are on. */
	public static void meet(ServerPlayer p, Hint h) {
		if (Config.HINTS <= 0 || p.isSpectator()) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (!d.hints.add(h.key())) {
			return;
		}
		DataStore.markDirty();
		WorldFx.fx(p, Fx.HINT, h.ordinal());
	}

	/** An advancement was earned: the hint its moment brings, if any. */
	public static void earned(ServerPlayer p, String advancement) {
		switch (advancement) {
			case Advancements.COLLAPSE -> meet(p, Hint.COLLAPSE);
			case Advancements.MARKED -> meet(p, Hint.CURSE);
			case Advancements.WATCHED -> meet(p, Hint.LURKER);
			case Advancements.HOARD_FOUND -> meet(p, Hint.HOARD);
			default -> { }
		}
	}

	/** Now and then for each player: what they hold for the first time (one look through the inventory). */
	public static void look(ServerPlayer p, PlayerData d) {
		if (Config.HINTS <= 0 || d.hints.contains(Hint.FRAGMENT.key()) && d.hints.contains(Hint.RELIC.key())
				&& d.hints.contains(Hint.NEEDLE.key()) && d.hints.contains(Hint.SILK.key())) {
			return;
		}
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			if (s.is(ModItems.TETHER_FRAGMENT)) {
				meet(p, Hint.FRAGMENT);
			} else if (s.is(ModItems.SEAM_NEEDLE)) {
				meet(p, Hint.NEEDLE);
			} else if (s.is(ModItems.SEAM_SILK)) {
				meet(p, Hint.SILK);
			} else if (RelicService.of(s) != null) {
				meet(p, Hint.RELIC);
			}
		}
	}

	/**
	 * Once per player, on joining: what they already know from before the hints existed is marked as told (a new
	 * player knows nothing yet, and so is told everything in its time).
	 */
	public static void known(ServerPlayer p, PlayerData d) {
		if (d.hintsChecked) {
			return;
		}
		d.hintsChecked = true;
		for (Hint h : Hint.values()) {
			if (knows(p, d, h)) {
				d.hints.add(h.key());
			}
		}
		DataStore.markDirty();
	}

	private static boolean knows(ServerPlayer p, PlayerData d, Hint h) {
		return switch (h) {
			case COLLAPSE -> d.collapses > 0 || WorldFx.hasAdvancement(p, Advancements.COLLAPSE);
			case CURSE -> !d.curses.isEmpty() || d.scars > 0 || WorldFx.hasAdvancement(p, Advancements.MARKED);
			case FRAGMENT -> d.journal.contains(JournalEntry.FRAGMENT.id);
			case LURKER -> WorldFx.hasAdvancement(p, Advancements.WATCHED);
			case HOARD -> WorldFx.hasAdvancement(p, Advancements.HOARD_FOUND);
			case RELIC -> d.relic != null || WorldFx.hasAdvancement(p, Advancements.RELIC_BOUND);
			case NEEDLE -> d.seamVisits > 0;
			case SILK -> d.journal.contains(JournalEntry.LOOM.id);
			case ELDER -> d.seen.contains(Bestiary.Beast.MATRIARCH.id) || d.seen.contains(Bestiary.Beast.FERRYMAN.id)
					|| d.seen.contains(Bestiary.Beast.LAST_WEAVER.id);
			case STORM -> d.journal.contains(JournalEntry.STORMS.id);
			case FALLEN -> d.liftsTaken > 0;
			case UNWEAVING -> d.unwoven > 0;
			case ABYSSAL -> {
				for (com.extremedimensions.penalty.Penalty pen : com.extremedimensions.penalty.Penalty.values()) {
					if (pen.abyssal && d.knows(pen)) {
						yield true;
					}
				}
				yield false;
			}
		};
	}
}
