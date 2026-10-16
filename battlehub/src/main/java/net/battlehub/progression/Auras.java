package net.battlehub.progression;

import java.util.List;

import net.battlehub.bot.Bots;
import net.battlehub.core.AuraShape;
import net.battlehub.core.CosmeticDef;
import net.battlehub.hub.HubWorld;
import net.battlehub.session.Sessions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Auras: wings, halos, crowns and other shapes of particles round a player in the lobby (not in matches). */
public final class Auras {
	private Auras() {}

	/** A frame every few ticks: smooth enough, and cheap enough with a lobby full of wings. */
	private static final int EVERY = 3;
	private static int frames;

	public static int frames() {
		return frames;
	}

	public static boolean valid(String effect) {
		return AuraShape.SHAPES.contains(AuraShape.shapeOf(effect)) && Particles.valid(AuraShape.particleOf(effect));
	}

	static void tick(MinecraftServer server, int tick) {
		if (tick % EVERY != 0) {
			return;
		}
		ServerLevel hub = HubWorld.level(server);
		if (hub == null) {
			return;
		}
		for (ServerPlayer p : hub.players()) {
			if (p.isSpectator() || p.isInvisible() || !Sessions.inLobby(p.getUUID()) || Bots.isBot(p)) {
				continue;
			}
			CosmeticDef c = Cosmetics.active(p, "aura");
			if (c != null) {
				draw(hub, c.effect, p.position(), p.yBodyRot, tick / EVERY);
			}
		}
	}

	/** One frame of an aura at a spot (also used for the preview in the shop). */
	public static void draw(ServerLevel level, String effect, Vec3 at, float yaw, long frame) {
		String particle = AuraShape.particleOf(effect);
		List<AuraShape.Point> pts = AuraShape.points(AuraShape.shapeOf(effect), frame, yaw);
		for (AuraShape.Point pt : pts) {
			double x = at.x + pt.x();
			double y = at.y + pt.y();
			double z = at.z + pt.z();
			if (pt.accent()) {
				Particles.emit(level, "minecraft:cloud", x, y, z, 1, 0.04, 0.02, 0.04, 0, frame);
			} else {
				Particles.emit(level, particle, x, y, z, 1, 0, 0, 0, 0, frame);
			}
		}
		frames++;
	}
}
