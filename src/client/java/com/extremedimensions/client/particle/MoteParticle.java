package com.extremedimensions.client.particle;

import com.extremedimensions.registry.ModParticles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

/**
 * The mod's own particles. Motes are small, full-bright points of light that drift and flicker; wisps are dark curls
 * of ink that swell and thin out. Each animates through its own sprite frames over its life.
 */
public class MoteParticle extends SingleQuadParticle {
	enum Kind { SOUL, EMBER, VOID, WISP, FIREFLY, DUST }

	private final SpriteSet sprites;
	private final Kind kind;
	private final float baseSize;
	private final float phase;

	MoteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
		super(level, x, y, z, 0, 0, 0, sprites.first());
		this.sprites = sprites;
		this.kind = kind;
		this.phase = this.random.nextFloat() * 6.28F;
		this.hasPhysics = false;
		this.friction = 0.96F;
		this.gravity = 0;
		// Spread given by the command arrives as velocity only when count is 0; keep it gentle either way
		this.xd = xd * 0.2 + (this.random.nextFloat() - 0.5F) * 0.01;
		this.zd = zd * 0.2 + (this.random.nextFloat() - 0.5F) * 0.01;
		switch (kind) {
			case SOUL -> {
				this.yd = 0.012 + this.random.nextFloat() * 0.012 + yd * 0.2;
				this.lifetime = 30 + this.random.nextInt(25);
				this.baseSize = 0.06F + this.random.nextFloat() * 0.04F;
			}
			case EMBER -> {
				this.yd = 0.03 + this.random.nextFloat() * 0.03 + yd * 0.2;
				this.lifetime = 20 + this.random.nextInt(20);
				this.baseSize = 0.05F + this.random.nextFloat() * 0.04F;
			}
			case VOID -> {
				this.yd = 0.008 + this.random.nextFloat() * 0.01 + yd * 0.2;
				this.lifetime = 25 + this.random.nextInt(25);
				this.baseSize = 0.06F + this.random.nextFloat() * 0.05F;
			}
			case FIREFLY -> {
				this.xd = (this.random.nextFloat() - 0.5F) * 0.02;
				this.yd = (this.random.nextFloat() - 0.5F) * 0.01;
				this.zd = (this.random.nextFloat() - 0.5F) * 0.02;
				this.friction = 0.98F;
				this.lifetime = 120 + this.random.nextInt(120);
				this.baseSize = 0.05F + this.random.nextFloat() * 0.05F;
			}
			case DUST -> {
				// what is given is the wind it drifts on (in the Seam, a breath of air towards the way home)
				this.xd = xd + (this.random.nextFloat() - 0.5F) * 0.006;
				this.yd = -0.01 - this.random.nextFloat() * 0.012;
				this.zd = zd + (this.random.nextFloat() - 0.5F) * 0.006;
				this.friction = 1.0F;
				this.lifetime = 160 + this.random.nextInt(120);
				this.baseSize = 0.025F + this.random.nextFloat() * 0.025F;
			}
			default -> {
				this.yd = 0.015 + this.random.nextFloat() * 0.015 + yd * 0.2;
				this.lifetime = 24 + this.random.nextInt(16);
				this.baseSize = 0.22F + this.random.nextFloat() * 0.18F;
			}
		}
		this.quadSize = this.baseSize;
		if (kind == Kind.FIREFLY || kind == Kind.DUST) {
			// one colour for life, picked from the sprite set
			this.setSprite(sprites.get(this.random.nextInt(4), 3));
			this.setAlpha(0);
		} else {
			this.setSpriteFromAge(sprites);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		float t = (float) this.age / this.lifetime;
		float a = this.age * 0.15F + this.phase;
		switch (this.kind) {
			case SOUL -> {
				// A slow spiral upward, fading in and out
				this.xd += Mth.cos(a) * 0.0015;
				this.zd += Mth.sin(a) * 0.0015;
				this.setAlpha(Mth.sin(t * Mth.PI) * (0.75F + 0.25F * Mth.sin(a * 2.3F)));
			}
			case EMBER -> {
				// Rises fast, jitters like a spark in hot air, cools as it goes
				this.xd += (this.random.nextFloat() - 0.5F) * 0.004;
				this.zd += (this.random.nextFloat() - 0.5F) * 0.004;
				this.setAlpha(1.0F - t * t);
			}
			case VOID -> {
				// Hangs nearly still and twinkles
				this.setAlpha((1.0F - t) * (0.55F + 0.45F * Mth.sin(a * 3.1F)));
			}
			case FIREFLY -> {
				// Wanders on lazy curves and pulses, fading in and out over its life
				this.xd += Mth.cos(a * 0.21F) * 0.0012;
				this.zd += Mth.sin(a * 0.17F) * 0.0012;
				this.yd += Mth.sin(a * 0.11F) * 0.0006;
				float fade = Math.min(1.0F, Math.min(t * 6.0F, (1.0F - t) * 6.0F));
				this.setAlpha(fade * (0.55F + 0.45F * Mth.sin(a * 0.9F)));
				this.quadSize = this.baseSize * (0.85F + 0.25F * Mth.sin(a * 0.9F));
			}
			case DUST -> {
				// Sifts down, swaying, glinting
				this.xd += Mth.sin(a * 0.08F) * 0.0004;
				float fade = Math.min(1.0F, Math.min(t * 5.0F, (1.0F - t) * 5.0F));
				this.setAlpha(fade * (0.6F + 0.4F * Mth.sin(a * 1.7F)));
			}
			default -> {
				// Ink curls and swells as it thins out
				this.xd += Mth.cos(a * 0.7F) * 0.002;
				this.zd += Mth.sin(a * 0.7F) * 0.002;
				this.quadSize = this.baseSize * (1.0F + t * 0.8F);
				this.setAlpha(0.85F * (1.0F - t));
			}
		}
		if (this.kind != Kind.FIREFLY && this.kind != Kind.DUST) {
			this.setSpriteFromAge(this.sprites);
		}
	}

	@Override
	protected Layer getLayer() {
		return Layer.bySprite(this.sprite);
	}

	@Override
	public int getLightCoords(float partialTick) {
		// Motes are their own light; ink is not
		return this.kind == Kind.WISP ? super.getLightCoords(partialTick) : 0xF000F0;
	}

	record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
			return new MoteParticle(level, x, y, z, xd, yd, zd, this.sprites, this.kind);
		}
	}

	public static void register() {
		ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
		r.register(ModParticles.SOUL_MOTE, sprites -> new Provider(sprites, Kind.SOUL));
		r.register(ModParticles.EMBER_MOTE, sprites -> new Provider(sprites, Kind.EMBER));
		r.register(ModParticles.VOID_MOTE, sprites -> new Provider(sprites, Kind.VOID));
		r.register(ModParticles.LURKER_WISP, sprites -> new Provider(sprites, Kind.WISP));
		r.register(ModParticles.SEAM_FIREFLY, sprites -> new Provider(sprites, Kind.FIREFLY));
		r.register(ModParticles.SEAM_DUST, sprites -> new Provider(sprites, Kind.DUST));
	}
}
