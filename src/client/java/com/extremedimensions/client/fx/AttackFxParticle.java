package com.extremedimensions.client.fx;

import java.util.function.Consumer;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One sprite of an Elder's attack, set up by {@link AttackFx}: laid flat on the ground (a sigil, a ring, a crack),
 * stood along a line (a tile of a thread, chain or beam), upright and turning to face the viewer (a falling lantern or
 * needle), or facing the viewer outright (debris, flashes, wisps). It can grow or shrink, spin, run through its
 * sprite's frames, fade in and out and pulse, and do something every tick it lives (leave a trail, fall to its mark).
 */
public class AttackFxParticle extends SingleQuadParticle {
	public enum Mode { BILLBOARD, GROUND, AXIS, UPRIGHT }

	private final SpriteSet sprites;
	private final int count;
	Mode mode = Mode.BILLBOARD;
	/** The first frame and how many it runs through (at {@code frameRate} frames a tick; 0 holds the first). */
	int frame0;
	int frames = 1;
	float frameRate;
	/** Size at birth, size once grown (over {@code growTicks}; it then holds), and the curve's power (1 is even). */
	float size0 = 0.2F;
	float size1 = 0.2F;
	int growTicks = 1;
	float growPower = 1;
	private float oSize;
	/** Spin in radians a tick: about the vertical for a ground sprite, about the view for the others. */
	float spin;
	private float angle;
	private float oAngle;
	/** Fading in over the first ticks and out over the last, to {@code peak}; a pulse of {@code pulse} radians a tick. */
	int fadeIn = 2;
	int fadeOut = 4;
	float peak = 1;
	float pulse;
	float pulseDepth = 0.25F;
	/** Whether it is its own light (sigils, fire, glass) or lit by the world (bark, coins, ash). */
	boolean bright = true;
	/** The line it lies along (AXIS), unit length. */
	final Vector3f axis = new Vector3f(0, 1, 0);
	/** Something it does every tick it lives, and once as it goes. */
	Consumer<AttackFxParticle> onTick;
	Runnable onDeath;

	private final FacingCameraMode ground = (q, cam, pt) -> q.rotationY(Mth.lerp(pt, this.oAngle, this.angle)).rotateX((float) (-Math.PI / 2));
	private final FacingCameraMode along = this::alongAxis;

	AttackFxParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int count, int frame) {
		super(level, x, y, z, frame(sprites, count, frame));
		this.sprites = sprites;
		this.count = count;
		this.frame0 = frame;
		this.hasPhysics = false;
		this.gravity = 0;
		this.friction = 1.0F;
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		this.lifetime = 20;
		this.quadSize = 0.2F;
		this.oSize = 0.2F;
	}

	private static net.minecraft.client.renderer.texture.TextureAtlasSprite frame(SpriteSet set, int count, int i) {
		return count <= 1 ? set.first() : set.get(Math.floorMod(i, count), count - 1);
	}

	/** Starts it with its birth size and angle (call once it is set up). */
	AttackFxParticle start() {
		this.quadSize = this.size0;
		this.oSize = this.size0;
		// a big sprite (a sigil, a ring) is kept in view by a box as big as it is, not the small one at its centre
		float span = Math.max(this.size0, this.size1) * 2;
		if (span > 0.6F) {
			this.setSize(span, this.mode == Mode.GROUND ? 0.1F : span);
		}
		this.angle = this.oAngle = (float) (this.random.nextFloat() * Math.PI * 2);
		if (this.mode == Mode.BILLBOARD && this.spin != 0) {
			this.roll = this.oRoll = this.random.nextFloat() * (float) (Math.PI * 2);
		}
		this.setAlpha(this.fadeIn > 0 ? 0 : this.peak);
		return this;
	}

	AttackFxParticle angle(float a) {
		this.angle = this.oAngle = a;
		return this;
	}

	int age() {
		return this.age;
	}

	int lifetime() {
		return this.lifetime;
	}

	/** Keeps it alive a few ticks more (a wisp the server still moves). */
	void extend(int ticks) {
		this.lifetime = Math.max(this.lifetime, this.age + ticks);
	}

	double px() {
		return this.x;
	}

	double py() {
		return this.y;
	}

	double pz() {
		return this.z;
	}

	double xd() {
		return this.xd;
	}

	double yd() {
		return this.yd;
	}

	double zd() {
		return this.zd;
	}

	/** Puts it somewhere else; it is drawn sliding there over the tick. */
	void place(double px, double py, double pz) {
		this.setPos(px, py, pz);
	}

	void velocity(double vx, double vy, double vz) {
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
	}

	void physics(float gravity, boolean collide) {
		this.gravity = gravity;
		this.hasPhysics = collide;
		this.friction = 0.96F;
	}

	void color(float r, float g, float b) {
		this.setColor(r, g, b);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			if (this.onDeath != null) {
				Runnable r = this.onDeath;
				this.onDeath = null;
				r.run();
			}
			return;
		}
		this.oSize = this.quadSize;
		float g = Mth.clamp(this.age / (float) Math.max(1, this.growTicks), 0, 1);
		this.quadSize = this.size0 + (this.size1 - this.size0) * (float) Math.pow(g, this.growPower);
		this.oAngle = this.angle;
		this.angle += this.mode == Mode.BILLBOARD ? 0 : this.spin;
		if (this.mode == Mode.BILLBOARD && this.spin != 0) {
			this.oRoll = this.roll;
			this.roll += this.spin;
		}
		float a = this.peak;
		if (this.fadeIn > 0 && this.age < this.fadeIn) {
			a *= this.age / (float) this.fadeIn;
		}
		int left = this.lifetime - this.age;
		if (this.fadeOut > 0 && left < this.fadeOut) {
			a *= Math.max(0, left) / (float) this.fadeOut;
		}
		if (this.pulse > 0) {
			a *= 1 - this.pulseDepth + this.pulseDepth * Mth.sin(this.age * this.pulse);
		}
		this.setAlpha(Mth.clamp(a, 0, 1));
		if (this.frames > 1 && this.frameRate > 0) {
			int f = this.frame0 + (int) (this.age * this.frameRate) % this.frames;
			this.setSprite(frame(this.sprites, this.count, f));
		}
		if (this.onTick != null) {
			this.onTick.accept(this);
		}
	}

	/** Shows another frame of its sprite set. */
	void show(int f) {
		this.setSprite(frame(this.sprites, this.count, f));
	}

	@Override
	public float getQuadSize(float partialTick) {
		return Mth.lerp(partialTick, this.oSize, this.quadSize);
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		return switch (this.mode) {
			case GROUND -> this.ground;
			case AXIS -> this.along;
			case UPRIGHT -> FacingCameraMode.LOOKAT_Y;
			default -> FacingCameraMode.LOOKAT_XYZ;
		};
	}

	/** Stands the sprite along its line, turned about it to face the viewer as nearly as it can. */
	private void alongAxis(Quaternionf q, Camera camera, float partialTick) {
		Vec3 cam = camera.position();
		Vector3f toCam = new Vector3f((float) (cam.x - Mth.lerp(partialTick, this.xo, this.x)),
				(float) (cam.y - Mth.lerp(partialTick, this.yo, this.y)), (float) (cam.z - Mth.lerp(partialTick, this.zo, this.z)));
		Vector3f right = new Vector3f(this.axis).cross(toCam);
		if (right.lengthSquared() < 1e-8F) {
			q.set(camera.rotation());
			return;
		}
		right.normalize();
		Vector3f front = new Vector3f(right).cross(this.axis);
		q.setFromNormalized(new Matrix3f(right, this.axis, front));
	}

	@Override
	protected Layer getLayer() {
		return Layer.bySprite(this.sprite);
	}

	@Override
	public int getLightCoords(float partialTick) {
		return this.bright ? 0xF000F0 : super.getLightCoords(partialTick);
	}
}
