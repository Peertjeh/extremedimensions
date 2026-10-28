package com.extremedimensions.compat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tapestry.Tapestry;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The stats export: every {@code export_minutes} and when the server stops, one file {@code extremedimensions.json} in
 * the folder {@code export_dir} (a relative path from the server's folder), for a web page to read: the world's
 * difficulty, ending, tapestry, New Thread+ and trial, and every player the mod has data for, online or not. It is worked
 * out on the server's thread and written on a thread of its own, first to a temporary file that then takes the old
 * one's place in one step, so a reader never sees half a file. Its words are the tablist's (see {@link TabPlaceholders}).
 */
public final class StatsExport {
	private StatsExport() {}

	public static final String FILE = ExtremeDimensions.MOD_ID + ".json";

	/** The four Elders, in the order of the bits in {@link PlayerData#elders}, with their names and the advancement each one's fall gives. */
	public enum Elder {
		MATRIARCH("The Matriarch", Advancements.MATRIARCH_SLAIN),
		FERRYMAN("The Ferryman", Advancements.FERRYMAN_SLAIN),
		WEAVER("The Last Weaver", Advancements.WEAVER_SLAIN),
		KNOT_MOTHER("The Knot Mother", Advancements.KNOT_MOTHER);

		public final String title;
		final String advancement;

		Elder(String title, String advancement) {
			this.title = title;
			this.advancement = advancement;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static ExecutorService writer;
	/** A write failed and was logged: the next failures are not, until a write works again. */
	private static volatile boolean warned = false;
	/** The thread that last wrote the file (for the tests: never the server's). */
	public static volatile String writtenBy = "";

	/** Every server tick: every {@code export_minutes} the export is written anew (when there is a folder for it). */
	public static void tick(MinecraftServer s) {
		if (Config.EXPORT_DIR.isBlank() || s.getTickCount() % (Math.max(1, Config.EXPORT_MINUTES) * 1200) != 0) {
			return;
		}
		try {
			export(s, false);
		} catch (RuntimeException e) {
			// (never a reason to stop the server)
			warn("Could not work out the stats export", e);
		}
	}

	/** The server stops: the export is written once more, with nobody online, and waited for (a few seconds at most). */
	public static void stopping(MinecraftServer s) {
		try {
			CompletableFuture<Path> done = export(s, true);
			if (done != null) {
				done.get(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
			// (a failed write is logged where it failed; a slow one finishes on its own)
		} catch (RuntimeException e) {
			warn("Could not work out the stats export", e);
		}
	}

	/**
	 * Works the export out now and writes it on the export's own thread: the file it was written to, or null when there is
	 * no folder for it ({@code export_dir} empty or not a path).
	 */
	public static CompletableFuture<Path> export(MinecraftServer s, boolean stopping) {
		Path dir = dir();
		if (dir == null) {
			return null;
		}
		JsonObject json = json(s, stopping);
		return CompletableFuture.supplyAsync(() -> write(dir, json), writer());
	}

	/** The folder of the export, from the server's folder; null when there is none. */
	public static Path dir() {
		String dir = Config.EXPORT_DIR.trim();
		if (dir.isEmpty()) {
			return null;
		}
		try {
			return FabricLoader.getInstance().getGameDir().resolve(dir).normalize();
		} catch (InvalidPathException e) {
			warn("export_dir \"" + dir + "\" is not a folder", null);
			return null;
		}
	}

	private static synchronized ExecutorService writer() {
		if (writer == null) {
			writer = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Extreme Dimensions stats export");
				t.setDaemon(true);
				return t;
			});
		}
		return writer;
	}

	/** Writes the export into the folder (made if need be): first to a temporary file, which then takes the old one's place. */
	static Path write(Path dir, JsonObject json) {
		Path file = dir.resolve(FILE), tmp = dir.resolve(FILE + ".tmp");
		try {
			Files.createDirectories(dir);
			Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			warned = false;
			writtenBy = Thread.currentThread().getName();
			return file;
		} catch (IOException e) {
			warn("Could not write the stats export to " + file, e);
			throw new UncheckedIOException(e);
		}
	}

	private static void warn(String what, Exception e) {
		if (!warned) {
			warned = true;
			if (e == null) {
				ExtremeDimensions.LOGGER.warn("{} (said once, until it works again)", what);
			} else {
				ExtremeDimensions.LOGGER.warn("{} (said once, until it works again)", what, e);
			}
		}
	}

	/**
	 * The export, worked out now (on the server's thread). Stopping, nobody counts as online: the file is what a web page
	 * shows while the server is down.
	 */
	public static JsonObject json(MinecraftServer s, boolean stopping) {
		JsonObject out = new JsonObject();
		out.addProperty("generated", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
		out.addProperty("version", FabricLoader.getInstance().getModContainer(ExtremeDimensions.MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(""));

		JsonObject world = new JsonObject();
		world.addProperty("difficulty", TabPlaceholders.difficulty());
		world.addProperty("ending", TabPlaceholders.ending());
		JsonObject tapestry = new JsonObject();
		tapestry.addProperty("woven", Tapestry.count());
		tapestry.addProperty("total", Tapestry.Panel.values().length);
		tapestry.addProperty("finished", DataStore.tapestryDone());
		world.add("tapestry", tapestry);
		world.addProperty("newThread", DataStore.newThread());
		world.addProperty("trial", TabPlaceholders.trial());
		out.add("world", world);

		Map<UUID, ServerPlayer> online = new HashMap<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			online.put(p.getUUID(), p);
			DataStore.get(p.getUUID());
		}
		List<JsonObject> players = new ArrayList<>();
		for (String key : DataStore.playerIds()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			ServerPlayer seen = online.get(id);
			players.add(player(id, DataStore.get(id), seen, stopping ? null : seen));
		}
		players.sort(Comparator.comparing((JsonObject o) -> o.get("name").getAsString().toLowerCase(Locale.ROOT))
				.thenComparing(o -> o.get("uuid").getAsString()));
		JsonArray list = new JsonArray();
		players.forEach(list::add);
		out.add("players", list);
		return out;
	}

	/** One player: {@code seen} if they are on the server, {@code p} if they count as online (not while it stops). */
	private static JsonObject player(UUID id, PlayerData d, ServerPlayer seen, ServerPlayer p) {
		if (seen != null) {
			elders(seen);
		}
		JsonObject o = new JsonObject();
		o.addProperty("uuid", id.toString());
		o.addProperty("name", seen != null ? seen.getScoreboardName() : d.name == null ? "" : d.name);
		o.addProperty("online", p != null);
		o.addProperty("realm", p == null ? "" : TabPlaceholders.realm(p, d));
		o.addProperty("status", p == null ? "" : TabPlaceholders.status(p, d));
		o.addProperty("curses", TabPlaceholders.curses(d, p != null));
		o.addProperty("abyssal", TabPlaceholders.abyssal(d, p != null));
		o.addProperty("scars", d.scars);
		o.addProperty("expeditions", d.expeditions);
		o.addProperty("collapses", d.collapses);
		o.addProperty("unwoven", d.unwoven);
		o.addProperty("trialMedal", TabPlaceholders.trialMedal(d));
		// Only what is known: left out for a player whose advancements were not looked at since 3.6.0
		if (d.elders >= 0) {
			o.addProperty("eldersSlain", Integer.bitCount(d.elders & 0xF));
			JsonArray names = new JsonArray();
			for (Elder e : Elder.values()) {
				if ((d.elders & 1 << e.ordinal()) != 0) {
					names.add(e.title);
				}
			}
			o.add("elders", names);
		}
		return o;
	}

	/** Which Elders a player helped defeat, read from their advancements and kept for when they are offline. */
	public static int elders(ServerPlayer p) {
		int mask = 0;
		for (Elder e : Elder.values()) {
			if (WorldFx.hasAdvancement(p, e.advancement)) {
				mask |= 1 << e.ordinal();
			}
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (d.elders != mask) {
			d.elders = mask;
			DataStore.markDirty();
		}
		return mask;
	}

	/** An advancement was granted: an Elder's fall is noted at once (a player may leave before the next export). */
	public static void earned(ServerPlayer p, String advancement) {
		for (Elder e : Elder.values()) {
			if (e.advancement.equals(advancement)) {
				elders(p);
				return;
			}
		}
	}
}
