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
 * The leaderboards above the lobby wall: where they hang, who is on them in which order, the player's own line,
 * switching modes, and the boards coming back after a player left the hub world.
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
		s.run(tag + ": one mode per wall, three players with stats", () -> {
			VARS.put("boardModes0", new ArrayList<>(lb().walls.get(0).modes));
			VARS.put("boardModes1", new ArrayList<>(lb().walls.get(1).modes));
			VARS.put("boardCycle", lb().cycleSeconds);
			lb().walls.get(0).modes = new ArrayList<>(List.of("pvp"));
			lb().walls.get(1).modes = new ArrayList<>(List.of("all"));
			HubBoards.rebuild();
			for (String n : L) {
				SelfTest.join(server, n);
			}
			PlayerProfile a = PlayerStore.get(p(L[0]));
			stats(a.mode(GameMode.SWORD), 30, 10, 500);
			stats(a.rankedStats(GameMode.SWORD), 12, 4, 200);
			a.setElo(GameMode.SWORD, 1300);
			PlayerProfile b = PlayerStore.get(p(L[1]));
			stats(b.mode(GameMode.SWORD), 50, 50, 300);
			stats(b.mode(GameMode.FFA), 5, 0, 20);
			// Not a PvP mode: must not count on the PvP wall
			stats(b.mode(GameMode.BED_WARS), 99, 0, 0);
			b.setElo(GameMode.AXE, 1100);
			PlayerProfile c = PlayerStore.get(p(L[2]));
			stats(c.mode(GameMode.AXE), 3, 0, 10);
		});
		s.waitFor(tag + ": all nine boards are on every player's client", 80, () -> {
			for (String n : L) {
				if (!hasAll(n)) return false;
			}
			return HubBoards.boardCount() == 9;
		});
		s.check(tag + ": PvP boards hang above the north-west wall, kills on the west end, facing the fountain", () -> {
			for (int i = 0; i < 4; i++) {
				if (!hangsWell(server, 0, i, -1, -1)) return false;
			}
			Vec3 kills = HubBoards.position(0, 0);
			Vec3 coins = HubBoards.position(0, 3);
			return kills.x < coins.x && kills.z > coins.z;
		});
		s.check(tag + ": Ranked boards hang above the south-west wall, facing the fountain", () -> {
			for (int i = 0; i < 5; i++) {
				if (!hangsWell(server, 1, i, -1, 1)) return false;
			}
			return true;
		});
		s.waitFor(tag + ": kills over all PvP modes (Bed Wars not counted)", 60, () -> {
			String t = board(L[0], 0, 0);
			return t.contains("#1 BHTestL2 - 55") && t.contains("#2 BHTestL1 - 30") && t.contains("#3 BHTestL3 - 3");
		});
		s.check(tag + ": deaths; nobody with zero deaths", () -> {
			String t = board(L[0], 0, 1);
			return t.contains("#1 BHTestL2 - 50") && t.contains("#2 BHTestL1 - 10") && !t.contains("BHTestL3") && t.contains("#3 -");
		});
		s.check(tag + ": K/D with two decimals; fewer than 10 kills is left out", () -> {
			String t = board(L[0], 0, 2);
			return t.contains("#1 BHTestL1 - 3.00") && t.contains("#2 BHTestL2 - 1.10") && !t.contains("BHTestL3");
		});
		s.check(tag + ": coins earned", () -> {
			String t = board(L[0], 0, 3);
			return t.contains("#1 BHTestL1 - 500") && t.contains("#2 BHTestL2 - 320") && t.contains("#3 BHTestL3 - 10");
		});
		s.check(tag + ": each player sees their own place at the bottom", () -> {
			String you = Lang.tr(p(L[2]), "boards.you", Component.literal("#3"), "3").getString();
			String none = Lang.tr(p(L[2]), "boards.you_none").getString();
			return board(L[2], 0, 0).contains(you) && board(L[2], 0, 2).contains(none)
					&& board(L[0], 0, 0).contains(Lang.tr(p(L[0]), "boards.you", Component.literal("#2"), "30").getString());
		});
		s.check(tag + ": ranked ELO: best rating, only players who played ranked", () -> {
			String t = board(L[0], 1, 0);
			return t.contains("#1 BHTestL1 - 1,300") && t.contains("#2 BHTestL2 - 1,100") && !t.contains("BHTestL3");
		});
		s.check(tag + ": ranked kills and coins count ranked matches only", () -> {
			String kills = board(L[0], 1, 1);
			String coins = board(L[0], 1, 4);
			return kills.contains("#1 BHTestL1 - 12") && !kills.contains("BHTestL2") && coins.contains("#1 BHTestL1 - 200");
		});

		s.run(tag + ": the PvP wall switches between all PvP and Sword", () -> {
			lb().walls.get(0).modes = new ArrayList<>(List.of("pvp", "sword"));
			lb().cycleSeconds = 3;
			HubBoards.rebuild();
		});
		s.waitFor(tag + ": Sword only: L3 (axe) is gone", 200, () -> {
			String t = board(L[0], 0, 0);
			return t.contains(Lang.tr(p(L[0]), "mode.sword").getString()) && t.contains("#1 BHTestL2 - 50")
					&& t.contains("#2 BHTestL1 - 30") && !t.contains("BHTestL3");
		});
		s.waitFor(tag + ": and back to all PvP modes", 200, () -> board(L[0], 0, 0).contains("#1 BHTestL2 - 55"));

		s.run(tag + ": L1 leaves BattleHub", () -> {
			VARS.put("boardIds", HubBoards.entityIds());
			Travel.leave(p(L[0]));
		});
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
			@SuppressWarnings("unchecked")
			List<String> m0 = (List<String>) VARS.get("boardModes0");
			@SuppressWarnings("unchecked")
			List<String> m1 = (List<String>) VARS.get("boardModes1");
			lb().walls.get(0).modes = m0;
			lb().walls.get(1).modes = m1;
			lb().cycleSeconds = (int) VARS.get("boardCycle");
			HubBoards.rebuild();
			for (String n : L) {
				PlayerProfile pr = PlayerStore.get(p(n));
				pr.modes.clear();
				pr.ranked.clear();
				pr.elo.clear();
				p(n).leave();
			}
		});
		s.waitFor(tag + ": boards are back", 60, () -> HubBoards.boardCount() == 9);
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
