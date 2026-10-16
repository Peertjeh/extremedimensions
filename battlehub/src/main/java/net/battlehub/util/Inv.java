package net.battlehub.util;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;

/** Inventory helpers that keep the client in step with the server. */
public final class Inv {
	private Inv() {}

	/**
	 * Selects a hotbar slot and tells the client. Changing the slot only on the server leaves the client holding
	 * the old slot: it shows one item while the server (and everybody else) uses another.
	 */
	public static void select(ServerPlayer p, int slot) {
		int s = Math.max(0, Math.min(8, slot));
		p.getInventory().setSelectedSlot(s);
		if (p.connection != null) {
			p.connection.send(new ClientboundSetHeldSlotPacket(s));
		}
	}
}
