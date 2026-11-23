package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Fx;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerPlayer;

/**
 * What curse came last, and from what (3.26). Every stack a player gains goes through {@link PlayerData#addStacks}, which
 * writes it here: the curse, how many stacks, when, and where it came from (a Lurker's touch, a swarm, a card chosen at
 * a death...). The newest few are kept with the player; the Curses screen shows them along its top and marks the curses
 * that came since the screen was last opened, and a curse laid without a choice shows as a toast the moment it lands.
 *
 * <p>Where a curse comes from is said by whoever lays it: {@link #from(Source, Supplier)} around the call.
 */
public final class CurseLog {
	private CurseLog() {}

	/** How many entries a player keeps. */
	public static final int KEEP = 8;

	/** Where a curse came from (its id names it in the Curses screen and on the toast). */
	public enum Source {
		UNKNOWN("unknown", false), CARD("card", true), SPILL("spill", false), LURKER("lurker", false),
		CAVE_LURKER("cave_lurker", false), SWARM("swarm", false), TIDEWALKER("tidewalker", false), BARTER("barter", false),
		FLEE("flee", false), COLLAPSE("collapse", false), CLAN("clan", false), BURDEN("burden", true), ELDER("elder", false),
		BLOOD("blood", false), COMMAND("command", true),
		/** Laid by an operator from the Players screen (3.27): told as a toast, as the player did not choose it. */
		OPERATOR("operator", false);

		public final String id;
		/** True when the player chose or set it themselves (no toast: they saw it come). */
		public final boolean chosen;

		Source(String id, boolean chosen) {
			this.id = id;
			this.chosen = chosen;
		}

		public String nameKey() {
			return "gui.extremedimensions.curses.from." + id;
		}

		public static Source byId(String id) {
			for (Source s : values()) {
				if (s.id.equals(id)) {
					return s;
				}
			}
			return UNKNOWN;
		}
	}

	/** One curse gained: its id, the stacks, when (wall clock, ms) and where from. */
	public record Entry(String id, int stacks, long at, Source source) {
		String encode() {
			return id + "," + stacks + "," + at + "," + source.id;
		}

		static Entry decode(String s) {
			String[] kv = s.split(",");
			if (kv.length < 4 || Penalty.byId(kv[0]) == null) {
				return null;
			}
			try {
				return new Entry(kv[0], Integer.parseInt(kv[1]), Long.parseLong(kv[2]), Source.byId(kv[3]));
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	private static final ThreadLocal<Source> SOURCE = new ThreadLocal<>();
	/** Set while an operator lays curses silently: nothing written down, no toast. */
	private static final ThreadLocal<Boolean> QUIET = ThreadLocal.withInitial(() -> false);

	/** Lays curses with their source said: everything {@code body} adds is written as coming from it. */
	public static <T> T from(Source source, Supplier<T> body) {
		Source was = SOURCE.get();
		SOURCE.set(source);
		try {
			return body.get();
		} finally {
			SOURCE.set(was);
		}
	}

	/** As {@link #from(Source, Supplier)}, for a call that returns nothing. */
	public static void from(Source source, Runnable body) {
		from(source, () -> {
			body.run();
			return null;
		});
	}

	/** Adds stacks with their source said (what PlayerData.addStacks returns). */
	public static int add(PlayerData d, Penalty p, int n, Source source) {
		return from(source, () -> d.addStacks(p, n));
	}

	/**
	 * Adds stacks silently (3.27.2, an operator's choice in the Players screen): they act at once, but nothing is written
	 * in the player's newest curses and no toast tells them. Returns how many were added.
	 */
	public static int addQuietly(PlayerData d, Penalty p, int n) {
		boolean was = QUIET.get();
		QUIET.set(true);
		try {
			return d.addStacks(p, n);
		} finally {
			QUIET.set(was);
		}
	}

	/** Stacks were added (called by {@link PlayerData#addStacks}): written first in the log, with a toast to come. */
	public static void record(PlayerData d, Penalty p, int added) {
		if (added <= 0 || QUIET.get()) {
			return;
		}
		Source source = SOURCE.get() == null ? Source.UNKNOWN : SOURCE.get();
		long now = System.currentTimeMillis();
		if (d.curseLog == null) {
			d.curseLog = new ArrayList<>();
		}
		// Two stacks of one curse from one source in the same moment are one line
		Entry first = d.curseLog.isEmpty() ? null : Entry.decode(d.curseLog.get(0));
		if (first != null && first.id().equals(p.id) && first.source() == source && now - first.at() < 2000) {
			d.curseLog.set(0, new Entry(p.id, first.stacks() + added, now, source).encode());
		} else {
			d.curseLog.add(0, new Entry(p.id, added, now, source).encode());
		}
		while (d.curseLog.size() > KEEP) {
			d.curseLog.remove(d.curseLog.size() - 1);
		}
		if (!source.chosen) {
			if (d.curseToasts == null) {
				d.curseToasts = new ArrayList<>();
			}
			d.curseToasts.add(new Entry(p.id, added, now, source));
		}
	}

	/** The log, newest first. */
	public static List<Entry> entries(PlayerData d) {
		List<Entry> out = new ArrayList<>();
		if (d.curseLog != null) {
			for (String s : d.curseLog) {
				Entry e = Entry.decode(s);
				if (e != null) {
					out.add(e);
				}
			}
		}
		return out;
	}

	/** Each tick: the toasts of curses that landed since, sent to the player. */
	public static void flush(ServerPlayer p, PlayerData d) {
		if (d.curseToasts == null || d.curseToasts.isEmpty()) {
			return;
		}
		for (Entry e : d.curseToasts) {
			Penalty pen = Penalty.byId(e.id());
			if (pen != null) {
				WorldFx.fx(p, Fx.CURSE_TOAST, pen.ordinal() | Math.min(63, e.stacks()) << 10 | e.source().ordinal() << 16);
			}
		}
		d.curseToasts.clear();
	}

	/**
	 * The log for the Curses screen: "id,stacks,secondsAgo,source,new" separated by ';', newest first; new: it came
	 * after the player last opened the screen.
	 */
	public static String data(PlayerData d) {
		long now = System.currentTimeMillis();
		StringBuilder out = new StringBuilder();
		for (Entry e : entries(d)) {
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(e.id()).append(',').append(e.stacks()).append(',').append(Math.max(0, (now - e.at()) / 1000)).append(',')
					.append(e.source().id).append(',').append(e.at() > d.cursesSeenAt ? 1 : 0);
		}
		return out.toString();
	}

	/** The player opened the Curses screen: what came before now is no longer new. */
	public static void seen(PlayerData d) {
		d.cursesSeenAt = System.currentTimeMillis();
	}
}
