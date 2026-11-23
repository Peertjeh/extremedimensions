package com.extremedimensions.expedition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.Dims;

/**
 * The words of a Collapse's recap (3.55.0), as translation keys and their arguments, so the card on the screen, the
 * journal's page of the last falls and the chat's "[Recap]" line all say the same: the client turns them into text,
 * the server into chat components. A {@link Text} among the arguments is translated too (a curse's name).
 */
public final class RecapText {
	private RecapText() {}

	/** Below this (a hundredth over the base rate) the drain counts as no faster at all. */
	private static final int FASTER = 105;

	/** A translation key and its arguments. */
	public record Text(String key, Object... args) {}

	/** Whether the recap is of a throw out of the Seam (rather than a Collapse beyond the wound). */
	public static boolean seam(ModPackets.RecapPayload r) {
		return Dims.SEAM.equals(r.realm());
	}

	/** Its title: a Collapse in the Nether or the End, or a throw out of the Seam. */
	public static Text title(ModPackets.RecapPayload r) {
		return new Text("gui.extremedimensions.recap.title." + (seam(r) ? Dims.SEAM : Dims.END.equals(r.realm()) ? Dims.END : Dims.NETHER));
	}

	/** How long they were away, and on how much charge (or thread). */
	public static Text away(ModPackets.RecapPayload r) {
		return new Text(seam(r) ? "gui.extremedimensions.recap.away.seam" : "gui.extremedimensions.recap.away.text", time(r.seconds()), time(r.charge()));
	}

	/** How far out they went. */
	public static Text far(ModPackets.RecapPayload r) {
		return new Text(seam(r) ? "gui.extremedimensions.recap.far.seam" : "gui.extremedimensions.recap.far.text", r.farthest());
	}

	/** Why the charge (or the thread) ran out faster: the distance, or the rest (curses, the wound, the world), or nothing. */
	public static Text why(ModPackets.RecapPayload r) {
		if (seam(r)) {
			if ("fell".equals(r.outcome())) {
				return new Text("gui.extremedimensions.recap.why.fell");
			}
			return r.drainX100() >= FASTER ? new Text("gui.extremedimensions.recap.why.fray", times(r.drainX100()))
					: new Text("gui.extremedimensions.recap.why.thread");
		}
		if (r.distanceX100() >= FASTER) {
			return new Text("gui.extremedimensions.recap.why.distance", times(r.distanceX100()), times(r.drainX100()));
		}
		return r.drainX100() >= FASTER ? new Text("gui.extremedimensions.recap.why.drain", times(r.drainX100()))
				: new Text("gui.extremedimensions.recap.why.base");
	}

	/**
	 * What it cost, part by part: the curses (by name, with their stacks), the Tether (lost in a Collapse), what the Seam
	 * kept, the atonement undone; "nothing but the time" alone when there was nothing.
	 */
	public static List<Text> cost(ModPackets.RecapPayload r) {
		List<Text> parts = new ArrayList<>();
		for (String part : r.curses().split(",")) {
			int colon = part.indexOf(':');
			Penalty pen = Penalty.byId(colon < 0 ? part : part.substring(0, colon));
			if (pen == null) {
				continue;
			}
			int stacks = 1;
			try {
				stacks = colon < 0 ? 1 : Integer.parseInt(part.substring(colon + 1));
			} catch (NumberFormatException e) {
				// (one stack, then)
			}
			Text name = new Text(pen.nameKey());
			parts.add(stacks > 1 ? new Text("gui.extremedimensions.recap.cost.stacks", name, stacks) : name);
		}
		if (!seam(r)) {
			parts.add(new Text("gui.extremedimensions.recap.cost.tether"));
		}
		if (r.silk() > 0) {
			parts.add(new Text("gui.extremedimensions.recap.cost.silk", r.silk()));
		}
		if (r.fragments() > 0) {
			parts.add(new Text("gui.extremedimensions.recap.cost.fragments", r.fragments()));
		}
		if (r.atonement() > 0) {
			parts.add(new Text("gui.extremedimensions.recap.cost.atonement", r.atonement()));
		}
		if (parts.isEmpty()) {
			parts.add(new Text("gui.extremedimensions.recap.cost.none"));
		}
		return parts;
	}

	/** Seconds as m:ss, as the HUD writes a time. */
	public static String time(double seconds) {
		int s = (int) Math.ceil(Math.max(0, seconds));
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	/** A hundredth of a rate as the HUD shows a drain ("2.4"). */
	public static String times(int x100) {
		return String.format(Locale.ROOT, "%.1f", x100 / 100.0);
	}

	// ------------------------------------------------------------------ kept for the journal

	/** How many recaps a player's journal keeps. */
	public static final int KEPT = 5;

	/**
	 * A recap as the player's data keeps it: when (ms since the epoch), then the card's fields, '|' between them. It holds
	 * neither ';' nor '=' (the ledger's own marks).
	 */
	public static String write(long when, ModPackets.RecapPayload r) {
		return when + "|" + r.realm() + "|" + r.outcome() + "|" + r.seconds() + "|" + r.charge() + "|" + r.farthest() + "|" + r.distanceX100()
				+ "|" + r.drainX100() + "|" + r.curses().replace(";", "").replace("=", "").replace("|", "") + "|" + r.silk() + "|" + r.fragments()
				+ "|" + r.atonement();
	}

	/** The first field of a recap as {@link #write} wrote it (when it fell, or how long ago), or -1 for a damaged one. */
	public static long head(String kept) {
		try {
			return Long.parseLong(kept.substring(0, Math.max(0, kept.indexOf('|'))));
		} catch (RuntimeException e) {
			return -1;
		}
	}

	/** The rest of a recap as {@link #write} wrote it, read back into a card; null for a damaged one. */
	public static ModPackets.RecapPayload read(String kept) {
		String[] f = kept.split("\\|", -1);
		if (f.length < 12) {
			return null;
		}
		try {
			return new ModPackets.RecapPayload(f[1], f[2], Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
					Integer.parseInt(f[6]), Integer.parseInt(f[7]), f[8], Integer.parseInt(f[9]), Integer.parseInt(f[10]), Integer.parseInt(f[11]));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
