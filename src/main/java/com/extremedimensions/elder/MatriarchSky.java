package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;

import com.extremedimensions.config.Config;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sky of the Matriarch's Verdant Requiem (3.42): as her roots go down, a vortex of root and moss opens high over
 * her and turns; lanterns come down out of it on chains, hang over the arena and drift after whoever is near, each
 * burning a beam of violet light down onto the ground under someone (a sigil shows where, a moment before); at the
 * last the lanterns are drawn back up, the vortex closes down into her, and she blooms. Everything here is displays and
 * light; nothing of the world changes (the ground is torn up by the ultimate's own upheaval, see {@link ElderUpheaval}).
 */
final class MatriarchSky {
	private MatriarchSky() {}

	/** How high over her the vortex turns. */
	private static final double HIGH = 42;
	/** How far out the vortex's ring turns. */
	private static final double RING = 16;
	private static final int RING_STONES = 20;

	private static final class Lantern {
		final Display.BlockDisplay display;
		/** Where on the vortex its chain hangs from (an angle round the ring). */
		final double anchor;
		double x, y, z;
		/** Where it hangs once down (it drifts from there after the living). */
		double hx, hy, hz;
		/** The tick of the Requiem it next burns at, and the real ticks till the beam it has aimed lands (-1: none). */
		double next;
		int landing = -1;
		double tx, ty, tz;

		Lantern(Display.BlockDisplay display, double anchor) {
			this.display = display;
			this.anchor = anchor;
		}
	}

	private static final class Sky {
		ServerLevel level;
		double cx, cy, cz;
		final List<Display.BlockDisplay> ring = new ArrayList<>();
		final List<Lantern> lanterns = new ArrayList<>();
	}

	private static final Map<UUID, Sky> SKIES = new HashMap<>();

	/** How many lanterns hang in her sky now (for tests). */
	static int lanterns(UUID boss) {
		Sky s = SKIES.get(boss);
		return s == null ? 0 : s.lanterns.size();
	}

	/** Whether her vortex turns now (for tests). */
	static boolean open(UUID boss) {
		return SKIES.containsKey(boss);
	}

	private static double ease(double k) {
		k = Math.max(0, Math.min(1, k));
		return k * k * (3 - 2 * k);
	}

	/** A display of this sky moved to have its middle at (x, y, z), turned by q, over {@code ticks}. */
	private static void place(Sky s, Display.BlockDisplay d, double x, double y, double z, Quaternionf q, double size, int ticks) {
		DisplayAccess acc = (DisplayAccess) d;
		acc.extremedimensions$setTransformation(BlockFlight.pose(x - s.cx - 0.5, y - s.cy + 0.5, z - s.cz - 0.5, q, (float) size));
		acc.extremedimensions$setInterpolationDuration(ticks);
		acc.extremedimensions$setInterpolationDelay(0);
	}

	/** The vortex opens over her. */
	static void begin(Matriarch m) {
		end(m.getUUID());
		ServerLevel level = (ServerLevel) m.level();
		Sky s = new Sky();
		s.level = level;
		s.cx = m.getX();
		s.cy = m.getY();
		s.cz = m.getZ();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockState[] kinds = {ArenaDesign.mc("dark_oak_wood", net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()),
				ArenaDesign.mc("mangrove_roots", net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()),
				ArenaDesign.mc("moss_block", net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState())};
		for (int i = 0; i < RING_STONES; i++) {
			Display.BlockDisplay d = BlockFlight.spawn(level, s.cx, s.cy, s.cz, kinds[i % kinds.length],
					BlockFlight.pose(0, HIGH, 0, new Quaternionf(), 0.2F));
			if (d != null) {
				s.ring.add(d);
			}
		}
		Brightness lit = new Brightness(15, 15);
		int n = Math.min(14, 6 + 2 * ElderFx.targets(m, Math.max(16, Config.ELDER_ULTIMATE_RADIUS)).size());
		BlockState lamp = ArenaDesign.mc("lantern", net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
		double turn = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double anchor = turn + Math.PI * 2 * i / n;
			Display.BlockDisplay d = BlockFlight.spawn(level, s.cx, s.cy, s.cz, lamp, BlockFlight.pose(0, HIGH, 0, new Quaternionf(), 0.2F));
			if (d == null) {
				continue;
			}
			((DisplayAccess) d).extremedimensions$setBrightnessOverride(lit);
			Lantern l = new Lantern(d, anchor);
			double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble(22);
			l.hx = s.cx + Math.cos(a) * r;
			l.hz = s.cz + Math.sin(a) * r;
			l.hy = s.cy + 16 + rnd.nextDouble(8);
			l.x = s.cx + Math.cos(anchor) * RING;
			l.y = s.cy + HIGH;
			l.z = s.cz + Math.sin(anchor) * RING;
			// (they burn one after another, not all at once)
			l.next = 60 + i * 37 % 40;
			s.lanterns.add(l);
		}
		SKIES.put(m.getUUID(), s);
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.respawn_anchor.charge", "hostile", s.cx, s.cy + HIGH, s.cz, 6f, 0.5f);
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:block.beacon.activate", "hostile", s.cx, s.cy + HIGH, s.cz, 6f, 0.4f);
	}

	/** One tick of her sky at {@code t} of a Requiem {@code length} ticks long. */
	static void tick(Matriarch m, double t, int length) {
		Sky s = SKIES.get(m.getUUID());
		if (s == null) {
			return;
		}
		ServerLevel level = s.level;
		String dim = ElderFx.dim(m);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double closing = ease((t - (length - 40)) / 30.0);
		double opening = ease(t / 24.0);
		double r = RING * (0.3 + 0.7 * opening) * (1 - closing);
		double vy = s.cy + HIGH - (HIGH - 8) * closing;
		double spin = t * 0.06 * (1 + 2 * closing);
		boolean move = ((int) Math.floor(t)) % 2 == 0;
		// The vortex: root and moss turning in a ring, smoke and the void pouring round it
		if (move) {
			for (int i = 0; i < s.ring.size(); i++) {
				double a = spin + Math.PI * 2 * i / s.ring.size();
				double wob = Math.sin(t * 0.1 + i) * 1.5;
				Quaternionf q = new Quaternionf().rotateY((float) -a).rotateZ((float) (0.4 + 0.2 * Math.sin(t * 0.07 + i)));
				place(s, s.ring.get(i), s.cx + Math.cos(a) * (r + wob), vy + Math.sin(a * 3 + t * 0.05), s.cz + Math.sin(a) * (r + wob), q,
						2.4 * (1 - 0.6 * closing), 2);
			}
			for (int i = 0; i < 8; i++) {
				double a = spin * 1.6 + Math.PI * 2 * i / 8;
				double rr = r * (0.4 + 0.6 * rnd.nextDouble());
				WorldFx.particleIn(level, i % 2 == 0 ? "minecraft:reverse_portal" : "minecraft:large_smoke", s.cx + Math.cos(a) * rr, vy, s.cz + Math.sin(a) * rr,
						0.6, 0.4, 0.6, 0.05, 2);
			}
		}
		if (((int) Math.floor(t)) % 20 == 0) {
			WorldFx.soundIn(dim, "minecraft:block.beacon.ambient", "hostile", s.cx, vy, s.cz, 5f, 0.4f + 0.4f * (float) closing);
		}
		List<ServerPlayer> near = ElderFx.reachable(m, Math.max(16, Config.ELDER_ULTIMATE_RADIUS));
		for (Lantern l : s.lanterns) {
			double ax = s.cx + Math.cos(l.anchor + spin) * r, az = s.cz + Math.sin(l.anchor + spin) * r;
			// Down out of the vortex on its chain, then drifting after the nearest of the living; drawn back up at the last
			double down = ease((t - 20) / 40.0);
			if (t >= 60 && closing <= 0 && move && !near.isEmpty()) {
				ServerPlayer q = near.get(0);
				double best = Double.MAX_VALUE;
				for (ServerPlayer c : near) {
					double d = (c.getX() - l.hx) * (c.getX() - l.hx) + (c.getZ() - l.hz) * (c.getZ() - l.hz);
					if (d < best) {
						best = d;
						q = c;
					}
				}
				double dx = q.getX() - l.hx, dz = q.getZ() - l.hz, d = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
				double step = Math.min(d, 0.3);
				l.hx += dx / d * step;
				l.hz += dz / d * step;
			}
			double up = Math.max(closing, 0);
			double tx = l.hx + (ax - l.hx) * up, tz = l.hz + (az - l.hz) * up, ty = l.hy + (vy - l.hy) * up;
			l.x = ax + (tx - ax) * down;
			l.y = vy + (ty - vy) * down;
			l.z = az + (tz - az) * down;
			if (move) {
				double sway = Math.sin(t * 0.12 + l.anchor) * 0.25;
				place(s, l.display, l.x, l.y, l.z, new Quaternionf().rotateZ((float) sway).rotateY((float) (t * 0.03 + l.anchor)), 2.6 * (1 - 0.7 * closing), 2);
				if (((int) Math.floor(t)) % 4 == 0) {
					AttackFx.line(level, 0, AttackFx.LINE_CHAIN, ax, vy, az, l.x, l.y + 1.6, l.z, 5);
				}
			}
			// Each burns a beam down under someone in turn: a sigil first, then the light
			if (t >= l.next && t < length - 50 && l.landing < 0) {
				double gx, gy, gz;
				if (!near.isEmpty()) {
					ServerPlayer q = near.get(rnd.nextInt(near.size()));
					net.minecraft.world.phys.Vec3 v = q.getDeltaMovement();
					gx = q.getX() + v.x * 8 + rnd.nextDouble(-1, 1);
					gz = q.getZ() + v.z * 8 + rnd.nextDouble(-1, 1);
					gy = ElderFx.groundUnder(q);
				} else {
					gx = l.x + rnd.nextDouble(-6, 6);
					gz = l.z + rnd.nextDouble(-6, 6);
					gy = ElderFx.surface(level, gx, l.y, gz, 2, 40);
					if (Double.isNaN(gy)) {
						gy = s.cy;
					}
				}
				l.tx = gx;
				l.ty = gy;
				l.tz = gz;
				l.landing = 14;
				ElderStrikes.strike(m, gx, gy, gz, 2.2, (float) Config.MATRIARCH_REQUIEM_BEAM_DAMAGE, l.landing, ElderStrikes.LANTERN);
				l.next = t + 26 + rnd.nextInt(14);
			}
			if (l.landing >= 0 && --l.landing < 0) {
				AttackFx.line(level, 0, AttackFx.LINE_BEAM, l.x, l.y, l.z, l.tx, l.ty + 0.2, l.tz, 10);
				for (int i = 0; i < 6; i++) {
					double k = i / 6.0;
					WorldFx.particleIn(level, "minecraft:end_rod", l.x + (l.tx - l.x) * k, l.y + (l.ty - l.y) * k, l.z + (l.tz - l.z) * k, 0.2, 0.2, 0.2, 0.02,
							2);
				}
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", l.tx, l.ty + 0.5, l.tz, 1, 0.3, 1, 0.05, 12);
				WorldFx.soundIn(dim, "minecraft:block.beacon.power_select", "hostile", l.tx, l.ty + 1, l.tz, 3f, 0.6f + rnd.nextFloat() * 0.3f);
			}
		}
		if (t >= length - 10) {
			end(m.getUUID());
		}
	}

	/** Her sky closes: every display of it goes (the Requiem over, cut short, or she gone). */
	static void end(UUID boss) {
		Sky s = SKIES.remove(boss);
		if (s == null) {
			return;
		}
		for (Display.BlockDisplay d : s.ring) {
			BlockFlight.drop(d);
		}
		for (Lantern l : s.lanterns) {
			BlockFlight.drop(l.display);
		}
	}

	static void clear() {
		for (UUID boss : new ArrayList<>(SKIES.keySet())) {
			end(boss);
		}
	}
}
