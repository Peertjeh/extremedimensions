package com.extremedimensions.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.seam.FirstKnot;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The wound's own sounds, now and then, the way the game has its cave and biome moods. In the Overworld a groan from
 * under the deepslate, heavy steps far below, giggling near a hoard at night, a shriek far off, and the sky tearing once
 * the seal is broken; in the Nether the heat's slow throb, the Ferryman's bell over the lava, basalt cracking deep down
 * and the undertow; in the End the undertow and a call from beneath the islands; in the Fray the undertow and, by a
 * knot, its heartbeat. Each is heard by one player alone, from somewhere around them, on the Ambient volume.
 *
 * <p>And the dread: a swell under everything that breaks off into silence, before a swarm (which waits for it) and when
 * a Lurker turns to hunt.
 */
public final class AmbientService {
	private AmbientService() {}

	/** Where a player is, as far as the wound's sounds care. */
	public record Place(String dim, String biome, int y, boolean sky, boolean dark, boolean night, boolean nearHoard,
			boolean sealBroken, boolean fray, boolean nearKnot, boolean outerEnd) {}

	/** Ticks between two looks at each player (each on a tick of their own). */
	private static final int CHECK = 200;
	/** How long the dread swells before it breaks off (ticks): what it warns of comes then. */
	public static final int DREAD_TICKS = 180;

	/** When (server tick) each player may hear the next. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	public static void clear() {
		NEXT.clear();
	}

	public static void forget(UUID player) {
		NEXT.remove(player);
	}

	/** The sounds that fit a place (names after {@code ambient.}), each as many times as it is likely. */
	public static List<String> choices(Place pl) {
		List<String> out = new ArrayList<>();
		if (Dims.OVERWORLD.equals(pl.dim())) {
			if (!pl.sky() && pl.y() < 0) {
				add(out, "deep_groan", 3);
				add(out, "far_steps", 1);
			} else if (!pl.sky() && pl.y() < 40 && pl.dark()) {
				add(out, "far_steps", 2);
				add(out, "deep_groan", 1);
			}
			if (pl.nearHoard() && pl.night()) {
				add(out, "hollow_far", 4);
			}
			if (pl.sky() && pl.night()) {
				add(out, "far_shriek", 1);
			}
			if (pl.sky() && pl.sealBroken()) {
				add(out, "storm_tear", 1);
			}
		} else if (Dims.NETHER.equals(pl.dim())) {
			switch (pl.biome()) {
				case "basalt_deltas" -> add(out, "basalt_deep", 4);
				case "nether_wastes", "crimson_forest" -> add(out, "ember_pulse", 4);
				default -> add(out, "ember_pulse", 1);
			}
			if (pl.y() < 40) {
				add(out, "undertow_nether", 2);
			}
			if (pl.y() < 48) {
				add(out, "ferryman_bell", 1);
			}
		} else if (Dims.END.equals(pl.dim())) {
			if (pl.outerEnd()) {
				add(out, "undertow_end", 2);
				add(out, "abyss_call", 2);
			} else {
				add(out, "abyss_call", 1);
			}
		} else if (Dims.SEAM.equals(pl.dim()) && pl.fray()) {
			add(out, "undertow_fray", 3);
			add(out, "abyss_call", 1);
			add(out, "knot_heartbeat", pl.nearKnot() ? 4 : 1);
		}
		return out;
	}

	private static void add(List<String> out, String name, int times) {
		for (int i = 0; i < times; i++) {
			out.add(name);
		}
	}

	/** Where this player is. */
	public static Place place(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		BlockPos at = p.blockPosition();
		String dim = Dims.of(p);
		Holder<Biome> b = level.getBiome(at);
		String biome = b.is(Biomes.BASALT_DELTAS) ? "basalt_deltas" : b.is(Biomes.NETHER_WASTES) ? "nether_wastes"
				: b.is(Biomes.CRIMSON_FOREST) ? "crimson_forest" : "other";
		boolean sky = level.canSeeSky(at.above());
		boolean dark = level.getBrightness(LightLayer.BLOCK, at) < 6 && !sky;
		boolean fray = Dims.SEAM.equals(dim) && p.getY() < Config.SEAM_FRAY_TOP;
		return new Place(dim, biome, at.getY(), sky, dark, level.isDarkOutside(), nearestHoard(p, 64) != null, DataStore.sealBroken(), fray,
				fray && nearKnot(p), Dims.END.equals(dim) && p.getX() * p.getX() + p.getZ() * p.getZ() > 800.0 * 800.0);
	}

	private static Hoard nearestHoard(ServerPlayer p, double range) {
		String dim = Dims.of(p);
		Hoard best = null;
		double bestD = range * range;
		for (Hoard h : DataStore.hoards()) {
			double d = h.distSq(p.getX(), p.getY(), p.getZ());
			if (h.dim.equals(dim) && d < bestD) {
				best = h;
				bestD = d;
			}
		}
		return best;
	}

	private static boolean nearKnot(ServerPlayer p) {
		for (int[] k : SeamDepth.knots()) {
			if (p.distanceToSqr(k[0] + 0.5, k[1], k[2] + 0.5) < 40 * 40) {
				return true;
			}
		}
		int[] heart = FirstKnot.where();
		return heart != null && p.distanceToSqr(heart[0] + 0.5, heart[1], heart[2] + 0.5) < 60 * 60;
	}

	public static void tick(MinecraftServer s) {
		if (Config.AMBIENT_CHANCE <= 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		long now = s.getTickCount();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			// (not while something of the tribe is after them: then the real thing is near)
			if ((now + p.getId()) % CHECK != 0 || p.isSpectator() || !p.isAlive() || !Dims.ours(p) || LurkerService.threat(p) != null) {
				continue;
			}
			Long next = NEXT.get(p.getUUID());
			if (next != null && now < next) {
				continue;
			}
			if (rnd.nextDouble() >= Config.AMBIENT_CHANCE) {
				continue;
			}
			List<String> fits = choices(place(p));
			if (fits.isEmpty()) {
				continue;
			}
			play(p, fits.get(rnd.nextInt(fits.size())));
			NEXT.put(p.getUUID(), now + (Math.max(10, Config.AMBIENT_MIN_SECONDS) + rnd.nextInt(Math.max(10, Config.AMBIENT_MIN_SECONDS))) * 20L);
		}
	}

	/** One of the wound's sounds for this player alone, from somewhere round them (giggling from the hoard's side). */
	public static void play(ServerPlayer p, String name) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double a = rnd.nextDouble() * Math.PI * 2;
		Hoard h = "hollow_far".equals(name) ? nearestHoard(p, 64) : null;
		if (h != null) {
			a = Math.atan2(h.z + 0.5 - p.getZ(), h.x + 0.5 - p.getX());
		}
		double r = 8 + rnd.nextDouble() * 10;
		double dy = name.startsWith("deep") || name.startsWith("far_steps") || name.startsWith("basalt") || name.startsWith("undertow")
				|| name.startsWith("abyss") || name.startsWith("knot") ? -6 : 2 + rnd.nextDouble() * 4;
		WorldFx.soundFor(p, "extremedimensions:ambient." + name, "ambient", p.getX() + Math.cos(a) * r, p.getY() + dy, p.getZ() + Math.sin(a) * r,
				(float) Math.max(0, Config.AMBIENT_VOLUME), 0.96f + rnd.nextFloat() * 0.08f);
	}

	/**
	 * The dread: something swells under everything and breaks off into silence (it breaks off after {@link #DREAD_TICKS}).
	 * False when the dread is switched off; whatever it warns of then comes at once.
	 */
	public static boolean dread(ServerPlayer p) {
		if (Config.AMBIENT_DREAD <= 0) {
			return false;
		}
		WorldFx.soundFor(p, "extremedimensions:ambient.dread_rise", "ambient", p.getX(), p.getY() + 1, p.getZ(), (float) Math.max(0.2, Config.AMBIENT_VOLUME), 1.0f);
		return true;
	}
}
