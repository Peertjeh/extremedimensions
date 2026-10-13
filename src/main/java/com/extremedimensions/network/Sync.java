package com.extremedimensions.network;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Sends player state, curse offers and the Curses screen to the client. */
public final class Sync {
	private Sync() {}

	public static void state(ServerPlayer p, PlayerData d) {
		boolean seam = d.inSeam && Dims.SEAM.equals(d.dim);
		int dimCode = seam ? 3 : Dims.NETHER.equals(d.dim) ? 1 : Dims.END.equals(d.dim) ? 2 : 0;
		boolean overworld = Dims.OVERWORLD.equals(d.dim);
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? AnchorService.nearCharge(p, Dims.NETHER) : -1,
				overworld ? AnchorService.nearCharge(p, Dims.END) : -1,
				seam ? (int) Math.ceil(d.seamLeft) : d.inRun ? (int) Math.ceil(AnchorService.linkedCharge(p, d)) : 0,
				(int) Math.ceil(seam ? d.seamMax : d.runMax), dimCode,
				seam ? 100 : (int) Math.round(d.currentDrain * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, penalties(d), d.scars,
				seam ? SeamService.riftDistance(p, d) : TetherService.distance(p, d)));
	}

	/** The cards on offer: "id:multiplier:stacks:max:valuePerStack", comma separated. */
	public static void offer(ServerPlayer p, PlayerData d) {
		if (d.pendingOffer.isEmpty()) {
			return;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < d.pendingOffer.size(); i++) {
			Penalty pen = Penalty.byId(d.pendingOffer.get(i));
			if (pen == null) {
				continue;
			}
			int mult = i < d.pendingMult.size() ? d.pendingMult.get(i) : 1;
			if (out.length() > 0) {
				out.append(',');
			}
			out.append(pen.id).append(':').append(mult).append(':').append(d.stacks(pen)).append(':').append(pen.max).append(':').append(pen.display());
		}
		ServerPlayNetworking.send(p, new ModPackets.OfferPayload(out.toString()));
	}

	/** Every curse, for the Curses screen (see {@link ModPackets.CursesPayload}). */
	public static void curses(ServerPlayer p, PlayerData d, boolean open) {
		StringBuilder out = new StringBuilder();
		out.append(d.scars).append('|').append(d.atoning == null ? "" : d.atoning).append('|').append(DataStore.overworldPenalties() ? 1 : 0).append('|');
		boolean first = true;
		for (Penalty pen : Penalty.values()) {
			if (!first) {
				out.append(';');
			}
			first = false;
			out.append(pen.id).append(':').append(d.stacks(pen)).append(':').append(pen.max).append(':')
					.append(d.atonement.getOrDefault(pen.id, 0)).append(':').append(PenaltyService.cost(d, pen)).append(':').append(pen.display());
		}
		ServerPlayNetworking.send(p, new ModPackets.CursesPayload(out.toString(), open));
	}

	/** "id:stacks:max;..." for every curse the player carries. */
	private static String penalties(PlayerData d) {
		StringBuilder out = new StringBuilder();
		for (Penalty pen : Penalty.values()) {
			int n = d.stacks(pen);
			if (n <= 0) {
				continue;
			}
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(pen.id).append(':').append(n).append(':').append(pen.max);
		}
		return out.toString();
	}
}
