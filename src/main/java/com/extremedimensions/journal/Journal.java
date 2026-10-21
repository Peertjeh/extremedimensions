package com.extremedimensions.journal;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Unlocking and opening Tether Journal pages. */
public final class Journal {
	private Journal() {}

	/** Unlocks a page once per player, with a quiet page-turn and a distant whisper. */
	public static void unlock(ServerPlayer p, PlayerData d, JournalEntry e) {
		if (!d.journal.add(e.id)) {
			return;
		}
		WorldFx.fx(p, com.extremedimensions.network.Fx.JOURNAL_PAGE, e.ordinal());
		WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.7f);
		WorldFx.soundSelf(p, "minecraft:block.sculk_shrieker.shriek", 0.15f, 1.6f);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.2, 0.4, 0.0, 10);
		if (d.journal.size() >= JournalEntry.values().length) {
			Advancements.grant(p, Advancements.JOURNAL_COMPLETE);
		}
	}

	/**
	 * A note in another hand turns up in the margin, once per player. The toast comes only when its page is already in
	 * the journal (otherwise the note waits there, unseen, for the page).
	 */
	public static void note(ServerPlayer p, PlayerData d, MarginNote n) {
		if (!d.notes.add(n.id)) {
			return;
		}
		DataStore.markDirty();
		if (d.journal.contains(n.page.id)) {
			WorldFx.fx(p, com.extremedimensions.network.Fx.JOURNAL_NOTE, n.ordinal());
			WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.55f);
		}
	}

	/**
	 * Reads a Wanderer's Page found in the world: the pages that can only be found come first, then any page the
	 * player has not reached yet. A page they already know crumbles into a little experience.
	 */
	public static void readPage(ServerPlayer p, PlayerData d) {
		JournalEntry next = null;
		for (JournalEntry e : new JournalEntry[] {JournalEntry.RUINED_GATE, JournalEntry.FIRST_WOUND}) {
			if (!d.journal.contains(e.id)) {
				next = e;
				break;
			}
		}
		if (next == null) {
			for (JournalEntry e : JournalEntry.values()) {
				if (e.wanderer && !d.journal.contains(e.id)) {
					next = e;
					break;
				}
			}
		}
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.5, 0.02, 14);
		if (next == null) {
			p.giveExperiencePoints(20);
			WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 1.2f);
			return;
		}
		unlock(p, d, next);
		DataStore.markDirty();
	}

	/**
	 * Reads a Loose Leaf: the Held's letter goes into the journal, with the notes in the same hand beside the pages it
	 * answers. The leaf is not used up; read a second time it has nothing more to say.
	 */
	public static net.minecraft.world.InteractionResult readLeaf(ServerPlayer p, PlayerData d) {
		if (d.journal.contains(JournalEntry.HELD.id)) {
			p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.loose_leaf.known"), true);
			WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 0.6f, 1.3f);
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		unlock(p, d, JournalEntry.HELD);
		note(p, d, MarginNote.WANDERER);
		note(p, d, MarginNote.FIRST_WOUND);
		WorldFx.soundSelf(p, "minecraft:block.chain.step", 0.8f, 0.6f);
		DataStore.markDirty();
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	public static void open(ServerPlayer p, PlayerData d) {
		ServerPlayNetworking.send(p, new ModPackets.OpenJournalPayload(String.join(",", d.journal), Bestiary.encode(d), Ledger.encode(d),
				String.join(",", d.notes)));
		WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.9f);
	}

	/** Hands out the first journal once per world. */
	public static void deliverFirst(ServerPlayer p, PlayerData d) {
		if (d.journalDelivered) {
			return;
		}
		d.journalDelivered = true;
		WorldFx.give(p, new ItemStack(ModItems.TETHER_JOURNAL));
		d.journal.add(JournalEntry.TETHERED.id);
		WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.7f);
		Advancements.grant(p, Advancements.ROOT);
		DataStore.markDirty();
	}
}
