package net.battlehub.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.DuelLayout;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.ParkourCourse;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Every layout in every theme: it builds, players spawn on solid ground with room to stand, required marks exist. */
final class LayoutTests {
	private LayoutTests() {}

	private static Map<String, Function<Theme, Blueprint>> layouts(RandomSource rnd) {
		Map<String, Function<Theme, Blueprint>> m = new LinkedHashMap<>();
		m.put("duel", t -> DuelLayout.design(t, rnd, false));
		m.put("duel_ranged", t -> DuelLayout.design(t, rnd, true));
		m.put("duel_big", t -> DuelLayout.design(t, rnd, false, 1.8, 1));
		m.put("team2", t -> DuelLayout.design(t, rnd, false, 1.2, 2));
		m.put("team4", t -> DuelLayout.design(t, rnd, true, 1.45, 4));
		m.put("team8", t -> DuelLayout.design(t, rnd, false, 1.8, 8));
		m.put("bow_spleef", t -> PartyLayouts.spleef(t, rnd));
		m.put("tnt_run", t -> PartyLayouts.tntRun(t, rnd, 3));
		m.put("sumo", t -> PartyLayouts.sumo(t, rnd));
		m.put("koth", t -> PartyLayouts.koth(t, rnd));
		m.put("ctf", t -> PartyLayouts.ctf(t, rnd));
		m.put("parkour", t -> {
			ParkourCourse.Difficulty d = ParkourCourse.Difficulty.values()[rnd.nextInt(4)];
			return PartyLayouts.parkour(t, rnd, ParkourCourse.generate(rnd.nextLong(), d, d.defaultJumps, d.checkpointEvery, 14));
		});
		m.put("lms", t -> PartyLayouts.lms(t, rnd));
		m.put("ffa", t -> PartyLayouts.ffa(t, rnd));
		for (int teams = 2; teams <= 4; teams++) {
			int n = teams;
			m.put("bedwars" + n, t -> net.battlehub.arena.layout.BedWarsLayout.design(t, rnd, n));
		}
		m.put("prophunt", t -> net.battlehub.arena.layout.PropHuntLayout.design(t, rnd,
				net.battlehub.config.ConfigManager.prophunt().disguises));
		return m;
	}

	private static List<String> required(String layout) {
		return switch (layout) {
			case "koth" -> List.of("zone");
			case "ctf" -> List.of("flag_red", "flag_blue", "spawn_red", "spawn_blue");
			case "prophunt" -> List.of("hunter_cage", "hunter_release");
			default -> {
				if (!layout.startsWith("bedwars")) yield List.of();
				List<String> marks = new ArrayList<>(List.of("gen_diamond", "gen_emerald", "build_min", "build_max", "podium"));
				for (int i = 0; i < Integer.parseInt(layout.substring(7)); i++) {
					marks.addAll(List.of("spawn_" + i, "bed_" + i, "gen_" + i, "shop_" + i, "shopblock_" + i));
				}
				yield marks;
			}
		};
	}

	static void all(Script s) {
		s.check("layouts: every layout in every theme is sound", () -> {
			List<String> problems = new ArrayList<>();
			RandomSource rnd = RandomSource.create(1234);
			for (int round = 0; round < 6; round++) {
				for (Map.Entry<String, Function<Theme, Blueprint>> e : layouts(rnd).entrySet()) {
					for (Theme theme : Theme.values()) {
						String where = e.getKey() + "/" + theme.id();
						try {
							check(where, e.getKey(), e.getValue().apply(theme), problems);
						} catch (RuntimeException ex) {
							problems.add(where + ": " + ex);
						}
					}
				}
			}
			for (String p : problems.subList(0, Math.min(20, problems.size()))) {
				net.battlehub.BattleHub.LOGGER.error("SELFTEST layout problem: {}", p);
			}
			return problems.isEmpty();
		});
	}

	private static void check(String where, String layout, Blueprint bp, List<String> problems) {
		if (bp.isEmpty()) {
			problems.add(where + ": no blocks");
			return;
		}
		if (bp.spawns().isEmpty()) {
			problems.add(where + ": no spawns");
		}
		if (layout.startsWith("team")) {
			int perSide = Integer.parseInt(layout.substring(4));
			java.util.Set<String> distinct = new java.util.HashSet<>();
			for (Blueprint.Spawn sp : bp.spawns()) {
				distinct.add(Mth.floor(sp.x()) + "," + Mth.floor(sp.z()));
			}
			if (bp.spawns().size() != 2 * perSide || distinct.size() != 2 * perSide) {
				problems.add(where + ": " + bp.spawns().size() + " spawns (" + distinct.size() + " different), expected " + 2 * perSide);
			}
		}
		for (Blueprint.Spawn sp : bp.spawns()) {
			int x = Mth.floor(sp.x());
			int y = Mth.floor(sp.y());
			int z = Mth.floor(sp.z());
			if (!solid(bp, x, y - 1, z)) {
				problems.add(where + ": spawn " + x + "," + y + "," + z + " has nothing to stand on");
			}
			if (solid(bp, x, y + 1, z)) {
				problems.add(where + ": spawn " + x + "," + y + "," + z + " has no head room");
			}
			if (y - 1 <= bp.voidY()) {
				problems.add(where + ": spawn is below the void line");
			}
		}
		for (String mark : required(layout)) {
			if (bp.marks(mark).isEmpty()) {
				problems.add(where + ": mark " + mark + " missing");
			}
		}
		if (layout.startsWith("bedwars")) {
			int teams = Integer.parseInt(layout.substring(7));
			if (bp.spawns().size() != teams) {
				problems.add(where + ": " + bp.spawns().size() + " team spawns, expected " + teams);
			}
			for (int i = 0; i < teams; i++) {
				net.minecraft.core.BlockPos bed = bp.markOne("bed_" + i);
				if (!(bp.get(bed.getX(), bed.getY(), bed.getZ()).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
					problems.add(where + ": no bed at bed_" + i);
				}
				net.minecraft.core.BlockPos shop = bp.markOne("shopblock_" + i);
				if (!bp.has(shop.getX(), shop.getY(), shop.getZ())) {
					problems.add(where + ": no shop block for team " + i);
				}
			}
		}
		if (layout.equals("prophunt")) {
			net.minecraft.core.BlockPos cage = bp.markOne("hunter_cage");
			if (solid(bp, cage.getX(), cage.getY(), cage.getZ()) || !solid(bp, cage.getX(), cage.getY() - 1, cage.getZ())) {
				problems.add(where + ": the hunter cage has no room");
			}
		}
		Blueprint.Spawn spec = bp.spectator();
		double dx = spec.x() - 0.5;
		double dz = spec.z() - 0.5;
		if (dx * dx + dz * dz > (double) bp.radius() * bp.radius() || spec.y() < bp.voidY() - 25 || spec.y() > 70) {
			problems.add(where + ": spectator point outside the spectator bounds");
		}
	}

	private static boolean solid(Blueprint bp, int x, int y, int z) {
		return bp.has(x, y, z) && !bp.get(x, y, z).isAir();
	}
}
