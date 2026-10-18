package com.extremedimensions.client.hud;

import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.JournalEntry;

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

	/** A creature was written into the bestiary (by its ordinal). */
	public static void bestiary(int ordinal) {
		Bestiary.Beast[] beasts = Bestiary.Beast.values();
		if (ordinal >= 0 && ordinal < beasts.length) {
			show(Component.translatable("gui.extremedimensions.toast.bestiary"), Component.translatable(beasts[ordinal].nameKey()));
		}
	}

	private static void show(Component title, Component text) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, text);
	}
}
