package net.battlehub.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.core.ml.Velocity;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * How every player in a match moves, per tick. A bot's velocity is its {@code getDeltaMovement()}; a person moves on
 * their client, and on the server that value is not how they move (it only holds what the server pushed, a knock), so
 * theirs is worked out from their positions ({@link Velocity}): the same kind of velocity a bot has and the simulation
 * trains on. Before 1.41 the model saw people's {@code getDeltaMovement()}, and the fight logs stored their steps.
 */
public final class Motion {
	private Motion() {}

	private static final Map<UUID, Vec3> POS = new HashMap<>();
	private static final Map<UUID, Vec3> STEP = new HashMap<>();
	private static final Map<UUID, Vec3> VEL = new HashMap<>();
	private static long now;

	/** Once a tick, before the bots think: where everyone in a match is now. */
	public static void tick(MinecraftServer server) {
		now++;
		for (Match m : Matches.all()) {
			for (UUID id : m.participants()) {
				ServerPlayer p = m.player(id);
				if (p == null) continue;
				Vec3 pos = p.position(), last = POS.put(id, pos);
				// a teleport (a respawn, a pearl) is no step
				Vec3 step = last == null || last.distanceToSqr(pos) > 16 ? Vec3.ZERO : pos.subtract(last);
				STEP.put(id, step);
				if (!(p instanceof BotPlayer)) {
					BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.5000001, p.getZ());
					double friction = p.level().getBlockState(under).getBlock().getFriction();
					double[] v = Velocity.fromStep(step.x, step.y, step.z, p.onGround(), friction, p.getAttributeValue(Attributes.GRAVITY));
					VEL.put(id, new Vec3(v[0], v[1], v[2]));
				}
			}
		}
		if (now % 200 == 0) {
			POS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			STEP.keySet().retainAll(POS.keySet());
			VEL.keySet().retainAll(POS.keySet());
		}
	}

	/** The player's velocity as the game keeps it (per tick, after friction and gravity). */
	static Vec3 velocity(ServerPlayer p) {
		if (p instanceof BotPlayer) return p.getDeltaMovement();
		return VEL.getOrDefault(p.getUUID(), Vec3.ZERO);
	}

	/** How far the player moved in the last tick. */
	static Vec3 step(ServerPlayer p) {
		return STEP.getOrDefault(p.getUUID(), Vec3.ZERO);
	}

	static void clear() {
		POS.clear();
		STEP.clear();
		VEL.clear();
	}
}
