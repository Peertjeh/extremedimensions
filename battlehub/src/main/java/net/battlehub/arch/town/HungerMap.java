package net.battlehub.arch.town;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.battlehub.arch.Rng;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.bldg.Building;
import net.battlehub.arch.mod.Library;

/**
 * The Hunger Games map: a lobby city from {@link Lobby}, a new one every week (the seed comes from the week), with
 * what a Hunger Games needs. Everyone starts on the dark ring of the plaza round the fountain, with the cornucopia
 * (a ring of chests with the best loot) between them and the fountain. Every house has its chest (and barrel),
 * the landmarks and watchtowers get a few chests with better loot inside. Doors are taken out so everyone (bots too)
 * walks in and out. The theme of a match only changes materials ({@link LobbyThemes}), never where anything is.
 */
public final class HungerMap {
	/** A spot on the map; {@code facing} 0 north, 1 east, 2 south, 3 west. */
	public record Spot(int x, int y, int z, int facing) {}

	public static final int SPAWNS = 16;
	public static final double SPAWN_RING = Plaza.at(13);
	public static final int CORNUCOPIA = 12;
	public static final double CORNUCOPIA_RING = 10.5;

	public final long seed;
	public final int radius;
	public final Voxels voxels;
	public final List<Spot> spawns = new ArrayList<>();
	public final List<Spot> houseChests = new ArrayList<>();
	public final List<Spot> landmarkChests = new ArrayList<>();
	public final List<Spot> cornucopia = new ArrayList<>();
	public int doorsRemoved;

	private HungerMap(long seed, int radius, Voxels voxels) {
		this.seed = seed;
		this.radius = radius;
		this.voxels = voxels;
	}

	/** The same seed all week (ISO weeks, Monday to Sunday), another one the next week. */
	public static long weekSeed(long base, LocalDate day) {
		WeekFields w = WeekFields.ISO;
		long week = day.get(w.weekBasedYear()) * 100L + day.get(w.weekOfWeekBasedYear());
		long z = base + week * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** "2026-W43": the week a map belongs to. */
	public static String weekLabel(LocalDate day) {
		WeekFields w = WeekFields.ISO;
		return String.format(Locale.ROOT, "%d-W%02d", day.get(w.weekBasedYear()), day.get(w.weekOfWeekBasedYear()));
	}

	public static HungerMap generate(long seed, int radius, int detail, double density, Library lib) {
		Lobby.Settings s = new Lobby.Settings();
		s.seed = seed;
		s.radius = radius;
		s.detail = detail;
		s.density = density;
		return of(Lobby.generate(s, lib), seed, radius);
	}

	static HungerMap of(Lobby.Result r, long seed, int radius) {
		HungerMap m = new HungerMap(seed, radius, r.voxels);
		Voxels v = r.voxels;
		// doors out, and the house chests and barrels as they are
		List<int[]> doors = new ArrayList<>();
		v.forEach((x, y, z, id) -> {
			String name = base(id);
			if (name.endsWith("_door")) doors.add(new int[] {x, y, z});
			else if (name.equals("chest") || name.equals("barrel")) m.houseChests.add(new Spot(x, y, z, facing(id)));
		});
		for (int[] d : doors) v.remove(d[0], d[1], d[2]);
		m.doorsRemoved = doors.size();
		Collections.sort(m.houseChests, (a, b) -> a.x != b.x ? Integer.compare(a.x, b.x) : a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.y, b.y));
		// chests in the landmarks and watchtowers
		Rng rng = new Rng(seed ^ 0x48756E676572L);
		for (Building b : r.buildings) {
			int n = switch (b.brief.archetype) {
				case "castle", "cathedral" -> 4;
				case "mage_tower", "temple_ruin" -> 3;
				case "chapel" -> 2;
				case "watchtower" -> 1;
				default -> 0;
			};
			if (n > 0) m.landmarkChests.addAll(indoor(v, b.worldBox(), n, rng.stream(b.brief.archetype + b.worldBox()[0] + "," + b.worldBox()[2])));
		}
		for (Spot s : m.landmarkChests) v.set(s.x, s.y, s.z, "chest[facing=" + FACING[s.facing] + "]");
		// the cornucopia and the spawns on the plaza
		for (int i = 0; i < CORNUCOPIA; i++) {
			double a = (i + 0.5) * 2 * Math.PI / CORNUCOPIA;
			int x = (int) Math.round(Math.cos(a) * CORNUCOPIA_RING), z = (int) Math.round(Math.sin(a) * CORNUCOPIA_RING);
			Spot s = new Spot(x, 1, z, outward(x, z));
			m.cornucopia.add(s);
			v.set(x, 1, z, "chest[facing=" + FACING[s.facing] + "]");
		}
		for (int i = 0; i < SPAWNS; i++) {
			double a = i * 2 * Math.PI / SPAWNS;
			int x = (int) Math.round(Math.cos(a) * SPAWN_RING), z = (int) Math.round(Math.sin(a) * SPAWN_RING);
			m.spawns.add(new Spot(x, 1, z, (outward(x, z) + 2) % 4));
		}
		return m;
	}

	static final String[] FACING = {"north", "east", "south", "west"};

	/** The side facing away from the middle of the plaza. */
	static int outward(int x, int z) {
		if (Math.abs(x) >= Math.abs(z)) return x >= 0 ? 1 : 3;
		return z >= 0 ? 2 : 0;
	}

	static String base(String id) {
		int open = id.indexOf('[');
		return open < 0 ? id : id.substring(0, open);
	}

	static int facing(String id) {
		for (int i = 0; i < 4; i++) {
			if (id.contains("facing=" + FACING[i])) return i;
		}
		return 2;
	}

	/** Spots inside a building (a roof above) on a full block, against a wall, with room in front: for a chest. */
	static List<Spot> indoor(Voxels v, int[] box, int n, Rng rng) {
		List<Spot> all = new ArrayList<>();
		for (int x = box[0]; x <= box[3]; x++) {
			for (int z = box[2]; z <= box[5]; z++) {
				for (int y = box[1] + 1; y <= box[4] - 2; y++) {
					if (!full(v.get(x, y - 1, z)) || !free(v, x, y, z) || !free(v, x, y + 1, z) || !roofed(v, x, y + 2, z)) continue;
					int open = -1;
					boolean[] wall = new boolean[4];
					for (int d = 0; d < 4; d++) {
						int nx = x + DX[d], nz = z + DZ[d];
						if (!free(v, nx, y, nz)) wall[d] = true;
						else if (free(v, nx, y + 1, nz) && full(v.get(nx, y - 1, nz))) open = d;
					}
					// against a wall, but not in a doorway or a passage (walls on both sides)
					boolean against = wall[0] || wall[1] || wall[2] || wall[3];
					boolean passage = (wall[0] && wall[2]) || (wall[1] && wall[3]);
					if (against && !passage && open >= 0) all.add(new Spot(x, y, z, open));
				}
			}
		}
		// lowest floors first (the ones you walk into), spread out
		Collections.shuffle(all, new java.util.Random(rng.seed()));
		all.sort((a, b) -> Integer.compare(a.y / 4, b.y / 4));
		List<Spot> out = new ArrayList<>();
		for (Spot s : all) {
			if (out.size() >= n) break;
			boolean near = false;
			for (Spot o : out) near |= Math.abs(o.x - s.x) + Math.abs(o.z - s.z) + Math.abs(o.y - s.y) < 6;
			if (!near) out.add(s);
		}
		return out;
	}

	static final int[] DX = {0, 1, 0, -1};
	static final int[] DZ = {-1, 0, 1, 0};

	static boolean free(Voxels v, int x, int y, int z) {
		String s = v.get(x, y, z);
		return s == null || s.equals("air");
	}

	private static boolean roofed(Voxels v, int x, int y, int z) {
		for (int dy = 0; dy < 14; dy++) {
			if (!free(v, x, y + dy, z)) return true;
		}
		return false;
	}

	private static final java.util.regex.Pattern NOT_FULL = java.util.regex.Pattern.compile(
			".*(slab|stairs|wall|fence|pane|door|carpet|leaves|bars|chain|lantern|torch|candle|bed|banner|button|sign|tulip|poppy|dandelion|cornflower|bluet"
					+ "|daisy|orchid|short_grass|tall_grass|fern|petals|vine|rod|campfire|pot|azalea|bush|cluster|water|lava|snow|rail|ladder|plate|lectern|stand|cauldron"
					+ "|table|bell|chest|barrel|furnace|beacon|glass|anvil|head|skull|path|farmland|flower|sapling|roots|fungus|scaffolding|lichen|moss_carpet).*");

	/** A whole block to put a chest on. */
	public static boolean full(String id) {
		if (id == null || id.equals("air")) return false;
		return !NOT_FULL.matcher(base(id)).matches();
	}
}
