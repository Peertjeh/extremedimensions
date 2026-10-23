package net.battlehub.hub;

import java.util.Set;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.LevelCurve;
import net.battlehub.data.PlayerStore;
import net.battlehub.data.StateStore;
import net.battlehub.lobby.HubItems;
import net.battlehub.lobby.Lobby;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.Ranks;
import net.battlehub.progression.TitleService;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.spectate.Spectators;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * Going into BattleHub and back out. On the way in the survival state (inventory, XP, health, hunger, effects,
 * game mode and position) is saved in {@code <world>/battlehub/survival/}; on the way out it is given back, so
 * BattleHub never touches what players own in the normal world.
 */
public final class Travel {
	private Travel() {}

	private static final StateStore SURVIVAL = new StateStore("survival");

	public static void init(MinecraftServer server) {
		SURVIVAL.init(server);
	}

	/** The player is in BattleHub and their survival state is waiting for them. */
	public static boolean hasSurvivalState(UUID id) {
		return SURVIVAL.has(id);
	}

	public static boolean inBattleHub(ServerPlayer p) {
		return HubWorld.isBattleHub(p.level());
	}

	/** From the normal world into the BattleHub lobby. */
	public static boolean enter(ServerPlayer p) {
		if (inBattleHub(p)) {
			return false;
		}
		MinecraftServer server = p.level().getServer();
		if (server.getLevel(HubWorld.KEY) == null) {
			Msg.send(p, "hub.missing");
			return false;
		}
		if (HubWorld.resetting()) {
			Msg.send(p, "hub.resetting");
			return false;
		}
		if (p.isSleeping()) {
			p.stopSleeping();
		}
		p.stopRiding();
		HubItems.strip(p);
		SURVIVAL.take(p, false);
		resetForHub(p);
		if (!Lobby.teleport(p)) {
			net.battlehub.BattleHub.LOGGER.warn("BattleHub: {} could not be sent into the lobby yet; trying again", p.getGameProfile().name());
			retryLobby(p, 100);
		}
		Sessions.toLobby(p.getUUID());
		HubItems.ensure(p, false);
		showLevel(p);
		TitleService.apply(p);
		Msg.send(p, "hub.welcome");
		net.battlehub.progression.FeaturedMode.announce(p);
		net.battlehub.progression.Retention.login(p);
		net.battlehub.social.Friends.entered(p);
		net.battlehub.fx.ResourcePack.offer(p);
		Msg.sound(p, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
		return true;
	}

	/**
	 * The server can refuse a change of dimension right after another one (a reset of BattleHub's world had just
	 * sent the player out): try again every tick for up to five seconds, as long as the player still means to be in the lobby.
	 */
	private static void retryLobby(ServerPlayer p, int tries) {
		net.battlehub.util.Tasks.next(() -> {
			if (p.isRemoved() || inBattleHub(p) || Sessions.state(p.getUUID()) != Sessions.State.LOBBY) return;
			if (Lobby.teleport(p)) {
				net.battlehub.BattleHub.LOGGER.info("BattleHub: {} is in the lobby now", p.getGameProfile().name());
			} else if (tries > 1) {
				retryLobby(p, tries - 1);
			} else {
				net.battlehub.BattleHub.LOGGER.warn("BattleHub: {} still could not be sent into the lobby", p.getGameProfile().name());
			}
		});
	}

	/** Back to the normal world, with everything the player had there. */
	public static boolean leave(ServerPlayer p) {
		if (!inBattleHub(p)) {
			Msg.send(p, "hub.not_inside");
			return false;
		}
		leaveGames(p, true);
		net.battlehub.fx.Sounds.stopMusic(p);
		net.battlehub.lobby.DoubleJump.reset(p);
		if (SURVIVAL.has(p.getUUID())) {
			SURVIVAL.restore(p, StateStore.Restore.FULL, Travel::toWorldSpawn, level -> !HubWorld.isBattleHub(level));
		} else {
			// Nothing saved (they never came in through /hub): start clean at the world spawn.
			p.closeContainer();
			p.getInventory().clearContent();
			p.setGameMode(GameType.SURVIVAL);
			toWorldSpawn(p);
		}
		HubItems.strip(p);
		Sessions.toLobby(p.getUUID());
		TitleService.apply(p);
		Msg.send(p, "hub.left");
		return true;
	}

	/** Takes the player to the hub first when they start something from the normal world. */
	public static boolean ensureInHub(ServerPlayer p) {
		if (inBattleHub(p)) {
			return true;
		}
		return enter(p);
	}

	/**
	 * Twice a second for every player: notices players who went into or out of BattleHub some other way (an admin
	 * teleport, a command) and saves or gives back their survival state as if they had used /hub or /survival.
	 */
	public static void guard(ServerPlayer p) {
		boolean inHubWorld = HubWorld.is(p.level());
		boolean inside = inBattleHub(p);
		boolean saved = SURVIVAL.has(p.getUUID());
		if (inHubWorld && !saved && Sessions.inLobby(p.getUUID())) {
			HubItems.strip(p);
			SURVIVAL.take(p, false);
			MinecraftServer server = p.level().getServer();
			LevelData.RespawnData spawn = server.getRespawnData();
			ServerLevel level = server.getLevel(spawn.dimension());
			if (level != null) {
				SURVIVAL.setPosition(p, level, spawnPosition(level, spawn.pos()), spawn.yaw(), 0);
			}
			resetForHub(p);
			HubItems.ensure(p, false);
			TitleService.apply(p);
		} else if (!inside && saved) {
			leaveGames(p, false);
			SURVIVAL.restore(p, StateStore.Restore.KEEP_POSITION, Travel::toWorldSpawn, level -> true);
			HubItems.strip(p);
			Sessions.toLobby(p.getUUID());
			TitleService.apply(p);
		}
	}

	/**
	 * Ends spectating, matches and queues. {@code inside}: the player is still in BattleHub and is sent back to
	 * the lobby first; otherwise they were moved out already and the lobby state from before the match is dropped
	 * (their survival state is what they get back).
	 */
	private static void leaveGames(ServerPlayer p, boolean inside) {
		if (Sessions.state(p.getUUID()) == Sessions.State.SPECTATING) {
			Spectators.leave(p, inside);
		}
		Match m = Matches.of(p.getUUID());
		if (m != null) {
			if (inside) {
				m.forfeit(p);
			} else {
				m.quit(p);
			}
		}
		if (!inside) {
			net.battlehub.data.Snapshots.discard(p.getUUID());
		}
		Queues.leaveAll(p);
	}

	private static GameType hubGameMode() {
		GameType t = GameType.byName(ConfigManager.config().hub.gameMode, GameType.ADVENTURE);
		return t == GameType.SURVIVAL ? GameType.SURVIVAL : GameType.ADVENTURE;
	}

	private static void resetForHub(ServerPlayer p) {
		p.closeContainer();
		p.getInventory().clearContent();
		p.removeAllEffects();
		p.setGameMode(hubGameMode());
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(5f);
		p.setAbsorptionAmount(0);
		p.clearFire();
		p.resetFallDistance();
		p.setArrowCount(0);
		StateStore.setXp(p, 0, 0, 0);
	}

	/** In the hub the XP bar shows the BattleHub level and the progress to the next one. */
	public static void showLevel(ServerPlayer p) {
		if (!ConfigManager.config().hub.levelOnXpBar || !inBattleHub(p)) {
			return;
		}
		LevelCurve.Progress pr = Ranks.curve().progress(PlayerStore.get(p).xp);
		float fraction = pr.maxed() ? 1f : (float) pr.fraction();
		if (p.experienceLevel != pr.level() || Math.abs(p.experienceProgress - fraction) > 0.001f) {
			StateStore.setXp(p, pr.level(), fraction, 0);
		}
	}

	private static void toWorldSpawn(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		LevelData.RespawnData spawn = server.getRespawnData();
		ServerLevel level = server.getLevel(spawn.dimension());
		if (level == null) {
			level = server.overworld();
		}
		Vec3 pos = spawnPosition(level, spawn.pos());
		p.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), spawn.yaw(), 0, true);
	}

	private static Vec3 spawnPosition(ServerLevel level, BlockPos pos) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
		return Vec3.atBottomCenterOf(top);
	}
}
