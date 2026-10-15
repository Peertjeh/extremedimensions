package com.extremedimensions.elder;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * An Elder forming, before its fight: black bars close in, the view of everyone near is held on it
 * ({@code elder_spawn_camera}) and its arena rises round it while it takes shape out of matter and light, each in its
 * own way; it cannot be hurt meanwhile, and its health bar fills as it forms. Then its name is struck across the screen
 * and the fight begins.
 *
 * <ul>
 * <li>The Matriarch is drawn up out of the earth: violet light spirals in over the ground, roots claw up round the spot,
 * and she rises through a storm of obsidian tears until the earth breaks open round her.</li>
 * <li>The Ferryman answers the toll: three bells, a whirl of fire opening in the ground, chains of fire rising out of it
 * and his boat and robe condensing out of the smoke, until the last bell.</li>
 * <li>The Last Weaver is woven: threads of light come down out of the sky to one point, wind themselves into a turning
 * cocoon that tightens round it, and tear open.</li>
 * </ul>
 */
final class ElderSpawn {
	private ElderSpawn() {}

	private static final String[] NAMES = {"matriarch", "ferryman", "weaver"};

	/** Ticks an Elder takes to form. */
	static int length() {
		return Math.max(40, (int) Math.round(Config.ELDER_SPAWN_SECONDS * 20));
	}

	/** It begins to form at {@code centre} (the first air above the ground under it), its arena rising round it. */
	static void begin(ElderBoss b, ElderFight f, BlockPos centre) {
		f.start(b, ElderBoss.SPAWN);
		f.bar.setProgress(0);
		ElderArena.raise(b, centre);
		int length = length();
		// The bars and the held view (a negative length leaves the view free)
		ElderFx.cinema(b, 96, ModPackets.BossFxPayload.SPAWN, b.getId(), Config.ELDER_SPAWN_CAMERA > 0 ? length : -length,
				"gui.extremedimensions.elder.forming." + NAMES[ElderFx.index(b)]);
		ElderFx.shake(b, b.getX(), b.getZ(), 48, 0.25f, length);
	}

	/** Its time to form is cut short (an operator or a test forces a blow): it is whole at once. */
	static void skip(ElderBoss b, ElderFight f) {
		if (f.action == ElderBoss.SPAWN) {
			finish(b, f);
		}
	}

	/** One tick of its forming; true once it is whole and the fight begins. */
	static boolean tick(ElderBoss b, ElderFight f) {
		f.step();
		double t = f.beat.t;
		int length = length();
		switch (ElderFx.index(b)) {
			case 0 -> matriarch(b, t, length);
			case 1 -> ferryman(b, t, length);
			default -> weaver(b, t, length);
		}
		if (t >= length) {
			finish(b, f);
			return true;
		}
		return false;
	}

	/** Whole: the bars lift, its name is struck across the screen, and it fights. */
	private static void finish(ElderBoss b, ElderFight f) {
		f.end(b, 30);
		ElderFx.cinema(b, 96, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
		ElderFx.cinema(b, 96, ModPackets.BossFxPayload.INTRO, 0, 0, "");
		ElderFx.cinema(b, 96, ModPackets.BossFxPayload.FLASH, 0, 0.5f, "");
		ElderFx.roar(b, 5f, 0.5f);
		ElderFx.shake(b, b.getX(), b.getZ(), 56, 0.9f, 25);
	}

	/** Whether {@code t} has just passed {@code at} (a share of the length). */
	private static boolean at(double t, int length, double share) {
		double mark = Math.floor(length * share);
		return t >= mark && t - 1 < mark;
	}

	private static double share(double t, int length) {
		return t / length;
	}

	// ------------------------------------------------------------------ the Matriarch

	private static void matriarch(ElderBoss b, double t, int length) {
		ServerLevel level = (ServerLevel) b.level();
		String dim = ElderFx.dim(b);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double k = share(t, length);
		double x = b.getX(), z = b.getZ();
		double gy = b.getY() + (b instanceof Matriarch ? MatriarchService.sunk(b) : 0);
		if (k < 0.5 && ((int) t) % 2 == 0) {
			// Violet light spiralling in over the ground
			for (int i = 0; i < 6; i++) {
				double a = t * 0.22 + i * Math.PI / 3;
				double r = 10 * (1 - (t % 24) / 24.0) + 0.5;
				WorldFx.particleIn(level, "minecraft:reverse_portal", x + Math.cos(a) * r, gy + 0.2, z + Math.sin(a) * r, 0.1, 0.05, 0.1, 0.0, 2);
			}
			WorldFx.particleIn(level, "extremedimensions:lurker_wisp", x, gy + 0.5, z, 1.2, 0.3, 1.2, 0.02, 2);
		}
		if (((int) t) % 10 == 0 && k < 0.85) {
			WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.break", "hostile", x, gy, z, 3f, (float) (0.4 + k * 0.4));
		}
		if (at(t, length, 0.15) || at(t, length, 0.28)) {
			// Roots claw up out of the ground round the spot
			for (int i = 0; i < 5; i++) {
				double a = rnd.nextDouble(Math.PI * 2), r = rnd.nextDouble(3.5, 7);
				ElderTerrain.column(level, x + Math.cos(a) * r, gy, z + Math.sin(a) * r, 2 + rnd.nextInt(3), Blocks.MANGROVE_ROOTS, length);
			}
			WorldFx.soundIn(dim, "minecraft:block.mangrove_roots.place", "hostile", x, gy, z, 4f, 0.5f);
			ElderFx.shake(b, x, z, 40, 0.5f, 10);
		}
		if (k >= 0.3 && k < 0.88) {
			// She rises through a storm of obsidian tears
			WorldFx.particleIn(level, "minecraft:falling_obsidian_tear", x, gy + 8, z, 3.5, 4, 3.5, 0, 5);
			WorldFx.particleIn(level, "minecraft:witch", x, gy + 1, z, 2.5, 0.6, 2.5, 0.05, 4);
			WorldFx.particleIn(level, "minecraft:large_smoke", x, gy + 0.3, z, 2.5, 0.2, 2.5, 0.02, 3);
		}
		if (at(t, length, 0.88)) {
			// The earth breaks open round her
			WorldFx.particleIn(level, "extremedimensions:lurker_wisp", x, gy + 7, z, 3, 5, 3, 0.05, 140);
			WorldFx.particleIn(level, "minecraft:reverse_portal", x, gy + 6, z, 4, 5, 4, 0.4, 120);
			ElderFx.ring(level, "minecraft:witch", x, gy + 0.3, z, 6, 40, 0);
			ElderFx.ring(level, "minecraft:witch", x, gy + 0.3, z, 10, 60, 0.3);
			ElderFx.roar(b, 5f, 0.45f);
			ElderTerrain.mark(level, x, gy, z, 7, 0.6, ElderTerrain.Mark.ROOT);
		}
	}

	// ------------------------------------------------------------------ the Ferryman

	private static void ferryman(ElderBoss b, double t, int length) {
		ServerLevel level = (ServerLevel) b.level();
		String dim = ElderFx.dim(b);
		double k = share(t, length);
		double x = b.getX(), y = b.getY(), z = b.getZ();
		if (at(t, length, 0.01) || at(t, length, 0.33) || at(t, length, 0.66) || at(t, length, 0.92)) {
			// The toll
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", x, y + 8, z, 6f, (float) (0.4 + 0.05 * Math.floor(k * 3)));
			WorldFx.soundIn(dim, "minecraft:block.bell.resonate", "hostile", x, y + 8, z, 3f, 0.5f);
			ElderFx.shake(b, x, z, 48, 0.6f, 12);
		}
		if (k < 0.6 && ((int) t) % 2 == 0) {
			// A whirl of fire opening in the ground
			double open = Math.min(1, k / 0.3);
			for (int arm = 0; arm < 5; arm++) {
				for (int j = 0; j < 4; j++) {
					double r = (1 + j * 2) * open;
					double a = t * 0.18 + arm * Math.PI * 2 / 5 - r * 0.45;
					WorldFx.particleIn(level, j % 2 == 0 ? "minecraft:flame" : "minecraft:small_flame", x + Math.cos(a) * r, y + 0.15,
							z + Math.sin(a) * r, 0.05, 0.02, 0.05, 0.01, 1);
				}
			}
			if (((int) t) % 8 == 0) {
				WorldFx.particleIn(level, "minecraft:lava", x, y + 0.3, z, 1.5, 0.1, 1.5, 0, 3);
				WorldFx.soundIn(dim, "minecraft:block.lava.ambient", "hostile", x, y, z, 2f, 0.6f);
			}
		}
		if (k >= 0.25 && k < 0.8 && ((int) t) % 4 == 0) {
			// Chains of fire rising out of the whirl and meeting over it
			for (int i = 0; i < 4; i++) {
				double a = i * Math.PI / 2 + t * 0.03;
				ElderFx.line(dim, "minecraft:flame", x + Math.cos(a) * 5, y + 0.2, z + Math.sin(a) * 5, x, y + 9 + 3 * (k - 0.25), z, 14);
			}
			if (((int) t) % 12 == 0) {
				WorldFx.soundIn(dim, "minecraft:block.chain.place", "hostile", x, y + 4, z, 3f, 0.5f);
			}
		}
		if (k >= 0.4 && k < 0.92) {
			// His boat and his robe condense out of the smoke
			WorldFx.particleIn(level, "minecraft:large_smoke", x, y + 1.5, z, 3, 0.6, 3, 0.0, 3);
			WorldFx.particleIn(level, "minecraft:campfire_cosy_smoke", x, y + 5 + 6 * (k - 0.4), z, 1.2, 2, 1.2, 0.01, 2);
			WorldFx.particleIn(level, "minecraft:soul", x, y + 6, z, 2, 3, 2, 0.02, 1);
		}
		if (at(t, length, 0.92)) {
			WorldFx.particleIn(level, "minecraft:flame", x, y + 6, z, 3, 4, 3, 0.15, 160);
			WorldFx.particleIn(level, "minecraft:large_smoke", x, y + 3, z, 4, 2, 4, 0.05, 80);
			ElderFx.ring(level, "minecraft:flame", x, y + 0.3, z, 7, 50, 0);
			ElderFx.roar(b, 4f, 0.65f);
		}
	}

	// ------------------------------------------------------------------ the Last Weaver

	private static void weaver(ElderBoss b, double t, int length) {
		ServerLevel level = (ServerLevel) b.level();
		String dim = ElderFx.dim(b);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double k = share(t, length);
		double x = b.getX(), y = b.getY() + 9, z = b.getZ();
		if (k < 0.55 && ((int) t) % 3 == 0) {
			// Threads of light coming down out of the sky to one point
			for (int i = 0; i < 3; i++) {
				double a = rnd.nextDouble(Math.PI * 2), r = rnd.nextDouble(8, 16);
				ElderFx.line(dim, "minecraft:end_rod", x + Math.cos(a) * r, y + 18 + rnd.nextDouble(6), z + Math.sin(a) * r, x, y, z, 22);
			}
		}
		if (((int) t) % 8 == 0 && k < 0.88) {
			WorldFx.soundIn(dim, "minecraft:block.amethyst_block.chime", "hostile", x, y, z, 3f, (float) (0.5 + k * 1.2));
		}
		if (k >= 0.2 && k < 0.88 && ((int) t) % 2 == 0) {
			// The cocoon: threads winding round it, turning and tightening
			double r = 7 - 3.5 * Math.min(1, (k - 0.2) / 0.6);
			for (int i = 0; i < 14; i++) {
				double a = t * 0.12 + i * Math.PI * 2 / 14;
				for (int ring = 0; ring < 3; ring++) {
					double tilt = ring * Math.PI / 3 + t * 0.02;
					double cx = Math.cos(a) * r, cy = Math.sin(a) * r * Math.cos(tilt), cz = Math.sin(a) * r * Math.sin(tilt);
					WorldFx.particleIn(level, ring == 0 ? "minecraft:end_rod" : "extremedimensions:soul_mote", x + cx, y + cy, z + cz, 0, 0, 0, 0, 1);
				}
			}
		}
		if (at(t, length, 0.88)) {
			// It tears open
			WorldFx.particleIn(level, "minecraft:end_rod", x, y, z, 2, 3, 2, 0.35, 160);
			WorldFx.particleIn(level, "extremedimensions:soul_mote", x, y, z, 3, 4, 3, 0.2, 140);
			WorldFx.soundIn(dim, "minecraft:block.amethyst_block.break", "hostile", x, y, z, 6f, 0.5f);
			WorldFx.soundIn(dim, "minecraft:entity.warden.sonic_boom", "hostile", x, y, z, 3f, 1.4f);
			ElderFx.ring(level, "minecraft:end_rod", x, b.getY() - 2, z, 8, 50, 0);
		}
	}
}
