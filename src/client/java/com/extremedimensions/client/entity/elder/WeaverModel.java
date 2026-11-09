package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Weaver;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Last Weaver moving: it floats and bobs, its long tatters ripple, its arms rise and fall a little with its
 * threads and lanterns, its halo turns, the needles circling its waist drift round and the spools at its hips spin.
 * It throws a thread forward with one arm, raises both to rain needles (the halo turning faster), draws both arms in
 * before flinging everything near away; for the lance its halo spins up and it points; for the cradle its hands weave
 * and then pull the threads taut; for the thousand needles it rises with its arms up and its needles whirl. In its
 * ultimate it rises with its arms spread wide while its halo and its needles swell and whirl, until everything snaps.
 * Dying, its halo falls and it sinks. As it forms, its halo comes first, then its needles are drawn in round it, and it
 * is woven from the hem up: its robe unrolling, its body, its arms unfolding, its head, its veil, its lanterns last.
 * Its face and crown are at Minecraft's own size, one texture pixel to a model pixel.
 */
public class WeaverModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("weaver"), "main");

	private final ModelPart root, body, torso, head, halo, armL, armR, foreL, foreR, handL, handR, needles, spoolL, spoolR, bodyCore,
			torsoCore, headCore;
	private final ModelPart[] robe, mantle, strands, lanterns, veil;
	/**
	 * Which way each strip of the robe turns (xRot) to be flung out from her: most hang round her facing out, the tabard
	 * and sashes from her front facing in (-1); the drapes hang from her arms (0).
	 */
	private final float[] robeOut;
	/** Which arm each drape of the robe hangs from (1 the left, -1 the right, 0 none). */
	private final float[] robeArm;

	public WeaverModel(ModelPart model) {
		super(model);
		this.root = WeaverGeometry.find(model, "root");
		this.body = WeaverGeometry.find(model, "body");
		this.torso = WeaverGeometry.find(model, "torso");
		this.head = WeaverGeometry.find(model, "head");
		this.halo = WeaverGeometry.find(model, "halo");
		this.armL = WeaverGeometry.find(model, "arm_l");
		this.armR = WeaverGeometry.find(model, "arm_r");
		this.foreL = WeaverGeometry.find(model, "forearm_l");
		this.foreR = WeaverGeometry.find(model, "forearm_r");
		this.handL = WeaverGeometry.find(model, "hand_l");
		this.handR = WeaverGeometry.find(model, "hand_r");
		this.needles = WeaverGeometry.find(model, "needles");
		this.spoolL = WeaverGeometry.find(model, "spool_l");
		this.spoolR = WeaverGeometry.find(model, "spool_r");
		this.robe = ElderAnim.parts(model, WeaverGeometry.ROBE, WeaverGeometry::find);
		this.mantle = ElderAnim.parts(model, WeaverGeometry.MANTLE, WeaverGeometry::find);
		this.strands = ElderAnim.parts(model, WeaverGeometry.STRAND, WeaverGeometry::find);
		this.lanterns = ElderAnim.parts(model, WeaverGeometry.LANTERN, WeaverGeometry::find);
		this.veil = ElderAnim.parts(model, WeaverGeometry.VEIL, WeaverGeometry::find);
		this.bodyCore = WeaverGeometry.find(model, "bodycore");
		this.torsoCore = WeaverGeometry.find(model, "torsocore");
		this.headCore = WeaverGeometry.find(model, "headcore");
		this.robeOut = new float[robe.length];
		this.robeArm = new float[robe.length];
		for (int i = 0; i < robe.length; i++) {
			ModelPart r = robe[i];
			String name = WeaverGeometry.ROBE[i];
			robeArm[i] = name.startsWith("drape_l") ? 1 : name.startsWith("drape_r") ? -1 : 0;
			robeOut[i] = robeArm[i] != 0 ? 0 : Mth.sin(r.yRot) * r.x + Mth.cos(r.yRot) * r.z < 0 ? -1 : 1;
		}
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(root, bodyCore, torsoCore, headCore, spoolL, spoolR, armL, armR, needles, halo);
		ElderAnim.reset(robe);
		ElderAnim.reset(mantle);
		ElderAnim.reset(veil);
		ElderAnim.reset(lanterns);
		float t = s.ageInTicks;
		// Dying, it plays its ultimate once more, ending in the bloom as it breaks apart
		boolean dying = s.deathTime > 0;
		int action = dying ? ElderBoss.ULTIMATE : s.action;
		float at = dying ? s.deathTime * (200F / ElderBoss.DEATH_TICKS) : s.actionTime;
		float drift = Mth.clamp(s.walkAnimationSpeed * 1.4F, 0, 1);
		boolean ultimate = action == ElderBoss.ULTIMATE;
		root.y += Mth.sin(t * 0.05F) * 2.2F;
		body.zRot += Mth.sin(t * 0.03F) * 0.012F;
		body.xRot += Mth.sin(t * 0.025F) * 0.01F + 0.05F * drift;
		head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.5F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		float rain = action == Weaver.NEEDLES ? ElderAnim.env(at, 14, 12, 14) : 0;
		float charge = action == Weaver.LANCE ? ElderAnim.env(at, 20, 36, 8) : 0;
		float whirl = action == Weaver.THOUSAND ? ElderAnim.env(at, 14, 56, 14) : 0;
		float storm = ultimate ? ElderAnim.env(at, 40, 140, 6) : 0;
		halo.zRot += t * (0.006F + 0.04F * rain + 0.12F * charge + 0.06F * whirl + 0.14F * storm);
		float haloSize = 1 + 0.15F * charge + 0.9F * storm;
		halo.xScale = haloSize;
		halo.yScale = haloSize;
		halo.zScale = haloSize;
		needles.yRot += t * (0.02F + 0.25F * whirl + 0.3F * storm);
		float ring = 1 + 0.5F * whirl + 1.1F * storm;
		needles.xScale = ring;
		needles.zScale = ring;
		spoolL.yRot += t * (0.08F + 0.4F * (whirl + storm));
		spoolR.yRot -= t * (0.08F + 0.4F * (whirl + storm));
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.04F + i * 0.8F) * 0.02F + 0.1F * drift + 0.12F * storm * Mth.sin(t * 0.3F + i);
			robe[i].zRot += Mth.cos(t * 0.035F + i * 1.1F) * 0.025F;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += Mth.sin(t * 0.06F + i * 1.3F) * 0.05F + 0.1F * drift;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.07F + i * 1.5F) * (0.12F + 0.2F * storm);
			strands[i].xRot += Mth.cos(t * 0.05F + i) * 0.08F + 0.15F * drift;
		}
		for (int i = 0; i < veil.length; i++) {
			veil[i].xRot += Mth.sin(t * 0.06F + i * 0.7F) * 0.04F + 0.08F * drift + 0.15F * storm * Mth.sin(t * 0.4F + i);
			veil[i].zRot += Mth.cos(t * 0.05F + i * 1.3F) * 0.03F;
		}
		for (int i = 0; i < lanterns.length; i++) {
			lanterns[i].zRot += Mth.sin(t * 0.06F + i * 1.9F) * (0.08F + 0.3F * storm);
			lanterns[i].xRot += Mth.cos(t * 0.05F + i * 1.2F) * (0.06F + 0.2F * storm);
		}
		armL.zRot += Mth.sin(t * 0.035F) * 0.05F;
		armR.zRot -= Mth.sin(t * 0.035F + 0.6F) * 0.05F;
		handL.zRot += Mth.sin(t * 0.05F) * 0.06F;
		handR.zRot -= Mth.sin(t * 0.05F + 0.9F) * 0.06F;

		// The weight of its blows (see below), which some of them also throw its halo and cloth about with
		float[] ac = ElderAnim.accent(at, impacts(action), 10, 12);
		float power = action == Weaver.THOUSAND ? 0.4F : 1F;
		float g = ac[0] * power, j = ac[1] * power;

		switch (action) {
			case Weaver.THREAD -> {
				float e = ElderAnim.env(at, 10, 8, 12);
				armR.xRot -= 0.9F * e;
				armR.zRot += 0.2F * e;
				head.yRot -= 0.2F * e;
			}
			case Weaver.NEEDLES -> {
				armL.zRot -= 0.6F * rain;
				armR.zRot += 0.6F * rain;
				head.xRot -= 0.3F * rain;
			}
			case Weaver.UNRAVEL -> {
				float in = ElderAnim.env(at, 6, 4, 4);
				float out = ElderAnim.env(at - 10, 3, 4, 8);
				armL.xRot -= 0.8F * in - 0.3F * out;
				armR.xRot -= 0.8F * in - 0.3F * out;
				body.xRot -= 0.1F * in;
			}
			case Weaver.LANCE -> {
				// The halo spins up behind its head, and it points along the beam
				head.xRot -= 0.25F * charge;
				armR.xRot -= 0.9F * charge;
				armR.zRot += 0.5F * charge;
				armL.zRot -= 0.3F * charge;
				body.xRot += Mth.sin(t * 2.2F) * 0.01F * charge;
			}
			case Weaver.CRADLE -> {
				// Its hands weave, then it pulls everything taut
				float weave = ElderAnim.env(at, 4, 8, 4);
				float pull = ElderAnim.env(at - 14, 6, 44, 8);
				float snap = ElderAnim.env(at - 62, 2, 3, 8);
				handL.zRot += Mth.sin(at * 1.3F) * 0.4F * weave;
				handR.zRot -= Mth.sin(at * 1.3F + 1F) * 0.4F * weave;
				armL.xRot += 0.5F * pull;
				armR.xRot += 0.5F * pull;
				armL.zRot += 0.25F * pull;
				armR.zRot -= 0.25F * pull;
				body.xRot -= 0.08F * pull;
				armL.zRot -= 0.5F * snap;
				armR.zRot += 0.5F * snap;
			}
			case Weaver.THOUSAND -> {
				// It rises with its arms up while its needles whirl
				root.y -= 6F * whirl;
				armL.zRot -= 0.7F * whirl;
				armR.zRot += 0.7F * whirl;
				head.xRot -= 0.35F * whirl;
			}
			case Weaver.WARP -> {
				// Warp and Weft: she sinks a little, then sweeps both arms up high over her, her head tilted up to them,
				// and her hands weave back and forth over her head, crossing and uncrossing three times (each passing in
				// front by turns) as if knotting a great grid; she holds it up, her arms trembling with the strain, her
				// halo swelling; she rears back and slams both arms down before her, bending deep at the waist (52),
				// stays bowed a moment and rises
				float coil = ElderAnim.env(at, 3, 0, 6);
				float raise = ElderAnim.smooth((at - 1) / 9F);
				float w = Mth.clamp((at - 4) / 7F, 0, 3);
				float cross = (1 - Mth.cos(w * Mth.PI * 2)) / 2;
				float front = ((int) w % 2 == 0 ? 1 : -1) * cross;
				float rear = ElderAnim.smooth((at - 42) / 5F);
				float down = ElderAnim.fall((at - 47) / 5F);
				float k = 1 - ElderAnim.smooth((at - 57) / 19F);
				float up = raise * (1 - down), back = rear * (1 - down), bow = down * k;
				float strain = ElderAnim.smooth((at - 22) / 16F) * (1 - down);
				float shakeL = ElderAnim.tremor(t, 0.04F * strain), shakeR = ElderAnim.tremor(t + 1.7F, 0.04F * strain);
				// (raised, a turn of zRot one way takes an arm on up over her and in towards her head, and xRot takes it
				// forward or back; brought down, the arms end before her, a little apart)
				armL.zRot += -1.15F * up - 0.15F * cross + 0.25F * coil + 1.2F * bow + shakeL;
				armR.zRot -= -1.15F * up - 0.15F * cross + 0.25F * coil + 1.2F * bow + shakeR;
				armL.xRot += -0.25F * up - 0.25F * front + 0.12F * strain + 0.45F * back - 0.85F * bow;
				armR.xRot += -0.25F * up + 0.25F * front + 0.12F * strain + 0.45F * back - 0.85F * bow;
				foreL.zRot += -0.22F * (up + bow) - 1.2F * cross;
				foreR.zRot -= -0.22F * (up + bow) - 1.2F * cross;
				handL.zRot += -0.25F * up - 0.3F * cross;
				handR.zRot -= -0.25F * up - 0.3F * cross;
				// her fingers clawing at the threads as she weaves and straining as she holds them, curled as she strikes
				float claw = Mth.sin(at * 1.1F) * 0.3F * cross + 0.25F * strain + 0.5F * bow;
				handL.xRot += claw;
				handR.xRot += claw;
				torso.xRot += 0.1F * coil - 0.1F * up - 0.08F * strain - 0.22F * back + 0.7F * bow;
				torso.zRot += 0.04F * Mth.sin(w * Mth.PI * 2) * up;
				body.xRot += 0.08F * bow;
				head.xRot += 0.1F * coil - 0.45F * up - 0.1F * strain - 0.1F * back + 0.15F * bow;
				root.y += 3F * coil - 4F * up - 3F * back + 9F * bow;
				// the halo turns once as she weaves and swells as she holds, and her cloth is thrown out by the blow
				halo.zRot += ElderAnim.spin(at - 2, 6, 14, 6, 1);
				ElderAnim.scale(halo, 1 + 0.08F * cross * up + 0.18F * strain);
				halo.y += 4F * j;
				fling(0.12F * strain + 0.45F * j, 0, t);
			}
			case Weaver.SPINDLE -> {
				// Spindle Storm: she crouches and twists back, then spins up, her arms going out and up and her dress
				// flaring; whirling about once a second, her head thrown back, her dress, threads and lanterns fly out
				// and her needles whirl wide round her; she slows and stops facing forward again (whole turns), her arms
				// dropping (only her body turns: the server keeps her facing)
				float turn = ElderAnim.spin(at, 16, 80, 14, 5);
				float fly = ElderAnim.spinRate(at, 16, 80, 14);
				float coil = ElderAnim.env(at, 4, 1, 8);
				float open = ElderAnim.smooth((at - 2) / 10F) * (1 - ElderAnim.smooth((at - 96) / 12F));
				float droop = ElderAnim.env(at - 96, 7, 0, 7);
				body.yRot += turn;
				torso.yRot -= 0.35F * coil;
				body.zRot += 0.05F * fly * Mth.sin(t * 0.23F);
				// (her arms trail the turn: it takes her left hand forward and her right back)
				armL.zRot += -0.4F * open + 0.35F * droop;
				armR.zRot -= -0.4F * open + 0.35F * droop;
				armL.xRot += 0.3F * fly;
				armR.xRot -= 0.3F * fly;
				foreL.zRot -= 0.22F * open;
				foreR.zRot += 0.22F * open;
				handL.zRot -= 0.25F * open;
				handR.zRot += 0.25F * open;
				torso.xRot += 0.08F * coil - 0.12F * fly + 0.08F * droop;
				head.xRot += 0.1F * coil - 0.55F * fly + 0.2F * droop;
				root.y += 3F * coil - 5F * fly;
				fling(0.9F * fly, 0.5F * fly, t);
				needles.yRot += 0.6F * turn;
				needles.xScale *= 1 + 0.6F * fly;
				needles.zScale *= 1 + 0.6F * fly;
				halo.zRot += 0.4F * turn;
				ElderAnim.scale(halo, 1 + 0.15F * fly);
				spoolL.yRot += 2 * turn;
				spoolR.yRot -= 2 * turn;
			}
			case Weaver.NEEDLEFALL -> {
				// The Loom Comes Down: her right arm rises to the sky, the hand clawing at it and her head tilted up after
				// it; then four times she yanks it down to her waist (24, 34, 44, 54), hunching over it with her whole
				// body, her halo jolting and her left arm thrown up and back against the pull, and drives it back up
				// between; after the last it hangs low a while, and she straightens
				float raise = ElderAnim.smooth(at / 16F);
				float claw = ElderAnim.env(at - 2, 4, 14, 4);
				float pull = 0;
				for (int y = 24; y <= 54; y += 10) {
					float yank = ElderAnim.fall((at - y + 4) / 4F);
					pull += yank * (1 - (y == 54 ? 0 : ElderAnim.smooth((at - y - 1) / 5F)));
				}
				float hang = ElderAnim.smooth((at - 55) / 7F);
				float k = 1 - ElderAnim.smooth((at - 66) / 18F);
				float up = raise * (1 - pull), low = pull * k, limp = hang * k;
				// (raised, a turn of zRot one way brings the arm down past her side, xRot forward: it comes down
				// before her right side)
				armR.zRot += 1.28F * up - 1.17F * low - 0.25F * limp;
				armR.xRot += -0.2F * up - 0.75F * low + 0.2F * limp;
				foreR.zRot += 0.22F * up;
				foreR.xRot += -0.6F * low + 0.45F * limp;
				handR.xRot += claw * (0.35F + 0.4F * Mth.sin(at * 1.3F)) + 0.6F * low - 0.3F * limp;
				handR.zRot += claw * 0.25F * Mth.sin(at * 0.9F + 1F);
				armL.zRot += 0.15F * up - 0.35F * low + 0.2F * limp;
				armL.xRot += 0.3F * low - 0.15F * limp;
				// (a turn of yRot one way takes her right shoulder forward, into the pull)
				torso.xRot += -0.08F * up + 0.38F * low + 0.1F * limp;
				torso.yRot -= 0.06F * up + 0.2F * low;
				torso.zRot -= 0.06F * up - 0.08F * low;
				body.xRot += 0.05F * low;
				head.xRot += -0.45F * up + 0.15F * low + 0.15F * limp;
				head.yRot -= 0.12F * up;
				root.y += 5F * low;
				// the halo jolts with each yank
				halo.y += 7F * j;
				halo.xRot += 0.25F * j;
				ElderAnim.scale(halo, 1 + 0.12F * up - 0.08F * j);
				fling(0.4F * j, 0, t);
			}
			case ElderBoss.ULTIMATE -> {
				float snap = ElderAnim.env(at - 188, 3, 4, 8);
				// Arms spread wide and up, head back, trembling
				armL.zRot -= 0.55F * storm;
				armR.zRot += 0.55F * storm;
				head.xRot -= 0.5F * storm;
				body.xRot -= 0.12F * storm;
				body.zRot += Mth.sin(t * 2.6F) * 0.012F * storm;
				// Everything snaps
				armL.xRot -= 1.0F * snap;
				armR.xRot -= 1.0F * snap;
				body.xRot += 0.35F * snap;
			}
			default -> {
			}
		}
		// The weight of its blows: it rises and draws its arms in as it gathers, its halo flaring, straining; then it
		// drops into the blow, thrown forward, and its halo kicks round
		root.y += -2.6F * g + 2.0F * j;
		root.z -= 2.2F * j;
		body.xRot += -0.12F * g + 0.2F * j;
		body.zRot += ElderAnim.tremor(t, 0.02F * g);
		head.xRot += -0.1F * g + 0.14F * j;
		armL.zRot -= 0.2F * g;
		armR.zRot += 0.2F * g;
		halo.zRot += 0.5F * j;
		halo.xScale *= 1 + 0.12F * g;
		halo.yScale *= 1 + 0.12F * g;
		if (action == ElderBoss.SPAWN) {
			woven(at, t);
		}
		if (dying) {
			// At the very end it sinks as it breaks apart
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}

	}

	/**
	 * Its cloth and threads flung out from it by a whirl or a blow ({@code out}, a turn outward) and trailing after it as it
	 * turns ({@code lag}): the robe (the drapes of the sleeves trailing), the mantle, the veil, the strands and the lanterns
	 * at its hands, which swing out from it.
	 */
	private void fling(float out, float lag, float t) {
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += robeOut[i] * out * (0.8F + 0.25F * Mth.sin(t * 0.5F + i * 1.7F)) + robeArm[i] * lag;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += out * (0.9F + 0.3F * Mth.sin(t * 0.6F + i * 1.3F));
		}
		for (int i = 0; i < veil.length; i++) {
			veil[i].xRot += 0.6F * out * (1 + 0.3F * Mth.sin(t * 0.7F + i));
			veil[i].zRot += 0.3F * out * Mth.sin(t * 0.55F + i * 2.1F);
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].xRot += 0.7F * out * (1 + 0.3F * Mth.sin(t * 0.8F + i * 1.9F));
			strands[i].zRot += 0.4F * out * Mth.sin(t * 0.65F + i * 1.3F);
		}
		// (the first three hang from its left hand: a turn of zRot one way swings each out from it, of xRot back)
		for (int i = 0; i < lanterns.length; i++) {
			float side = i < 3 ? 1 : -1;
			lanterns[i].zRot -= side * out * (0.75F + 0.15F * Mth.sin(t * 0.4F + i));
			lanterns[i].xRot += side * lag;
		}
	}

	/** Forming, on its 140-tick timeline: the halo first, its needles drawn in, then it is woven from the hem up. */
	private void woven(float at, float t) {
		ElderAnim.scale(halo, ElderAnim.smooth((at - 8) / 37F));
		halo.zRot += t * 0.3F * (1 - ElderAnim.smooth((at - 100) / 30F));
		float draw = ElderAnim.smooth((at - 18) / 47F);
		float ring = at < 18 ? 0 : 1 + 1.6F * (1 - draw);
		needles.xScale *= ring;
		needles.yScale *= at < 18 ? 0 : 1;
		needles.zScale *= ring;
		needles.yRot += t * 0.5F * (1 - draw);
		float hem = ElderAnim.smooth((at - 45) / 45F);
		bodyCore.xScale *= 0.6F + 0.4F * hem;
		bodyCore.yScale *= hem;
		bodyCore.zScale *= 0.6F + 0.4F * hem;
		for (int i = 0; i < robe.length; i++) {
			// Each strip unrolls down from the waist
			robe[i].yScale *= ElderAnim.smooth((at - 55 - (i % 12) * 3) / 30F);
		}
		ElderAnim.scale(spoolL, ElderAnim.smooth((at - 60) / 20F));
		ElderAnim.scale(spoolR, ElderAnim.smooth((at - 60) / 20F));
		ElderAnim.scale(torsoCore, ElderAnim.smooth((at - 70) / 25F));
		for (ModelPart m : mantle) {
			m.yScale *= ElderAnim.smooth((at - 85) / 25F);
		}
		float arms = ElderAnim.smooth((at - 80) / 32F);
		ElderAnim.scale(armL, arms);
		ElderAnim.scale(armR, arms);
		armL.zRot += 1.2F * (1 - arms);
		armR.zRot -= 1.2F * (1 - arms);
		ElderAnim.scale(headCore, ElderAnim.smooth((at - 95) / 23F));
		for (ModelPart v : veil) {
			v.yScale *= ElderAnim.smooth((at - 110) / 20F);
		}
		for (int i = 0; i < lanterns.length; i++) {
			ElderAnim.scale(lanterns[i], ElderAnim.smooth((at - 112 - i * 3) / 8F));
		}
		// The cocoon tears: it swells a little and settles
		ElderAnim.scale(root, 1 + 0.06F * ElderAnim.env(at - 121, 2, 2, 12));
	}

	/** When each of its blows lands (ticks at tempo 1, as the server strikes them). */
	private static float[] impacts(int action) {
		return switch (action) {
			case Weaver.THREAD -> new float[] {15};
			case Weaver.NEEDLES -> new float[] {20};
			case Weaver.UNRAVEL -> new float[] {10};
			case Weaver.LANCE -> new float[] {22};
			case Weaver.CRADLE -> new float[] {14, 62};
			case Weaver.THOUSAND -> new float[] {16, 21, 26, 31, 36, 41, 46, 51, 56, 60};
			case Weaver.WARP -> new float[] {52};
			// the yanks of the loom
			case Weaver.NEEDLEFALL -> new float[] {24, 34, 44, 54};
			default -> ElderAnim.NONE;
		};
	}
}
