package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.ModeStats;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.HubBoards;
import net.battlehub.hub.HubWorld;
import net.battlehub.hub.Travel;
import net.battlehub.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * The leaderboards above the lobby wall (total kills, total deaths, best parkour runner): where they hang, who is
 * on them in which order, the player's own line, switching modes, and the boards coming back after a player left
 * the hub world.
 */
final class BoardTests {
	private BoardTests() {}

	private static final String[] L = {"BHTestL1", "BHTestL2", "BHTestL3"};

	private static HubConfig.Leaderboards lb() {
		return ConfigManager.config().leaderboards;
	}

	private static void stats(ModeStats s, int kills, int deaths, long coins) {
		s.played += 1;
		s.kills += kills;
		s.deaths += deaths;
		s.coins += coins;
	}

	private static String board(String player, int wall, int board) {
		return HubBoards.shown(p(player).getUUID(), wall, board);
	}

	private static boolean hasAll(String player) {
		return HubBoards.showing(p(player).getUUID()) && p(player).clientEntities().containsAll(HubBoards.entityIds());
	}

	static void all(MinecraftServer server, Script s) {
		String tag = "boards";
		SelfTest.reset(s, tag);
		s.run(tag + ": three players with stats", () -> {
			VARS.put("boardCycle", lb().cycleSeconds);
			HubBoards.rebuild();
			for (String n : L) {
				SelfTest.join(server, n);
			}
			PlayerProfile a = PlayerStore.get(p(L[0]));
			stats(a.overall, 30, 10, 0);
			stats(a.mode(GameMode.SWORD), 20, 5, 0);
			a.mode(GameMode.PARKOUR).played = 6;
			a.mode(GameMode.PARKOUR).wins = 4;
			PlayerProfile b = PlayerStore.get(p(L[1]));
			stats(b.overall, 55, 50, 0);
			stats(b.mode(GameMode.SWORD), 40, 30, 0);
			b.mode(GameMode.PARKOUR).played = 3;
			b.mode(GameMode.PARKOUR).wins = 1;
			PlayerProfile c = PlayerStore.get(p(L[2]));
			stats(c.overall, 3, 0, 0);
			c.mode(GameMode.PARKOUR).played = 2;
		});
		s.waitFor(tag + ": the three boards are on every player's client", 80, () -> {
			for (String n : L) {
				if (!hasAll(n)) return false;
			}
			return HubBoards.boardCount() == 3;
		});
		s.check(tag + ": kills and deaths hang above the north-west wall, kills on the west side, facing the fountain", () -> {
			if (!hangsWell(server, 0, 0, -1, -1) || !hangsWell(server, 0, 1, -1, -1)) return false;
			Vec3 kills = HubBoards.position(0, 0);
			Vec3 deaths = HubBoards.position(0, 1);
			return kills.x < deaths.x && kills.z > deaths.z;
		});
		s.check(tag + ": the parkour board hangs in the middle of the south-west wall, facing the fountain", () -> {
			Vec3 pk = HubBoards.position(1, 0);
			return hangsWell(server, 1, 0, -1, 1) && Math.abs(Math.abs(pk.x - 0.5) - Math.abs(pk.z - 0.5)) < 1;
		});
		s.waitFor(tag + ": total kills over every game", 60, () -> {
			String t = board(L[0], 0, 0);
			return t.contains(Lang.tr(p(L[0]), "boards.scope.all").getString())
					&& t.contains("#1 BHTestL2 - 55") && t.contains("#2 BHTestL1 - 30") && t.contains("#3 BHTestL3 - 3")
					&& !t.contains("●");
		});
		s.check(tag + ": total deaths; nobody with zero deaths", () -> {
			String t = board(L[0], 0, 1);
			return t.contains("#1 BHTestL2 - 50") && t.contains("#2 BHTestL1 - 10") && !t.contains("BHTestL3") && t.contains("#3 -");
		});
		s.check(tag + ": best parkour runner: most races won", () -> {
			String t = board(L[0], 1, 0);
			return t.contains(Lang.tr(p(L[0]), "boards.stat.wins.parkour").getString())
					&& t.contains("#1 BHTestL1 - 4") && t.contains("#2 BHTestL2 - 1") && !t.contains("BHTestL3");
		});
		s.check(tag + ": each player sees their own place at the bottom", () -> {
			String you = Lang.tr(p(L[2]), "boards.you", Component.literal("#3"), "3").getString();
			String none = Lang.tr(p(L[2]), "boards.you_none").getString();
			return board(L[2], 0, 0).contains(you) && board(L[2], 1, 0).contains(none)
					&& board(L[0], 0, 0).contains(Lang.tr(p(L[0]), "boards.you", Component.literal("#2"), "30").getString());
		});
		s.check(tag + ": a config from 1.6.0 gets the three boards; a changed one is kept", () -> {
			HubConfig old = new HubConfig();
			old.configVersion = 2;
			old.leaderboards.walls = HubConfig.wallsOf160();
			old.leaderboards.scale = 1.0;
			old.normalize();
			HubConfig changed = new HubConfig();
			changed.configVersion = 2;
			changed.leaderboards.walls = HubConfig.wallsOf160();
			changed.leaderboards.walls.get(0).stats = new ArrayList<>(List.of("kills"));
			changed.normalize();
			return old.leaderboards.walls.size() == 2 && old.leaderboards.walls.get(0).stats.equals(List.of("kills", "deaths"))
					&& old.leaderboards.walls.get(1).modes.equals(List.of("parkour")) && old.leaderboards.scale == 1.25
					&& changed.leaderboards.walls.get(1).ranked && changed.leaderboards.walls.get(0).stats.equals(List.of("kills"));
		});

		s.run(tag + ": the kills wall switches between all games and Sword", () -> {
			lb().walls.get(0).modes = new ArrayList<>(List.of("all", "sword"));
			lb().cycleSeconds = 3;
			HubBoards.rebuild();
		});
		s.waitFor(tag + ": Sword only: L3 (no sword kills) is gone", 200, () -> {
			String t = board(L[0], 0, 0);
			return t.contains(Lang.tr(p(L[0]), "mode.sword").getString()) && t.contains("#1 BHTestL2 - 40")
					&& t.contains("#2 BHTestL1 - 20") && !t.contains("BHTestL3") && t.contains("●");
		});
		s.waitFor(tag + ": and back to all games", 200, () -> board(L[0], 0, 0).contains("#1 BHTestL2 - 55"));

		s.run(tag + ": L1 leaves BattleHub", () -> Travel.leave(p(L[0])));
		s.waitFor(tag + ": L1's client dropped the boards", 60, () -> !HubWorld.is(p(L[0]).level())
				&& !HubBoards.showing(p(L[0]).getUUID())
				&& Collections.disjoint(p(L[0]).clientEntities(), HubBoards.entityIds()));
		s.run(tag + ": L1 comes back", () -> Travel.enter(p(L[0])));
		s.waitFor(tag + ": the boards are sent to L1 again", 80, () -> hasAll(L[0]) && !board(L[0], 0, 0).isEmpty());

		s.run(tag + ": boards switched off", () -> {
			VARS.put("boardIds", HubBoards.entityIds());
			lb().enabled = false;
			HubBoards.rebuild();
		});
		s.waitFor(tag + ": removed from the clients of players in the hub", 60, () -> {
			@SuppressWarnings("unchecked")
			Set<Integer> old = (Set<Integer>) VARS.get("boardIds");
			return HubBoards.boardCount() == 0 && !old.isEmpty() && Collections.disjoint(p(L[1]).clientEntities(), old)
					&& !HubBoards.showing(p(L[1]).getUUID());
		});
		s.run(tag + ": boards back on with the default modes; test stats cleared", () -> {
			lb().enabled = true;
			lb().walls.get(0).modes = new ArrayList<>(List.of("all"));
			lb().cycleSeconds = (int) VARS.get("boardCycle");
			HubBoards.rebuild();
			for (String n : L) {
				PlayerProfile pr = PlayerStore.get(p(n));
				pr.overall = new ModeStats();
				pr.modes.clear();
				pr.ranked.clear();
				pr.elo.clear();
				p(n).leave();
			}
		});
		s.waitFor(tag + ": boards are back", 60, () -> HubBoards.boardCount() == 3);
	}

	/** Above the wall in this quarter (signs of x and z from the fountain), in the air, turned towards the fountain. */
	private static boolean hangsWell(MinecraftServer server, int wall, int board, int sx, int sz) {
		Vec3 pos = HubBoards.position(wall, board);
		// The fountain is at 0.5, 0.5 (the lobby is built around 0,0)
		double dx = pos.x - 0.5;
		double dz = pos.z - 0.5;
		double r = Math.sqrt(dx * dx + dz * dz);
		if (Math.signum(dx) != sx || Math.signum(dz) != sz || r < 19 || r > 23) return false;
		if (pos.y <= HubWorld.floorY() + 2.5 || pos.y > HubWorld.floorY() + 4) return false;
		// Just above the slabs on the wall, so the block above the text's foot is air
		if (!HubWorld.level(server).getBlockState(BlockPos.containing(pos.add(0, 0.5, 0))).isAir()) return false;
		// The way the text faces (its yaw) points at the middle of the plaza
		double yaw = Math.toRadians(HubBoards.yaw(wall, board));
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double cx = 0.5 - pos.x;
		double cz = 0.5 - pos.z;
		return (fx * cx + fz * cz) / Math.sqrt(cx * cx + cz * cz) > 0.99;
	}
}
