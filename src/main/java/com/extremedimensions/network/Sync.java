package com.extremedimensions.network;

import java.util.List;
import java.util.Map;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Sends player state and penalty offers to the client. */
public final class Sync {
	private Sync() {}

	public static void state(ServerPlayer p, PlayerData d) {
		int dimCode = Dims.NETHER.equals(d.dim) ? 1 : Dims.END.equals(d.dim) ? 2 : 0;
		boolean overworld = Dims.OVERWORLD.equals(d.dim);
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? AnchorService.nearCharge(p, Dims.NETHER) : -1,
				overworld ? AnchorService.nearCharge(p, Dims.END) : -1,
				d.inRun ? (int) Math.ceil(AnchorService.linkedCharge(p, d)) : 0, (int) Math.ceil(d.runMax), dimCode,
				(int) Math.round(d.currentDrain * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, penalties(d), d.penaltyClock, TetherService.distance(p, d)));
	}

	public static void offer(ServerPlayer p, PlayerData d) {
		if (!d.pendingOffer.isEmpty()) {
			ServerPlayNetworking.send(p, new ModPackets.OfferPayload(String.join(",", d.pendingOffer)));
		}
	}

	/** "id:stacks:seconds;..." where seconds is the time until the next stack wears off (-1 next run, -2 XP debt). */
	private static String penalties(PlayerData d) {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, List<Double>> e : d.penaltyTime.entrySet()) {
			if (e.getValue().isEmpty()) {
				continue;
			}
			double min = Double.MAX_VALUE;
			for (double r : e.getValue()) {
				min = Math.min(min, r);
			}
			append(out, e.getKey(), e.getValue().size(), (int) Math.ceil(min));
		}
		if (d.mourningSecs > 0) {
			append(out, Penalty.MOURNING.id, 1, (int) Math.ceil(d.mourningSecs));
		}
		if (d.xpDebt > 0) {
			append(out, Penalty.TOLL.id, d.xpDebt, -2);
		}
		if (d.leakStacks > 0) {
			append(out, Penalty.LEAKY_TANK.id, d.leakStacks, -1);
		}
		return out.toString();
	}

	private static void append(StringBuilder out, String id, int stacks, int secs) {
		if (out.length() > 0) {
			out.append(';');
		}
		out.append(id).append(':').append(stacks).append(':').append(secs);
	}
}
