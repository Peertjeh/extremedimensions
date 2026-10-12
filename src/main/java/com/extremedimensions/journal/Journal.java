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
		WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.7f);
		WorldFx.soundSelf(p, "minecraft:block.sculk_shrieker.shriek", 0.15f, 1.6f);
		if (d.journal.size() >= JournalEntry.values().length) {
			Advancements.grant(p, Advancements.JOURNAL_COMPLETE);
		}
	}

	public static void open(ServerPlayer p, PlayerData d) {
		ServerPlayNetworking.send(p, new ModPackets.OpenJournalPayload(String.join(",", d.journal)));
		WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.9f);
	}

	/** Hands out the first journal once per world. */
	public static void deliverFirst(ServerPlayer p, PlayerData d) {
		if (d.journalDelivered) {
			return;
		}
		d.journalDelivered = true;
		if (!p.getInventory().add(new ItemStack(ModItems.TETHER_JOURNAL))) {
			WorldFx.run("give " + p.getStringUUID() + " extremedimensions:tether_journal");
		}
		d.journal.add(JournalEntry.TETHERED.id);
		WorldFx.soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.7f);
		Advancements.grant(p, Advancements.ROOT);
		DataStore.save();
	}
}
