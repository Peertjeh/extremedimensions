package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.Set;

import net.battlehub.command.HubCommands;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.HubWorld;
import net.battlehub.hub.Travel;
import net.battlehub.lobby.HubItems;
import net.battlehub.progression.Ranks;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** BattleHub's own world: the normal world is left alone, and going in and out keeps everything a player owns. */
final class HubWorldTests {
	private HubWorldTests() {}

	static final String S = "BHTestS";

	private static boolean hasHubItem(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (Icon.isHubItem(inv.getItem(i))) return true;
		}
		return false;
	}

	private static int diamonds(ServerPlayer p) {
		return p.getInventory().countItem(Items.DIAMOND);
	}

	static void all(MinecraftServer server, Script s) {
		String tag = "hub world";
		s.waitFor(tag + ": the lobby is built", 200, () -> {
			if (!HubWorld.ready()) return false;
			Vec3 spawn = HubWorld.defaultSpawn();
			return !HubWorld.level(server).getBlockState(BlockPos.containing(spawn.x, spawn.y - 1, spawn.z)).isAir();
		});
		s.check(tag + ": the plaza: four open gateways with bridges to the four buildings, beacons shining", () -> {
			net.minecraft.server.level.ServerLevel hub = HubWorld.level(server);
			int y = HubWorld.floorY();
			int r = HubWorld.plazaRadius();
			boolean ok = hub.getBlockState(new BlockPos(0, y + 15, 0)).is(net.minecraft.world.level.block.Blocks.BEACON);
			int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
			for (int[] d : dirs) {
				BlockPos gate = new BlockPos(d[0] * r, y, d[1] * r);
				ok &= !hub.getBlockState(gate).isAir() && hub.getBlockState(gate.above()).isAir() && hub.getBlockState(gate.above(2)).isAir();
				ok &= !hub.getBlockState(new BlockPos(d[0] * (r + 12), y, d[1] * (r + 12))).isAir();
				ok &= hub.getBlockState(new BlockPos(d[0] * r, y + 13, d[1] * r)).is(net.minecraft.world.level.block.Blocks.BEACON);
			}
			return ok;
		});
		s.check(tag + ": where the lobby characters and the dummy stand there is room", () -> {
			net.minecraft.server.level.ServerLevel hub = HubWorld.level(server);
			var cfg = ConfigManager.config();
			java.util.List<Double> angles = new java.util.ArrayList<>();
			java.util.List<Double> radii = new java.util.ArrayList<>();
			for (var n : cfg.npcs.list) {
				angles.add(n.angle);
				radii.add(cfg.npcs.radius);
			}
			angles.add(cfg.lobbyExtras.dummyAngle);
			radii.add(cfg.lobbyExtras.dummyRadius);
			for (int i = 0; i < angles.size(); i++) {
				double a = Math.toRadians(angles.get(i));
				BlockPos at = BlockPos.containing(0.5 + Math.cos(a) * radii.get(i), HubWorld.floorY() + 1, 0.5 + Math.sin(a) * radii.get(i));
				if (!hub.getBlockState(at).isAir() || !hub.getBlockState(at.above()).isAir() || hub.getBlockState(at.below()).isAir()) {
					net.battlehub.BattleHub.LOGGER.warn("SELFTEST hub: no room at {} ({})", at, hub.getBlockState(at));
					return false;
				}
			}
			return true;
		});
		s.run(tag + ": S plays survival in the normal world", () -> {
			ServerPlayer pl = SelfTest.joinOutside(server, S);
			pl.setGameMode(GameType.SURVIVAL);
			pl.getInventory().clearContent();
			pl.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 5));
			pl.setExperienceLevels(7);
			// On the ground: simulated players fall like real ones
			server.overworld().getChunk(0, -1);
			int y = server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 12, -8);
			pl.teleportTo(server.overworld(), 12.5, y, -7.5, Set.of(), 90, 0, false);
			VARS.put("sPos", pl.position());
		});
		s.pause(25);
		s.check(tag + ": the normal world is left alone (no compass, things kept)", () -> {
			ServerPlayer pl = p(S);
			return !HubWorld.isBattleHub(pl.level()) && !hasHubItem(pl) && diamonds(pl) == 5 && pl.experienceLevel == 7
					&& !Travel.hasSurvivalState(pl.getUUID());
		});
		s.check(tag + ": damage works normally there", () -> {
			ServerPlayer pl = p(S);
			float before = pl.getHealth();
			pl.hurtServer(pl.level(), pl.damageSources().generic(), 2f);
			boolean hurt = pl.getHealth() < before;
			pl.setHealth(pl.getMaxHealth());
			return hurt;
		});
		s.run(tag + ": S types /hub", () -> HubCommands.hub(p(S)));
		s.check(tag + ": in the hub with the compass, survival things put away", () -> {
			ServerPlayer pl = p(S);
			int slot = ConfigManager.config().compass.slot;
			return HubWorld.is(pl.level()) && HubItems.COMPASS.equals(Icon.tagOf(pl.getInventory().getItem(slot)))
					&& HubItems.EXIT.equals(Icon.tagOf(pl.getInventory().getItem(HubItems.LEAVE_SLOT)))
					&& diamonds(pl) == 0 && Travel.hasSurvivalState(pl.getUUID()) && pl.gameMode() == GameType.ADVENTURE
					&& Sessions.isFree(pl.getUUID());
		});
		s.check(tag + ": the XP bar shows the BattleHub level", () ->
				p(S).experienceLevel == Ranks.curve().progress(PlayerStore.get(p(S)).xp).level());
		s.run(tag + ": S types /survival", () -> Travel.leave(p(S)));
		s.check(tag + ": back where S was, with everything", () -> {
			ServerPlayer pl = p(S);
			Vec3 was = (Vec3) VARS.get("sPos");
			double flat = Math.hypot(pl.getX() - was.x, pl.getZ() - was.z);
			boolean ok = !HubWorld.isBattleHub(pl.level()) && flat < 1.0 && Math.abs(pl.getY() - was.y) < 3 && diamonds(pl) == 5
					&& pl.experienceLevel == 7 && !hasHubItem(pl) && pl.gameMode() == GameType.SURVIVAL
					&& !Travel.hasSurvivalState(pl.getUUID());
			if (!ok) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST S after /survival: level {} pos {} (was {}) diamonds {} xp {} hubItem {} mode {} saved {}",
						pl.level().dimension().identifier(), pl.position(), was, diamonds(pl), pl.experienceLevel, hasHubItem(pl),
						pl.gameMode().getName(), Travel.hasSurvivalState(pl.getUUID()));
			}
			return ok;
		});
		s.run(tag + ": an admin teleports S into the hub world", () -> {
			ServerPlayer pl = p(S);
			Vec3 spawn = HubWorld.defaultSpawn();
			pl.teleportTo(HubWorld.level(server), spawn.x + 3, spawn.y, spawn.z, Set.of(), 0, 0, false);
		});
		s.waitFor(tag + ": checked in automatically", 30, () -> Travel.hasSurvivalState(p(S).getUUID())
				&& diamonds(p(S)) == 0 && HubItems.COMPASS.equals(Icon.tagOf(p(S).getInventory().getItem(ConfigManager.config().compass.slot))));
		s.run(tag + ": and back out to the normal world", () -> {
			ServerPlayer pl = p(S);
			pl.teleportTo(server.overworld(), 20.5, pl.getY(), 20.5, Set.of(), 0, 0, false);
		});
		s.waitFor(tag + ": checked out automatically, things back", 30, () -> !Travel.hasSurvivalState(p(S).getUUID())
				&& diamonds(p(S)) == 5 && !hasHubItem(p(S)) && p(S).gameMode() == GameType.SURVIVAL);
		s.run(tag + ": S queues from the normal world", () -> Queues.join(p(S), GameMode.SWORD, false));
		s.check(tag + ": taken to the hub and queued", () -> HubWorld.is(p(S).level())
				&& Sessions.state(p(S).getUUID()) == Sessions.State.QUEUED && diamonds(p(S)) == 0);
		s.run(tag + ": S leaves BattleHub with the door", () -> Travel.leave(p(S)));
		s.check(tag + ": queue left, things back", () -> !HubWorld.isBattleHub(p(S).level())
				&& Sessions.state(p(S).getUUID()) != Sessions.State.QUEUED && diamonds(p(S)) == 5);
		s.run(tag + ": S logs off", () -> p(S).leave());
	}
}
