package com.extremedimensions.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionRecord;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.server.Dims;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * The playtest log ({@code playtest_log}, off by default): one line in {@code extremedimensions/playtest.csv} in the
 * server's folder for every expedition that ends and every visit to the Seam (see ExpeditionRecord), every relic bound
 * and every curse chosen, with a header line when the file is new, so an evening's play can be judged on real numbers
 * in a spreadsheet. The column {@code kind} says what a line is: {@code expedition}, {@code relic_bound} or
 * {@code curse_chosen}.
 *
 * <p>Every number is a whole one (seconds, blocks, percent), so no spreadsheet reads a decimal point as a thousands
 * separator; lists inside a column are joined with " + " and never hold a comma. A line is made on the server's thread
 * and appended on a thread of its own, so the server never waits for the disk; a write that fails is logged once (until
 * one works again) and never stops the server.
 */
public final class PlaytestLog {
	private PlaytestLog() {}

	public static final String FILE = "playtest.csv";

	/** The columns, in order (the header line). */
	public static final List<String> COLUMNS = List.of("kind", "time", "player", "realm", "from", "nth", "outcome", "seconds",
			"farthest_blocks", "distance_pct", "drain_pct", "charge_s", "curses", "curse_stacks", "silk_taken", "fragments_taken",
			"atonement_lost", "charge_lost_s", "levels_taken", "relic", "tether", "pact", "pact_heat", "difficulty", "notes", "version");

	public static final String EXPEDITION = "expedition", RELIC_BOUND = "relic_bound", CURSE_CHOSEN = "curse_chosen";

	/** The server's own time of day, as a spreadsheet reads it. */
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

	/** For the tests: another folder for the file (null: {@code extremedimensions} in the server's folder). */
	public static volatile Path folder = null;

	private static ExecutorService writer;
	/** A write failed and was logged: the next failures are not, until a write works again. */
	private static volatile boolean warned = false;
	private static String version;

	public static boolean on() {
		return Config.PLAYTEST_LOG > 0;
	}

	/** The file the log is written to (null when there is no folder for it). */
	public static Path file() {
		Path dir = folder;
		if (dir == null) {
			try {
				dir = FabricLoader.getInstance().getGameDir().resolve(ExtremeDimensions.MOD_ID).normalize();
			} catch (InvalidPathException e) {
				warn("The playtest log has no folder", e);
				return null;
			}
		}
		return dir.resolve(FILE);
	}

	// ------------------------------------------------------------------ the lines

	/** An expedition (or a visit to the Seam) ended and what it cost is settled. Null when the log is off. */
	public static CompletableFuture<Path> expedition(ExpeditionRecord r) {
		if (!on() || r == null) {
			return null;
		}
		Map<String, String> row = row(EXPEDITION, r.ended, r.player);
		row.put("realm", realm(r.realm));
		row.put("from", realm(r.from));
		row.put("nth", String.valueOf(r.nth));
		row.put("outcome", r.outcome);
		row.put("seconds", whole(r.seconds));
		row.put("farthest_blocks", whole(r.farthest));
		// (where the distance does not count, in the Seam, the column is empty rather than a 0 that would read as none)
		row.put("distance_pct", r.distanceX < 0 ? "" : whole((r.distanceX - 1) * 100));
		row.put("drain_pct", whole((r.drainX - 1) * 100));
		row.put("charge_s", whole(r.charge));
		row.put("curses", stacks(r.curses));
		row.put("curse_stacks", String.valueOf(r.stacks));
		row.put("silk_taken", String.valueOf(r.silk));
		row.put("fragments_taken", String.valueOf(r.fragments));
		row.put("atonement_lost", String.valueOf(r.atonement));
		row.put("charge_lost_s", whole(r.chargeLost));
		row.put("levels_taken", String.valueOf(r.levels));
		row.put("relic", r.relic);
		row.put("tether", tether(r.tether));
		row.put("pact", stacks(r.pact));
		row.put("pact_heat", String.valueOf(r.heat));
		row.put("difficulty", r.difficulty);
		row.put("notes", String.join(" + ", r.notes));
		return append(row);
	}

	/** A relic was bound to the Tether ({@code over}: the one it took the place of, or null). Null when the log is off. */
	public static CompletableFuture<Path> relic(ServerPlayer p, PlayerData d, Relic relic, Relic over, boolean overBreaks) {
		if (!on() || relic == null) {
			return null;
		}
		Map<String, String> row = row(RELIC_BOUND, System.currentTimeMillis(), p.getScoreboardName());
		row.put("realm", realm(Dims.of(p)));
		row.put("relic", relic.id);
		row.put("difficulty", TabPlaceholders.difficulty());
		// What the player had done before they chose it: how often each relic is taken up, and when, is the question
		row.put("notes", (over == null ? "" : (overBreaks ? "bound over " : "in place of ") + over.id + " + ") + "expeditions before: "
				+ d.expeditions + " / collapses before: " + d.collapses);
		return append(row);
	}

	/**
	 * A curse card was chosen (by the Tether itself with {@code auto}, the offer left too long): {@code mult} stacks of it
	 * meant, {@code spilled} the stacks that had no room and landed on other curses, {@code offer} the cards it was chosen
	 * from, {@code from} the ended expedition it was owed for (or null). Null when the log is off.
	 */
	public static CompletableFuture<Path> curse(ServerPlayer p, PlayerData d, Penalty pen, int mult, Map<Penalty, Integer> spilled,
			List<String> offer, boolean auto, ExpeditionRecord from) {
		if (!on() || pen == null) {
			return null;
		}
		Map<String, String> row = row(CURSE_CHOSEN, System.currentTimeMillis(), p.getScoreboardName());
		if (from != null) {
			// The expedition it was owed for, as its own line will have it (that line follows once all its cards are chosen)
			row.put("realm", realm(from.realm));
			row.put("from", realm(from.from));
			row.put("nth", String.valueOf(from.nth));
			row.put("outcome", from.outcome);
		} else {
			row.put("realm", realm(Dims.of(p)));
		}
		Map<String, Integer> chosen = new LinkedHashMap<>();
		chosen.put(pen.id, Math.max(1, mult));
		row.put("curses", stacks(chosen));
		row.put("curse_stacks", String.valueOf(Math.max(1, mult)));
		row.put("relic", d.relic == null ? "" : d.relic);
		row.put("difficulty", TabPlaceholders.difficulty());
		StringBuilder notes = new StringBuilder("offered ").append(String.join(" / ", offer));
		if (spilled != null && !spilled.isEmpty()) {
			Map<String, Integer> over = new LinkedHashMap<>();
			spilled.forEach((k, v) -> over.put(k.id, v));
			notes.append(" + spilled onto ").append(stacks(over));
		}
		if (auto) {
			notes.append(" + chosen by the Tether");
		}
		row.put("notes", notes.toString());
		return append(row);
	}

	// ------------------------------------------------------------------ making a line

	/** A line with every column empty but its kind, its time and its player. */
	private static Map<String, String> row(String kind, long at, String player) {
		Map<String, String> row = new LinkedHashMap<>();
		for (String c : COLUMNS) {
			row.put(c, "");
		}
		row.put("kind", kind);
		row.put("time", LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).format(TIME));
		row.put("player", player == null ? "" : player);
		row.put("version", version());
		return row;
	}

	/** A world in the words of the tablist (Nether, End, Seam, Overworld), "" for none. */
	static String realm(String dim) {
		if (dim == null || dim.isEmpty()) {
			return "";
		}
		return switch (dim) {
			case Dims.NETHER -> "Nether";
			case Dims.END -> "End";
			case Dims.SEAM -> "Seam";
			case Dims.OVERWORLD -> "Overworld";
			default -> "Elsewhere";
		};
	}

	private static String tether(int tier) {
		return tier == 2 ? "seam_woven" : tier == 1 ? "soul_woven" : "iron";
	}

	/** Curses as "id xN" (just "id" for one stack), joined with " + ". */
	private static String stacks(Map<String, Integer> curses) {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Integer> e : curses.entrySet()) {
			if (out.length() > 0) {
				out.append(" + ");
			}
			out.append(e.getKey());
			if (e.getValue() != null && e.getValue() > 1) {
				out.append(" x").append(e.getValue());
			}
		}
		return out.toString();
	}

	private static String whole(double v) {
		return Double.isFinite(v) ? String.valueOf(Math.round(v)) : "";
	}

	private static String version() {
		if (version == null) {
			version = FabricLoader.getInstance().getModContainer(ExtremeDimensions.MOD_ID)
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
		}
		return version;
	}

	/** The header line. */
	public static String header() {
		return String.join(",", COLUMNS);
	}

	/** One line of the file, its fields in the order of the columns (quoted only where a field needs it). */
	static String line(Map<String, String> row) {
		StringBuilder out = new StringBuilder();
		for (String c : COLUMNS) {
			if (out.length() > 0) {
				out.append(',');
			}
			out.append(field(row.getOrDefault(c, "")));
		}
		return out.toString();
	}

	private static String field(String v) {
		if (v == null) {
			return "";
		}
		if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf('\r') < 0) {
			return v;
		}
		return '"' + v.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + '"';
	}

	// ------------------------------------------------------------------ writing it

	/** Hands the line to the log's own thread, which appends it to the file. */
	private static CompletableFuture<Path> append(Map<String, String> row) {
		Path file = file();
		if (file == null) {
			return null;
		}
		String text = line(row);
		try {
			return CompletableFuture.supplyAsync(() -> write(file, text), writer());
		} catch (RejectedExecutionException e) {
			warn("The playtest log's writer is gone", e);
			return null;
		}
	}

	private static synchronized ExecutorService writer() {
		if (writer == null) {
			writer = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Extreme Dimensions playtest log");
				t.setDaemon(true);
				return t;
			});
		}
		return writer;
	}

	/**
	 * Appends one line to the file (the folder made if need be, and the header written first when the file is new or
	 * empty). Returns the file, or null when the write failed (logged once; never thrown at whoever waits for it).
	 */
	static Path write(Path file, String line) {
		try {
			Path dir = file.getParent();
			if (dir != null) {
				Files.createDirectories(dir);
			}
			boolean fresh = !Files.exists(file) || Files.size(file) == 0;
			String text = (fresh ? header() + "\n" : "") + line + "\n";
			Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
			warned = false;
			return file;
		} catch (IOException | RuntimeException e) {
			warn("Could not write the playtest log to " + file, e);
			return null;
		}
	}

	/**
	 * Waits, a few seconds at most, until every line handed to the writer so far is written: as the server stops, and for
	 * the tests. (The writer is a single thread, so a task given to it now runs after every line before it.)
	 */
	public static void flush() {
		ExecutorService w;
		synchronized (PlaytestLog.class) {
			w = writer;
		}
		if (w == null) {
			return;
		}
		try {
			CompletableFuture.runAsync(() -> {}, w).get(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RejectedExecutionException e) {
			// (a slow disk finishes on its own; the daemon thread is no reason to keep the server from stopping)
		}
	}

	private static void warn(String what, Exception e) {
		if (!warned) {
			warned = true;
			ExtremeDimensions.LOGGER.warn("{} (said once, until it works again)", what, e);
		}
	}
}
