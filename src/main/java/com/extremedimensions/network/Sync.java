package com.extremedimensions.network;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.AbyssalCurses;
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
		// The drain as it is, taken apart (3.55.0): in the Seam the thread's own, the Fray's and a frayed island's in it, so
		// the panel's timer keeps pace with the thread there (before 3.55.0 it ran at x1.0 in the Fray); on an expedition
		// with what its cracked or blocked anchors leak on top
		com.extremedimensions.expedition.DrainCauses.Breakdown why = com.extremedimensions.expedition.DrainCauses.of(p, d);
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? AnchorService.nearCharge(p, Dims.NETHER) : -1,
				overworld ? AnchorService.nearCharge(p, Dims.END) : -1,
				seam ? (int) Math.ceil(d.seamLeft) : d.inRun ? (int) Math.ceil(AnchorService.linkedCharge(p, d)) : 0,
				(int) Math.ceil(seam ? d.seamMax : d.runMax), dimCode,
				(int) Math.round(why.drain() * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, penalties(d), d.scars,
				seam ? SeamService.riftDistance(p, d) : TetherService.distance(p, d), com.extremedimensions.guide.Guide.progress(d),
				seam ? com.extremedimensions.trial.TrialService.hud(d) : "",
				home(p, d, seam, 0), home(p, d, seam, 1), home(p, d, seam, 2),
				why.cause(), why.encode(), -1, 0, 0));
	}

	/**
	 * Where the way home lies, one axis of it (0 x, 1 y, 2 z): the rift's floor in the Seam, and on an expedition in the
	 * Nether or the End the arrival portal (3.55.0) while its compass is on and Unmapped does not hide it. NaN where there
	 * is none to show, which the client reads as no way home known.
	 */
	public static float home(ServerPlayer p, PlayerData d, boolean seam, int axis) {
		if (seam) {
			return (float) (axis == 0 ? d.riftX : axis == 1 ? d.riftY : d.riftZ);
		}
		if (!arrivalShown(d)) {
			return Float.NaN;
		}
		return (float) (axis == 0 ? d.arrivalX : axis == 1 ? d.arrivalY : d.arrivalZ);
	}

	/** Whether the way back to the arrival portal is shown to this player now: on an expedition there, compass on, not Unmapped. */
	public static boolean arrivalShown(PlayerData d) {
		return d.inRun && !d.collapsing && !d.away && Config.ARRIVAL_COMPASS > 0 && d.tetherAnchor != null
				&& (Dims.NETHER.equals(d.dim) || Dims.END.equals(d.dim)) && d.stacks(com.extremedimensions.penalty.Penalty.UNMAPPED) <= 0;
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
		boolean abyss = false;
		for (int i = 0; i < d.pendingOffer.size(); i++) {
			Penalty pen = Penalty.byId(d.pendingOffer.get(i));
			if (pen == null) {
				continue;
			}
			abyss |= pen.abyssal;
			int mult = i < d.pendingMult.size() ? d.pendingMult.get(i) : 1;
			out.append(',');
			out.append(pen.id).append(':').append(mult).append(':').append(d.owned(pen)).append(':').append(pen.max).append(':').append(pen.display())
					.append(':').append(CurseRunes.readable(d, pen) ? 1 : 0);
		}
		ServerPlayNetworking.send(p, new ModPackets.OfferPayload(out.toString()));
		if (abyss) {
			// An Abyssal card on the table: something deep stirs as the cards turn
			com.extremedimensions.server.WorldFx.soundSelf(p, "minecraft:entity.warden.sonic_charge", 0.7f, 0.5f);
			com.extremedimensions.server.WorldFx.soundSelf(p, "minecraft:ambient.basalt_deltas.mood", 1.0f, 0.6f);
		}
	}

	/** Every curse, for the Curses screen (see {@link ModPackets.CursesPayload}). */
	public static void curses(ServerPlayer p, PlayerData d, boolean open) {
		ServerPlayNetworking.send(p, new ModPackets.CursesPayload(cursesData(p, d), open));
		if (open) {
			// The screen opened: what came before now is no longer new there (it marked it as it opened)
			com.extremedimensions.penalty.CurseLog.seen(d);
			DataStore.markDirty();
		}
	}

	/**
	 * The Curses screen's data: scars, the curse atoned for, Overworld curses on or off, every curse, players nearby, the
	 * newest curses, and the Fresh Wound and the wait for the next Mending Cloth.
	 */
	public static String cursesData(ServerPlayer p, PlayerData d) {
		StringBuilder out = new StringBuilder();
		out.append(d.scars).append('|').append(d.atoning == null ? "" : d.atoning).append('|').append(DataStore.overworldPenalties() ? 1 : 0).append('|');
		boolean first = true;
		for (Penalty pen : Penalty.values()) {
			// Abyssal curses show only where they are dealt, or while one is still carried
			if (pen.abyssal && Config.ABYSSAL_CURSES <= 0 && d.owned(pen) == 0) {
				continue;
			}
			if (!first) {
				out.append(';');
			}
			first = false;
			out.append(pen.id).append(':').append(d.owned(pen)).append(':').append(pen.max).append(':')
					.append(d.atonement.getOrDefault(pen.id, 0)).append(':').append(PenaltyService.cost(d, pen)).append(':').append(pen.display())
					.append(':').append(CurseRunes.readable(d, pen) ? 1 : 0)
					.append(':').append(pen.abyssal ? AbyssalCurses.progress(d, pen) : 0).append(':').append(pen.abyssal ? AbyssalCurses.target(pen) : 0);
		}
		out.append('|').append(com.extremedimensions.pact.PactService.data(p, d));
		out.append('|').append(d.relic == null ? "" : d.relic);
		out.append('|').append(nearby(p));
		// The newest curses gained, with where from and whether new since the screen was last opened
		out.append('|').append(com.extremedimensions.penalty.CurseLog.data(d));
		// Whether the player may open the operators' Players screen (3.27)
		out.append('|').append(com.extremedimensions.admin.AdminService.allowed(p) ? 1 : 0);
		// The Fresh Wound and the Mending Cloth (3.49): seconds of play the wound lasts yet, of those no Tether binds yet,
		// and before another cloth may be used
		out.append('|').append(Math.max(0, d.woundIn)).append(',').append(com.extremedimensions.penalty.Wound.bindIn(d)).append(',')
				.append(Math.max(0, d.clothIn));
		return out.toString();
	}

	/** How close another player must stand to show on the Curses screen. */
	private static final double NEARBY = 7.0;

	/**
	 * Players within {@link #NEARBY} blocks, nearest first: "uuid,name,<stacks per set>,clan" separated by ';' (clan: 0
	 * none, 1 the player's own, 2 another). Only how many stacks each set holds, never which curses.
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
		String mine = com.extremedimensions.coop.ClanService.clanOf(p.getUUID());
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
			// and their clan: none, the player's own, or another
			String theirs = com.extremedimensions.coop.ClanService.clanOf(q.getUUID());
			out.append(',').append(theirs == null ? 0 : theirs.equals(mine) ? 1 : 2);
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
