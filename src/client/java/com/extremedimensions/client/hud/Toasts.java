package com.extremedimensions.client.hud;

import com.extremedimensions.guide.Hints;
import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** News the game would give as a toast in the corner: a page written into the journal, a creature into the bestiary. */
public final class Toasts {
	private Toasts() {}

	/** A page of the Tether Journal was written (by its ordinal). */
	public static void journal(int ordinal) {
		JournalEntry[] pages = JournalEntry.values();
		if (ordinal >= 0 && ordinal < pages.length) {
			show(Component.translatable("gui.extremedimensions.toast.journal"), Component.translatable(pages[ordinal].titleKey()));
		}
	}

	/** A note in another hand turned up beside a page (by the note's ordinal): the toast names the page. */
	public static void note(int ordinal) {
		MarginNote[] notes = MarginNote.values();
		if (ordinal >= 0 && ordinal < notes.length) {
			show(Component.translatable("gui.extremedimensions.toast.note"), Component.translatable(notes[ordinal].page.titleKey()));
		}
	}

	/**
	 * A curse laid without a choice (a Lurker's touch, a swarm...): which, how many stacks, and where it came from. The
	 * value packs the curse's ordinal, the stacks (shifted 10 bits) and the source's ordinal (shifted 16).
	 */
	public static void curse(int value) {
		com.extremedimensions.penalty.Penalty[] curses = com.extremedimensions.penalty.Penalty.values();
		com.extremedimensions.penalty.CurseLog.Source[] sources = com.extremedimensions.penalty.CurseLog.Source.values();
		int ordinal = value & 1023, stacks = Math.max(1, value >> 10 & 63), source = value >> 16 & 255;
		if (ordinal >= curses.length) {
			return;
		}
		com.extremedimensions.penalty.CurseLog.Source from = source < sources.length ? sources[source] : com.extremedimensions.penalty.CurseLog.Source.UNKNOWN;
		Component title = Component.translatable("gui.extremedimensions.toast.curse", Component.translatable(curses[ordinal].nameKey()), stacks)
				.withStyle(net.minecraft.ChatFormatting.RED);
		Component text = Component.translatable("gui.extremedimensions.toast.curse_from", Component.translatable(from.nameKey()));
		Minecraft mc = Minecraft.getInstance();
		SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, text);
	}

	/** A creature was written into the bestiary (by its ordinal). */
	public static void bestiary(int ordinal) {
		Bestiary.Beast[] beasts = Bestiary.Beast.values();
		if (ordinal >= 0 && ordinal < beasts.length) {
			show(Component.translatable("gui.extremedimensions.toast.bestiary"), Component.translatable(beasts[ordinal].nameKey()));
		}
	}

	/**
	 * The first time the player meets a part of the mod (by the hint's ordinal): a word on it, unless they turned hints
	 * off. Shown, the server is told so (3.55.0), and only then counts it told: one turned off here comes again later.
	 */
	public static void hint(int ordinal) {
		Hints.Hint[] hints = Hints.Hint.values();
		if (ordinal < 0 || ordinal >= hints.length || !com.extremedimensions.client.options.ClientOptions.hints()) {
			return;
		}
		tell(Component.translatable(hints[ordinal].titleKey()), Component.translatable(hints[ordinal].textKey()));
		if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.extremedimensions.network.ModPackets.HintShownPayload.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.extremedimensions.network.ModPackets.HintShownPayload(ordinal));
		}
	}

	/** A word of the mod's own (the keys after the first join, say), wrapped and kept as long as a hint (3.55.0). */
	public static void tell(Component title, Component text) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast toast = multiline(mc, title, text);
		if (toast != null) {
			mc.gui.toastManager().addToast(toast);
		} else {
			SystemToast.add(mc.gui.toastManager(), HINT, title, text);
		}
	}

	/** Hints stay as long as the game's own tutorial hints, so their lines can be read. */
	private static final SystemToast.SystemToastId HINT = new SystemToast.SystemToastId(10000L);
	private static boolean warned;

	/**
	 * A toast with its text wrapped over lines, as the game makes them. Looked up by name, since what it takes has
	 * changed between versions of the game (the game, its font, the toast's id and two texts, in some order); null if
	 * there is no such toast here.
	 */
	private static SystemToast multiline(Minecraft mc, Component title, Component text) {
		for (java.lang.reflect.Method m : SystemToast.class.getMethods()) {
			if (!m.getName().equals("multiline") || !java.lang.reflect.Modifier.isStatic(m.getModifiers())
					|| !SystemToast.class.isAssignableFrom(m.getReturnType())) {
				continue;
			}
			Class<?>[] types = m.getParameterTypes();
			Object[] args = new Object[types.length];
			int texts = 0;
			boolean fits = true;
			for (int i = 0; i < types.length && fits; i++) {
				if (types[i].isInstance(mc)) {
					args[i] = mc;
				} else if (types[i].isInstance(mc.font)) {
					args[i] = mc.font;
				} else if (types[i] == SystemToast.SystemToastId.class) {
					args[i] = HINT;
				} else if (types[i].isAssignableFrom(Component.class) && texts < 2) {
					args[i] = texts++ == 0 ? title : text;
				} else {
					fits = false;
				}
			}
			if (fits && texts == 2) {
				try {
					return (SystemToast) m.invoke(null, args);
				} catch (ReflectiveOperationException | RuntimeException e) {
					break;
				}
			}
		}
		if (!warned) {
			warned = true;
			com.extremedimensions.ExtremeDimensions.LOGGER.warn("No toast with wrapped text found; hints show on one line");
		}
		return null;
	}

	private static void show(Component title, Component text) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, text);
	}
}
