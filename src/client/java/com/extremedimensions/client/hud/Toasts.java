package com.extremedimensions.client.hud;

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

	/** A creature was written into the bestiary (by its ordinal). */
	public static void bestiary(int ordinal) {
		Bestiary.Beast[] beasts = Bestiary.Beast.values();
		if (ordinal >= 0 && ordinal < beasts.length) {
			show(Component.translatable("gui.extremedimensions.toast.bestiary"), Component.translatable(beasts[ordinal].nameKey()));
		}
	}

	private static void show(Component title, Component text) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, text);
	}
}
