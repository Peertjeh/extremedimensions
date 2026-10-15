package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
	 * What a slain Elder leaves falls at the feet of the one it fought (if they are near and alive), not under the Elder,
	 * which may hang over lava or the void; it lies there a long while and does not burn.
	 */
	static void loot(ElderBoss b, java.util.UUID target, net.minecraft.world.item.ItemStack stack) {
		ServerLevel level = (ServerLevel) b.level();
		ServerPlayer p = target == null ? null : level.getServer().getPlayerList().getPlayer(target);
		Entity at = p != null && p.isAlive() && p.level() == level && p.distanceToSqr(b) < 96 * 96 ? p : b;
		net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
		item.setDefaultPickUpDelay();
		item.setInvulnerable(true);
		item.setUnlimitedLifetime();
		level.addFreshEntity(item);
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

	/** Everyone an Elder can hit within {@code radius} blocks (not spectators or creative players). */
	static List<ServerPlayer> targets(ElderBoss b, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : ((ServerLevel) b.level()).players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(b) <= radius * radius) {
				out.add(p);
			}
		}
		return out;
	}

	/** Throws a player away from a point ({@code horiz} blocks a tick outwards, {@code up} upwards); negative pulls. */
	static void knock(ServerPlayer p, double fromX, double fromZ, double horiz, double up) {
		double dx = p.getX() - fromX, dz = p.getZ() - fromZ;
		double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
		p.setDeltaMovement(dx / d * horiz, up, dz / d * horiz);
		p.needsSync = true;
	}

	/** Drags a player towards an Elder: a hard yank that carries them part of the way (at most about {@code max} blocks). */
	static void pull(Entity towards, ServerPlayer p, double max) {
		double dx = towards.getX() - p.getX(), dz = towards.getZ() - p.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 3) {
			return;
		}
		double h = Math.min(Math.min(max, dist * 0.6) * 0.32, 2.4);
		WorldFx.particleIn(p.level(), "extremedimensions:soul_mote", p.getX(), p.getY() + 1, p.getZ(), 0.4, 0.8, 0.4, 0.0, 12);
		knock(p, towards.getX(), towards.getZ(), -h, 0.45);
	}

	/** Throws a player away from an Elder; {@code lift} sends them up as well. */
	static void shove(Entity from, ServerPlayer p, double strength, double lift) {
		WorldFx.particleIn(p.level(), "minecraft:cloud", p.getX(), p.getY() + 0.5, p.getZ(), 0.4, 0.3, 0.4, 0.05, 10);
		knock(p, from.getX(), from.getZ(), 0.55 + strength * 0.75, 0.3 + lift * 0.9);
	}

	/** Which Elder this is, for the client's cinema and music: 0 the Matriarch, 1 the Ferryman, 2 the Last Weaver. */
	static int index(ElderBoss b) {
		return b instanceof Matriarch ? 0 : b instanceof Ferryman ? 1 : 2;
	}

	/** Sends a piece of the fight's cinema to everyone within {@code radius} blocks (see BossFxPayload). */
	static void cinema(ElderBoss b, double radius, int kind, int value, float strength, String key) {
		ModPackets.BossFxPayload fx = new ModPackets.BossFxPayload(kind, index(b), value, strength, key);
		for (ServerPlayer p : ((ServerLevel) b.level()).players()) {
			if (p.distanceToSqr(b) <= radius * radius) {
				ServerPlayNetworking.send(p, fx);
			}
		}
	}

	/** The ground shakes around a point: the screen of everyone near shakes, less the further away they are. */
	static void shake(ElderBoss b, double x, double z, double radius, float strength, int ticks) {
		for (ServerPlayer p : ((ServerLevel) b.level()).players()) {
			double dx = p.getX() - x, dz = p.getZ() - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < radius) {
				float s = (float) (strength * (1 - d / radius * 0.75));
				ServerPlayNetworking.send(p, new ModPackets.BossFxPayload(ModPackets.BossFxPayload.SHAKE, index(b), ticks, s, ""));
			}
		}
	}

	/** The name of a great blow, called out on the screens of everyone near. */
	static void call(ElderBoss b, String move) {
		cinema(b, 64, ModPackets.BossFxPayload.MOVE, 0, 0, "gui.extremedimensions.elder.move." + move);
	}

	/** A ring of particles on the ground. */
	static void ring(Level level, String particle, double x, double y, double z, double r, int n, double phase) {
		for (int i = 0; i < n; i++) {
			double a = 2 * Math.PI * i / n + phase;
			WorldFx.particleIn(level, particle, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 0, 0.02, 0, 0, 1);
		}
	}

	/** A heavy step: the ground thuds and dusts up under a walking giant, and shakes a little for those close. */
	static void step(ElderBoss b, float volume) {
		Level level = b.level();
		WorldFx.soundIn(dim(b), "minecraft:entity.ravager.step", "hostile", b.getX(), b.getY(), b.getZ(), volume, 0.45f);
		WorldFx.soundIn(dim(b), "minecraft:block.rooted_dirt.break", "hostile", b.getX(), b.getY(), b.getZ(), volume * 0.6f, 0.5f);
		WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", b.getX(), b.getY() + 0.3, b.getZ(), b.getBbWidth() * 0.5, 0.1, b.getBbWidth() * 0.5, 0.01, 4);
		WorldFx.particleIn(level, "minecraft:poof", b.getX(), b.getY() + 0.2, b.getZ(), b.getBbWidth() * 0.6, 0.1, b.getBbWidth() * 0.6, 0.03, 12);
		shake(b, b.getX(), b.getZ(), 26, 0.22f, 6);
	}

	/**
	 * The air of the fight around everyone near it: the Matriarch's falling leaves and spores, the Ferryman's ash and
	 * drifting souls, the Weaver's motes and threads of light.
	 */
	static void ambience(ElderBoss b) {
		Level level = b.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int which = index(b);
		for (ServerPlayer p : ((ServerLevel) level).players()) {
			if (p.distanceToSqr(b) > 64 * 64) {
				continue;
			}
			for (int i = 0; i < 3; i++) {
				double x = p.getX() + rnd.nextDouble(-14, 14), y = p.getY() + rnd.nextDouble(2, 12), z = p.getZ() + rnd.nextDouble(-14, 14);
				switch (which) {
					case 0 -> WorldFx.particleIn(level, i == 0 ? "minecraft:spore_blossom_air" : i == 1 ? "minecraft:falling_spore_blossom" : "minecraft:ash",
							x, y, z, 0.5, 0.5, 0.5, 0, 1);
					case 1 -> WorldFx.particleIn(level, i == 0 ? "minecraft:soul" : "minecraft:white_ash", x, y, z, 0.5, 0.5, 0.5, 0.01, i == 0 ? 1 : 3);
					default -> WorldFx.particleIn(level, i == 0 ? "minecraft:end_rod" : "extremedimensions:soul_mote", x, y, z, 0.5, 0.5, 0.5, 0.005, 1);
				}
			}
		}
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
				ServerPlayNetworking.send(p, new ModPackets.BossMusicPayload(track, false));
			}
		}
	}

	/** This fight's music ends for everyone who could hear it (another fight's music near plays on). */
	static void silence(ElderBoss b, int track) {
		for (ServerPlayer p : ((ServerLevel) b.level()).players()) {
			if (p.distanceToSqr(b) < 160 * 160) {
				ServerPlayNetworking.send(p, new ModPackets.BossMusicPayload(track, true));
			}
		}
	}

	/** A great burst where an Elder dies or leaves. */
	static void burst(ElderBoss b, String particle, int count) {
		WorldFx.particleIn(dim(b), particle, b.getX(), b.getY() + b.getBbHeight() * 0.5, b.getZ(), b.getBbWidth(), b.getBbHeight() * 0.4,
				b.getBbWidth(), 0.05, count);
	}
}
