package com.extremedimensions.journal;

import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.tribe.Standing;

/**
 * The ledger at the back of the journal: what the player has been through, counted. Three pages: the road (where they
 * went and how often it went wrong), the burden (the curses, what was lifted, what was found) and the others (the
 * tribe, the great ones, allies).
 */
public final class Ledger {
	private Ledger() {}

	/** The ledger as the journal reads it: "page:key=value;..." (a few values are read in a form of their own). */
	public static String encode(PlayerData d) {
		StringBuilder out = new StringBuilder();
		int stacks = 0, curses = 0;
		for (Penalty pen : Penalty.values()) {
			int n = d.owned(pen);
			stacks += n;
			curses += n > 0 ? 1 : 0;
		}
		int great = d.kills.getOrDefault("matriarch", 0) + d.kills.getOrDefault("ferryman", 0) + d.kills.getOrDefault("weaver", 0);
		add(out, "road", "expeditions", d.expeditions);
		add(out, "road", "collapses", d.collapses);
		add(out, "road", "longest", (long) d.longestRun);
		add(out, "road", "furthest", (long) d.furthest);
		add(out, "road", "deaths", d.deaths);
		add(out, "road", "seam_visits", d.seamVisits);
		add(out, "road", "fray_descents", d.frayDescents);
		add(out, "burden", "curses", stacks + "/" + curses);
		add(out, "burden", "lifted", d.lifted);
		add(out, "burden", "scars", d.scars);
		add(out, "burden", "pages", d.journal.size() + "/" + JournalEntry.total());
		add(out, "burden", "relic", d.relic == null ? "" : d.relic);
		add(out, "burden", "seen", d.seen.size() + "/" + Bestiary.Beast.values().length);
		add(out, "others", "standing", Standing.level(d.tribeRep).name().toLowerCase(java.util.Locale.ROOT) + "/" + d.tribeRep);
		add(out, "others", "bartered", d.bartered);
		add(out, "others", "raids", d.raidsWon);
		add(out, "others", "keepers", d.keepersSlain);
		add(out, "others", "great", great);
		add(out, "others", "lifts_given", d.liftsGiven);
		add(out, "others", "lifts_taken", d.liftsTaken);
		add(out, "others", "burdens", d.burdensCarried);
		return out.toString();
	}

	private static void add(StringBuilder out, String page, String key, Object value) {
		if (out.length() > 0) {
			out.append(';');
		}
		out.append(page).append(':').append(key).append('=').append(String.valueOf(value).replace(";", ",").replace("=", "-"));
	}
}
