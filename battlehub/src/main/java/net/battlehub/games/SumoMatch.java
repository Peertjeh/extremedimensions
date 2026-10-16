package net.battlehub.games;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.GameMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** Sumo: no damage (the kit gives Resistance V), only knockback. Fall off the platform and you are out. */
public final class SumoMatch extends PartyMatch {
	public SumoMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.SUMO, server, players);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return PartyLayouts.sumo(theme, random);
	}


	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		// Only punches from other players: they push, Resistance makes them harmless.
		return attacker != null && attacker != victim;
	}

	@Override
	protected void tickGame() {
		for (java.util.UUID id : alive) {
			ServerPlayer p = player(id);
			if (p != null && p.getHealth() < p.getMaxHealth()) {
				p.setHealth(p.getMaxHealth());
			}
		}
	}
}
