package net.battlehub.debug;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.BattleHub;
import net.battlehub.arch.arena.ArenaCheck;
import net.battlehub.arch.arena.ArenaPlan;
import net.battlehub.arch.check.Report;
import net.battlehub.arch.render.Render;
import net.battlehub.arena.ArchArenas;
import net.battlehub.arena.ArenaWorld;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Build;
import net.battlehub.arena.Theme;
import net.battlehub.core.GridPath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Arenas from the architecture engine in the real game: Bed Wars maps in every theme and team count pass the
 * arena check (points, free zones, routes, lanes, fairness, nothing floating), every block name exists, and one
 * map placed in the arena world matches its plan and can be walked from each spawn to its bed, generator and
 * shopkeeper. Pictures for the log.
 */
final class ArenaArchTests {
	private ArenaArchTests() {}

	/** Where the test map is placed: far from every arena slot (slots are at positive coordinates). */
	private static final int AT = -4096;

	static void all(MinecraftServer server, Script s) {
		String tag = "arena architecture";
		s.check(tag + ": Bed Wars maps in every theme for 2, 3, 4, 6 and 8 teams pass the arena check", () -> {
			List<String> problems = new ArrayList<>();
			long seed = 11;
			for (Theme t : Theme.values()) {
				for (int teams : new int[] {2, 3, 4, 6, 8}) {
					ArenaPlan p = ArchArenas.bedwarsPlan(t, seed++, teams, 24, 10, teams == 4);
					Report r = ArenaCheck.bedwars(p, teams);
					if (!r.ok()) problems.add(t.id() + "/" + teams + ": " + r.errors());
					ArchArenas.blueprint(p);
				}
			}
			for (String p : problems.subList(0, Math.min(10, problems.size()))) BattleHub.LOGGER.error("SELFTEST arena problem: {}", p);
			if (!Build.unknown().isEmpty()) BattleHub.LOGGER.warn("SELFTEST arena: unknown block names {}", Build.unknown());
			return problems.isEmpty() && Build.unknown().isEmpty();
		});
		s.check(tag + ": a Bed Wars map placed in the world matches its plan and every team walks to its bed, generator and shop", () -> {
			ServerLevel level = ArenaWorld.level(server);
			if (level == null) return false;
			ArenaPlan p = ArchArenas.bedwarsPlan(Theme.CASTLE, 7, 4, 24, 10, false);
			BlockPos o = new BlockPos(AT, net.battlehub.config.ConfigManager.config().arenas.baseY, AT);
			Blueprint bp = ArchArenas.blueprint(p);
			bp.blocks().forEach((k, st) -> level.setBlock(o.offset(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)), st, 2 | 16 | 32));
			int[] counts = {0, 0};
			p.voxels.forEach((x, y, z, id) -> {
				if (((x * 13 + z * 7 + y * 5) & 15) != 0 || id.equals("air")) return;
				counts[0]++;
				BlockState st = level.getBlockState(o.offset(x, y, z));
				if (!BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().equals(net.battlehub.arch.Props.base(id))) counts[1]++;
			});
			GridPath.Terrain t = ArchTests.terrain(level);
			boolean ok = counts[0] > 500 && counts[1] == 0;
			for (int i = 0; i < 4; i++) {
				int[] spawn = p.mark("spawn_" + i);
				for (String goal : new String[] {"bed_", "gen_", "shop_"}) {
					int[] g = p.mark(goal + i);
					int sy = ArchTests.standY(t, o.getX() + spawn[0], o.getY() + spawn[1], o.getZ() + spawn[2]);
					List<GridPath.Node> path = sy == Integer.MIN_VALUE ? null
							: GridPath.find(t, new GridPath.Node(o.getX() + spawn[0], sy, o.getZ() + spawn[2]), new GridPath.Node(o.getX() + g[0], o.getY() + g[1], o.getZ() + g[2]), 1.6, 40000);
					if (path == null) {
						BattleHub.LOGGER.warn("SELFTEST arena: team {} cannot walk to its {}", i, goal);
						ok = false;
					}
				}
			}
			BattleHub.LOGGER.info("SELFTEST arena: Bed Wars map placed, {} sampled blocks, {} differ", counts[0], counts[1]);
			BlockState air = Blocks.AIR.defaultBlockState();
			bp.blocks().keySet().forEach(k -> level.setBlock(o.offset(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)), air, 2 | 16 | 32));
			return ok;
		});
		s.check(tag + ": duel arenas get stands and engine cover, and the cover is the same on both halves", () -> {
			net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(77);
			boolean ok = true;
			for (Theme t : new Theme[] {Theme.CASTLE, Theme.DESERT, Theme.NETHER, Theme.CHERRY}) {
				Blueprint bp = net.battlehub.arena.layout.DuelLayout.design(t, rnd, true, 1.4, 1);
				int seats = 0, solid = 0, unlike = 0;
				for (var e : bp.blocks().entrySet()) {
					long k = e.getKey();
					int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
					BlockState st = e.getValue();
					if (st.getBlock() instanceof net.minecraft.world.level.block.StairBlock && !bp.inPlayArea(x, 1, z)) seats++;
					if (y >= 1 && bp.inPlayArea(x, y, z) && Math.hypot(x, z) < 10 && st.blocksMotion()) {
						solid++;
						if (!bp.get(-x, y, -z).blocksMotion()) unlike++;
					}
				}
				BattleHub.LOGGER.info("SELFTEST arena: duel {}: {} stair blocks in the stands, {} solid blocks in the middle, {} without a twin, core {}", t.id(),
						seats, solid, unlike, bp.core() == null ? "none" : java.util.Arrays.toString(bp.core()));
				ok &= seats > 100 && bp.core() != null && unlike <= Math.max(2, solid / 30);
			}
			return ok;
		});
		s.run(tag + ": pictures of Bed Wars maps for the log", () -> {
			ArenaPlan castle = ArchArenas.bedwarsPlan(Theme.CASTLE, 7, 4, 24, 10, true);
			Render rd = new Render(castle.voxels);
			Preview.log("bedwars_castle_world", rd.view(200, 18, 0.7, 960, 600));
			int e = 54;
			Preview.log("bedwars_castle_map", rd.view(30, 40, 0.62, 960, 600, new int[] {-e, -12, -e, e, 14, e}));
			ArenaPlan.Isle me = castle.isles("team").get(0);
			Preview.log("bedwars_castle_island", rd.perspective(new double[] {me.cx() - 12, 13, me.cz() - 20}, new double[] {me.cx(), 1, me.cz()}, 55, 960, 600));
			ArenaPlan nether = ArchArenas.bedwarsPlan(Theme.NETHER, 11, 8, 24, 10, true);
			int n = 70;
			Preview.log("bedwars_nether_8_teams", new Render(nether.voxels).view(30, 40, 0.62, 960, 600, new int[] {-n, -12, -n, n, 14, n}));
			ArenaPlan cherry = ArchArenas.bedwarsPlan(Theme.CHERRY, 13, 4, 24, 10, true);
			ArenaPlan.Isle c0 = cherry.isles("team").get(0);
			Preview.log("bedwars_cherry_from_the_spawn", new Render(cherry.voxels).perspective(new double[] {c0.cx() + 0.5, 2.6, c0.cz() - 1.5},
					new double[] {0, 4, 0}, 75, 960, 600));
		});
		s.run(tag + ": pictures of dressed duel and party arenas for the log", () -> {
			net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(5);
			Blueprint duel = net.battlehub.arena.layout.DuelLayout.design(Theme.CASTLE, rnd, true, 1.4, 1);
			Preview.log("duel_castle_stands", Preview.iso(duel, 2, false));
			Blueprint koth = net.battlehub.arena.layout.PartyLayouts.koth(Theme.DESERT, rnd);
			Preview.log("koth_desert_stands", Preview.iso(koth, 2, false));
			Blueprint sumo = net.battlehub.arena.layout.PartyLayouts.sumo(Theme.CHERRY, rnd);
			Preview.log("sumo_cherry_islands", Preview.iso(sumo, 2, false));
		});
	}
}
