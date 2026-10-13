package net.battlehub.games;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.match.Kits;
import net.battlehub.match.Match;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;

/** Base for the party games: 2 to 16 players, a name like "Bow Spleef (8 players)" and optional respawning. */
public abstract class PartyMatch extends Match {
	/** Players waiting to respawn, with the run tick they come back. */
	protected final Map<UUID, Integer> respawnAt = new HashMap<>();

	protected PartyMatch(int id, GameMode mode, MinecraftServer server, List<ServerPlayer> players) {
		super(id, mode, false, server, players);
	}

	@Override
	public Component describe(ServerPlayer viewer) {
		return Lang.tr(viewer, "spectate.party", Lang.tr(viewer, Icons.modeKey(mode)), participants.size());
	}

	@Override
	protected boolean active(UUID id) {
		return alive.contains(id) && !respawnAt.containsKey(id);
	}

	protected int respawnTicks() {
		return Math.max(1, ConfigManager.config().timings.respawnDelaySeconds) * 20;
	}

	/** Out for a few seconds (as a spectator), then back at the spawn with a fresh kit. */
	protected void scheduleRespawn(ServerPlayer p) {
		respawnAt.put(p.getUUID(), runTicks + respawnTicks());
		p.setGameMode(GameType.SPECTATOR);
		teleport(p, arena.spectator());
		Msg.title(p, Lang.tr(p, "match.respawn_title"), Lang.tr(p, "match.respawn_sub", respawnTicks() / 20), 0, 30, 10);
	}

	protected void tickRespawns() {
		Iterator<Map.Entry<UUID, Integer>> it = respawnAt.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			if (runTicks < e.getValue()) {
				continue;
			}
			it.remove();
			ServerPlayer p = player(e.getKey());
			if (p == null || !alive.contains(e.getKey())) {
				continue;
			}
			respawn(p);
		}
	}

	protected void respawn(ServerPlayer p) {
		Arena.Point sp = spawnOf.getOrDefault(p.getUUID(), arena.spawn(0));
		p.setGameMode(GameType.ADVENTURE);
		teleport(p, sp);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.clearFire();
		Kits.give(p, kit(), leatherColor(p.getUUID()));
		Msg.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.2f);
		onRespawned(p);
	}

	protected void onRespawned(ServerPlayer p) {}

	@Override
	protected void onLeft(UUID id) {
		respawnAt.remove(id);
	}
}
