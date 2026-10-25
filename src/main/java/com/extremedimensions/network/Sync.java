package com.extremedimensions.network;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.CurseRunes;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Sends player state, curse offers and the Curses screen to the client. */
public final class Sync {
	private Sync() {}

	public static void state(ServerPlayer p, PlayerData d) {
		boolean seam = d.inSeam && Dims.SEAM.equals(d.dim);
		// 4: another mod's world, where nothing of the mod shows
		int dimCode = d.away ? 4 : seam ? 3 : Dims.NETHER.equals(d.dim) ? 1 : Dims.END.equals(d.dim) ? 2 : 0;
		boolean overworld = !d.away && Dims.OVERWORLD.equals(d.dim);
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? AnchorService.nearCharge(p, Dims.NETHER) : -1,
				overworld ? AnchorService.nearCharge(p, Dims.END) : -1,
				seam ? (int) Math.ceil(d.seamLeft) : d.inRun ? (int) Math.ceil(AnchorService.linkedCharge(p, d)) : 0,
				(int) Math.ceil(seam ? d.seamMax : d.runMax), dimCode,
				seam ? 100 : (int) Math.round(d.currentDrain * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, penalties(d), d.scars,
				seam ? SeamService.riftDistance(p, d) : TetherService.distance(p, d), com.extremedimensions.guide.Guide.progress(d),
				seam ? com.extremedimensions.trial.TrialService.hud(d) : "",
				seam ? (float) d.riftX : 0, seam ? (float) d.riftY : 0, seam ? (float) d.riftZ : 0));
	}

	/**
	 * The cards on offer: "@set" (beyond or overworld), then "id:multiplier:stacks:max:valuePerStack:readable", comma
	 * separated (readable 0: the card shows runes, see {@link CurseRunes}).
	 */
	public static void offer(ServerPlayer p, PlayerData d) {
		if (d.pendingOffer.isEmpty()) {
			return;
		}
		StringBuilder out = new StringBuilder("@").append(d.owed.isEmpty() ? "beyond" : d.owed.get(0));
		for (int i = 0; i < d.pendingOffer.size(); i++) {
			Penalty pen = Penalty.byId(d.pendingOffer.get(i));
			if (pen == null) {
				continue;
			}
			int mult = i < d.pendingMult.size() ? d.pendingMult.get(i) : 1;
			out.append(',');
			out.append(pen.id).append(':').append(mult).append(':').append(d.owned(pen)).append(':').append(pen.max).append(':').append(pen.display())
					.append(':').append(CurseRunes.readable(d, pen) ? 1 : 0);
		}
		ServerPlayNetworking.send(p, new ModPackets.OfferPayload(out.toString()));
	}

	/** Every curse, for the Curses screen (see {@link ModPackets.CursesPayload}). */
	public static void curses(ServerPlayer p, PlayerData d, boolean open) {
		ServerPlayNetworking.send(p, new ModPackets.CursesPayload(cursesData(p, d), open));
	}

	/** The Curses screen's data: scars, the curse atoned for, Overworld curses on or off, every curse, players nearby. */
	public static String cursesData(ServerPlayer p, PlayerData d) {
		StringBuilder out = new StringBuilder();
		out.append(d.scars).append('|').append(d.atoning == null ? "" : d.atoning).append('|').append(DataStore.overworldPenalties() ? 1 : 0).append('|');
		boolean first = true;
		for (Penalty pen : Penalty.values()) {
			if (!first) {
				out.append(';');
			}
			first = false;
			out.append(pen.id).append(':').append(d.owned(pen)).append(':').append(pen.max).append(':')
					.append(d.atonement.getOrDefault(pen.id, 0)).append(':').append(PenaltyService.cost(d, pen)).append(':').append(pen.display())
					.append(':').append(CurseRunes.readable(d, pen) ? 1 : 0);
		}
		out.append('|').append(com.extremedimensions.pact.PactService.data(p, d));
		out.append('|').append(d.relic == null ? "" : d.relic);
		out.append('|').append(nearby(p));
		return out.toString();
	}

	/** How close another player must stand to show on the Curses screen. */
	private static final double NEARBY = 7.0;

	/**
	 * Players within {@link #NEARBY} blocks, nearest first: "uuid,name,beyond,overworld,everywhere" separated by ';'.
	 * Only how many stacks each set holds, never which curses.
	 */
	private static String nearby(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return "";
		}
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer q : level.players()) {
			if (q != p && !q.isSpectator() && q.distanceToSqr(p) <= NEARBY * NEARBY) {
				near.add(q);
			}
		}
		near.sort(Comparator.comparingDouble(q -> q.distanceToSqr(p)));
		StringBuilder out = new StringBuilder();
		for (ServerPlayer q : near) {
			PlayerData d = DataStore.get(q.getUUID());
			int[] sets = new int[Penalty.Group.values().length];
			for (Penalty pen : Penalty.values()) {
				sets[pen.group.ordinal()] += d.owned(pen);
			}
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(q.getUUID()).append(',').append(q.getName().getString());
			for (int n : sets) {
				out.append(',').append(n);
			}
		}
		return out.toString();
	}

	/** "id:stacks:max:valuePerStack;..." for every curse the player carries. */
	private static String penalties(PlayerData d) {
		StringBuilder out = new StringBuilder();
		for (Penalty pen : Penalty.values()) {
			int n = d.carried(pen);
			if (n <= 0) {
				continue;
			}
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(pen.id).append(':').append(n).append(':').append(pen.max).append(':').append(pen.display());
		}
		return out.toString();
	}
}
