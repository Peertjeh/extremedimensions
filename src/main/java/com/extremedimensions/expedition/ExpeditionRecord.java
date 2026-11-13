package com.extremedimensions.expedition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.compat.PlaytestLog;
import com.extremedimensions.compat.TabPlaceholders;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.pact.PactService;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * One record per expedition beyond the wound, and one per visit to the Seam: what it came to, gathered while it lasts
 * (its {@link Tally}), and, once it is over and what it cost is settled, told twice. The playtest log writes it as a
 * line ({@code playtest_log}), and after a Collapse (or a throw out of the Seam) the player is shown a short card of it
 * once they are home ({@code collapse_recap}).
 *
 * <p>An ending that costs curses owes them as cards, and the record waits for them to be chosen. The cards owed while
 * it is being made are its own ({@link #owing}); owed cards are answered in the order they were owed, so once the ones
 * owed before it are answered, the next are its own ({@link #resolve}). A Collapse's record also waits for the death and
 * the respawn that follow it, so its card only comes once the player is home. Records wait in the player's data, so
 * neither a logout nor a restart loses one. A visit to the Seam made on an expedition is a record of its own, beside the
 * expedition's (whose time and drain still run through it).
 */
public final class ExpeditionRecord {
	/** How long a record may wait for its cards before it is told with what it has (a card answered some way not counted here). */
	private static final long STALE_MS = 30 * 60 * 1000L;

	/** How an expedition (or a visit to the Seam) ended, in the playtest log's words. */
	public enum Outcome {
		/** Home through the portal, or out through the rift home, on their own feet. */
		RETURNED("returned"),
		/** The charge ran out: the Collapse (for a visit to the Seam, the Collapse of the expedition it was made on). */
		COLLAPSED("collapsed"),
		/** A Seam-Woven Tether unravelled instead of letting them collapse. */
		UNRAVELLED("unravelled"),
		/** Died beyond the wound, or in the Seam. */
		DIED("died"),
		/** The expedition ended with a death in the Seam, on a visit made from it. */
		DIED_IN_SEAM("died_in_seam"),
		/** Died in another mod's world: the expedition ends quietly, without reward or curse. */
		DIED_ELSEWHERE("died_elsewhere"),
		/** The Seam's thread ran out and it threw them home. */
		THROWN_HOME("thrown_home"),
		/** Fell below the Seam's islands, and it threw them home. */
		FELL("fell"),
		/** The Seam was made anew while they were in it: home, with no curse. */
		REMADE("remade"),
		/** Left the Seam some other way (a command). */
		LEFT("left");

		public final String word;

		Outcome(String word) {
			this.word = word;
		}
	}

	/** What an expedition, or a visit to the Seam, has come to so far (saved with the player's data). */
	public static final class Tally {
		/** The world's short id (see Dims), and the one it began from. */
		public String realm = "", from = "";
		/** The player's how-manieth into this world (see {@code PlayerData.ventures}). */
		public int nth = 0;
		/** The charge (or the thread) it began with, in seconds, and the tier of the Tether taken along. */
		public double charge = 0;
		public int tether = 0;
		/** Ticks it drained, the farthest from its portal (blocks), and the sums of the distance's share of the drain and of the drain. */
		public long ticks = 0;
		public double farthest = 0, distance = 0, drain = 0;
		/** In the Seam: thrown home for a fall below the islands, not for the thread running out. */
		public boolean fell = false;
	}

	/** When it ended (ms since the epoch), who, in which world, from which, how, and the how-manieth into that world. */
	public long ended;
	public String player = "", realm = "", from = "", outcome = "";
	public int nth;
	/** How long it lasted (seconds), the farthest it went from its portal (blocks), and the charge or thread it began with (seconds). */
	public double seconds, farthest, charge;
	/**
	 * How fast its timer ran on average against the base rate: from the distance alone (-1 where the distance does not
	 * count, in the Seam), and all told.
	 */
	public double distanceX = -1, drainX = 1;
	/** The tier of the Tether taken along (0 iron, 1 soul-woven, 2 seam-woven). */
	public int tether;
	/**
	 * What it cost: the curses chosen or laid (id to stacks) and their stacks together, the Seam Silk and Tether Fragments
	 * the Seam kept, the atonement a death undid, the charge a death took (seconds) and the levels the crossing home took.
	 */
	public Map<String, Integer> curses = new LinkedHashMap<>();
	public int stacks, silk, fragments, atonement, levels;
	public double chargeLost;
	/** The relic bound, the pact that acted (id to stacks sworn) and its heat, and the world's difficulty. */
	public String relic = "", difficulty = "";
	public Map<String, Integer> pact = new LinkedHashMap<>();
	public int heat;
	/** Anything else worth knowing of it (a relic that held, a card no curse had room for). */
	public List<String> notes = new ArrayList<>();
	/** Cards owed before it still to be answered, and its own still to be answered. */
	public int ahead, left;
	/** The player is home again (a Collapse's record waits for the death and the respawn that follow it). */
	public boolean home = true;
	/** Still being made: the cards owed meanwhile are its own. */
	public transient boolean open = false;

	/** The last card sent, and to whom (for the tests: a test player's client never answers). */
	public record Sent(UUID to, ModPackets.RecapPayload recap) {}

	public static volatile Sent lastRecap = null;

	// ------------------------------------------------------------------ while it lasts

	/** An expedition (or a visit to the Seam) begins: its tally. */
	public static Tally begin(PlayerData d, String realm, String from, double charge, int tether) {
		Tally t = new Tally();
		t.realm = realm;
		t.from = from == null ? "" : from;
		if (d.ventures == null) {
			d.ventures = new HashMap<>();
		}
		t.nth = d.ventures.merge(realm, 1, Integer::sum);
		t.charge = Math.max(0, charge);
		t.tether = tether;
		DataStore.markDirty();
		return t;
	}

	/**
	 * One tick of it: how far it is from its portal (or the rift home), the distance's share of the drain this tick (1
	 * where the distance does not count) and the drain itself (charge a second; the Seam's thread a second).
	 */
	public static void tick(Tally t, double far, double distance, double drain) {
		if (t == null) {
			return;
		}
		t.ticks++;
		if (far > t.farthest) {
			t.farthest = far;
		}
		t.distance += distance;
		t.drain += drain;
	}

	// ------------------------------------------------------------------ the end, and what it cost

	/**
	 * The expedition (or, with {@code seam}, the visit to the Seam) ended: its record, still open for what the ending
	 * costs (the cards owed until {@link #settle} are its own). Null when nothing was tallied (one begun before 3.53.0).
	 */
	public static ExpeditionRecord end(ServerPlayer p, PlayerData d, boolean seam, Outcome how) {
		Tally t = seam ? d.seamTally : d.runTally;
		if (seam) {
			d.seamTally = null;
		} else {
			d.runTally = null;
		}
		if (t == null) {
			return null;
		}
		ExpeditionRecord r = new ExpeditionRecord();
		r.ended = System.currentTimeMillis();
		r.player = p.getScoreboardName();
		r.realm = t.realm;
		r.from = t.from;
		r.nth = t.nth;
		r.outcome = how.word;
		r.seconds = t.ticks / 20.0;
		r.farthest = t.farthest;
		r.charge = t.charge;
		r.tether = t.tether;
		boolean inSeam = Dims.SEAM.equals(t.realm);
		double ticks = Math.max(1, t.ticks);
		r.distanceX = inSeam ? -1 : t.ticks > 0 ? t.distance / ticks : 1;
		// (the Seam's thread runs a second a second at its base; an expedition's charge at the world's base drain)
		double base = inSeam || Config.BASE_DRAIN <= 0 ? 1 : Config.BASE_DRAIN;
		r.drainX = t.ticks > 0 ? t.drain / ticks / base : 1;
		r.relic = d.relic == null ? "" : d.relic;
		// A pact acts on an expedition (and in the Seam on a visit made from one), never on a visit from home
		if (d.inRun && d.pact != null) {
			r.heat = PactService.actingHeat(d);
			if (r.heat > 0) {
				for (Map.Entry<String, Integer> e : d.pact.entrySet()) {
					if (e.getValue() != null && e.getValue() > 0) {
						r.pact.put(e.getKey(), e.getValue());
					}
				}
			}
		}
		r.difficulty = TabPlaceholders.difficulty();
		// What a relic did for it (a visit made on an expedition shares the expedition's Last Stitch, and leaves it to its record)
		if (d.stitchSpent && (!inSeam || !d.inRun)) {
			r.notes.add("Last Stitch held");
		}
		if (!inSeam && d.heldSpent) {
			r.notes.add(how == Outcome.RETURNED && d.heldTicks > 0 ? "home in the Held's Chain's grace" : "Held's Chain held");
		}
		r.ahead = d.owed == null ? 0 : d.owed.size();
		r.home = how != Outcome.COLLAPSED || inSeam;
		r.open = true;
		records(d).add(r);
		DataStore.markDirty();
		return r;
	}

	/** An ending that costs nothing more than what its record already holds: settled at once. */
	public static void ended(ServerPlayer p, PlayerData d, boolean seam, Outcome how) {
		settle(p, d, end(p, d, seam, how));
	}

	/** What the ending itself cost is in: the record is told now, unless it waits for cards (or, after a Collapse, the respawn). */
	public static void settle(ServerPlayer p, PlayerData d, ExpeditionRecord r) {
		if (r == null) {
			return;
		}
		r.open = false;
		tell(p, d);
	}

	/** A curse laid without a choice as the ending's price (a throw out of the Seam): one stack of it. */
	public void laid(Penalty pen) {
		if (pen != null) {
			curses.merge(pen.id, 1, Integer::sum);
			stacks++;
		}
	}

	/** A curse was owed as a card (see {@code PenaltyService.owe}): while a record is being made, the card is its own. */
	public static void owing(PlayerData d) {
		List<ExpeditionRecord> list = d.records;
		if (list != null && !list.isEmpty()) {
			ExpeditionRecord r = list.get(list.size() - 1);
			if (r.open) {
				r.left++;
			}
		}
	}

	/**
	 * The first owed card was answered: with {@code stacks} of {@code pen}, or (null) with nothing, no curse having room
	 * left. Cards are answered in the order they were owed, so it was the record's whose own cards come first once those
	 * owed before them are answered. Returns that record, or null when the card was owed for none (a death at home, say).
	 */
	public static ExpeditionRecord resolve(PlayerData d, Penalty pen, int stacks) {
		ExpeditionRecord to = null;
		for (ExpeditionRecord r : records(d)) {
			if (r.ahead > 0) {
				// (this card was owed before this record's own)
				r.ahead--;
			} else if (to == null && r.left > 0) {
				r.left--;
				to = r;
			}
		}
		if (to != null) {
			if (pen == null) {
				to.notes.add("a card no curse had room for: the levels were taken");
			} else {
				to.curses.merge(pen.id, Math.max(1, stacks), Integer::sum);
				to.stacks += Math.max(1, stacks);
			}
			DataStore.markDirty();
		}
		return to;
	}

	/** The card a player was shown was answered (see {@code PenaltyService.choose}): its record may be told now. */
	public static void answered(ServerPlayer p, PlayerData d) {
		tell(p, d);
	}

	/**
	 * Back home after a death (on respawn): a Collapse's record takes the atonement that death undid, and may be told
	 * now (with no card owed, or once its cards are answered).
	 */
	public static void home(ServerPlayer p, PlayerData d) {
		boolean toll = false;
		for (ExpeditionRecord r : records(d)) {
			if (!r.home) {
				r.home = true;
				if (!toll) {
					toll = true;
					r.atonement += Math.max(0, d.atonementLost);
				}
			}
		}
		tell(p, d);
	}

	/**
	 * Whether a Collapse's record waits for the player to come home, and will be shown as its card (3.55.0): that card
	 * lists the atonement the Collapse's death undid, so the respawn's line need not say it as well.
	 */
	public static boolean cardAwaitsHome(PlayerData d) {
		if (Config.COLLAPSE_RECAP <= 0) {
			return false;
		}
		for (ExpeditionRecord r : records(d)) {
			if (!r.home && r.recap()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Once a second for every player in the mod's worlds: a record whose cards are all answered is told, and so is any once
	 * nothing at all is owed any more, or once it has waited half an hour, so none waits for ever on a card answered in some
	 * way not counted here.
	 */
	public static void second(ServerPlayer p, PlayerData d) {
		List<ExpeditionRecord> list = d.records;
		if (list == null || list.isEmpty() || d.collapsing) {
			return;
		}
		boolean nothingOwed = (d.owed == null || d.owed.isEmpty()) && (d.pendingOffer == null || d.pendingOffer.isEmpty());
		long now = System.currentTimeMillis();
		for (ExpeditionRecord r : list) {
			// Past a Collapse's pull the respawn has come (only it ends the pull), whichever way it came: home
			r.home = true;
			if (!r.open && (nothingOwed || now - r.ended > STALE_MS)) {
				r.ahead = 0;
				r.left = 0;
				r.home = true;
			}
		}
		tell(p, d);
	}

	/**
	 * Tells every record that is settled: it goes to the playtest log, and a Collapse's card to the player. Never during
	 * a Collapse's pull, nor while the player lies dead: the card is for when they are home.
	 */
	private static void tell(ServerPlayer p, PlayerData d) {
		List<ExpeditionRecord> list = d.records;
		if (list == null || list.isEmpty() || d.collapsing || !p.isAlive()) {
			return;
		}
		for (Iterator<ExpeditionRecord> it = list.iterator(); it.hasNext();) {
			ExpeditionRecord r = it.next();
			if (r.open || !r.home || r.ahead > 0 || r.left > 0) {
				continue;
			}
			it.remove();
			DataStore.markDirty();
			PlaytestLog.expedition(r);
			if (r.recap() && Config.COLLAPSE_RECAP > 0) {
				recap(p, r);
			}
		}
	}

	/** Whether this ending is one the player is shown a card of: a Collapse beyond the wound, or a throw out of the Seam. */
	public boolean recap() {
		return Outcome.COLLAPSED.word.equals(outcome) && !Dims.SEAM.equals(realm) || Outcome.THROWN_HOME.word.equals(outcome)
				|| Outcome.FELL.word.equals(outcome);
	}

	/** The card of it, sent to the player (see the client's CollapseRecap). */
	private static void recap(ServerPlayer p, ExpeditionRecord r) {
		StringBuilder curses = new StringBuilder();
		for (Map.Entry<String, Integer> e : r.curses.entrySet()) {
			if (curses.length() > 0) {
				curses.append(',');
			}
			curses.append(e.getKey()).append(':').append(e.getValue());
		}
		ModPackets.RecapPayload out = new ModPackets.RecapPayload(r.realm, r.outcome, round(r.seconds), round(r.charge), round(r.farthest),
				r.distanceX < 0 ? 0 : round(r.distanceX * 100), round(r.drainX * 100), curses.toString(), r.silk, r.fragments, r.atonement);
		lastRecap = new Sent(p.getUUID(), out);
		if (ServerPlayNetworking.canSend(p, ModPackets.RecapPayload.TYPE)) {
			ServerPlayNetworking.send(p, out);
		}
	}

	private static int round(double v) {
		return Double.isFinite(v) ? (int) Math.max(0, Math.round(v)) : 0;
	}

	/** The player's records waiting to be told (never null). */
	public static List<ExpeditionRecord> records(PlayerData d) {
		if (d.records == null) {
			d.records = new ArrayList<>();
		}
		return d.records;
	}
}
