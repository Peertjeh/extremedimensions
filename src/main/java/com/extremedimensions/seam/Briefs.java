package com.extremedimensions.seam;

import java.io.Reader;

import com.extremedimensions.ExtremeDimensions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * The Seam's briefs, as data: which kinds of island it raises and how often, how big each kind grows, and how often the
 * open ground of a plain island carries an arch of rock or a terraced hill. They are read from
 * {@code data/extremedimensions/seam/briefs.json} (a datapack may replace the file) when the server starts and on
 * every reload; what a file leaves out keeps its default, and a file that cannot be read is reported and changes
 * nothing. Islands already built stay as they are.
 */
public final class Briefs {
	private Briefs() {}

	public static final Identifier FILE = ExtremeDimensions.id("seam/briefs.json");

	/** The island kinds, in the order the Seam numbers them. */
	public static final String[] KINDS = {"plain", "loom", "village", "spire", "hanging", "halo"};

	/** One set of briefs: a weight and a range of radii per kind, and the chances of the land's shapes. */
	public record Brief(int[] weights, int[][] radii, double arches, double terraces) {
		/** The kind of a cell's island, from a number drawn for it (the same number, the same kind). */
		public int kind(long draw) {
			int total = 0;
			for (int v : weights) {
				total += v;
			}
			int t = (int) Math.floorMod(draw, (long) total);
			for (int i = 0; i < weights.length; i++) {
				t -= weights[i];
				if (t < 0) {
					return i;
				}
			}
			return 0;
		}

		/** The radius of an island of a kind, from a number drawn for it. */
		public int radius(int kind, long draw) {
			int[] r = radii[kind];
			return r[0] + (int) Math.floorMod(draw, (long) (r[1] - r[0] + 1));
		}
	}

	/** As the mod ships them (the mix and sizes the Seam had before they were data). */
	public static final Brief DEFAULT = new Brief(new int[] {7, 3, 3, 3, 2, 2},
			new int[][] {{11, 19}, {22, 26}, {26, 30}, {34, 38}, {14, 18}, {16, 20}}, 0.25, 0.2);

	private static volatile Brief current = DEFAULT;

	public static Brief current() {
		return current;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Briefs::load);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				load(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> current = DEFAULT);
	}

	/** Reads the briefs from the server's data. */
	public static void load(MinecraftServer server) {
		var found = server.getResourceManager().getResource(FILE);
		if (found.isEmpty()) {
			current = DEFAULT;
			return;
		}
		try (Reader in = found.get().openAsReader()) {
			current = parse(JsonParser.parseReader(in).getAsJsonObject());
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.error("The Seam's briefs ({}) could not be read; the defaults stand", FILE, e);
			current = DEFAULT;
		}
	}

	/** What a briefs file says; anything it leaves out, or gives out of bounds, keeps its default. */
	public static Brief parse(JsonObject json) {
		int[] w = DEFAULT.weights().clone();
		int[][] r = new int[KINDS.length][];
		for (int i = 0; i < KINDS.length; i++) {
			r[i] = DEFAULT.radii()[i].clone();
		}
		if (json.has("kinds")) {
			JsonObject kinds = json.getAsJsonObject("kinds");
			for (int i = 0; i < KINDS.length; i++) {
				if (kinds.has(KINDS[i])) {
					w[i] = Math.max(0, Math.min(1000, kinds.get(KINDS[i]).getAsInt()));
				}
			}
		}
		int total = 0;
		for (int v : w) {
			total += v;
		}
		if (total <= 0) {
			ExtremeDimensions.LOGGER.warn("The Seam's briefs give every kind of island a weight of 0; the default mix stands");
			w = DEFAULT.weights().clone();
		}
		if (json.has("radius")) {
			JsonObject radius = json.getAsJsonObject("radius");
			for (int i = 0; i < KINDS.length; i++) {
				JsonElement e = radius.get(KINDS[i]);
				if (e != null && e.isJsonArray() && e.getAsJsonArray().size() == 2) {
					int lo = Math.max(8, Math.min(48, e.getAsJsonArray().get(0).getAsInt()));
					int hi = Math.max(lo, Math.min(48, e.getAsJsonArray().get(1).getAsInt()));
					r[i] = new int[] {lo, hi};
				}
			}
		}
		double arches = DEFAULT.arches(), terraces = DEFAULT.terraces();
		if (json.has("landforms")) {
			JsonObject lf = json.getAsJsonObject("landforms");
			if (lf.has("rock_arch")) {
				arches = Math.max(0, Math.min(1, lf.get("rock_arch").getAsDouble()));
			}
			if (lf.has("terraces")) {
				terraces = Math.max(0, Math.min(1, lf.get("terraces").getAsDouble()));
			}
		}
		return new Brief(w, r, arches, terraces);
	}
}
