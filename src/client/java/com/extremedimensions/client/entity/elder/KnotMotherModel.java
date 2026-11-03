package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.Weaver;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Knot Mother moving. She does not drift: she sways on the cords that tie her to the floor, and the knot she is
 * breathes, its loops turning slowly round it each its own way. Her upper hands hold her threads up like a puppeteer's
 * strings, rising and falling; her lower hands weave without stopping. Her broken halo turns unevenly and the bobbins
 * circle the knot. Drawing in, she raises her upper hands and hauls them down hard; snarling, her lower hands weave
 * fast and fling down. She takes the Weaver's other blows in her own way. Forming, the cords come up out of the floor
 * first, then the loops tie themselves round nothing, the knot fills, and she grows out of it: body, four arms, head,
 * veil, halo last. Dying, her loops come loose and spin outwards as she sinks.
 */
public class KnotMotherModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("knot_mother"), "main");

	private final ModelPart root, body, knot, roots, head, halo, orbit, spoolL, spoolR, bodyCore, torsoCore, headCore;
	private final ModelPart armL, armR, handL, handR, armLL, armLR, foreLL, foreLR, handLL, handLR;
	private final ModelPart[] loops, tails, shawl, strands, bobbins, veil;

	public KnotMotherModel(ModelPart model) {
		super(model);
		this.root = KnotMotherGeometry.find(model, "root");
		this.body = KnotMotherGeometry.find(model, "body");
		this.knot = KnotMotherGeometry.find(model, "knot");
		this.roots = KnotMotherGeometry.find(model, "roots");
		this.head = KnotMotherGeometry.find(model, "head");
		this.halo = KnotMotherGeometry.find(model, "halo");
		this.orbit = KnotMotherGeometry.find(model, "orbit");
		this.spoolL = KnotMotherGeometry.find(model, "spool_l");
		this.spoolR = KnotMotherGeometry.find(model, "spool_r");
		this.bodyCore = KnotMotherGeometry.find(model, "bodycore");
		this.torsoCore = KnotMotherGeometry.find(model, "torsocore");
		this.headCore = KnotMotherGeometry.find(model, "headcore");
		this.armL = KnotMotherGeometry.find(model, "arm_l");
		this.armR = KnotMotherGeometry.find(model, "arm_r");
		this.handL = KnotMotherGeometry.find(model, "hand_l");
		this.handR = KnotMotherGeometry.find(model, "hand_r");
		this.armLL = KnotMotherGeometry.find(model, "arm_ll");
		this.armLR = KnotMotherGeometry.find(model, "arm_lr");
		this.foreLL = KnotMotherGeometry.find(model, "forearm_ll");
		this.foreLR = KnotMotherGeometry.find(model, "forearm_lr");
		this.handLL = KnotMotherGeometry.find(model, "hand_ll");
		this.handLR = KnotMotherGeometry.find(model, "hand_lr");
		this.loops = ElderAnim.parts(model, KnotMotherGeometry.LOOPS, KnotMotherGeometry::find);
		this.tails = ElderAnim.parts(model, KnotMotherGeometry.ROBE, KnotMotherGeometry::find);
		this.shawl = ElderAnim.parts(model, KnotMotherGeometry.MANTLE, KnotMotherGeometry::find);
		this.strands = ElderAnim.parts(model, KnotMotherGeometry.STRAND, KnotMotherGeometry::find);
		this.bobbins = ElderAnim.parts(model, KnotMotherGeometry.LANTERN, KnotMotherGeometry::find);
		this.veil = ElderAnim.parts(model, KnotMotherGeometry.VEIL, KnotMotherGeometry::find);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(root, body, knot, roots, bodyCore, torsoCore, headCore, spoolL, spoolR, orbit, halo, head);
		ElderAnim.reset(armL, armR, handL, handR, armLL, armLR, foreLL, foreLR, handLL, handLR);
		ElderAnim.reset(loops);
		ElderAnim.reset(tails);
		ElderAnim.reset(shawl);
		ElderAnim.reset(strands);
		ElderAnim.reset(bobbins);
		ElderAnim.reset(veil);
		float t = s.ageInTicks;
		boolean dying = s.deathTime > 0;
		int action = dying ? ElderBoss.ULTIMATE : s.action;
		float at = dying ? s.deathTime * (200F / ElderBoss.DEATH_TICKS) : s.actionTime;
		float drift = Mth.clamp(s.walkAnimationSpeed * 1.4F, 0, 1);
		boolean ultimate = action == ElderBoss.ULTIMATE;
		float storm = ultimate ? ElderAnim.env(at, 40, 140, 6) : 0;
		float draw = action == KnotMother.DRAW ? ElderAnim.env(at, 12, 20, 10) : 0;
		float snarl = action == KnotMother.SNARL ? ElderAnim.env(at, 6, 16, 10) : 0;
		float whirl = action == Weaver.THOUSAND ? ElderAnim.env(at, 14, 56, 14) : 0;
		float charge = action == Weaver.LANCE ? ElderAnim.env(at, 20, 36, 8) : 0;

		// Swaying on her cords rather than floating free
		root.y += Mth.sin(t * 0.04F) * 1.2F;
		body.zRot += Mth.sin(t * 0.027F) * 0.02F;
		body.xRot += Mth.sin(t * 0.021F) * 0.015F + 0.04F * drift;
		head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.5F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		head.zRot += Mth.sin(t * 0.031F) * 0.05F;
		// The knot breathes; its loops turn round it, each its own way
		float breathe = 1 + 0.025F * Mth.sin(t * 0.06F) + 0.05F * draw + 0.08F * storm;
		knot.xScale *= breathe;
		knot.yScale *= breathe;
		knot.zScale *= breathe;
		for (int i = 0; i < loops.length; i++) {
			float way = i % 2 == 0 ? 1 : -1;
			loops[i].zRot += way * t * (0.008F + 0.002F * (i % 3)) * (1 + 6 * draw + 4 * snarl + 8 * storm);
			loops[i].xRot += Mth.sin(t * 0.03F + i * 1.7F) * 0.04F;
		}
		halo.zRot += t * (0.004F + 0.03F * charge + 0.08F * storm) + Mth.sin(t * 0.05F) * 0.08F;
		float haloSize = 1 + 0.12F * charge + 0.7F * storm;
		halo.xScale = haloSize;
		halo.yScale = haloSize;
		halo.zScale = haloSize;
		orbit.yRot += t * (0.015F + 0.2F * whirl + 0.25F * storm + 0.12F * draw);
		float ring = 1 + 0.4F * whirl + 0.9F * storm - 0.25F * draw;
		orbit.xScale = ring;
		orbit.zScale = ring;
		spoolL.yRot += t * (0.05F + 0.5F * (draw + whirl + storm));
		spoolR.yRot -= t * (0.05F + 0.5F * (draw + whirl + storm));
		for (int i = 0; i < tails.length; i++) {
			tails[i].xRot += Mth.sin(t * 0.04F + i * 0.8F) * 0.025F + 0.08F * drift + 0.1F * storm * Mth.sin(t * 0.3F + i);
			tails[i].zRot += Mth.cos(t * 0.035F + i * 1.1F) * 0.03F;
		}
		for (int i = 0; i < shawl.length; i++) {
			shawl[i].xRot += Mth.sin(t * 0.06F + i * 1.3F) * 0.05F + 0.08F * drift;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.07F + i * 1.5F) * (0.1F + 0.2F * storm);
			strands[i].xRot += Mth.cos(t * 0.05F + i) * 0.07F + 0.12F * drift;
		}
		for (int i = 0; i < veil.length; i++) {
			veil[i].xRot += Mth.sin(t * 0.06F + i * 0.7F) * 0.04F + 0.06F * drift + 0.15F * storm * Mth.sin(t * 0.4F + i);
			veil[i].zRot += Mth.cos(t * 0.05F + i * 1.3F) * 0.03F;
		}
		for (int i = 0; i < bobbins.length; i++) {
			bobbins[i].zRot += Mth.sin(t * 0.07F + i * 1.9F) * (0.12F + 0.3F * storm + 0.4F * draw);
			bobbins[i].xRot += Mth.cos(t * 0.06F + i * 1.2F) * (0.08F + 0.2F * storm);
		}
		// Her upper hands hold her threads up, rising and falling like a puppeteer's; her lower hands weave
		armL.zRot += Mth.sin(t * 0.03F) * 0.06F;
		armR.zRot -= Mth.sin(t * 0.03F + 0.8F) * 0.06F;
		handL.xRot += Mth.sin(t * 0.045F) * 0.1F;
		handR.xRot += Mth.sin(t * 0.045F + 1.2F) * 0.1F;
		float weave = Mth.sin(t * 0.12F);
		foreLL.yRot += weave * 0.08F;
		foreLR.yRot -= weave * 0.08F;
		handLL.zRot += Mth.sin(t * 0.18F) * 0.12F;
		handLR.zRot -= Mth.sin(t * 0.18F + 0.5F) * 0.12F;

		switch (action) {
			case KnotMother.DRAW -> {
				// Up go the upper hands, then she hauls them down and back hard on her threads
				float raise = ElderAnim.env(at, 10, 4, 4);
				float haul = ElderAnim.env(at - 14, 3, 14, 10);
				armL.zRot -= 0.35F * raise;
				armR.zRot += 0.35F * raise;
				armL.xRot += 0.9F * haul;
				armR.xRot += 0.9F * haul;
				armL.zRot += 0.5F * haul;
				armR.zRot -= 0.5F * haul;
				body.xRot += 0.12F * haul;
				head.xRot -= 0.15F * haul;
				// and her lower hands open for whoever comes in
				armLL.xRot -= 0.4F * haul;
				armLR.xRot -= 0.4F * haul;
				handLL.zRot -= 0.4F * haul;
				handLR.zRot += 0.4F * haul;
			}
			case KnotMother.SNARL -> {
				// Her lower hands weave fast, then fling the knots down
				float fast = ElderAnim.env(at, 3, 8, 3);
				float fling = ElderAnim.env(at - 10, 2, 6, 10);
				foreLL.yRot += Mth.sin(at * 1.6F) * 0.3F * fast;
				foreLR.yRot -= Mth.sin(at * 1.6F) * 0.3F * fast;
				armLL.xRot += 0.7F * fling;
				armLR.xRot += 0.7F * fling;
				armLL.zRot += 0.3F * fling;
				armLR.zRot -= 0.3F * fling;
				body.xRot += 0.08F * fling;
				head.xRot += 0.2F * fling;
			}
			case Weaver.THREAD -> {
				float e = ElderAnim.env(at, 10, 8, 12);
				armR.xRot -= 0.7F * e;
				armR.zRot += 0.4F * e;
				head.yRot -= 0.2F * e;
			}
			case Weaver.NEEDLES -> {
				float e = ElderAnim.env(at, 14, 12, 14);
				armL.zRot -= 0.3F * e;
				armR.zRot += 0.3F * e;
				armLL.xRot -= 0.5F * e;
				armLR.xRot -= 0.5F * e;
				head.xRot -= 0.3F * e;
			}
			case Weaver.UNRAVEL -> {
				float in = ElderAnim.env(at, 6, 4, 4);
				float out = ElderAnim.env(at - 10, 3, 4, 8);
				armLL.xRot -= 0.6F * in - 0.5F * out;
				armLR.xRot -= 0.6F * in - 0.5F * out;
				armL.zRot += 0.4F * in - 0.6F * out;
				armR.zRot -= 0.4F * in - 0.6F * out;
				ElderAnim.scale(knot, 1 - 0.08F * in + 0.12F * out);
			}
			case Weaver.LANCE -> {
				head.xRot -= 0.25F * charge;
				armR.xRot -= 0.6F * charge;
				armL.xRot -= 0.6F * charge;
				body.xRot += Mth.sin(t * 2.2F) * 0.01F * charge;
			}
			case Weaver.CRADLE -> {
				float fast = ElderAnim.env(at, 4, 8, 4);
				float pull = ElderAnim.env(at - 14, 6, 44, 8);
				float snap = ElderAnim.env(at - 62, 2, 3, 8);
				handLL.zRot += Mth.sin(at * 1.3F) * 0.4F * fast;
				handLR.zRot -= Mth.sin(at * 1.3F + 1F) * 0.4F * fast;
				armLL.zRot += 0.3F * pull;
				armLR.zRot -= 0.3F * pull;
				armL.xRot += 0.4F * pull;
				armR.xRot += 0.4F * pull;
				armLL.zRot -= 0.5F * snap;
				armLR.zRot += 0.5F * snap;
			}
			case Weaver.THOUSAND -> {
				root.y -= 5F * whirl;
				armL.zRot -= 0.4F * whirl;
				armR.zRot += 0.4F * whirl;
				head.xRot -= 0.35F * whirl;
			}
			case ElderBoss.ULTIMATE -> {
				float snap = ElderAnim.env(at - 188, 3, 4, 8);
				armL.zRot -= 0.3F * storm;
				armR.zRot += 0.3F * storm;
				armLL.zRot += 0.6F * storm;
				armLR.zRot -= 0.6F * storm;
				head.xRot -= 0.5F * storm;
				body.zRot += Mth.sin(t * 2.6F) * 0.015F * storm;
				armL.xRot += 1.0F * snap;
				armR.xRot += 1.0F * snap;
				body.xRot += 0.3F * snap;
			}
			default -> {
			}
		}
		// The weight of her blows: she gathers, straining on her cords, then drops into the blow
		float[] ac = ElderAnim.accent(at, impacts(action), 10, 12);
		float power = action == Weaver.THOUSAND ? 0.4F : 1F;
		float g = ac[0] * power, j = ac[1] * power;
		root.y += -1.6F * g + 1.6F * j;
		body.xRot += -0.1F * g + 0.16F * j;
		body.zRot += ElderAnim.tremor(t, 0.02F * g);
		head.xRot += -0.08F * g + 0.12F * j;
		halo.zRot += 0.4F * j;
		ElderAnim.scale(knot, 1 + 0.05F * g - 0.03F * j);
		if (action == ElderBoss.SPAWN) {
			tied(at, t);
		}
		if (dying) {
			// Her loops come loose and spin outwards; at the very end she sinks
			float loose = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.5F) / (ElderBoss.DEATH_TICKS * 0.4F));
			for (int i = 0; i < loops.length; i++) {
				ElderAnim.scale(loops[i], 1 + 0.8F * loose);
				loops[i].zRot += t * 0.2F * loose * (i % 2 == 0 ? 1 : -1);
			}
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}
	}

	/** Forming, on the same 140-tick timeline as the Weaver: tied together out of her cords, from the floor up. */
	private void tied(float at, float t) {
		// The cords come up out of the floor
		roots.yScale *= ElderAnim.smooth(at / 30F);
		// The loops tie themselves round nothing, spinning, one after another
		for (int i = 0; i < loops.length; i++) {
			float k = ElderAnim.smooth((at - 18 - i * 3) / 22F);
			ElderAnim.scale(loops[i], k);
			loops[i].zRot += (1 - k) * 6F;
		}
		// The knot fills
		ElderAnim.scale(knot, 0.2F + 0.8F * ElderAnim.smooth((at - 40) / 30F));
		float spools = ElderAnim.smooth((at - 55) / 20F);
		ElderAnim.scale(spoolL, spools);
		ElderAnim.scale(spoolR, spools);
		for (int i = 0; i < tails.length; i++) {
			tails[i].yScale *= ElderAnim.smooth((at - 55 - (i % 9) * 3) / 25F);
		}
		float draw = ElderAnim.smooth((at - 50) / 45F);
		orbit.xScale *= at < 50 ? 0 : 1 + 1.4F * (1 - draw);
		orbit.yScale *= at < 50 ? 0 : 1;
		orbit.zScale *= at < 50 ? 0 : 1 + 1.4F * (1 - draw);
		orbit.yRot += t * 0.5F * (1 - draw);
		// She grows out of it: body, four arms, head, veil, halo last
		ElderAnim.scale(torsoCore, ElderAnim.smooth((at - 70) / 25F));
		for (ModelPart m : shawl) {
			m.yScale *= ElderAnim.smooth((at - 85) / 25F);
		}
		float upper = ElderAnim.smooth((at - 80) / 30F), lower = ElderAnim.smooth((at - 88) / 26F);
		ElderAnim.scale(armL, upper);
		ElderAnim.scale(armR, upper);
		armL.zRot += 1.0F * (1 - upper);
		armR.zRot -= 1.0F * (1 - upper);
		ElderAnim.scale(armLL, lower);
		ElderAnim.scale(armLR, lower);
		ElderAnim.scale(headCore, ElderAnim.smooth((at - 95) / 23F));
		for (ModelPart v : veil) {
			v.yScale *= ElderAnim.smooth((at - 108) / 20F);
		}
		for (int i = 0; i < bobbins.length; i++) {
			ElderAnim.scale(bobbins[i], ElderAnim.smooth((at - 110 - i * 3) / 8F));
		}
		ElderAnim.scale(halo, ElderAnim.smooth((at - 105) / 25F));
		halo.zRot += t * 0.3F * (1 - ElderAnim.smooth((at - 115) / 20F));
		// The last knot pulled tight: she swells a little and settles
		ElderAnim.scale(root, 1 + 0.06F * ElderAnim.env(at - 124, 2, 2, 12));
	}

	/** When each of her blows lands (ticks at tempo 1, as the server strikes them). */
	private static float[] impacts(int action) {
		return switch (action) {
			case KnotMother.DRAW -> new float[] {16, 26};
			case KnotMother.SNARL -> new float[] {12};
			case Weaver.THREAD -> new float[] {15};
			case Weaver.NEEDLES -> new float[] {20};
			case Weaver.UNRAVEL -> new float[] {10};
			case Weaver.LANCE -> new float[] {22};
			case Weaver.CRADLE -> new float[] {14, 62};
			case Weaver.THOUSAND -> new float[] {16, 21, 26, 31, 36, 41, 46, 51, 56, 60};
			default -> ElderAnim.NONE;
		};
	}
}
