package com.extremedimensions.mending;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.AttackFx;
import com.extremedimensions.elder.ElderFx;
import com.extremedimensions.elder.ElderTerrain;
import com.extremedimensions.elder.HeartArena;
import com.extremedimensions.network.ModPackets.BossFxPayload;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.FightBlocks;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Starved One's scenes at the Heart (3.48), in the Elders' manner, for everyone at its fight.
 *
 * <ul>
 * <li>Its rise ({@code starved_rise_seconds}, after the wind-up): the sky darkens and the fog closes in, its own sound
 * drawn out over the whole of it and its music beginning; black bars close in and the view is held on the spot
 * ({@code elder_spawn_camera}). The Heart's ground quakes harder and harder and cracks with soul fire out from where it
 * will break through, souls spiral in from the whole of the island group, and the four pillars light one after another,
 * each sending a strand of souls in to it. Then the floor breaks open over it and it claws its way up out of the hole,
 * until it bursts free: a shockwave of soul fire, its roar, a flash and its name across the screen.</li>
 * <li>Its drinking of time, once a fight ({@code starved_drink_*}): it rises high over the Heart and the bars close in
 * as for an Elder's ultimate; it drinks the light out of the arena (the soul lanterns' light and everyone's own runs to
 * it in streams), drawing everyone in its reach towards it and slowing them, a ring on the floor under it showing how
 * far what it drank will burst; at the end the soul fire bursts out of it, striking everyone still inside the ring.
 * Quenching all four pillars while it drinks tears the drinking out of it unspent.</li>
 * <li>Its death ({@link StarvedOne#DEATH_TICKS}): the view held on it, it convulses and sags as its chains to the
 * pillars snap one after another, its embers go out, and it collapses into a bloom of soul fire.</li>
 * </ul>
 *
 * Where it breaks out it tears a hole in the Heart's floor (where the world may be torn up); every block of it is held
 * by {@link FightBlocks} and the floor is whole again once the fight is over.
 */
final class HeartScenes {
	private HeartScenes() {}

	/** Where it breaks out of the Heart, from the altar (blocks), and how deep under the floor it starts. */
	static final int EX = 0, EZ = -8, DEPTH = 9;
	/** The breadth of the hole it tears, and of the crying obsidian round it. */
	private static final double PIT = 2.6, RIM = 1.2;
	private static final double TAU = Math.PI * 2;
	/** The Heart's floor it tore open, held until the fight is over. */
	private static final List<BlockPos> TORN = new ArrayList<>();

	static void forget() {
		TORN.clear();
	}

	private static String dim() {
		return SeamService.SEAM.identifier().toString();
	}

	/** Everyone at its fight. */
	private static final Predicate<ServerPlayer> NEAR = MendingService::inArena;

	/** Whether {@code t} is the tick at {@code share} of {@code length}. */
	static boolean at(int t, int length, double share) {
		return t == (int) Math.floor(length * share);
	}

	/** Eased from 0 to 1 (slow out, quick through, slow in). */
	static double ease(double k) {
		double x = Math.max(0, Math.min(1, k));
		return x * x * (3 - 2 * x);
	}

	private static double ex() {
		return MendingService.HX + EX + 0.5;
	}

	private static double ez() {
		return MendingService.HZ + EZ + 0.5;
	}

	private static void throwBack(ServerPlayer p, double x, double z, double horiz, double up) {
		double dx = p.getX() - x, dz = p.getZ() - z;
		double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
		p.setDeltaMovement(dx / d * horiz, up, dz / d * horiz);
		WorldFx.moved(p);
	}

	// ------------------------------------------------------------------ its rise

	/** Its rise begins: its own sound, the bars and the held view, its music, the first rumble under the Heart. */
	static void begin(ServerLevel seam, StarvedOne so, int length) {
		double x = ex(), y = MendingService.HY + 1, z = ez();
		// (its sound drawn out or hurried to fit however long the rise is set to take, as the Knot Mother's)
		float rate = (float) Math.max(0.5, Math.min(2.0, 9.0 / (length / 20.0)));
		WorldFx.soundIn(dim(), "extremedimensions:elder.starved_one.wake", "hostile", x, y, z, 6f, rate);
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.SPAWN, so.getId(), Config.ELDER_SPAWN_CAMERA > 0 ? length : -length,
				"gui.extremedimensions.elder.forming.starved_one");
		ElderFx.rumble(seam, NEAR, x, z, MendingService.ARENA + 16, 0.2f, length);
		ElderFx.music(seam, NEAR, ElderFx.MUSIC_STARVED_ONE);
		for (ServerPlayer p : seam.players()) {
			if (NEAR.test(p)) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.rising").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
			}
		}
	}

	/** A pillar lights as it rises: soul fire up its shaft from the node, and the node's light bursts. */
	static void lit(ServerLevel seam, BlockPos node) {
		double x = node.getX() + 0.5, y = node.getY() + 0.5, z = node.getZ() + 0.5;
		WorldFx.soundIn(dim(), "minecraft:block.beacon.activate", "block", x, y, z, 6f, 0.6f);
		WorldFx.soundIn(dim(), "minecraft:block.respawn_anchor.charge", "block", x, y, z, 5f, 0.5f);
		AttackFx.burst(seam, AttackFx.STARVED, x, y, z, 4);
		for (int k = 0; k < 24; k++) {
			WorldFx.particleFar(dim(), "minecraft:soul_fire_flame", x, y - 10 + k * 1.5, z, 0.6, 0.4, 0.6, 0.02, 3, 200);
		}
		WorldFx.particleFar(dim(), "minecraft:soul", x, y + 1, z, 1, 1, 1, 0.08, 40, 200);
	}

	/**
	 * One tick of its rise ({@code t} of {@code length}; {@code lit}: the nodes lit so far): the ground quakes and cracks,
	 * souls spiral in, the lit pillars' strands of souls run in to it, and at six tenths the floor breaks open over it.
	 */
	static void rising(ServerLevel seam, StarvedOne so, int t, int length, List<BlockPos> lit) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double k = t / (double) length;
		double x = ex(), fy = MendingService.HY + 1, z = ez();
		String dim = dim();
		// The ground quakes, harder and harder, and jolts at each turn of it
		if (t % 10 == 0) {
			ElderFx.rumble(seam, NEAR, x, z, MendingService.ARENA + 16, (float) (0.15 + 0.7 * k), 14);
		}
		if (at(t, length, 0.2) || at(t, length, 0.4) || at(t, length, 0.8)) {
			WorldFx.punch(seam, so, x, z, MendingService.ARENA + 16, (float) (0.35 + 0.5 * k), 12);
			WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", x, fy, z, 5f, 0.4f);
			AttackFx.wave(seam, AttackFx.STARVED, true, x, fy + 0.05, z, 1, 0.6, 6 + 14 * k);
		}
		// Cracks run out across the Heart's floor from where it will come, glowing with soul fire, longer as it comes
		if (t % 4 == 0 && k < 0.97) {
			double reach = 3 + 17 * Math.min(1, k / 0.7);
			for (int i = 0; i < 7; i++) {
				double a = i * TAU / 7 + 0.4 * Math.sin(i * 1.7);
				for (int n = 0; n < 2; n++) {
					double d = rnd.nextDouble() * reach, wob = Math.sin(d * 0.7 + i) * 0.8;
					WorldFx.particleIn(seam, "minecraft:soul_fire_flame", x + Math.cos(a) * d - Math.sin(a) * wob, fy + 0.1, z + Math.sin(a) * d + Math.cos(a) * wob,
							0.08, 0.02, 0.08, 0.01, 1);
				}
			}
			if (t % 12 == 0) {
				AttackFx.ring(seam, AttackFx.STARVED, x, fy + 0.05, z, 1.5 + 5 * k, 14);
			}
		}
		// Souls spiral in from the whole of the island group into the Heart
		if (k > 0.08 && k < 0.88 && t % 2 == 0) {
			for (int i = 0; i < 5; i++) {
				double a = t * 0.05 + i * TAU / 5, r = 18 + rnd.nextDouble() * 62;
				double sx = MendingService.HX + 0.5 + Math.cos(a) * r, sz = MendingService.HZ + 0.5 + Math.sin(a) * r;
				double sy = MendingService.HY + rnd.nextDouble() * 50 - 10;
				double vx = x - sx, vy = fy + 2 - sy, vz = z - sz, len = Math.max(1e-3, Math.sqrt(vx * vx + vy * vy + vz * vz));
				vx /= len;
				vy /= len;
				vz /= len;
				// (moving in, and curling round as they come: with no count, the spread is their way)
				WorldFx.particleFar(dim, i % 2 == 0 ? "minecraft:soul" : "minecraft:sculk_soul", sx, sy, sz, vx - vz * 0.6, vy, vz + vx * 0.6, 0.8, 0, 160);
			}
		}
		// The lit pillars' strands of souls converge on it
		if (t % 8 == 0) {
			for (BlockPos n : lit) {
				AttackFx.line(seam, AttackFx.STARVED, AttackFx.LINE_STREAM, n.getX() + 0.5, n.getY() + 1.4, n.getZ() + 0.5, x, fy + 1, z, 10);
			}
		}
		if (k < 0.6 && t % 20 == 0) {
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.ambient", "hostile", x, fy - 2, z, 2f + 3f * (float) k, 0.6f + 0.4f * (float) k);
		}
		// It breaks the floor open over it
		if (at(t, length, 0.6)) {
			tear(seam);
			WorldFx.punch(seam, so, x, z, MendingService.ARENA + 16, 0.9f, 16);
			WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", x, fy, z, 4f, 0.5f);
			WorldFx.soundIn(dim, "minecraft:block.deepslate.break", "hostile", x, fy, z, 6f, 0.5f);
			WorldFx.particleIn(seam, "minecraft:explosion", x, fy + 0.5, z, 1.5, 0.3, 1.5, 0, 6);
			WorldFx.particleIn(seam, "minecraft:large_smoke", x, fy + 1, z, 2, 0.6, 2, 0.05, 40);
			AttackFx.burst(seam, AttackFx.STARVED, x, fy, z, 5);
		}
		// ... and claws its way up out of the hole, its chains rattling, grunting with each pull
		if (k >= 0.6) {
			WorldFx.particleIn(seam, "extremedimensions:lurker_wisp", x, fy + 0.5, z, PIT * 0.6, 0.4, PIT * 0.6, 0.02, 3);
			WorldFx.particleIn(seam, "minecraft:soul_fire_flame", x, fy + 0.2, z, PIT * 0.7, 0.1, PIT * 0.7, 0.03, 2);
			if (t % 9 == 0) {
				WorldFx.soundIn(dim, "minecraft:block.chain.place", "hostile", x, fy, z, 3f, 0.5f);
			}
			if (t % 16 == 0) {
				WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.voice", "hostile", x, fy + 2, z, 4f, 0.7f + 0.3f * (float) k);
			}
		}
	}

	/** It bursts free of the Heart: the bars lift and its name is struck, a shockwave, its roar and a flash. */
	static void burst(ServerLevel seam, StarvedOne so) {
		double x = so.getX(), y = MendingService.HY + 1, z = so.getZ();
		String dim = dim();
		WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.roar", "hostile", x, y + 4, z, 6f, 1f);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", x, y, z, 4f, 0.5f);
		AttackFx.wave(seam, AttackFx.STARVED, false, x, y + 0.1, z, 1, 1.4, 40);
		AttackFx.wave(seam, AttackFx.STARVED, true, x, y + 0.1, z, 1, 0.8, 24);
		AttackFx.burst(seam, AttackFx.STARVED, x, y, z, 8);
		ElderFx.ring(seam, "minecraft:soul_fire_flame", x, y + 0.3, z, 6, 50, 0);
		ElderFx.ring(seam, "minecraft:soul_fire_flame", x, y + 0.3, z, 11, 70, 0.3);
		WorldFx.particleIn(seam, "extremedimensions:lurker_wisp", x, y + 4, z, 3, 4, 3, 0.05, 140);
		WorldFx.particleIn(seam, "minecraft:reverse_portal", x, y + 4, z, 4, 4, 4, 0.4, 120);
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.ULTIMATE_END, 0, 0, "");
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.INTRO, 0, 0, "");
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.FLASH, 0, 0.8f, "");
		WorldFx.punch(seam, so, x, z, MendingService.ARENA + 16, 1.0f, 25);
		for (ServerPlayer p : seam.players()) {
			if (NEAR.test(p) && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(x, y, z) < 9 * 9) {
				throwBack(p, x, z, 1.1, 0.5);
			}
		}
	}

	/** The Heart's floor breaks open where it comes up (only where the world may be torn up): a hole rimmed with crying obsidian. */
	private static void tear(ServerLevel seam) {
		if (!ElderTerrain.griefing(seam) || !TORN.isEmpty()) {
			return;
		}
		int cx = MendingService.HX + EX, cz = MendingService.HZ + EZ, hy = MendingService.HY;
		BlockState crying = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "crying_obsidian")).map(h -> h.value().defaultBlockState())
				.orElse(Blocks.OBSIDIAN.defaultBlockState());
		int r = (int) Math.ceil(PIT + RIM);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				for (int dy = 0; dy < 3; dy++) {
					BlockPos pos = new BlockPos(cx + dx, hy - dy, cz + dz);
					if (!Loaded.ready(seam, pos)) {
						continue;
					}
					BlockState s = seam.getBlockState(pos);
					if (s.isAir() || s.hasBlockEntity()) {
						continue;
					}
					if (d <= PIT) {
						FightBlocks.place(seam, pos, Blocks.AIR.defaultBlockState(), 2 | 16);
						TORN.add(pos);
					} else if (d <= PIT + RIM && dy == 0) {
						FightBlocks.place(seam, pos, crying, 2 | 16);
						TORN.add(pos);
					}
				}
			}
		}
	}

	/** The fight is over: the floor it tore open is whole again (now, or once its ground is loaded). */
	static void close(ServerLevel seam) {
		for (BlockPos pos : TORN) {
			FightBlocks.release(seam, pos);
		}
		TORN.clear();
	}

	/** Its music ends for everyone who could hear it. */
	static void silence(ServerLevel seam) {
		double r = Math.max(MendingService.ARENA, Config.STARVED_ARENA_REACH) + 64;
		ElderFx.silence(seam, p -> p.distanceToSqr(MendingService.HX + 0.5, MendingService.HY, MendingService.HZ + 0.5) < r * r, ElderFx.MUSIC_STARVED_ONE);
	}

	// ------------------------------------------------------------------ its drinking of time

	/** It begins to drink time: its own sound, the bars of an ultimate, and a word on what is coming and how far it reaches. */
	static void drinkBegins(ServerLevel seam, StarvedOne so, int length) {
		String dim = dim();
		WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.ultimate", "hostile", so.getX(), so.getY() + 4, so.getZ(), 6f, 1f);
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.ULTIMATE, length, 1, "");
		WorldFx.punch(seam, so, so.getX(), so.getZ(), MendingService.ARENA + 16, 0.6f, 20);
		for (ServerPlayer p : seam.players()) {
			if (NEAR.test(p)) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.drink", Math.max(4, Config.STARVED_DRINK_NOVA))
						.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
			}
		}
	}

	/** The ground under it, where the ring of its burst is laid. */
	private static double floorUnder(StarvedOne so) {
		return MendingService.HY + 1;
	}

	/**
	 * One tick of its drinking of time ({@code t} of {@code length}): a vortex of souls round it; the light of the arena's
	 * lanterns and of everyone in its reach running to it in streams; everyone in its reach drawn towards it and slowed;
	 * the ring of its burst on the floor under it, and near the end its sigil tightening there.
	 */
	static void drinking(ServerLevel seam, StarvedOne so, int t, int length, List<ServerPlayer> arena) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		String dim = dim();
		double x = so.getX(), y = so.getY() + so.getBbHeight() * 0.55, z = so.getZ(), gy = floorUnder(so);
		double k = t / (double) length;
		double reach = Math.max(8, Config.STARVED_DRINK_RADIUS), nova = Math.max(4, Config.STARVED_DRINK_NOVA);
		// The vortex: souls wheeling in round it, tighter and faster as it drinks
		if (t % 2 == 0) {
			for (int i = 0; i < 8; i++) {
				double a = t * (0.12 + 0.2 * k) + i * TAU / 8, r = 12 * (1 - ((t * 0.03 + i * 0.125) % 1)) + 1;
				WorldFx.particleIn(seam, i % 2 == 0 ? "minecraft:reverse_portal" : "minecraft:soul", x + Math.cos(a) * r, y + Math.sin(a * 0.5) * 2, z + Math.sin(a) * r,
						0, 0, 0, 0, 1);
			}
		}
		// The light pulled out of the arena: its soul lanterns' and everyone's own, in streams to it
		if (t % 10 == 0) {
			List<BlockPos> lanterns = HeartArena.lanterns(so);
			for (int i = 0; i < Math.min(10, lanterns.size()); i++) {
				BlockPos l = lanterns.get(rnd.nextInt(lanterns.size()));
				AttackFx.line(seam, AttackFx.STARVED, AttackFx.LINE_STREAM, l.getX() + 0.5, l.getY() + 0.5, l.getZ() + 0.5, x, y, z, 10);
			}
			for (ServerPlayer p : arena) {
				if (p.distanceToSqr(so) < reach * reach) {
					AttackFx.line(seam, AttackFx.STARVED, AttackFx.LINE_STREAM, p.getX(), p.getY() + 1, p.getZ(), x, y, z, 10);
				}
			}
		}
		if (t % 40 == 0) {
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.drink", "hostile", x, y, z, 6f, 0.8f + 0.4f * (float) k);
			// the ring its burst will fill
			AttackFx.ring(seam, AttackFx.STARVED, x, gy + 0.05, z, nova, 42);
		}
		if (t == length - 30) {
			AttackFx.sigil(seam, AttackFx.STARVED, 0, x, gy + 0.05, z, nova, 30);
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.voice", "hostile", x, y, z, 6f, 0.6f);
		}
		if (t % 10 == 0) {
			ElderFx.rumble(seam, NEAR, x, z, reach + 16, (float) (0.1 + 0.5 * k), 12);
		}
		// Everyone in its reach is drawn in and slowed, and the light goes out of their eyes
		int slow = Math.max(0, Math.min(5, Config.STARVED_DRINK_SLOW));
		for (ServerPlayer p : arena) {
			if (p.isCreative() || p.isSpectator() || p.distanceToSqr(so) > reach * reach) {
				continue;
			}
			double dx = x - p.getX(), dz = z - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
			if (d > 2.5) {
				double ux = dx / d, uz = dz / d;
				double pull = com.extremedimensions.events.PaleSquall.push(p, ux, uz, Math.max(0, Config.STARVED_DRINK_PULL) * (0.5 + 0.5 * k));
				if (pull > 0) {
					com.extremedimensions.events.PaleSquall.gust(p, ux * pull, uz * pull);
				}
			}
			if (t % 20 == 0) {
				if (slow > 0) {
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, slow - 1, false, false));
				}
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
			}
		}
	}

	/** What it drank bursts out of it: soul fire over everyone still inside its ring, thrown out, and a flash. */
	static void nova(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		String dim = dim();
		double x = so.getX(), y = so.getY() + so.getBbHeight() * 0.5, z = so.getZ(), gy = floorUnder(so);
		double nova = Math.max(4, Config.STARVED_DRINK_NOVA);
		WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.ultimate", "hostile", x, y, z, 6f, 1.25f);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", x, gy, z, 6f, 0.5f);
		AttackFx.burst(seam, AttackFx.STARVED, x, gy, z, nova * 0.6);
		AttackFx.wave(seam, AttackFx.STARVED, false, x, gy + 0.1, z, 1, 1.2, nova * 1.6);
		WorldFx.particleIn(seam, "minecraft:soul_fire_flame", x, y, z, nova * 0.4, 3, nova * 0.4, 0.25, 220);
		ElderFx.ring(seam, "minecraft:soul_fire_flame", x, gy + 0.3, z, nova, 80, 0);
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.FLASH, 0, 0.9f, "");
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.ULTIMATE_END, 0, 0, "");
		WorldFx.punch(seam, so, x, z, MendingService.ARENA + 16, 1.2f, 30);
		double damage = Math.max(0, Config.STARVED_DRINK_DAMAGE);
		for (ServerPlayer p : arena) {
			double dx = p.getX() - x, dz = p.getZ() - z;
			if (p.isCreative() || p.isSpectator() || dx * dx + dz * dz > nova * nova || Math.abs(p.getY() - y) > nova + 6) {
				continue;
			}
			if (damage > 0) {
				p.hurtServer(seam, seam.damageSources().mobAttack(so), (float) damage);
			}
			throwBack(p, x, z, 1.3, 0.6);
		}
	}

	/** Its drinking of time is torn out of it unspent (every pillar quenched, or its fight over): the bars lift. */
	static void drinkBroken(ServerLevel seam, StarvedOne so) {
		WorldFx.soundIn(dim(), "minecraft:block.beacon.deactivate", "hostile", so.getX(), so.getY() + 4, so.getZ(), 5f, 0.5f);
		WorldFx.particleIn(seam, "minecraft:soul", so.getX(), so.getY() + 3, so.getZ(), 2, 2, 2, 0.1, 60);
		ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.ULTIMATE_END, 0, 0, "");
	}

	// ------------------------------------------------------------------ its death

	/** A chain to a pillar snaps as it dies: a crack of iron, sparks where it broke, and the node goes dark (see Binding). */
	static void snap(ServerLevel seam, StarvedOne so, BlockPos node) {
		double nx = node.getX() + 0.5, ny = node.getY() + 1, nz = node.getZ() + 0.5;
		double mx = (nx + so.getX()) / 2, my = (ny + so.getY() + 3) / 2, mz = (nz + so.getZ()) / 2;
		String dim = dim();
		WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", mx, my, mz, 6f, 0.5f);
		WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", so.getX(), so.getY() + 3, so.getZ(), 5f, 0.7f);
		WorldFx.soundIn(dim, "minecraft:block.beacon.deactivate", "block", nx, ny, nz, 5f, 0.6f);
		AttackFx.burst(seam, AttackFx.STARVED, mx, my, mz, 3);
		WorldFx.particleFar(dim, "minecraft:soul", nx, ny, nz, 0.6, 1, 0.6, 0.05, 30, 200);
		WorldFx.punch(seam, so, so.getX(), so.getZ(), MendingService.ARENA + 16, 0.5f, 10);
	}

	/**
	 * One tick of its death ({@code t} of {@code length}; {@code held}: the nodes whose chains still hold it): the bars
	 * close in on its name and the view is held on it; it convulses, the chains still holding it drawn taut; its light is
	 * drawn in and its embers go out; then the bloom.
	 */
	static void death(ServerLevel seam, StarvedOne so, int t, int length, BlockPos[] held) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		String dim = dim();
		double w = so.getBbWidth() * 0.7, h = so.getBbHeight(), k = t / (double) length;
		double x = so.getX(), y = so.getY(), z = so.getZ();
		if (t == 1) {
			ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.DEATH, so.getId(), length, "gui.extremedimensions.elder.title.starved_one");
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.death", "hostile", x, y + h * 0.6, z, 6f, 1f);
			WorldFx.soundIn(dim, "minecraft:block.beacon.deactivate", "hostile", x, y + h * 0.6, z, 5f, 0.5f);
		}
		if (t % 2 == 0) {
			ElderFx.rumble(seam, NEAR, x, z, MendingService.ARENA + 16, (float) (0.15 + 0.6 * k), 4);
		}
		// The chains still holding it, drawn taut as it thrashes
		if (t % 6 == 0) {
			for (BlockPos n : held) {
				if (n != null) {
					AttackFx.line(seam, AttackFx.STARVED, AttackFx.LINE_CHAIN, n.getX() + 0.5, n.getY() + 1, n.getZ() + 0.5, x, y + h * 0.6, z, 7);
				}
			}
		}
		if (k < 0.75) {
			// Its light drawn in, and its embers still burning, fewer and fewer
			for (int i = 0; i < 3; i++) {
				double a = rnd.nextDouble() * TAU;
				WorldFx.particleIn(seam, "minecraft:reverse_portal", x + Math.cos(a) * w * 2, y + rnd.nextDouble(0.2, 1.0) * h, z + Math.sin(a) * w * 2, 0.2, 0.2, 0.2,
						0.4, 2);
			}
			if (rnd.nextDouble() > k) {
				WorldFx.particleIn(seam, "minecraft:soul_fire_flame", x, y + h * 0.6, z, w * 0.4, h * 0.2, w * 0.4, 0.05, 4);
			}
		} else if (t % 3 == 0) {
			// ... gone out: smoke where they were
			WorldFx.particleIn(seam, "minecraft:large_smoke", x, y + h * 0.6, z, w * 0.4, h * 0.2, w * 0.4, 0.02, 3);
		}
		if (t % 12 == 0 && k > 0.4 && k < 0.9) {
			WorldFx.soundIn(dim, "minecraft:block.fire.extinguish", "hostile", x, y + h * 0.6, z, 3f, 0.5f + (float) k * 0.3f);
		}
		if (t % 8 == 0 && k < 0.9) {
			// It breaks apart: bursts of soul fire all over its body, louder towards the end
			double bx = x + rnd.nextDouble(-w, w), by = y + rnd.nextDouble(0.1, 0.95) * h, bz = z + rnd.nextDouble(-w, w);
			WorldFx.particleIn(seam, "minecraft:explosion", bx, by, bz, 0.5, 0.5, 0.5, 0, 1);
			WorldFx.particleIn(seam, "minecraft:soul", bx, by, bz, 1, 1, 1, 0.1, 10);
			WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", bx, by, bz, 2f, 0.5f + (float) k * 0.6f);
		}
		if (t == length - 5) {
			// The bloom
			WorldFx.particleIn(seam, "minecraft:explosion_emitter", x, y + h * 0.5, z, 1, 1, 1, 0, 3);
			WorldFx.particleIn(seam, "extremedimensions:soul_mote", x, y + h * 0.5, z, w, h * 0.4, w, 0.25, 200);
			WorldFx.particleIn(seam, "minecraft:soul_fire_flame", x, y + h * 0.5, z, w, h * 0.4, w, 0.3, 160);
			AttackFx.burst(seam, AttackFx.STARVED, x, y + 1, z, 10);
			for (int r = 4; r <= 24; r += 5) {
				ElderFx.ring(seam, "minecraft:soul_fire_flame", x, y + 0.3, z, r, 18 + r * 3, r * 0.2);
			}
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.death", "hostile", x, y + 6, z, 6f, 0.7f);
			WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", x, y + 6, z, 5f, 0.4f);
			ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.FLASH, 0, 0.9f, "");
			WorldFx.punch(seam, so, x, z, MendingService.ARENA + 16, 1.2f, 30);
		}
		if (t == length - 1) {
			ElderFx.cinema(seam, NEAR, ElderFx.CINEMA_STARVED_ONE, BossFxPayload.ULTIMATE_END, 0, 0, "");
		}
	}
}
