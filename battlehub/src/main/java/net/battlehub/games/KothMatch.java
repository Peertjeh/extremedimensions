package net.battlehub.games;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.GameMode;
import net.battlehub.core.KothScore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * King of the Hill: stand alone on the 5x5 top of the hill to score a point per second. First to the target, or
 * the most points when time runs out, wins. Deaths respawn after a short delay.
 */
public final class KothMatch extends PartyMatch {
	private KothScore score;
	private UUID lastKing;

	public KothMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.KOTH, server, players);
	}

	private int target() {
		return (int) Math.max(1, game().setting("targetPoints", 90));
	}

	@Override
	protected Blueprint design(Theme theme) {
		score = new KothScore((int) Math.max(1, game().setting("pointsPerSecond", 1)), false);
		return PartyLayouts.koth(theme, random);
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		scheduleRespawn(victim);
	}

	@Override
	protected void checkEnd() {
		if (alive.size() <= 1) {
			finish(new ArrayList<>(alive));
		}
	}

	@Override
	public net.minecraft.world.phys.Vec3 botObjective(ServerPlayer bot) {
		BlockPos z = arena.mark("zone");
		return new net.minecraft.world.phys.Vec3(z.getX() + 0.5, z.getY(), z.getZ() + 0.5);
	}

	public boolean inZone(ServerPlayer p) {
		BlockPos z = arena.mark("zone");
		return Math.abs(p.getX() - (z.getX() + 0.5)) <= 2.6 && Math.abs(p.getZ() - (z.getZ() + 0.5)) <= 2.6
				&& p.getY() >= z.getY() - 0.6 && p.getY() <= z.getY() + 3;
	}

	@Override
	protected void tickGame() {
		tickRespawns();
		if (runTicks % 20 != 0) {
			return;
		}
		List<UUID> inZone = new ArrayList<>();
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p != null && active(id) && inZone(p)) {
				inZone.add(id);
			}
		}
		List<UUID> scored = score.second(inZone);
		UUID king = scored.isEmpty() ? null : scored.get(0);
		if (king != null && !king.equals(lastKing)) {
			UUID k = king;
			forEveryone(p -> Msg.send(p, "koth.new_king", name(k)));
		}
		lastKing = king;
		BlockPos z = arena.mark("zone");
		arena.level().sendParticles(inZone.size() > 1 ? ParticleTypes.ANGRY_VILLAGER : ParticleTypes.HAPPY_VILLAGER,
				z.getX() + 0.5, z.getY() + 0.3, z.getZ() + 0.5, 12, 2.2, 0.1, 2.2, 0);
		UUID leader = score.leader();
		Component leaderName = leader == null ? Component.literal("-") : Component.literal(name(leader));
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p == null || left.contains(id)) continue;
			String key = inZone.size() > 1 && inZone.contains(id) ? "koth.actionbar_contested" : "koth.actionbar";
			Msg.actionBar(p, key, leaderName, leader == null ? 0 : score.points(leader), target(), score.points(id));
			if (id.equals(king)) {
				Msg.sound(p, SoundEvents.NOTE_BLOCK_HAT, 0.4f, 1.8f);
			}
		}
		forWatchers(p -> Msg.actionBar(p, "koth.actionbar_watch", leaderName, leader == null ? 0 : score.points(leader), target()));
		UUID winner = score.reached(target());
		if (winner != null) {
			finish(List.of(winner));
		}
	}

	@Override
	protected void onTimeUp() {
		UUID leader = score.leader();
		finish(leader == null ? List.of() : List.of(leader));
	}

	public KothScore score() {
		return score;
	}

	@Override
	public Component status(ServerPlayer viewer) {
		UUID leader = score == null ? null : score.leader();
		return Lang.tr(viewer, "koth.status", leader == null ? "-" : name(leader), leader == null ? 0 : score.points(leader), target());
	}
}
