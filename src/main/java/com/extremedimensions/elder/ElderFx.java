package com.extremedimensions.elder;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.network.ModPackets;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** What the Elders share: ground under a giant, gliding and turning, a roar, a pull, and their music. */
final class ElderFx {
	private ElderFx() {}

	/** The fight's music, by track: the Matriarch, the Ferryman, the Last Weaver. */
	static final int MUSIC_MATRIARCH = 0;
	static final int MUSIC_FERRYMAN = 1;
	static final int MUSIC_WEAVER = 2;

	static String dim(Entity e) {
		return e.level().dimension().identifier().toString();
	}

	/** The first spot going down from {@code top} with two blocks of air over solid, dry ground; null if none. */
	static BlockPos ground(ServerLevel level, int x, int top, int z, int depth) {
		for (int y = top; y > top - depth; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
					&& !level.getBlockState(feet.below()).isAir() && !level.getBlockState(feet.below()).liquid()) {
				return feet;
			}
		}
		return null;
	}

	/**
	 * The surface under a point for a giant: the top of the first solid block or liquid (lava carries the Ferryman's
	 * boat) going down from a little above it, ignoring leaves; NaN over the void.
	 */
	static double surface(ServerLevel level, double x, double y, double z, int up, int down) {
		int bx = Mth.floor(x), bz = Mth.floor(z);
		int top = Mth.floor(y) + up;
		for (int by = top; by > top - up - down; by--) {
			BlockState s = level.getBlockState(new BlockPos(bx, by, bz));
			if (!s.isAir() && !s.is(BlockTags.LEAVES) && (!s.canBeReplaced() || s.liquid())
					&& level.getBlockState(new BlockPos(bx, by + 1, bz)).canBeReplaced() && !level.getBlockState(new BlockPos(bx, by + 1, bz)).liquid()) {
				return by + 1;
			}
		}
		return Double.NaN;
	}

	/** Somewhere to stand between {@code min} and {@code max} blocks from the player, ahead of them if it can be. */
	static BlockPos near(ServerLevel level, ServerPlayer p, int min, int max) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double ahead = Math.toRadians(p.getYRot()) + Math.PI / 2;
		for (int i = 0; i < 16; i++) {
			double a = ahead + (rnd.nextDouble() - 0.5) * (i < 8 ? 1.4 : 6.2);
			double r = min + rnd.nextDouble() * (max - min);
			BlockPos spot = ground(level, (int) Math.floor(p.getX() + Math.cos(a) * r), (int) p.getY() + 8, (int) Math.floor(p.getZ() + Math.sin(a) * r), 20);
			if (spot != null) {
				return spot;
			}
		}
		return p.blockPosition().offset(6, 0, 6);
	}

	/**
	 * Moves a giant towards (tx, tz) by at most {@code speed} blocks, riding the surface below at {@code hover}
	 * blocks (its height eases rather than jumps); over the void it keeps its height.
	 */
	static void glide(ElderBoss b, double tx, double tz, double speed, double hover) {
		double dx = tx - b.getX(), dz = tz - b.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		double step = Math.min(speed, dist);
		double nx = b.getX() + (dist > 1e-4 ? dx / dist * step : 0);
		double nz = b.getZ() + (dist > 1e-4 ? dz / dist * step : 0);
		double ground = surface((ServerLevel) b.level(), nx, b.getY() - hover, nz, 8, 24);
		double ny = b.getY();
		if (!Double.isNaN(ground)) {
			ny += Mth.clamp(ground + hover - b.getY(), -0.4, 0.4);
		}
		b.setPos(nx, ny, nz);
		b.setDeltaMovement(Vec3.ZERO);
	}

	/** Turns a giant (body and head) towards a yaw, at most {@code max} degrees a tick. */
	static void turn(ElderBoss b, float yaw, float max) {
		float now = b.getYRot();
		float next = now + Mth.clamp(Mth.wrapDegrees(yaw - now), -max, max);
		b.setYRot(next);
		b.setYBodyRot(next);
		b.setYHeadRot(next);
	}

	/** The yaw that faces from one point towards another. */
	static float yawTo(Entity from, double x, double z) {
		return (float) (Mth.atan2(z - from.getZ(), x - from.getX()) * Mth.RAD_TO_DEG) - 90.0F;
	}

	/** The head alone turns towards a point (the Ferryman sails one way and looks another). */
	static void look(ElderBoss b, Entity at) {
		b.setYHeadRot(yawTo(b, at.getX(), at.getZ()));
	}

	static void roar(Entity e, float volume, float pitch) {
		WorldFx.soundIn(dim(e), "extremedimensions:keeper_roar", "hostile", e.getX(), e.getY() + 6, e.getZ(), volume, pitch);
		WorldFx.particleIn(dim(e), "extremedimensions:lurker_wisp", e.getX(), e.getY() + 7, e.getZ(), 2.5, 4.0, 2.5, 0.0, 80);
	}

	/** Drags a player part of the way towards a point (at most {@code max} blocks), onto somewhere safe to stand. */
	static void pull(Entity towards, ServerPlayer p, double max) {
		double dx = towards.getX() - p.getX(), dz = towards.getZ() - p.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 3) {
			return;
		}
		double step = Math.min(max, dist * 0.4);
		BlockPos to = ground((ServerLevel) p.level(), (int) Math.floor(p.getX() + dx / dist * step), (int) p.getY() + 3,
				(int) Math.floor(p.getZ() + dz / dist * step), 7);
		if (to == null) {
			return;
		}
		WorldFx.particleIn(dim(p), "extremedimensions:soul_mote", p.getX(), p.getY() + 1, p.getZ(), 0.4, 0.8, 0.4, 0.0, 12);
		p.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
	}

	/** Pushes a player away from (or, negative, towards) a point. */
	static void shove(Entity from, ServerPlayer p, double strength, double lift) {
		double dx = p.getX() - from.getX(), dz = p.getZ() - from.getZ();
		double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		p.setDeltaMovement(dx / d * strength, lift, dz / d * strength);
		p.hurtMarked = true;
	}

	/** A line of particles between two points (a chain, a thread). */
	static void line(String dim, String particle, double x0, double y0, double z0, double x1, double y1, double z1, int points) {
		for (int i = 0; i <= points; i++) {
			double k = i / (double) points;
			WorldFx.particleIn(dim, particle, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, z0 + (z1 - z0) * k, 0, 0, 0, 0, 1);
		}
	}

	/** The fight's music for everyone near (sent again every couple of seconds; it fades when it stops coming). */
	static void music(ElderBoss b, int track) {
		for (ServerPlayer p : ((ServerLevel) b.level()).players()) {
			if (p.distanceToSqr(b) < 96 * 96) {
				ServerPlayNetworking.send(p, new ModPackets.BossMusicPayload(track));
			}
		}
	}

	/** The music ends at once for everyone who could hear it. */
	static void silence(ElderBoss b) {
		for (ServerPlayer p : ((ServerLevel) b.level()).players()) {
			if (p.distanceToSqr(b) < 160 * 160) {
				ServerPlayNetworking.send(p, new ModPackets.BossMusicPayload(-1));
			}
		}
	}

	/** A great burst where an Elder dies or leaves. */
	static void burst(ElderBoss b, String particle, int count) {
		WorldFx.particleIn(dim(b), particle, b.getX(), b.getY() + b.getBbHeight() * 0.5, b.getZ(), b.getBbWidth(), b.getBbHeight() * 0.4,
				b.getBbWidth(), 0.05, count);
	}
}
