package net.battlehub.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Free for All: everyone against everyone with a kit. A death puts you back in after a short delay, at the spawn
 * farthest from the others, with a few seconds of spawn protection. The first to the kill target wins; when time
 * runs out, the most kills win.
 */
public final class FfaMatch extends PartyMatch {
	/** Run tick until which a player cannot be hurt (after spawning). */
	private final Map<UUID, Integer> protectedUntil = new HashMap<>();
	private UUID leader;
	/** The voted kit (sword, axe or mace), or null for the Free for All kit. */
	public final GameMode kitMode;

	public FfaMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		this(id, server, players, "");
	}

	public FfaMatch(int id, MinecraftServer server, List<ServerPlayer> players, String kitVote) {
		super(id, GameMode.FFA, server, players);
		GameMode k = GameMode.byId(kitVote);
		this.kitMode = k != null && k.isDuel() ? k : null;
	}

	@Override
	protected String kit() {
		return kitMode == null ? super.kit() : net.battlehub.config.ConfigManager.config().game(kitMode).kit;
	}

	private Component kitName(ServerPlayer p) {
		return Lang.tr(p, kitMode == null ? "ffa.kit_default" : net.battlehub.gui.Icons.modeKey(kitMode));
	}

	public int target() {
		return (int) Math.max(1, game().setting("killsToWin", 15));
	}

	private int protectionTicks() {
		return (int) Math.max(0, game().setting("spawnProtectionSeconds", 3) * 20);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return PartyLayouts.ffa(theme, random);
	}

	/** Spread over the spawn ring. */
	@Override
	protected Arena.Point spawnPoint(UUID id, int index) {
		int step = Math.max(1, arena.spawnCount() / Math.max(1, participants.size()));
		return arena.spawn((index * step) % arena.spawnCount());
	}

	@Override
	protected void onGo() {
		forEveryone(p -> Msg.send(p, "ffa.start", target(), kitName(p)));
	}

	public boolean isProtected(UUID id) {
		return protectedUntil.getOrDefault(id, -1) > runTicks;
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		if (isProtected(victim.getUUID())) {
			return false;
		}
		if (attacker != null) {
			// Attacking ends your own spawn protection
			protectedUntil.remove(attacker.getUUID());
		}
		return true;
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		scheduleRespawn(victim);
		if (killer == null) {
			return;
		}
		int k = kills(killer.getUUID());
		if (k >= target()) {
			finish(List.of(killer.getUUID()));
		} else if (k == target() - 1) {
			forEveryone(p -> Msg.send(p, "ffa.one_more", name(killer.getUUID())));
		}
	}

	/** Back at the spawn point farthest from everybody in play. */
	@Override
	protected void respawn(ServerPlayer p) {
		Arena.Point best = null;
		double bestDist = -1;
		for (int i = 0; i < arena.spawnCount(); i++) {
			Arena.Point sp = arena.spawn(i);
			double nearest = Double.MAX_VALUE;
			for (UUID id : alive) {
				ServerPlayer o = player(id);
				if (o == null || o == p || !active(id)) continue;
				nearest = Math.min(nearest, o.position().distanceToSqr(sp.pos()));
			}
			if (nearest > bestDist) {
				bestDist = nearest;
				best = sp;
			}
		}
		if (best != null) {
			spawnOf.put(p.getUUID(), best);
		}
		super.respawn(p);
	}

	@Override
	protected void onRespawned(ServerPlayer p) {
		protectedUntil.put(p.getUUID(), runTicks + protectionTicks());
	}

	@Override
	protected void checkEnd() {
		// Everybody else left
		if (alive.size() <= 1) {
			finish(new ArrayList<>(alive));
		}
	}

	/** Most kills win (all of them on a tie); nobody scored: a draw. */
	@Override
	protected void onTimeUp() {
		int best = 0;
		List<UUID> top = new ArrayList<>();
		for (UUID id : alive) {
			int k = kills(id);
			if (k > best) {
				best = k;
				top.clear();
				top.add(id);
			} else if (k == best && k > 0) {
				top.add(id);
			}
		}
		finish(best == 0 ? List.of() : top);
	}

	/** The player with the most kills (first to get there on a tie), or null. */
	public UUID leader() {
		UUID best = null;
		for (UUID id : alive) {
			if (best == null || kills(id) > kills(best) || (kills(id) == kills(best) && id.equals(leader))) {
				best = id;
			}
		}
		return best == null || kills(best) == 0 ? null : best;
	}

	@Override
	protected void tickGame() {
		tickRespawns();
		if (runTicks % 10 != 0) {
			return;
		}
		UUID now = leader();
		if (now != null && !now.equals(leader)) {
			UUID l = now;
			forEveryone(p -> Msg.send(p, "ffa.new_leader", name(l), kills(l)));
			for (UUID id : alive) {
				ServerPlayer p = player(id);
				if (p != null && id.equals(l)) Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.8f);
			}
		}
		leader = now;
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null || respawnAt.containsKey(id)) continue;
			if (isProtected(id)) {
				Msg.actionBar(p, "ffa.protected");
			} else {
				Msg.actionBar(p, "ffa.actionbar", kills(id), target(), leader == null ? Lang.tr(p, "ffa.nobody") : name(leader),
						leader == null ? 0 : kills(leader));
			}
		}
	}

	@Override
	public Component status(ServerPlayer viewer) {
		UUID l = leader();
		return l == null ? Lang.tr(viewer, "ffa.status_none", alive.size()) : Lang.tr(viewer, "ffa.status", name(l), kills(l), target());
	}
}
