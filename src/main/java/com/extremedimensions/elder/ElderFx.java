package com.extremedimensions.elder;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** What the Elders share: finding ground to stand on, a roar, and a pull towards themselves. */
final class ElderFx {
	private ElderFx() {}

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

	/** Somewhere to stand between {@code min} and {@code max} blocks from the player, ahead of them if it can be. */
	static BlockPos near(ServerLevel level, ServerPlayer p, int min, int max) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double ahead = Math.toRadians(p.getYRot()) + Math.PI / 2;
		for (int i = 0; i < 16; i++) {
			double a = ahead + (rnd.nextDouble() - 0.5) * (i < 8 ? 1.4 : 6.2);
			double r = min + rnd.nextDouble() * (max - min);
			BlockPos spot = ground(level, (int) Math.floor(p.getX() + Math.cos(a) * r), (int) p.getY() + 6, (int) Math.floor(p.getZ() + Math.sin(a) * r), 14);
			if (spot != null) {
				return spot;
			}
		}
		return p.blockPosition().offset(4, 0, 4);
	}

	static void roar(Entity e, float volume, float pitch) {
		WorldFx.soundIn(dim(e), "extremedimensions:keeper_roar", "hostile", e.getX(), e.getY() + 3, e.getZ(), volume, pitch);
		WorldFx.particleIn(dim(e), "extremedimensions:lurker_wisp", e.getX(), e.getY() + 2.5, e.getZ(), 0.8, 1.5, 0.8, 0.0, 40);
	}

	/** Drags a player part of the way towards the Elder (at most {@code max} blocks), onto somewhere safe to stand. */
	static void pull(Entity boss, ServerPlayer p, double max) {
		double dx = boss.getX() - p.getX(), dz = boss.getZ() - p.getZ();
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
}
