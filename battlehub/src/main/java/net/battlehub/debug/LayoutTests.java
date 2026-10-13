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
		m.put("duel", t -> DuelLayout.design(t, rnd));
		m.put("bow_spleef", t -> PartyLayouts.spleef(t, rnd));
		m.put("tnt_run", t -> PartyLayouts.tntRun(t, rnd, 3));
		m.put("sumo", t -> PartyLayouts.sumo(t, rnd));
		m.put("koth", t -> PartyLayouts.koth(t, rnd));
		m.put("ctf", t -> PartyLayouts.ctf(t, rnd));
		m.put("parkour", t -> PartyLayouts.parkour(t, rnd, ParkourCourse.generate(rnd.nextLong(), 36, 9, 12)));
		m.put("lms", t -> PartyLayouts.lms(t, rnd));
		return m;
	}

	private static List<String> required(String layout) {
		return switch (layout) {
			case "koth" -> List.of("zone");
			case "ctf" -> List.of("flag_red", "flag_blue", "spawn_red", "spawn_blue");
			default -> List.of();
		};
	}

	static void all(Script s) {
		s.check("layouts: every layout in every theme is sound", () -> {
			List<String> problems = new ArrayList<>();
			RandomSource rnd = RandomSource.create(1234);
			for (int round = 0; round < 3; round++) {
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
