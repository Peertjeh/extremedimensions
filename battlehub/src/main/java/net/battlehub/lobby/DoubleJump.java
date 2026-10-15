package net.battlehub.lobby;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.hub.HubWorld;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Double jump in the lobby: players may "fly"; the moment the client starts flying (jump pressed twice) the
 * flight is taken away again and the player is launched forward and up. Only in the hub, never in matches or
 * while spectating, and never for players in creative or spectator mode (their own flying stays theirs).
 */
public final class DoubleJump {
	private DoubleJump() {}

	/** Players we gave the ability to fly, so we only ever take back what we gave. */
	private static final Set<UUID> GRANTED = new HashSet<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static long ticks;

	private static HubConfig.LobbyExtras cfg() {
		return ConfigManager.config().lobbyExtras;
	}

	static boolean eligible(ServerPlayer p) {
		GameType mode = p.gameMode();
		return cfg().doubleJump && HubWorld.is(p.level()) && Sessions.inLobby(p.getUUID()) && !Bots.isBot(p)
				&& (mode == GameType.ADVENTURE || mode == GameType.SURVIVAL) && !p.isPassenger() && !p.isFallFlying();
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			boolean granted = GRANTED.contains(id);
			if (!eligible(p)) {
				if (granted) {
					take(p);
				}
				continue;
			}
			if (granted && p.getAbilities().flying) {
				launch(p);
				continue;
			}
			Long last = LAST.get(id);
			boolean ready = last == null || ticks - last >= cfg().doubleJumpCooldownTicks;
			if (ready && p.onGround() && !p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.onUpdateAbilities();
				GRANTED.add(id);
			}
		}
	}

	private static void take(ServerPlayer p) {
		GRANTED.remove(p.getUUID());
		if (p.gameMode() == GameType.ADVENTURE || p.gameMode() == GameType.SURVIVAL) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	/** The second jump: no more flying, a push forward and up. */
	static void launch(ServerPlayer p) {
		take(p);
		LAST.put(p.getUUID(), ticks);
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
		Vec3 push = flat.scale(cfg().doubleJumpForward).add(0, cfg().doubleJumpUp, 0);
		p.setDeltaMovement(push);
		p.connection.send(new ClientboundSetEntityMotionPacket(p));
		p.resetFallDistance();
		p.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 12, 0.3, 0.05, 0.3, 0.02);
		Msg.sound(p, SoundEvents.BAT_TAKEOFF, 0.4f, 1.4f);
		net.battlehub.fx.Sounds.play(p, net.battlehub.fx.Sounds.JUMP);
	}

	/** Before a match or spectating: flying is taken back first, so it is never saved with the player's state. */
	public static void reset(ServerPlayer p) {
		if (GRANTED.contains(p.getUUID())) {
			take(p);
		}
	}

	public static boolean granted(UUID id) {
		return GRANTED.contains(id);
	}

	/** For the self-test: pretend the client started flying. */
	public static void jumpForTest(ServerPlayer p) {
		if (GRANTED.contains(p.getUUID())) {
			p.getAbilities().flying = true;
		}
	}

	public static void forget(UUID id) {
		GRANTED.remove(id);
		LAST.remove(id);
	}

	public static void clear() {
		GRANTED.clear();
		LAST.clear();
		ticks = 0;
	}
}
