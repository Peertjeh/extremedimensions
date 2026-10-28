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
	static final String A = "BHTestA";
	/** Whether the last Travel.enter of the reset test was accepted. */
	private static final boolean[] VARS_ENTERED = new boolean[1];
	private static final int[] WAITED = new int[1];

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

	/**
	 * /battlehub world reset: A, in the lobby, is sent back to the normal world; /hub is refused while it runs; then
	 * the lobby stands again (the plaza's beacons) and A can come in.
	 */
	static void reset(MinecraftServer server, Script s) {
		String tag = "world reset";
		SelfTest.reset(s, tag);
		s.run(tag + ": A is in the lobby; an admin resets BattleHub's world", () -> {
			SelfTest.join(server, A);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "battlehub world reset");
		});
		s.check(tag + ": A is back in the normal world and the reset runs", () -> HubWorld.resetting()
				&& !HubWorld.isBattleHub(p(A).level()));
		s.check(tag + ": /hub waits until it is done", () -> !Travel.enter(p(A)) && !HubWorld.isBattleHub(p(A).level()));
		s.waitFor(tag + ": the lobby is built again", 6000, () -> {
			keepSafe(server);
			return !HubWorld.resetting() && HubWorld.ready();
		});
		s.check(tag + ": the plaza stands, beacons shining at its rim", () -> {
			net.minecraft.server.level.ServerLevel hub = HubWorld.level(server);
			int y = HubWorld.floorY(), r = HubWorld.plazaRadius();
			return hub.getBlockState(new BlockPos(0, y + 15, 0)).is(net.minecraft.world.level.block.Blocks.BEACON)
					&& hub.getBlockState(new BlockPos(0, y + 13, -r)).is(net.minecraft.world.level.block.Blocks.BEACON);
		});
		// the change of dimension can land a tick or more after the call: enter, then wait for A to arrive
		s.run(tag + ": A goes in again", () -> {
			VARS_ENTERED[0] = Travel.enter(p(A));
			WAITED[0] = 0;
		});
		s.waitFor(tag + ": and A comes in again", 200, () -> {
			keepSafe(server);
			boolean before = Travel.inBattleHub(p(A));
			boolean entered = VARS_ENTERED[0];
			boolean in = entered && HubWorld.isBattleHub(p(A).level());
			if (!in && ++WAITED[0] % 50 == 0) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST world reset: A was in BattleHub {}, entered {}, now in {}, resetting {}, ready {}, session {}, match {}",
						before, entered, p(A).level().dimension().identifier(), HubWorld.resetting(), HubWorld.ready(), Sessions.state(p(A).getUUID()),
						net.battlehub.match.Matches.of(p(A).getUUID()) == null ? "none" : net.battlehub.match.Matches.of(p(A).getUUID()).mode.id());
			}
			return in;
		});
	}

	/**
	 * The test players wait in the normal world while the lobby is built again: standing still in the open, at night the
	 * monsters there killed them (a dead player cannot come back in), which is no part of what is tested.
	 */
	private static void keepSafe(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (HubWorld.isBattleHub(p.level()) || !p.isAlive() || !p.getGameProfile().name().startsWith("BHTest")) continue;
			p.setHealth(p.getMaxHealth());
			for (net.minecraft.world.entity.Entity e : p.level().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(24),
					e -> e.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER)) {
				e.discard();
			}
		}
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
		chat(server, s);
		invites(server, s);
		s.run(tag + ": S logs off", () -> p(S).leave());
	}

	/**
	 * Invitations (1.51): A waits alone in the Sumo lobby; S in the normal world gets a line with a [Join] button and A
	 * does not; the button's command takes S to BattleHub and into that lobby.
	 */
	private static void invites(MinecraftServer server, Script s) {
		String tag = "invites";
		s.run(tag + ": A waits alone in the Sumo lobby", () -> {
			ConfigManager.config().hub.inviteAfterSeconds = 1;
			net.battlehub.hub.Invites.clear();
			net.battlehub.queue.Parties.join(p(A), GameMode.SUMO);
		});
		s.waitFor(tag + ": S in the normal world is invited, with the button's command", 100,
				() -> p(S).clientSystemHas("/battlehub join sumo"));
		s.check(tag + ": A, in BattleHub already, is not", () -> !p(A).clientSystemHas("/battlehub join sumo"));
		s.run(tag + ": S clicks [Join]", () -> server.getCommands().performPrefixedCommand(p(S).createCommandSourceStack(),
				"battlehub join sumo"));
		s.waitFor(tag + ": S is in BattleHub, in the Sumo lobby", 200, () -> Travel.inBattleHub(p(S))
				&& net.battlehub.queue.Parties.members(GameMode.SUMO).contains(p(S).getUUID()));
		s.run(tag + ": both leave the lobby, S back to the normal world", () -> {
			Queues.leaveAll(p(A));
			Queues.leaveAll(p(S));
			Travel.leave(p(S));
			ConfigManager.config().hub.inviteAfterSeconds = 30;
		});
		s.waitFor(tag + ": S outside again", 200, () -> !HubWorld.isBattleHub(p(S).level()));
	}

	/**
	 * Chat stays in its world (1.50): A in the lobby and S in the normal world each say something, and only the player
	 * in the same world reads it. With {@code hub.separateChat} off there is one chat for everyone again, and a message
	 * starting with {@code hub.globalChatPrefix} reaches everyone anyway (1.51).
	 */
	private static void chat(MinecraftServer server, Script s) {
		String tag = "chat";
		s.run(tag + ": A comes into the lobby, S stays in the normal world", () -> SelfTest.join(server, A));
		s.waitFor(tag + ": A in BattleHub, S outside", 200, () -> Travel.inBattleHub(p(A)) && !HubWorld.isBattleHub(p(S).level()));
		s.run(tag + ": A and S each say something", () -> {
			say(server, p(A), "chat-from-the-hub");
			say(server, p(S), "chat-from-survival");
		});
		s.waitFor(tag + ": each reads what was said in their own world", 20, () -> p(A).clientChatHas("chat-from-the-hub")
				&& p(S).clientChatHas("chat-from-survival"));
		s.check(tag + ": and nothing from the other world", () -> !p(A).clientChatHas("chat-from-survival")
				&& !p(S).clientChatHas("chat-from-the-hub"));
		s.run(tag + ": with hub.separateChat off, A says something", () -> {
			ConfigManager.config().hub.separateChat = false;
			say(server, p(A), "chat-for-everyone");
		});
		s.waitFor(tag + ": S in the normal world reads it too", 20, () -> p(S).clientChatHas("chat-for-everyone")
				&& p(A).clientChatHas("chat-for-everyone"));
		s.run(tag + ": hub.separateChat back on", () -> ConfigManager.config().hub.separateChat = true);
		s.run(tag + ": S starts a message with the global prefix", () -> say(server, p(S), "!chat-across-worlds"));
		s.waitFor(tag + ": A in the lobby reads it (1.51)", 20, () -> p(A).clientChatHas("!chat-across-worlds"));
	}

	/** A player says something in chat, as /me (a command run for the player goes out as their chat message). */
	private static void say(MinecraftServer server, ServerPlayer p, String text) {
		server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "me " + text);
	}
}
