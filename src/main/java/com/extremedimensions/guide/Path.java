package com.extremedimensions.guide;

import java.util.Locale;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * The Wanderer's path: the steps from a first expedition to the ending, as the journal's Path tab shows them, with a
 * line on how to take the next one. A step is done by doing it, whenever that was (also before this page existed):
 * the steps of the world (the seal, the Starved One, the ending) are done for everyone once anyone has done them.
 * Beside the path, the things that make it easier and the older things that stir along it, each once its page is in
 * the journal.
 */
public final class Path {
	private Path() {}

	public enum Step {
		/** Bind a Tether to an anchor and come back from beyond the wound (the first expedition, see {@link Guide}). */
		RETURN,
		/** Tear a rift with a Seam Needle and step through. */
		SEAM,
		/** Come home through the rift you arrived by. */
		SEAM_HOME,
		/** Slay the Ender Dragon. */
		SEAL,
		/** Reach the Heart of the Seam. */
		HEART,
		/** Defeat the Starved One. */
		STARVED,
		/** Use the Mending Thread or the Severing Shears. */
		CHOOSE;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Beside the path: shown once the page in brackets is in the journal. */
	public enum Side {
		COMPANION(JournalEntry.HOLLOWS),
		WOVEN(JournalEntry.LOOM),
		HELD(JournalEntry.RUINED_GATE),
		MATRIARCH(JournalEntry.MATRIARCH),
		FERRYMAN(JournalEntry.FERRYMAN),
		LAST_WEAVER(JournalEntry.LAST_WEAVER),
		FIRST_WEAVER(JournalEntry.FRAME),
		/** Known once the world has its ending; done when the tapestry on the loom is finished. */
		TAPESTRY(JournalEntry.ENDING);

		public final JournalEntry page;

		Side(JournalEntry page) {
			this.page = page;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static boolean done(ServerPlayer p, PlayerData d, Step s) {
		return switch (s) {
			case RETURN -> Guide.progress(d) >= Guide.STEPS;
			case SEAM -> d.seamVisits > 0 || WorldFx.hasAdvancement(p, Advancements.SEAM_ENTERED);
			case SEAM_HOME -> WorldFx.hasAdvancement(p, Advancements.SEAM_RETURNED);
			case SEAL -> DataStore.sealBroken();
			case HEART -> DataStore.starvedSlain() || d.journal.contains(JournalEntry.HEART.id) || WorldFx.hasAdvancement(p, Advancements.HEART_REACHED);
			case STARVED -> DataStore.starvedSlain();
			case CHOOSE -> !DataStore.ending().isEmpty();
		};
	}

	public static boolean done(ServerPlayer p, PlayerData d, Side s) {
		return switch (s) {
			case COMPANION -> d.companion || WorldFx.hasAdvancement(p, Advancements.HOLLOW_BOUND);
			case WOVEN -> d.tetherTier > 0 || d.journal.contains(JournalEntry.WOVEN.id);
			case HELD -> d.heldFound;
			case MATRIARCH -> d.matriarchSlain || WorldFx.hasAdvancement(p, Advancements.MATRIARCH_SLAIN);
			case FERRYMAN -> WorldFx.hasAdvancement(p, Advancements.FERRYMAN_SLAIN);
			case LAST_WEAVER -> WorldFx.hasAdvancement(p, Advancements.WEAVER_SLAIN);
			case FIRST_WEAVER -> d.knotReached;
			case TAPESTRY -> DataStore.tapestryDone();
		};
	}

	/** The first step not done yet, or null when the whole path is walked. */
	public static Step current(ServerPlayer p, PlayerData d) {
		for (Step s : Step.values()) {
			if (!done(p, d, s)) {
				return s;
			}
		}
		return null;
	}

	/** How to take the next step: the translation key of the line the journal shows under it. */
	public static String hint(ServerPlayer p, PlayerData d) {
		Step s = current(p, d);
		if (s == null) {
			return "gui.extremedimensions.path.beyond";
		}
		return switch (s) {
			// The first expedition says what the guide card says, step by step
			case RETURN -> "gui.extremedimensions.guide." + Math.min(Guide.STEPS, Guide.progress(d) + 1);
			case SEAM -> "gui.extremedimensions.path.seam." + (holds(p, ModItems.SEAM_NEEDLE) ? "tear" : "craft");
			case CHOOSE -> "gui.extremedimensions.path.choose" + (holds(p, ModItems.MENDING_THREAD) || holds(p, ModItems.SEVERING_SHEARS) ? ".held" : "");
			default -> "gui.extremedimensions.path." + s.key();
		};
	}

	private static boolean holds(ServerPlayer p, Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The path as the journal gets it: "steps done;beside the path done;beside the path seen;hint", the first three as
	 * bit masks (bit = ordinal).
	 */
	public static String encode(ServerPlayer p, PlayerData d) {
		int steps = 0, sideDone = 0, sideSeen = 0;
		for (Step s : Step.values()) {
			if (done(p, d, s)) {
				steps |= 1 << s.ordinal();
			}
		}
		for (Side s : Side.values()) {
			boolean done = done(p, d, s);
			if (done) {
				sideDone |= 1 << s.ordinal();
			}
			if (done || d.journal.contains(s.page.id)) {
				sideSeen |= 1 << s.ordinal();
			}
		}
		return steps + ";" + sideDone + ";" + sideSeen + ";" + hint(p, d);
	}
}
