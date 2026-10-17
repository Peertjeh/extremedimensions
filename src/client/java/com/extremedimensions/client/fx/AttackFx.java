package com.extremedimensions.client.fx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.ModPackets.AttackFxPayload.Fx;
import com.extremedimensions.registry.ModParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Draws the Elders' attacks in fine detail from what the server sends each tick (see the server's {@code AttackFx}):
 * <ul>
 * <li>a sigil on the ground foretells a blow, turning and tightening on the spot it will land, a ring round it showing
 * how far it reaches; a lantern, needle, lance or comet of soul fire falls on it as the blow comes;</li>
 * <li>a blow lands in a flash, cracks the ground (they glow a while) and throws the debris of whoever struck it:
 * bark, thorns and leaves and violet fire for the Matriarch, ash, soul fire and spinning obols for the Ferryman,
 * glass shards and glints for the Last Weaver;</li>
 * <li>shockwaves run out as rings of torn earth, soul fire or woven light;</li>
 * <li>threads, chains and beams are laid along their lines as tiles that turn to face the viewer;</li>
 * <li>wisps and lantern bombs fly as sprites the server keeps steering, trailing fire.</li>
 * </ul>
 */
public final class AttackFx {
	private AttackFx() {}

	// The sprite families, in the order of ModParticles.ATTACK_FX, and how many sprites each holds
	static final int SIGIL = 0, WAVE = 1, CRACK = 2, FLASH = 3, THORN = 4, BARK = 5, LEAF = 6, COIN = 7, FLAME = 8, ASH = 9,
			SHARD = 10, GLINT = 11, LANTERN = 12, WISP = 13, NEEDLE = 14, THREAD = 15, CHAIN = 16, BEAM = 17, STREAM = 18;
	private static final int[] COUNT = {3, 3, 1, 4, 2, 3, 4, 8, 8, 4, 4, 4, 4, 6, 2, 2, 2, 4, 4};
	private static final SpriteSet[] SPRITES = new SpriteSet[COUNT.length];

	// The server's kinds (see the server's AttackFx and ElderStrikes)
	private static final int K_SIGIL = 1, K_IMPACT = 2, K_WAVE = 3, K_LINE = 4, K_WISP = 5, K_BURST = 6, K_RING = 7;
	private static final int S_LANTERN = 1, S_ROOTS = 2, S_SLAM = 3, S_NEEDLE = 4, S_SPEAR = 5, S_SOULFALL = 6;
	private static final int I_WISP = 20, I_BOMB = 21, I_SCORCH = 22, I_CUT = 23, I_BLAST = 24, I_CHARGE = 25;

	/** Each Elder's light: the Matriarch violet, the Ferryman teal, the Last Weaver gold-white. */
	private static final float[][] GLOW = {{0.82F, 0.52F, 1.0F}, {0.38F, 1.0F, 0.88F}, {1.0F, 0.88F, 0.62F}};

	private static final Map<Integer, AttackFxParticle> WISPS = new HashMap<>();
	private static float detail = 1;

	/** Takes the sprite sets of the attack families as the particle atlas is stitched. */
	public static void register() {
		ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
		for (int i = 0; i < ModParticles.ATTACK_FX_TYPES.length; i++) {
			int family = i;
			r.register(ModParticles.ATTACK_FX_TYPES[i], sprites -> {
				SPRITES[family] = sprites;
				return (type, level, x, y, z, dx, dy, dz, random) -> null;
			});
		}
	}

	public static void handle(ModPackets.AttackFxPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || SPRITES[0] == null) {
			return;
		}
		detail = payload.detail();
		for (Fx f : payload.fx()) {
			int e = Mth.clamp(f.elder(), 0, 2);
			switch (f.kind()) {
				case K_SIGIL -> sigil(level, e, f);
				case K_IMPACT -> impact(level, e, f.variant(), f.x(), f.y(), f.z(), f.size());
				case K_WAVE -> wave(level, e, f.variant() == 1, f.x(), f.y(), f.z(), f.y2(), f.x2(), f.size());
				case K_LINE -> line(level, f);
				case K_WISP -> wisp(level, e, f);
				case K_BURST -> burst(level, e, f.x(), f.y(), f.z(), f.size());
				case K_RING -> ring(level, e, f.x(), f.y(), f.z(), f.size(), Math.max(4, f.ticks()));
				default -> {
				}
			}
		}
		for (Iterator<AttackFxParticle> it = WISPS.values().iterator(); it.hasNext();) {
			if (!it.next().isAlive()) {
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ building blocks

	/** Whether the sprites are stitched yet (they are once the particle atlas is). */
	static boolean ready() {
		return SPRITES[BEAM] != null;
	}

	static AttackFxParticle make(ClientLevel level, int family, int frame, double x, double y, double z) {
		return new AttackFxParticle(level, x, y, z, SPRITES[family], COUNT[family], frame);
	}

	static AttackFxParticle add(AttackFxParticle p) {
		Minecraft.getInstance().particleEngine.add(p.start());
		return p;
	}

	private static int n(int base) {
		return Math.max(1, Math.round(base * detail));
	}

	private static void tint(AttackFxParticle p, int elder, float k) {
		float[] c = GLOW[elder];
		p.color(Math.min(1, c[0] * k), Math.min(1, c[1] * k), Math.min(1, c[2] * k));
	}

	/** A flat sprite on the ground (just above it). */
	private static AttackFxParticle decal(ClientLevel level, int family, int frame, double x, double y, double z, float size, int life) {
		AttackFxParticle p = make(level, family, frame, x, y + 0.04, z);
		p.mode = AttackFxParticle.Mode.GROUND;
		p.size0 = p.size1 = size;
		p.setLifetime(life);
		return p;
	}

	/** A piece of debris thrown from a point: outward and up, spinning, falling (or rising, for fire). */
	private static AttackFxParticle debris(ClientLevel level, int family, int frame, double x, double y, double z, float spread, float up,
			float size, float gravity, boolean collide, int life) {
		RandomSource r = level.getRandom();
		AttackFxParticle p = make(level, family, frame, x + (r.nextFloat() - 0.5) * spread * 0.4, y + 0.2 + r.nextFloat() * 0.4,
				z + (r.nextFloat() - 0.5) * spread * 0.4);
		double a = r.nextFloat() * Math.PI * 2;
		double h = (0.3 + r.nextFloat() * 0.7) * spread * 0.11;
		p.velocity(Math.cos(a) * h, up * (0.5 + r.nextFloat() * 0.6), Math.sin(a) * h);
		p.physics(gravity, collide);
		p.size0 = p.size1 = size * (0.75F + r.nextFloat() * 0.5F);
		p.setLifetime(life + r.nextInt(Math.max(1, life / 2)));
		p.fadeIn = 0;
		p.fadeOut = 8;
		p.spin = (r.nextFloat() - 0.5F) * 0.5F;
		return p;
	}

	private static void bark(ClientLevel level, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, BARK, r.nextInt(3), x, y, z, spread, 0.45F, 0.26F, 1.0F, true, 30);
			p.bright = false;
			add(p);
		}
	}

	private static void thorns(ClientLevel level, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, THORN, r.nextInt(2), x, y, z, spread, 0.5F, 0.3F, 1.1F, true, 30);
			p.bright = false;
			add(p);
		}
	}

	private static void leaves(ClientLevel level, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, LEAF, r.nextInt(4), x, y, z, spread, 0.35F, 0.24F, 0.12F, true, 70);
			p.bright = false;
			p.frames = 4;
			p.frameRate = 0.25F;
			p.spin *= 0.4F;
			p.onTick = q -> q.velocity(q.xd() * 0.97 + Mth.sin(q.age() * 0.3F) * 0.006, q.yd(), q.zd() * 0.97);
			add(p);
		}
	}

	private static void coins(ClientLevel level, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, COIN, r.nextInt(8), x, y, z, spread, 0.6F, 0.2F, 0.9F, true, 40);
			p.bright = false;
			p.frames = 8;
			p.frameRate = 0.6F;
			p.spin = 0;
			add(p);
		}
	}

	private static void ash(ClientLevel level, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, ASH, r.nextInt(4), x, y, z, spread, 0.3F, 0.16F, 0.2F, false, 50);
			p.bright = false;
			add(p);
		}
	}

	private static void flames(ClientLevel level, int elder, double x, double y, double z, float spread, int count, float size) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, FLAME, r.nextInt(8), x, y, z, spread, 0.12F, size, -0.15F, false, 16);
			p.frames = 8;
			p.frameRate = 0.5F;
			p.spin = 0;
			p.mode = AttackFxParticle.Mode.UPRIGHT;
			p.fadeOut = 6;
			p.growTicks = p.lifetime();
			p.size1 = p.size0 * 0.3F;
			tint(p, elder, 1);
			add(p);
		}
	}

	private static void shards(ClientLevel level, int elder, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, SHARD, r.nextInt(4), x, y, z, spread, 0.55F, 0.24F, 0.9F, true, 30);
			if (elder == 0) {
				tint(p, elder, 1.1F);
			}
			add(p);
		}
	}

	private static void glints(ClientLevel level, int elder, double x, double y, double z, float spread, int count) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			AttackFxParticle p = debris(level, GLINT, r.nextInt(4), x, y, z, spread, 0.25F, 0.28F, 0.05F, false, 18);
			p.frames = 4;
			p.frameRate = 0.3F;
			p.spin = 0;
			tint(p, elder, 1.15F);
			add(p);
		}
	}

	/** The matter of each Elder, thrown from a point. */
	private static void matter(ClientLevel level, int elder, double x, double y, double z, float spread, int count) {
		switch (elder) {
			case 0 -> {
				bark(level, x, y, z, spread, count);
				thorns(level, x, y, z, spread, Math.max(1, count / 2));
				leaves(level, x, y, z, spread, Math.max(1, count / 2));
				flames(level, 0, x, y, z, spread, Math.max(1, count / 3), 0.35F);
			}
			case 1 -> {
				flames(level, 1, x, y, z, spread, count, 0.45F);
				ash(level, x, y, z, spread, count);
				coins(level, x, y, z, spread, Math.max(1, count / 3));
			}
			default -> {
				shards(level, 2, x, y, z, spread, count);
				glints(level, 2, x, y, z, spread, count);
			}
		}
	}

	private static void flash(ClientLevel level, int elder, double x, double y, double z, float size) {
		boolean calm = ClientOptions.reduceFlashing();
		AttackFxParticle p = make(level, FLASH, 0, x, y, z);
		p.frames = 4;
		p.frameRate = 0.6F;
		p.size0 = size * (calm ? 0.5F : 1.0F);
		p.size1 = p.size0 * 0.7F;
		p.growTicks = 7;
		p.setLifetime(7);
		p.fadeIn = 0;
		p.fadeOut = 3;
		p.peak = calm ? 0.35F : 1.0F;
		tint(p, elder, 1.25F);
		add(p);
	}

	private static void crack(ClientLevel level, int elder, double x, double y, double z, float size, int life) {
		AttackFxParticle p = decal(level, CRACK, 0, x, y, z, size, life);
		p.fadeIn = 0;
		p.fadeOut = life / 2;
		p.peak = 0.7F;
		tint(p, elder, 1);
		add(p);
	}

	// ------------------------------------------------------------------ the effects

	/** A sigil foretelling a blow: it turns and tightens until the blow lands, a ring round it marks its reach. */
	private static void sigil(ClientLevel level, int elder, Fx f) {
		float radius = Math.max(0.8F, f.size());
		int ticks = Math.max(4, f.ticks());
		double x = f.x(), y = f.y(), z = f.z();
		AttackFxParticle s = decal(level, SIGIL, elder, x, y, z, radius * 1.35F, ticks + 5);
		s.size1 = radius * 0.95F;
		s.growTicks = ticks;
		s.growPower = 0.7F;
		s.spin = elder == 1 ? -0.03F : 0.035F;
		s.fadeIn = 5;
		s.fadeOut = 5;
		s.peak = 0.95F;
		if (!ClientOptions.reduceFlashing()) {
			s.onTick = p -> {
				p.pulse = 0.25F + 0.9F * (p.age() / (float) ticks) * (p.age() / (float) ticks);
				p.pulseDepth = 0.2F;
			};
		}
		add(s);
		AttackFxParticle edge = decal(level, WAVE, elder, x, y + 0.01, z, radius / 0.9F, ticks + 2);
		edge.spin = -s.spin * 0.5F;
		edge.fadeIn = 6;
		edge.fadeOut = 3;
		edge.peak = 0.8F;
		edge.onTick = p -> {
			// motes rising off its rim while it waits
			if (p.age() % 2 == 0) {
				double a = level.getRandom().nextFloat() * Math.PI * 2;
				double mx = x + Math.cos(a) * radius, mz = z + Math.sin(a) * radius;
				switch (elder) {
					case 0 -> flames(level, 0, mx, y, mz, 0.3F, 1, 0.22F);
					case 1 -> flames(level, 1, mx, y, mz, 0.3F, 1, 0.28F);
					default -> glints(level, 2, mx, y, mz, 0.3F, 1);
				}
			}
		};
		add(edge);
		falling(level, elder, f.variant(), x, y, z, radius, ticks);
	}

	/** What falls on a sigil as its blow comes: a lantern, a needle, a lance, a comet of soul fire. */
	private static void falling(ClientLevel level, int elder, int kind, double x, double y, double z, float radius, int ticks) {
		int family;
		int frame = 0;
		float size;
		double height;
		switch (kind) {
			case S_LANTERN -> {
				family = LANTERN;
				size = 0.8F;
				height = 14;
			}
			case S_NEEDLE -> {
				family = NEEDLE;
				size = 1.0F;
				height = 11;
			}
			case S_SPEAR -> {
				family = NEEDLE;
				frame = 1;
				size = 2.4F;
				height = 13;
			}
			case S_SOULFALL -> {
				family = FLAME;
				size = 1.3F;
				height = 16;
			}
			default -> {
				return;
			}
		}
		AttackFxParticle p = make(level, family, frame, x, y + height, z);
		p.mode = AttackFxParticle.Mode.UPRIGHT;
		p.size0 = p.size1 = size;
		p.setLifetime(ticks);
		p.fadeIn = 3;
		p.fadeOut = 0;
		if (family == LANTERN) {
			p.frames = 4;
			p.frameRate = 0.3F;
		} else if (family == FLAME) {
			p.frames = 8;
			p.frameRate = 0.5F;
			tint(p, 1, 1);
		}
		p.onTick = q -> {
			// it falls faster and faster, to land as the blow does
			double k = Math.min(1, q.age() / (double) ticks);
			q.setPos(x, y + size * 0.9 + height * (1 - k * k), z);
			switch (kind) {
				case S_LANTERN -> flames(level, 0, x, q.py() + size * 0.5, z, 0.2F, 1, 0.3F);
				case S_SOULFALL -> {
					flames(level, 1, x, q.py() + size * 0.5, z, 0.4F, 2, 0.5F);
					if (q.age() % 3 == 0) {
						ash(level, x, q.py(), z, 0.5F, 1);
					}
				}
				default -> {
					if (q.age() % 2 == 0) {
						glints(level, 2, x, q.py() + size * 0.8, z, 0.2F, 1);
					}
				}
			}
		};
		add(p);
	}

	/** A blow landing: a flash, cracks in the ground and the debris of whoever struck it. */
	private static void impact(ClientLevel level, int elder, int kind, double x, double y, double z, float radius) {
		float r = Math.max(0.6F, radius);
		switch (kind) {
			case S_LANTERN -> {
				flash(level, 0, x, y + 0.8, z, r * 0.9F);
				crack(level, 0, x, y, z, r, 70);
				flames(level, 0, x, y, z, r, n(12), 0.45F);
				shards(level, 0, x, y, z, r, n(6));
				ash(level, x, y, z, r, n(5));
			}
			case S_ROOTS -> {
				crack(level, 0, x, y, z, r * 1.1F, 90);
				bark(level, x, y, z, r, n(10));
				thorns(level, x, y, z, r, n(7));
				leaves(level, x, y, z, r, n(6));
				flames(level, 0, x, y, z, r, n(4), 0.3F);
			}
			case S_SLAM -> {
				flash(level, 1, x, y + 0.6, z, r * 0.8F);
				crack(level, 1, x, y, z, r, 70);
				ash(level, x, y, z, r, n(10));
				coins(level, x, y, z, r, n(4));
				flames(level, 1, x, y, z, r, n(8), 0.45F);
			}
			case S_NEEDLE -> {
				flash(level, 2, x, y + 0.5, z, r * 0.7F);
				crack(level, 2, x, y, z, r * 0.8F, 60);
				shards(level, 2, x, y, z, r, n(8));
				glints(level, 2, x, y, z, r, n(6));
			}
			case S_SPEAR -> {
				flash(level, 2, x, y + 1, z, r * 1.2F);
				crack(level, 2, x, y, z, r * 1.4F, 100);
				shards(level, 2, x, y, z, r * 1.3F, n(18));
				glints(level, 2, x, y, z, r * 1.3F, n(12));
				wave(level, 2, true, x, y, z, 0.5F, 0.9F, r * 2.2F);
			}
			case S_SOULFALL -> {
				flash(level, 1, x, y + 0.8, z, r);
				crack(level, 1, x, y, z, r, 80);
				flames(level, 1, x, y, z, r, n(14), 0.55F);
				ash(level, x, y, z, r, n(8));
				coins(level, x, y, z, r, n(2));
			}
			case I_WISP -> {
				flash(level, 1, x, y, z, 0.9F);
				flames(level, 1, x, y - 0.4, z, 1.2F, n(8), 0.35F);
				glints(level, 1, x, y - 0.4, z, 1.2F, n(4));
			}
			case I_BOMB -> {
				flash(level, 0, x, y, z, 2.2F);
				crack(level, 0, x, y - 0.6, z, 2.4F, 80);
				flames(level, 0, x, y - 0.6, z, 2.5F, n(12), 0.5F);
				ash(level, x, y - 0.6, z, 2.5F, n(8));
				shards(level, 0, x, y - 0.6, z, 2.5F, n(4));
			}
			case I_SCORCH -> {
				crack(level, elder, x, y, z, 1.4F, 50);
				flames(level, elder, x, y, z, 1.0F, n(2), 0.4F);
				glints(level, elder, x, y, z, 1.0F, n(2));
			}
			case I_CUT -> {
				glints(level, 2, x, y, z, 0.6F, n(3));
				shards(level, 2, x, y, z, 0.6F, n(2));
			}
			case I_BLAST -> {
				flash(level, elder, x, y + 1, z, r * 1.1F);
				crack(level, elder, x, y, z, r * 1.5F, 110);
				matter(level, elder, x, y, z, r * 1.4F, n(14));
				wave(level, elder, true, x, y, z, 0.5F, 0.8F, r * 2.5F);
			}
			case I_CHARGE -> {
				crack(level, elder, x, y, z, r, 60);
				matter(level, elder, x, y, z, r, n(3));
			}
			default -> {
				flash(level, elder, x, y + 0.5, z, r * 0.8F);
				crack(level, elder, x, y, z, r, 60);
				matter(level, elder, x, y, z, r, n(6));
			}
		}
	}

	/** A shockwave running out over the ground: a ring of the Elder's matter growing at the server's pace. */
	private static void wave(ClientLevel level, int elder, boolean faint, double x, double y, double z, float start, float speed, float max) {
		float sp = Math.max(0.05F, speed);
		int life = Math.max(2, Mth.ceil((max - start) / sp));
		// the band of the ring sprite sits at nine tenths of its size
		AttackFxParticle p = decal(level, WAVE, elder, x, y + (faint ? 0.02 : 0.05), z, Math.max(0.05F, start / 0.9F), life + 1);
		p.size1 = (max + sp) / 0.9F;
		p.growTicks = life + 1;
		p.fadeIn = 1;
		p.fadeOut = Math.max(2, life / 3);
		p.peak = faint ? 0.45F : 0.95F;
		p.spin = 0.01F;
		if (faint) {
			tint(p, elder, 0.7F);
		}
		if (!faint) {
			p.onTick = q -> {
				double rr = Math.min(max, start + q.age() * sp);
				for (int i = 0; i < n(2); i++) {
					double a = level.getRandom().nextFloat() * Math.PI * 2;
					double mx = x + Math.cos(a) * rr, mz = z + Math.sin(a) * rr;
					switch (elder) {
						case 0 -> {
							if (level.getRandom().nextBoolean()) {
								bark(level, mx, y, mz, 0.6F, 1);
							} else {
								leaves(level, mx, y, mz, 0.6F, 1);
							}
						}
						case 1 -> flames(level, 1, mx, y, mz, 0.6F, 1, 0.45F);
						default -> glints(level, 2, mx, y, mz, 0.6F, 1);
					}
				}
			};
		}
		add(p);
	}

	/** Threads, chains, beams and streams: tiles laid end to end along the line, each turned to face the viewer. */
	private static void line(ClientLevel level, Fx f) {
		double dx = f.x2() - f.x(), dy = f.y2() - f.y(), dz = f.z2() - f.z();
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.05) {
			return;
		}
		int kind = f.variant();
		if (kind == 5) {
			path(level, f, len);
			return;
		}
		int family;
		float half;
		switch (kind) {
			case 1 -> {
				family = THREAD;
				half = 0.3F;
			}
			case 2 -> {
				family = CHAIN;
				half = 0.42F;
			}
			case 3 -> {
				family = BEAM;
				half = 0.6F;
			}
			case 4 -> {
				family = STREAM;
				half = 0.45F;
			}
			default -> {
				family = THREAD;
				half = 0.26F;
			}
		}
		float tile = half * 2;
		int count = Math.min(160, Mth.ceil(len / tile));
		float ux = (float) (dx / len), uy = (float) (dy / len), uz = (float) (dz / len);
		int life = Math.max(2, f.ticks());
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			double k = Math.min(len - half, (i + 0.5) * tile);
			int frame = switch (kind) {
				case 1 -> 1;
				case 2 -> i % 2;
				case 3 -> r.nextInt(4);
				case 4 -> i % 4;
				default -> 0;
			};
			AttackFxParticle p = make(level, family, frame, f.x() + ux * k, f.y() + uy * k, f.z() + uz * k);
			p.mode = AttackFxParticle.Mode.AXIS;
			p.axis.set(ux, uy, uz);
			p.size0 = p.size1 = half;
			p.setLifetime(life);
			p.fadeIn = 0;
			p.fadeOut = kind == 1 ? Math.max(2, life / 2) : 0;
			if (kind == 3 || kind == 4) {
				p.frames = 4;
				p.frameRate = 1;
			}
			add(p);
		}
		// what comes off the line now and then: glints off silk, flames off the chains and the stream, sparks off the beam
		if (r.nextFloat() < 0.5F * detail) {
			double k = r.nextFloat() * len;
			double mx = f.x() + ux * k, my = f.y() + uy * k, mz = f.z() + uz * k;
			switch (kind) {
				case 2, 4 -> flames(level, 1, mx, my - 0.3, mz, 0.4F, 1, 0.3F);
				case 3 -> glints(level, 2, mx, my - 0.2, mz, 0.6F, 1);
				default -> glints(level, 2, mx, my - 0.2, mz, 0.2F, 1);
			}
		}
	}

	/** A path of small sigils on the ground along a line, kindling one after another from its start. */
	private static void path(ClientLevel level, Fx f, double len) {
		int e = Mth.clamp(f.elder(), 0, 2);
		float step = 2.6F;
		int count = Math.min(40, Mth.ceil(len / step));
		int life = Math.max(4, f.ticks());
		for (int i = 0; i < count; i++) {
			double k = Math.min(1, (i * step + step / 2) / len);
			AttackFxParticle p = decal(level, SIGIL, e, f.x() + (f.x2() - f.x()) * k, f.y() + (f.y2() - f.y()) * k + 0.01 * (i % 3),
					f.z() + (f.z2() - f.z()) * k, 1.15F, life + 2);
			p.fadeIn = 1 + i / 2;
			p.fadeOut = 3;
			p.peak = 0.85F;
			p.spin = e == 1 ? -0.06F : 0.06F;
			add(p);
		}
	}

	/** A wisp or a lantern bomb: one sprite for its whole flight, moved to where the server says, trailing fire. */
	private static void wisp(ClientLevel level, int elder, Fx f) {
		AttackFxParticle p = WISPS.get(f.ticks());
		if (p != null && p.isAlive()) {
			p.setPos(f.x(), f.y(), f.z());
			p.velocity(f.x2(), f.y2(), f.z2());
			p.extend(4);
			return;
		}
		boolean bomb = f.variant() == 1;
		p = make(level, bomb ? LANTERN : WISP, 0, f.x(), f.y(), f.z());
		p.mode = bomb ? AttackFxParticle.Mode.UPRIGHT : AttackFxParticle.Mode.BILLBOARD;
		p.frames = bomb ? 4 : 6;
		p.frameRate = bomb ? 0.3F : 0.4F;
		p.size0 = p.size1 = bomb ? 1.0F : 0.9F;
		p.setLifetime(5);
		p.fadeIn = 2;
		p.fadeOut = 2;
		p.velocity(f.x2(), f.y2(), f.z2());
		p.onTick = q -> {
			if (bomb) {
				flames(level, 0, q.px(), q.py() + 0.3, q.pz(), 0.3F, 1, 0.3F);
				if (q.age() % 3 == 0) {
					ash(level, q.px(), q.py(), q.pz(), 0.3F, 1);
				}
			} else {
				flames(level, 1, q.px(), q.py() - 0.1, q.pz(), 0.25F, 1, 0.25F);
			}
		};
		add(p);
		WISPS.put(f.ticks(), p);
	}

	/** A great burst of an Elder's matter: a flash, wide cracks, a storm of debris and a ring running out. */
	private static void burst(ClientLevel level, int elder, double x, double y, double z, float size) {
		float s = Math.max(2, size);
		flash(level, elder, x, y + s * 0.4, z, s * 0.8F);
		crack(level, elder, x, y, z, s, 140);
		matter(level, elder, x, y + s * 0.3, z, s, n(30));
		wave(level, elder, false, x, y, z, 0.5F, 1.0F, s * 2.2F);
	}

	/** A ring of the Elder's sigil laid round a place a while (a cage of chains, a circle of thorns). */
	private static void ring(ClientLevel level, int elder, double x, double y, double z, float radius, int ticks) {
		AttackFxParticle p = decal(level, WAVE, elder, x, y + 0.03, z, radius / 0.9F, ticks);
		p.spin = elder == 1 ? -0.02F : 0.02F;
		p.fadeIn = 6;
		p.fadeOut = 8;
		p.peak = 0.9F;
		p.onTick = q -> {
			if (q.age() % 3 == 0) {
				double a = level.getRandom().nextFloat() * Math.PI * 2;
				double mx = x + Math.cos(a) * radius, mz = z + Math.sin(a) * radius;
				switch (elder) {
					case 0 -> flames(level, 0, mx, y, mz, 0.3F, 1, 0.3F);
					case 1 -> flames(level, 1, mx, y, mz, 0.3F, 1, 0.4F);
					default -> glints(level, 2, mx, y, mz, 0.3F, 1);
				}
			}
		};
		add(p);
		AttackFxParticle inner = decal(level, SIGIL, elder, x, y + 0.02, z, radius * 0.7F, ticks);
		inner.spin = -p.spin;
		inner.fadeIn = 8;
		inner.fadeOut = 8;
		inner.peak = 0.45F;
		add(inner);
	}
}
